package com.fongmi.android.tv.ui.home;

import com.fongmi.android.tv.bean.RexSection;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CinemaHomeFeedPlanTest {
    @Test public void defaultHomeRequestsOnlyVisibleShelvesWithHeroSourcesFirst() {
        List<String> keys = keys(PosterHomeCatalog.ALL);
        assertEquals(List.of("trending_day", "douban_movie", "douban_tv", "cn_series", "kr_series",
                "cn_variety", "anime_daily"), keys);
        assertEquals(keys.size(), keys.stream().distinct().count());
    }

    @Test public void eachCategoryKeepsItsSourcesWithoutLoadingUnrelatedCategories() {
        assertEquals(List.of("trending_day", "douban_movie", "now_playing", "popular_movie", "top_movie"), keys(PosterHomeCatalog.MOVIES));
        assertEquals(List.of("top_movie", "top_tv"), keys(PosterHomeCatalog.TOP));
        List<String> series = keys(PosterHomeCatalog.TV);
        assertEquals(List.of("trending_day", "douban_tv", "popular_tv"), series.subList(0, 3));
        assertEquals(7, series.size());
        assertTrue(series.contains("anime_daily"));
    }

    private List<String> keys(int category) {
        return CinemaHomeFeedPlan.sections(category).stream().map(RexSection::getKey).toList();
    }
}
