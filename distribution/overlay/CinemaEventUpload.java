package com.fongmi.android.tv.monitoring;

import com.fongmi.android.tv.BuildConfig;
import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Near-real-time LAN sink for the event log: keeps one file per app session on the user's own
 * NAS over WebDAV, rewriting it every {@link #PERIOD_MS} and again ~{@link #DEBOUNCE_MS} after any
 * key step, so a run can be watched while it happens instead of being exported by hand.
 *
 * <p>Off by default. It only runs when {@code CINEMA_LOG_UPLOAD_URL} was baked in at build time
 * from {@code local.properties} or the environment — never from the repository — so no address or
 * credential enters git or this file. All traffic runs on one daemon thread, one request at a
 * time, and only when the buffer actually changed; the outcome is recorded as an event, so a
 * failing sink stays visible in the log itself.
 *
 * <p>The whole buffer is rewritten rather than appended: WebDAV has no cheap append, the buffer is
 * capped at 2000 lines, and a single complete file is what a reader wants afterwards.
 */
public final class CinemaEventUpload {

    private static final long PERIOD_MS = 5_000L;
    private static final long DEBOUNCE_MS = 300L;
    private static final long TIMEOUT_MS = 15_000L;
    private static final MediaType TEXT = MediaType.parse("text/plain; charset=utf-8");
    private static final Set<String> KEY_AREAS = new HashSet<>(Arrays.asList("app", "play", "ui", "setting"));
    private static final AtomicInteger lastSent = new AtomicInteger(-1);
    private static final AtomicBoolean pending = new AtomicBoolean();
    private static ScheduledExecutorService worker;

    private CinemaEventUpload() {}

    public static boolean configured() {
        return !BuildConfig.CINEMA_LOG_UPLOAD_URL.isEmpty();
    }

    /** Starts the periodic flush; no-op unless a sink was configured at build time. */
    public static synchronized void start() {
        if (!configured() || worker != null) return;
        worker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cinema-upload");
            thread.setDaemon(true);
            return thread;
        });
        worker.scheduleWithFixedDelay(() -> flush(false), PERIOD_MS, PERIOD_MS, TimeUnit.MILLISECONDS);
        CinemaEventLog.event("log", "sink", "period " + PERIOD_MS + "ms url=" + BuildConfig.CINEMA_LOG_UPLOAD_URL);
    }

    /** Called after every event; key steps get pushed almost immediately, the rest ride the timer. */
    public static void onEvent(String area) {
        ScheduledExecutorService executor = worker;
        if (executor == null || !KEY_AREAS.contains(area)) return;
        if (!pending.compareAndSet(false, true)) return;
        try {
            executor.schedule(() -> {
                pending.set(false);
                flush(false);
            }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            pending.set(false);
        }
    }

    /** Pushes the current buffer now; {@code force} also re-sends an unchanged buffer. */
    public static void flushNow(boolean force) {
        ScheduledExecutorService executor = worker;
        if (executor == null) return;
        try {
            executor.execute(() -> flush(force));
        } catch (RejectedExecutionException ignored) {
        }
    }

    private static void flush(boolean force) {
        if (!configured()) return;
        int total = CinemaEventLog.total();
        if (!force && total == lastSent.get()) return;
        String report = CinemaEventLog.report();
        if (report == null) return;
        try {
            int code = send(report);
            boolean ok = code >= 200 && code < 300;
            CinemaEventLog.event("log", ok ? "upload" : "upload fail",
                    "code=" + code + " bytes=" + report.length() + " lines=" + total);
            // The event above is itself a new line; treat the buffer as sent up to here.
            lastSent.set(CinemaEventLog.total());
        } catch (IOException | RuntimeException e) {
            CinemaEventLog.event("log", "upload fail", e.getClass().getSimpleName() + ": " + e.getMessage());
            lastSent.set(CinemaEventLog.total());
        }
    }

    private static int send(String report) throws IOException {
        String base = BuildConfig.CINEMA_LOG_UPLOAD_URL;
        if (!base.endsWith("/")) base = base + "/";
        String name = "cinema_events_" + CinemaEventLog.session() + ".txt";
        Request.Builder builder = new Request.Builder()
                .url(base + name)
                .header("X-Cinema-Log", "1")
                .put(RequestBody.create(report, TEXT));
        if (!BuildConfig.CINEMA_LOG_UPLOAD_USER.isEmpty()) {
            builder.header("Authorization", Credentials.basic(BuildConfig.CINEMA_LOG_UPLOAD_USER, BuildConfig.CINEMA_LOG_UPLOAD_PASS));
        }
        try (Response response = OkHttp.client(TIMEOUT_MS).newCall(builder.build()).execute()) {
            return response.code();
        }
    }
}
