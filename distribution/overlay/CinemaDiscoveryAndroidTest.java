package com.fongmi.android.tv.api.config;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;

import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.ui.activity.SettingActivity;
import com.fongmi.android.tv.ui.custom.JetStreamSettingView;
import com.fongmi.android.tv.test.NativeUiEvidence;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Prefers;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class CinemaDiscoveryAndroidTest {
    private static final String KEY = "cinema_merged_candidate_slot";
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final List<File> files = new ArrayList<>();
    private final List<byte[]> originalFiles = new ArrayList<>();
    private List<Config> originalConfigs;
    private String originalSelection;
    private int originalSlot;
    private MockWebServer server;
    private SettingActivity activity;

    @Before public void setup() throws Exception {
        assertEquals("com.fongmi.android.tv.sourceprobe", instrumentation.getTargetContext().getPackageName());
        originalConfigs = Config.getAll(BaseConfig.VOD);
        originalSelection = Prefers.getString("config_0");
        originalSlot = Prefers.getInt(KEY, 0);
        for (String name : List.of("cinema_merged_slot0.json", "cinema_merged_slot1.json")) {
            File file = Path.files(name);
            files.add(file);
            originalFiles.add(file.isFile() ? Files.readAllBytes(file.toPath()) : null);
        }
        server = new MockWebServer();
        server.start();
    }

    @After public void cleanup() throws Exception {
        if (activity != null) instrumentation.runOnMainSync(activity::finish);
        server.shutdown();
        for (int index = 0; index < files.size(); index++) {
            if (originalFiles.get(index) == null) Files.deleteIfExists(files.get(index).toPath());
            else Files.write(files.get(index).toPath(), originalFiles.get(index));
        }
        for (File file : files) {
            String url = "cinema://" + file.getName();
            if (originalConfigs.stream().noneMatch(config -> url.equals(config.getUrl()))) {
                Config.delete(url, BaseConfig.VOD);
            }
        }
        originalConfigs.forEach(Config::save);
        Prefers.put(KEY, originalSlot);
        Prefers.put("config_0", originalSelection);
        VodConfig.get().init();
    }

    @Test(timeout = 30000)
    public void manualFetchAddsOneCandidateAndFailureKeepsItWithoutSwitching() throws Exception {
        String current = Config.vod().getUrl();
        assertEquals(6, CinemaSources.entries().size());
        assertEquals(0, server.getRequestCount());
        server.enqueue(new MockResponse().setBody("{\"sites\":[{\"key\":\"one\",\"name\":\"测试\","
                + "\"type\":3,\"api\":\"csp_Fixture\",\"jar\":\"./never-run.jar\"}]}"));
        assertTrue(fetch());
        assertEquals(1, server.getRequestCount());
        assertEquals("/config.json", server.takeRequest().getPath());
        assertTrue(CinemaDiscovery.hasCandidate());
        assertEquals(7, CinemaSources.catalogEntries().size());
        assertEquals(current, Config.vod().getUrl());
        String candidate = CinemaDiscovery.candidateContent(CinemaDiscovery.candidateUrl());
        server.enqueue(new MockResponse().setResponseCode(503));
        assertFalse(fetch());
        assertEquals(candidate, CinemaDiscovery.candidateContent(CinemaDiscovery.candidateUrl()));
        assertEquals(current, Config.vod().getUrl());
    }

    @Test(timeout = 30000)
    public void settingsShowsManualActionAndCandidateIsSelectableInBuiltinCatalog() throws Exception {
        String current = Config.vod().getUrl();
        activity = (SettingActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), SettingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        UiDevice device = UiDevice.getInstance(instrumentation);
        instrumentation.waitForIdleSync();
        assertTrue("Manual action is visible in source settings", device.hasObject(By.text("手动抓取片源")));
        NativeUiEvidence.capture("source-settings-manual-action");
        instrumentation.runOnMainSync(() -> activity.onSettingAction(JetStreamSettingView.KEY_DISCOVER_SOURCES));
        instrumentation.waitForIdleSync();
        assertTrue(device.hasObject(By.text("开始抓取")));
        assertEquals("Showing the action does not fetch", 0, server.getRequestCount());
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        server.enqueue(new MockResponse().setBody("{\"sites\":[{\"key\":\"one\",\"name\":\"测试\","
                + "\"type\":1,\"api\":\"" + server.url("/api") + "\"}]}"));
        assertTrue(fetch());
        String firstUrl = CinemaDiscovery.candidateUrl();
        String firstBody = CinemaDiscovery.candidateContent(firstUrl);
        instrumentation.runOnMainSync(() -> activity.onSettingAction(JetStreamSettingView.KEY_BUILTIN_SOURCES));
        instrumentation.waitForIdleSync();
        assertTrue("Merged candidate is in the same list",
                device.hasObject(By.textStartsWith("手动抓取 · 合并片源")));
        assertEquals("The newest candidate is immediately visible", firstUrl, CinemaSources.catalogEntries().get(0).url());
        assertEquals("Opening the catalog does not switch", current, Config.vod().getUrl());
        NativeUiEvidence.capture("source-catalog-merged-option");
        device.findObject(By.textStartsWith("手动抓取 · 合并片源")).click();
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (!firstUrl.equals(Config.vod().getUrl()) && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
        assertEquals("Explicit selection enables the candidate", firstUrl, Config.vod().getUrl());
        server.enqueue(new MockResponse().setBody("{\"sites\":[{\"key\":\"two\",\"name\":\"新的测试\","
                + "\"type\":1,\"api\":\"" + server.url("/other") + "\"}]}"));
        assertTrue(fetch());
        assertFalse("A new candidate uses the other bounded slot", firstUrl.equals(CinemaDiscovery.candidateUrl()));
        assertEquals("Refetch does not replace the active candidate", firstBody, CinemaDiscovery.candidateContent(firstUrl));
        assertEquals(firstUrl, Config.vod().getUrl());
        assertEquals(7, CinemaSources.catalogEntries().size());
    }

    @Test(timeout = 30000)
    public void fetchingTwoProvidersKeepsTheirOwnRelativeRulesWithoutDownloadingPlugins() throws Exception {
        String body = "{\"spider\":\"" + server.url("/never-run.jar")
                + "\",\"sites\":[{\"key\":\"same\",\"name\":\"测试\",\"type\":3,"
                + "\"api\":\"csp_Fixture\",\"ext\":\"./rules.json\"}]}";
        server.enqueue(new MockResponse().setBody(body));
        server.enqueue(new MockResponse().setBody(body));
        assertTrue(fetch(List.of(
                new CinemaSources.Entry(BaseConfig.VOD, "甲", server.url("/a/config.json").toString()),
                new CinemaSources.Entry(BaseConfig.VOD, "乙", server.url("/b/config.json").toString()))));
        assertEquals("Only configuration text is fetched", 2, server.getRequestCount());
        JsonArray sites = JsonParser.parseString(CinemaDiscovery.candidateContent(CinemaDiscovery.candidateUrl()))
                .getAsJsonObject().getAsJsonArray("sites");
        assertEquals(2, sites.size());
        assertEquals(server.url("/a/rules.json").toString(), sites.get(0).getAsJsonObject().get("ext").getAsString());
        assertEquals(server.url("/b/rules.json").toString(), sites.get(1).getAsJsonObject().get("ext").getAsString());
    }

    private boolean fetch() throws Exception {
        return fetch(List.of(new CinemaSources.Entry(BaseConfig.VOD, "测试订阅",
                server.url("/config.json").toString())));
    }

    private boolean fetch(List<CinemaSources.Entry> entries) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        assertTrue(CinemaDiscovery.start(entries, new OkHttpClient(), new CinemaDiscovery.Listener() {
            @Override public void onFinished(CinemaSourceMerger.Result result) {
                success.set(true);
                completed.countDown();
            }
            @Override public void onError() {
                completed.countDown();
            }
        }));
        assertTrue("Manual discovery completed", completed.await(15, TimeUnit.SECONDS));
        return success.get();
    }
}
