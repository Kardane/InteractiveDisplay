package com.interactivedisplay.core.window;

import com.interactivedisplay.core.interaction.UiHitResult;
import java.util.UUID;

public interface WindowActionExecutor {
    RemoveWindowResult closeWindow(UUID owner, WindowNavigationContext context);

    CreateWindowResult openWindow(UUID owner, WindowNavigationContext context, String windowId);

    CreateWindowResult switchMode(UUID owner, WindowNavigationContext context, com.interactivedisplay.core.positioning.PositionMode positionMode);

    ActionExecutionResult runCommand(UUID owner, UiHitResult hitResult, Integer permissionLevel, String command);

    ActionExecutionResult executeCallback(UUID owner, String windowId, String componentId, String callbackId);

    default ActionExecutionResult openTextInput(UUID owner, UiHitResult hitResult) {
        return ActionExecutionResult.failure(DebugReason.ACTION_EXECUTION_FAILED, "text input action is not supported");
    }

    ActionExecutionResult togglePlacementTracking(UUID owner, WindowNavigationContext context);
}
