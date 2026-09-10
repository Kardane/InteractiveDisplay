package com.interactivedisplay.core.component;

import java.util.Map;

public record ComponentAction(
        ComponentActionType type,
        String target,
        Integer permissionLevel,
        Map<String, String> parameters
) {
    public ComponentAction(ComponentActionType type, String target, Integer permissionLevel) {
        this(type, target, permissionLevel, Map.of());
    }

    public ComponentAction {
        parameters = Map.copyOf(parameters == null ? Map.of() : parameters);
    }

    public static ComponentAction closeWindow() {
        return new ComponentAction(ComponentActionType.CLOSE_WINDOW, null, null);
    }

    public static ComponentAction openWindow(String targetWindowId) {
        return new ComponentAction(ComponentActionType.OPEN_WINDOW, targetWindowId, null);
    }

    public static ComponentAction switchModeFixed() {
        return new ComponentAction(ComponentActionType.SWITCH_MODE_FIXED, null, null);
    }

    public static ComponentAction switchModePlayerFixed() {
        return new ComponentAction(ComponentActionType.SWITCH_MODE_PLAYER_FIXED, null, null);
    }

    public static ComponentAction togglePlacementTracking() {
        return new ComponentAction(ComponentActionType.TOGGLE_PLACEMENT_TRACKING, null, null);
    }

    public static ComponentAction runCommand(String command) {
        return runCommand(command, null);
    }

    public static ComponentAction runCommand(String command, Integer permissionLevel) {
        return new ComponentAction(ComponentActionType.RUN_COMMAND, command, permissionLevel);
    }

    public static ComponentAction callback(String callbackId) {
        return new ComponentAction(ComponentActionType.CALLBACK, callbackId, null);
    }

    public static ComponentAction custom(String actionId, Map<String, String> parameters) {
        return new ComponentAction(ComponentActionType.CUSTOM, actionId, null, parameters);
    }
}
