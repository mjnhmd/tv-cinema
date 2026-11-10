package com.fongmi.android.tv.update;

import java.util.function.LongSupplier;

/** Monotonic transfer samples; retries must save time after restarting from byte zero. */
final class UpdateTransferMeter {
    private static final long MIN_REMAINING = 1024 * 1024;
    private final LongSupplier clock;
    private final long windowMs;
    private final long minRate;
    private final long started;
    private long windowStart;
    private long windowBytes;
    private long sampleStart;
    private long sampleBytes;
    private long total;
    private long rate;
    private int lowWindows;

    UpdateTransferMeter(LongSupplier clock, long windowMs, long minRate) {
        this.clock = clock;
        this.windowMs = windowMs;
        this.minRate = minRate;
        started = windowStart = sampleStart = clock.getAsLong();
    }

    boolean record(long bytes) {
        total += bytes;
        long now = clock.getAsLong();
        if (now - windowStart >= windowMs) {
            long windowRate = perSecond(total - windowBytes, now - windowStart);
            lowWindows = windowRate < minRate ? lowWindows + 1 : 0;
            windowStart = now;
            windowBytes = total;
        }
        if (now - sampleStart < 500) return false;
        long sampleRate = perSecond(total - sampleBytes, now - sampleStart);
        rate = rate == 0 ? sampleRate : (rate + sampleRate) / 2;
        sampleStart = now;
        sampleBytes = total;
        return true;
    }

    long bytesPerSecond() { return rate; }

    long remainingSeconds(long expectedSize) {
        return rate <= 0 || expectedSize <= 0 ? -1 : (Math.max(0, expectedSize - total) + rate - 1) / rate;
    }

    boolean shouldSwitch(long expectedSize, long alternativeRate) {
        if (lowWindows < 2 || clock.getAsLong() - started < windowMs * 3
                || expectedSize - total <= MIN_REMAINING || rate <= 0) return false;
        // A failed probe is unknown, not proof that the route cannot download. Avoid discarding
        // most of a usable download just to explore an unknown route.
        if (alternativeRate <= 0) return total < expectedSize / 2;
        return alternativeRate > rate * 1.5
                && (expectedSize - total) / (double) rate > expectedSize / (double) alternativeRate + 5;
    }

    private static long perSecond(long bytes, long millis) {
        return bytes * 1000 / Math.max(1, millis);
    }
}
