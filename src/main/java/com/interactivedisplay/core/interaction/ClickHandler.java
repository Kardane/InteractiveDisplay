package com.interactivedisplay.core.interaction;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.component.ComponentActionType;
import com.interactivedisplay.core.window.ActionExecutionResult;
import com.interactivedisplay.core.window.CreateWindowResult;
import com.interactivedisplay.core.window.RemoveWindowResult;
import com.interactivedisplay.core.window.WindowActionExecutor;
import com.interactivedisplay.core.window.WindowNavigationContext;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.debug.DebugEventType;
import com.interactivedisplay.debug.DebugLevel;
import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.debug.DebugRecorder;
import java.util.UUID;

public final class ClickHandler {
    private final WindowActionExecutor actionExecutor;
    private final DebugRecorder debugRecorder;

    public ClickHandler(WindowActionExecutor actionExecutor, DebugRecorder debugRecorder) {
        this.actionExecutor = actionExecutor;
        this.debugRecorder = debugRecorder;
    }

    public ClickHandleResult handle(UUID playerId, String playerName, UiHitResult hitResult) {
        if (hitResult == null) {
            return pass(DebugLevel.DEBUG, DebugReason.INTERACTION_NOT_FOUND, playerId, playerName, null, null, null, "선택된 UI hit 없음");
        }
        WindowNavigationContext context = hitResult.navigationContext();

        if (hitResult.action().type() == ComponentActionType.CLOSE_WINDOW) {
            RemoveWindowResult result = this.actionExecutor.closeWindow(playerId, context);
            if (!result.success()) {
                return pass(DebugLevel.WARN, result.reasonCode(), playerId, playerName, hitResult.windowId(), hitResult.componentId(), null, result.message());
            }
            ClickHandleResult clickResult = ClickHandleResult.consumed(playerId, playerName, hitResult.windowId(), hitResult.componentId(), null, "close_window 처리 완료");
            record(DebugLevel.DEBUG, clickResult);
            return clickResult;
        }

        if (hitResult.action().type() == ComponentActionType.OPEN_WINDOW) {
            String targetWindowId = hitResult.action().target();
            if (targetWindowId == null || targetWindowId.isBlank()) {
                return pass(DebugLevel.WARN, DebugReason.ACTION_TARGET_NOT_FOUND, playerId, playerName, hitResult.windowId(), hitResult.componentId(), null, "open_window target 없음");
            }
            CreateWindowResult result = this.actionExecutor.openWindow(playerId, context, targetWindowId);
            if (!result.success()) {
                return pass(DebugLevel.WARN, result.reasonCode(), playerId, playerName, hitResult.windowId(), hitResult.componentId(), targetWindowId, result.message());
            }
            ClickHandleResult clickResult = ClickHandleResult.consumed(playerId, playerName, hitResult.windowId(), hitResult.componentId(), targetWindowId, "open_window 처리 완료");
            record(DebugLevel.DEBUG, clickResult);
            return clickResult;
        }

        if (hitResult.action().type() == ComponentActionType.SWITCH_MODE_FIXED) {
            CreateWindowResult result = this.actionExecutor.switchMode(playerId, context, PositionMode.FIXED);
            if (!result.success()) {
                return pass(DebugLevel.WARN, result.reasonCode(), playerId, playerName, hitResult.windowId(), hitResult.componentId(), PositionMode.FIXED.name(), result.message());
            }
            ClickHandleResult clickResult = ClickHandleResult.consumed(playerId, playerName, hitResult.windowId(), hitResult.componentId(), PositionMode.FIXED.name(), "switch_mode_fixed 처리 완료");
            record(DebugLevel.DEBUG, clickResult);
            return clickResult;
        }

        if (hitResult.action().type() == ComponentActionType.SWITCH_MODE_PLAYER_FIXED) {
            CreateWindowResult result = this.actionExecutor.switchMode(playerId, context, PositionMode.PLAYER_FIXED);
            if (!result.success()) {
                return pass(DebugLevel.WARN, result.reasonCode(), playerId, playerName, hitResult.windowId(), hitResult.componentId(), PositionMode.PLAYER_FIXED.name(), result.message());
            }
            ClickHandleResult clickResult = ClickHandleResult.consumed(playerId, playerName, hitResult.windowId(), hitResult.componentId(), PositionMode.PLAYER_FIXED.name(), "switch_mode_player_fixed 처리 완료");
            record(DebugLevel.DEBUG, clickResult);
            return clickResult;
        }

        if (hitResult.action().type() == ComponentActionType.TOGGLE_PLACEMENT_TRACKING) {
            ActionExecutionResult result = this.actionExecutor.togglePlacementTracking(playerId, context);
            if (!result.success()) {
                return pass(DebugLevel.WARN, result.reasonCode(), playerId, playerName, hitResult.windowId(), hitResult.componentId(), null, result.message());
            }
            ClickHandleResult clickResult = ClickHandleResult.consumed(playerId, playerName, hitResult.windowId(), hitResult.componentId(), null, result.message());
            record(DebugLevel.DEBUG, clickResult);
            return clickResult;
        }

        if (hitResult.action().type() == ComponentActionType.RUN_COMMAND) {
            String command = hitResult.action().target();
            if (command == null || command.isBlank()) {
                return pass(DebugLevel.WARN, DebugReason.ACTION_TARGET_NOT_FOUND, playerId, playerName, hitResult.windowId(), hitResult.componentId(), null, "run_command command 없음");
            }
            ActionExecutionResult result = this.actionExecutor.runCommand(playerId, hitResult, hitResult.action().permissionLevel(), command);
            if (!result.success()) {
                return pass(DebugLevel.WARN, result.reasonCode(), playerId, playerName, hitResult.windowId(), hitResult.componentId(), command, result.message());
            }
            ClickHandleResult clickResult = ClickHandleResult.consumed(playerId, playerName, hitResult.windowId(), hitResult.componentId(), command, result.message());
            record(DebugLevel.DEBUG, clickResult);
            return clickResult;
        }

        if (hitResult.action().type() == ComponentActionType.CALLBACK) {
            String callbackId = hitResult.action().target();
            if (callbackId == null || callbackId.isBlank()) {
                return pass(DebugLevel.WARN, DebugReason.ACTION_TARGET_NOT_FOUND, playerId, playerName, hitResult.windowId(), hitResult.componentId(), null, "callback id 없음");
            }
            ActionExecutionResult result = this.actionExecutor.executeCallback(playerId, hitResult.windowId(), hitResult.componentId(), callbackId);
            if (!result.success()) {
                return pass(DebugLevel.WARN, result.reasonCode(), playerId, playerName, hitResult.windowId(), hitResult.componentId(), callbackId, result.message());
            }
            ClickHandleResult clickResult = ClickHandleResult.consumed(playerId, playerName, hitResult.windowId(), hitResult.componentId(), callbackId, result.message());
            record(DebugLevel.DEBUG, clickResult);
            return clickResult;
        }

        return pass(DebugLevel.WARN, DebugReason.ACTION_EXECUTION_FAILED, playerId, playerName, hitResult.windowId(), hitResult.componentId(), hitResult.action().target(), "지원하지 않는 action 타입");
    }

