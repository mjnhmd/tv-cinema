package com.fongmi.android.tv.api.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class CinemaLiveListTest {

    @Test
    public void countsM3uEntries() {
        String m3u = "#EXTM3U x-tvg-url=\"http://a/b.xml\"\n"
                + "#EXTINF:-1 tvg-name=\"CCTV1\" group-title=\"央视\",CCTV1\n"
                + "http://a/1\n"
                + "#EXTINF:-1 tvg-name=\"CCTV2\" group-title=\"央视\",CCTV2\n"
                + "http://a/2\n";
        assertEquals(2, CinemaLiveList.countChannels(m3u));
    }

    @Test
    public void countsTxtEntries() {
        String txt = "央视,#genre#\nCCTV1,http://a/1\nCCTV2,http://a/2\n";
        assertEquals(2, CinemaLiveList.countChannels(txt));
    }

    @Test
    public void ignoresNonPlaylistHtml() {
        String html = "<html><body><a href=\"http://x\">link</a></body></html>";
        assertEquals(0, CinemaLiveList.countChannels(html));
    }

    @Test
    public void ignoresEmptyAndNull() {
        assertEquals(0, CinemaLiveList.countChannels(null));
        assertEquals(0, CinemaLiveList.countChannels(""));
    }

    @Test
    public void requiresFeatureBeforeCounting() {
        // 有网址但没有播放列表特征，不应计为频道
        assertEquals(0, CinemaLiveList.countChannels("some text\nhttp://a/1\n"));
    }

    @Test
    public void handlesCrlf() {
        String m3u = "#EXTM3U\r\n#EXTINF:-1,A\r\nhttp://a/1\r\n";
        assertEquals(1, CinemaLiveList.countChannels(m3u));
    }

    @Test
    public void privateRanges() {
        assertTrue(CinemaLiveList.isPrivate("192.168.31.236"));
        assertTrue(CinemaLiveList.isPrivate("10.0.0.5"));
        assertTrue(CinemaLiveList.isPrivate("172.16.0.1"));
        assertTrue(CinemaLiveList.isPrivate("172.31.255.254"));
        assertFalse(CinemaLiveList.isPrivate("172.32.0.1"));
        assertFalse(CinemaLiveList.isPrivate("8.8.8.8"));
        assertFalse(CinemaLiveList.isPrivate(null));
        assertFalse(CinemaLiveList.isPrivate("not-an-ip"));
    }

    @Test
    public void expandsWholeSubnetAndSkipsNonPrivate() {
        List<String> hosts = CinemaLiveList.expandHosts(List.of("192.168.31.12", "8.8.8.8"), 254, "");
        assertEquals(254, hosts.size());
        assertEquals("192.168.31.1", hosts.get(0));
        assertEquals("192.168.31.254", hosts.get(253));
        assertFalse(hosts.contains("8.8.8.1"));
    }

    @Test
    public void lastHitIsProbedFirst() {
        List<String> hosts = CinemaLiveList.expandHosts(List.of("192.168.31.12"), 254, "192.168.31.236");
        assertEquals("192.168.31.236", hosts.get(0));
        assertEquals(254, hosts.size());
    }

    @Test
    public void subnetBudgetIsSharedAcrossSubnets() {
        // 两个网段各 254 个地址，上限 254 时必须两边都扫到，不能被第一个网段吃光
        List<String> hosts = CinemaLiveList.expandHosts(List.of("192.168.31.12", "10.0.0.8"), 254, "");
        assertEquals(254, hosts.size());
        assertTrue(hosts.contains("10.0.0.1"));
        assertTrue(hosts.contains("10.0.0.127"));
        assertTrue(hosts.contains("192.168.31.1"));
        assertEquals("192.168.31.1", hosts.get(0));
        assertEquals("10.0.0.1", hosts.get(1));
    }

    @Test
    public void hostOfExtractsPrivateHostOnly() {
        assertEquals("192.168.31.236",
                CinemaLiveList.hostOf("http://192.168.31.236:1905/interface.m3u?relay=1"));
        assertEquals("10.0.0.5", CinemaLiveList.hostOf("https://10.0.0.5/txt"));
        assertEquals("", CinemaLiveList.hostOf("http://example.com:1905/a.m3u"));
        assertEquals("", CinemaLiveList.hostOf(""));
        assertEquals("", CinemaLiveList.hostOf(null));
    }

    @Test
    public void subnetPrefixRejectsNonPrivate() {
        assertEquals("192.168.31.", CinemaLiveList.subnetPrefix("192.168.31.236"));
        assertEquals("", CinemaLiveList.subnetPrefix("8.8.8.8"));
        assertEquals("", CinemaLiveList.subnetPrefix(null));
    }
}
