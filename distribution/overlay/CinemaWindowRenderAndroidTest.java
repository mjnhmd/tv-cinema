package com.fongmi.android.tv.ui.custom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.PixelCopy;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.activity.PlaybackActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.github.catvod.utils.Prefers;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@RunWith(AndroidJUnit4.class)
public final class CinemaWindowRenderAndroidTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private VideoActivity activity;
    private PlayerManager manager;

    @Test public void surfacePlaybackKeepsTheSameOutputAcrossWindowAndFullscreen() throws Exception {
        verifyPlayback(PlayerSetting.ENGINE_EXO, PlayerSetting.RENDER_SURFACE, SurfaceView.class, "surface", true);
    }

    @Test public void surfacePlaybackStartsDirectlyInTheWindow() throws Exception {
        verifyPlayback(PlayerSetting.ENGINE_EXO, PlayerSetting.RENDER_SURFACE, SurfaceView.class, "surface-first", false);
    }

    @Test public void texturePreferenceIsPreservedAcrossWindowAndFullscreen() throws Exception {
        verifyPlayback(PlayerSetting.ENGINE_EXO, PlayerSetting.RENDER_TEXTURE, TextureView.class, "texture", true);
    }

    @Test public void mpvKeepsItsTextureOutputAcrossWindowAndFullscreen() throws Exception {
        verifyPlayback(PlayerSetting.ENGINE_MPV, PlayerSetting.RENDER_SURFACE, TextureView.class, "mpv", false);
    }

    private void verifyPlayback(int engine, int render, Class<? extends View> outputType,
                                String name, boolean startFullscreen) throws Exception {
        String pkg = instrumentation.getTargetContext().getPackageName();
        assertTrue("Disposable validation app only", pkg.equals("com.fongmi.android.tv.preview")
                || pkg.equals("com.fongmi.android.tv.sourceprobe"));
        File files = instrumentation.getTargetContext().getExternalFilesDir(null);
        File fixture = new File(files, "cinema-window-render-fixture.mp4");
        assertTrue("Install the generated H.264/AAC fixture before running", fixture.isFile());
        Map<String, Object> settings = Map.of(
                "incognito", true, "player_engine", engine, "render", render,
                "tunnel", false, "preload", false, "preload_next", false,
                "community_skip_enabled", false);
        Map<String, Object> saved = new HashMap<>();
        Map<String, ?> preferences = Prefers.getPrefers().getAll();
        for (String key : settings.keySet()) if (preferences.containsKey(key)) saved.put(key, preferences.get(key));
        main(() -> settings.forEach(Prefers::put));
        try {
            activity = (VideoActivity) instrumentation.startActivitySync(new Intent(
                    instrumentation.getTargetContext(), VideoActivity.class)
                    .putExtra("key", "push_agent").putExtra("id", Uri.fromFile(fixture).toString())
                    .putExtra("name", "Window render regression").putExtra("pic", "")
                    .putExtra("cast", startFullscreen)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(() -> {
                if (invoke(PlaybackActivity.class, "service") == null) return false;
                manager = (PlayerManager) invoke(PlaybackActivity.class, "player");
                return manager != null && manager.getPlaybackState() == Player.STATE_READY
                        && manager.isPlaying() && manager.getDuration() > 115_000;
            }, "Local video must be ready and playing");
            main(() -> manager.getPlayer().setVolume(0));
            assertEquals("Playback must use the requested engine", engine, (int) read(manager::getEngine));
            PlayerView playerView = read(() -> activity.findViewById(R.id.player));
            Player player = read(manager::getPlayer);
            if (engine == PlayerSetting.ENGINE_EXO) {
                assertTrue("Audio track must be selected", read(() -> player.getCurrentTracks().isTypeSelected(C.TRACK_TYPE_AUDIO)));
            }
            View initialOutput = read(playerView::getVideoSurfaceView);
            assertTrue("Playback should use the selected output", outputType.isInstance(initialOutput));
            awaitVideo(initialOutput);

            if (startFullscreen) main(() -> invoke(VideoActivity.class, "exitFullscreen"));
            await(() -> activity.findViewById(R.id.detail).getVisibility() == View.VISIBLE,
                    "Detail view must return");
            View windowOutput = read(playerView::getVideoSurfaceView);
            assertTrue("Window should use the selected output", outputType.isInstance(windowOutput));
            assertSame("Window must not replace the selected output", initialOutput, windowOutput);
            assertSame("Window must keep the current player", player, read(manager::getPlayer));
            awaitVideo(windowOutput);
            saveScreenshot(new File(files, "cinema-window-render-" + name + ".png"));
            long position = read(manager::getPosition);

            main(() -> assertTrue(activity.findViewById(R.id.video).performClick()));
            await(() -> activity.findViewById(R.id.detail).getVisibility() == View.GONE,
                    "Fullscreen must hide detail");
            assertSame("Fullscreen must not replace the output", windowOutput, read(playerView::getVideoSurfaceView));
            assertSame("Fullscreen must keep the current player", player, read(manager::getPlayer));
            awaitVideo(windowOutput);
            await(() -> manager.isPlaying() && manager.getPosition() > position + 500,
                    "Playback must continue across the transition");
            if (engine == PlayerSetting.ENGINE_EXO) {
                assertTrue("Audio track must remain selected", read(() -> player.getCurrentTracks().isTypeSelected(C.TRACK_TYPE_AUDIO)));
            }

            main(() -> activity.getOnBackPressedDispatcher().onBackPressed());
            await(() -> activity.findViewById(R.id.detail).getVisibility() == View.VISIBLE,
                    "Back must return to the window");
            assertSame("Back must keep the current output", windowOutput, read(playerView::getVideoSurfaceView));
            awaitVideo(windowOutput);
        } finally {
            main(() -> {
                if (activity != null) activity.finish();
                for (String key : settings.keySet()) {
                    if (saved.containsKey(key)) Prefers.put(key, saved.get(key));
                    else Prefers.getPrefers().edit().remove(key).apply();
                }
            });
        }
    }

    private void awaitVideo(View output) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 15_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            Bitmap frame = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888);
            try {
                boolean copied;
                if (output instanceof SurfaceView) {
                    CountDownLatch complete = new CountDownLatch(1);
                    AtomicInteger result = new AtomicInteger(-1);
                    main(() -> PixelCopy.request((SurfaceView) output, frame, code -> {
                        result.set(code);
                        complete.countDown();
                    }, new Handler(Looper.getMainLooper())));
                    copied = complete.await(5, TimeUnit.SECONDS) && result.get() == PixelCopy.SUCCESS;
                } else {
                    copied = read(() -> ((TextureView) output).getBitmap(frame)) != null;
                }
                if (copied && hasVariedVideo(frame)) return;
            } finally {
                frame.recycle();
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Video output did not contain the varied local test frame");
    }

    private boolean hasVariedVideo(Bitmap frame) {
        int minimum = 255;
        int maximum = 0;
        int nonblack = 0;
        int greenDominant = 0;
        for (int row = 0; row < frame.getHeight(); row++) {
            for (int column = 0; column < frame.getWidth(); column++) {
                int color = frame.getPixel(column, row);
                int red = (color >> 16) & 255;
                int green = (color >> 8) & 255;
                int blue = color & 255;
                int value = Math.max(red, Math.max(green, blue));
                minimum = Math.min(minimum, value);
                maximum = Math.max(maximum, value);
                if (value > 20) nonblack++;
                if (green > 40 && green * 2 > red * 3 && green * 2 > blue * 3) greenDominant++;
            }
        }
        return nonblack > 100 && maximum - minimum > 32 && greenDominant * 100 < nonblack * 60;
    }

    private void saveScreenshot(File target) throws Exception {
        Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull("Unable to capture the test display", screenshot);
        try (FileOutputStream stream = new FileOutputStream(target)) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally {
            screenshot.recycle();
        }
    }

    private Object invoke(Class<?> type, String name) {
        try {
            Method method = type.getDeclaredMethod(name);
            method.setAccessible(true);
            return method.invoke(activity);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private void await(BooleanSupplier condition, String message) {
        long deadline = SystemClock.elapsedRealtime() + 15_000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (read(condition::getAsBoolean)) return;
            SystemClock.sleep(50);
        }
        throw new AssertionError(message);
    }

    private void main(Runnable task) {
        read(() -> {
            task.run();
            return null;
        });
    }

    private <T> T read(Supplier<T> task) {
        AtomicReference<T> result = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> result.set(task.get()));
        return result.get();
    }
}
