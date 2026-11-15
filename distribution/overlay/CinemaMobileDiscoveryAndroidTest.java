package com.fongmi.android.tv.api.config;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Config;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class CinemaMobileDiscoveryAndroidTest {
    @Test public void candidateActivationRequiresPhoneCatalogAndRefetchPreservesTheActiveSlot() throws Exception {
        CinemaMobileTestSupport support = new CinemaMobileTestSupport();
        support.start();
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            String body = "{\"sites\":[{\"key\":\"phone-candidate\",\"name\":\"手机候选\","
                    + "\"type\":1,\"api\":\"" + support.url("/api") + "\"}]}";
            List<CinemaSources.Entry> entries = List.of(new CinemaSources.Entry(BaseConfig.VOD,
                    "手机候选", server.url("/config.json").toString()));
            server.enqueue(new MockResponse().setBody(body));
            assertTrue(fetch(entries));
            String firstUrl = CinemaDiscovery.candidateUrl();
            String firstBody = CinemaDiscovery.candidateContent(firstUrl);
            assertEquals(support.configUrl, Config.vod().getUrl());
            support.main(() -> support.activity.change(1));
            support.instrumentation.waitForIdleSync();
            UiDevice device = UiDevice.getInstance(support.instrumentation);
            device.findObject(By.res(support.activity.getPackageName(), "builtinSources")).click();
            support.instrumentation.waitForIdleSync();
            assertEquals(support.configUrl, Config.vod().getUrl());
            assertNotNull(device.findObject(By.textStartsWith("手动抓取 · 合并片源")));
            device.findObject(By.textStartsWith("手动抓取 · 合并片源")).click();
            support.await(() -> firstUrl.equals(Config.vod().getUrl())
                    && firstUrl.equals(VodConfig.get().getConfig().getUrl()), "Explicit phone selection loads candidate");
            server.enqueue(new MockResponse().setBody(body));
            assertTrue(fetch(entries));
            assertNotEquals(firstUrl, CinemaDiscovery.candidateUrl());
            assertEquals(firstBody, CinemaDiscovery.candidateContent(firstUrl));
            assertEquals(firstUrl, Config.vod().getUrl());
        } finally {
            support.close();
        }
    }

    @Test public void manualFetchAndFailureKeepCurrentPhoneSubscription() throws Exception {
        assertProbe();
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            String current = Config.vod().getUrl();
            List<CinemaSources.Entry> entries = List.of(new CinemaSources.Entry(BaseConfig.VOD,
                    "手机抓取测试", server.url("/config.json").toString()));
            assertEquals(0, server.getRequestCount());
            server.enqueue(new MockResponse().setBody("{\"sites\":[{\"key\":\"one\",\"name\":\"测试\","
                    + "\"type\":3,\"api\":\"csp_Fixture\",\"jar\":\"./never-run.jar\"}]}"));
            assertTrue(fetch(entries));
            assertEquals(1, server.getRequestCount());
            assertEquals("/config.json", server.takeRequest().getPath());
            assertEquals(current, Config.vod().getUrl());
            assertTrue(CinemaDiscovery.hasCandidate());
            String candidate = CinemaDiscovery.candidateContent(CinemaDiscovery.candidateUrl());
            assertEquals(CinemaDiscovery.candidateUrl(), CinemaSources.catalogEntries().get(0).url());
            server.enqueue(new MockResponse().setResponseCode(503));
            assertFalse(fetch(entries));
            assertEquals(candidate, CinemaDiscovery.candidateContent(CinemaDiscovery.candidateUrl()));
            assertEquals(current, Config.vod().getUrl());
            assertEquals("No plugin downloaded", 2, server.getRequestCount());
        }
    }

    @Test public void mergingPhoneProvidersPreservesTheirOwnRules() throws Exception {
        assertProbe();
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            String body = "{\"spider\":\"" + server.url("/never-run.jar")
                    + "\",\"sites\":[{\"key\":\"same\",\"name\":\"测试\",\"type\":3,"
                    + "\"api\":\"csp_Fixture\",\"ext\":\"./rules.json\"}]}";
            server.enqueue(new MockResponse().setBody(body));
            server.enqueue(new MockResponse().setBody(body));
            assertTrue(fetch(List.of(new CinemaSources.Entry(BaseConfig.VOD, "甲", server.url("/a/config.json").toString()),
                    new CinemaSources.Entry(BaseConfig.VOD, "乙", server.url("/b/config.json").toString()))));
            assertEquals("Only config texts requested", 2, server.getRequestCount());
            JsonArray sites = JsonParser.parseString(CinemaDiscovery.candidateContent(CinemaDiscovery.candidateUrl()))
                    .getAsJsonObject().getAsJsonArray("sites");
            assertEquals(2, sites.size());
            assertEquals(server.url("/a/rules.json").toString(), sites.get(0).getAsJsonObject().get("ext").getAsString());
            assertEquals(server.url("/b/rules.json").toString(), sites.get(1).getAsJsonObject().get("ext").getAsString());
        }
    }

    private boolean fetch(List<CinemaSources.Entry> entries) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean();
        assertTrue(CinemaDiscovery.start(entries, new OkHttpClient(), new CinemaDiscovery.Listener() {
            @Override public void onFinished(CinemaSourceMerger.Result result) {
                success.set(true);
                completed.countDown();
            }
            @Override public void onError() { completed.countDown(); }
        }));
        assertTrue("Discovery completes", completed.await(15, TimeUnit.SECONDS));
        return success.get();
    }

    private void assertProbe() {
        assertEquals("com.mjnhmd.tvcinema.mobile.probe",
                InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName());
    }
}
