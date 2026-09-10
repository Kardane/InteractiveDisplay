package com.interactivedisplay.api.action;

import com.interactivedisplay.api.window.WindowApi;
import com.interactivedisplay.api.window.WindowSpec;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public interface ActionApi {
    RegistrationResult register(ResourceLocation id, ActionHandler handler);

    WindowSpec.ButtonAction bind(ResourceLocation id, Map<String, String> parameters);

    default WindowSpec.ButtonAction bind(ResourceLocation id) {
        return bind(id, Map.of());
    }

    @FunctionalInterface
    interface ActionHandler {
        void execute(ActionContext context);
    }

    record ActionContext(
            ServerPlayer player,
            ResourceLocation windowId,
            String componentId,
            ResourceLocation actionId,
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

    record RegistrationResult(boolean success, ResourceLocation id, String message) {
        public static RegistrationResult success(ResourceLocation id) {
            return new RegistrationResult(true, id, "action registered");
        }

        public static RegistrationResult failure(ResourceLocation id, String message) {
            return new RegistrationResult(false, id, message);
        }
    }
}
