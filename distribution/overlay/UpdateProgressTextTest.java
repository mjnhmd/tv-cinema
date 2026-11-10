package com.fongmi.android.tv.update;

import org.junit.Test;
import static org.junit.Assert.*;

public final class UpdateProgressTextTest {
    @Test public void formatsRealRatesAndBoundedDurationWithoutInventingUnknownEta() {
        assertEquals("0 KB/s", UpdateProgressText.speed(0));
        assertEquals("128 KB/s", UpdateProgressText.speed(131_072));
        assertEquals("1.5 MB/s", UpdateProgressText.speed(1_572_864));
        assertEquals("", UpdateProgressText.duration(-1));
        assertEquals("0:09", UpdateProgressText.duration(9));
        assertEquals("2:05", UpdateProgressText.duration(125));
        assertEquals("1:02:03", UpdateProgressText.duration(3723));
    }
}