    private ClickHandleResult pass(DebugLevel level,
                                   DebugReason reasonCode,
                                   UUID playerId,
                                   String playerName,
                                   String windowId,
                                   String componentId,
                                   String targetWindowId,
                                   String message) {
        ClickHandleResult result = ClickHandleResult.pass(reasonCode, playerId, playerName, windowId, componentId, targetWindowId, message);
        record(level, result);
        return result;
    }

    private void record(DebugLevel level, ClickHandleResult result) {
        this.debugRecorder.record(DebugEventType.CLICK_HANDLE, level, result.playerUuid(), result.playerName(), result.windowId(), result.componentId(), null, result.reasonCode(), result.message(), null);

        if (level == DebugLevel.ERROR) {
            InteractiveDisplay.LOGGER.error("[{}] click handle error player={} windowId={} componentId={} reasonCode={} message={}", InteractiveDisplay.MOD_ID, result.playerName(), result.windowId(), result.componentId(), result.reasonCode(), result.message());
            return;
        }
        if (level == DebugLevel.WARN) {
            InteractiveDisplay.LOGGER.warn("[{}] click handle warn player={} windowId={} componentId={} reasonCode={} message={}", InteractiveDisplay.MOD_ID, result.playerName(), result.windowId(), result.componentId(), result.reasonCode(), result.message());
            return;
        }
        InteractiveDisplay.LOGGER.debug("[{}] click handle debug player={} windowId={} componentId={} reasonCode={} message={}", InteractiveDisplay.MOD_ID, result.playerName(), result.windowId(), result.componentId(), result.reasonCode(), result.message());
    }
}
