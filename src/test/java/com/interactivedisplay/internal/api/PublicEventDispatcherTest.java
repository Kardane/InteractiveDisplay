package com.interactivedisplay.internal.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.interactivedisplay.api.event.EventApi;
import com.interactivedisplay.api.window.WindowPositionMode;
import com.interactivedisplay.core.positioning.PositionMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicEventDispatcherTest {
    @Test
    void shouldPublishAndRemoveSubscriptions() {
        List<EventApi.WindowEvent> opened = new ArrayList<>();
        List<EventApi.ButtonClickEvent> clicked = new ArrayList<>();
        EventApi.Subscription openedSubscription = PublicEventDispatcher.api().onWindowOpened(opened::add);
        EventApi.Subscription clickedSubscription = PublicEventDispatcher.api().onButtonClicked(clicked::add);
        UUID owner = UUID.randomUUID();

        PublicEventDispatcher.fireWindowOpened(owner, "economy:shop", PositionMode.PLAYER_VIEW);
        PublicEventDispatcher.fireButtonClicked(owner, "economy:shop", "buy");

        assertEquals(1, opened.size());
        assertEquals(WindowPositionMode.PLAYER_VIEW, opened.get(0).mode());
        assertEquals("economy:shop", opened.get(0).windowId().toString());
        assertEquals("buy", clicked.get(0).componentId());

        openedSubscription.close();
        clickedSubscription.close();
        PublicEventDispatcher.fireWindowOpened(owner, "economy:shop", PositionMode.PLAYER_VIEW);
        PublicEventDispatcher.fireButtonClicked(owner, "economy:shop", "buy");

        assertEquals(1, opened.size());
        assertEquals(1, clicked.size());
    }
}
