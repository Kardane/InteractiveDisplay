package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.entity.VirtualWindowHolder;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class InteractiveDisplayDisconnectCleanupGameTest implements CustomTestMethodInvoker {
    @SuppressWarnings("removal")
    @GameTest
    public void disconnectEventShouldRemoveOwnerWindowsGroupsBindingsAndVirtualEntities(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var manager = InteractiveDisplay.instance().windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        helper.assertTrue(player.connection != null, Component.literal("mock player has no play connection"));

        var windowOpen = manager.createWindow(player, "main_menu2", PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
        helper.assertTrue(windowOpen.success(), Component.literal("disconnect fixture window failed to open: " + windowOpen.message()));
        var groupOpen = manager.createGroup(player, "menu_group", PositionMode.PLAYER_VIEW, null, 0.0f, 0.0f);
        helper.assertTrue(groupOpen.success(), Component.literal("disconnect fixture group failed to open: " + groupOpen.message()));

        var standalone = manager.findActiveWindow(player.getUUID(), "main_menu2");
        var group = manager.findActiveGroup(player.getUUID(), "menu_group");
        helper.assertTrue(standalone != null && group != null,
                Component.literal("disconnect fixture did not create both standalone and group runtime"));
        var standaloneHolder = standalone.virtualHolder();
        var groupHolder = group.currentWindow().virtualHolder();
        helper.assertTrue(manager.ownerWindows(player.getUUID()).size() >= 2,
                Component.literal("disconnect fixture owner window topology incomplete"));
        helper.assertTrue(!manager.bindingSnapshots(player.getUUID()).isEmpty(),
                Component.literal("disconnect fixture has no interaction bindings"));

        ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(player.connection, server);
        VirtualWindowHolder.destroyAllPending(server);

        helper.assertTrue(manager.findActiveWindow(player.getUUID(), "main_menu2") == null,
                Component.literal("standalone window remained active after DISCONNECT event"));
        helper.assertTrue(manager.findActiveGroup(player.getUUID(), "menu_group") == null,
                Component.literal("group remained active after DISCONNECT event"));
        helper.assertTrue(manager.ownerWindows(player.getUUID()).isEmpty(),
                Component.literal("owner window state remained after DISCONNECT event"));
        helper.assertTrue(manager.bindingSnapshots(player.getUUID()).isEmpty(),
                Component.literal("interaction bindings remained after DISCONNECT event"));
        helper.assertTrue(standaloneHolder.entityCount() == 0,
                Component.literal("standalone virtual holder retained entities after DISCONNECT event"));
        helper.assertTrue(groupHolder.entityCount() == 0,
                Component.literal("group virtual holder retained entities after DISCONNECT event"));

        helper.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
