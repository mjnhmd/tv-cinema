package com.fongmi.android.tv.api.config;

/**
 * 播放列表内容识别的纯逻辑部分，独立出来便于在 JVM 上单测。
 * {@link CinemaLanDiscovery} 的网络与线程部分依赖 Android 运行时，不在此覆盖。
 */
public final class CinemaLiveList {

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
}
