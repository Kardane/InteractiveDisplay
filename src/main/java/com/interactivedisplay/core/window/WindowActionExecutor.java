package com.interactivedisplay.core.window;

import java.util.UUID;

public interface WindowActionExecutor {
    RemoveWindowResult closeWindow(UUID owner, WindowNavigationContext context);

    CreateWindowResult openWindow(UUID owner, WindowNavigationContext context, String windowId);

    CreateWindowResult switchMode(UUID owner, WindowNavigationContext context, com.interactivedisplay.core.positioning.PositionMode positionMode);

    ActionExecutionResult runCommand(UUID owner, String windowId, String componentId, String command);

    ActionExecutionResult executeCallback(UUID owner, String windowId, String componentId, String callbackId);
}
