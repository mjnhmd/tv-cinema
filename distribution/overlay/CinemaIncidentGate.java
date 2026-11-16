package com.fongmi.android.tv.monitoring;

import java.util.HashMap;
import java.util.Map;

final class CinemaIncidentGate {
    static final long COOLDOWN_MS = 10 * 60_000L;
    static final int MAX_PER_PROCESS = 20;

    private final Map<String, Long> lastByKind = new HashMap<>();
    private int sent;

    synchronized boolean acquire(String kind, long now) {
        Long previous = lastByKind.get(kind);
        if (sent >= MAX_PER_PROCESS || previous != null && now >= previous && now - previous < COOLDOWN_MS) return false;
        lastByKind.put(kind, now);
        sent++;
        return true;
    }
}
