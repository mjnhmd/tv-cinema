package com.fongmi.android.tv.update;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import okio.Buffer;
import static org.junit.Assert.*;

public final class UpdateRouteProbeTest {
    private static byte[] apk(int size) {
        byte[] result = new byte[size]; Arrays.fill(result, (byte) 42);
        result[0] = 'P'; result[1] = 'K'; result[2] = 3; result[3] = 4;
        return result;
    }
    private static MockResponse response(byte[] data) {
        return new MockResponse().setHeader("Content-Type", "application/vnd.android.package-archive")
                .setBody(new Buffer().write(data));
    }
    @Test public void ranksPayloadThroughputRatherThanHeadersAndBoundsIgnoredRange() throws Exception {
        try (MockWebServer slow = new MockWebServer(); MockWebServer fast = new MockWebServer()) {
            byte[] data = apk(1024 * 1024);
            slow.enqueue(response(data).throttleBody(1_024, 100, TimeUnit.MILLISECONDS));
            fast.enqueue(response(data).setBodyDelay(100, TimeUnit.MILLISECONDS));
            String a = slow.url("/slow.apk").toString(), b = fast.url("/fast.apk").toString();
            long start = System.nanoTime();
            List<UpdateRouteProbe.Route> routes = new UpdateRouteProbe(new OkHttpClient(), 800, 32_768)
                    .rank(List.of(a, b), data.length);
            assertEquals(b, routes.get(0).url());
            assertEquals(2, routes.get(0).number());
            assertTrue(routes.get(0).bytesPerSecond() > routes.get(1).bytesPerSecond());
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 2_500);
            assertEquals("bytes=0-32767", fast.takeRequest().getHeader("Range"));
            assertEquals(1, slow.getRequestCount()); assertEquals(1, fast.getRequestCount());
        }
    }
    @Test public void retainsInvalidAndUnreachableProbesBehindValidRange() throws Exception {
        try (MockWebServer blocked = new MockWebServer(); MockWebServer good = new MockWebServer()) {
            blocked.enqueue(new MockResponse().setHeader("Content-Type", "text/html").setBody("Blocked"));
            good.enqueue(response(apk(32_768)).setResponseCode(206)
                    .setHeader("Content-Range", "bytes 0-32767/1048576"));
            String a = blocked.url("/blocked.apk").toString(), b = good.url("/good.apk").toString();
            List<UpdateRouteProbe.Route> routes = new UpdateRouteProbe(new OkHttpClient(), 500, 32_768)
                    .rank(List.of(a, b), 1_048_576);
            assertEquals(b, routes.get(0).url()); assertEquals(a, routes.get(1).url());
            assertEquals(0, routes.get(1).bytesPerSecond());
        }
    }
    @Test public void rejectsWrongRangeOffsetsAndDisguisedHtml() throws Exception {
        try (MockWebServer first = new MockWebServer(); MockWebServer second = new MockWebServer()) {
            first.enqueue(response(apk(32_768)).setResponseCode(206)
                    .setHeader("Content-Range", "bytes 1-32768/1048576"));
            byte[] bad = apk(1_048_576); bad[0] = '<';
            second.enqueue(response(bad));
            List<String> urls = List.of(first.url("/bad-range.apk").toString(), second.url("/html.apk").toString());
            List<UpdateRouteProbe.Route> routes = new UpdateRouteProbe(new OkHttpClient(), 500, 32_768).rank(urls, bad.length);
            assertEquals(urls, routes.stream().map(UpdateRouteProbe.Route::url).toList());
            assertTrue(routes.stream().allMatch(route -> route.bytesPerSecond() == 0));
        }
    }
    @Test public void cancellationAbortsEveryConcurrentRequestPromptly() throws Exception {
        try (MockWebServer first = new MockWebServer(); MockWebServer second = new MockWebServer()) {
            first.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            second.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            UpdateRouteProbe probe = new UpdateRouteProbe(new OkHttpClient(), 5_000, 32_768);
            var executor = Executors.newSingleThreadExecutor();
            try {
                var future = executor.submit(() -> {
                    try { probe.rank(List.of(first.url("/a").toString(), second.url("/b").toString()), 1_048_576); return false; }
                    catch (java.io.InterruptedIOException expected) { return true; }
                });
                assertNotNull(first.takeRequest(2, TimeUnit.SECONDS));
                assertNotNull(second.takeRequest(2, TimeUnit.SECONDS));
                probe.cancel();
                assertTrue(future.get(2, TimeUnit.SECONDS));
            } finally { executor.shutdownNow(); }
        }
    }
}
