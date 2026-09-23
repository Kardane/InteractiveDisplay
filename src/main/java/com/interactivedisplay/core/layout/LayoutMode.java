package com.interactivedisplay.core.layout;

public enum LayoutMode {
    ABSOLUTE,
    VERTICAL,
    HORIZONTAL,
    GRID;

    public static LayoutMode fromString(String value) {
        if (value == null || value.isBlank()) {
            return ABSOLUTE;
        }
        return switch (value.toLowerCase()) {
            case "vertical" -> VERTICAL;
            case "horizontal" -> HORIZONTAL;
            case "grid" -> GRID;
            default -> ABSOLUTE;
        };
    }
}
