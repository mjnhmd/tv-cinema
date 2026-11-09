package com.fongmi.android.tv.ui.home;

import com.fongmi.android.tv.bean.Vod;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Small, bounded cache of the last successfully displayed hero items. */
public final class CinemaHeroSnapshot {

    static final int SCHEMA = 1;
    static final int MAX_ITEMS = 5;
    static final long MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;
    private static final int MAX_TEXT = 120;
    private static final int MAX_CONTENT = 240;
    private static final int MAX_URL = 2048;
    private static final String KEY = "cinema_hero_snapshot_";
    private static final String SAVED_AT = "_saved_at";

    private CinemaHeroSnapshot() {
    }

    public static List<Vod> read(int category) {
        return decode(Prefers.getString(KEY + category),
                Prefers.getLong(KEY + category + SAVED_AT), System.currentTimeMillis());
    }

    public static void write(int category, List<Vod> items) {
        String json = encode(items);
        if (json.isEmpty()) return;
        if (!json.equals(Prefers.getString(KEY + category))) Prefers.put(KEY + category, json);
        Prefers.put(KEY + category + SAVED_AT, System.currentTimeMillis());
    }

    static String encode(List<Vod> items) {
        if (items == null || items.isEmpty()) return "";
        JsonArray array = new JsonArray();
        Set<String> ids = new LinkedHashSet<>();
        for (Vod item : items) {
            if (item == null || array.size() >= MAX_ITEMS) break;
            String id = trim(item.getId(), MAX_TEXT);
            String name = trim(item.getName(), MAX_TEXT);
            String artwork = trim(item.getBackdrop(), MAX_URL);
            if (id.isEmpty() || name.isEmpty() || !isHttps(artwork) || !ids.add(id)) continue;
            JsonObject object = new JsonObject();
            object.addProperty("id", id);
            object.addProperty("name", name);
            object.addProperty("artwork", artwork);
            object.addProperty("type", trim(item.getTypeName(), MAX_TEXT));
            object.addProperty("year", trim(item.getYear(), MAX_TEXT));
            object.addProperty("remarks", trim(item.getRemarks(), MAX_TEXT));
            object.addProperty("content", content(item));
            object.addProperty("rating", trim(item.getContentRating(), MAX_TEXT));
            String pic = trim(item.getPic(), MAX_URL);
            if (isHttps(pic)) object.addProperty("pic", pic);
            array.add(object);
        }
        if (array.size() == 0) return "";
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        root.add("items", array);
        return root.toString();
    }

    static List<Vod> decode(String raw, long savedAt, long now) {
        List<Vod> items = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return items;
        try {
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            if (root.get("schema").getAsInt() != SCHEMA) return items;
            if (savedAt <= 0 || now - savedAt > MAX_AGE_MS) return items;
            for (JsonElement element : root.getAsJsonArray("items")) {
                if (items.size() >= MAX_ITEMS) break;
                Vod item = parse(element.getAsJsonObject());
                if (item != null) items.add(item);
            }
        } catch (RuntimeException ignored) {
        }
        return items;
    }

    private static Vod parse(JsonObject object) {
        String id = string(object, "id");
        String name = string(object, "name");
        String artwork = string(object, "artwork");
        if (id.isEmpty() || name.isEmpty() || !isHttps(artwork)) return null;
        Vod item = new Vod();
        item.setId(id);
        item.setName(name);
        item.setBackdrop(artwork);
        item.setTypeName(string(object, "type"));
        item.setYear(string(object, "year"));
        item.setRemarks(string(object, "remarks"));
        item.setContent(string(object, "content"));
        item.setContentRating(string(object, "rating"));
        String pic = string(object, "pic");
        if (isHttps(pic)) item.setPic(pic);
        return item;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : trim(value.getAsString(), MAX_TEXT);
    }

    private static String content(Vod item) {
        try {
            return trim(item.getContent(), MAX_CONTENT);
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static String trim(String value, int limit) {
        if (value == null) return "";
        String text = value.trim();
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private static boolean isHttps(String value) {
        return value != null && value.startsWith("https://") && value.length() > 8;
    }
}
