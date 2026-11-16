package com.fongmi.android.tv.monitoring;

import androidx.media3.common.Format;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.analytics.AnalyticsListener;

public final class CinemaExoAnalytics implements AnalyticsListener {
    private final ExoPlayer player;
    private String decoder = "unknown";

    public CinemaExoAnalytics(ExoPlayer player) {
        this.player = player;
    }

    @Override public void onVideoDecoderInitialized(EventTime eventTime, String name, long initializedMs, long durationMs) {
        decoder = safeToken(name);
        CinemaMonitoring.breadcrumb("video_decoder", durationMs, "engine=EXO decoder=" + decoder);
    }

    @Override public void onVideoInputFormatChanged(EventTime eventTime, Format format, DecoderReuseEvaluation evaluation) {
        CinemaMonitoring.breadcrumb("video_format", 0, "engine=EXO mime=" + safeToken(format.sampleMimeType)
                + " width=" + Math.max(0, format.width) + " height=" + Math.max(0, format.height)
                + " fps=" + Math.max(0, Math.round(format.frameRate)) + " bitrate=" + Math.max(0, format.averageBitrate));
    }

    @Override public void onDroppedVideoFrames(EventTime eventTime, int dropped, long elapsedMs) {
        Format format = player.getVideoFormat();
        String details = "engine=EXO decoder=" + decoder + " dropped=" + dropped
                + " mime=" + safeToken(format == null ? null : format.sampleMimeType)
                + " width=" + (format == null ? 0 : Math.max(0, format.width))
                + " height=" + (format == null ? 0 : Math.max(0, format.height));
        CinemaMonitoring.breadcrumb("dropped_frames", elapsedMs, details);
        if (dropped >= 20 && elapsedMs > 0 && dropped * 1_000L >= 2 * elapsedMs) {
            CinemaMonitoring.incident("video_dropped", elapsedMs, details, null);
        }
    }

    public static String safeToken(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String safe = value.replaceAll("[^A-Za-z0-9_.-]", "_");
        return safe.length() > 80 ? safe.substring(0, 80) : safe;
    }
}
