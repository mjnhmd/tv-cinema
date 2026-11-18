package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.monitoring.CinemaMonitoring;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 局域网 IPTV 服务自动发现。
 *
 * 设计取舍：
 * 1. 不做 mDNS/SSDP 广播发现。目标服务（IPTV-API、iPTV 这类自建产物）绝大多数只在
 *    HTTP 上暴露播放列表，不注册任何发现协议，等 mDNS 等不到东西。
 * 2. 先做一轮 TCP 端口探活，再只对真正开放的端口去读播放列表内容。
 *    旧实现直接对每个 host:port 顺序试全部路径，实测 254 个地址 × 9 端口 × 8 路径
 *    在家庭网络上要 5 分钟以上，用户在出结果前就放弃了。
 * 3. 1905（IPTV-API 默认端口）单独先行扫描，命中就不再扫其余端口。
 * 4. 只做「发现并填入」，不自动启用。用户点选后才替换当前直播源，避免悄悄改配置。
 */
public final class CinemaLanDiscovery {

    /** 常见自建 IPTV 服务端口，按命中概率排序；1905 是 IPTV-API 默认端口。 */
    private static final int[] PORTS = {1905, 8080, 8000, 8088, 9000, 3000, 5000, 7890, 9981};

    /** 播放列表探测路径，命中即认为是对端产物。 */
    private static final String[] PATHS = {
            "/interface.m3u", "/interface.m3u?relay=1", "/txt", "/m3u",
            "/result.m3u", "/result.txt", "/live.m3u", "/tvbox.m3u"
    };

    /** 局域网内可用主机 RTT 通常小于 10ms，250ms 足够把无响应地址判掉。 */
    private static final int CONNECT_TIMEOUT_MS = 250;
    private static final int READ_TIMEOUT_MS = 1500;
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final int MAX_SCAN_HOSTS = 254;
    private static final int PORT_THREADS = 64;
    private static final int PATH_THREADS = 16;
    /** 单次扫描的总时长上限，超时就让用户看到已经找到的部分和诊断信息。 */
    private static final long SCAN_BUDGET_MS = 25_000L;
    private static final long PATH_WAIT_MS = 8_000L;

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

    /**
     * 一次扫描的诊断信息。扫不到时把它显示给用户，
     * 用户才能判断是「网段不同」「路由器隔离」还是「服务没开」。
     *
     * @param localIp   本机私有 IPv4 地址，多个用 / 分隔
     * @param hosts     实际探测的主机数
     * @param ports     探测的端口数
     * @param openPorts 真正开放的端口数
     * @param tookMs    总耗时
     */
    public record Diagnostics(String localIp, int hosts, int ports, int openPorts, long tookMs) {

        public String summary() {
            String local = localIp == null || localIp.isEmpty() ? "未知" : localIp;
            return "本机 " + local + "，扫描 " + hosts + " 个地址 / " + ports + " 个端口，"
                    + openPorts + " 个端口有响应，用时 " + String.format("%.1f", tookMs / 1000.0) + " 秒";
        }
    }

    public interface Listener {

        void onFound(List<Result> results);

        void onEmpty(Diagnostics diagnostics);

        void onError();
    }

