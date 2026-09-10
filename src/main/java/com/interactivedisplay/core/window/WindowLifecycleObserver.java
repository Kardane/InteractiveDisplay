package com.interactivedisplay.core.window;

import com.interactivedisplay.core.positioning.PositionMode;
import java.util.UUID;

public interface WindowLifecycleObserver {
    WindowLifecycleObserver NOOP = new WindowLifecycleObserver() {
        @Override
        public void opened(UUID owner, String windowId, PositionMode mode) {
        }

        @Override
        public void closed(UUID owner, String windowId, PositionMode mode) {
        }
    };

    void opened(UUID owner, String windowId, PositionMode mode);

    void closed(UUID owner, String windowId, PositionMode mode);
}
