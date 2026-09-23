package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ClickType;
import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.layout.LayoutMode;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.positioning.WindowOffset;
import com.interactivedisplay.core.window.WindowDefinition;
import com.interactivedisplay.core.window.WindowNavigationContext;
import com.interactivedisplay.entity.VirtualWindowHolder;
import com.interactivedisplay.item.InteractiveDisplayItems;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

public final class InteractiveDisplayClickDedupGameTest implements CustomTestMethodInvoker {
    @SuppressWarnings("removal")
    @GameTest
    public void shiftRightClickOnPanelShouldStartPlacementTracking(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var app = InteractiveDisplay.instance();
        var manager = app.windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        String windowId = "qa:panel_placement_" + UUID.randomUUID().toString().replace("-", "");

        PanelComponentDefinition background = new PanelComponentDefinition(
                "background",
                new ComponentPosition(0.0f, -1.0f, 0.0f),
                new ComponentSize(3.0f, 2.0f),
                true,
                1.0f,
                "#88000000",
                0.0f,
                LayoutMode.ABSOLUTE,
                List.of()
        );
        ButtonComponentDefinition overlappingButton = new ButtonComponentDefinition(
                "overlapping_button",
                new ComponentPosition(0.0f, -0.1f, 0.04f),
                new ComponentSize(1.0f, 0.4f),
                true,
                1.0f,
                "Close",
                1.0f,
                "#AA222222",
                "#CC444444",
                null,
                ClickType.BOTH,
                ComponentAction.closeWindow()
        );
        WindowDefinition definition = new WindowDefinition(
                windowId,
                new ComponentSize(3.0f, 2.0f),
                new WindowOffset(2.0f, 0.0f, 0.0f),
                LayoutMode.ABSOLUTE,
                List.of(background, overlappingButton)
        );

        helper.assertTrue(manager.registerProgrammaticWindow(definition),
                Component.literal("failed to register panel placement fixture"));
        player.setYRot(0.0f);
        player.setXRot(0.0f);
        player.setYHeadRot(0.0f);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(InteractiveDisplayItems.POINTER));

