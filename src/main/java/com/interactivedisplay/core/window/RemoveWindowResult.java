package com.interactivedisplay.core.window;

import com.interactivedisplay.debug.DebugReason;
import java.util.UUID;

public record RemoveWindowResult(
        boolean success,
        DebugReason reasonCode,
        UUID owner,
        String windowId,
        int removedEntityCount,
        String message
) {
    public static RemoveWindowResult success(UUID owner, String windowId, int removedEntityCount, String message) {
        return new RemoveWindowResult(true, null, owner, windowId, removedEntityCount, message);
    }

    public static RemoveWindowResult failure(DebugReason reasonCode, UUID owner, String windowId, String message) {
        return new RemoveWindowResult(false, reasonCode, owner, windowId, 0, message);
    }
}
