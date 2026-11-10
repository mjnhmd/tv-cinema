package com.fongmi.android.tv.update;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public final class UpdateTransferMeterTest {
    @Test public void switchesSustainedSlowTransferOnlyWhenRestartingSavesTime() {
        AtomicLong clock = new AtomicLong();
        UpdateTransferMeter meter = new UpdateTransferMeter(clock::get, 4_000, 65_536);
        assertEquals(-1, meter.remainingSeconds(80_000_000));
        for (int i = 1; i <= 12; i++) {
            clock.set(i * 1_000L);
            assertTrue(meter.record(16_384));
            if (i < 12) assertFalse(meter.shouldSwitch(80_000_000, 1_000_000));
        }
        assertEquals(16_384, meter.bytesPerSecond());
        assertTrue(meter.shouldSwitch(80_000_000, 1_000_000));
        assertTrue(meter.shouldSwitch(80_000_000, 0));
        assertFalse(meter.shouldSwitch(80_000_000, 8_192));
        assertEquals(4_871, meter.remainingSeconds(80_000_000));
        assertFalse(meter.shouldSwitch(1_000_000, 1_000_000));
    }

    @Test public void recoveryResetsTheLowSpeedWindowAndSpeedSamplesAreBounded() {
        AtomicLong clock = new AtomicLong();
        UpdateTransferMeter meter = new UpdateTransferMeter(clock::get, 4_000, 65_536);
        clock.set(100);
        assertFalse(meter.record(1_024));
        for (int i = 1; i <= 3; i++) { clock.set(i * 4_000L); meter.record(4_096); }
        assertTrue(meter.shouldSwitch(80_000_000, 1_000_000));
        clock.set(16_000);
        meter.record(4_000_000);
        assertFalse(meter.shouldSwitch(80_000_000, 8_000_000));
        assertEquals(0, meter.remainingSeconds(0 + 4_013_312));
    }

    @Test public void keepsProgressWhenAnUnknownAlternativeWouldRestartMostBytes() {
        AtomicLong clock = new AtomicLong();
        UpdateTransferMeter meter = new UpdateTransferMeter(clock::get, 100, 10_000_000);
        for (int i = 1; i <= 5; i++) { clock.set(i * 500L); meter.record(1_000_000); }
        assertFalse(meter.shouldSwitch(8_000_000, 0));
        assertFalse(meter.shouldSwitch(8_000_000, 2_500_000));
    }
}
