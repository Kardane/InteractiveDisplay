package com.interactivedisplay.core.positioning;

public record WindowOffset(float forward, float horizontal, float vertical) {
    public static WindowOffset defaults() {
        return new WindowOffset(2.0f, 0.0f, 0.5f);
    }
}
