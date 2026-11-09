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
    private static final int VERSION = 1;
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
        List<Seed> seeds = seeds();
        boolean hasVod = seeds.stream().anyMatch(seed -> seed.type() == BaseConfig.VOD);
        boolean hasLive = seeds.stream().anyMatch(seed -> seed.type() == BaseConfig.LIVE);
        if (!hasVod || !hasLive) return;
        Config[] first = new Config[1];
        Config[] live = new Config[1];
        try {
            AppDatabase.get().runInTransaction(() -> {
                long time = System.currentTimeMillis();
                for (Seed seed : seeds) {
                    Config config = new Config().type(seed.type()).url(seed.url()).name(seed.name());
                    config.setTime(time--);
                    config.insert();
                    if (config.getId() < 0) throw new IllegalStateException();
                    if (seed.type() == BaseConfig.VOD && first[0] == null) first[0] = config;
                    if (seed.type() == BaseConfig.LIVE && live[0] == null) live[0] = config;
                }
                if (first[0] == null || live[0] == null) throw new IllegalStateException();
            });
        } catch (RuntimeException e) {
            return;
        }
        Prefers.put("config_" + BaseConfig.VOD, first[0].getUrl());
        Prefers.put("config_" + BaseConfig.LIVE, live[0].getUrl());
        Prefers.put(KEY_SEEDED, VERSION);
    }

    public static synchronized boolean needsConsent() {
        return Prefers.getInt(KEY_SEEDED) < VERSION && !hasUserConfig();
    }

    public static synchronized void skip() {
        Prefers.put(KEY_SEEDED, VERSION);
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
}
