package com.fongmi.android.tv.update;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Samples only an APK prefix; failed measurements remain fallback routes. */
final class UpdateRouteProbe {
    private static final Pattern RANGE = Pattern.compile("bytes 0-([0-9]+)/([0-9]+)");
    private final OkHttpClient client;
    private final long timeoutMs;
    private final int sampleBytes;
    private final Set<Call> calls = new HashSet<>();
    private boolean cancelled;

    record Route(String url, int number, long bytesPerSecond) {}

    UpdateRouteProbe(OkHttpClient client, long timeoutMs, int sampleBytes) {
        this.timeoutMs = timeoutMs;
        this.sampleBytes = sampleBytes;
        this.client = client.newBuilder().connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS).callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(false).followSslRedirects(false).build();
    }

    List<Route> rank(List<String> urls, long expectedSize) throws InterruptedIOException {
        checkCancelled();
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(3, urls.size()), task -> {
            Thread thread = new Thread(task, "cinema-update-probe");
            thread.setDaemon(true);
            return thread;
        });
        List<Future<Route>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < Math.min(3, urls.size()); i++) {
                int number = i + 1;
                futures.add(executor.submit(() -> measure(urls.get(number - 1), number, expectedSize)));
            }
            List<Route> routes = new ArrayList<>();
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs + 500);
            for (int i = 0; i < urls.size(); i++) {
                checkCancelled();
                Route sample = new Route(urls.get(i), i + 1, 0);
                if (i < futures.size()) {
                    try { sample = futures.get(i).get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS); }
                    catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ignored) { }
                }
                routes.add(sample);
            }
            checkCancelled();
            routes.sort(Comparator.comparingLong(Route::bytesPerSecond).reversed());
            return routes;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Update download cancelled");
        } finally {
            futures.forEach(future -> future.cancel(true));
            executor.shutdownNow();
            cancelCalls();
        }
    }

    private Route measure(String url, int number, long expectedSize) {
        long start = System.nanoTime();
        long bytes = 0;
        Call call = null;
        boolean validHeader = false;
        try {
            int limit = (int) (expectedSize > 0 ? Math.min(sampleBytes, expectedSize) : sampleBytes);
            Request request = new Request.Builder().url(url).header("Range", "bytes=0-" + (limit - 1))
                    .header("Accept-Encoding", "identity").build();
            call = client.newCall(request);
            synchronized (this) {
                if (cancelled || Thread.currentThread().isInterrupted()) return new Route(url, number, 0);
                calls.add(call);
            }
            try (Response response = call.execute()) {
                if (response.body() == null || !validResponse(response, expectedSize, limit)) {
                    call.cancel();
                    return new Route(url, number, 0);
                }
                byte[] buffer = new byte[8_192];
                byte[] header = new byte[4];
                try (InputStream input = response.body().byteStream()) {
                    int count;
                    while (bytes < limit && (count = input.read(buffer, 0, (int) Math.min(buffer.length, limit - bytes))) != -1) {
                        if (Thread.currentThread().isInterrupted()) break;
                        if (count == 0) continue;
                        for (int i = 0; i < count && bytes + i < 4; i++) header[(int) bytes + i] = buffer[i];
                        bytes += count;
                        if (bytes >= 4) {
                            validHeader = header[0] == 'P' && header[1] == 'K' && header[2] == 3 && header[3] == 4;
                            if (!validHeader) break;
                        }
                    }
                    // Do not let close() drain an ignored Range response containing the full APK.
                    call.cancel();
                }
            }
        } catch (IOException | IllegalArgumentException ignored) {
            // Partial but valid APK samples still carry useful throughput information.
        } finally {
            if (call != null) {
                call.cancel();
                synchronized (this) { calls.remove(call); }
            }
        }
        long elapsedMs = Math.max(1, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        return new Route(url, number, validHeader ? bytes * 1000 / elapsedMs : 0);
    }

    private static boolean validResponse(Response response, long expectedSize, int limit) {
        String type = response.header("Content-Type", "").toLowerCase(Locale.ROOT);
        if (type.startsWith("text/") || type.contains("json") || type.contains("xml")) return false;
        if (response.code() == 200) return expectedSize <= 0 || response.body().contentLength() == -1
                || response.body().contentLength() == expectedSize;
        if (response.code() != 206) return false;
        Matcher range = RANGE.matcher(response.header("Content-Range", ""));
        if (!range.matches()) return false;
        try {
            long end = Long.parseLong(range.group(1)), total = Long.parseLong(range.group(2));
            return end < limit && end >= 3 && total > end && (expectedSize <= 0 || total == expectedSize)
                    && (response.body().contentLength() == -1 || response.body().contentLength() == end + 1);
        } catch (NumberFormatException invalid) { return false; }
    }

    synchronized void cancel() { cancelled = true; cancelCalls(); }

    private synchronized void cancelCalls() { calls.forEach(Call::cancel); }

    private synchronized void checkCancelled() throws InterruptedIOException {
        if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Update download cancelled");
    }
}
