package com.fongmi.android.tv.monitoring;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;

import com.fongmi.android.tv.BuildConfig;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * One-time snapshot of the parameters that make a log report useful: app build, device model, system
 * version, ABI, screen, locale, storage, connection type and current IP addresses.
 *
 * <p>Collection runs on a private daemon thread because interface and storage queries block; every
 * probe is individually guarded so an absent permission only drops that field.
 */
public final class CinemaDeviceInfo {

    private static final String TAG = "CinemaDevice";
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "cinema-device");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile String line;

    private CinemaDeviceInfo() {}

    public static void start(Context context) {
        if (line != null) return;
        Context application = context.getApplicationContext();
        WORKER.execute(() -> {
            String value = collect(application);
            line = value;
            CinemaEventLog.event("app", "device", value);
        });
    }

    /** Cached device line; returns "pending" until the background probe finishes. */
    public static String line() {
        String value = line;
        return value == null ? "pending" : value;
    }

    private static String collect(Context context) {
        List<String> parts = new ArrayList<>();
        parts.add("app=" + BuildConfig.VERSION_NAME + "/" + BuildConfig.VERSION_CODE);
        parts.add("flavor=" + safe(() -> BuildConfig.FLAVOR_mode));
        parts.add("pkg=" + BuildConfig.APPLICATION_ID);
        parts.add("sdk=" + Build.VERSION.SDK_INT + "(" + Build.VERSION.RELEASE + ")");
        parts.add("brand=" + Build.BRAND + " model=" + Build.MODEL);
        parts.add("device=" + Build.DEVICE + " hw=" + Build.HARDWARE);
        parts.add("abis=" + join(Build.SUPPORTED_ABIS));
        parts.add("cpu=" + Build.SUPPORTED_ABIS.length + "core arch=" + System.getProperty("os.arch"));
        parts.add("screen=" + screen(context));
        parts.add("locale=" + Locale.getDefault());
        parts.add("tz=" + TimeZone.getDefault().getID());
        parts.add("uptime_s=" + SystemClock.elapsedRealtime() / 1000);
        parts.add("storage=" + storage(context));
        parts.add("net=" + networkType(context));
        parts.add("ip=" + addresses());
        return String.join(" ", parts);
    }

    private static String screen(Context context) {
        return safe(() -> {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            return metrics.widthPixels + "x" + metrics.heightPixels + "@" + metrics.density;
        });
    }

    private static String storage(Context context) {
        return safe(() -> {
            StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long free = stat.getAvailableBytes() / (1024 * 1024 * 1024);
            long total = stat.getTotalBytes() / (1024 * 1024 * 1024);
            return free + "/" + total + "GB";
        });
    }

    private static String networkType(Context context) {
        return safe(() -> {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network network = manager == null ? null : manager.getActiveNetwork();
            NetworkCapabilities capabilities = network == null ? null : manager.getNetworkCapabilities(network);
            if (capabilities == null) return "offline";
            String transport = "other";
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transport = "wifi";
            else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) transport = "cellular";
            else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transport = "ethernet";
            else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) transport = "vpn";
            return transport + " metered=" + !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        });
    }

    private static String addresses() {
        return safe(() -> {
            List<String> found = new ArrayList<>();
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (networkInterface.isLoopback() || !networkInterface.isUp()) continue;
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address.isLoopbackAddress() || address.isLinkLocalAddress()) continue;
                    boolean v4 = address instanceof Inet4Address;
                    if (!v4 && address.isAnyLocalAddress()) continue;
                    found.add(networkInterface.getName() + "=" + address.getHostAddress());
                }
            }
            return found.isEmpty() ? "none" : join(found.toArray(new String[0]));
        });
    }

    private static String join(String[] values) {
        if (values == null || values.length == 0) return "none";
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append(',');
            builder.append(value);
        }
        return builder.toString();
    }

    private static String safe(Probe probe) {
        try {
            String value = probe.get();
            return value == null ? "unknown" : value;
        } catch (Exception | LinkageError e) {
            Log.w(TAG, "probe unavailable: " + e);
            return "unknown";
        }
    }

    private interface Probe {
        String get() throws Exception;
    }
}
