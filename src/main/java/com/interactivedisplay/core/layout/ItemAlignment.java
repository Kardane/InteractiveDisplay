package com.interactivedisplay.core.layout;

import java.util.Locale;

public enum ItemAlignment {
    START,
    CENTER,
    END;

    public static ItemAlignment fromString(String value) {
        if (value == null || value.isBlank()) {
            return START;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "center" -> CENTER;
            case "end" -> END;
            default -> START;
        };
    }

    public float offset(float trackSize, float itemSize) {
        float freeSpace = Math.max(0.0f, trackSize - itemSize);
        return switch (this) {
            case START -> 0.0f;
            case CENTER -> freeSpace / 2.0f;
            case END -> freeSpace;
        };
    }
}
