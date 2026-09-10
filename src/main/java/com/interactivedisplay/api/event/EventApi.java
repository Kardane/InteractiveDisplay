package com.interactivedisplay.api.event;

import com.interactivedisplay.api.window.WindowPositionMode;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

public interface EventApi {
    Subscription onWindowOpened(Consumer<WindowEvent> listener);

    Subscription onWindowClosed(Consumer<WindowEvent> listener);

    Subscription onButtonClicked(Consumer<ButtonClickEvent> listener);

    @FunctionalInterface
    interface Subscription extends AutoCloseable {
        @Override
        void close();
    }

    record WindowEvent(UUID ownerId, ResourceLocation windowId, WindowPositionMode mode) {
        public WindowEvent {
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(windowId, "windowId");
            Objects.requireNonNull(mode, "mode");
        }
    }

    record ButtonClickEvent(UUID ownerId, ResourceLocation windowId, String componentId) {
        public ButtonClickEvent {
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(windowId, "windowId");
            Objects.requireNonNull(componentId, "componentId");
        }
    }
}
