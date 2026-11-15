package com.fongmi.android.tv.api.config;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.github.catvod.utils.Prefers;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import static org.junit.Assert.*;

public final class CinemaMobileTestSupport {
    public final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    public final MockWebServer server = new MockWebServer();
    public HomeActivity activity;
    public String configUrl;

    public void start() throws Exception {
        assertEquals("Disposable emulator app only", "com.mjnhmd.tvcinema.mobile.probe",
                instrumentation.getTargetContext().getPackageName());
        server.start(InetAddress.getByName("127.0.0.1"), 0);
        server.setDispatcher(new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                String body = request.getPath().startsWith("/config")
                        ? "{\"sites\":[{\"key\":\"mobile-fixture\",\"name\":\"手机测试片源\","
                        + "\"type\":1,\"api\":\"" + url("/api") + "\"}]}"
                        : request.getPath().startsWith("/live") ? "#EXTM3U\n"
                        : "{\"class\":[{\"type_id\":\"1\",\"type_name\":\"本地测试\"}],\"list\":[]}";
                return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
            }
        });
        main(() -> {
            Prefers.put("ota_last_check", System.currentTimeMillis());
            Prefers.put("incognito", true);
            Prefers.put("preload", false);
            Prefers.put("preload_next", false);
            CinemaSources.install();
            configUrl = url("/config.json");
            Config.find(configUrl, BaseConfig.VOD).name("手机本地测试").update();
            Config.find(url("/live.m3u"), BaseConfig.LIVE).name("本地直播").update();
        });
        activity = (HomeActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(() -> configUrl.equals(VodConfig.get().getConfig().getUrl())
                && !VodConfig.get().getHome().isEmpty(), "Controlled phone configuration loads");
    }

    public void close() throws Exception {
        main(() -> { if (activity != null) activity.finish(); });
        instrumentation.waitForIdleSync();
        server.shutdown();
    }

    public void main(Runnable action) {
        instrumentation.runOnMainSync(action);
    }

    public String url(String path) {
        return "http://127.0.0.1:" + server.getPort() + path;
    }

    public <Value> Value read(Supplier<Value> supplier) {
        AtomicReference<Value> value = new AtomicReference<>();
        main(() -> value.set(supplier.get()));
        return value.get();
    }

    public void await(BooleanSupplier condition, String message) {
        long deadline = SystemClock.uptimeMillis() + 15000;
        while (SystemClock.uptimeMillis() < deadline) {
            if (read(condition::getAsBoolean)) return;
            SystemClock.sleep(100);
        }
        fail(message);
    }
}
