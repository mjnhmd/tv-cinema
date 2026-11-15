package com.fongmi.android.tv.api.config;

import android.view.View;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.impl.UpdateListener;
import com.fongmi.android.tv.ui.dialog.UpdateDialog;
import com.github.catvod.utils.Prefers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class CinemaMobileUiAndroidTest {
    private final CinemaMobileTestSupport support = new CinemaMobileTestSupport();
    private final UiDevice device = UiDevice.getInstance(support.instrumentation);

    @Before public void setup() throws Exception { support.start(); }
    @After public void cleanup() throws Exception { support.close(); }

    @Test public void phoneSettingsCatalogAndCancelledDiscoveryPreserveCurrentSource() throws Exception {
        String pkg = support.instrumentation.getTargetContext().getPackageName();
        assertEquals("TV Cinema", support.activity.getApplicationInfo().loadLabel(support.activity.getPackageManager()).toString());
        support.await(() -> support.activity.findViewById(R.id.setting).isShown(), "Phone navigation is ready");
        assertTrue(device.hasObject(By.res(pkg, "navigation")));
        device.findObject(By.res(pkg, "setting")).click();
        support.await(() -> support.activity.findViewById(R.id.builtinSources) != null
                && support.activity.findViewById(R.id.builtinSources).isShown(), "Cinema settings rows appear");
        assertTrue(device.hasObject(By.res(pkg, "discoverSources")));
        device.takeScreenshot(new File(support.activity.getExternalFilesDir(null), "cinema-mobile-settings.png"));
        device.findObject(By.res(pkg, "builtinSources")).click();
        support.instrumentation.waitForIdleSync();
        assertTrue(CinemaSources.catalogEntries().size() >= 6);
        assertTrue(device.hasObject(By.textContains("饭太硬")));
        assertEquals(support.configUrl, Config.vod().getUrl());
        device.pressBack();
        String before = CinemaDiscovery.hasCandidate()
                ? CinemaDiscovery.candidateContent(CinemaDiscovery.candidateUrl()) : "";
        device.findObject(By.res(pkg, "discoverSources")).click();
        support.instrumentation.waitForIdleSync();
        assertTrue(device.hasObject(By.text(support.activity.getString(R.string.cinema_discover_start))));
        device.pressBack();
        assertEquals(before, CinemaDiscovery.hasCandidate()
                ? CinemaDiscovery.candidateContent(CinemaDiscovery.candidateUrl()) : "");
        assertEquals(support.configUrl, Config.vod().getUrl());
        support.main(() -> {
            Prefers.getPrefers().edit().remove("cinema_sources_seeded_version").commit();
            CinemaSources.install();
        });
        assertEquals("Existing phone subscription is preserved", support.configUrl, Config.vod().getUrl());
    }

    @Test public void phoneUpdateTelemetryAndBackCancellationWork() {
        AtomicInteger cancelled = new AtomicInteger();
        UpdateDialog dialog = UpdateDialog.create().title("Cinema 更新").desc("手机更新线路测试")
                .listener(new UpdateListener() {
                    @Override public void onConfirm(View view) { view.setEnabled(false); }
                    @Override public void onCancel(View view) { cancelled.incrementAndGet(); }
                });
        support.main(() -> {
            dialog.show(support.activity);
            support.activity.getSupportFragmentManager().executePendingTransactions();
            dialog.setTesting();
            dialog.setSource(2, 3);
            dialog.setProgress(37);
            dialog.setTransfer(1572864, 125);
        });
        support.instrumentation.waitForIdleSync();
        TextView status = support.read(() -> dialog.getDialog().findViewById(R.id.updateStatus));
        assertTrue(support.read(() -> status.getText().toString().contains("1.5 MB/s")));
        assertTrue(support.read(() -> status.getText().toString().contains("2:05")));
        device.takeScreenshot(new File(support.activity.getExternalFilesDir(null), "cinema-mobile-update.png"));
        device.pressBack();
        support.instrumentation.waitForIdleSync();
        assertEquals(1, cancelled.get());
    }
}
