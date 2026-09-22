package com.interactivedisplay.core.component;

import java.util.Locale;

public enum ButtonVerticalAlignment {
    BOTTOM,
    CENTER,
    TOP;

    public static ButtonVerticalAlignment fromString(String value) {
        if (value == null || value.isBlank()) {
            return CENTER;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "bottom" -> BOTTOM;
            case "top" -> TOP;
            default -> CENTER;
        };
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