    private record Report(List<Result> results, Diagnostics diagnostics) {
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
            Report report;
            boolean failed = false;
            try {
                report = scan();
            } catch (Throwable error) {
                failed = true;
                report = new Report(List.of(), new Diagnostics("", 0, 0, 0, 0));
            } finally {
                running = false;
            }
            Report finalReport = report;
            boolean finalFailed = failed;
            reportToMonitoring(finalFailed, finalReport);
            App.post(() -> {
                if (finalFailed) listener.onError();
                else if (finalReport.results().isEmpty()) listener.onEmpty(finalReport.diagnostics());
                else listener.onFound(finalReport.results());
            });
        }, "cinema-lan-scan").start();
        return true;
    }

    /**
     * 往 Bugly 留一条可检索的记录。
     * 扫不到时用户往往只能描述「搜不到」，这里带上本机网段、扫描规模和耗时，
     * 下次反馈时可以直接对上；扫描成功则只写 breadcrumb，不占一条异常记录。
     */
    private static void reportToMonitoring(boolean failed, Report report) {
        Diagnostics diagnostics = report.diagnostics();
        String local = diagnostics.localIp() == null || diagnostics.localIp().isEmpty() ? "none" : diagnostics.localIp();
        String detail = "local=" + local + " hosts=" + diagnostics.hosts()
                + " ports=" + diagnostics.ports() + " open=" + diagnostics.openPorts()
                + " found=" + report.results().size();
        if (failed) CinemaMonitoring.incident("lan_scan_error", diagnostics.tookMs(), detail, null);
        else if (report.results().isEmpty()) CinemaMonitoring.incident("lan_scan_empty", diagnostics.tookMs(), detail, null);
        else CinemaMonitoring.breadcrumb("lan_scan_found", diagnostics.tookMs(), detail);
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

    /** 扫描到结果但页面已经关闭时，至少把地址留下来，下次可以「用上次地址」。 */
    public static void remember(Result result) {
        Prefers.put(KEY_LAST, result.url());
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

    private static Report scan() {
        long started = System.currentTimeMillis();
        List<String> ips = localIps();
        String local = String.join("/", ips);
        List<String> hosts = CinemaLiveList.expandHosts(ips, MAX_SCAN_HOSTS, CinemaLiveList.hostOf(lastUrl()));
        if (hosts.isEmpty()) return new Report(List.of(), new Diagnostics(local, 0, PORTS.length, 0, 0));
        OkHttpClient client = OkHttp.client().newBuilder()
                .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .callTimeout(READ_TIMEOUT_MS * 2L, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false)
                .build();
        ExecutorService portPool = Executors.newFixedThreadPool(PORT_THREADS);
        ExecutorService pathPool = Executors.newFixedThreadPool(PATH_THREADS);
        int openCount = 0;
        List<Result> found = new ArrayList<>();
        try {
            // 第一轮：只扫 IPTV-API 默认端口，命中就不必再动其余端口
            List<String> open = probePorts(portPool, hosts, new int[]{PORTS[0]}, started);
            openCount += open.size();
            found.addAll(probePaths(pathPool, client, open));
            if (found.isEmpty()) {
                int[] rest = new int[PORTS.length - 1];
                System.arraycopy(PORTS, 1, rest, 0, rest.length);
                List<String> more = probePorts(portPool, hosts, rest, started);
                openCount += more.size();
                found.addAll(probePaths(pathPool, client, more));
            }
        } finally {
            portPool.shutdownNow();
            pathPool.shutdownNow();
        }
        found.sort(Comparator.comparing(Result::name));
        return new Report(found, new Diagnostics(local, hosts.size(), PORTS.length, openCount,
                System.currentTimeMillis() - started));
    }

    /** 并发探活主机端口，返回形如 {@code host:port} 的开放列表。 */
    private static List<String> probePorts(ExecutorService pool, List<String> hosts, int[] ports, long started) {
        List<Future<String>> futures = new ArrayList<>();
        for (int port : ports) {
            for (String host : hosts) {
                futures.add(pool.submit(() -> portOpen(host, port) ? host + ":" + port : null));
            }
        }
        List<String> open = new ArrayList<>();
        for (Future<String> future : futures) {
            long remaining = SCAN_BUDGET_MS - (System.currentTimeMillis() - started);
            if (remaining <= 0) break;
            try {
                String value = future.get(remaining, TimeUnit.MILLISECONDS);
                if (value != null) open.add(value);
            } catch (Exception ignored) {
            }
        }
        return open;
    }

    /** 对已开放的端口并发试播放列表路径，同一地址只保留一条结果。 */
    private static List<Result> probePaths(ExecutorService pool, OkHttpClient client, List<String> targets) {
        if (targets.isEmpty()) return List.of();
        List<Future<Result>> futures = new ArrayList<>();
        for (String target : targets) {
            for (String path : PATHS) futures.add(pool.submit(probePath(client, target, path)));
        }
        Set<String> seen = new LinkedHashSet<>();
        List<Result> results = new ArrayList<>();
        for (Future<Result> future : futures) {
            try {
                Result result = future.get(PATH_WAIT_MS, TimeUnit.MILLISECONDS);
                if (result == null || !result.usable()) continue;
                // 同一服务常常多条路径都能出列表，按 host:port 去重
                if (seen.add(result.name())) results.add(result);
            } catch (Exception ignored) {
            }
        }
        return results;
    }

    private static Callable<Result> probePath(OkHttpClient client, String target, String path) {
        return () -> {
            String url = "http://" + target + path;
            try {
                Request request = new Request.Builder().url(url).get().build();
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) return null;
                    int channels = CinemaLiveList.countChannels(peek(response));
                    return channels > 0 ? new Result(target, url, channels) : null;
                }
            } catch (Exception ignored) {
                return null;
            }
        };
    }

    /** 只探测端口是否接受连接，不读任何内容。 */
    private static boolean portOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            return true;
        } catch (Exception ignored) {
            return false;
        }
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

    /** 取本机所有可用的 IPv4 私有地址。跨网段扫描既慢又不合理，范围由 expandHosts 决定。 */
    private static List<String> localIps() {
        List<String> ips = new ArrayList<>();
        try {
            for (Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces(); nifs.hasMoreElements(); ) {
                NetworkInterface nif = nifs.nextElement();
                if (!nif.isUp() || nif.isLoopback() || nif.isVirtual()) continue;
                for (Enumeration<InetAddress> addresses = nif.getInetAddresses(); addresses.hasMoreElements(); ) {
                    InetAddress address = addresses.nextElement();
                    if (!(address instanceof Inet4Address) || address.isLoopbackAddress()) continue;
                    String ip = address.getHostAddress();
                    if (!CinemaLiveList.isPrivate(ip) || ips.contains(ip)) continue;
                    ips.add(ip);
                }
            }
        } catch (Exception ignored) {
        }
        return ips;
    }
}
