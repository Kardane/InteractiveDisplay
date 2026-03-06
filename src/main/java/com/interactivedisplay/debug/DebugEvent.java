package com.interactivedisplay.debug;

import java.time.Instant;
import java.util.UUID;

public record DebugEvent(
        Instant timestamp,
        DebugEventType type,
        DebugLevel level,
        UUID playerUuid,
        String playerName,
        String windowId,
        String componentId,
        UUID entityUuid,
        DebugReason reasonCode,
        String message,
        String exceptionSummary
) {
    public boolean isFailure() {
        return this.level.isFailure();
    }

    public boolean matchesPlayer(UUID playerUuid) {
        return playerUuid == null || playerUuid.equals(this.playerUuid);
    }

    public boolean matchesWindow(String windowId) {
        return windowId == null || windowId.equals(this.windowId);
    }
}
