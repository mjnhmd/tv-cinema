package com.fongmi.android.tv.monitoring;

final class CinemaFrameWindow {
    interface Listener { void slowWindow(int total, int slow, int lost, long maxMs, String stage); }
    private static final long WINDOW_MS = 5_000;
    private static final long SLOW_FRAME_MS = 50;
    private final Listener listener;
    private long since = -1;
    private int frames;
    private int slow;
    private int lost;
    private long maxMs;
    private final long[] stageTotals = new long[4];

    CinemaFrameWindow(Listener listener) { this.listener = listener; }

    void record(long now, long totalMs, int missed, long inputMs, long layoutMs, long drawMs, long syncMs) {
        if (since < 0) since = now;
        if (now - since >= WINDOW_MS) {
            finish();
            since = now;
        }
        frames++;
        lost += Math.max(0, missed);
        maxMs = Math.max(maxMs, totalMs);
        if (totalMs < SLOW_FRAME_MS) return;
        slow++;
        stageTotals[0] += Math.max(0, inputMs);
        stageTotals[1] += Math.max(0, layoutMs);
        stageTotals[2] += Math.max(0, drawMs);
        stageTotals[3] += Math.max(0, syncMs);
    }

    void reset() {
        since = -1;
        frames = slow = lost = 0;
        maxMs = 0;
        java.util.Arrays.fill(stageTotals, 0);
    }

    private void finish() {
        if (frames >= 10 && slow >= 8 && slow * 4 >= frames) {
            String[] stages = {"input", "layout", "draw", "sync"};
            int top = 0;
            for (int index = 1; index < stages.length; index++) if (stageTotals[index] > stageTotals[top]) top = index;
            listener.slowWindow(frames, slow, lost, maxMs, stageTotals[top] == 0 ? "unknown" : stages[top]);
        }
        reset();
    }
}
