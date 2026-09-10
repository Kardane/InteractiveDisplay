package com.interactivedisplay.api.group;

import com.interactivedisplay.api.window.WindowPositionMode;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public interface GroupApi {
    OperationResult open(ServerPlayer player, ResourceLocation groupId, GroupOpenOptions options);

    default OperationResult open(ServerPlayer player, ResourceLocation groupId) {
        return open(player, groupId, GroupOpenOptions.playerView());
    }

    OperationResult close(ServerPlayer player, ResourceLocation groupId);

    boolean isOpen(ServerPlayer player, ResourceLocation groupId);

    Optional<GroupHandle> find(ServerPlayer player, ResourceLocation groupId);

    interface GroupHandle {
        ResourceLocation id();

        UUID ownerId();

        Optional<WindowPositionMode> mode();

        Optional<ResourceLocation> currentWindowId();

        boolean isOpen();

        OperationResult close();
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
