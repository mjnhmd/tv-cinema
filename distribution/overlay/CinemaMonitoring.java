package com.fongmi.android.tv.monitoring;

import android.content.Context;
import android.os.Build;

import com.fongmi.android.tv.BuildConfig;
import com.tencent.bugly.CrashModule;
import com.tencent.bugly.crashreport.CrashReport;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class CinemaMonitoring {
    private static final String APP_ID = BuildConfig.CINEMA_BUGLY_APP_ID;

    private CinemaMonitoring() {}

    public static void start(Context context) {
        if (!isMainProcess(context) || APP_ID.isBlank()) return;
        CrashReport.UserStrategy strategy = new CrashReport.UserStrategy(context);
        strategy.setAppChannel(BuildConfig.FLAVOR_mode);
        CrashReport.initCrashReport(context.getApplicationContext(), APP_ID, BuildConfig.DEBUG, strategy);
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
