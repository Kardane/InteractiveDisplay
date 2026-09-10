package com.interactivedisplay.core.window;

public enum WindowTransitionType {
    NONE,
    SCALE,
    SLIDE_UP,
    SLIDE_DOWN;

    public static WindowTransitionType fromString(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        return switch (value.trim().toLowerCase()) {
            case "scale" -> SCALE;
            case "slide_up" -> SLIDE_UP;
            case "slide_down" -> SLIDE_DOWN;
            default -> NONE;
        };
    }
}
