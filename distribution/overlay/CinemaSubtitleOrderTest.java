package com.fongmi.android.tv.player.track;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public final class CinemaSubtitleOrderTest {
    private record Entry(String language, String title, boolean selected) {}

    @Test public void recognizesLanguageCodesAndLabelsWithoutSubstringFalsePositives() {
        assertEquals(0, CinemaSubtitleOrder.rank("zh_Hans", null));
        assertEquals(0, CinemaSubtitleOrder.rank("und", "简中英双语"));
        assertEquals(1, CinemaSubtitleOrder.rank("chi", "Chinese"));
        assertEquals(2, CinemaSubtitleOrder.rank("zh-Hant", "繁體"));
        assertEquals(3, CinemaSubtitleOrder.rank("en", "description"));
        assertEquals(3, CinemaSubtitleOrder.rank(null, null));
    }

    @Test public void sortingIsStableAndPreservesTrackIdentityAndSelection() {
        Entry english = new Entry("en", "English", true);
        Entry first = new Entry("zh-Hans", "简体", false);
        Entry second = new Entry("und", "简体双语", false);
        Entry traditional = new Entry("zh-Hant", "繁体", false);
        List<Entry> original = List.of(english, traditional, first, second);
        List<Entry> result = CinemaSubtitleOrder.sorted(original, Entry::language, Entry::title);
        assertEquals(List.of(first, second, traditional, english), result);
        assertSame(english, result.get(3));
        assertEquals(true, result.get(3).selected());
        assertSame(english, original.get(0));
    }
}
