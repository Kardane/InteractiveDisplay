package com.interactivedisplay.api.action;

import com.interactivedisplay.api.window.WindowApi;
import com.interactivedisplay.api.window.WindowSpec;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public interface ActionApi {
    RegistrationResult register(Identifier id, ActionHandler handler);

    WindowSpec.ButtonAction bind(Identifier id, Map<String, String> parameters);

    default WindowSpec.ButtonAction bind(Identifier id) {
        return bind(id, Map.of());
    }

    @FunctionalInterface
    interface ActionHandler {
        void execute(ActionContext context);
    }

    record ActionContext(
            ServerPlayer player,
            Identifier windowId,
            String componentId,
            Identifier actionId,
            Map<String, String> parameters,
            WindowApi windows
    ) {
        public ActionContext {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(windowId, "windowId");
            Objects.requireNonNull(componentId, "componentId");
            Objects.requireNonNull(actionId, "actionId");
            parameters = Map.copyOf(parameters == null ? Map.of() : parameters);
            Objects.requireNonNull(windows, "windows");
        }
    }

    record RegistrationResult(boolean success, Identifier id, String message) {
        public static RegistrationResult success(Identifier id) {
            return new RegistrationResult(true, id, "action registered");
        }

        public static RegistrationResult failure(Identifier id, String message) {
            return new RegistrationResult(false, id, message);
        }
    }
}
