package com.interactivedisplay.core.animation;

import java.util.Locale;

public enum AnimationInterpolation {
    LINEAR,
    SMOOTH,
    CUT;

    public static AnimationInterpolation fromString(String value) {
        if (value == null || value.isBlank()) {
            return LINEAR;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "smooth" -> SMOOTH;
            case "cut" -> CUT;
            default -> LINEAR;
        };
    }

    public float apply(float progress) {
        float t = Math.max(0.0f, Math.min(1.0f, progress));
        return switch (this) {
            case LINEAR -> t;
            case SMOOTH -> t * t * (3.0f - 2.0f * t);
            case CUT -> t >= 1.0f ? 1.0f : 0.0f;
        };
    }
}
