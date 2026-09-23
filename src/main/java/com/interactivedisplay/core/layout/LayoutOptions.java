package com.interactivedisplay.core.layout;

public record LayoutOptions(
        LayoutMode mode,
        float gap,
        int columns,
        float rowGap,
        float columnGap,
        ItemAlignment justifyItems,
        ItemAlignment alignItems,
        OverflowPolicy overflow
) {
    public static final float DEFAULT_GAP = 0.05f;
    public static final int DEFAULT_COLUMNS = 1;

    public LayoutOptions {
        mode = mode == null ? LayoutMode.ABSOLUTE : mode;
        requireNonNegativeFinite(gap, "gap");
        if (columns < 1) {
            throw new IllegalArgumentException("columns must be >= 1");
        }
        requireNonNegativeFinite(rowGap, "rowGap");
        requireNonNegativeFinite(columnGap, "columnGap");
        justifyItems = justifyItems == null ? ItemAlignment.START : justifyItems;
        alignItems = alignItems == null ? ItemAlignment.START : alignItems;
        overflow = overflow == null ? OverflowPolicy.VISIBLE : overflow;
    }

    public LayoutOptions(LayoutMode mode, float gap, int columns, float rowGap, float columnGap) {
        this(mode, gap, columns, rowGap, columnGap, ItemAlignment.START, ItemAlignment.START, OverflowPolicy.VISIBLE);
    }

    public LayoutOptions(LayoutMode mode,
                         float gap,
                         int columns,
                         float rowGap,
                         float columnGap,
                         ItemAlignment justifyItems,
                         ItemAlignment alignItems) {
        this(mode, gap, columns, rowGap, columnGap, justifyItems, alignItems, OverflowPolicy.VISIBLE);
    }

    public static LayoutOptions defaults(LayoutMode mode) {
        return new LayoutOptions(
                mode,
                DEFAULT_GAP,
                DEFAULT_COLUMNS,
                DEFAULT_GAP,
                DEFAULT_GAP,
                ItemAlignment.START,
                ItemAlignment.START,
                OverflowPolicy.VISIBLE
        );
    }

    private static void requireNonNegativeFinite(float value, String name) {
        if (!Float.isFinite(value) || value < 0.0f) {
            throw new IllegalArgumentException(name + " must be a finite non-negative number");
        }
    }
}
