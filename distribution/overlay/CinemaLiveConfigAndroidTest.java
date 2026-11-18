package com.fongmi.android.tv.api.config;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.impl.Callback;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 确认「在设置里手动填 http://<局域网地址>:1905/interface.m3u?relay=1」这条路径能真的把直播源加载进来。
 *
 * 之前电视上反馈「添加链接时就提示不支持」：要么是明文 HTTP 被运行时拦掉，
 * 要么是配置加载链路对 http 有别的限制。测试在设备内起一个 http 播放列表服务，
 * 走完整配置加载链路，失败时把真实报错打出来。
 */
@RunWith(AndroidJUnit4.class)
public class CinemaLiveConfigAndroidTest {

    private static final String PLAYLIST = "#EXTM3U\n"
            + "#EXTINF:-1 tvg-name=\"CCTV1\" group-title=\"央视\",CCTV1\n"
            + "http://192.168.31.236:1905/1.ts\n"
            + "#EXTINF:-1 tvg-name=\"CCTV2\" group-title=\"央视\",CCTV2\n"
            + "http://192.168.31.236:1905/2.ts\n";

    @Test
    public void loadsPlainHttpLiveConfigFromLanServer() throws Exception {
        try (ServerSocket server = new ServerSocket(1905)) {
            server.setSoTimeout(30_000);
            AtomicBoolean serving = new AtomicBoolean(true);
            Thread serverThread = new Thread(() -> serve(server, serving), "cinema-live-config-fixture");
            serverThread.setDaemon(true);
            serverThread.start();

            AtomicReference<String> error = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            Config config = Config.find("http://127.0.0.1:1905/interface.m3u?relay=1", 1);
            LiveConfig.load(config, new Callback() {
                @Override
                public void start() {
                }

                @Override
                public void success() {
                    done.countDown();
                }

                @Override
                public void error(String msg) {
                    error.set(msg);
                    done.countDown();
                }
            });
            assertTrue("配置加载应在 20 秒内结束", done.await(20, TimeUnit.SECONDS));
            serving.set(false);

            assertNull("明文 http 直播源加载失败：" + error.get(), error.get());
            assertTrue("配置里应该记下地址", LiveConfig.hasUrl());
            assertTrue("应该解析出频道，而不是空表", !LiveConfig.isEmpty());
        }
    }

    private static void serve(ServerSocket server, AtomicBoolean serving) {
        while (serving.get()) {
            try {
                Socket socket = server.accept();
                Thread handler = new Thread(() -> handle(socket), "cinema-live-config-req");
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

    private static void drain(Socket socket) {
        try {
            socket.setSoTimeout(300);
            byte[] buffer = new byte[2048];
            socket.getInputStream().read(buffer);
        } catch (Exception ignored) {
        }
    }
}
