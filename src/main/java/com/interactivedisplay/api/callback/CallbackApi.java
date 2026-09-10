package com.interactivedisplay.api.callback;

import com.interactivedisplay.api.window.WindowApi;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public interface CallbackApi {
    RegistrationResult register(ResourceLocation id, DisplayCallback callback);

    @FunctionalInterface
    interface DisplayCallback {
        void execute(CallbackContext context);
    }

    record CallbackContext(
            ServerPlayer player,
            ResourceLocation windowId,
            String componentId,
            WindowApi windows
    ) {
        public CallbackContext {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(windowId, "windowId");
            Objects.requireNonNull(componentId, "componentId");
            Objects.requireNonNull(windows, "windows");
        }
    }

    record RegistrationResult(boolean success, ResourceLocation id, String message) {
        public static RegistrationResult success(ResourceLocation id) {
            return new RegistrationResult(true, id, "callback registered");
        }

        public static RegistrationResult failure(ResourceLocation id, String message) {
            return new RegistrationResult(false, id, message);
        }
    }
}
