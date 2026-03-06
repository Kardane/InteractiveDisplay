package com.interactivedisplay.core.window;

public record WindowOrbit(float yaw, float pitch) {
    public static WindowOrbit zero() {
        return new WindowOrbit(0.0f, 0.0f);
    }

    public WindowOrbit plus(WindowOrbit other) {
        return new WindowOrbit(this.yaw + other.yaw, this.pitch + other.pitch);
    }
}
