package com.interactivedisplay.api.window;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public interface WindowApi {
    RegistrationResult register(WindowSpec spec);

    OperationResult open(ServerPlayer player, Identifier windowId, WindowOpenOptions options);

    default OperationResult open(ServerPlayer player, Identifier windowId) {
        return open(player, windowId, WindowOpenOptions.playerView());
    }

    OperationResult close(ServerPlayer player, Identifier windowId);

    void closeAll(ServerPlayer player);

    boolean isOpen(ServerPlayer player, Identifier windowId);

    Optional<WindowHandle> find(ServerPlayer player, Identifier windowId);

    Set<Identifier> registeredIds();

    interface WindowHandle {
        Identifier id();

        UUID ownerId();

        Optional<WindowPositionMode> mode();

        boolean isOpen();

        OperationResult close();
    }

    record RegistrationResult(boolean success, Identifier id, String message) {
        public static RegistrationResult success(Identifier id) {
            return new RegistrationResult(true, id, "window registered");
        }

        public static RegistrationResult failure(Identifier id, String message) {
            return new RegistrationResult(false, id, message);
        }
    }

    record OperationResult(boolean success, String reason, String message) {
        public static OperationResult success(String message) {
            return new OperationResult(true, null, message);
        }

        public static OperationResult failure(String reason, String message) {
            return new OperationResult(false, reason, message);
        }
    }
}
