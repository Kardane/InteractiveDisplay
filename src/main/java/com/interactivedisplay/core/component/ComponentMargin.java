package com.interactivedisplay.core.component;

public record ComponentMargin(float top, float right, float bottom, float left) {
    public ComponentMargin {
        requireNonNegativeFinite(top, "top");
        requireNonNegativeFinite(right, "right");
        requireNonNegativeFinite(bottom, "bottom");
        requireNonNegativeFinite(left, "left");
    }

    public static ComponentMargin zero() {
        return new ComponentMargin(0.0f, 0.0f, 0.0f, 0.0f);
    }

    public float horizontal() {
        return left + right;
    }

    public float vertical() {
        return top + bottom;
    }

    private static void requireNonNegativeFinite(float value, String name) {
        if (!Float.isFinite(value) || value < 0.0f) {
            throw new IllegalArgumentException("margin " + name + " must be a finite non-negative number");
        }
    }
}
