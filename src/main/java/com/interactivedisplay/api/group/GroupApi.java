package com.interactivedisplay.api.group;

import com.interactivedisplay.api.window.WindowPositionMode;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

public interface GroupApi {
    OperationResult open(ServerPlayer player, Identifier groupId, GroupOpenOptions options);

    default OperationResult open(ServerPlayer player, Identifier groupId) {
        return open(player, groupId, GroupOpenOptions.playerView());
    }

    OperationResult close(ServerPlayer player, Identifier groupId);

    boolean isOpen(ServerPlayer player, Identifier groupId);

    Optional<GroupHandle> find(ServerPlayer player, Identifier groupId);

    interface GroupHandle {
        Identifier id();

        UUID ownerId();

        Optional<WindowPositionMode> mode();

        Optional<Identifier> currentWindowId();

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
