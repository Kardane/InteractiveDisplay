package com.interactivedisplay.core.positioning;

public record WindowOffset(float forward, float horizontal, float vertical) {
    public static WindowOffset defaults() {
        return new WindowOffset(2.0f, 0.0f, 0.5f);
    }

    public static WindowOffset zero() {
        return new WindowOffset(0.0f, 0.0f, 0.0f);
    }

    public WindowOffset plus(WindowOffset other) {
        return new WindowOffset(
                this.forward + other.forward,
                this.horizontal + other.horizontal,
                this.vertical + other.vertical
        );
    }
}
