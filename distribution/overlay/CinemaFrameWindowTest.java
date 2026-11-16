package com.fongmi.android.tv.monitoring;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CinemaFrameWindowTest {
    @Test public void reportsRepeatedSlowFramesAndLargestMeasuredStage() {
        List<String> events = new ArrayList<>();
        CinemaFrameWindow window = new CinemaFrameWindow((total, slow, lost, max, stage) ->
                events.add(total + ":" + slow + ":" + lost + ":" + max + ":" + stage));
        for (int index = 0; index < 10; index++) window.record(index * 100, 65, 0, 2, 35, 10, 3);
        window.record(5_000, 10, 0, 0, 0, 0, 0);
        assertEquals(List.of("10:10:0:65:layout"), events);
    }

    @Test public void doesNotReportIsolatedSlowFrames() {
        List<String> events = new ArrayList<>();
        CinemaFrameWindow window = new CinemaFrameWindow((total, slow, lost, max, stage) -> events.add(stage));
        for (int index = 0; index < 20; index++) window.record(index * 100, index < 3 ? 70 : 10, 0, 0, 0, 0, 0);
        window.record(5_000, 10, 0, 0, 0, 0, 0);
        assertTrue(events.isEmpty());
    }
}
