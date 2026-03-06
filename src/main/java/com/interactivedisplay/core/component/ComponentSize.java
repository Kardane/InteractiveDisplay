package com.interactivedisplay.core.component;

public record ComponentSize(float width, float height) {
    public ComponentSize {
        if (width <= 0.0f) {
            throw new IllegalArgumentException("width must be > 0");
        }
        if (height <= 0.0f) {
            throw new IllegalArgumentException("height must be > 0");
        }
    }
}
