package com.interactivedisplay.core.layout;

import java.util.Locale;

public enum OverflowPolicy {
    VISIBLE,
    ERROR;

    public static OverflowPolicy fromString(String value) {
        if (value == null || value.isBlank()) {
            return VISIBLE;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "visible" -> VISIBLE;
            case "error" -> ERROR;
            default -> throw new IllegalArgumentException("unsupported overflow policy: " + value);
        };
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
