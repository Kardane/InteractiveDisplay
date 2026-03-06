package com.interactivedisplay.core.window;

import com.interactivedisplay.debug.DebugReason;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;

public record CreateWindowResult(
        boolean success,
        DebugReason reasonCode,
        UUID playerUuid,
        String playerName,
        String windowId,
        String componentId,
        Vec3d anchor,
        int layoutComponentCount,
        int spawnedEntityCount,
        String message
) {
    public static CreateWindowResult success(UUID playerUuid,
                                             String playerName,
                                             String windowId,
                                             Vec3d anchor,
                                             int layoutComponentCount,
                                             int spawnedEntityCount,
                                             String message) {
        return new CreateWindowResult(
                true,
                null,
                playerUuid,
                playerName,
                windowId,
                null,
                anchor,
                layoutComponentCount,
                spawnedEntityCount,
                message
        );
    }

    public static CreateWindowResult failure(DebugReason reasonCode,
                                             UUID playerUuid,
                                             String playerName,
                                             String windowId,
                                             String componentId,
                                             Vec3d anchor,
                                             int layoutComponentCount,
                                             int spawnedEntityCount,
                                             String message) {
        return new CreateWindowResult(
                false,
                reasonCode,
                playerUuid,
                playerName,
                windowId,
                componentId,
                anchor,
                layoutComponentCount,
                spawnedEntityCount,
                message
        );
    }
}
