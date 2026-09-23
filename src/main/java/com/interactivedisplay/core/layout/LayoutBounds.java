package com.interactivedisplay.core.layout;

public record LayoutBounds(float centerX, float bottom, float width, float height) {
    public LayoutBounds {
        if (!Float.isFinite(centerX) || !Float.isFinite(bottom)) {
            throw new IllegalArgumentException("layout bounds position must be finite");
        }
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0.0f || height <= 0.0f) {
            throw new IllegalArgumentException("layout bounds size must be finite and positive");
        }
    }

    public static LayoutBounds centered(float width, float height) {
        return new LayoutBounds(0.0f, -height / 2.0f, width, height);
    }

    public float left() {
        return centerX - width / 2.0f;
    }

    public float right() {
        return centerX + width / 2.0f;
    }

    public float top() {
        return bottom + height;
    }

    public float centerY() {
        return bottom + height / 2.0f;
    }

    public LayoutBounds inset(float padding) {
        float innerWidth = Math.max(0.0001f, width - padding * 2.0f);
        float innerHeight = Math.max(0.0001f, height - padding * 2.0f);
        return new LayoutBounds(centerX, bottom + padding, innerWidth, innerHeight);
    }
}
