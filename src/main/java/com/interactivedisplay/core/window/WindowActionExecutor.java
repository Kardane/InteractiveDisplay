package com.interactivedisplay.core.window;

import java.util.UUID;

public interface WindowActionExecutor {
    RemoveWindowResult closeWindow(UUID owner, String windowId);

    CreateWindowResult openWindow(UUID owner, String windowId);

    ActionExecutionResult runCommand(UUID owner, String windowId, String componentId, String command);

    ActionExecutionResult executeCallback(UUID owner, String windowId, String componentId, String callbackId);
}
