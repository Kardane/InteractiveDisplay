package com.interactivedisplay.internal.api;

import com.interactivedisplay.api.event.EventApi;
import com.interactivedisplay.api.window.WindowPositionMode;
import com.interactivedisplay.core.positioning.PositionMode;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

public final class PublicEventDispatcher {
    private static final List<Consumer<EventApi.WindowEvent>> WINDOW_OPENED = new CopyOnWriteArrayList<>();
    private static final List<Consumer<EventApi.WindowEvent>> WINDOW_CLOSED = new CopyOnWriteArrayList<>();
    private static final List<Consumer<EventApi.ButtonClickEvent>> BUTTON_CLICKED = new CopyOnWriteArrayList<>();
    private static final EventApi API = new EventApi() {
        @Override
        public Subscription onWindowOpened(Consumer<WindowEvent> listener) {
            WINDOW_OPENED.add(listener);
            return () -> WINDOW_OPENED.remove(listener);
        }

        @Override
        public Subscription onWindowClosed(Consumer<WindowEvent> listener) {
            WINDOW_CLOSED.add(listener);
            return () -> WINDOW_CLOSED.remove(listener);
        }

        @Override
        public Subscription onButtonClicked(Consumer<ButtonClickEvent> listener) {
            BUTTON_CLICKED.add(listener);
            return () -> BUTTON_CLICKED.remove(listener);
        }
    };

    private PublicEventDispatcher() {
    }

    static EventApi api() {
        return API;
    }

    public static void fireWindowOpened(UUID ownerId, String internalWindowId, PositionMode mode) {
        EventApi.WindowEvent event = new EventApi.WindowEvent(ownerId, toPublicWindowId(internalWindowId), toPublicMode(mode));
        for (Consumer<EventApi.WindowEvent> listener : WINDOW_OPENED) {
            listener.accept(event);
        }
    }

    public static void fireWindowClosed(UUID ownerId, String internalWindowId, PositionMode mode) {
        EventApi.WindowEvent event = new EventApi.WindowEvent(ownerId, toPublicWindowId(internalWindowId), toPublicMode(mode));
        for (Consumer<EventApi.WindowEvent> listener : WINDOW_CLOSED) {
            listener.accept(event);
        }
    }

    public static void fireButtonClicked(UUID ownerId, String internalWindowId, String componentId) {
        EventApi.ButtonClickEvent event = new EventApi.ButtonClickEvent(ownerId, toPublicWindowId(internalWindowId), componentId);
        for (Consumer<EventApi.ButtonClickEvent> listener : BUTTON_CLICKED) {
            listener.accept(event);
        }
    }

    private static ResourceLocation toPublicWindowId(String internalId) {
        return PublicIdCodec.toPublicWindowId(internalId);
    }

    private static WindowPositionMode toPublicMode(PositionMode mode) {
        return WindowPositionMode.valueOf(mode.name());
    }
}
