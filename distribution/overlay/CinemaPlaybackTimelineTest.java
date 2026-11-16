package com.fongmi.android.tv.monitoring;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CinemaPlaybackTimelineTest {
    private final List<String> events = new ArrayList<>();
    private final CinemaPlaybackTimeline timeline = new CinemaPlaybackTimeline(new CinemaPlaybackTimeline.Listener() {
        @Override public void breadcrumb(String kind, long duration, String detail) {}
        @Override public void incident(String kind, long duration, String detail) { events.add(kind + ":" + detail + ":" + duration); }
    });

    @Test public void reportsSlowParseOnceEvenBeforeItCompletes() {
        timeline.begin(100, true);
        timeline.tick(8_099, true);
        assertTrue(events.isEmpty());
        timeline.tick(8_100, true);
        timeline.tick(9_100, true);
        timeline.mediaStarted(10_000);
        timeline.ready(10_100, true);
        timeline.firstFrame(10_500);
        assertEquals(List.of("slow_start:parse:8000"), events);
    }

    @Test public void distinguishesRebufferFromSeekAndDoesNotDoubleReport() {
        timeline.begin(0, false);
        timeline.mediaStarted(0);
        timeline.ready(100, true);
        timeline.firstFrame(200);
        timeline.seek(1_000);
        timeline.buffering(1_200, true);
        timeline.tick(6_200, true);
        timeline.ready(7_000, true);
        assertEquals(List.of("slow_buffer:after_seek:5000"), events);
    }

    @Test public void pausedBufferDoesNotCountAndResumedBufferStartsANewClock() {
        timeline.begin(0, false);
        timeline.ready(100, false);
        timeline.buffering(1_000, false);
        timeline.tick(20_000, false);
        assertTrue(events.isEmpty());
        timeline.requested(21_000, true);
        timeline.tick(26_000, true);
        assertEquals(List.of("slow_buffer:during_playback:5000"), events);
    }

    @Test public void audioOnlyAndCancellationDoNotReportMissingVideoFrames() {
        timeline.begin(0, false);
        timeline.ready(100, false);
        timeline.tick(10_000, true);
        assertTrue(events.isEmpty());
        timeline.begin(11_000, false);
        timeline.stop();
        timeline.tick(30_000, true);
        assertFalse(timeline.hasPendingWork());
        assertTrue(events.isEmpty());
    }

    @Test public void slowAudioReadyIsReportedEvenWhenNoTimerRan() {
        timeline.begin(0, false);
        timeline.ready(9_000, false);
        assertEquals(List.of("slow_start:audio_or_unknown_ready:9000"), events);
    }

    @Test public void userPauseIsNotCountedAsSlowStartup() {
        timeline.begin(0, false);
        timeline.requested(1_000, false);
        timeline.requested(21_000, true);
        timeline.tick(27_000, true);
        assertTrue(events.isEmpty());
        timeline.tick(28_000, true);
        assertEquals(List.of("slow_start:prepare:8000"), events);
    }
}
