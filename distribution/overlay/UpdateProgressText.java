package com.fongmi.android.tv.update;

import java.util.Locale;

/** Compact numeric values; the dialog owns translated status and unit labels. */
public final class UpdateProgressText {
    private UpdateProgressText() {}

    public static String speed(long bytesPerSecond) {
        if (bytesPerSecond >= 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024));
        return String.format(Locale.ROOT, "%d KB/s", Math.max(0, bytesPerSecond) / 1024);
    }

    public static String duration(long seconds) {
        if (seconds < 0) return "";
        if (seconds >= 3600) return String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
