package com.interactivedisplay.core.component;

public record ComponentSize(
        float width,
        float height,
        ComponentSizeMode widthMode,
        ComponentSizeMode heightMode,
        float minWidth,
        float maxWidth,
        float minHeight,
        float maxHeight
) {
    public ComponentSize(float width, float height) {
        this(
                width,
                height,
                ComponentSizeMode.FIXED,
                ComponentSizeMode.FIXED,
                0.0f,
                Float.POSITIVE_INFINITY,
                0.0f,
                Float.POSITIVE_INFINITY
        );
    }

    public ComponentSize {
        if (!Float.isFinite(width) || width <= 0.0f) {
            throw new IllegalArgumentException("width must be finite and > 0");
        }
        if (!Float.isFinite(height) || height <= 0.0f) {
            throw new IllegalArgumentException("height must be finite and > 0");
        }
        widthMode = widthMode == null ? ComponentSizeMode.FIXED : widthMode;
        heightMode = heightMode == null ? ComponentSizeMode.FIXED : heightMode;
        validateMinimum(minWidth, "minWidth");
        validateMinimum(minHeight, "minHeight");
        validateMaximum(maxWidth, "maxWidth");
        validateMaximum(maxHeight, "maxHeight");
        if (minWidth > maxWidth) {
            throw new IllegalArgumentException("minWidth must be <= maxWidth");
        }
        if (minHeight > maxHeight) {
            throw new IllegalArgumentException("minHeight must be <= maxHeight");
        }
    }

    public float resolveWidth(float availableWidth) {
        float candidate = widthMode == ComponentSizeMode.FILL ? availableWidth : width;
        return clampPositive(candidate, minWidth, maxWidth);
    }

    public float resolveHeight(float availableHeight) {
        float candidate = heightMode == ComponentSizeMode.FILL ? availableHeight : height;
        return clampPositive(candidate, minHeight, maxHeight);
    }

    public ComponentSize resolved(float resolvedWidth, float resolvedHeight) {
        return new ComponentSize(
                clampPositive(resolvedWidth, minWidth, maxWidth),
                clampPositive(resolvedHeight, minHeight, maxHeight),
                ComponentSizeMode.FIXED,
                ComponentSizeMode.FIXED,
                minWidth,
                maxWidth,
                minHeight,
                maxHeight
        );
    }

    private static float clampPositive(float value, float minimum, float maximum) {
        float finite = Float.isFinite(value) ? value : maximum;
        float clamped = Math.max(minimum, Math.min(maximum, finite));
        return Math.max(0.0001f, clamped);
    }

    private static void validateMinimum(float value, String name) {
        if (!Float.isFinite(value) || value < 0.0f) {
            throw new IllegalArgumentException(name + " must be finite and >= 0");
        }
    }

    private static void validateMaximum(float value, String name) {
        if (Float.isNaN(value) || value <= 0.0f) {
            throw new IllegalArgumentException(name + " must be > 0");
        }
    }
}
