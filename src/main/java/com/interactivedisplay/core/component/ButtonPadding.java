package com.interactivedisplay.core.component;

public record ButtonPadding(float horizontal, float vertical) {
    public static ButtonPadding zero() {
        return new ButtonPadding(0.0f, 0.0f);
    }

    public ButtonPadding {
        if (!Float.isFinite(horizontal) || horizontal < 0.0f) {
            throw new IllegalArgumentException("button horizontal padding must be finite and >= 0");
        }
        if (!Float.isFinite(vertical) || vertical < 0.0f) {
            throw new IllegalArgumentException("button vertical padding must be finite and >= 0");
        }
    }
}
