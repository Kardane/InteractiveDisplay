package com.interactivedisplay.api.window;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public interface WindowApi {
    RegistrationResult register(WindowSpec spec);

    OperationResult open(ServerPlayer player, ResourceLocation windowId, WindowOpenOptions options);

    default OperationResult open(ServerPlayer player, ResourceLocation windowId) {
        return open(player, windowId, WindowOpenOptions.playerView());
    }

    OperationResult close(ServerPlayer player, ResourceLocation windowId);

    void closeAll(ServerPlayer player);

    boolean isOpen(ServerPlayer player, ResourceLocation windowId);

    Optional<WindowHandle> find(ServerPlayer player, ResourceLocation windowId);

    Set<ResourceLocation> registeredIds();

    interface WindowHandle {
        ResourceLocation id();

        UUID ownerId();

        Optional<WindowPositionMode> mode();

        boolean isOpen();

        OperationResult close();
    }

    record RegistrationResult(boolean success, ResourceLocation id, String message) {
        public static RegistrationResult success(ResourceLocation id) {
            return new RegistrationResult(true, id, "window registered");
        }

        public static RegistrationResult failure(ResourceLocation id, String message) {
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
