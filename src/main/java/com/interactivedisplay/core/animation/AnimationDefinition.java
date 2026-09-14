package com.interactivedisplay.core.animation;

import java.util.Locale;
import java.util.Objects;

public record AnimationDefinition(
        String type,
        int delay,
        int duration,
        int interval,
        int charsPerStep,
        AnimationInterpolation interpolation
) {
    public AnimationDefinition {
        Objects.requireNonNull(type, "type");
        type = type.trim().toLowerCase(Locale.ROOT);
        if (type.isEmpty()) {
            throw new IllegalArgumentException("animation type must not be blank");
        }
        if (delay < 0) {
            throw new IllegalArgumentException("animation delay must be >= 0");
        }
        if (duration < 0) {
            throw new IllegalArgumentException("animation duration must be >= 0");
        }
        if (interval < 1) {
            throw new IllegalArgumentException("animation interval must be >= 1");
        }
        if (charsPerStep < 1) {
            throw new IllegalArgumentException("animation charsPerStep must be >= 1");
        }
        interpolation = interpolation == null ? AnimationInterpolation.LINEAR : interpolation;
    }
}
