package com.fongmi.android.tv.monitoring;

import android.content.Context;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import com.fongmi.android.tv.BuildConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded, exportable record of what the user did and what the app answered.
 *
 * <p>Every event is one pre-formatted line kept in a fixed-size ring buffer, so the cost of a hit is
 * a string append plus a short {@code Log.d}; nothing touches disk, the main-looper payload, or the
 * render path. Chatty areas are capped per ten-second window, so a busy network or a held D-pad can
 * never flood the buffer or the exported file.
 *
 * <p>The user reads it through 设置 → MPV 日志 → 导出 (Download/cinema_events_*.txt), or through
 * {@code adb logcat -s CinemaEvents} without touching the TV.
 */
public final class CinemaEventLog {

    private static final String TAG = "CinemaEvents";
    private static final int MAX_ENTRIES = 2000;
    private static final int AREA_QUOTA = 60;
    private static final long AREA_WINDOW_MS = 10_000L;
    private static final int MAX_DETAIL = 220;
    private static final long START_MS = SystemClock.elapsedRealtime();
    private static final String SESSION = UUID.randomUUID().toString().substring(0, 8);
    private static final List<String> entries = new ArrayList<>();
    private static final Map<String, int[]> windows = new HashMap<>();
    private static final AtomicInteger dropped = new AtomicInteger();
    private static final AtomicInteger appended = new AtomicInteger();
    private static int base;
    private static boolean started;

    private CinemaEventLog() {}

    /** Records what this install is, once per process. */
    public static void app(Context context) {
        if (started) return;
        started = true;
        event("app", "start", "version=" + BuildConfig.VERSION_NAME + "/" + BuildConfig.VERSION_CODE
                + " pkg=" + BuildConfig.APPLICATION_ID + " session=" + SESSION
                + " debug=" + BuildConfig.DEBUG);
        CinemaDeviceInfo.start(context);
    }

    public static void event(String area, String action) {
        event(area, action, null);
    }

    public static void event(String area, String action, String detail) {
        if (area == null || action == null) return;
        if (!allow(area)) return;
        StringBuilder line = new StringBuilder(96);
        line.append('[').append(elapsed()).append("] ").append(area).append(' ').append(action);
        if (detail != null && !detail.isEmpty()) line.append(' ').append(trim(detail));
        append(line.toString());
    }

    public static String session() {
        return SESSION;
    }

    public static int size() {
        synchronized (entries) {
            return entries.size();
        }
    }

    /** Monotonic count of appended lines, so an uploader can tell whether anything is new. */
    public static int total() {
        return appended.get();
    }

    public static int dropped() {
        return dropped.get();
    }

    public static String elapsed() {
        long millis = SystemClock.elapsedRealtime() - START_MS;
        return String.format(Locale.US, "+%d.%03ds", millis / 1000, millis % 1000);
    }

    public static List<String> snapshot() {
        synchronized (entries) {
            return new ArrayList<>(entries);
        }
    }

    /** Global index of the oldest line still buffered; anything below it has been evicted. */
    public static int firstAvailable() {
        synchronized (entries) {
            return base;
        }
    }

    /** Lines from global index {@code from} to the newest, oldest first. */
    public static List<String> since(int from) {
        synchronized (entries) {
            int start = Math.max(0, from - base);
            List<String> lines = new ArrayList<>(entries.size() - start);
            for (int i = start; i < entries.size(); i++) lines.add(entries.get(i));
            return lines;
        }
    }

    /** Renders the whole buffered log; returns null when nothing has been recorded yet. */
    public static String report() {
        List<String> lines = snapshot();
        if (lines.isEmpty()) return null;
        StringBuilder builder = new StringBuilder(8192);
        builder.append("=== TV Cinema 事件日志 ===\n");
        builder.append("导出时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date())).append('\n');
        builder.append("会话: ").append(SESSION).append('\n');
        builder.append("应用: ").append(BuildConfig.VERSION_NAME).append('/').append(BuildConfig.VERSION_CODE)
                .append(" (").append(BuildConfig.APPLICATION_ID).append(")\n");
        builder.append("设备: ").append(CinemaDeviceInfo.line()).append('\n');
        builder.append("条数: ").append(lines.size()).append(" 丢弃(限流): ").append(dropped()).append('\n');
        builder.append("==================\n\n");
        for (String line : lines) builder.append(line).append('\n');
        return builder.toString();
    }

    /** Writes every buffered event to Download/cinema_events_*.txt; returns the path or null. */
    public static String export(Context context) {
        String body = report();
        if (body == null) return null;
        try {
            File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!directory.exists()) directory.mkdirs();
            String name = "cinema_events_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".txt";
            File file = new File(directory, name);
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(body);
            }
            return file.getAbsolutePath();
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "导出事件日志失败: " + e);
            return null;
        }
    }

    private static void append(String line) {
        appended.incrementAndGet();
        synchronized (entries) {
            entries.add(line);
            if (entries.size() > MAX_ENTRIES) {
                entries.remove(0);
                base++;
            }
        }
        Log.d(TAG, line);
    }

    private static boolean allow(String area) {
        long window = SystemClock.elapsedRealtime() / AREA_WINDOW_MS;
        synchronized (windows) {
            int[] state = windows.get(area);
            if (state == null) {
                windows.put(area, new int[]{(int) window, 1});
                return true;
            }
            if (state[0] != (int) window) {
                state[0] = (int) window;
                state[1] = 1;
                return true;
            }
            if (state[1] >= AREA_QUOTA) {
                dropped.incrementAndGet();
                return false;
            }
            state[1]++;
            return true;
        }
    }

    private static String trim(String detail) {
        String value = detail.replace('\n', ' ').replace('\r', ' ');
        return value.length() <= MAX_DETAIL ? value : value.substring(0, MAX_DETAIL) + "…(" + value.length() + ")";
    }
}
