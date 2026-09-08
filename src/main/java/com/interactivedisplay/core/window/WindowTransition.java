package com.interactivedisplay.core.window;

public record WindowTransition(
        int duration,
        WindowTransitionType enter,
        WindowTransitionType exit
) {
    public static final int DEFAULT_DURATION = 4;
    private static final WindowTransition NONE = new WindowTransition(0, WindowTransitionType.NONE, WindowTransitionType.NONE);

    public WindowTransition {
        duration = Math.max(0, duration);
        enter = enter == null ? WindowTransitionType.NONE : enter;
        exit = exit == null ? WindowTransitionType.NONE : exit;
    }

    public static WindowTransition none() {
        return NONE;
    }

    public boolean hasEnter() {
        return duration > 0 && enter != WindowTransitionType.NONE;
    }

    public boolean hasExit() {
        return duration > 0 && exit != WindowTransitionType.NONE;
    }
}
