package com.interactivedisplay.internal.api;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.api.event.EventApi;
import com.interactivedisplay.api.window.WindowPositionMode;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.window.WindowLifecycleObserver;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

public final class PublicEventDispatcher {
    private static final List<Consumer<EventApi.WindowEvent>> WINDOW_OPENED = new CopyOnWriteArrayList<>();
    private static final List<Consumer<EventApi.WindowEvent>> WINDOW_CLOSED = new CopyOnWriteArrayList<>();
    private static final List<Consumer<EventApi.ButtonClickEvent>> BUTTON_CLICKED = new CopyOnWriteArrayList<>();
    private static final WindowLifecycleObserver WINDOW_LIFECYCLE_OBSERVER = new WindowLifecycleObserver() {
        @Override
        public void opened(UUID owner, String windowId, PositionMode mode) {
            fireWindowOpened(owner, windowId, mode);
        }

        @Override
        public void closed(UUID owner, String windowId, PositionMode mode) {
            fireWindowClosed(owner, windowId, mode);
        }
    };
    private static final EventApi API = new EventApi() {
        @Override
        public Subscription onWindowOpened(Consumer<WindowEvent> listener) {
            WINDOW_OPENED.add(java.util.Objects.requireNonNull(listener, "listener"));
            return () -> WINDOW_OPENED.remove(listener);
        }

        @Override
        public Subscription onWindowClosed(Consumer<WindowEvent> listener) {
            WINDOW_CLOSED.add(java.util.Objects.requireNonNull(listener, "listener"));
            return () -> WINDOW_CLOSED.remove(listener);
        }

        @Override
        public Subscription onButtonClicked(Consumer<ButtonClickEvent> listener) {
            BUTTON_CLICKED.add(java.util.Objects.requireNonNull(listener, "listener"));
            return () -> BUTTON_CLICKED.remove(listener);
        }
    };

    private PublicEventDispatcher() {
    }

    static EventApi api() {
        return API;
    }

    public static WindowLifecycleObserver lifecycleObserver() {
        return WINDOW_LIFECYCLE_OBSERVER;
    }

    public static void fireWindowOpened(UUID ownerId, String internalWindowId, PositionMode mode) {
        EventApi.WindowEvent event = new EventApi.WindowEvent(ownerId, toPublicWindowId(internalWindowId), toPublicMode(mode));
        dispatch(WINDOW_OPENED, event, "window-opened");
    }

    public static void fireWindowClosed(UUID ownerId, String internalWindowId, PositionMode mode) {
        EventApi.WindowEvent event = new EventApi.WindowEvent(ownerId, toPublicWindowId(internalWindowId), toPublicMode(mode));
        dispatch(WINDOW_CLOSED, event, "window-closed");
    }

    public static void fireButtonClicked(UUID ownerId, String internalWindowId, String componentId) {
        EventApi.ButtonClickEvent event = new EventApi.ButtonClickEvent(ownerId, toPublicWindowId(internalWindowId), componentId);
        dispatch(BUTTON_CLICKED, event, "button-clicked");
    }

    private static <T> void dispatch(List<Consumer<T>> listeners, T event, String eventName) {
        for (Consumer<T> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException exception) {
                InteractiveDisplay.LOGGER.error(
                        "[{}] public API event listener failed event={}",
                        InteractiveDisplay.MOD_ID,
                        eventName,
                        exception
                );
            }
        }
    }

    private static ResourceLocation toPublicWindowId(String internalId) {
        return PublicIdCodec.toPublicWindowId(internalId);
    }

    private static WindowPositionMode toPublicMode(PositionMode mode) {
        return WindowPositionMode.valueOf(mode.name());
    }
}
