package com.fongmi.android.tv.ui.home;

import com.fongmi.android.tv.bean.Vod;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CinemaHeroSnapshotTest {

    private static final long NOW = 1_800_000_000_000L;

    @Test
    public void roundTripsVisibleFieldsAndArtwork() {
        Vod item = vod("tmdb:movie:1", "示例电影", "https://image.tmdb.org/t/p/w780/a.jpg");
        item.setTypeName("movie");
        item.setYear("2026");
        item.setRemarks("HD");
        item.setContent("简介");

        List<Vod> restored = CinemaHeroSnapshot.decode(CinemaHeroSnapshot.encode(List.of(item)), NOW, NOW);

        assertEquals(1, restored.size());
        assertEquals("tmdb:movie:1", restored.get(0).getId());
        assertEquals("示例电影", restored.get(0).getName());
        assertEquals("https://image.tmdb.org/t/p/w780/a.jpg", restored.get(0).getBackdrop());
        assertEquals("2026", restored.get(0).getYear());
        assertEquals("HD", restored.get(0).getRemarks());
        assertEquals("简介", restored.get(0).getContent());
    }

    @Test
    public void dropsInsecureArtworkAndDuplicateIds() {
        List<Vod> items = List.of(
                vod("douban:1", "无图", ""),
                vod("douban:2", "一", "http://img.example/a.jpg"),
                vod("douban:3", "二", "https://img.example/b.jpg"),
                vod("douban:3", "重复", "https://img.example/c.jpg"));

        List<Vod> restored = CinemaHeroSnapshot.decode(CinemaHeroSnapshot.encode(items), NOW, NOW);

        assertEquals(1, restored.size());
        assertEquals("douban:3", restored.get(0).getId());
        assertEquals("二", restored.get(0).getName());
    }

    @Test
    public void boundsItemCount() {
        List<Vod> items = new ArrayList<>();
        for (int i = 0; i < 12; i++) items.add(vod("tmdb:movie:" + i, "电影" + i, "https://img.example/" + i + ".jpg"));

        assertEquals(CinemaHeroSnapshot.MAX_ITEMS,
                CinemaHeroSnapshot.decode(CinemaHeroSnapshot.encode(items), NOW, NOW).size());
    }

    @Test
    public void rejectsStaleExpiredAndMalformedSnapshots() {
        String json = CinemaHeroSnapshot.encode(List.of(
                vod("tmdb:movie:1", "电影", "https://img.example/a.jpg")));

        assertTrue(CinemaHeroSnapshot.decode(json, NOW, NOW + CinemaHeroSnapshot.MAX_AGE_MS + 1).isEmpty());
        assertTrue(CinemaHeroSnapshot.decode("not json", NOW, NOW).isEmpty());
        assertTrue(CinemaHeroSnapshot.decode("", NOW, NOW).isEmpty());
    }

    private static Vod vod(String id, String name, String artwork) {
        Vod item = new Vod();
        item.setId(id);
        item.setName(name);
        item.setBackdrop(artwork);
        return item;
    }
}
