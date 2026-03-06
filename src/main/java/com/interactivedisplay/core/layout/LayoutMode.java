package com.interactivedisplay.core.layout;

public enum LayoutMode {
    ABSOLUTE,
    VERTICAL,
    HORIZONTAL;

    public static LayoutMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return ABSOLUTE;
        }
        return switch (value.toLowerCase()) {
            case "vertical" -> VERTICAL;
            case "horizontal" -> HORIZONTAL;
            default -> ABSOLUTE;
        };
    }
}
