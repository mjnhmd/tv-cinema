package com.fongmi.android.tv.source;

import com.fongmi.android.tv.utils.MpvLogCollector;

/**
 * Bounded diagnostics for the "continue watching" resume path.
 *
 * <p>Every line is written through {@link MpvLogCollector}, so it lands in the log the user exports
 * from 设置 → MPV 日志 (写入 Download/mpv_log_*.txt). When that collector is switched off the line is
 * still written to logcat under {@code CinemaResume}, so an adb session keeps working.
 *
 * <p>Nothing here changes playback behaviour: no state, no persistence, no network, and the position
 * tick is throttled so a two hour film cannot flood the export.
 */
public final class CinemaResumeTrace {

    public static final String TAG = "Resume";
    private static final int MAX_FIELD = 160;
    private static final long TICK_QUIET_MS = 60000;
    private static final long TICK_BACKWARD_MS = 3000;

    private static long lastTickAt;
    private static long lastTickPosition = Long.MIN_VALUE;

    private CinemaResumeTrace() {}

    public static void log(String message) {
        MpvLogCollector.log(TAG, message);
        if (!MpvLogCollector.isEnabled()) android.util.Log.i("CinemaResume", message);
    }

    /** Called once per playback start with the millisecond offset handed to the player. */
    public static void started(String source, long requestedMs) {
        lastTickAt = 0;
        lastTickPosition = Long.MIN_VALUE;
        log("start source=" + source + " requestedMs=" + requestedMs);
    }

    /** Called from the player clock; records the first tick and then only meaningful jumps. */
    public static void positionTick(long position, long duration) {
        long now = System.currentTimeMillis();
        boolean first = lastTickPosition == Long.MIN_VALUE;
        boolean jumpedBack = !first && position < lastTickPosition - TICK_BACKWARD_MS;
        boolean quiet = now - lastTickAt >= TICK_QUIET_MS;
        if (!first && !jumpedBack && !quiet) return;
        lastTickAt = now;
        lastTickPosition = position;
        log("tick position=" + position + " duration=" + duration);
    }

    /** Shortens a URL or episode title so a long export stays readable. */
    public static String brief(String value) {
        if (value == null) return "null";
        String trimmed = value.trim();
        if (trimmed.length() <= MAX_FIELD) return trimmed;
        return trimmed.substring(0, MAX_FIELD) + "…(" + trimmed.length() + ")";
    }
}
