package com.fongmi.android.tv.monitoring;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CinemaIncidentGateTest {
    @Test public void limitsRepeatedEventsButAllowsDifferentTypes() {
        CinemaIncidentGate gate = new CinemaIncidentGate();
        assertTrue(gate.acquire("ui_stall", 0));
        assertFalse(gate.acquire("ui_stall", CinemaIncidentGate.COOLDOWN_MS - 1));
        assertTrue(gate.acquire("buffering", 1));
        assertTrue(gate.acquire("ui_stall", CinemaIncidentGate.COOLDOWN_MS));
    }

    @Test public void limitsTotalIncidentsForOneProcess() {
        CinemaIncidentGate gate = new CinemaIncidentGate();
        for (int index = 0; index < CinemaIncidentGate.MAX_PER_PROCESS; index++) {
            assertTrue(gate.acquire("event" + index, index));
        }
        assertFalse(gate.acquire("another", CinemaIncidentGate.COOLDOWN_MS));
    }
}
