package com.fongmi.android.tv.ui.custom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.test.SeekInputActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Remote release regressions using the production input and Android Handler. */
@RunWith(AndroidJUnit4.class)
public final class VodSeekReleaseAndroidTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private SeekInputActivity activity;
    private CustomKeyDownVod input;
    private boolean accelerate;

    @Before public void setup() {
        String pkg = instrumentation.getTargetContext().getPackageName();
        assertTrue("Disposable validation app only", pkg.equals("com.fongmi.android.tv.preview")
                || pkg.equals("com.fongmi.android.tv.sourceprobe"));
        activity = (SeekInputActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), SeekInputActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        main(() -> {
            accelerate = Setting.isSeekAccelerate();
            Setting.putSeekAccelerate(true);
            input = CustomKeyDownVod.create(activity);
            input.setFull(true);
        });
    }

    @After public void cleanup() {
        main(() -> {
            if (input != null) input.reset();
            Setting.putSeekAccelerate(accelerate);
            if (activity != null) activity.finish();
        });
    }

    @Test public void heldFastForwardCommitsWithoutAReleaseOrAnotherPress() {
        hold(100, KeyEvent.KEYCODE_DPAD_RIGHT);
        SystemClock.sleep(1_350);
        main(() -> {
            assertEquals("A lost release must not leave the preview pending", 1, activity.commits);
            assertEquals(60_000, activity.committed);
            send(100, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 30, 0);
            send(100, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 0);
        });
        SystemClock.sleep(1_350);
        main(() -> assertEquals("Late events from the completed hold cannot seek twice", 1, activity.commits));
    }

    @Test public void heldRewindCommitsWhenReleaseIdentityDoesNotMatch() {
        hold(200, KeyEvent.KEYCODE_DPAD_LEFT);
        main(() -> send(201, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT, 0, 0));
        SystemClock.sleep(1_350);
        main(() -> {
            assertEquals(1, activity.commits);
            assertEquals(-60_000, activity.committed);
        });
    }

    @Test public void repeatsKeepThePreviewPendingUntilTheButtonIsReleased() {
        main(() -> send(300, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 0));
        for (int repeat : new int[]{1, 5, 25}) {
            SystemClock.sleep(600);
            main(() -> {
                assertEquals("Holding must not commit between repeats", 0, activity.commits);
                send(300, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, repeat, 0);
            });
        }
        main(() -> {
            assertEquals(70_000, activity.preview);
            send(300, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 0);
        });
        SystemClock.sleep(450);
        main(() -> { assertEquals(1, activity.commits); assertEquals(70_000, activity.committed); });
        SystemClock.sleep(1_100);
        main(() -> assertEquals("Explicit release replaces the fallback", 1, activity.commits));
    }

    @Test public void shortPressBurstsKeepTheirStepAndCombineIntoOneCommit() {
        main(() -> {
            send(400, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 0);
            send(400, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 0);
        });
        SystemClock.sleep(100);
        main(() -> {
            assertEquals(0, activity.commits);
            send(500, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 0);
            send(500, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 0);
            assertEquals(20_000, activity.preview);
        });
        SystemClock.sleep(450);
        main(() -> { assertEquals(1, activity.commits); assertEquals(20_000, activity.committed); });
    }

    @Test public void canceledReleaseAndUnavailablePlaybackCancelTheFallback() {
        hold(600, KeyEvent.KEYCODE_DPAD_RIGHT);
        main(() -> send(600, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0, KeyEvent.FLAG_CANCELED));
        SystemClock.sleep(1_350);
        main(() -> {
            assertEquals(0, activity.commits);
            assertEquals(1, activity.cancellations);
        });
        hold(700, KeyEvent.KEYCODE_DPAD_RIGHT);
        main(() -> activity.seekable = false);
        SystemClock.sleep(1_350);
        main(() -> {
            assertEquals("Seekability must be checked again at fallback time", 0, activity.commits);
            assertEquals(2, activity.cancellations);
        });
    }

    @Test public void fallbackCannotOutliveBackFullscreenExitOrMediaReset() {
        for (int reason = 0; reason < 3; reason++) {
            hold(800, KeyEvent.KEYCODE_DPAD_RIGHT);
            int action = reason;
            main(() -> {
                if (action == 0) input.cancelSeek(); // Activity Back callback.
                else if (action == 1) input.setFull(false);
                else input.reset(); // Media/lifecycle replacement callback.
                input.setFull(true);
            });
            SystemClock.sleep(1_350);
            main(() -> {
                assertEquals(0, activity.commits);
                send(800, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 30, 0);
                send(800, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 0);
            });
        }
        main(() -> assertEquals(3, activity.cancellations));
    }

    @Test public void zeroOffsetFallbackClearsThePreviewAndRejectsLateHeldKeys() {
        main(() -> {
            send(900, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 0);
            send(1_000, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 0, 0);
            assertEquals(0, activity.preview);
        });
        SystemClock.sleep(1_350);
        main(() -> {
            assertEquals(0, activity.commits);
            assertEquals(1, activity.cancellations);
            send(1_000, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 25, 0);
            send(1_000, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT, 0, 0);
            assertEquals(0, activity.preview);
        });
        SystemClock.sleep(1_350);
        main(() -> { assertEquals(0, activity.commits); assertEquals(1, activity.cancellations); });
    }

    private void hold(long down, int code) {
        main(() -> {
            send(down, KeyEvent.ACTION_DOWN, code, 0, 0);
            send(down, KeyEvent.ACTION_DOWN, code, 5, 0);
            send(down, KeyEvent.ACTION_DOWN, code, 25, 0);
        });
    }

    private void send(long down, int action, int code, int repeat, int flags) {
        input.onKeyDown(new KeyEvent(down, down + repeat + 1, action, code, repeat,
                0, 7, 0, flags, InputDevice.SOURCE_DPAD));
    }

    private void main(Runnable task) { instrumentation.runOnMainSync(task); }
}
