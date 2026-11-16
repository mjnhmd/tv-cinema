package com.fongmi.android.tv.monitoring;

import android.app.Activity;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.FrameMetrics;
import android.view.Window;

final class CinemaUiWatchdog {
    private static final long TICK_MS = 1_000;
    private static final long STALL_MS = 2_000;
    private static final long SLOW_START_MS = 8_000;
    private final Handler main = new Handler(android.os.Looper.getMainLooper());
    private final HandlerThread thread = new HandlerThread("cinema-ui-monitor", android.os.Process.THREAD_PRIORITY_BACKGROUND);
    private final Handler worker;
    private volatile Window window;
    private volatile String screen = "none";
    private final CinemaFrameWindow frames = new CinemaFrameWindow((total, slow, lost, max, stage) ->
            CinemaMonitoring.incident("ui_frames", max,
                    "screen=" + screen + " frames=" + total + " slow=" + slow + " missing=" + lost + " stage=" + stage, null));
    private volatile PowerManager power;
    private volatile long pendingSince = -1;
    private volatile int generation;
    private volatile boolean foreground;
    private volatile boolean stallReported;
    private volatile long resumedAt;
    private volatile boolean firstFramePending;
    private final Runnable tick = this::tick;
    private final Window.OnFrameMetricsAvailableListener frameListener = this::onFrame;

    CinemaUiWatchdog() {
        thread.start();
        worker = new Handler(thread.getLooper());
    }

    void resume(Activity activity) {
        detach();
        screen = activity.getClass().getSimpleName();
        power = (PowerManager) activity.getSystemService(Activity.POWER_SERVICE);
        window = activity.getWindow();
        foreground = true;
        resumedAt = SystemClock.elapsedRealtime();
        firstFramePending = true;
        generation++;
        pendingSince = -1;
        stallReported = false;
        worker.post(frames::reset);
        try { window.addOnFrameMetricsAvailableListener(frameListener, worker); }
        catch (RuntimeException ignored) { CinemaMonitoring.breadcrumb("ui_metrics_unavailable", 0, "screen=" + screen); }
        worker.post(tick);
    }

    void pause(Activity activity) {
        if (window == activity.getWindow()) detach();
    }

    private void detach() {
        foreground = false;
        generation++;
        pendingSince = -1;
        worker.removeCallbacks(tick);
        Window previous = window;
        window = null;
        if (previous != null) {
            try { previous.removeOnFrameMetricsAvailableListener(frameListener); }
            catch (RuntimeException ignored) { }
        }
    }

    private void tick() {
        if (!foreground) return;
        long now = SystemClock.uptimeMillis();
        long waiting = pendingSince;
        if (waiting < 0) {
            int current = generation;
            pendingSince = now;
            stallReported = false;
            main.post(() -> {
                if (current == generation) pendingSince = -1;
            });
        } else if (!stallReported && now - waiting >= STALL_MS) {
            stallReported = true;
            PowerManager manager = power;
            StackTraceElement[] stack = main.getLooper().getThread().getStackTrace();
            if (pendingSince == waiting && (manager == null || manager.isInteractive()) && !idle(stack)) {
                CinemaMonitoring.incident("ui_stall", now - waiting, "screen=" + screen,
                        stack);
            }
        }
        worker.postDelayed(tick, TICK_MS);
    }

    private void onFrame(Window owner, FrameMetrics metrics, int missed) {
        if (!foreground || window != owner) return;
        long now = SystemClock.elapsedRealtime();
        if (firstFramePending) {
            firstFramePending = false;
            long elapsed = Math.max(0, now - resumedAt);
            CinemaMonitoring.breadcrumb("resume_first_frame", elapsed, "screen=" + screen);
            if (elapsed >= SLOW_START_MS) CinemaMonitoring.incident("slow_ui_draw", elapsed, "screen=" + screen, null);
        }
        long total = millis(metrics, FrameMetrics.TOTAL_DURATION);
        if (total >= 50) {
            frames.record(now, total, missed, millis(metrics, FrameMetrics.INPUT_HANDLING_DURATION),
                    millis(metrics, FrameMetrics.LAYOUT_MEASURE_DURATION), millis(metrics, FrameMetrics.DRAW_DURATION),
                    millis(metrics, FrameMetrics.SYNC_DURATION));
        } else {
            frames.record(now, total, missed, 0, 0, 0, 0);
        }
    }

    private static long millis(FrameMetrics metrics, int id) {
        return Math.max(0, metrics.getMetric(id)) / 1_000_000;
    }

    private static boolean idle(StackTraceElement[] stack) {
        for (StackTraceElement element : stack) {
            if ("android.os.MessageQueue".equals(element.getClassName())
                    && "nativePollOnce".equals(element.getMethodName())) return true;
        }
        return false;
    }
}
