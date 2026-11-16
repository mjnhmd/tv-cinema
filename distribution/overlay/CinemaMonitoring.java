package com.fongmi.android.tv.monitoring;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import com.fongmi.android.tv.BuildConfig;
import com.tencent.bugly.CrashModule;
import com.tencent.bugly.crashreport.BuglyLog;
import com.tencent.bugly.crashreport.CrashReport;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

public final class CinemaMonitoring {
    private static final String APP_ID = BuildConfig.CINEMA_BUGLY_APP_ID;
    private static final String TAG = "CinemaPerf";
    private static final String PRODUCT_PACKAGE = "com.mjnhmd.tvcinema";
    private static final ExecutorService REPORTS = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cinema-incident");
        thread.setDaemon(true);
        return thread;
    });
    private static CinemaIncidentGate gate = new CinemaIncidentGate();
    private static volatile BiConsumer<String, StackTraceElement[]> testSink;
    private static CinemaUiWatchdog uiWatchdog;

    private CinemaMonitoring() {}

    public static void start(Context context) {
        if (!isMainProcess(context) || APP_ID.isBlank()) return;
        if (PRODUCT_PACKAGE.equals(BuildConfig.APPLICATION_ID)) {
            CrashReport.UserStrategy strategy = new CrashReport.UserStrategy(context);
            strategy.setAppChannel(BuildConfig.FLAVOR_mode);
            CrashReport.initCrashReport(context.getApplicationContext(), APP_ID, BuildConfig.DEBUG, strategy);
        }
        try { uiWatchdog = new CinemaUiWatchdog(); }
        catch (RuntimeException | LinkageError failure) { Log.w(TAG, "ui monitoring unavailable"); }
    }

    public static void onResumed(Activity activity) {
        if (uiWatchdog != null) uiWatchdog.resume(activity);
    }

    public static void onPaused(Activity activity) {
        if (uiWatchdog != null) uiWatchdog.pause(activity);
    }

    public static void breadcrumb(String kind, long durationMs, String detail) {
        String message = message(kind, durationMs, detail);
        Log.i(TAG, message);
        if (PRODUCT_PACKAGE.equals(BuildConfig.APPLICATION_ID)) {
            try { BuglyLog.i(TAG, message); }
            catch (RuntimeException | LinkageError ignored) { }
        }
    }

    public static void incident(String kind, long durationMs, String detail, StackTraceElement[] stack) {
        BiConsumer<String, StackTraceElement[]> observer = testSink;
        if (observer == null && (!PRODUCT_PACKAGE.equals(BuildConfig.APPLICATION_ID) || APP_ID.isBlank())) return;
        if (!gate.acquire(kind, SystemClock.elapsedRealtime())) return;
        String message = message(kind, durationMs, detail);
        REPORTS.execute(() -> {
            if (observer != null) {
                observer.accept(message, stack);
                return;
            }
            if (!CrashModule.getInstance().hasInitialized()) return;
            try {
                BuglyLog.w(TAG, message);
                CinemaPerformanceIncident incident = new CinemaPerformanceIncident(message);
                if (stack != null && stack.length > 0) incident.setStackTrace(stack);
                Thread reportThread = stack == null ? Thread.currentThread() : android.os.Looper.getMainLooper().getThread();
                CrashReport.postCatchedException(incident, reportThread);
            } catch (RuntimeException | LinkageError ignored) {
                Log.w(TAG, "incident submission unavailable");
            }
        });
    }

    static void setTestSink(BiConsumer<String, StackTraceElement[]> observer) {
        if (!BuildConfig.DEBUG) throw new IllegalStateException("Test sink is available only in debug builds");
        gate = new CinemaIncidentGate();
        testSink = observer;
    }

    private static String message(String kind, long durationMs, String detail) {
        String safe = detail == null ? "" : detail.replaceAll("[^A-Za-z0-9_.=,:+ -]", "_");
        if (safe.length() > 320) safe = safe.substring(0, 320);
        return "event=" + kind + " duration_ms=" + Math.max(0, durationMs) + " " + safe;
    }

    private static final class CinemaPerformanceIncident extends RuntimeException {
        CinemaPerformanceIncident(String message) { super(message); }
    }

    public static boolean reportPlaybackProblem(Context context) {
        if (!isMainProcess(context) || APP_ID.isBlank()) return false;
        if (!CrashModule.getInstance().hasInitialized()) return false;
        try {
            CrashReport.postCatchedException(new IllegalStateException("User reported a playback problem"));
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean isMainProcess(Context context) {
        String processName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? android.app.Application.getProcessName() : readProcessName();
        String packageName = context.getApplicationInfo().processName;
        return packageName != null && packageName.equals(processName);
    }

    private static String readProcessName() {
        try (FileInputStream input = new FileInputStream("/proc/self/cmdline")) {
            byte[] bytes = new byte[256];
            int length = input.read(bytes);
            if (length <= 0) return "";
            int end = 0;
            while (end < length && bytes[end] != 0) end++;
            return new String(bytes, 0, end, StandardCharsets.UTF_8);
        } catch (IOException | SecurityException ignored) {
            return "";
        }
    }
}
