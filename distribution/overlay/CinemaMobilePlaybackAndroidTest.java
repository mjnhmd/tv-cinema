package com.fongmi.android.tv.api.config;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.view.SurfaceView;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.uiautomator.UiDevice;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.github.catvod.utils.Prefers;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class CinemaMobilePlaybackAndroidTest {
    private final CinemaMobileTestSupport support = new CinemaMobileTestSupport();

    @Test public void phoneVideoPlaysPausesSeeksAndReturnsFromFullscreen() throws Exception {
        support.start();
        VideoActivity video = null;
        try {
            File fixture = new File(support.activity.getExternalFilesDir(null), "cinema-window-render-fixture.mp4");
            assertTrue("Install the generated local H.264/AAC fixture", fixture.isFile());
            support.main(() -> {
                Prefers.put("player_engine", PlayerSetting.ENGINE_EXO);
                Prefers.put("render", PlayerSetting.RENDER_SURFACE);
                Prefers.put("background", 0);
            });
            video = (VideoActivity) support.instrumentation.startActivitySync(new Intent(
                    support.activity, VideoActivity.class).putExtra("key", "push_agent")
                    .putExtra("id", Uri.fromFile(fixture).toString()).putExtra("name", "手机播放测试")
                    .putExtra("pic", "").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            VideoActivity activity = video;
            PlayerView view = support.read(() -> activity.findViewById(R.id.player));
            support.await(() -> view.getPlayer() != null && view.getPlayer().getPlaybackState() == Player.STATE_READY
                    && view.getPlayer().isPlaying() && activity.getPlayerPosition() > 500, "Local video plays");
            Player player = support.read(view::getPlayer);
            support.main(() -> player.addListener(new Player.Listener() {
                @Override public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                    System.out.println("Cinema mobile playWhenReady=" + playWhenReady + " reason=" + reason
                            + " finishing=" + activity.isFinishing() + " fullscreen=" + activity.isFullscreenForPlayback());
                }
            }));
            support.main(() -> player.setVolume(0));
            assertTrue(support.read(() -> player.getCurrentTracks().isTypeSelected(C.TRACK_TYPE_AUDIO)));
            assertVideo((SurfaceView) support.read(view::getVideoSurfaceView));
            support.main(() -> activity.findViewById(R.id.play).performClick());
            support.await(() -> !player.isPlaying(), "Touch pause action pauses playback");
            support.main(() -> activity.findViewById(R.id.play).performClick());
            support.await(player::isPlaying, "Touch play action resumes playback");
            support.main(() -> activity.onSeekEnd(30000));
            support.await(() -> activity.getPlayerPosition() >= 28000, "Phone seek advances position");
            support.main(activity::onDoubleTap);
            support.await(() -> activity.isFullscreenForPlayback()
                    && activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                    && activity.findViewById(R.id.video).getWidth() > activity.findViewById(R.id.video).getHeight()
                    && activity.findViewById(R.id.video).getHeight() >= activity.getWindow().getDecorView().getHeight() - 10,
                    "Fullscreen fills the landscape phone display");
            assertVideo((SurfaceView) support.read(view::getVideoSurfaceView));
            UiDevice device = UiDevice.getInstance(support.instrumentation);
            device.waitForIdle(3000);
            device.takeScreenshot(
                    new File(support.activity.getExternalFilesDir(null), "cinema-mobile-playback.png"));
            System.out.println("Cinema mobile before Back fullscreen=" + support.read(activity::isFullscreenForPlayback));
            device.pressBack();
            device.waitForIdle(1000);
            if (support.read(activity::isFullscreenForPlayback)) {
                device.pressBack();
                device.waitForIdle(1000);
            }
            support.await(() -> !activity.isFullscreenForPlayback(), "Back returns to phone detail");
            assertFalse("Back must keep the phone detail activity open", support.read(activity::isFinishing));
            assertSame(player, support.read(view::getPlayer));
            support.await(player::isPlaying, "Video continues after fullscreen exit");
        } finally {
            VideoActivity activity = video;
            support.main(() -> { if (activity != null) activity.finish(); });
            support.close();
        }
    }

    private void assertVideo(SurfaceView surface) throws Exception {
        Bitmap frame = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888);
        CountDownLatch copied = new CountDownLatch(1);
        AtomicInteger result = new AtomicInteger(-1);
        support.main(() -> PixelCopy.request(surface, frame, code -> {
            result.set(code);
            copied.countDown();
        }, new Handler(Looper.getMainLooper())));
        assertTrue(copied.await(5, TimeUnit.SECONDS));
        assertEquals(PixelCopy.SUCCESS, result.get());
        Set<Integer> colors = new HashSet<>();
        for (int row = 0; row < 36; row++) for (int column = 0; column < 64; column++) colors.add(frame.getPixel(column, row));
        frame.recycle();
        assertTrue("Actual decoded video must not be a solid black/green frame", colors.size() > 8);
    }
}
