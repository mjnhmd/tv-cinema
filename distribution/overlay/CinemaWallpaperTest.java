package com.fongmi.android.tv.api.config;

import org.junit.Test;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class CinemaWallpaperTest {

    @Test
    public void shouldReadXxapiDataUrl() {
        String body = "{\"code\":200,\"msg\":\"ok\",\"data\":\"https://images.xxapi.cn/images/wallpaper/test.jpg\"}";
        assertEquals("https://images.xxapi.cn/images/wallpaper/test.jpg", CinemaWallpaper.imageUrl(body));
    }

    @Test
    public void shouldReadFirstPeapixFullUrl() {
        String body = "[{\"title\":\"test\",\"fullUrl\":\"https://img.peapix.com/test_1920.jpg\"," +
                "\"imageUrl\":\"https://img.peapix.com/test.jpg\"}]";
        assertEquals("https://img.peapix.com/test_1920.jpg", CinemaWallpaper.imageUrl(body));
    }

    @Test
    public void shouldIgnoreHtmlAndMalformedPayloads() {
        assertEquals("", CinemaWallpaper.imageUrl("<html>rate limited</html>"));
        assertEquals("", CinemaWallpaper.imageUrl("{\"code\":500,\"data\":\"\"}"));
    }

    @Test
    public void shouldBoundCandidateFileNames() {
        Set<String> names = new HashSet<>();
        for (long request = 0; request < 16; request++) names.add(CinemaWallpaper.candidateName(request));
        assertEquals(2, names.size());
        assertNotEquals(CinemaWallpaper.candidateName(1), CinemaWallpaper.candidateName(2));
        assertEquals(CinemaWallpaper.candidateName(2), CinemaWallpaper.candidateName(4));
        assertTrue(CinemaWallpaper.candidateName(7).startsWith("wallpaper_candidate_"));
    }

    @Test
    public void shouldRotateProviderIndexWithinBounds() {
        assertEquals(0, CinemaWallpaper.indexFor(0, 0));
        assertEquals(5, CinemaWallpaper.indexFor(0, 5));
        assertEquals(0, CinemaWallpaper.indexFor(5, 1));
        assertEquals(2, CinemaWallpaper.indexFor(4, 4));
    }

    @Test
    public void shouldNotTreatTimeoutAsCancellation() {
        assertFalse(CinemaWallpaper.isCancellation(new SocketTimeoutException("timeout")));
        assertFalse(CinemaWallpaper.isCancellation(new IOException("HTTP 500")));
        assertTrue(CinemaWallpaper.isCancellation(new InterruptedIOException("Canceled")));
        assertTrue(CinemaWallpaper.isCancellation(new IOException("Canceled")));
    }

    @Test
    public void shouldTreatStaleOrInterruptedRequestAsCanceled() {
        assertTrue(CinemaWallpaper.isCanceled(1, 2, false));
        assertFalse(CinemaWallpaper.isCanceled(2, 2, false));
        assertTrue(CinemaWallpaper.isCanceled(2, 2, true));
    }
}
