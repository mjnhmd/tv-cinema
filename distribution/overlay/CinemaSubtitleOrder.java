package com.fongmi.android.tv.player.track;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class CinemaSubtitleOrder {
    private static final Pattern SIMPLIFIED = Pattern.compile("(^|[^a-z])(chs|zhs|zh[-_]hans|zh[-_]cn|zh[-_]sg|simplified|gb2312|gbk)([^a-z]|$)");
    private static final Pattern TRADITIONAL = Pattern.compile("(^|[^a-z])(cht|zht|zh[-_]hant|zh[-_]tw|zh[-_]hk|traditional|big5)([^a-z]|$)");
    private static final Pattern CHINESE = Pattern.compile("(^|[^a-z])(zh|chi|zho|chinese|mandarin|cmn|cantonese|yue)([^a-z]|$)");

    private CinemaSubtitleOrder() {}

    public static int rank(String language, String title) {
        String value = ((language == null ? "" : language) + " " + (title == null ? "" : title)).toLowerCase(Locale.ROOT);
        if (value.contains("简") || value.contains("簡") || SIMPLIFIED.matcher(value).find()) return 0;
        if (value.contains("繁") || TRADITIONAL.matcher(value).find()) return 2;
        if (CHINESE.matcher(value).find() || value.contains("中文") || value.contains("中字")
                || value.contains("国语") || value.contains("國語") || value.contains("双语") || value.contains("雙語")) return 1;
        return 3;
    }

    public static <T> List<T> sorted(List<T> items, Function<T, String> language, Function<T, String> title) {
        List<T> result = new ArrayList<>(items);
        result.sort(Comparator.comparingInt(item -> rank(language.apply(item), title.apply(item))));
        return result;
    }
}
