package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.positioning.PositionMode;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class InteractiveDisplaySoakServerGameTest implements CustomTestMethodInvoker {
    @SuppressWarnings("removal")
    @GameTest
    public void windowAndGroupLifecycleShouldRemainCleanAcrossOneHundredCycles(GameTestHelper helper) {
        var manager = InteractiveDisplay.instance().windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        for (int i = 0; i < 100; i++) {
            var opened = manager.createWindow(player, "main_menu", PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
            helper.assertTrue(opened.success(), Component.literal("window open failed at iteration " + i + ": " + opened.message()));
            helper.assertTrue(manager.findActiveWindow(player.getUUID(), "main_menu") != null, Component.literal("active window missing at iteration " + i));

            var closed = manager.removeWindow(player.getUUID(), "main_menu");
            helper.assertTrue(closed.success(), Component.literal("window close failed at iteration " + i + ": " + closed.message()));
            helper.assertTrue(manager.findActiveWindow(player.getUUID(), "main_menu") == null, Component.literal("window state leaked at iteration " + i));
            helper.assertTrue(manager.ownerWindows(player.getUUID()).isEmpty(), Component.literal("owner window list leaked at iteration " + i));
            helper.assertTrue(manager.bindingSnapshots(player.getUUID()).isEmpty(), Component.literal("window binding leaked at iteration " + i));
        }

        for (int i = 0; i < 100; i++) {
            var opened = manager.createGroup(player, "menu_group", PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
            helper.assertTrue(opened.success(), Component.literal("group open failed at iteration " + i + ": " + opened.message()));
            helper.assertTrue(manager.findActiveGroup(player.getUUID(), "menu_group") != null, Component.literal("active group missing at iteration " + i));

            var closed = manager.removeGroup(player.getUUID(), "menu_group");
            helper.assertTrue(closed.success(), Component.literal("group close failed at iteration " + i + ": " + closed.message()));
            helper.assertTrue(manager.findActiveGroup(player.getUUID(), "menu_group") == null, Component.literal("group state leaked at iteration " + i));
            helper.assertTrue(manager.ownerWindows(player.getUUID()).isEmpty(), Component.literal("group current window leaked at iteration " + i));
            helper.assertTrue(manager.bindingSnapshots(player.getUUID()).isEmpty(), Component.literal("group binding leaked at iteration " + i));
        }

        helper.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
