package com.fongmi.android.tv.playback;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public final class CinemaTrackStatusTest {
    @Test public void describesSelectedTrackAndLeavesDisabledSubtitlesOff() {
        Format english = new Format.Builder().setSampleMimeType("text/vtt").setLanguage("en").build();
        Format chinese = english.buildUpon().setLanguage("zh-Hans").setLabel("简中双语").build();
        Tracks selected = new Tracks(List.of(
                new Tracks.Group(new TrackGroup(english), false, new int[]{C.FORMAT_HANDLED}, new boolean[]{false}),
                new Tracks.Group(new TrackGroup(chinese), false, new int[]{C.FORMAT_HANDLED}, new boolean[]{true})));
        assertEquals("简中双语", CinemaTrackStatus.selected(selected, C.TRACK_TYPE_TEXT, "关"));
        assertEquals("关", CinemaTrackStatus.selected(Tracks.EMPTY, C.TRACK_TYPE_TEXT, "关"));
        assertEquals("", CinemaTrackStatus.selected(selected, C.TRACK_TYPE_AUDIO, ""));
    }

    @Test public void unnamedSelectedSubtitleMustNotBeReportedAsOff() {
        Format unknown = new Format.Builder().setSampleMimeType("text/vtt").build();
        Tracks selected = new Tracks(List.of(new Tracks.Group(new TrackGroup(unknown), false,
                new int[]{C.FORMAT_HANDLED}, new boolean[]{true})));
        assertEquals("text/vtt", CinemaTrackStatus.selected(selected, C.TRACK_TYPE_TEXT, "关"));
    }
}
