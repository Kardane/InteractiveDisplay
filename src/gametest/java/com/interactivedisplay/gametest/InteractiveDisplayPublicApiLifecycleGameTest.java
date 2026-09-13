package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.api.InteractiveDisplayApi;
import com.interactivedisplay.api.event.EventApi;
import com.interactivedisplay.api.group.GroupOpenOptions;
import com.interactivedisplay.api.window.WindowOpenOptions;
import com.interactivedisplay.api.window.WindowPositionMode;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class InteractiveDisplayPublicApiLifecycleGameTest implements CustomTestMethodInvoker {
    @SuppressWarnings("removal")
    @GameTest
    public void windowAndGroupApisShouldEmitOwnerScopedLifecycleEventsForAllModes(GameTestHelper helper) {
        InteractiveDisplayApi api = InteractiveDisplayApi.get();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ResourceLocation main = ResourceLocation.fromNamespaceAndPath(InteractiveDisplay.MOD_ID, "main_menu");
        ResourceLocation groupId = ResourceLocation.fromNamespaceAndPath(InteractiveDisplay.MOD_ID, "menu_group");

        List<EventApi.WindowEvent> opened = new ArrayList<>();
        List<EventApi.WindowEvent> closed = new ArrayList<>();
        EventApi.Subscription openedSubscription = api.events().onWindowOpened(event -> {
            if (event.ownerId().equals(player.getUUID())) {
                opened.add(event);
            }
        });
        EventApi.Subscription closedSubscription = api.events().onWindowClosed(event -> {
            if (event.ownerId().equals(player.getUUID())) {
                closed.add(event);
            }
        });

        try {
            assertWindowLifecycle(helper, api, player, main, WindowOpenOptions.playerFixed(), WindowPositionMode.PLAYER_FIXED, opened, closed);
            assertWindowLifecycle(helper, api, player, main, WindowOpenOptions.playerView(), WindowPositionMode.PLAYER_VIEW, opened, closed);
            assertWindowLifecycle(helper, api, player, main, WindowOpenOptions.fixed(new Vec3(12.0, 72.0, -4.0), 45.0f, -15.0f), WindowPositionMode.FIXED, opened, closed);

            assertGroupLifecycle(helper, api, player, groupId, GroupOpenOptions.playerFixed(), WindowPositionMode.PLAYER_FIXED, opened, closed);
            assertGroupLifecycle(helper, api, player, groupId, GroupOpenOptions.playerView(), WindowPositionMode.PLAYER_VIEW, opened, closed);
            assertGroupLifecycle(helper, api, player, groupId, GroupOpenOptions.fixed(new Vec3(-8.0, 68.0, 9.0), -30.0f, 10.0f), WindowPositionMode.FIXED, opened, closed);
        } finally {
            api.windows().closeAll(player);
            if (api.groups().isOpen(player, groupId)) {
                api.groups().close(player, groupId);
            }
            openedSubscription.close();
            closedSubscription.close();
        }

        helper.succeed();
    }

    private static void assertWindowLifecycle(
            GameTestHelper helper,
            InteractiveDisplayApi api,
            ServerPlayer player,
            ResourceLocation windowId,
            WindowOpenOptions options,
            WindowPositionMode expectedMode,
            List<EventApi.WindowEvent> opened,
            List<EventApi.WindowEvent> closed
    ) {
        int openedBefore = opened.size();
        int closedBefore = closed.size();

        var openResult = api.windows().open(player, windowId, options);
        helper.assertTrue(openResult.success(), Component.literal("window open failed for " + expectedMode + ": " + openResult.message()));
        helper.assertTrue(opened.size() == openedBefore + 1, Component.literal("window open event count mismatch for " + expectedMode));
        EventApi.WindowEvent openEvent = opened.get(opened.size() - 1);
        helper.assertTrue(windowId.equals(openEvent.windowId()), Component.literal("window OPENED id mismatch for " + expectedMode));
        helper.assertTrue(openEvent.mode() == expectedMode, Component.literal("window OPENED mode mismatch for " + expectedMode));

        var handle = api.windows().find(player, windowId).orElseThrow();
        helper.assertTrue(handle.mode().orElseThrow() == expectedMode, Component.literal("window handle mode mismatch for " + expectedMode));
        var closeResult = api.windows().close(player, windowId);
        helper.assertTrue(closeResult.success(), Component.literal("WindowApi.close failed for " + expectedMode + ": " + closeResult.message()));
        helper.assertTrue(closed.size() == closedBefore + 1, Component.literal("WindowApi.close event count mismatch for " + expectedMode));
        EventApi.WindowEvent closeEvent = closed.get(closed.size() - 1);
        helper.assertTrue(windowId.equals(closeEvent.windowId()), Component.literal("window CLOSED id mismatch for " + expectedMode));
        helper.assertTrue(closeEvent.mode() == expectedMode, Component.literal("window CLOSED mode mismatch for " + expectedMode));
        helper.assertFalse(api.windows().isOpen(player, windowId), Component.literal("window remained open after API close for " + expectedMode));
    }

    private static void assertGroupLifecycle(
            GameTestHelper helper,
            InteractiveDisplayApi api,
            ServerPlayer player,
            ResourceLocation groupId,
            GroupOpenOptions options,
            WindowPositionMode expectedMode,
            List<EventApi.WindowEvent> opened,
            List<EventApi.WindowEvent> closed
    ) {
        int openedBefore = opened.size();
        int closedBefore = closed.size();

        var openResult = api.groups().open(player, groupId, options);
        helper.assertTrue(openResult.success(), Component.literal("group open failed for " + expectedMode + ": " + openResult.message()));
        helper.assertTrue(api.groups().isOpen(player, groupId), Component.literal("GroupApi.isOpen false for " + expectedMode));
        var handle = api.groups().find(player, groupId).orElseThrow();
        helper.assertTrue(handle.mode().orElseThrow() == expectedMode, Component.literal("GroupHandle.mode mismatch for " + expectedMode));
        ResourceLocation currentWindowId = handle.currentWindowId().orElseThrow();
        helper.assertTrue(opened.size() == openedBefore + 1, Component.literal("group current-window open event count mismatch for " + expectedMode));
        EventApi.WindowEvent openEvent = opened.get(opened.size() - 1);
        helper.assertTrue(currentWindowId.equals(openEvent.windowId()), Component.literal("group OPENED window id mismatch for " + expectedMode));
        helper.assertTrue(openEvent.mode() == expectedMode, Component.literal("group OPENED mode mismatch for " + expectedMode));

        var closeResult = api.groups().close(player, groupId);
        helper.assertTrue(closeResult.success(), Component.literal("GroupApi.close failed for " + expectedMode + ": " + closeResult.message()));
        helper.assertTrue(closed.size() == closedBefore + 1, Component.literal("group close event count mismatch for " + expectedMode));
        EventApi.WindowEvent closeEvent = closed.get(closed.size() - 1);
        helper.assertTrue(currentWindowId.equals(closeEvent.windowId()), Component.literal("group CLOSED window id mismatch for " + expectedMode));
        helper.assertTrue(closeEvent.mode() == expectedMode, Component.literal("group CLOSED mode mismatch for " + expectedMode));
        helper.assertFalse(api.groups().isOpen(player, groupId), Component.literal("group remained open after API close for " + expectedMode));
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
