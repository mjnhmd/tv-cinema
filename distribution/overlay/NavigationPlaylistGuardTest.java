package com.fongmi.android.tv.source;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NavigationPlaylistGuardTest {

    @Test
    public void rejectsScreenshotCategoriesAsOnePlaylist() {
        Flag flag = categories();
        for (int index = 0; index < 20; index++) flag.getEpisodes().add(Episode.create("首页视频" + index, "https://example.invalid/video/" + index));
        assertTrue(NavigationPlaylistGuard.isNavigationList(flag));
    }

    @Test
    public void preservesEpisodesWithTitlesOrNumberedEntries() {
        assertFalse(NavigationPlaylistGuard.isNavigationList(flag("正片", "预告", "花絮", "第1集", "第2集", "第3集")));
        assertFalse(NavigationPlaylistGuard.isNavigationList(flag("喜剧片", "动作片", "第1集", "第2集", "第3期", "2026-10-01")));
        assertFalse(NavigationPlaylistGuard.isNavigationList(flag("喜剧片", "动作片", "爱情片", "科幻片")));
    }

    @Test
    public void preservesVarietyAndChineseTraditionalEpisodes() {
        assertFalse(NavigationPlaylistGuard.isNavigationList(flag("第1期 上", "第1期 下", "第2期 上", "第2期 下", "会员版", "纯享版")));
        assertFalse(NavigationPlaylistGuard.isNavigationList(flag("喜劇片", "動作片", "愛情片", "劇情片", "戰爭片", "紀錄片")));
    }

    private static Flag categories() {
        Flag flag = new Flag("line");
        List<String> names = List.of("喜剧片", "动作片", "爱情片", "科幻片", "恐怖片", "剧情片", "战争片", "纪录片", "动画片", "电视剧", "国剧");
        List<String> paths = List.of("xijupian", "dongzuopian", "aiqingpian", "kehuanpian", "kongbupian", "juqingpian", "zhanzhengpian", "jilupian", "donghuapian", "dianshiju", "dianshiju/guoju");
        for (int index = 0; index < names.size(); index++) flag.getEpisodes().add(Episode.create(names.get(index), "https://www.66ss.org/" + paths.get(index) + "/"));
        return flag;
    }

    private static Flag flag(String... names) {
        Flag flag = new Flag("line");
        for (String name : names) flag.getEpisodes().add(Episode.create(name, "https://example.invalid/" + name));
        return flag;
    }
}
