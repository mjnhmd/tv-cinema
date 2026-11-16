package com.fongmi.android.tv.monitoring;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.media3.common.C;
import androidx.media3.common.Player;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class CinemaPlaybackProbe {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Supplier<String> details;
    private final BooleanSupplier requested;
    private final Runnable tick = this::tick;
    private final CinemaPlaybackTimeline timeline = new CinemaPlaybackTimeline(new CinemaPlaybackTimeline.Listener() {
        @Override public void breadcrumb(String kind, long duration, String detail) {
            CinemaMonitoring.breadcrumb(kind, duration, detail + " " + safeDetails());
        }
        @Override public void incident(String kind, long duration, String detail) {
            CinemaMonitoring.incident(kind, duration, detail + " " + safeDetails(), null);
        }
    });
    private boolean parsing;

    public CinemaPlaybackProbe(Supplier<String> details, BooleanSupplier requested) {
        this.details = details;
        this.requested = requested;
    }

    public void begin(boolean parse) {
        parsing = parse;
        timeline.begin(SystemClock.elapsedRealtime(), parse);
        schedule();
    }

    public void mediaStarted() {
        parsing = false;
        timeline.mediaStarted(SystemClock.elapsedRealtime());
        schedule();
    }

    public void stateChanged(int state, boolean playWhenReady, boolean hasVideo) {
        long now = SystemClock.elapsedRealtime();
        if (state == Player.STATE_BUFFERING) timeline.buffering(now, playWhenReady);
        else if (state == Player.STATE_READY) timeline.ready(now, hasVideo);
        else if (state == Player.STATE_ENDED) timeline.stop();
        schedule();
    }

    public void requested(boolean playWhenReady) {
        timeline.requested(SystemClock.elapsedRealtime(), playWhenReady);
        schedule();
    }

    public void firstFrame() {
        timeline.firstFrame(SystemClock.elapsedRealtime());
        schedule();
    }

    public void seek(int reason) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
            timeline.seek(SystemClock.elapsedRealtime());
        }
    }

    public void error(int errorCode) {
        CinemaMonitoring.incident("playback_error", 0, "code=" + errorCode + " " + safeDetails(), null);
    }

    public void parseError() {
        CinemaMonitoring.incident("parse_error", 0, safeDetails(), null);
        stop();
    }

    public void stop() {
        handler.removeCallbacks(tick);
        parsing = false;
        timeline.stop();
    }

    private void tick() {
        timeline.tick(SystemClock.elapsedRealtime(), parsing || requested.getAsBoolean());
        schedule();
    }

    private String safeDetails() {
        try { return details.get(); }
        catch (RuntimeException | LinkageError ignored) { return "details=unavailable"; }
    }

    private void schedule() {
        handler.removeCallbacks(tick);
        if (timeline.hasPendingWork() && (parsing || requested.getAsBoolean())) handler.postDelayed(tick, 1_000);
    }

    public static boolean hasSelectedVideo(Player player) {
        return player != null && player.getCurrentTracks().isTypeSelected(C.TRACK_TYPE_VIDEO);
    }
}
