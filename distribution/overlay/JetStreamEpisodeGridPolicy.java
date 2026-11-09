package com.fongmi.android.tv.ui.custom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class JetStreamEpisodeGridPolicy {

    public static final int MAX_VISIBLE_ROWS = 3;
    public static final int PAGE_SIZE = 20;

    private JetStreamEpisodeGridPolicy() {
    }

    public static List<Row> layout(List<Integer> widths, int availableWidth, int spacing) {
        if (widths == null || widths.isEmpty() || availableWidth <= 0) return Collections.emptyList();
        List<Row> rows = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        int used = 0;
        int gap = Math.max(0, spacing);
        for (int index = 0; index < widths.size(); index++) {
            Integer value = widths.get(index);
            int width = Math.min(availableWidth, Math.max(1, value == null ? 1 : value));
            if (!indices.isEmpty() && used + gap + width > availableWidth) {
                rows.add(new Row(indices, sizes));
                indices = new ArrayList<>();
                sizes = new ArrayList<>();
                used = 0;
            }
            if (!indices.isEmpty()) used += gap;
            indices.add(index);
            sizes.add(width);
            used += width;
        }
        if (!indices.isEmpty()) rows.add(new Row(indices, sizes));
        return rows;
    }

    public static int rowFor(List<Row> rows, int index) {
        if (rows == null || index < 0) return -1;
        for (int row = 0; row < rows.size(); row++) if (rows.get(row).indices.contains(index)) return row;
        return -1;
    }

    public static boolean atRowStart(List<Row> rows, int index) {
        int row = rowFor(rows, index);
        return row >= 0 && rows.get(row).indices.get(0) == index;
    }

    public static boolean atRowEnd(List<Row> rows, int index) {
        int row = rowFor(rows, index);
        return row >= 0 && rows.get(row).indices.get(rows.get(row).indices.size() - 1) == index;
    }

    public static boolean handoffUp(List<Row> rows, int index) {
        return rowFor(rows, index) == 0;
    }

    public static boolean handoffDown(List<Row> rows, int index) {
        int row = rowFor(rows, index);
        return row >= 0 && row == rows.size() - 1;
    }

    public static int visibleRows(List<Row> rows) {
        return rows == null ? 0 : Math.min(MAX_VISIBLE_ROWS, rows.size());
    }

    public static int clampIndex(int index, int itemCount) {
        return itemCount <= 0 ? -1 : Math.max(0, Math.min(index, itemCount - 1));
    }

    public static int gridHeightDp(int rows, int cellHeightDp, int spacingDp, int verticalPaddingDp) {
        return rows <= 0 ? 0 : rows * cellHeightDp + (rows - 1) * spacingDp + verticalPaddingDp * 2;
    }

    public static final class Row {
        public final List<Integer> indices;
        public final List<Integer> widths;

        private Row(List<Integer> indices, List<Integer> widths) {
            this.indices = Collections.unmodifiableList(new ArrayList<>(indices));
            this.widths = Collections.unmodifiableList(new ArrayList<>(widths));
        }
    }
}
