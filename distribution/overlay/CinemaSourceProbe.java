package com.fongmi.android.tv.api.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class CinemaSourceProbe {

    public interface Fetcher {
        String fetch(String url) throws Exception;
    }

    public record Target(String label, String url) {
    }

    public record Sample(String label, String url, long elapsedMs, boolean ok, String detail) {
    }

    public record Report(List<Sample> samples, Sample best, String body) {

        public boolean ok() {
            return best != null && body != null && !body.isEmpty();
        }
    }

    private CinemaSourceProbe() {
    }

    public static Report probe(List<Target> targets, Fetcher fetcher, long timeoutMs, long goodEnoughMs) {
        if (targets == null || targets.isEmpty()) return new Report(List.of(), null, "");
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cinema-source-probe");
            thread.setDaemon(true);
            return thread;
        });
        List<Sample> samples = new ArrayList<>();
        try {
            Sample best = null;
            String body = "";
            for (Target target : targets) {
                Attempt attempt = measure(executor, target, fetcher, timeoutMs);
                samples.add(attempt.sample());
                if (!attempt.sample().ok()) continue;
                if (best == null || attempt.sample().elapsedMs() < best.elapsedMs()) {
                    best = attempt.sample();
                    body = attempt.body();
                }
                if (best.elapsedMs() <= goodEnoughMs) break;
            }
            return new Report(Collections.unmodifiableList(samples), best, body);
        } finally {
            executor.shutdownNow();
        }
    }

    private record Attempt(Sample sample, String body) {
    }

    private static Attempt measure(ExecutorService executor, Target target, Fetcher fetcher, long timeoutMs) {
        long start = System.nanoTime();
        Future<String> future = executor.submit(() -> fetcher.fetch(target.url()));
        try {
            String body = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            long elapsed = elapsedMs(start);
            String normalized = normalize(body);
            if (normalized == null) return new Attempt(new Sample(target.label(), target.url(), elapsed, false, "no-sites"), "");
            return new Attempt(new Sample(target.label(), target.url(), elapsed, true, ""), normalized);
        } catch (Exception error) {
            future.cancel(true);
            return new Attempt(new Sample(target.label(), target.url(), elapsedMs(start), false, describe(error)), "");
        }
    }

    private static long elapsedMs(long start) {
        return (System.nanoTime() - start) / 1_000_000L;
    }

    private static final java.util.regex.Pattern COMMENT_LINE = java.util.regex.Pattern.compile("(?m)^\\s*//.*$");

    static boolean isConfig(String body) {
        return normalize(body) != null;
    }

    static String normalize(String body) {
        if (body == null || body.isEmpty()) return null;
        String stripped = stripComments(body);
        if (hasSites(object(stripped))) return stripped;
        return hasSites(object(body)) ? body : null;
    }

    private static boolean hasSites(JsonObject object) {
        if (object == null) return false;
        JsonElement sites = object.get("sites");
        return sites != null && sites.isJsonArray() && !sites.getAsJsonArray().isEmpty();
    }

    static String stripComments(String body) {
        return body == null ? "" : COMMENT_LINE.matcher(body).replaceAll("");
    }

    private static JsonObject object(String body) {
        try {
            JsonElement element = JsonParser.parseString(body);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception error) {
            return null;
        }
    }

    private static String describe(Throwable error) {
        Throwable cause = error;
        while (cause instanceof java.util.concurrent.ExecutionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof java.util.concurrent.TimeoutException) return "timeout";
        String message = cause.getMessage();
        return message == null || message.isEmpty() ? cause.getClass().getSimpleName() : message;
    }
}
