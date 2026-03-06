package com.interactivedisplay.core.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ClickType;
import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.interaction.UiHitResult;
import com.interactivedisplay.core.positioning.CoordinateTransformer;
import com.interactivedisplay.core.positioning.PositionMode;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class UiHitResolverTest {
    private final WindowStateStore store = new WindowStateStore();
    private final UiHitResolver resolver = new UiHitResolver(store, new CoordinateTransformer());

    @Test
    void shouldReturnClosestInteractiveRuntime() {
        UUID owner = UUID.randomUUID();
        WindowInstance farWindow = window(owner, "far", null, null, World.OVERWORLD, new Vec3d(0.0, 0.0, 4.0));
        farWindow.addRuntime(buttonRuntime("far_button", new Vector3f(0.0f, 0.0f, 0.0f)));
        WindowInstance nearWindow = window(owner, "near", null, null, World.OVERWORLD, new Vec3d(0.0, 0.0, 2.0));
        nearWindow.addRuntime(buttonRuntime("near_button", new Vector3f(0.0f, 0.0f, 0.0f)));

        store.putActiveWindow(owner, farWindow.windowId(), farWindow);
        store.putActiveWindow(owner, nearWindow.windowId(), nearWindow);

        UiHitResult hit = resolver.findUiHit(owner, World.OVERWORLD, Vec3d.ZERO, new Vec3d(0.0, 0.0, 1.0));

        assertNotNull(hit);
        assertEquals("near", hit.windowId());
        assertEquals("near_button", hit.componentId());
    }

    @Test
    void shouldIgnoreNonInteractiveRuntime() {
        UUID owner = UUID.randomUUID();
        WindowInstance textWindow = window(owner, "text_only", null, null, World.OVERWORLD, new Vec3d(0.0, 0.0, 2.0));
        textWindow.addRuntime(textRuntime("title", new Vector3f(0.0f, 0.0f, 0.0f)));
        WindowInstance buttonWindow = window(owner, "button_only", null, null, World.OVERWORLD, new Vec3d(0.0, 0.0, 4.0));
        buttonWindow.addRuntime(buttonRuntime("submit", new Vector3f(0.0f, 0.0f, 0.0f)));

        store.putActiveWindow(owner, textWindow.windowId(), textWindow);
        store.putActiveWindow(owner, buttonWindow.windowId(), buttonWindow);

        UiHitResult hit = resolver.findUiHit(owner, World.OVERWORLD, Vec3d.ZERO, new Vec3d(0.0, 0.0, 1.0));

        assertNotNull(hit);
        assertEquals("button_only", hit.windowId());
        assertEquals("submit", hit.componentId());
    }

    @Test
    void shouldIgnoreOtherWorldAndPreserveGroupId() {
        UUID owner = UUID.randomUUID();
        WindowInstance otherWorld = window(owner, "nether_window", null, null, World.NETHER, new Vec3d(0.0, 0.0, 1.0));
        otherWorld.addRuntime(buttonRuntime("nether_button", new Vector3f(0.0f, 0.0f, 0.0f)));
        WindowInstance groupWindow = window(owner, "settings", "menu_group", "settings", World.OVERWORLD, new Vec3d(0.0, 0.0, 3.0));
        groupWindow.addRuntime(buttonRuntime("apply", new Vector3f(0.0f, 0.0f, 0.0f)));

        store.putActiveWindow(owner, otherWorld.windowId(), otherWorld);
        store.putActiveGroup(owner, "menu_group", new WindowGroupInstance(
                owner,
                "menu_group",
                new Vec3d(1.0, 2.0, 3.0),
                25.0f,
                -10.0f,
                PositionMode.PLAYER_FIXED,
                "settings",
                groupWindow
        ));

        UiHitResult hit = resolver.findUiHit(owner, World.OVERWORLD, Vec3d.ZERO, new Vec3d(0.0, 0.0, 1.0));

        assertNotNull(hit);
        assertEquals("settings", hit.windowId());
        assertEquals("menu_group", hit.navigationContext().groupId());
        assertEquals(25.0f, hit.navigationContext().fixedYaw(), 0.0001f);
    }

    @Test
    void shouldReturnNullWhenNothingHits() {
        UUID owner = UUID.randomUUID();
        WindowInstance window = window(owner, "main_menu", null, null, World.OVERWORLD, new Vec3d(5.0, 0.0, 2.0));
        window.addRuntime(buttonRuntime("close", new Vector3f(0.0f, 0.0f, 0.0f)));
        store.putActiveWindow(owner, window.windowId(), window);

        UiHitResult hit = resolver.findUiHit(owner, World.OVERWORLD, Vec3d.ZERO, new Vec3d(0.0, 0.0, 1.0));

        assertNull(hit);
    }

    private static WindowInstance window(UUID owner,
                                         String windowId,
                                         String groupId,
                                         String groupWindowId,
                                         net.minecraft.registry.RegistryKey<World> worldKey,
                                         Vec3d anchor) {
        return new WindowInstance(
                owner,
                windowId,
                groupId,
                groupWindowId,
                worldKey,
                PositionMode.FIXED,
                anchor,
                0.0f,
                0.0f,
                UUID.randomUUID(),
                anchor,
                0.0f,
                0.0f,
                anchor,
                0.0f,
                0.0f,
                0L
        );
    }

    private static WindowComponentRuntime buttonRuntime(String componentId, Vector3f localPosition) {
        return new WindowComponentRuntime(
                World.OVERWORLD,
                "button:" + componentId,
                new ButtonComponentDefinition(
                        componentId,
                        new ComponentPosition(0.0f, 0.0f, 0.0f),
                        new ComponentSize(1.0f, 0.4f),
                        true,
                        1.0f,
                        "버튼",
                        1.0f,
                        "#AA2222",
                        "#FFFFFF",
                        null,
                        ClickType.RIGHT,
                        ComponentAction.closeWindow()
                ),
                localPosition,
                UUID.randomUUID(),
                null
        );
    }

    private static WindowComponentRuntime textRuntime(String componentId, Vector3f localPosition) {
        return new WindowComponentRuntime(
                World.OVERWORLD,
                "text:" + componentId,
                new TextComponentDefinition(
                        componentId,
                        new ComponentPosition(0.0f, 0.0f, 0.0f),
                        new ComponentSize(1.0f, 0.2f),
                        true,
                        1.0f,
                        "본문",
                        1.0f,
                        "#FFFFFF",
                        "left",
                        100,
                        true,
                        "#00000000"
                ),
                localPosition,
                UUID.randomUUID(),
                null
        );
    }
}
