package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ClickType;
import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.layout.LayoutMode;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.positioning.WindowOffset;
import com.interactivedisplay.core.window.WindowDefinition;
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
