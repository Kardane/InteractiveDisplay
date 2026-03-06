package com.interactivedisplay.core.interaction;

import com.interactivedisplay.debug.DebugReason;
import java.util.UUID;

public record ClickHandleResult(
        boolean consumed,
        DebugReason reasonCode,
        UUID playerUuid,
        String playerName,
        UUID interactionEntityId,
        String windowId,
        String componentId,
        String targetWindowId,
        String message
) {
    public static ClickHandleResult consumed(UUID playerUuid,
                                             String playerName,
                                             UUID interactionEntityId,
                                             String windowId,
                                             String componentId,
                                             String targetWindowId,
                                             String message) {
        return new ClickHandleResult(
                true,
                null,
                playerUuid,
                playerName,
                interactionEntityId,
                windowId,
                componentId,
                targetWindowId,
                message
        );
    }

    public static ClickHandleResult pass(DebugReason reasonCode,
                                         UUID playerUuid,
                                         String playerName,
                                         UUID interactionEntityId,
                                         String windowId,
                                         String componentId,
                                         String targetWindowId,
                                         String message) {
        return new ClickHandleResult(
                false,
                reasonCode,
                playerUuid,
                playerName,
                interactionEntityId,
                windowId,
                componentId,
                targetWindowId,
                message
        );
    }
}
