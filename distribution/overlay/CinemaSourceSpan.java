package com.fongmi.android.tv.monitoring;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

public final class CinemaSourceSpan {
    private static final long SLOW_MS = 8_000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final long started = SystemClock.elapsedRealtime();
    private final int sourceType;
    private final long slowMs;
    private final Runnable timeout = this::slow;
    private boolean closed;
    private boolean reported;

    public CinemaSourceSpan(int sourceType) {
        this(sourceType, SLOW_MS);
    }

    CinemaSourceSpan(int sourceType, long slowMs) {
        this.sourceType = sourceType;
        this.slowMs = slowMs;
        CinemaMonitoring.breadcrumb("source_begin", 0, "type=" + sourceType);
        handler.postDelayed(timeout, slowMs);
    }

    public void finish(boolean success) {
        if (closed) return;
        closed = true;
        handler.removeCallbacks(timeout);
        long elapsed = SystemClock.elapsedRealtime() - started;
        CinemaMonitoring.breadcrumb("source_end", elapsed, "type=" + sourceType + " success=" + success);
        if (!success) CinemaMonitoring.incident("source_error", elapsed, "type=" + sourceType, null);
        if (elapsed >= slowMs && !reported) {
            CinemaMonitoring.incident("slow_source", elapsed, "type=" + sourceType + " finished=true", null);
        }
    }

    public void cancel() {
        closed = true;
        handler.removeCallbacks(timeout);
    }

    private void slow() {
        if (closed) return;
        reported = true;
        CinemaMonitoring.incident("slow_source", SystemClock.elapsedRealtime() - started,
                "type=" + sourceType + " finished=false", null);
    }
}
