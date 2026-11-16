package com.fongmi.android.tv.utils;

import android.icu.text.AlphabeticIndex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CinemaLocalSearch {
    private record Entry(String title, String normalized, String initials) {}
    private static final AlphabeticIndex.ImmutableIndex<Void> INDEX = new AlphabeticIndex<Void>(Locale.SIMPLIFIED_CHINESE)
            .addLabels(Locale.ENGLISH).buildImmutableIndex();
    private final List<Entry> entries;

    public CinemaLocalSearch(List<String> titles) {
        Map<String, Entry> unique = new LinkedHashMap<>();
        for (String title : titles) {
            if (title == null || title.isBlank()) continue;
            String trimmed = title.trim();
            unique.putIfAbsent(trimmed, new Entry(trimmed, trimmed.toLowerCase(Locale.ROOT), initials(trimmed)));
            if (unique.size() >= 300) break;
        }
        entries = List.copyOf(unique.values());
    }

    public List<String> find(String query) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        boolean abbreviations = normalized.matches("[a-z0-9]+");
        for (boolean exactText : new boolean[]{true, false}) {
            for (Entry entry : entries) {
                boolean contains = entry.normalized.contains(normalized);
                if (exactText ? contains : abbreviations && !contains && entry.initials.contains(normalized)) result.add(entry.title);
                if (result.size() == 12) return result;
            }
        }
        return result;
    }

    public static String initials(String title) {
        StringBuilder result = new StringBuilder();
        title.codePoints().forEach(code -> {
            if (code < 128 && Character.isLetterOrDigit(code)) result.appendCodePoint(Character.toLowerCase(code));
            else if (Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN) {
                AlphabeticIndex.Bucket<Void> bucket = INDEX.getBucket(INDEX.getBucketIndex(new String(Character.toChars(code))));
                String label = bucket == null ? "" : bucket.getLabel();
                if (label.length() == 1 && label.charAt(0) >= 'A' && label.charAt(0) <= 'Z') result.append(label.toLowerCase(Locale.ROOT));
            }
        });
        return result.toString();
    }
}
