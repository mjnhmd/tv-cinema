package com.fongmi.android.tv.ui.custom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.SystemClock;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import androidx.media3.ui.PlayerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.test.CorePlaybackActivity;
import com.fongmi.android.tv.ui.theme.JetStreamAmbient;
import com.fongmi.android.tv.ui.theme.JetStreamAnimations;
import com.github.catvod.utils.Prefers;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import fi.iki.elonen.NanoHTTPD;

@RunWith(AndroidJUnit4.class)
public final class CinemaPerformanceAndroidTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private CorePlaybackActivity activity;
    private Object savedLightweight;
    private PlaybackSpecsBackdrop backdrop;
    private JetStreamPlaybackSpecsView capsule;
    private Images images;

    @Before public void setup() {
        assertEquals("Use a disposable validation app", "com.fongmi.android.tv.sourceprobe",
                instrumentation.getTargetContext().getPackageName());
        main(() -> {
            savedLightweight = Prefers.getPrefers().getAll().get("cinema_lightweight");
            Prefers.put("cinema_lightweight", true);
        });
        activity = (CorePlaybackActivity) instrumentation.startActivitySync(new Intent(
                instrumentation.getTargetContext(), CorePlaybackActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    @After public void cleanup() {
        main(() -> {
            if (backdrop != null) backdrop.stop();
            if (capsule != null) capsule.dismiss();
            if (activity != null) activity.finish();
            if (savedLightweight == null) Prefers.remove("cinema_lightweight");
            else Prefers.put("cinema_lightweight", savedLightweight);
        });
        if (images != null) images.stop();
        instrumentation.waitForIdleSync();
    }

    @Test public void lightweightFocusDoesNotDownloadUnusedAmbientImages() throws Exception {
        images = new Images();
        images.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
        for (int index = 0; index < 8; index++) {
            String url = "http://127.0.0.1:" + images.getListeningPort() + "/poster-" + index + ".png";
            main(() -> JetStreamAmbient.push(url));
            SystemClock.sleep(100);
        }
        SystemClock.sleep(500);
        System.out.println("CINEMA_PERFORMANCE ambient_requests=" + images.requests.get());
        assertEquals("Focus changes must not fetch images that no background consumes", 0, images.requests.get());
        main(() -> {
            Prefers.put("cinema_lightweight", false);
            JetStreamAmbient.push("http://127.0.0.1:" + images.getListeningPort() + "/enabled.png");
        });
        long deadline = SystemClock.elapsedRealtime() + 2_000;
        while (images.requests.get() == 0 && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50);
        assertEquals("The optional non-lightweight path remains functional", 1, images.requests.get());
    }

    @Test public void lightweightPlaybackKeepsSpecsWithoutVideoReadbacks() throws Exception {
        main(() -> {
            FrameLayout host = new FrameLayout(activity);
            PlayerView player = new PlayerView(activity);
            host.addView(player, new FrameLayout.LayoutParams(-1, -1));
            capsule = new JetStreamPlaybackSpecsView(activity);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, 46);
            params.setMargins(32, 32, 32, 0);
            host.addView(capsule, params);
            activity.setContentView(host);
            backdrop = new PlaybackSpecsBackdrop(capsule, player);
            capsule.show("1080P SDR", "H.264", "AAC", "2.0", null);
        });
        instrumentation.waitForIdleSync();
        SystemClock.sleep(200);
        main(() -> {
            PlayerView player = (PlayerView) ((FrameLayout) capsule.getParent()).getChildAt(0);
            SurfaceView surface = (SurfaceView) player.getVideoSurfaceView();
            Canvas canvas = surface.getHolder().lockCanvas();
            if (canvas != null) {
                try { canvas.drawColor(Color.BLUE); }
                finally { surface.getHolder().unlockCanvasAndPost(canvas); }
            }
            backdrop.start();
        });
        SystemClock.sleep(1_000);
        main(() -> {
            try {
                Field samples = PlaybackSpecsBackdrop.class.getDeclaredField("samples");
                samples.setAccessible(true);
                Field running = PlaybackSpecsBackdrop.class.getDeclaredField("running");
                running.setAccessible(true);
                System.out.println("CINEMA_PERFORMANCE video_readbacks=" + samples.getInt(backdrop));
                assertEquals("Lightweight playback must not read video back for decoration", 0, samples.getInt(backdrop));
                assertFalse(running.getBoolean(backdrop));
                assertTrue("Useful playback metadata remains visible", capsule.isActive());
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
    }

    @Test public void lightweightComposeMotionHonorsTheSameProfileAsNativeViews() {
        main(() -> {
            assertEquals(0, JetStreamAnimations.INSTANCE.getDurationFocus());
            assertEquals(0, JetStreamAnimations.INSTANCE.getDurationPanel());
            assertEquals(1f, JetStreamAnimations.INSTANCE.getFocusScaleSmall(), 0f);
            Prefers.put("cinema_lightweight", false);
            assertEquals(120, JetStreamAnimations.INSTANCE.getDurationFocus());
            assertEquals(220, JetStreamAnimations.INSTANCE.getDurationPanel());
            assertEquals(1.02f, JetStreamAnimations.INSTANCE.getFocusScaleSmall(), 0f);
        });
    }

    private void main(Runnable action) { instrumentation.runOnMainSync(action); }

    private static final class Images extends NanoHTTPD {
        final AtomicInteger requests = new AtomicInteger();
        private final byte[] png;

        Images() {
            super("127.0.0.1", 0);
            Bitmap bitmap = Bitmap.createBitmap(96, 54, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.BLUE);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            bitmap.recycle();
            png = output.toByteArray();
        }

        @Override public Response serve(IHTTPSession session) {
            requests.incrementAndGet();
            return newFixedLengthResponse(Response.Status.OK, "image/png", new ByteArrayInputStream(png), png.length);
        }
    }
}
