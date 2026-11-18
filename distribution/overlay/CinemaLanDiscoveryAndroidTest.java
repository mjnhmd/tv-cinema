package com.fongmi.android.tv.api.config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 在真实 Android 运行时里跑一次局域网扫描，确认「一直转圈」不会再出现。
 *
 * 测试自己在设备上起一个 1905 端口的播放列表服务，扫描必须能发现它，
 * 并且整体耗时受扫描预算约束。
 */
@RunWith(AndroidJUnit4.class)
public class CinemaLanDiscoveryAndroidTest {

    private static final String PLAYLIST = "#EXTM3U\n"
            + "#EXTINF:-1 tvg-name=\"CCTV1\" group-title=\"央视\",CCTV1\n"
            + "http://example.test/1.m3u8\n"
            + "#EXTINF:-1 tvg-name=\"CCTV2\" group-title=\"央视\",CCTV2\n"
            + "http://example.test/2.m3u8\n";

    @Test
    public void findsPlaylistServerOnLanPort() throws Exception {
        try (ServerSocket server = new ServerSocket(1905)) {
            server.setSoTimeout(30_000);
            AtomicBoolean serving = new AtomicBoolean(true);
            Thread serverThread = new Thread(() -> serve(server, serving), "cinema-lan-fixture");
            serverThread.setDaemon(true);
            serverThread.start();

            List<CinemaLanDiscovery.Result> found = new ArrayList<>();
            List<CinemaLanDiscovery.Diagnostics> empty = new ArrayList<>();
            CountDownLatch done = new CountDownLatch(1);
            long startedAt = System.currentTimeMillis();
            assertTrue("扫描应该能启动", CinemaLanDiscovery.start(new CinemaLanDiscovery.Listener() {
                @Override
                public void onFound(List<CinemaLanDiscovery.Result> results) {
                    found.addAll(results);
                    done.countDown();
                }

                @Override
                public void onEmpty(CinemaLanDiscovery.Diagnostics diagnostics) {
                    empty.add(diagnostics);
                    done.countDown();
                }

                @Override
                public void onError() {
                    done.countDown();
                }
            }));
            assertFalse("扫描期间不应重复启动", CinemaLanDiscovery.start(new CinemaLanDiscovery.Listener() {
                @Override
                public void onFound(List<CinemaLanDiscovery.Result> results) {
                }

                @Override
                public void onEmpty(CinemaLanDiscovery.Diagnostics diagnostics) {
                }

                @Override
                public void onError() {
                }
            }));
            assertTrue("扫描应在预算内结束而不是一直转圈", done.await(40, TimeUnit.SECONDS));
            long took = System.currentTimeMillis() - startedAt;
            serving.set(false);

            assertTrue("本机 1905 端口上的播放列表必须被发现，实际诊断：" + empty.size(), !found.isEmpty());
            CinemaLanDiscovery.Result result = found.get(0);
            assertTrue("命中地址应带 1905 端口：" + result.url(), result.url().contains(":1905"));
            assertTrue("频道数应大于 0", result.channels() > 0);
            assertTrue("扫描耗时 " + took + "ms 超出 30 秒", took < 30_000);
        }
    }

    private static void serve(ServerSocket server, AtomicBoolean serving) {
        while (serving.get()) {
            try {
                Socket socket = server.accept();
                Thread handler = new Thread(() -> handle(socket), "cinema-lan-fixture-req");
                handler.setDaemon(true);
                handler.start();
            } catch (Exception ignored) {
            }
        }
    }

    private static void handle(Socket socket) {
        try (Socket closeable = socket) {
            byte[] body = PLAYLIST.getBytes(StandardCharsets.UTF_8);
            drain(closeable);
            OutputStream output = closeable.getOutputStream();
            String header = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: application/vnd.apple.mpegurl\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            output.write(header.getBytes(StandardCharsets.UTF_8));
            output.write(body);
            output.flush();
        } catch (Exception ignored) {
        }
    }

    /** 读一次请求就不管了，避免等客户端关闭连接造成互等。 */
    private static void drain(Socket socket) {
        try {
            socket.setSoTimeout(300);
            byte[] buffer = new byte[2048];
            socket.getInputStream().read(buffer);
        } catch (Exception ignored) {
        }
    }
}
