package com.fongmi.android.tv.api.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CinemaSourceMerger {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String MD5 = ";md5;";
    private static final String SITES = "sites";
    private static final String PARSES = "parses";
    private static final String SPIDER = "spider";

    private CinemaSourceMerger() {
    }

    public static final class Input {

        private final String name;
        private final String url;
        private final String body;

        public Input(String name, String url, String body) {
            this.name = name == null ? "" : name;
            this.url = url == null ? "" : url;
            this.body = body == null ? "" : body;
        }

        public String name() {
            return name;
        }

        public String url() {
            return url;
        }

        public String body() {
            return body;
        }

        String label() {
            if (!name.isEmpty()) return name;
            if (!url.isEmpty()) return url;
            return "config";
        }
    }

    public static final class Limits {

        public static final Limits DEFAULT = new Limits(2000, 4 * 1024 * 1024, 24);

        private final int maxSites;
        private final int maxOutputBytes;
        private final int maxWarnings;

        public Limits(int maxSites, int maxOutputBytes, int maxWarnings) {
            this.maxSites = Math.max(1, maxSites);
            this.maxOutputBytes = Math.max(1024, maxOutputBytes);
            this.maxWarnings = Math.max(0, maxWarnings);
        }

        public int maxSites() {
            return maxSites;
        }

        public int maxOutputBytes() {
            return maxOutputBytes;
        }

        public int maxWarnings() {
            return maxWarnings;
        }
    }

    public static final class Stats {

        private final List<String> warnings = new ArrayList<>();

        public int inputs;
        public int validInputs;
        public int invalidInputs;
        public String base = "";
        public int siteCandidates;
        public int sitesAdded;
        public int sitesDuplicate;
        public int sitesSkipped;
        public int keysRenamed;
        public int jarsInherited;
        public int parsesAdded;
        public int parsesDuplicate;
        public int parsesSkippedCollision;
        public long outputBytes;
        public boolean truncated;

        public List<String> warnings() {
            return warnings;
        }
    }

    public static final class Result {

        private final boolean ok;
        private final String json;
        private final String error;
        private final Stats stats;

        private Result(boolean ok, String json, String error, Stats stats) {
            this.ok = ok;
            this.json = json;
            this.error = error;
            this.stats = stats;
        }

        public boolean ok() {
            return ok;
        }

        public String json() {
            return json;
        }

        public String error() {
            return error;
        }

        public Stats stats() {
            return stats;
        }
    }

    public static Result merge(List<Input> inputs) {
        return merge(inputs, Limits.DEFAULT);
    }

    public static Result merge(List<Input> inputs, Limits limits) {
        Limits lim = limits == null ? Limits.DEFAULT : limits;
        Stats stats = new Stats();
        List<Input> source = inputs == null ? new ArrayList<>() : inputs;
        stats.inputs = source.size();
        if (source.isEmpty()) return fail("no config inputs", stats);

        List<Input> accepted = new ArrayList<>();
        List<JsonObject> roots = new ArrayList<>();
        for (Input input : source) {
            if (input == null) {
                stats.invalidInputs++;
                continue;
            }
            JsonObject root = parse(input.body());
            if (root == null || root.has("urls") || root.has("msg") || !isComplete(root)) {
                stats.invalidInputs++;
                warn(stats, "invalid config: " + input.label(), lim);
                continue;
            }
            stats.validInputs++;
            accepted.add(input);
            roots.add(root);
        }
        if (accepted.isEmpty()) return fail("no valid config", stats);

        Input baseInput = accepted.get(0);
        JsonObject out = roots.get(0).deepCopy();
        stats.base = baseInput.label();
        JsonArray outSites = new JsonArray();
        JsonArray outParses = new JsonArray();
        Set<String> keys = new LinkedHashSet<>();
        Set<String> signatures = new HashSet<>();
        Map<String, String> parseNames = new HashMap<>();
        Set<String> parseUrls = new HashSet<>();
        String baseSpider = resolveJar(string(out, SPIDER), baseInput.url());
        if (!baseSpider.isEmpty()) out.addProperty(SPIDER, baseSpider);

        int baseSites = 0;
        for (int i = 0; i < accepted.size(); i++) {
            Input input = accepted.get(i);
            JsonObject root = roots.get(i);
            String spider = resolveJar(string(root, SPIDER), input.url());
            String prefix = prefixOf(input);
            boolean first = i == 0;

            appendParses(root, input, outParses, parseNames, parseUrls, stats);

            JsonArray sites = array(root, SITES);
            if (sites != null) {
                for (JsonElement element : sites) {
                    stats.siteCandidates++;
                    if (element == null || !element.isJsonObject()) {
                        stats.sitesSkipped++;
                        continue;
                    }
                    JsonObject site = element.getAsJsonObject();
                    String key = string(site, "key");
                    String api = string(site, "api");
                    Integer type = integer(site, "type");
                    if (key.isEmpty() || api.isEmpty() || type == null) {
                        stats.sitesSkipped++;
                        warn(stats, "site without key/api/type: " + input.label(), lim);
                        continue;
                    }
                    String apiResolved = resolve(api, input.url());
                    boolean explicitJar = !string(site, "jar").isEmpty();
                    String jar = explicitJar ? resolveJar(string(site, "jar"), input.url()) : "";
                    boolean inherited = false;
                    if (jar.isEmpty() && !spider.isEmpty()) {
                        jar = spider;
                        inherited = true;
                    }
                    JsonElement ext = site.get("ext");
                    String extSignature = extKey(ext);
                    if (ext != null && ext.isJsonPrimitive() && ext.getAsJsonPrimitive().isString()) {
                        extSignature = resolve(ext.getAsString(), input.url());
                    }
                    String signature = type + "|" + apiResolved + "|" + extSignature + "|" + jar;
                    if (!signatures.add(signature)) {
                        stats.sitesDuplicate++;
                        continue;
                    }
                    if (outSites.size() >= lim.maxSites()) {
                        stats.truncated = true;
                        warn(stats, "site limit reached", lim);
                        break;
                    }
                    JsonObject copy = site.deepCopy();
                    copy.addProperty("api", apiResolved);
                    if (ext != null && ext.isJsonPrimitive() && ext.getAsJsonPrimitive().isString()) {
                        copy.addProperty("ext", resolve(ext.getAsString(), input.url()));
                    }
                    if (jar.isEmpty()) copy.remove("jar");
                    else copy.addProperty("jar", jar);
                    String playUrl = string(site, "playUrl");
                    if (!playUrl.isEmpty()) copy.addProperty("playUrl", resolve(playUrl, input.url()));
                    String click = string(site, "click");
                    if (!click.isEmpty()) copy.addProperty("click", resolve(click, input.url()));
                    String finalKey = unique(key, prefix, keys);
                    if (!finalKey.equals(key)) stats.keysRenamed++;
                    copy.addProperty("key", finalKey);
                    outSites.add(copy);
                    stats.sitesAdded++;
                    if (inherited) stats.jarsInherited++;
                }
            }
            if (first) baseSites = outSites.size();
            if (stats.truncated) break;
        }

        out.add(SITES, outSites);
        if (outParses.size() > 0) out.add(PARSES, outParses);
        else out.remove(PARSES);

        String json = GSON.toJson(out);
        long bytes = json.getBytes(StandardCharsets.UTF_8).length;
        while (bytes > lim.maxOutputBytes() && outSites.size() > baseSites) {
            outSites.remove(outSites.size() - 1);
            stats.sitesAdded--;
            stats.truncated = true;
            warn(stats, "output limit reached", lim);
            json = GSON.toJson(out);
            bytes = json.getBytes(StandardCharsets.UTF_8).length;
        }
        if (bytes > lim.maxOutputBytes()) return fail("output exceeds limit", stats);
        stats.outputBytes = bytes;
        return new Result(true, json, "", stats);
    }

    private static void appendParses(JsonObject root, Input input, JsonArray out, Map<String, String> names,
                                     Set<String> urls, Stats stats) {
        JsonArray parses = array(root, PARSES);
        if (parses == null) return;
        for (JsonElement element : parses) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject parse = element.getAsJsonObject();
            String name = string(parse, "name");
            String url = resolve(string(parse, "url"), input.url());
            if (url.isEmpty()) continue;
            String known = names.get(name);
            if (known != null) {
                if (known.equals(url)) stats.parsesDuplicate++;
                else stats.parsesSkippedCollision++;
                continue;
            }
            if (!urls.add(url)) {
                stats.parsesDuplicate++;
                continue;
            }
            names.put(name, url);
            JsonObject copy = parse.deepCopy();
            copy.addProperty("url", url);
            out.add(copy);
            stats.parsesAdded++;
        }
    }

    private static String unique(String key, String prefix, Set<String> used) {
        if (used.add(key)) return key;
        String candidate = prefix + "_" + key;
        int index = 2;
        while (!used.add(candidate)) {
            candidate = prefix + "_" + key + "_" + index;
            index++;
        }
        return candidate;
    }

    private static String prefixOf(Input input) {
        StringBuilder builder = new StringBuilder();
        String name = input.name();
        for (int i = 0; i < name.length() && builder.length() < 10; i++) {
            char c = name.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) builder.append(c);
        }
        if (builder.length() >= 2) return builder.toString().toLowerCase(Locale.ROOT);
        return "src" + hash8(name + "|" + input.url());
    }

    private static String hash8(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 4; i++) builder.append(String.format(Locale.ROOT, "%02x", digest[i]));
            return builder.toString();
        } catch (Throwable ignored) {
            return Integer.toHexString(value.hashCode());
        }
    }

    static String resolveJar(String jar, String parent) {
        if (jar == null) return "";
        String value = jar.trim();
        if (value.isEmpty()) return "";
        int index = value.indexOf(MD5);
        if (index < 0) return resolve(value, parent);
        String head = resolve(value.substring(0, index), parent);
        return head + value.substring(index);
    }

    static String resolve(String child, String parent) {
        if (child == null) return "";
        String value = child.trim();
        if (value.isEmpty()) return "";
        if (!isRelative(value)) return value;
        if (parent == null || parent.isEmpty()) return value;
        int scheme = parent.indexOf("://");
        if (scheme < 0) return value;
        if (value.startsWith("//")) return parent.substring(0, scheme) + ":" + value;
        int pathStart = parent.indexOf('/', scheme + 3);
        String authority;
        String dir;
        if (pathStart < 0) {
            authority = parent + "/";
            dir = "";
        } else {
            authority = parent.substring(0, pathStart + 1);
            String rest = parent.substring(pathStart + 1);
            int cut = rest.length();
            int query = rest.indexOf('?');
            if (query >= 0 && query < cut) cut = query;
            int hash = rest.indexOf('#');
            if (hash >= 0 && hash < cut) cut = hash;
            rest = rest.substring(0, cut);
            int last = rest.lastIndexOf('/');
            dir = last < 0 ? "" : rest.substring(0, last);
        }
        List<String> segments = new ArrayList<>();
        for (String segment : dir.split("/")) {
            if (!segment.isEmpty()) segments.add(segment);
        }
        if (value.startsWith("/")) return authority + value.substring(1);
        for (String segment : value.split("/")) {
            if (segment.isEmpty() || ".".equals(segment)) continue;
            if ("..".equals(segment)) {
                if (!segments.isEmpty()) segments.remove(segments.size() - 1);
                continue;
            }
            segments.add(segment);
        }
        return authority + String.join("/", segments);
    }

    private static boolean isRelative(String value) {
        return value.startsWith("./") || value.startsWith("../") || value.startsWith("/");
    }

    private static String extKey(JsonElement ext) {
        if (ext == null || ext.isJsonNull()) return "";
        if (ext.isJsonPrimitive()) {
            try {
                return ext.getAsString().trim();
            } catch (Throwable ignored) {
                return ext.toString();
            }
        }
        return ext.toString();
    }

    private static boolean isComplete(JsonObject root) {
        JsonArray sites = array(root, SITES);
        if (sites == null) return false;
        for (JsonElement element : sites) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject site = element.getAsJsonObject();
            if (!string(site, "key").isEmpty() && !string(site, "api").isEmpty() && integer(site, "type") != null) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject parse(String body) {
        if (body == null || body.trim().isEmpty()) return null;
        try {
            JsonElement element = JsonParser.parseString(body);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static JsonArray array(JsonObject object, String key) {
        if (object == null) return null;
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String string(JsonObject object, String key) {
        if (object == null) return "";
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return "";
        try {
            return element.getAsString().trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static Integer integer(JsonObject object, String key) {
        if (object == null) return null;
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return null;
        try {
            return element.getAsInt();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void warn(Stats stats, String message, Limits limits) {
        if (stats.warnings.size() >= limits.maxWarnings()) return;
        stats.warnings.add(message);
    }

    private static Result fail(String error, Stats stats) {
        return new Result(false, "", error, stats);
    }
}
