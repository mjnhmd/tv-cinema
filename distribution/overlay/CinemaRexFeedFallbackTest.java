package com.fongmi.android.tv.ui.rex;

import com.fongmi.android.tv.api.DiscoverApi;
import com.github.catvod.net.OkHttp;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CinemaRexFeedFallbackTest {

    private Field okHttpClientField;
    private Object previousClient;
    private OkHttpClient fakeClient;
    private final List<HttpUrl> requestedUrls = new CopyOnWriteArrayList<>();
    private volatile int directStatus = 200;
    private volatile String directBody = "";
    private volatile String rexNowBody = "";

    @Before
    public void setup() throws Exception {
        clearCaches();
        requestedUrls.clear();
        directStatus = 200;
        directBody = "{\"subjects\":[{\"id\":\"36847425\",\"title\":\"可能的爱\",\"cover\":\"https://img1.doubanio.com/p1.jpg\",\"rate\":\"7.6\"}]}";
        rexNowBody = "[{\"id\":\"36953973\",\"title\":\"特立独行\",\"tmdb_info\":{\"id\":1353926,\"title\":\"特立独行\",\"mediaType\":\"movie\",\"posterPath\":\"/p.jpg\",\"backdropPath\":\"/b.jpg\"}},"
                + "{\"id\":\"1\",\"title\":\"旧剧\",\"tmdb_info\":{\"id\":999,\"title\":\"旧剧\",\"mediaType\":\"tv\",\"posterPath\":\"/t.jpg\"}}]";

        okHttpClientField = OkHttp.class.getDeclaredField("client");
        okHttpClientField.setAccessible(true);
        previousClient = okHttpClientField.get(OkHttp.get());
        fakeClient = new OkHttpClient.Builder().addInterceptor(chain -> {
            HttpUrl url = chain.request().url();
            requestedUrls.add(url);
            if ("movie.douban.com".equals(url.host())) {
                return new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(directStatus)
                        .message(directStatus == 200 ? "OK" : "Error")
                        .body(ResponseBody.create(directBody, MediaType.get("application/json")))
                        .build();
            }
            if ("assets.rexnow.tv".equals(url.host())) {
                return new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(rexNowBody, MediaType.get("application/json")))
                        .build();
            }
            return new Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(404)
                    .message("Not Found")
                    .body(ResponseBody.create("", MediaType.get("text/plain")))
                    .build();
        }).build();
        okHttpClientField.set(OkHttp.get(), fakeClient);
    }

    @After
    public void cleanup() throws Exception {
        clearCaches();
        if (okHttpClientField != null) okHttpClientField.set(OkHttp.get(), previousClient);
        if (fakeClient != null) {
            fakeClient.dispatcher().executorService().shutdownNow();
            fakeClient.connectionPool().evictAll();
        }
    }

    @Test(timeout = 10_000)
    public void prefersLiveDirectDoubanOverStaleRexNowFeed() throws Exception {
        RexFeedLoader loader = new RexFeedLoader(true);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<RexFeedResult>> delivered = new AtomicReference<>();
        try {
            loader.load(List.of(RexSections.byKey("douban_movie")), results -> {
                delivered.set(results);
                latch.countDown();
            });
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } finally {
            loader.close();
        }
        List<RexFeedResult> results = delivered.get();
        assertEquals(1, results.size());
        assertEquals("douban:36847425", results.get(0).getItems().get(0).getId());
        assertEquals("可能的爱", results.get(0).getItems().get(0).getName());
        assertTrue(requestedUrls.stream().anyMatch(url -> "movie.douban.com".equals(url.host())));
        assertTrue("Stale rexnow feed must not be fetched when live Douban succeeds",
                requestedUrls.stream().noneMatch(url -> "assets.rexnow.tv".equals(url.host())));
    }

    @Test(timeout = 10_000)
    public void fallsBackToRexNowFeedFilteredByMediaTypeWhenDirectDoubanFails() throws Exception {
        directStatus = 500;
        directBody = "";
        RexFeedLoader loader = new RexFeedLoader(true);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<RexFeedResult>> delivered = new AtomicReference<>();
        try {
            loader.load(List.of(RexSections.byKey("douban_movie")), results -> {
                delivered.set(results);
                latch.countDown();
            });
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } finally {
            loader.close();
        }
        List<RexFeedResult> results = delivered.get();
        assertEquals(1, results.size());
        assertEquals(1, results.get(0).getItems().size());
        assertEquals("tmdb:movie:1353926", results.get(0).getItems().get(0).getId());
        assertTrue(requestedUrls.stream().anyMatch(url -> "movie.douban.com".equals(url.host())));
        assertTrue(requestedUrls.stream().anyMatch(url -> "assets.rexnow.tv".equals(url.host())));
    }

    @SuppressWarnings("unchecked")
    private static void clearCaches() throws Exception {
        RexFeedLoader.clearCache();
        Field cache = DiscoverApi.class.getDeclaredField("CACHE");
        cache.setAccessible(true);
        ((Map<?, ?>) cache.get(null)).clear();
    }
}
