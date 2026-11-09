package com.fongmi.android.tv.api.config;

import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Downloads one of the reviewed public wallpaper feeds with bounded fallback. */
public final class CinemaWallpaper {

    private static final String TAG = "CinemaWallpaper";
    private static final String KEY_INDEX = "cinema_wallpaper_source_index";
    private static final long TIMEOUT = 10_000L;
    private static final int MAX_JSON = 64 * 1024;
    private static final int MAX_IMAGE = 24 * 1024 * 1024;
    private static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 9) TVCinema/0.1";
    private static final String CANDIDATE_PREFIX = "wallpaper_candidate_";
    private static final int CANDIDATE_SLOTS = 2;
    private static final Object CANDIDATE_LOCK = new Object();
    private static final AtomicLong REQUEST = new AtomicLong();
    private static final Provider[] PROVIDERS = {
            new Provider("星空壁纸", "https://v2.xxapi.cn/api/wallpaper"),
            new Provider("壁纸站", "https://wp.upx8.com/api.php"),
            new Provider("Peapix Bing", "https://peapix.com/bing/feed?country=cn"),
            new Provider("Wuw Bing", "https://bing-img.wuw.li/random.php"),
            new Provider("MXin", "https://api.mxin.me/api/v1/wallpaper"),
            new Provider("XSOT Bing", "https://api.xsot.cn/bing?jump=true")
    };

    private CinemaWallpaper() {
    }

    public static void load(Callback callback) {
        long request = REQUEST.incrementAndGet();
        OkHttp.cancel(TAG);
        Task.submit(() -> run(request, callback));
    }

    static String candidateName(long request) {
        return CANDIDATE_PREFIX + Math.floorMod(request, CANDIDATE_SLOTS);
    }

    static int indexFor(int start, int offset) {
        return Math.floorMod(start + offset, PROVIDERS.length);
    }

    static boolean isCanceled(long request, long current, boolean interrupted) {
        return request != current || interrupted;
    }

    static boolean isCancellation(Throwable error) {
        boolean canceled = false;
        boolean timedOut = false;
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedException || cause instanceof CancellationException) return true;
            canceled |= cause instanceof InterruptedIOException || "Canceled".equals(cause.getMessage());
            timedOut |= cause instanceof SocketTimeoutException;
        }
        return canceled && !timedOut;
    }

    static String resolveSource(String source) throws IOException {
        for (Provider provider : PROVIDERS) {
            if (provider.endpoint().equals(source)) return resolve(provider);
        }
        return source;
    }

    static boolean isProvider(String source) {
        for (Provider provider : PROVIDERS) if (provider.endpoint().equals(source)) return true;
        return false;
    }

    private static void run(long request, Callback callback) {
        int start = Math.floorMod(Prefers.getInt(KEY_INDEX), PROVIDERS.length);
        for (int offset = 0; offset < PROVIDERS.length; offset++) {
            if (canceled(request)) return;
            int index = indexFor(start, offset);
            Provider provider = PROVIDERS[index];
            try {
                if (!install(request, index, resolve(provider))) return;
                App.post(() -> {
                    if (canceled(request)) return;
                    WallConfig.get().postEvent();
                    callback.success();
                });
                return;
            } catch (Throwable error) {
                if (isCancellation(error) || canceled(request)) return;
                Log.w(TAG, "Wallpaper provider failed: " + provider.name(), error);
            }
        }
        App.post(() -> {
            if (canceled(request)) return;
            callback.error(App.get().getString(R.string.cinema_wall_failed));
        });
    }

    private static boolean install(long request, int index, String imageUrl) throws Throwable {
        if (TextUtils.isEmpty(imageUrl)) throw new IOException("No image URL");
        File candidate = Path.files(candidateName(request));
        synchronized (CANDIDATE_LOCK) {
            if (canceled(request)) return false;
            try {
                download(imageUrl, candidate);
                validateHeader(candidate);
                if (canceled(request)) return false;
                Provider provider = PROVIDERS[index];
                Config config = Config.find(provider.endpoint(), BaseConfig.WALL);
                config.setName("在线壁纸 · " + provider.name());
                config.setTime(System.currentTimeMillis());
                WallConfig.get().apply(candidate, config);
                Prefers.put(KEY_INDEX, (index + 1) % PROVIDERS.length);
                return true;
            } finally {
                Path.clear(candidate);
            }
        }
    }

    private static boolean canceled(long request) {
        return isCanceled(request, REQUEST.get(), Thread.currentThread().isInterrupted());
    }

    private static String resolve(Provider provider) throws IOException {
        String current = provider.endpoint();
        for (int redirect = 0; redirect <= 6; redirect++) {
            try (Response response = execute(current)) {
                if (isRedirect(response.code())) {
                    current = redirect(response, current);
                    continue;
                }
                if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
                String type = contentType(response);
                if (type.startsWith("image/")) return current;
                String body = readText(response, MAX_JSON);
                String image = imageUrl(body);
                if (!TextUtils.isEmpty(image)) return image;
                throw new IOException("No image URL in response");
            }
        }
        throw new IOException("Too many redirects");
    }

    private static void download(String url, File output) throws IOException {
        String current = url;
        for (int redirect = 0; redirect <= 6; redirect++) {
            try (Response response = execute(current)) {
                if (isRedirect(response.code())) {
                    current = redirect(response, current);
                    continue;
                }
                if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
                ResponseBody body = response.body();
                if (body == null) throw new IOException("Empty response");
                long length = body.contentLength();
                if (length > MAX_IMAGE) throw new IOException("Image is too large");
                copy(body.byteStream(), output);
                return;
            }
        }
        throw new IOException("Too many redirects");
    }

    private static Response execute(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .tag(TAG)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/*,application/json;q=0.9,*/*;q=0.8")
                .build();
        return OkHttp.noRedirect(TIMEOUT).newCall(request).execute();
    }

    private static void copy(InputStream stream, File output) throws IOException {
        Path.create(output);
        long total = 0;
        byte[] buffer = new byte[16 * 1024];
        try (InputStream input = new BufferedInputStream(stream);
             FileOutputStream target = new FileOutputStream(output)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Canceled");
                total += read;
                if (total > MAX_IMAGE) throw new IOException("Image is too large");
                target.write(buffer, 0, read);
            }
        } catch (IOException error) {
            Path.clear(output);
            throw error;
        }
        if (total < 1024) {
            Path.clear(output);
            throw new IOException("Image is empty");
        }
    }

    private static void validateHeader(File file) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        long pixels = (long) options.outWidth * options.outHeight;
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Image header is invalid");
        if (pixels > 48_000_000L) throw new IOException("Image dimensions are too large");
    }

    private static String readText(Response response, int limit) throws IOException {
        ResponseBody body = response.body();
        if (body == null) throw new IOException("Empty response");
        if (body.contentLength() > limit) throw new IOException("Response is too large");
        try (InputStream input = body.byteStream()) {
            byte[] data = new byte[limit + 1];
            int total = 0;
            int read;
            while (total < data.length && (read = input.read(data, total, data.length - total)) != -1) total += read;
            if (total > limit) throw new IOException("Response is too large");
            return new String(data, 0, total, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    static String imageUrl(String body) {
        try {
            JsonElement root = Json.parse(body);
            String value = imageUrl(root);
            return value != null && value.startsWith("http") ? value : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String imageUrl(JsonElement root) {
        if (root == null || root.isJsonNull()) return "";
        if (root.isJsonPrimitive()) {
            String value = root.getAsString();
            return value.startsWith("http") ? value : "";
        }
        if (root.isJsonArray()) {
            JsonArray array = root.getAsJsonArray();
            for (JsonElement item : array) {
                String value = imageUrl(item);
                if (value != null && !value.isEmpty()) return value;
            }
            return "";
        }
        JsonObject object = root.getAsJsonObject();
        String[] keys = {"data", "fullUrl", "imageUrl", "url", "image", "src"};
        for (String key : keys) {
            JsonElement item = object.get(key);
            String value = imageUrl(item);
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
    }

    private static String contentType(Response response) {
        String value = response.header("Content-Type", "");
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static boolean isRedirect(int code) {
        return code == 300 || code == 301 || code == 302 || code == 303 || code == 307 || code == 308;
    }

    private static String redirect(Response response, String source) throws IOException {
        String location = response.header("Location");
        HttpUrl base = HttpUrl.parse(source);
        HttpUrl target = base == null || location == null ? null : base.resolve(location);
        if (target == null) throw new IOException("Invalid redirect");
        return target.toString();
    }

    private record Provider(String name, String endpoint) {
    }
}
