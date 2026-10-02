package com.interactivedisplay.api.callback;

import com.interactivedisplay.api.window.WindowApi;
import java.util.Objects;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public interface CallbackApi {
    RegistrationResult register(Identifier id, DisplayCallback callback);

    @FunctionalInterface
    interface DisplayCallback {
        void execute(CallbackContext context);
    }

    record CallbackContext(
            ServerPlayer player,
            Identifier windowId,
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

    record RegistrationResult(boolean success, Identifier id, String message) {
        public static RegistrationResult success(Identifier id) {
            return new RegistrationResult(true, id, "callback registered");
        }

        public static RegistrationResult failure(Identifier id, String message) {
            return new RegistrationResult(false, id, message);
        }
    }
}
