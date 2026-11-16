package com.fongmi.android.tv.monitoring;

final class CinemaPlaybackTimeline {
    static final long SLOW_START_MS = 8_000;
    static final long SLOW_BUFFER_MS = 5_000;

    interface Listener {
        void breadcrumb(String kind, long durationMs, String detail);
        void incident(String kind, long durationMs, String detail);
    }

    private final Listener listener;
    private long started = -1;
    private long phaseStarted = -1;
    private long bufferStarted = -1;
    private long lastSeek = -1;
    private long pausedSince = -1;
    private boolean ready;
    private boolean complete;
    private boolean buffering;
    private boolean startReported;
    private boolean bufferReported;
    private String phase = "idle";
    private String bufferCause = "playback";

    CinemaPlaybackTimeline(Listener listener) {
        this.listener = listener;
    }

    void begin(long now, boolean parse) {
        started = phaseStarted = now;
        bufferStarted = lastSeek = pausedSince = -1;
        ready = complete = buffering = startReported = bufferReported = false;
        phase = parse ? "parse" : "prepare";
        listener.breadcrumb("start", 0, phase);
    }

    void mediaStarted(long now) {
        if (started < 0) return;
        if ("parse".equals(phase)) listener.breadcrumb("parse_finished", now - phaseStarted, phase);
        phaseStarted = now;
        phase = "prepare";
    }

    void seek(long now) { lastSeek = now; }

    void buffering(long now, boolean requested) {
        if (started < 0) return;
        buffering = true;
        if (ready && complete && requested && bufferStarted < 0) {
            bufferStarted = now;
            bufferReported = false;
            bufferCause = lastSeek >= 0 && now - lastSeek <= 2_000 ? "after_seek" : "during_playback";
            listener.breadcrumb("buffer_start", 0, bufferCause);
        }
    }

    void requested(long now, boolean requested) {
        if (started < 0) return;
        if (!"parse".equals(phase)) {
            if (!requested && pausedSince < 0) pausedSince = now;
            if (requested && pausedSince >= 0) {
                long paused = Math.max(0, now - pausedSince);
                started += paused;
                phaseStarted += paused;
                pausedSince = -1;
            }
        }
        if (!buffering) return;
        if (!requested) bufferStarted = -1;
        else if (ready && bufferStarted < 0) buffering(now, true);
    }

    void ready(long now, boolean hasVideo) {
        if (started < 0) return;
        if (bufferStarted >= 0) {
            long elapsed = Math.max(0, now - bufferStarted);
            listener.breadcrumb("buffer_end", elapsed, bufferCause);
            if (elapsed >= SLOW_BUFFER_MS && !bufferReported) listener.incident("slow_buffer", elapsed, bufferCause);
        }
        bufferStarted = -1;
        buffering = false;
        ready = true;
        long activeNow = pausedSince >= 0 ? pausedSince : now;
        listener.breadcrumb("ready", Math.max(0, activeNow - started), hasVideo ? "video" : "audio_or_unknown");
        phase = "first_frame";
        if (!hasVideo) {
            if (!complete && !startReported && activeNow - started >= SLOW_START_MS) {
                listener.incident("slow_start", activeNow - started, "audio_or_unknown_ready");
                startReported = true;
            }
            complete = true;
        }
    }

    void firstFrame(long now) {
        if (started < 0 || complete) return;
        long elapsed = Math.max(0, (pausedSince >= 0 ? pausedSince : now) - started);
        listener.breadcrumb("first_frame", elapsed, phase);
        if (elapsed >= SLOW_START_MS && !startReported) listener.incident("slow_start", elapsed, phase);
        complete = true;
    }

    void tick(long now, boolean requested) {
        if (started < 0 || !requested) return;
        if (!complete && !startReported && now - started >= SLOW_START_MS) {
            startReported = true;
            listener.incident("slow_start", now - started, phase);
        }
        if (bufferStarted >= 0 && !bufferReported && now - bufferStarted >= SLOW_BUFFER_MS) {
            bufferReported = true;
            listener.incident("slow_buffer", now - bufferStarted, bufferCause);
        }
    }

    boolean hasPendingWork() { return started >= 0 && (!complete && !startReported || bufferStarted >= 0 && !bufferReported); }

    void stop() {
        started = phaseStarted = bufferStarted = lastSeek = pausedSince = -1;
        ready = complete = buffering = startReported = bufferReported = false;
        phase = "idle";
    }
}
