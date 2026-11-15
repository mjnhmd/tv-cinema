package com.fongmi.android.tv.api.config;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.FileUtil;
import com.github.catvod.utils.Prefers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Offline checks: provider recognition and preserving the current wallpaper when every provider fails. */
@RunWith(AndroidJUnit4.class)
public final class CinemaMobileWallpaperAndroidTest {
    private static final String PROVIDER = "https://v2.xxapi.cn/api/wallpaper";
    private final CinemaMobileTestSupport support = new CinemaMobileTestSupport();
    private String originalUrl;
    private byte[] originalCache;

    @Before public void setup() throws Exception {
        support.start();
        File cache = FileUtil.getWallCache();
        originalCache = cache.isFile() ? Files.readAllBytes(cache.toPath()) : null;
        originalUrl = Config.wall().getUrl();
    }

    @After public void cleanup() throws Exception {
        support.main(() -> {
            if (originalUrl != null && !originalUrl.isEmpty()) Config.find(originalUrl, BaseConfig.WALL).update();
        });
        File cache = FileUtil.getWallCache();
        if (originalCache == null) cache.delete();
        else Files.write(cache.toPath(), originalCache);
        support.close();
    }

    @Test public void recognizesCinemaProvidersButLeavesOrdinaryUrlsAlone() throws Exception {
        assertTrue(support.read(() -> CinemaWallpaper.isProvider(PROVIDER)));
        for (String provider : new String[]{"https://wp.upx8.com/api.php", "https://peapix.com/bing/feed?country=cn",
                "https://bing-img.wuw.li/random.php", "https://api.mxin.me/api/v1/wallpaper", "https://api.xsot.cn/bing?jump=true"}) {
            assertTrue(provider, support.read(() -> CinemaWallpaper.isProvider(provider)));
        }
        String ordinary = "https://example.test/wallpaper.jpg";
        assertEquals(ordinary, CinemaWallpaper.resolveSource(ordinary));
        assertFalse(support.read(() -> CinemaWallpaper.isProvider(ordinary)));
    }

    @Test public void phoneRefreshKeepsCurrentWallpaperWhenProvidersAreUnreachable() throws Exception {
        support.main(() -> {
            Prefers.put("cinema_wallpaper_source_index", 0);
            Config.find(PROVIDER, BaseConfig.WALL).name("在线壁纸 · 星空壁纸").update();
            Setting.putWallType(0);
            Setting.putWall(0);
        });
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        support.main(() -> CinemaWallpaper.load(new Callback() {
            @Override public void success() { done.countDown(); }
            @Override public void error(String msg) {
                error.set(msg);
                done.countDown();
            }
        }));
        assertTrue("Wallpaper attempt finishes", done.await(90, TimeUnit.SECONDS));
        assertNotNull("Offline providers must report failure instead of a fake wallpaper", error.get());
        assertEquals(PROVIDER, support.read(() -> Config.wall().getUrl()));
        File cache = FileUtil.getWallCache();
        if (originalCache != null) assertArrayEquals(originalCache, Files.readAllBytes(cache.toPath()));
    }
}
