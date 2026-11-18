package com.fongmi.android.tv.monitoring;

import android.os.SystemClock;

import java.io.IOException;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;

/**
 * One line per finished HTTP call — method, host, short path, status, elapsed.
 *
 * <p>Media segments and image loads are skipped: playback would otherwise emit hundreds of events a
 * minute, bury the user actions that actually matter and cost measurable time on the target TV. The
 * listener only formats a string when a call finishes, so it adds no work to the request path.
 */
public final class CinemaNetworkTrace implements EventListener.Factory {

    private static final CinemaNetworkTrace INSTANCE = new CinemaNetworkTrace();
    private static final String[] SKIP_SUFFIX = {".mp4", ".m3u8", ".m3u", ".ts", ".m4s", ".mpd", ".mkv", ".flv", ".aac", ".mp3", ".m4a", ".jpg", ".jpeg", ".png", ".webp", ".gif", ".ico", ".svg"};
    private static final String[] SKIP_CONTAINS = {"m3u8", "playlist", "/segment", ".key?"};
    private static final int MAX_PATH = 60;

    private CinemaNetworkTrace() {}

    public static CinemaNetworkTrace factory() {
        return INSTANCE;
    }

    @Override
    public EventListener create(Call call) {
        return new Trace(call);
    }

    private static boolean skip(Request request, HttpUrl url) {
        if (request.header("Range") != null) return true;
        if (request.header("X-Cinema-Log") != null) return true;
        String path = url.encodedPath().toLowerCase(Locale.US);
        for (String suffix : SKIP_SUFFIX) if (path.endsWith(suffix)) return true;
        String full = url.toString().toLowerCase(Locale.US);
        for (String token : SKIP_CONTAINS) if (full.contains(token)) return true;
        return false;
    }

    private static final class Trace extends EventListener {

        private final String target;
        private final boolean skipped;
        private long startedAt;
        private int code;

        Trace(Call call) {
            Request request = call.request();
            HttpUrl url = request.url();
            String path = url.encodedPath();
            this.target = request.method() + " " + url.host() + (path.length() > MAX_PATH ? path.substring(0, MAX_PATH) : path);
            this.skipped = skip(request, url);
        }

        @Override
        public void callStart(Call call) {
            startedAt = SystemClock.elapsedRealtime();
        }

        @Override
        public void responseHeadersEnd(Call call, Response response) {
            code = response.code();
        }

        @Override
        public void callEnd(Call call) {
            if (!skipped) CinemaEventLog.event("net", target, code + " " + (SystemClock.elapsedRealtime() - startedAt) + "ms");
        }

        @Override
        public void callFailed(Call call, IOException error) {
            if (!skipped) CinemaEventLog.event("net", target, "FAIL " + error.getClass().getSimpleName() + " " + (SystemClock.elapsedRealtime() - startedAt) + "ms");
        }
    }
}
