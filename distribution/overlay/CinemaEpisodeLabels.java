package com.fongmi.android.tv.ui.custom;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.source.EpisodeLabel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Short, stable chip captions. The raw name stays untouched and is shown above the grid for
 * the focused item, so trimming the caption cannot change playback identity or history.
 */
public final class CinemaEpisodeLabels {

    private static final int RAW_LIMIT = 18;
    private static final Pattern NUMBER_UNIT = Pattern.compile("第\\s*([0-9零〇一二两三四五六七八九十百千]{1,6})\\s*([集话期回])");
    private static final Pattern PART_SUFFIX = Pattern.compile("第\\s*[0-9零〇一二两三四五六七八九十百千]{1,6}\\s*[集话期回]\\s*([上中下])");
    private static final Pattern PART_NUMBER = Pattern.compile("(?i)part[ ._-]*([0-9]{1,2})");

    private CinemaEpisodeLabels() {}

    public static List<String> display(List<Episode> items) {
        List<String> captions = new ArrayList<>(items == null ? 0 : items.size());
        if (items == null) return captions;
        Map<String, Integer> counts = new HashMap<>();
        for (Episode item : items) {
            String caption = compact(item);
            counts.put(caption, counts.getOrDefault(caption, 0) + 1);
            captions.add(caption);
        }
        for (int index = 0; index < captions.size(); index++) {
            if (counts.get(captions.get(index)) > 1) captions.set(index, raw(items.get(index)));
        }
        return captions;
    }

    static String compact(Episode item) {
        String raw = raw(item);
        if (raw.length() <= RAW_LIMIT || item.isGeneratedLabel()) return raw;
        EpisodeLabel label = item.getLabel();
        Matcher number = NUMBER_UNIT.matcher(raw);
        boolean hasNumber = number.find();
        if (!hasNumber && !label.hasAirDate() && label.part().isEmpty()) return raw;
        StringBuilder caption = new StringBuilder();
        if (label.hasAirDate()) caption.append(label.airDate().substring(5).replace('-', '.')).append(' ');
        if (hasNumber) {
            number.reset();
            number.find();
            caption.append('第').append(toInt(number.group(1))).append(number.group(2));
            caption.append(part(raw));
        } else if (!label.part().isEmpty()) {
            caption.append(part(raw));
        }
        String variant = variant(label.variant().isEmpty() ? raw : label.variant());
        if (!variant.isEmpty()) {
            if (caption.length() > 0) caption.append(' ');
            caption.append(variant);
        }
        return caption.length() == 0 ? raw : caption.toString();
    }

    private static String raw(Episode item) {
        if (item == null) return "";
        String value = item.getDesc() + item.getName();
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String part(String raw) {
        Matcher suffix = PART_SUFFIX.matcher(raw);
        if (suffix.find()) return suffix.group(1);
        Matcher part = PART_NUMBER.matcher(raw);
        return part.find() ? "P" + part.group(1) : "";
    }

    private static String variant(String variant) {
        if (variant == null || variant.isEmpty()) return "";
        if (variant.contains("bonus") || variant.contains("加更") || variant.contains("会员")) return "加更";
        if (variant.contains("preview") || variant.contains("花絮")) return "花絮";
        if (variant.contains("member")) return "会员";
        if (variant.contains("pure")) return "纯享";
        return "";
    }

    private static int toInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
        }
        int total = 0;
        int current = 0;
        for (char symbol : value.toCharArray()) {
            int digit = switch (symbol) {
                case '零', '〇' -> 0;
                case '一' -> 1;
                case '二', '两' -> 2;
                case '三' -> 3;
                case '四' -> 4;
                case '五' -> 5;
                case '六' -> 6;
                case '七' -> 7;
                case '八' -> 8;
                case '九' -> 9;
                default -> -1;
            };
            if (digit >= 0) {
                current = digit;
                continue;
            }
            int scale = symbol == '十' ? 10 : symbol == '百' ? 100 : symbol == '千' ? 1000 : 0;
            if (scale == 0) continue;
            total += (current == 0 ? 1 : current) * scale;
            current = 0;
        }
        return total + current;
    }
}
