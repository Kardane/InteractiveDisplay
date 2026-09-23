package com.interactivedisplay.core.component;

import java.util.Locale;

public enum ComponentAnchor {
    NONE,
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    CENTER_LEFT,
    CENTER,
    CENTER_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT;

    public static ComponentAnchor fromString(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        return switch (value.toLowerCase(Locale.ROOT).replace('_', '-')) {
            case "top-left" -> TOP_LEFT;
            case "top-center" -> TOP_CENTER;
            case "top-right" -> TOP_RIGHT;
            case "center-left" -> CENTER_LEFT;
            case "center" -> CENTER;
            case "center-right" -> CENTER_RIGHT;
            case "bottom-left" -> BOTTOM_LEFT;
            case "bottom-center" -> BOTTOM_CENTER;
            case "bottom-right" -> BOTTOM_RIGHT;
            default -> throw new IllegalArgumentException("unsupported anchor: " + value);
        };
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
