package com.fongmi.android.tv.source;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class NavigationPlaylistGuard {

    private static final Set<String> CATEGORIES = Set.of(
            "喜剧片", "喜劇片", "动作片", "動作片", "爱情片", "愛情片", "科幻片", "恐怖片",
            "剧情片", "劇情片", "战争片", "戰爭片", "纪录片", "紀錄片", "动画片", "動畫片",
            "电视剧", "電視劇", "电影", "電影", "国剧", "國劇", "港剧", "港劇", "美剧", "美劇",
            "韩剧", "韓劇", "日剧", "日劇", "综艺", "綜藝", "动漫", "動漫");
    private static final Pattern CATEGORY_PATH = Pattern.compile("(?i)(?:^|/)(?:xijupian|dongzuopian|aiqingpian|kehuanpian|kongbupian|juqingpian|zhanzhengpian|jilupian|donghuapian|dianshiju)(?:/|$|[?#])");

    private NavigationPlaylistGuard() {
    }

    public static boolean isNavigationList(Flag flag) {
        if (flag == null) return false;
        List<Episode> episodes = flag.getEpisodes();
        if (episodes.size() < 5) return false;
        int categories = 0;
        int paths = 0;
        for (Episode episode : episodes) {
            if (episode == null || !CATEGORIES.contains(episode.getName().trim())) continue;
            categories++;
            if (CATEGORY_PATH.matcher(episode.getUrl()).find()) paths++;
        }
        return categories >= 5 && categories * 3 >= episodes.size() && paths >= 4;
    }
}
