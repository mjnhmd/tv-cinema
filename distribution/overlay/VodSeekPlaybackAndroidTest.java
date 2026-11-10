package com.fongmi.android.tv.ui.custom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;

import androidx.media3.common.Player;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.activity.PlaybackActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.github.catvod.utils.Prefers;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Actual Activity dispatch, MediaController seek and popup dismissal with a local H.264 fixture. */
@RunWith(AndroidJUnit4.class)
public final class VodSeekPlaybackAndroidTest {
    private static final Map<String, Object> SETTINGS = Map.of(
            "incognito", true, "player_engine", PlayerSetting.ENGINE_EXO,
            "tunnel", false, "preload", false, "preload_next", false,
            "seek_accelerate", true, "community_skip_enabled", false);
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private VideoActivity activity;
    private PlayerManager manager;
    private JetStreamVodControlView controls;

    @Test public void lostReleaseSeeksVideoAndClosesThePreviewWithoutAnotherPress() throws Exception {
        String pkg = instrumentation.getTargetContext().getPackageName();
        assertTrue("Disposable validation app only", pkg.equals("com.fongmi.android.tv.preview")
                || pkg.equals("com.fongmi.android.tv.sourceprobe"));
        File fixture = new File(instrumentation.getTargetContext().getExternalFilesDir(null), "cinema-seek-fixture.mp4");
        assertTrue("Install the generated 120s H.264 fixture before running", fixture.isFile());
        Map<String, Object> saved = new HashMap<>();
        Map<String, ?> preferences = Prefers.getPrefers().getAll();
        for (String key : SETTINGS.keySet()) if (preferences.containsKey(key)) saved.put(key, preferences.get(key));
        main(() -> SETTINGS.forEach(Prefers::put));
        try {
            activity = (VideoActivity) instrumentation.startActivitySync(new Intent(
                    instrumentation.getTargetContext(), VideoActivity.class)
                    .putExtra("key", "push_agent").putExtra("id", Uri.fromFile(fixture).toString())
                    .putExtra("name", "Seek regression").putExtra("pic", "").putExtra("cast", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(() -> {
                if (invoke(PlaybackActivity.class, "service") == null) return false;
                manager = (PlayerManager) invoke(PlaybackActivity.class, "player");
                return manager != null && manager.getDuration() > 115_000 && manager.isPlaying() && activity.canSeek();
            }, "Local H.264 video must be playing and seekable");
            main(() -> {
                controls = activity.findViewById(com.fongmi.android.tv.R.id.jetstream);
                manager.getPlayer().setVolume(0);
                if (!(Boolean) invoke(VideoActivity.class, "isFullscreen")) invoke(VideoActivity.class, "enterFullscreen");
                invoke(VideoActivity.class, "hideControl");
                invoke(VideoActivity.class, "hideCenter");
            });
            long before = read(manager::getPosition);
            long down = SystemClock.uptimeMillis();
            main(() -> {
                send(down, KeyEvent.ACTION_DOWN, 0);
                send(down, KeyEvent.ACTION_DOWN, 5);
                send(down, KeyEvent.ACTION_DOWN, 25);
                assertTrue("Held seek must show a preview", controls.isCenterInfoVisible());
                assertEquals(JetStreamVodControlView.ACTION_FORWARD, controls.currentInfoAction());
            });
            await(() -> manager.getPosition() >= before + 58_000 && manager.getPlaybackState() == Player.STATE_READY,
                    "Lost release must move the actual video by the accumulated 60s");
            await(() -> !controls.isCenterInfoVisible(), "Seek preview must close after the video seeks");
            main(() -> {
                assertTrue("Seeking should retain playback", manager.isPlaying());
                send(down, KeyEvent.ACTION_UP, 0);
            });
            SystemClock.sleep(1_200);
            main(() -> {
                assertTrue("A late release cannot seek a second time", manager.getPosition() < before + 70_000);
                assertFalse(controls.isCenterInfoVisible());
            });
        } finally {
            main(() -> {
                if (activity != null) activity.finish();
                for (String key : SETTINGS.keySet()) {
                    if (saved.containsKey(key)) Prefers.put(key, saved.get(key));
                    else Prefers.getPrefers().edit().remove(key).apply();
                }
            });
        }
    }

    private Object invoke(Class<?> type, String name) {
        try {
            Method method = type.getDeclaredMethod(name);
            method.setAccessible(true);
            return method.invoke(activity);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }

    private void send(long down, int action, int repeat) {
        assertTrue(activity.dispatchKeyEvent(new KeyEvent(down, SystemClock.uptimeMillis(), action,
                KeyEvent.KEYCODE_DPAD_RIGHT, repeat, 0, 7, 0, 0, InputDevice.SOURCE_DPAD)));
    }

    private void await(BooleanSupplier condition, String message) {
        long deadline = SystemClock.elapsedRealtime() + 15_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (read(condition::getAsBoolean)) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError(message);
    }

    private void main(Runnable task) { read(() -> { task.run(); return null; }); }

    private <T> T read(Supplier<T> task) {
        AtomicReference<T> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> result.set(task.get()));
        return result.get();
    }
}
