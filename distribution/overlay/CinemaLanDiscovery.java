package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Config;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 局域网 IPTV 服务自动发现。
 *
 * 设计取舍：
 * 1. 不做 mDNS/SSDP 广播发现。目标服务（IPTV-API、iPTV 这类自建产物）绝大多数只在
 *    HTTP 上暴露播放列表，不注册任何发现协议，等 mDNS 等不到东西。
 * 2. 直接扫描本机 /24 网段的常见 IPTV 端口，用播放列表内容特征判定，比端口名可靠。
 * 3. 只做「发现并填入」，不自动启用。用户点选后才替换当前直播源，避免悄悄改配置。
 */
public final class CinemaLanDiscovery {

    /** 常见自建 IPTV 服务端口，按命中概率排序。 */
    private static final int[] PORTS = {1905, 8080, 8000, 8088, 9000, 3000, 5000, 7890, 9981};

    /** 播放列表探测路径，命中即认为是对端产物。 */
    private static final String[] PATHS = {
            "/interface.m3u", "/interface.m3u?relay=1", "/txt", "/m3u",
            "/result.m3u", "/result.txt", "/live.m3u", "/tvbox.m3u"
    };

    private static final int CONNECT_TIMEOUT_MS = 400;
    private static final int READ_TIMEOUT_MS = 1200;
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final int MAX_SCAN_HOSTS = 254;
    private static final int PROBE_THREADS = 24;

    private static final String KEY_LAST = "cinema_lan_last";
    private static final Object LOCK = new Object();
    private static volatile boolean running;

    private CinemaLanDiscovery() {
    }

    public record Result(String name, String url, int channels) {

        /** 频道数大于 0 才算可用结果。 */
        public boolean usable() {
            return channels > 0;
        }
    }

    public interface Listener {

        void onFound(List<Result> results);

        void onEmpty();

        void onError();
    }

    public static boolean isRunning() {
        return running;
    }

    public static String lastUrl() {
        return Prefers.getString(KEY_LAST);
    }

    /** 启动一次后台扫描。重复调用会被拒绝，调用方应据此提示「正在扫描」。 */
    public static boolean start(Listener listener) {
        synchronized (LOCK) {
            if (running) return false;
            running = true;
        }
        new Thread(() -> {
            List<Result> results = new ArrayList<>();
            try {
                results.addAll(scan());
            } catch (Throwable ignored) {
            } finally {
                running = false;
            }
            App.post(() -> {
                if (results.isEmpty()) listener.onEmpty();
                else listener.onFound(results);
            });
        }, "cinema-lan-scan").start();
        return true;
    }

    /**
     * 把发现结果做成一条直播配置，但不切换当前源。
     * 调用方拿到 Config 后自行决定是否保存或加载。
     */
    public static Config toConfig(Result result) {
        Prefers.put(KEY_LAST, result.url());
        return Config.find(result.url(), BaseConfig.LIVE)
                .name("局域网 · " + result.name());
    }

    /**
     * 取上次扫描到的地址并包成直播配置，供「用上次地址」直接复用。
     * 由本类内部处理 {@link BaseConfig#LIVE}，调用方无需接触包级私有类型。
     *
     * @return 无历史地址时返回 null
     */
    public static Config lastConfig() {
        String last = lastUrl();
        if (last.isEmpty()) return null;
        return Config.find(last, BaseConfig.LIVE).name("局域网 · " + last);
    }

    private static List<Result> scan() {
        Set<String> hosts = localHosts();
        if (hosts.isEmpty()) return List.of();
        OkHttpClient client = OkHttp.client().newBuilder()
                .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .callTimeout(READ_TIMEOUT_MS * 2L, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .build();
        ExecutorService pool = Executors.newFixedThreadPool(PROBE_THREADS);
        List<Future<Result>> futures = new ArrayList<>();
        AtomicInteger budget = new AtomicInteger(MAX_SCAN_HOSTS);
        try {
            for (String host : hosts) {
                if (budget.decrementAndGet() < 0) break;
                for (int port : PORTS) futures.add(pool.submit(probe(client, host, port)));
            }
            Set<String> seen = new LinkedHashSet<>();
            List<Result> found = new ArrayList<>();
            for (Future<Result> future : futures) {
                Result result;
                try {
                    result = future.get();
                } catch (Exception ignored) {
                    continue;
                }
                if (result == null || !result.usable()) continue;
                // 同一服务可能多路径命中，按 URL 去重
                if (seen.add(result.url())) found.add(result);
            }
            return found;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Callable<Result> probe(OkHttpClient client, String host, int port) {
        return () -> {
            for (String path : PATHS) {
                String url = "http://" + host + ":" + port + path;
                try {
                    Request request = new Request.Builder().url(url).get().build();
                    try (Response response = client.newCall(request).execute()) {
                        if (!response.isSuccessful() || response.body() == null) continue;
                        int channels = CinemaLiveList.countChannels(peek(response));
                        if (channels > 0) return new Result(host + ":" + port, url, channels);
                    }
                } catch (Exception ignored) {
                }
            }
            return null;
        };
    }

    /** 只读响应体前若干字节，避免把超大播放列表整个读进内存。 */
    private static String peek(Response response) throws Exception {
        try (var source = response.body().source()) {
            source.request(MAX_BODY_BYTES);
            var buffer = source.getBuffer().clone();
            long bytes = Math.min(buffer.size(), MAX_BODY_BYTES);
            return buffer.readUtf8(bytes);
        }
    }

    /**
     * 取本机所有可用的 IPv4 私有地址，按 /24 展开成待扫描主机。
     * 只扫前三段相同的地址，跨网段扫描既慢又不合理。
     */
    private static Set<String> localHosts() {
        Set<String> hosts = new LinkedHashSet<>();
        Set<String> prefixes = new LinkedHashSet<>();
        try {
            for (Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces(); nifs.hasMoreElements(); ) {
                NetworkInterface nif = nifs.nextElement();
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) continue;
                for (Enumeration<InetAddress> addresses = nif.getInetAddresses(); addresses.hasMoreElements(); ) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) continue;
                    String ip = address.getHostAddress();
                    if (!CinemaLiveList.isPrivate(ip)) continue;
                    prefixes.add(ip.substring(0, ip.lastIndexOf('.') + 1));
                }
            }
        } catch (Exception ignored) {
        }
        for (String prefix : prefixes) {
            for (int host = 1; host <= 254; host++) hosts.add(prefix + host);
        }
        return hosts;
    }
}
