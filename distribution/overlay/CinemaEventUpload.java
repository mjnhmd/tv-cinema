package com.fongmi.android.tv.monitoring;

import android.os.Process;

import com.fongmi.android.tv.BuildConfig;
import com.github.catvod.net.OkHttp;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Batched sink for the event log: one POST per {@link #PERIOD_MS} carrying only the lines recorded
 * since the last success, plus one more when the app leaves the foreground.
 *
 * <p>The television is the constraint. Nothing is sent per event, no JSON is built, no request is
 * made when nothing new happened, and the work runs on a single background-priority daemon thread
 * through one reused client so the keep-alive connection does the work instead of a fresh handshake
 * every time. A failed batch keeps its cursor and retries on the next tick; if a backlog outgrows
 * the ring buffer it is skipped with one explicit gap event instead of growing without bound.
 *
 * <p>Off by default: it only runs when {@code CINEMA_LOG_UPLOAD_URL} was baked in at build time
 * from {@code local.properties} or the environment, so neither the address nor the token lives in
 * the repository.
 */
public final class CinemaEventUpload {

    private static final long PERIOD_MS = 10_000L;
    private static final long TIMEOUT_MS = 10_000L;
    private static final int MAX_BATCH = 400;
    private static final MediaType TEXT = MediaType.parse("text/plain; charset=utf-8");
    private static final AtomicInteger cursor = new AtomicInteger();
    private static ScheduledExecutorService worker;
    private static volatile OkHttpClient client;

    private CinemaEventUpload() {}

    public static boolean configured() {
        return !BuildConfig.CINEMA_LOG_UPLOAD_URL.isEmpty();
    }

    /** Starts the batch timer; no-op unless a sink was configured at build time. */
    public static synchronized void start() {
        if (!configured() || worker != null) return;
        worker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cinema-upload");
            thread.setDaemon(true);
            return thread;
        });
        worker.scheduleWithFixedDelay(() -> flush(false), PERIOD_MS, PERIOD_MS, TimeUnit.MILLISECONDS);
        CinemaEventLog.event("log", "sink", "batch " + PERIOD_MS + "ms url=" + BuildConfig.CINEMA_LOG_UPLOAD_URL);
    }

    /** Pushes what is pending now; used when the app leaves the foreground. */
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
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        int from = cursor.get();
        int first = CinemaEventLog.firstAvailable();
        if (from < first) {
            int lost = first - from;
            from = first;
            cursor.set(from);
            CinemaEventLog.event("log", "gap", "lost=" + lost);
        }
        if (!force && CinemaEventLog.total() <= from) return;
        List<String> lines = CinemaEventLog.since(from);
        if (lines.isEmpty()) return;
        if (lines.size() > MAX_BATCH) lines = lines.subList(0, MAX_BATCH);
        try {
            int code = send(String.join("\n", lines));
            if (code >= 200 && code < 300) {
                cursor.set(from + lines.size());
            } else if (code == 401 || code == 403) {
                CinemaEventLog.event("log", "upload fail", "code=" + code + " token");
            } else {
                CinemaEventLog.event("log", "upload fail", "code=" + code + " lines=" + lines.size());
            }
        } catch (IOException | RuntimeException e) {
            CinemaEventLog.event("log", "upload fail", e.getClass().getSimpleName());
        }
    }

    private static int send(String body) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(BuildConfig.CINEMA_LOG_UPLOAD_URL)
                .header("X-Cinema-Log", "1")
                .header("X-Cinema-Session", CinemaEventLog.session())
                .header("X-Cinema-Version", BuildConfig.VERSION_NAME + "/" + BuildConfig.VERSION_CODE)
                .post(RequestBody.create(body, TEXT));
        if (!BuildConfig.CINEMA_LOG_TOKEN.isEmpty()) {
            builder.header("Authorization", "Bearer " + BuildConfig.CINEMA_LOG_TOKEN);
        }
        try (Response response = client().newCall(builder.build()).execute()) {
            return response.code();
        }
    }

    private static OkHttpClient client() {
        OkHttpClient value = client;
        if (value == null) {
            value = OkHttp.client(TIMEOUT_MS);
            client = value;
        }
        return value;
    }
}
