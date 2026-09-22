package com.interactivedisplay.core.component;

public record ResolvedButtonBox(
        float width,
        float height,
        float contentWidth,
        float contentHeight,
        float labelWidth,
        float labelHeight,
        int lineCount
) {
    public ResolvedButtonBox {
        if (width <= 0.0f || height <= 0.0f || contentWidth <= 0.0f || contentHeight <= 0.0f) {
            throw new IllegalArgumentException("resolved button box dimensions must be positive");
        }
        lineCount = Math.max(1, lineCount);
    }
}
