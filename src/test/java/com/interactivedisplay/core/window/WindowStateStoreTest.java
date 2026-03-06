package com.interactivedisplay.core.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ClickType;
import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.positioning.PositionMode;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class WindowStateStoreTest {
    @Test
    void mixedStandaloneAndGroupWindowsShouldKeepCountsAndLookups() {
        UUID owner = UUID.randomUUID();
        WindowStateStore store = new WindowStateStore();

        WindowInstance standalone = window(owner, "main_menu", null, null, new Vec3d(0.0, 0.0, 2.0));
        standalone.addRuntime(buttonRuntime("close", new Vector3f(0.0f, 0.0f, 0.0f)));

        WindowInstance groupWindow = window(owner, "settings", "menu_group", "settings", new Vec3d(0.0, 0.0, 3.0));
        groupWindow.addRuntime(buttonRuntime("apply", new Vector3f(0.0f, 0.0f, 0.0f)));
        groupWindow.addRuntime(textRuntime("body", new Vector3f(0.0f, 0.5f, 0.0f)));

        store.putActiveWindow(owner, standalone.windowId(), standalone);
        store.putActiveGroup(owner, "menu_group", new WindowGroupInstance(
                owner,
                "menu_group",
                new Vec3d(1.0, 2.0, 3.0),
                45.0f,
                10.0f,
                PositionMode.PLAYER_FIXED,
                groupWindow.windowId(),
                groupWindow
        ));

        assertEquals(2, store.activeWindowCount());
        assertEquals(2, store.activeBindingCount());
        assertSame(standalone, store.findActiveWindow(owner, "main_menu"));
        assertSame(groupWindow, store.findActiveGroup(owner, "menu_group").currentWindow());
        assertSame(groupWindow, store.findWindow(owner, "settings"));

        List<WindowManager.BindingSnapshot> bindings = store.bindingSnapshots(owner);
        assertEquals(2, bindings.size());
        assertTrue(bindings.stream().anyMatch(binding ->
                binding.windowId().equals("main_menu") && binding.componentId().equals("close")));
        assertTrue(bindings.stream().anyMatch(binding ->
                binding.windowId().equals("settings") && binding.componentId().equals("apply")));
    }

    @Test
    void removingOwnerStateShouldClearLookupsAndBindings() {
        UUID owner = UUID.randomUUID();
        WindowStateStore store = new WindowStateStore();

        WindowInstance standalone = window(owner, "main_menu", null, null, new Vec3d(0.0, 0.0, 2.0));
        standalone.addRuntime(buttonRuntime("close", new Vector3f(0.0f, 0.0f, 0.0f)));
        WindowInstance groupWindow = window(owner, "settings", "menu_group", "settings", new Vec3d(0.0, 0.0, 3.0));
        groupWindow.addRuntime(buttonRuntime("apply", new Vector3f(0.0f, 0.0f, 0.0f)));

        store.putActiveWindow(owner, standalone.windowId(), standalone);
        store.putActiveGroup(owner, "menu_group", new WindowGroupInstance(
                owner,
                "menu_group",
                Vec3d.ZERO,
                0.0f,
                0.0f,
                PositionMode.FIXED,
                groupWindow.windowId(),
                groupWindow
        ));

        store.removeActiveWindow(owner, "main_menu");
        store.removeActiveGroup(owner, "menu_group");

        assertTrue(store.ownerWindows(owner).isEmpty());
        assertTrue(store.ownerWindowContexts(owner).isEmpty());
        assertTrue(store.bindingSnapshots(owner).isEmpty());
        assertNull(store.findActiveWindow(owner, "main_menu"));
        assertNull(store.findActiveGroup(owner, "menu_group"));
        assertNull(store.findWindow(owner, "settings"));
    }

    private static WindowInstance window(UUID owner,
                                         String windowId,
                                         String groupId,
                                         String groupWindowId,
                                         Vec3d anchor) {
        return new WindowInstance(
                owner,
                windowId,
                groupId,
                groupWindowId,
                World.OVERWORLD,
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
