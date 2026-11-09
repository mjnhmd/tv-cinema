package com.fongmi.android.tv.ui.custom;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JetStreamEpisodeGridPolicyTest {

    @Test
    public void packsMeasuredWidthsWithoutForcingShortTitlesToFillTheRow() {
        List<JetStreamEpisodeGridPolicy.Row> rows = JetStreamEpisodeGridPolicy.layout(
                List.of(60, 120, 95, 400, 72), 300, 8);
        assertEquals(3, rows.size());
        assertEquals(List.of(0, 1, 2), rows.get(0).indices);
        assertEquals(List.of(60, 120, 95), rows.get(0).widths);
        assertEquals(List.of(3), rows.get(1).indices);
        assertEquals(List.of(300), rows.get(1).widths);
        assertEquals(List.of(4), rows.get(2).indices);
    }

    @Test
    public void repacksWhenAvailableWidthChanges() {
        List<Integer> widths = List.of(70, 120, 95, 70);
        assertEquals(2, JetStreamEpisodeGridPolicy.layout(widths, 250, 8).size());
        assertEquals(3, JetStreamEpisodeGridPolicy.layout(widths, 180, 8).size());
        assertEquals(1, JetStreamEpisodeGridPolicy.layout(widths, 400, 8).size());
        assertTrue(JetStreamEpisodeGridPolicy.layout(widths, 0, 8).isEmpty());
    }

    @Test
    public void navigationBoundariesFollowActualRows() {
        List<JetStreamEpisodeGridPolicy.Row> rows = JetStreamEpisodeGridPolicy.layout(
                List.of(60, 120, 95, 400, 72), 300, 8);
        assertTrue(JetStreamEpisodeGridPolicy.handoffUp(rows, 2));
        assertFalse(JetStreamEpisodeGridPolicy.handoffUp(rows, 3));
        assertFalse(JetStreamEpisodeGridPolicy.handoffDown(rows, 3));
        assertTrue(JetStreamEpisodeGridPolicy.handoffDown(rows, 4));
        assertTrue(JetStreamEpisodeGridPolicy.atRowStart(rows, 3));
        assertTrue(JetStreamEpisodeGridPolicy.atRowEnd(rows, 3));
        assertEquals(1, JetStreamEpisodeGridPolicy.rowFor(rows, 3));
        assertEquals(-1, JetStreamEpisodeGridPolicy.rowFor(rows, 9));
    }

    @Test
    public void clampsRowsAndSelectionWithoutIncreasingViewport() {
        List<JetStreamEpisodeGridPolicy.Row> rows = JetStreamEpisodeGridPolicy.layout(
                List.of(50, 50, 50, 50, 50, 50), 70, 8);
        assertEquals(3, JetStreamEpisodeGridPolicy.visibleRows(rows));
        assertEquals(-1, JetStreamEpisodeGridPolicy.clampIndex(0, 0));
        assertEquals(0, JetStreamEpisodeGridPolicy.clampIndex(-1, 10));
        assertEquals(9, JetStreamEpisodeGridPolicy.clampIndex(10, 10));
        assertEquals(4, JetStreamEpisodeGridPolicy.clampIndex(4, 10));
        assertEquals(0, JetStreamEpisodeGridPolicy.gridHeightDp(0, 48, 8, 4));
        assertEquals(56, JetStreamEpisodeGridPolicy.gridHeightDp(1, 48, 8, 4));
        assertEquals(112, JetStreamEpisodeGridPolicy.gridHeightDp(2, 48, 8, 4));
        assertEquals(168, JetStreamEpisodeGridPolicy.gridHeightDp(3, 48, 8, 4));
    }

    @Test
    public void exactRowFitAndNewRowsKeepMeasuredWidthsAndOrder() {
        List<JetStreamEpisodeGridPolicy.Row> rows = JetStreamEpisodeGridPolicy.layout(
                List.of(50, 242, 140, 150, 20), 300, 8);
        assertEquals(List.of(0, 1), rows.get(0).indices);
        assertEquals(List.of(50, 242), rows.get(0).widths);
        assertEquals(List.of(2, 3), rows.get(1).indices);
        assertEquals(List.of(140, 150), rows.get(1).widths);
        assertEquals(List.of(4), rows.get(2).indices);
        assertEquals(List.of(20), rows.get(2).widths);
    }

    @Test
    public void largeEpisodeListsRemainBoundedAndFullyReachable() {
        List<JetStreamEpisodeGridPolicy.Row> rows = JetStreamEpisodeGridPolicy.layout(
                java.util.Collections.nCopies(3000, 64), 640, 8);
        int expected = 0;
        for (JetStreamEpisodeGridPolicy.Row row : rows) {
            int used = Math.max(0, row.indices.size() - 1) * 8;
            for (int column = 0; column < row.indices.size(); column++) {
                assertEquals(expected++, row.indices.get(column).intValue());
                used += row.widths.get(column);
            }
            assertTrue(used <= 640);
        }
        assertEquals(3000, expected);
        assertEquals(3, JetStreamEpisodeGridPolicy.visibleRows(rows));
        assertTrue(JetStreamEpisodeGridPolicy.handoffDown(rows, 2999));
    }
}
