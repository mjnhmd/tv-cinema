package com.fongmi.android.tv.source;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class CinemaEpisodeOrderTest {

    private static Flag flag(Episode... episodes) {
        Flag flag = new Flag("main");
        flag.getEpisodes().addAll(List.of(episodes));
        return flag;
    }

    @Test
    public void screenshotVarietyFilesSortByRealIssueInsteadOfFileSize() {
        Episode thirdUpper = Episode.create("[2.18GB] Xz出发 2026-10-24 第3期上-4K.mp4", "u1");
        Episode first = Episode.create("[3.54GB] Xz出发 2026.10.10-第一期上 4K.mkv", "u2");
        Episode thirdLower = Episode.create("[2.82GB] Xz出发 2026.10.25-第3期下.4K.mkv", "u3");
        Episode bonus = Episode.create("[832.17MB] Xz出发2026.11.10.第5期还有加更-4K.mp4", "u4");
        Flag flag = flag(thirdUpper, first, thirdLower, bonus);
        flag.setPosition(0);

        assertTrue(CinemaEpisodeOrder.apply(flag, false));

        assertEquals(List.of("u2", "u1", "u3", "u4"), List.of(
                flag.getEpisodes().get(0).getUrl(), flag.getEpisodes().get(1).getUrl(),
                flag.getEpisodes().get(2).getUrl(), flag.getEpisodes().get(3).getUrl()));
        assertSame("The playing object must stay selected after sorting", thirdUpper, flag.getEpisodes().get(flag.getPosition()));
    }

    @Test
    public void numbersSortNumericallyAndUpperMiddleLowerStayTogether() {
        Flag flag = flag(
                Episode.create("第10集", "u10"),
                Episode.create("第2集下", "u2b"),
                Episode.create("第1集", "u1"),
                Episode.create("第2集上", "u2a"));

        assertTrue(CinemaEpisodeOrder.apply(flag, false));

        assertEquals(List.of("u1", "u2a", "u2b", "u10"), urls(flag));
    }

    @Test
    public void reversedOrderKeepsPartsReadableAndUnknownItemsLast() {
        Flag flag = flag(
                Episode.create("第1集", "u1"),
                Episode.create("第2集上", "u2a"),
                Episode.create("第2集下", "u2b"),
                Episode.create("第3集", "u3"),
                Episode.create("特别彩蛋", "unknown"));

        assertTrue(CinemaEpisodeOrder.apply(flag, true));

        assertEquals(List.of("u3", "u2a", "u2b", "u1", "unknown"), urls(flag));
    }

    @Test
    public void unclearPlaylistsKeepSourceOrder() {
        Flag flag = flag(
                Episode.create("文件甲", "a"),
                Episode.create("文件乙", "b"),
                Episode.create("合集 第1-3集", "c"),
                Episode.create("第2集", "d"));

        assertFalse(CinemaEpisodeOrder.apply(flag, false));

        assertEquals(List.of("a", "b", "c", "d"), urls(flag));
    }

    @Test
    public void duplicatesAndGeneratedLabelsAreNotReordered() {
        Flag flag = new Flag("main");
        Episode generated = Episode.create("01", "g1");
        generated.setGeneratedLabel(true);
        Episode generatedTwo = Episode.create("02", "g2");
        generatedTwo.setGeneratedLabel(true);
        flag.getEpisodes().addAll(List.of(generated, generatedTwo));

        assertFalse(CinemaEpisodeOrder.apply(flag, false));
        assertEquals(List.of("g1", "g2"), urls(flag));
    }

    @Test
    public void stableTiesKeepTheSourceReleaseOrder() {
        Flag flag = flag(
                Episode.create("Show 第2集 1080p", "a"),
                Episode.create("Show 第1集 1080p", "b"),
                Episode.create("Show 第1集 2160p", "c"),
                Episode.create("Show 第2集 2160p", "d"));

        assertTrue(CinemaEpisodeOrder.apply(flag, false));

        assertEquals(List.of("b", "c", "a", "d"), urls(flag));
    }

    @Test
    public void dateOnlyPlaylistsUseAirDatesAndKeepUndatedItemsLast() {
        Flag flag = dateFlag();

        assertTrue(CinemaEpisodeOrder.apply(flag, false));

        assertEquals(List.of("d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9", "undated"), urls(flag));
    }

    @Test
    public void reversedDatePlaylistsDescendButStillKeepUndatedItemsLast() {
        Flag flag = dateFlag();

        assertTrue(CinemaEpisodeOrder.apply(flag, true));

        assertEquals(List.of("d9", "d8", "d7", "d6", "d5", "d4", "d3", "d2", "d1", "undated"), urls(flag));
    }

    private static Flag dateFlag() {
        return flag(
                Episode.create("Xz出发 2026.10.24", "d3"),
                Episode.create("无日期花絮", "undated"),
                Episode.create("Xz出发 2026.10.10", "d1"),
                Episode.create("Xz出发 2026.10.17", "d2"),
                Episode.create("Xz出发 2026.10.31", "d4"),
                Episode.create("Xz出发 2026.11.07", "d5"),
                Episode.create("Xz出发 2026.11.14", "d6"),
                Episode.create("Xz出发 2026.11.21", "d7"),
                Episode.create("Xz出发 2026.11.28", "d8"),
                Episode.create("Xz出发 2026.12.05", "d9"));
    }

    @Test
    public void datedExtrasWithoutEpisodeNumberStayAfterTheNumberedEpisodes() {
        Episode second = Episode.create("第2集 2026-09-02", "u2");
        Flag flag = flag(
                Episode.create("花絮 2026-09-20", "extra"),
                second,
                Episode.create("第1集 2026-09-01", "u1"),
                Episode.create("第3集 2026-09-03", "u3"),
                Episode.create("第4集 2026-09-04", "u4"),
                Episode.create("第5集 2026-09-05", "u5"),
                Episode.create("第6集 2026-09-06", "u6"),
                Episode.create("第7集 2026-09-07", "u7"),
                Episode.create("第8集 2026-09-08", "u8"),
                Episode.create("第9集 2026-09-09", "u9"));
        flag.setPosition(1);

        assertTrue(CinemaEpisodeOrder.apply(flag, false));

        assertEquals(List.of("u1", "u2", "u3", "u4", "u5", "u6", "u7", "u8", "u9", "extra"), urls(flag));
        assertSame("Sorting must keep the selected episode object", second,
                flag.getEpisodes().get(flag.getPosition()));
    }

    private static List<String> urls(Flag flag) {
        List<String> urls = new ArrayList<>();
        for (Episode episode : flag.getEpisodes()) urls.add(episode.getUrl());
        return urls;
    }
}
