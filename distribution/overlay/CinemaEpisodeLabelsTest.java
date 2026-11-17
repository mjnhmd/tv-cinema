package com.fongmi.android.tv.ui.custom;

import com.fongmi.android.tv.bean.Episode;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CinemaEpisodeLabelsTest {

    @Test
    public void compactsLongFileNamesWithoutLosingEpisodeCoordinates() {
        List<Episode> items = List.of(
                Episode.create("[2.18GB] Xz出发 2026-10-24 第3期上-4K.mp4", "u1"),
                Episode.create("[3.54GB] Xz出发 2026.10.10-第一期上 4K.mkv", "u2"),
                Episode.create("[2.82GB] Xz出发 2026.10.25-第3期下.4K.mkv", "u3"),
                Episode.create("[832.17MB] Xz出发2026.11.10.第5期还有加更-4K.mp4", "u4"),
                Episode.create("[3.73GB] Xz出发2026.11.15-第六期下-4K.mp4", "u5"),
                Episode.create("[992.00MB] Xz出发20261105.第4期花絮特辑4k.mp4", "u6"));
        List<String> labels = CinemaEpisodeLabels.display(items);
        assertEquals("10.24 第3期上", labels.get(0));
        assertTrue(labels.get(1).contains("第1期"));
        assertEquals("10.25 第3期下", labels.get(2));
        assertTrue(labels.get(3).contains("第5期"));
        assertEquals("11.15 第6期下", labels.get(4));
        assertTrue(labels.get(5).contains("第4期"));
    }

    @Test
    public void keepsShortNamesRawAndDisambiguatesCollapsedDuplicates() {
        List<String> labels = CinemaEpisodeLabels.display(List.of(
                Episode.create("第01集", "u1"),
                Episode.create("S01E02 1080p", "u2")));
        assertEquals("第01集", labels.get(0));
        assertEquals("S01E02 1080p", labels.get(1));
        assertTrue(labels.stream().noneMatch(String::isEmpty));
    }

    @Test
    public void unknownLabelsRemainRawInsteadOfInventingAnOrder() {
        String raw = "某个没有集数和日期但特别长的文件名称-4K";
        assertEquals(raw, CinemaEpisodeLabels.display(List.of(Episode.create(raw, "u"))).get(0));
    }
}
