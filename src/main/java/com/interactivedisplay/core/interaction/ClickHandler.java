package com.interactivedisplay.core.interaction;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.component.ComponentActionType;
import com.interactivedisplay.core.window.ActionExecutionResult;
import com.interactivedisplay.core.window.CreateWindowResult;
import com.interactivedisplay.core.window.RemoveWindowResult;
import com.interactivedisplay.core.window.WindowActionExecutor;
import com.interactivedisplay.debug.DebugEventType;
import com.interactivedisplay.debug.DebugLevel;
import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.debug.DebugRecorder;
import java.util.UUID;

public final class ClickHandler {
    private final InteractionRegistry registry;
    private final WindowActionExecutor actionExecutor;
    private final DebugRecorder debugRecorder;

    public ClickHandler(InteractionRegistry registry, WindowActionExecutor actionExecutor, DebugRecorder debugRecorder) {
        this.registry = registry;
        this.actionExecutor = actionExecutor;
        this.debugRecorder = debugRecorder;
    }

    public ClickHandleResult handle(UUID playerId, String playerName, UUID interactionEntityId, boolean attack) {
        InteractionBinding binding = this.registry.find(interactionEntityId).orElse(null);
        if (binding == null) {
            return pass(DebugLevel.DEBUG, DebugReason.INTERACTION_NOT_FOUND, playerId, playerName, interactionEntityId, null, null, null, "interaction 바인딩 없음");
        }

        if (!binding.owner().equals(playerId)) {
            return pass(DebugLevel.DEBUG, DebugReason.OWNER_MISMATCH, playerId, playerName, interactionEntityId, binding.windowId(), binding.componentId(), binding.action().target(), "interaction 소유자 불일치");
        }

        if (attack) {
            return pass(DebugLevel.DEBUG, DebugReason.CLICK_TYPE_MISMATCH, playerId, playerName, interactionEntityId, binding.windowId(), binding.componentId(), binding.action().target(), "좌클릭은 무시되고 우클릭만 허용");
        }

        if (binding.action().type() == ComponentActionType.CLOSE_WINDOW) {
            RemoveWindowResult result = this.actionExecutor.closeWindow(playerId, binding.windowId());
            if (!result.success()) {
                return pass(DebugLevel.WARN, result.reasonCode(), playerId, playerName, interactionEntityId, binding.windowId(), binding.componentId(), null, result.message());
            }
            ClickHandleResult clickResult = ClickHandleResult.consumed(playerId, playerName, interactionEntityId, binding.windowId(), binding.componentId(), null, "close_window 처리 완료");
            record(DebugLevel.DEBUG, clickResult);
            return clickResult;
        }

        if (binding.action().type() == ComponentActionType.OPEN_WINDOW) {
            String targetWindowId = binding.action().target();
            if (targetWindowId == null || targetWindowId.isBlank()) {
                return pass(DebugLevel.WARN, DebugReason.ACTION_TARGET_NOT_FOUND, playerId, playerName, interactionEntityId, binding.windowId(), binding.componentId(), null, "open_window target 없음");
            }
            CreateWindowResult result = this.actionExecutor.openWindow(playerId, targetWindowId);
            if (!result.success()) {
                return pass(DebugLevel.WARN, result.reasonCode(), playerId, playerName, interactionEntityId, binding.windowId(), binding.componentId(), targetWindowId, result.message());
            }
            ClickHandleResult clickResult = ClickHandleResult.consumed(playerId, playerName, interactionEntityId, binding.windowId(), binding.componentId(), targetWindowId, "open_window 처리 완료");
            record(DebugLevel.DEBUG, clickResult);
            return clickResult;
        }

        if (binding.action().type() == ComponentActionType.RUN_COMMAND) {
            String command = binding.action().target();
            if (command == null || command.isBlank()) {
                return pass(DebugLevel.WARN, DebugReason.ACTION_TARGET_NOT_FOUND, playerId, playerName, interactionEntityId, binding.windowId(), binding.componentId(), null, "run_command command 없음");
            }
            ActionExecutionResult result = this.actionExecutor.runCommand(playerId, binding.windowId(), binding.componentId(), command);
            if (!result.success()) {
                return pass(DebugLevel.WARN, result.reasonCode(), playerId, playerName, interactionEntityId, binding.windowId(), binding.componentId(), command, result.message());
            }
            ClickHandleResult clickResult = ClickHandleResult.consumed(playerId, playerName, interactionEntityId, binding.windowId(), binding.componentId(), command, result.message());
            record(DebugLevel.DEBUG, clickResult);
            return clickResult;
        }

        if (binding.action().type() == ComponentActionType.CALLBACK) {
            String callbackId = binding.action().target();
            if (callbackId == null || callbackId.isBlank()) {
                return pass(DebugLevel.WARN, DebugReason.ACTION_TARGET_NOT_FOUND, playerId, playerName, interactionEntityId, binding.windowId(), binding.componentId(), null, "callback id 없음");
            }
            ActionExecutionResult result = this.actionExecutor.executeCallback(playerId, binding.windowId(), binding.componentId(), callbackId);
            if (!result.success()) {
                return pass(DebugLevel.WARN, result.reasonCode(), playerId, playerName, interactionEntityId, binding.windowId(), binding.componentId(), callbackId, result.message());
            }
            ClickHandleResult clickResult = ClickHandleResult.consumed(playerId, playerName, interactionEntityId, binding.windowId(), binding.componentId(), callbackId, result.message());
            record(DebugLevel.DEBUG, clickResult);
            return clickResult;
        }

        return pass(DebugLevel.WARN, DebugReason.ACTION_EXECUTION_FAILED, playerId, playerName, interactionEntityId, binding.windowId(), binding.componentId(), binding.action().target(), "지원하지 않는 action 타입");
    }

    private ClickHandleResult pass(DebugLevel level,
                                   DebugReason reasonCode,
                                   UUID playerId,
                                   String playerName,
                                   UUID interactionEntityId,
                                   String windowId,
                                   String componentId,
                                   String targetWindowId,
                                   String message) {
        ClickHandleResult result = ClickHandleResult.pass(reasonCode, playerId, playerName, interactionEntityId, windowId, componentId, targetWindowId, message);
        record(level, result);
        return result;
    }

    private void record(DebugLevel level, ClickHandleResult result) {
        this.debugRecorder.record(DebugEventType.CLICK_HANDLE, level, result.playerUuid(), result.playerName(), result.windowId(), result.componentId(), result.interactionEntityId(), result.reasonCode(), result.message(), null);

        if (level == DebugLevel.ERROR) {
            InteractiveDisplay.LOGGER.error("[{}] click handle error player={} entity={} windowId={} componentId={} reasonCode={} message={}", InteractiveDisplay.MOD_ID, result.playerName(), result.interactionEntityId(), result.windowId(), result.componentId(), result.reasonCode(), result.message());
            return;
        }
        if (level == DebugLevel.WARN) {
            InteractiveDisplay.LOGGER.warn("[{}] click handle warn player={} entity={} windowId={} componentId={} reasonCode={} message={}", InteractiveDisplay.MOD_ID, result.playerName(), result.interactionEntityId(), result.windowId(), result.componentId(), result.reasonCode(), result.message());
            return;
        }
        InteractiveDisplay.LOGGER.debug("[{}] click handle debug player={} entity={} windowId={} componentId={} reasonCode={} message={}", InteractiveDisplay.MOD_ID, result.playerName(), result.interactionEntityId(), result.windowId(), result.componentId(), result.reasonCode(), result.message());
    }
}
