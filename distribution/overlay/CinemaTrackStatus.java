package com.fongmi.android.tv.playback;

import androidx.media3.common.Format;
import androidx.media3.common.Tracks;

import com.fongmi.android.tv.player.PlaybackSpecs;

public final class CinemaTrackStatus {
    private CinemaTrackStatus() {}

    public static String selected(Tracks tracks, int type, String inactive) {
        Format format = PlaybackSpecs.selectedFormat(tracks, type);
        if (format == null) return inactive;
        if (format.label != null && !format.label.isBlank()) return format.label;
        if (format.language != null && !format.language.isBlank() && !"und".equals(format.language)) return format.language;
        return format.sampleMimeType == null || format.sampleMimeType.isBlank() ? "—" : format.sampleMimeType;
    }
}
