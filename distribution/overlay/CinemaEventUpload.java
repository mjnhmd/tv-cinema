package com.fongmi.android.tv.monitoring;

import android.os.SystemClock;

import com.fongmi.android.tv.BuildConfig;
import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Optional LAN sink for the event log: PUTs the buffered report into a WebDAV folder on the
 * user's own NAS, so a run can be inspected without copying files off the TV by hand.
 *
 * <p>Off by default. It only runs when {@code CINEMA_LOG_UPLOAD_URL} was baked in at build time
 * from {@code local.properties} or the environment — never from the repository — so no address
 * or credential enters git or this file. Every upload runs on one daemon thread, at most once per
 * {@link #MIN_GAP_MS}, and only when new events exist; the outcome is recorded as an event so a
 * silent failure stays visible in the log itself.
 */
public final class CinemaEventUpload {

    private static final long MIN_GAP_MS = 30_000L;
    private static final long TIMEOUT_MS = 15_000L;
    private static final MediaType TEXT = MediaType.parse("text/plain; charset=utf-8");
    private static final AtomicBoolean running = new AtomicBoolean();
    private static final AtomicLong lastAt = new AtomicLong();
    private static final AtomicInteger lastSent = new AtomicInteger(-1);

    private CinemaEventUpload() {}

    public static boolean configured() {
        return !BuildConfig.CINEMA_LOG_UPLOAD_URL.isEmpty();
    }

    /** Fire-and-forget; safe from the UI thread and from lifecycle callbacks. */
    public static void uploadAsync(boolean force) {
        if (!configured()) return;
        int total = CinemaEventLog.total();
        if (!force && total == lastSent.get()) return;
        long now = SystemClock.elapsedRealtime();
        if (!force && now - lastAt.get() < MIN_GAP_MS) return;
        if (!running.compareAndSet(false, true)) return;
        lastAt.set(now);
        String report = CinemaEventLog.report();
        if (report == null) {
            running.set(false);
            return;
        }
        Thread worker = new Thread(() -> {
            try {
                send(report, total);
            } finally {
                running.set(false);
            }
        }, "cinema-upload");
        worker.setDaemon(true);
        worker.start();
    }

    private static void send(String report, int total) {
        String url = BuildConfig.CINEMA_LOG_UPLOAD_URL;
        if (!url.endsWith("/")) url = url + "/";
        String name = "cinema_events_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                + "_" + CinemaEventLog.session() + ".txt";
        Request.Builder builder = new Request.Builder()
                .url(url + name)
                .header("X-Cinema-Log", "1")
                .put(RequestBody.create(report, TEXT));
        if (!BuildConfig.CINEMA_LOG_UPLOAD_USER.isEmpty()) {
            builder.header("Authorization", Credentials.basic(BuildConfig.CINEMA_LOG_UPLOAD_USER, BuildConfig.CINEMA_LOG_UPLOAD_PASS));
        }
        try (Response response = OkHttp.client(TIMEOUT_MS).newCall(builder.build()).execute()) {
            if (response.isSuccessful()) lastSent.set(total);
            CinemaEventLog.event("log", "upload", "code=" + response.code() + " bytes=" + report.length() + " name=" + name);
        } catch (IOException | RuntimeException e) {
            CinemaEventLog.event("log", "upload fail", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
