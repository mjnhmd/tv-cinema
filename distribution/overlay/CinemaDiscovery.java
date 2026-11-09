package com.fongmi.android.tv.api.config;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class CinemaDiscovery {

    private static final String[] SLOTS = {"cinema_merged_slot0.json", "cinema_merged_slot1.json"};
    private static final String KEY_CANDIDATE = "cinema_merged_candidate_slot";
    private static final String TEMP = "cinema_merged_candidate.tmp";
    private static final int MAX_SOURCE_BYTES = 1024 * 1024;
    private static final Object LOCK = new Object();
    private static boolean running;

    private CinemaDiscovery() {
    }

    public interface Listener {
        void onFinished(CinemaSourceMerger.Result result);
        void onError();
    }

    public static boolean start(Listener listener) {
        OkHttpClient client = OkHttp.client().newBuilder().connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build();
        return start(CinemaSources.entries(), client, listener);
    }

    static boolean start(List<CinemaSources.Entry> entries, OkHttpClient client, Listener listener) {
        synchronized (LOCK) {
            if (running) return false;
            running = true;
        }
        Task.submit(() -> discover(entries, client, listener));
        return true;
    }

    public static boolean hasCandidate() {
        File file = candidateFile();
        return file.isFile() && file.length() > 0;
    }

    public static String candidateUrl() {
        return urlFor(com.github.catvod.utils.Prefers.getInt(KEY_CANDIDATE, 0));
    }

    public static Config candidateConfig() {
        return Config.find(candidateUrl(), BaseConfig.VOD).name("手动抓取 · 合并片源").update();
    }

    public static boolean isCandidateUrl(String url) {
        return urlFor(0).equals(url) || urlFor(1).equals(url);
    }

    public static String candidateContent(String url) throws IOException {
        if (!isCandidateUrl(url)) throw new IOException("Invalid candidate");
        File file = Path.files(url.substring("cinema://".length()));
        if (!file.isFile() || file.length() <= 0 || file.length() > 4 * 1024 * 1024) {
            throw new IOException("Missing candidate");
        }
        return Path.read(file);
    }

    private static File candidateFile() {
        int slot = Math.floorMod(com.github.catvod.utils.Prefers.getInt(KEY_CANDIDATE, 0), SLOTS.length);
        return Path.files(SLOTS[slot]);
    }

    private static String urlFor(int slot) {
        return "cinema://" + SLOTS[Math.floorMod(slot, SLOTS.length)];
    }

    private static void discover(List<CinemaSources.Entry> entries, OkHttpClient client, Listener listener) {
        CinemaSourceMerger.Result result = null;
        try {
            List<CinemaSourceMerger.Input> inputs = new ArrayList<>();
            for (CinemaSources.Entry entry : entries) {
                if (!entry.isVod()) continue;
                try {
                    inputs.add(new CinemaSourceMerger.Input(entry.name(), entry.url(), read(client, entry.url())));
                } catch (IOException ignored) {
                    inputs.add(new CinemaSourceMerger.Input(entry.name(), entry.url(), ""));
                }
            }
            CinemaSourceMerger.Result merged = CinemaSourceMerger.merge(inputs);
            if (!merged.ok()) throw new IOException("No valid source");
            save(merged.json());
            result = merged;
        } catch (Exception ignored) {
        } finally {
            synchronized (LOCK) {
                running = false;
            }
        }
        CinemaSourceMerger.Result completed = result;
        App.post(() -> {
            if (completed == null) listener.onError();
            else listener.onFinished(completed);
        });
    }

    private static String read(OkHttpClient client, String url) throws IOException {
        Request request = new Request.Builder().url(url).header("Accept", "application/json,text/plain,*/*").build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null || body.contentLength() > MAX_SOURCE_BYTES) throw new IOException("Invalid source size");
            try (InputStream input = body.byteStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > MAX_SOURCE_BYTES) throw new IOException("Source too large");
                    output.write(buffer, 0, count);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }

    private static void save(String json) throws IOException {
        String current = Config.vod().getUrl();
        int slot = urlFor(0).equals(current) ? 1 : urlFor(1).equals(current) ? 0
                : Math.floorMod(com.github.catvod.utils.Prefers.getInt(KEY_CANDIDATE, 0), SLOTS.length);
        File target = Path.files(SLOTS[slot]);
        File temp = Path.files(TEMP);
        try {
            try (FileOutputStream output = new FileOutputStream(temp)) {
                output.write(json.getBytes(StandardCharsets.UTF_8));
                output.getFD().sync();
            }
            if (!temp.renameTo(target)) throw new IOException("Cannot finalize candidate");
            com.github.catvod.utils.Prefers.put(KEY_CANDIDATE, slot);
        } finally {
            if (temp.exists() && !temp.delete()) temp.deleteOnExit();
        }
    }
}
