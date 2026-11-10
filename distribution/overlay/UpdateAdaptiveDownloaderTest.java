package com.fongmi.android.tv.update;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import static org.junit.Assert.*;

public final class UpdateAdaptiveDownloaderTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static byte[] apk(int size) {
        byte[] bytes = new byte[size]; Arrays.fill(bytes, (byte) 42);
        bytes[0] = 'P'; bytes[1] = 'K'; bytes[2] = 3; bytes[3] = 4;
        return bytes;
    }
    private static MockResponse response(byte[] bytes) {
        return new MockResponse().setHeader("Content-Type", "application/vnd.android.package-archive")
                .setBody(new Buffer().write(bytes));
    }
    private static String hash(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) result.append(String.format("%02x", value & 255));
        return result.toString();
    }
    private UpdateDownloader downloader() { return new UpdateDownloader(new OkHttpClient(), 700, 32_768, 200, 8_000_000); }
    private File target() { return new File(temporary.getRoot(), "update.apk"); }

    @Test public void adaptiveSelectionDownloadsFastPayloadAndReportsOriginalRouteNumber() throws Exception {
        byte[] bytes = apk(2 * 1024 * 1024);
        try (MockWebServer slow = new MockWebServer(); MockWebServer fast = new MockWebServer()) {
            slow.enqueue(response(bytes).throttleBody(1_024, 100, TimeUnit.MILLISECONDS));
            fast.enqueue(response(bytes)); fast.enqueue(response(bytes));
            List<String> events = new CopyOnWriteArrayList<>();
            File file = downloader().downloadAdaptive(List.of(slow.url("/a").toString(), fast.url("/b").toString()),
                    target(), bytes.length, hash(bytes), ignored -> events.add("validated"), new UpdateDownloader.Progress() {
                        @Override public void update(int percent) { if (percent == 100) events.add("complete"); }
                        @Override public void onTesting() { events.add("testing"); }
                        @Override public void onSource(int number, int total) { events.add("route " + number); }
                    });
            assertArrayEquals(bytes, Files.readAllBytes(file.toPath()));
            assertEquals(List.of("testing", "route 2", "validated", "complete"), events);
            assertEquals(1, slow.getRequestCount()); assertEquals(2, fast.getRequestCount());
        }
    }

    @Test public void slowContinuousBodySwitchesWithoutMixingPartialBytesAndKeepsFinalRoute() throws Exception {
        byte[] bytes = apk(2 * 1024 * 1024);
        try (MockWebServer first = new MockWebServer(); MockWebServer last = new MockWebServer()) {
            first.enqueue(response(bytes));
            first.enqueue(response(bytes).throttleBody(8_192, 100, TimeUnit.MILLISECONDS));
            last.enqueue(response(bytes).setBodyDelay(100, TimeUnit.MILLISECONDS));
            last.enqueue(response(bytes).throttleBody(262_144, 100, TimeUnit.MILLISECONDS));
            AtomicInteger switches = new AtomicInteger(), telemetry = new AtomicInteger(), validated = new AtomicInteger();
            downloader().downloadAdaptive(List.of(first.url("/a").toString(), last.url("/b").toString()),
                    target(), bytes.length, hash(bytes), file -> {
                        assertArrayEquals(bytes, Files.readAllBytes(file.toPath())); validated.incrementAndGet();
                    }, new UpdateDownloader.Progress() {
                        @Override public void update(int percent) { }
                        @Override public void onSwitching() { switches.incrementAndGet(); }
                        @Override public void onTransfer(long rate, long seconds) {
                            assertTrue(rate > 0); assertTrue(seconds >= 0); telemetry.incrementAndGet();
                        }
                    });
            assertEquals(1, switches.get()); assertEquals(1, validated.get()); assertTrue(telemetry.get() > 0);
            assertArrayEquals(bytes, Files.readAllBytes(target().toPath()));
            assertFalse(new File(target() + ".part").exists());
            assertEquals(2, first.getRequestCount()); assertEquals(2, last.getRequestCount());
        }
    }

    @Test public void probingCannotAuthorizeWrongChecksumAndCancellingTestingStartsNoRequests() throws Exception {
        byte[] bytes = apk(65_536);
        try (MockWebServer first = new MockWebServer(); MockWebServer second = new MockWebServer()) {
            first.enqueue(response(bytes)); first.enqueue(response(bytes));
            second.enqueue(response(bytes)); second.enqueue(response(bytes));
            byte[] wrong = bytes.clone(); wrong[100] ^= 1;
            assertThrows(java.io.IOException.class, () -> downloader().downloadAdaptive(
                    List.of(first.url("/a").toString(), second.url("/b").toString()), target(), bytes.length,
                    hash(wrong), file -> fail("Wrong bytes validated"), null));
            assertFalse(target().exists());
            UpdateDownloader cancelled = downloader();
            int count = first.getRequestCount();
            assertThrows(java.io.InterruptedIOException.class, () -> cancelled.downloadAdaptive(
                    List.of(first.url("/a").toString(), second.url("/b").toString()), target(), bytes.length,
                    hash(bytes), file -> fail(), new UpdateDownloader.Progress() {
                        @Override public void update(int percent) { fail(); }
                        @Override public void onTesting() { cancelled.cancel(); }
                    }));
            assertEquals(count, first.getRequestCount());
        }
    }

    @Test public void retriesTheUsableSlowRouteWhenEveryAlternativeFails() throws Exception {
        byte[] bytes = apk(2 * 1024 * 1024);
        try (MockWebServer working = new MockWebServer(); MockWebServer blocked = new MockWebServer()) {
            working.enqueue(response(bytes));
            working.enqueue(response(bytes).throttleBody(8_192, 100, TimeUnit.MILLISECONDS));
            working.enqueue(response(bytes).throttleBody(262_144, 100, TimeUnit.MILLISECONDS));
            blocked.enqueue(new MockResponse().setResponseCode(503));
            blocked.enqueue(new MockResponse().setResponseCode(503));
            List<Integer> attempts = new CopyOnWriteArrayList<>();
            downloader().downloadAdaptive(List.of(working.url("/a").toString(), blocked.url("/b").toString()),
                    target(), bytes.length, hash(bytes), file -> {}, new UpdateDownloader.Progress() {
                        @Override public void update(int percent) { }
                        @Override public void onSource(int number, int total) { assertEquals(2, total); attempts.add(number); }
                    });
            assertArrayEquals(bytes, Files.readAllBytes(target().toPath()));
            assertEquals(List.of(1, 2, 1), attempts);
            assertEquals(3, working.getRequestCount()); assertEquals(2, blocked.getRequestCount());
        }
    }

    @Test public void workingFinalRouteHasNoWholeDownloadDeadline() throws Exception {
        byte[] bytes = apk(65_536);
        OkHttpClient client = new OkHttpClient.Builder().callTimeout(1, TimeUnit.MILLISECONDS)
                .addInterceptor(chain -> {
                    assertEquals("Usable slow download has no total deadline", 0, chain.call().timeout().timeoutNanos());
                    return chain.proceed(chain.request());
                }).build();
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(response(bytes).setBodyDelay(100, TimeUnit.MILLISECONDS));
            new UpdateDownloader(client).downloadAdaptive(List.of(server.url("/only.apk").toString()),
                    target(), bytes.length, hash(bytes), file -> {}, null);
            assertArrayEquals(bytes, Files.readAllBytes(target().toPath()));
            assertEquals(1, server.getRequestCount());
        }
    }
}
