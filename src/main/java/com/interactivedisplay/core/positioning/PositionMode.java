package com.interactivedisplay.core.positioning;

public enum PositionMode {
    FIXED,
    PLAYER_FIXED,
    PLAYER_VIEW;

    public static PositionMode fromArgument(String mode) {
        return switch (mode.toLowerCase()) {
            case "fixed" -> FIXED;
            case "player_fixed" -> PLAYER_FIXED;
            case "player_view" -> PLAYER_VIEW;
            default -> throw new IllegalArgumentException("Unsupported position mode: " + mode);
        };
    }
}
