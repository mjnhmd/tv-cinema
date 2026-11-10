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
    private static final int VERSION = 3;
    private static final String DEFAULT = "饭太硬";
    private static final String GROUP_URL = "cinema://fantai";
    private static final String LEGACY_DEFAULT = "https://0.12yue.de5.net/tvbox/%E9%A5%AD%E5%A4%AA%E7%A1%AC.json";
    private static final String VOD = "vod";
    private static final String LIVE = "live";

    private CinemaSources() {
    }

    public static synchronized void install() {
        migrate();
        if (Prefers.getInt(KEY_SEEDED) >= VERSION) return;
        if (hasUserConfig()) {
            Prefers.put(KEY_SEEDED, VERSION);
            return;
        }
        Seed seed = fantai();
        if (seed == null) return;
        try {
            AppDatabase.get().runInTransaction(() -> {
                long time = System.currentTimeMillis();
                Config vod = new Config().type(BaseConfig.VOD).url(seed.groupUrl()).name(DEFAULT);
                Config live = new Config().type(BaseConfig.LIVE).url(seed.primary()).name(DEFAULT + " · 直播");
                vod.setTime(time);
                live.setTime(time);
                if (vod.insert().getId() <= 0 || live.insert().getId() <= 0) throw new IllegalStateException();
            });
        } catch (RuntimeException ignored) {
            return;
        }
        Prefers.put("config_" + BaseConfig.VOD, seed.groupUrl());
        Prefers.put("config_" + BaseConfig.LIVE, seed.primary());
        Prefers.put(KEY_SEEDED, VERSION);
    }

    private static void migrate() {
        Seed seed = fantai();
        if (seed == null) return;
        rewrite(BaseConfig.VOD, LEGACY_DEFAULT, seed.groupUrl());
        rewrite(BaseConfig.LIVE, LEGACY_DEFAULT, seed.primary());
    }

    private static void rewrite(int type, String from, String to) {
        if (from.equals(to)) return;
        try {
            Config legacy = AppDatabase.get().getConfigDao().find(from, type);
            if (legacy == null || legacy.getId() <= 0) return;
            Config existing = AppDatabase.get().getConfigDao().find(to, type);
            if (existing != null && existing.getId() > 0) AppDatabase.get().getConfigDao().delete(from, type);
            else legacy.url(to).save();
            if (from.equals(Prefers.getString("config_" + type))) Prefers.put("config_" + type, to);
        } catch (RuntimeException ignored) {
        }
    }

    public static synchronized boolean needsConsent() {
        return false;
    }

    public static synchronized void skip() {
        Prefers.put(KEY_SEEDED, VERSION);
    }

    public static List<Entry> entries() {
        return seeds().stream().map(seed -> new Entry(seed.type(), seed.name(), seed.entryUrl())).toList();
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

    public static boolean isGroupUrl(String url) {
        return GROUP_URL.equals(url);
    }

    public static List<Endpoint> groupEndpoints() {
        Seed seed = fantai();
        return seed == null ? List.of() : seed.endpoints();
    }

    private static Seed fantai() {
        return seeds().stream().filter(item -> DEFAULT.equals(item.name()) && item.type() == BaseConfig.VOD).findFirst().orElse(null);
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
            String name = Json.safeString(item, "name");
            List<Endpoint> endpoints = endpoints(item);
            if (type < 0 || endpoints.isEmpty() || !seen.add(name)) continue;
            result.add(new Seed(type, name, endpoints));
        }
        return result;
    }

    private static List<Endpoint> endpoints(JsonObject item) {
        List<Endpoint> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        JsonElement list = item.get("endpoints");
        if (list != null && list.isJsonArray()) {
            for (JsonElement element : list.getAsJsonArray()) {
                JsonObject endpoint = Json.safeObject(element);
                String url = Json.safeString(endpoint, "url");
                boolean insecure = endpoint.has("insecure") && endpoint.get("insecure").getAsBoolean();
                if (!allowed(url, insecure) || !seen.add(url)) continue;
                result.add(new Endpoint(Json.safeString(endpoint, "label"), url));
            }
            return result;
        }
        String url = Json.safeString(item, "url");
        if (allowed(url, false)) result.add(new Endpoint(item.has("name") ? Json.safeString(item, "name") : url, url));
        return result;
    }

    private static boolean allowed(String url, boolean insecure) {
        return url.startsWith("https://") || (insecure && url.startsWith("http://"));
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

    public record Endpoint(String label, String url) {

        public String label() {
            return label == null || label.isEmpty() ? url : label;
        }
    }

    private record Seed(int type, String name, List<Endpoint> endpoints) {

        String primary() {
            return endpoints.stream().filter(item -> item.url().startsWith("https://")).map(Endpoint::url).findFirst()
                    .orElse(endpoints.get(0).url());
        }

        String entryUrl() {
            return endpoints.size() > 1 ? GROUP_URL : endpoints.get(0).url();
        }

        String groupUrl() {
            return GROUP_URL;
        }
    }

    public record Entry(int type, String name, String url) {

        public boolean isVod() {
            return type == BaseConfig.VOD;
        }
    }
}
