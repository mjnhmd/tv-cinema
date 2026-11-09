package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.db.AppDatabase;
import com.github.catvod.utils.Asset;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CinemaSources {

    private static final String ASSET = "cinema/sources.json";
    private static final String KEY_SEEDED = "cinema_sources_seeded_version";
    private static final int VERSION = 2;
    private static final String DEFAULT = "饭太硬";
    private static final String VOD = "vod";
    private static final String LIVE = "live";

    private CinemaSources() {
    }

    public static synchronized void install() {
        if (Prefers.getInt(KEY_SEEDED) >= VERSION) return;
        if (hasUserConfig()) {
            Prefers.put(KEY_SEEDED, VERSION);
            return;
        }
        Seed seed = seeds().stream().filter(item -> DEFAULT.equals(item.name()) && item.type() == BaseConfig.VOD).findFirst().orElse(null);
        if (seed == null) return;
        try {
            AppDatabase.get().runInTransaction(() -> {
                long time = System.currentTimeMillis();
                Config vod = new Config().type(BaseConfig.VOD).url(seed.url()).name(DEFAULT);
                Config live = new Config().type(BaseConfig.LIVE).url(seed.url()).name(DEFAULT + " · 直播");
                vod.setTime(time);
                live.setTime(time);
                if (vod.insert().getId() <= 0 || live.insert().getId() <= 0) throw new IllegalStateException();
            });
        } catch (RuntimeException ignored) {
            return;
        }
        Prefers.put("config_" + BaseConfig.VOD, seed.url());
        Prefers.put("config_" + BaseConfig.LIVE, seed.url());
        Prefers.put(KEY_SEEDED, VERSION);
    }

    public static synchronized boolean needsConsent() {
        return false;
    }

    public static synchronized void skip() {
        Prefers.put(KEY_SEEDED, VERSION);
    }

    public static List<Entry> entries() {
        return seeds().stream().map(seed -> new Entry(seed.type(), seed.name(), seed.url())).toList();
    }

    public static List<Entry> catalogEntries() {
        List<Entry> result = new ArrayList<>(entries());
        if (CinemaDiscovery.hasCandidate()) {
            result.add(0, new Entry(BaseConfig.VOD, "手动抓取 · 合并片源", CinemaDiscovery.candidateUrl()));
        }
        return result;
    }

    public static Config activate(Entry entry) {
        return Config.find(entry.url(), entry.type()).name(entry.name()).update();
    }

    public static boolean isAdded(Entry entry) {
        return Config.getAll(entry.type()).stream().anyMatch(item -> entry.url().equals(item.getUrl()));
    }

    public static boolean isActive(Entry entry) {
        Config current = entry.isVod() ? Config.vod() : Config.live();
        return entry.url().equals(current.getUrl());
    }

    private static boolean hasUserConfig() {
        return hasConfig(BaseConfig.VOD)
                || hasConfig(BaseConfig.LIVE)
                || !Prefers.getString("config_" + BaseConfig.VOD).isEmpty()
                || !Prefers.getString("config_" + BaseConfig.LIVE).isEmpty();
    }

    private static boolean hasConfig(int type) {
        return Config.getAll(type).stream().anyMatch(item -> !item.isEmpty());
    }

    private static List<Seed> seeds() {
        List<Seed> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonElement element : sources()) {
            JsonObject item = Json.safeObject(element);
            int type = type(Json.safeString(item, "type"));
            String url = Json.safeString(item, "url");
            String name = Json.safeString(item, "name");
            if (type < 0 || !url.startsWith("https://") || !seen.add(url)) continue;
            result.add(new Seed(type, url, name));
        }
        return result;
    }

    private static List<JsonElement> sources() {
        try {
            return Json.safeListElement(Json.parse(Asset.read(ASSET)).getAsJsonObject(), "sources");
        } catch (Exception e) {
            return List.of();
        }
    }

    private static int type(String value) {
        return switch (value) {
            case VOD -> BaseConfig.VOD;
            case LIVE -> BaseConfig.LIVE;
            default -> -1;
        };
    }

    private record Seed(int type, String url, String name) {
    }

    public record Entry(int type, String name, String url) {

        public boolean isVod() {
            return type == BaseConfig.VOD;
        }
    }
}