        var opened = manager.createWindow(player, windowId, PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
        helper.assertTrue(opened.success(), Component.literal("failed to open panel placement fixture: " + opened.message()));
        helper.assertTrue(manager.findUiHit(player) != null,
                Component.literal("overlapping button is not raycastable"));
        helper.assertTrue(manager.findPlacementSurfaceHit(player) != null,
                Component.literal("background panel is not raycastable as a placement surface"));
        helper.assertTrue(app.consumeUiRightClick(player),
                Component.literal("shift-right-click over button was not consumed as placement"));
        helper.assertTrue(manager.findActiveWindow(player.getUUID(), windowId) != null,
                Component.literal("shift-right-click activated the overlapping close button"));

        player.setYRot(30.0f);
        player.setYHeadRot(30.0f);
        player.setXRot(90.0f);
        manager.tick();
        var moved = manager.findActiveWindow(player.getUUID(), windowId);
        helper.assertTrue(moved != null && Math.abs(moved.currentYaw() - 30.0f) < 0.001f,
                Component.literal("placement tracking did not follow player yaw after background gesture"));
        helper.assertTrue(Math.abs(moved.currentPitch() - 60.0f) < 0.001f,
                Component.literal("placement tracking did not clamp player pitch to 60 degrees"));

        var originalRuntime = moved.runtime("overlapping_button");
        var commit = manager.togglePlacementTracking(player.getUUID(), new WindowNavigationContext(
                windowId, null, PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f));
        helper.assertTrue(commit.success(), Component.literal("placement commit failed: " + commit.message()));
        helper.assertTrue(manager.findActiveWindow(player.getUUID(), windowId) == moved,
                Component.literal("placement commit recreated the window"));
        helper.assertTrue(moved.runtime("overlapping_button") == originalRuntime,
                Component.literal("placement commit recreated component runtime"));
        helper.assertTrue(Math.abs(moved.fixedYaw() - 30.0f) < 0.001f && Math.abs(moved.fixedPitch() - 60.0f) < 0.001f,
                Component.literal("placement commit did not keep the preview rotation"));

        manager.removeWindow(player.getUUID(), windowId);
        VirtualWindowHolder.destroyAllPending(server);
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest
    public void groupPlacementCommitShouldKeepCurrentWindowAndRuntimes(GameTestHelper helper) {
        var manager = InteractiveDisplay.instance().windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setYRot(0.0f);
        player.setXRot(0.0f);

        var opened = manager.createGroup(player, "menu_group", PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
        helper.assertTrue(opened.success(), Component.literal("sample group failed to open: " + opened.message()));
        var group = manager.findActiveGroup(player.getUUID(), "menu_group");
        var window = group.currentWindow();
        var runtime = window.runtime("title");
        var context = new WindowNavigationContext(window.windowId(), group.groupId(), PositionMode.PLAYER_FIXED,
                group.baseAnchor(), group.baseYaw(), group.basePitch());

        helper.assertTrue(manager.togglePlacementTracking(player.getUUID(), context).success(),
                Component.literal("group placement tracking did not start"));
        player.setYRot(25.0f);
        player.setXRot(15.0f);
        helper.assertTrue(manager.togglePlacementTracking(player.getUUID(), context).success(),
                Component.literal("group placement tracking did not commit"));
        helper.assertTrue(manager.findActiveGroup(player.getUUID(), "menu_group") == group,
                Component.literal("placement commit recreated the group"));
        helper.assertTrue(group.currentWindow() == window && window.runtime("title") == runtime,
                Component.literal("placement commit recreated the group window or runtime"));

        manager.removeGroup(player.getUUID(), "menu_group");
        VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest
    public void sameTickRuntimeClicksShouldInvokeActionOnceAndNextTickShouldInvokeAgain(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var app = InteractiveDisplay.instance();
        var manager = app.windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String windowId = "qa:click_dedup_" + suffix;
        String callbackId = "qa:click_dedup_callback_" + suffix;
        AtomicInteger callbackCalls = new AtomicInteger();

        InteractiveDisplay.callbackRegistry().register(callbackId, (clickedPlayer, clickedWindowId, componentId) -> {
            if (!clickedPlayer.getUUID().equals(player.getUUID())) {
                throw new AssertionError("dedup callback received wrong player");
            }
            if (!windowId.equals(clickedWindowId)) {
                throw new AssertionError("dedup callback received wrong window: " + clickedWindowId);
            }
            if (!"target".equals(componentId)) {
                throw new AssertionError("dedup callback received wrong component: " + componentId);
            }
            callbackCalls.incrementAndGet();
        });

        ButtonComponentDefinition button = new ButtonComponentDefinition(
                "target",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(4.0f, 4.0f),
                true,
                1.0f,
                "QA",
                1.0f,
                "#AA222222",
                "#CC444444",
                null,
                ClickType.BOTH,
                ComponentAction.callback(callbackId)
        );
        WindowDefinition definition = new WindowDefinition(
                windowId,
                new ComponentSize(4.0f, 4.0f),
                new WindowOffset(2.0f, 0.0f, 0.0f),
                LayoutMode.ABSOLUTE,
                List.of(button)
        );

        helper.assertTrue(manager.registerProgrammaticWindow(definition),
                Component.literal("failed to register click dedup fixture"));
        player.setYRot(0.0f);
        player.setXRot(0.0f);
        player.setYHeadRot(0.0f);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(InteractiveDisplayItems.POINTER));

        var opened = manager.createWindow(player, windowId, PositionMode.PLAYER_VIEW, null, 0.0f, 0.0f);
        helper.assertTrue(opened.success(), Component.literal("failed to open click dedup fixture: " + opened.message()));
        helper.assertTrue(manager.findUiHit(player) != null,
                Component.literal("click dedup fixture is not raycastable from player view"));

        boolean first = app.consumeUiRightClick(player);
        boolean duplicate = app.consumeUiRightClick(player);
        helper.assertTrue(first, Component.literal("first runtime click was not consumed"));
        helper.assertTrue(duplicate, Component.literal("same-tick duplicate should report already-consumed input"));
        helper.assertTrue(callbackCalls.get() == 1,
                Component.literal("same-tick duplicate executed action more than once: calls=" + callbackCalls.get()));

        helper.runAfterDelay(2L, () -> {
            try {
                helper.assertTrue(manager.findUiHit(player) != null,
                        Component.literal("click dedup fixture lost hit target on next tick"));
                boolean nextTick = app.consumeUiRightClick(player);
                helper.assertTrue(nextTick, Component.literal("next-tick runtime click was not consumed"));
                helper.assertTrue(callbackCalls.get() == 2,
                        Component.literal("next-tick click did not execute exactly once: calls=" + callbackCalls.get()));
                helper.succeed();
            } finally {
                manager.removeWindow(player.getUUID(), windowId);
                VirtualWindowHolder.destroyAllPending(server);
            }
        });
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
