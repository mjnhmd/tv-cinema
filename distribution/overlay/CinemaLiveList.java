package com.fongmi.android.tv.api.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 播放列表内容识别与扫描范围计算，独立出来便于在 JVM 上单测。
 * {@link CinemaLanDiscovery} 的网络与线程部分依赖 Android 运行时，不在此覆盖。
 */
public final class CinemaLiveList {

    /** 一个 /24 网段里可分配的主机数。 */
    private static final int HOSTS_PER_SUBNET = 254;

    private CinemaLiveList() {
    }

    /**
     * 统计播放列表里的频道条目数。
     *
     * m3u 数 {@code #EXTINF}，txt 数含 {@code ://} 的非注释行，两者取大。
     * 同时要求出现播放列表特征（m3u 头或 txt 分组标记），
     * 否则普通网页里的链接会被误判成频道。
     *
     * @return 频道数，非播放列表返回 0
     */
    public static int countChannels(String body) {
        if (body == null || body.isEmpty()) return 0;
        String text = body.replace("\r\n", "\n").replace("\r", "");
        boolean m3u = text.contains("#EXTM3U") || text.contains("#EXTINF");
        boolean txt = text.contains("#genre#");
        if (!m3u && !txt) return 0;
        int count = 0;
        for (String line : text.split("\n")) {
            if (line.startsWith("#EXTINF")) count++;
            else if (txt && !line.startsWith("#") && line.contains("://")) count++;
        }
        return count;
    }

    /** 判断一个 IPv4 地址是否为私有网段地址。 */
    public static boolean isPrivate(String ip) {
        if (ip == null) return false;
        if (ip.startsWith("10.") || ip.startsWith("192.168.")) return true;
        if (!ip.startsWith("172.")) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        try {
            int second = Integer.parseInt(parts[1]);
            return second >= 16 && second <= 31;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 取 IPv4 地址的前三段加结尾点，用于展开 /24 网段。 */
    public static String subnetPrefix(String ip) {
        if (ip == null || !isPrivate(ip)) return "";
        int index = ip.lastIndexOf('.');
        if (index <= 0) return "";
        return ip.substring(0, index + 1);
    }

    /**
     * 把本机各个私有 IPv4 地址展开成待扫描主机列表。
     *
     * 多网段时按 1..254 轮转展开，保证每个网段都能在预算内扫到前面的地址，
     * 而不是第一个网段把预算吃光。上次命中的地址排在第一位，
     * 这样重复扫描能立刻拿到已知结果。
     *
     * @param localIps      本机私有 IPv4 地址，非私有地址会被忽略
     * @param limit         主机总数上限
     * @param preferredHost 优先探测的地址，可为空
     * @return 去重后的待扫描地址，长度不超过 limit
     */
    public static List<String> expandHosts(List<String> localIps, int limit, String preferredHost) {
        Set<String> prefixes = new LinkedHashSet<>();
        if (localIps != null) {
            for (String ip : localIps) {
                String prefix = subnetPrefix(ip);
                if (!prefix.isEmpty()) prefixes.add(prefix);
            }
        }
        Set<String> hosts = new LinkedHashSet<>();
        if (preferredHost != null && isPrivate(preferredHost)) hosts.add(preferredHost);
        for (int host = 1; host <= HOSTS_PER_SUBNET && hosts.size() < limit; host++) {
            for (String prefix : prefixes) {
                if (hosts.size() >= limit) break;
                hosts.add(prefix + host);
            }
        }
        return new ArrayList<>(hosts);
    }

    /** 从一条播放列表地址里取出主机名，用于下一次扫描优先探测。 */
    public static String hostOf(String url) {
        if (url == null || url.isEmpty()) return "";
        String rest = url;
        int scheme = rest.indexOf("://");
        if (scheme >= 0) rest = rest.substring(scheme + 3);
        int slash = rest.indexOf('/');
        if (slash >= 0) rest = rest.substring(0, slash);
        int colon = rest.indexOf(':');
        if (colon > 0) rest = rest.substring(0, colon);
        return isPrivate(rest) ? rest : "";
    }
}
