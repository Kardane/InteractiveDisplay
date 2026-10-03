package com.interactivedisplay.internal.api;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.api.action.ActionApi;
import com.interactivedisplay.api.window.WindowApi;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public final class PublicActionDispatcher {
    private static final Map<Identifier, RegisteredAction> ACTIONS = new ConcurrentHashMap<>();

    private PublicActionDispatcher() {
    }

    static boolean register(Identifier id, ActionApi.ActionHandler handler, WindowApi windows) {
        return ACTIONS.putIfAbsent(id, new RegisteredAction(handler, windows)) == null;
    }

    public static boolean isRegistered(Identifier id) {
        return id != null && ACTIONS.containsKey(id);
    }

    public static ExecutionResult execute(
            ServerPlayer player,
            String internalWindowId,
            String componentId,
            String rawActionId,
            Map<String, String> parameters
    ) {
        Identifier actionId = Identifier.tryParse(rawActionId);
        if (actionId == null || rawActionId.indexOf(':') < 0) {
            return ExecutionResult.failure("invalid custom action id: " + rawActionId);
        }
        RegisteredAction action = ACTIONS.get(actionId);
        if (action == null) {
            return ExecutionResult.failure("unregistered custom action: " + actionId);
        }
        try {
            action.handler().execute(new ActionApi.ActionContext(
                    player,
                    PublicIdCodec.toPublicWindowId(internalWindowId),
                    componentId,
                    actionId,
                    parameters,
                    action.windows()
            ));
            return ExecutionResult.success("custom action handled: " + actionId);
        } catch (RuntimeException exception) {
            InteractiveDisplay.LOGGER.error(
                    "[{}] public custom action failed id={} windowId={} componentId={}",
                    InteractiveDisplay.MOD_ID,
                    actionId,
                    internalWindowId,
                    componentId,
                    exception
            );
            return ExecutionResult.failure("custom action failed: " + actionId);
        }
    }

    public record ExecutionResult(boolean success, String message) {
        static ExecutionResult success(String message) {
            return new ExecutionResult(true, message);
        }

        static ExecutionResult failure(String message) {
            return new ExecutionResult(false, message);
        }
    }

    private record RegisteredAction(ActionApi.ActionHandler handler, WindowApi windows) {
    }
}
