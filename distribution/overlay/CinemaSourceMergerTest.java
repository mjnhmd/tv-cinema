package com.fongmi.android.tv.api.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CinemaSourceMergerTest {

    private static final String A = "https://a.example/dir/config.json";
    private static final String B = "https://b.example/dir/config.json";

    private static JsonObject site(String key, int type, String api) {
        JsonObject site = new JsonObject();
        site.addProperty("key", key);
        site.addProperty("name", key);
        site.addProperty("type", type);
        site.addProperty("api", api);
        return site;
    }

    private static JsonObject config(String spider, JsonObject... sites) {
        JsonObject root = new JsonObject();
        if (spider != null) root.addProperty("spider", spider);
        JsonArray array = new JsonArray();
        for (JsonObject site : sites) array.add(site);
        root.add("sites", array);
        return root;
    }

    private static List<CinemaSourceMerger.Input> inputs(CinemaSourceMerger.Input... values) {
        List<CinemaSourceMerger.Input> list = new ArrayList<>();
        for (CinemaSourceMerger.Input value : values) list.add(value);
        return list;
    }

    private static JsonObject parse(CinemaSourceMerger.Result result) {
        assertTrue(result.error(), result.ok());
        return JsonParser.parseString(result.json()).getAsJsonObject();
    }

    private static JsonArray sites(CinemaSourceMerger.Result result) {
        return parse(result).getAsJsonArray("sites");
    }

    private static JsonObject site(JsonArray array, int index) {
        return array.get(index).getAsJsonObject();
    }

    @Test
    public void keepsBaseTopLevelAndAppendsSites() {
        JsonObject base = config("https://a.example/spider.jar", site("one", 1, "https://a.example/api.php"));
        JsonArray rules = new JsonArray();
        rules.add("rule-a");
        base.add("rules", rules);
        JsonArray lives = new JsonArray();
        lives.add("live-a");
        base.add("lives", lives);
        JsonObject other = config("https://b.example/spider.jar", site("two", 1, "https://b.example/api.php"));
        JsonArray otherLives = new JsonArray();
        otherLives.add("live-b");
        other.add("lives", otherLives);

        CinemaSourceMerger.Result result = CinemaSourceMerger.merge(inputs(
                new CinemaSourceMerger.Input("alpha", A, base.toString()),
                new CinemaSourceMerger.Input("beta", B, other.toString())));

        JsonObject merged = parse(result);
        assertEquals(2, sites(result).size());
        assertEquals(1, merged.getAsJsonArray("rules").size());
        assertEquals(1, merged.getAsJsonArray("lives").size());
        assertEquals("live-a", merged.getAsJsonArray("lives").get(0).getAsString());
        assertEquals("https://a.example/spider.jar", merged.get("spider").getAsString());
        assertEquals("alpha", result.stats().base);
        assertFalse(result.stats().truncated);
    }

    @Test
    public void renamesCollidingKeysWithStableSourcePrefix() {
        JsonObject base = config(null, site("vod", 1, "https://a.example/api.php"));
        JsonObject other = config(null, site("vod", 1, "https://b.example/api.php"));

        CinemaSourceMerger.Result first = CinemaSourceMerger.merge(inputs(
                new CinemaSourceMerger.Input("alpha", A, base.toString()),
                new CinemaSourceMerger.Input("beta", B, other.toString())));
        CinemaSourceMerger.Result second = CinemaSourceMerger.merge(inputs(
                new CinemaSourceMerger.Input("alpha", A, base.toString()),
                new CinemaSourceMerger.Input("beta", B, other.toString())));

        JsonArray merged = sites(first);
        assertEquals(2, merged.size());
        assertEquals("vod", site(merged, 0).get("key").getAsString());
        assertEquals("beta_vod", site(merged, 1).get("key").getAsString());
        assertEquals(1, first.stats().keysRenamed);
        assertEquals(site(sites(second), 1).get("key").getAsString(), site(merged, 1).get("key").getAsString());
    }

    @Test
    public void inheritsConfigSpiderForJarSites() {
        JsonObject base = config("https://a.example/spider.jar", site("home", 1, "https://a.example/api.php"));
        JsonObject other = config("https://b.example/spider.jar", site("crawler", 3, "com.example.Spider"));

        JsonArray merged = sites(CinemaSourceMerger.merge(inputs(
                new CinemaSourceMerger.Input("alpha", A, base.toString()),
                new CinemaSourceMerger.Input("beta", B, other.toString()))));

        assertEquals("https://b.example/spider.jar", site(merged, 1).get("jar").getAsString());
        assertEquals("https://a.example/spider.jar", site(merged, 0).get("jar").getAsString());
    }

    @Test
    public void keepsSiteJarOverConfigSpider() {
        JsonObject base = config("https://a.example/spider.jar", site("home", 1, "https://a.example/api.php"));
        JsonObject other = config("https://b.example/spider.jar", site("crawler", 3, "com.example.Spider"));
        site(other.getAsJsonArray("sites"), 0).addProperty("jar", "https://c.example/own.jar");

        JsonArray merged = sites(CinemaSourceMerger.merge(inputs(
                new CinemaSourceMerger.Input("alpha", A, base.toString()),
                new CinemaSourceMerger.Input("beta", B, other.toString()))));

        assertEquals("https://c.example/own.jar", site(merged, 1).get("jar").getAsString());
        assertEquals(1, CinemaSourceMerger.merge(inputs(
                new CinemaSourceMerger.Input("alpha", A, base.toString()),
                new CinemaSourceMerger.Input("beta", B, other.toString()))).stats().jarsInherited);
    }

    @Test
    public void preservesSpiderForBuiltinAndScriptApis() {
        JsonObject base = config("https://a.example/spider.jar", site("home", 1, "https://a.example/api.php"));
        JsonObject other = config("https://b.example/spider.jar",
                site("builtin", 3, "csp_Guard"),
                site("script", 3, "https://b.example/crawler.js"),
                site("python", 3, "https://b.example/crawler.py"));

        JsonArray merged = sites(CinemaSourceMerger.merge(inputs(
                new CinemaSourceMerger.Input("alpha", A, base.toString()),
                new CinemaSourceMerger.Input("beta", B, other.toString()))));

        assertEquals("https://b.example/spider.jar", site(merged, 1).get("jar").getAsString());
        assertEquals("https://b.example/spider.jar", site(merged, 2).get("jar").getAsString());
        assertEquals("https://b.example/spider.jar", site(merged, 3).get("jar").getAsString());
    }

    @Test
    public void preservesMd5Suffix() {
        JsonObject base = config("https://a.example/spider.jar;md5;aaaa",
                site("home", 1, "https://a.example/api.php"));
        JsonObject other = config("https://b.example/spider.jar;md5;bbbb", site("crawler", 3, "com.example.Spider"));

        CinemaSourceMerger.Result result = CinemaSourceMerger.merge(inputs(
                new CinemaSourceMerger.Input("alpha", A, base.toString()),
                new CinemaSourceMerger.Input("beta", B, other.toString())));

        JsonObject merged = parse(result);
        assertEquals("https://a.example/spider.jar;md5;aaaa", merged.get("spider").getAsString());
        assertEquals("https://b.example/spider.jar;md5;bbbb", site(sites(result), 1).get("jar").getAsString());
    }

    @Test
    public void resolvesRelativeReferencesOnly() {
        JsonObject base = config(null, site("home", 1, "./api.php"));
        site(base.getAsJsonArray("sites"), 0).addProperty("jar", "../jars/x.jar");
        site(base.getAsJsonArray("sites"), 0).addProperty("playUrl", "/play/1");
        site(base.getAsJsonArray("sites"), 0).addProperty("click", "click_handler");
        JsonObject ext = new JsonObject();
        ext.addProperty("mode", "./keep");
        ext.addProperty("api", "csp_Guard");
        site(base.getAsJsonArray("sites"), 0).add("ext", ext);

        JsonObject merged = site(sites(CinemaSourceMerger.merge(inputs(
                new CinemaSourceMerger.Input("alpha", A, base.toString())))), 0);

        assertEquals("https://a.example/dir/api.php", merged.get("api").getAsString());
        assertEquals("https://a.example/jars/x.jar", merged.get("jar").getAsString());
        assertEquals("https://a.example/play/1", merged.get("playUrl").getAsString());
        assertEquals("click_handler", merged.get("click").getAsString());
        assertEquals("./keep", merged.getAsJsonObject("ext").get("mode").getAsString());
        assertEquals("csp_Guard", merged.getAsJsonObject("ext").get("api").getAsString());
    }

    @Test
    public void resolvesStringExtFromItsOwnSourceDirectory() {
        JsonObject first = site("one", 3, "csp_A");
        first.addProperty("ext", "./rules/a.json");
        JsonObject second = site("two", 3, "csp_A");
        second.addProperty("ext", "./rules/a.json");
        JsonArray merged = sites(CinemaSourceMerger.merge(inputs(
                new CinemaSourceMerger.Input("alpha", A, config("https://a.example/spider.jar", first).toString()),
                new CinemaSourceMerger.Input("beta", B, config("https://b.example/spider.jar", second).toString()))));
        assertEquals("https://a.example/dir/rules/a.json", site(merged, 0).get("ext").getAsString());
        assertEquals("https://b.example/dir/rules/a.json", site(merged, 1).get("ext").getAsString());
    }

    @Test
    public void keepsDistinctRelativeRulesEvenWhenApiAndSpiderMatch() {
        JsonObject first = site("one", 3, "csp_A");
        first.addProperty("ext", "./rules.json");
        JsonObject second = site("two", 3, "csp_A");
        second.addProperty("ext", "./rules.json");
        String jar = "https://shared.example/spider.jar";
        JsonArray merged = sites(CinemaSourceMerger.merge(inputs(
                new CinemaSourceMerger.Input("alpha", A, config(jar, first).toString()),
                new CinemaSourceMerger.Input("beta", B, config(jar, second).toString()))));
        assertEquals(2, merged.size());
        assertEquals("https://a.example/dir/rules.json", site(merged, 0).get("ext").getAsString());
        assertEquals("https://b.example/dir/rules.json", site(merged, 1).get("ext").getAsString());
    }

    @Test
    public void rejectsDepotEvenWithSites() {
        JsonObject base = config(null, site("one", 1, "https://a.example/api.php"));
        base.add("urls", new JsonArray());
        assertFalse(CinemaSourceMerger.merge(inputs(
                new CinemaSourceMerger.Input("depot", A, base.toString()))).ok());
    }

    @Test
    public void skipsInvalidJsonAndUsesNextValidConfig() {
        JsonObject other = config(null, site("two", 1, "https://b.example/api.php"));

        CinemaSourceMerger.Result result = CinemaSourceMerger.merge(inputs(
                new CinemaSourceMerger.Input("broken", A, "{not json"),
                new CinemaSourceMerger.Input("beta", B, other.toString())));

        assertEquals(1, result.stats().invalidInputs);
        assertEquals(1, result.stats().validInputs);
        assertEquals("beta", result.stats().base);
        assertEquals(1, sites(result).size());
    }

    @Test
    public void rejectsMergingWithoutValidConfig() {
        CinemaSourceMerger.Result result = CinemaSourceMerger.merge(inputs(
                new CinemaSourceMerger.Input("broken", A, "{\"sites\":[]}")));

        assertFalse(result.ok());
        assertEquals("", result.json());
        assertTrue(result.error().length() > 0);
    }

    @Test
    public void deduplicatesByTypeApiExtAndJar() {
        JsonObject base = config(null, site("one", 1, "https://a.example/api.php"));
        JsonObject other = config(null, site("two", 1, "https://a.example/api.php"));

        CinemaSourceMerger.Result result = CinemaSourceMerger.merge(inputs(
                new CinemaSourceMerger.Input("alpha", A, base.toString()),
                new CinemaSourceMerger.Input("beta", B, other.toString())));

        assertEquals(1, sites(result).size());
        assertEquals(1, result.stats().sitesDuplicate);
    }

    @Test
    public void keepsSameApiWithDifferentJar() {
        JsonObject base = config("https://a.example/spider.jar", site("one", 3, "com.example.Spider"));
        JsonObject other = config("https://b.example/spider.jar", site("two", 3, "com.example.Spider"));

        CinemaSourceMerger.Result result = CinemaSourceMerger.merge(inputs(
                new CinemaSourceMerger.Input("alpha", A, base.toString()),
                new CinemaSourceMerger.Input("beta", B, other.toString())));

        JsonArray merged = sites(result);
        assertEquals(2, merged.size());
        assertEquals("https://a.example/spider.jar", site(merged, 0).get("jar").getAsString());
        assertEquals("https://b.example/spider.jar", site(merged, 1).get("jar").getAsString());
        assertEquals(0, result.stats().sitesDuplicate);
    }

    @Test
    public void deduplicatesParsesAndCountsNameCollisions() {
        JsonObject base = config(null, site("one", 1, "https://a.example/api.php"));
        JsonArray parses = new JsonArray();
        parses.add(parseEntry("Json", "https://a.example/p1"));
        base.add("parses", parses);
        JsonObject other = config(null, site("two", 1, "https://b.example/api.php"));
        JsonArray otherParses = new JsonArray();
        otherParses.add(parseEntry("Json", "https://b.example/p2"));
        otherParses.add(parseEntry("Json", "https://a.example/p1"));
        other.add("parses", otherParses);

        CinemaSourceMerger.Result result = CinemaSourceMerger.merge(inputs(
                new CinemaSourceMerger.Input("alpha", A, base.toString()),
                new CinemaSourceMerger.Input("beta", B, other.toString())));

        JsonArray merged = parse(result).getAsJsonArray("parses");
        assertEquals(1, merged.size());
        assertEquals("https://a.example/p1", merged.get(0).getAsJsonObject().get("url").getAsString());
        assertEquals(1, result.stats().parsesSkippedCollision);
        assertEquals(1, result.stats().parsesDuplicate);
    }

    @Test
    public void enforcesSiteLimit() {
        JsonObject base = config(null,
                site("s1", 1, "https://a.example/1.php"),
                site("s2", 1, "https://a.example/2.php"),
                site("s3", 1, "https://a.example/3.php"));
        JsonObject other = config(null,
                site("s4", 1, "https://b.example/4.php"),
                site("s5", 1, "https://b.example/5.php"));

        CinemaSourceMerger.Result result = CinemaSourceMerger.merge(inputs(
                new CinemaSourceMerger.Input("alpha", A, base.toString()),
                new CinemaSourceMerger.Input("beta", B, other.toString())),
                new CinemaSourceMerger.Limits(2, 1024 * 1024, 8));

        assertTrue(result.stats().truncated);
        assertEquals(2, sites(result).size());
        assertEquals(2, result.stats().sitesAdded);
    }

    @Test
    public void skipsSitesWithoutRequiredFields() {
        JsonObject base = config(null, site("one", 1, "https://a.example/api.php"));
        JsonObject broken = new JsonObject();
        broken.addProperty("key", "noApi");
        base.getAsJsonArray("sites").add(broken);

        CinemaSourceMerger.Result result = CinemaSourceMerger.merge(inputs(
                new CinemaSourceMerger.Input("alpha", A, base.toString())));

        assertEquals(1, sites(result).size());
        assertEquals(1, result.stats().sitesSkipped);
    }

    private static JsonObject parseEntry(String name, String url) {
        JsonObject entry = new JsonObject();
        entry.addProperty("name", name);
        entry.addProperty("type", 1);
        entry.addProperty("url", url);
        return entry;
    }
}
