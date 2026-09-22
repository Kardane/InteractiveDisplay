package com.interactivedisplay.core.component;

import java.util.Locale;

public enum ButtonSizeMode {
    FIXED,
    CONTENT;

    public static ButtonSizeMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return FIXED;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "content" -> CONTENT;
            default -> FIXED;
        };
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
