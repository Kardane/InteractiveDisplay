package com.interactivedisplay.core.component;

public record ComponentAction(ComponentActionType type, String target) {
    public static ComponentAction closeWindow() {
        return new ComponentAction(ComponentActionType.CLOSE_WINDOW, null);
    }

    public static ComponentAction openWindow(String targetWindowId) {
        return new ComponentAction(ComponentActionType.OPEN_WINDOW, targetWindowId);
    }

    public static ComponentAction runCommand(String command) {
        return new ComponentAction(ComponentActionType.RUN_COMMAND, command);
    }

    public static ComponentAction callback(String callbackId) {
        return new ComponentAction(ComponentActionType.CALLBACK, callbackId);
    }
}
