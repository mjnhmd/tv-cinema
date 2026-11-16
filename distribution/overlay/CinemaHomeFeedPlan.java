package com.fongmi.android.tv.ui.home;

import com.fongmi.android.tv.bean.RexSection;
import com.fongmi.android.tv.ui.rex.RexSections;

import java.util.ArrayList;
import java.util.List;

public final class CinemaHomeFeedPlan {
    private CinemaHomeFeedPlan() {}

    public static List<RexSection> sections(int category) {
        List<RexSection> available = RexSections.home();
        List<RexSection> result = new ArrayList<>();
        if (category == PosterHomeCatalog.MOVIES || category == PosterHomeCatalog.TV) {
            for (RexSection section : available) if (section.getKey().equals("trending_day")) result.add(section);
        }
        for (String key : PosterHomeCatalog.compactKeys(category)) {
            for (RexSection section : available) if (section.getKey().equals(key)) result.add(section);
        }
        if (category == PosterHomeCatalog.ALL || category == PosterHomeCatalog.TV) {
            for (RexSection section : available) if (section.isShelf()) result.add(section);
        }
        return result;
    }
}
