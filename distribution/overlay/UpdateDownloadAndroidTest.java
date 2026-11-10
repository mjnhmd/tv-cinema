package com.fongmi.android.tv.update;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.impl.UpdateListener;
import com.fongmi.android.tv.ui.activity.SettingActivity;
import com.fongmi.android.tv.ui.dialog.UpdateDialog;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Actual API28 dialog geometry/status and D-pad cancellation; no external download/install. */
@RunWith(AndroidJUnit4.class)
public final class UpdateDownloadAndroidTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final AtomicInteger cancelled = new AtomicInteger();
    private SettingActivity activity;
    private UpdateDialog dialog;

    @Before public void setup() {
        assertEquals("Disposable update validation app only", "com.fongmi.android.tv.updateprobe",
                instrumentation.getTargetContext().getPackageName());
        activity = (SettingActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), SettingActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        instrumentation.runOnMainSync(() -> {
            dialog = UpdateDialog.create().title("Cinema 更新").desc("优化下载线路选择和慢速切换").listener(new UpdateListener() {
                @Override public void onConfirm(View view) { view.setEnabled(false); }
                @Override public void onCancel(View view) { cancelled.incrementAndGet(); dialog.dismiss(); }
            }).show(activity);
            activity.getSupportFragmentManager().executePendingTransactions();
        });
        instrumentation.waitForIdleSync();
    }

    @After public void cleanup() {
        instrumentation.runOnMainSync(() -> {
            if (dialog != null) dialog.dismissAllowingStateLoss();
            if (activity != null) activity.finish();
        });
    }

    @Test public void realDialogKeepsTelemetryReadableAndCancelReachable() throws Exception {
        instrumentation.runOnMainSync(() -> {
            dialog.setTesting();
            assertEquals(activity.getString(R.string.update_testing_routes), status().getText().toString());
            dialog.setSource(2, 3);
            dialog.setProgress(37);
            dialog.setTransfer(1_572_864, 125);
            dialog.getDialog().findViewById(R.id.confirm).setEnabled(false);
        });
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            assertTrue(status().getText().toString().contains("1.5 MB/s"));
            assertTrue(status().getText().toString().contains("2:05"));
            assertTrue(status().getText().toString().contains("37"));
            assertNotNull(status().getLayout());
            for (int line = 0; line < status().getLayout().getLineCount(); line++)
                assertEquals("Status must not be clipped", 0, status().getLayout().getEllipsisCount(line));
            assertTrue("Status stays above remote buttons", status().getBottom()
                    <= ((View) dialog.getDialog().findViewById(R.id.cancel).getParent()).getTop());
            assertTrue(dialog.getDialog().findViewById(R.id.cancel).requestFocus());
        });
        Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        File output = new File(instrumentation.getTargetContext().getExternalFilesDir(null), "update-download-status.png");
        try (FileOutputStream stream = new FileOutputStream(output)) { screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream); }
        screenshot.recycle();
        instrumentation.runOnMainSync(() -> {
            dialog.setSwitching();
            assertFalse(status().getText().toString().contains("MB/s"));
            dialog.setSource(1, 3);
            assertFalse(status().getText().toString().contains("2:05"));
            dialog.setVerifying();
            assertEquals(activity.getString(R.string.update_download_verifying), status().getText().toString());
        });
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);
        instrumentation.waitForIdleSync();
        assertEquals(1, cancelled.get());
    }

    @Test public void backCancelsTheActualDialogDuringRouteTesting() {
        instrumentation.runOnMainSync(() -> dialog.setTesting());
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        instrumentation.waitForIdleSync();
        assertEquals(1, cancelled.get());
    }

    @Test public void androidNetworkUsesFastRouteAndPreservesCancellationAndIntegrity() throws Exception {
        UpdateRouteProbeTest probes = new UpdateRouteProbeTest();
        probes.ranksPayloadThroughputRatherThanHeadersAndBoundsIgnoredRange();
        probes.retainsInvalidAndUnreachableProbesBehindValidRange();
        probes.rejectsWrongRangeOffsetsAndDisguisedHtml();
        probes.cancellationAbortsEveryConcurrentRequestPromptly();
        UpdateAdaptiveDownloaderTest downloads = new UpdateAdaptiveDownloaderTest();
        downloads.temporary.create();
        try {
            downloads.adaptiveSelectionDownloadsFastPayloadAndReportsOriginalRouteNumber();
            downloads.slowContinuousBodySwitchesWithoutMixingPartialBytesAndKeepsFinalRoute();
            downloads.probingCannotAuthorizeWrongChecksumAndCancellingTestingStartsNoRequests();
            downloads.retriesTheUsableSlowRouteWhenEveryAlternativeFails();
            downloads.workingFinalRouteHasNoWholeDownloadDeadline();
        } finally { downloads.temporary.delete(); }
    }

    private TextView status() { return dialog.getDialog().findViewById(R.id.updateStatus); }
}
