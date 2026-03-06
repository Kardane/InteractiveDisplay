package com.interactivedisplay.core.window;

import com.interactivedisplay.core.positioning.PositionMode;
import java.util.List;

public record WindowGroupDefinition(
        String id,
        String initialWindowId,
        PositionMode defaultMode,
        List<WindowGroupEntry> windows
) {
    public WindowGroupEntry entry(String windowId) {
        return this.windows.stream()
                .filter(entry -> entry.windowId().equals(windowId))
                .findFirst()
                .orElse(null);
    }
}
