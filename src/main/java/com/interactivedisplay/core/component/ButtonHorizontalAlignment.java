package com.interactivedisplay.core.component;

import java.util.Locale;

public enum ButtonHorizontalAlignment {
    LEFT,
    CENTER,
    RIGHT;

    public static ButtonHorizontalAlignment fromString(String value) {
        if (value == null || value.isBlank()) {
            return CENTER;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "left" -> LEFT;
            case "right" -> RIGHT;
            default -> CENTER;
        };
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
