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
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class InteractiveDisplayHoverScaleGameTest implements CustomTestMethodInvoker {
    @SuppressWarnings("removal")
    @GameTest
    public void hoverShouldScaleWithoutTranslationDriftAndRestoreAcrossRepeatedEnterExit(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var manager = InteractiveDisplay.instance().windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setYRot(0.0f);
        player.setXRot(0.0f);
        player.setYHeadRot(0.0f);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(InteractiveDisplayItems.POINTER));

        String suffix = UUID.randomUUID().toString().replace("-", "");
        String scaledId = "qa:hover_scaled_" + suffix;
        String unitId = "qa:hover_unit_" + suffix;
        Vec3 anchor = player.getEyePosition().add(0.0, 0.0, 2.0);

        try {
            helper.assertTrue(manager.registerProgrammaticWindow(definition(scaledId, 1.5f)),
                    Component.literal("failed to register hover-scale fixture"));
            var opened = manager.createWindow(player, scaledId, PositionMode.FIXED, anchor, 0.0f, 0.0f);
            helper.assertTrue(opened.success(), Component.literal("failed to open hover-scale fixture: " + opened.message()));

            var instance = manager.findActiveWindow(player.getUUID(), scaledId);
            var runtime = instance == null ? null : instance.runtime("target");
            helper.assertTrue(runtime != null && runtime.displayElement() instanceof TextDisplayElement,
                    Component.literal("hover-scale fixture runtime missing"));
            TextDisplayElement element = (TextDisplayElement) runtime.displayElement();
            Vector3f baseScale = runtime.baseScale();
            Vector3f baseTranslation = new Vector3f(element.getTranslation());

            helper.assertTrue(manager.findUiHit(player) != null,
                    Component.literal("hover-scale fixture is not raycastable"));
            manager.tick();
            helper.assertTrue(runtime.hovered(), Component.literal("hover enter was not detected"));
            assertVector(helper, element.getScale(), new Vector3f(baseScale).mul(1.5f), "hover enter scale");
            assertVector(helper, element.getTranslation(), baseTranslation, "hover enter translation drift");

            player.setYRot(180.0f);
            player.setYHeadRot(180.0f);
            manager.tick();
            helper.assertTrue(!runtime.hovered(), Component.literal("hover exit was not detected"));
            assertVector(helper, element.getScale(), baseScale, "hover exit scale restore");
            assertVector(helper, element.getTranslation(), baseTranslation, "hover exit translation drift");

            player.setYRot(0.0f);
            player.setYHeadRot(0.0f);
            manager.tick();
            helper.assertTrue(runtime.hovered(), Component.literal("repeated hover enter was not detected"));
            assertVector(helper, element.getScale(), new Vector3f(baseScale).mul(1.5f), "repeated hover scale");
            assertVector(helper, element.getTranslation(), baseTranslation, "repeated hover translation drift");

            helper.assertTrue(manager.removeWindow(player.getUUID(), scaledId).success(),
                    Component.literal("hover-scale fixture cleanup failed"));
            VirtualWindowHolder.destroyAllPending(server);

            helper.assertTrue(manager.registerProgrammaticWindow(definition(unitId, 1.0f)),
                    Component.literal("failed to register hoverScale=1 fixture"));
            var unitOpened = manager.createWindow(player, unitId, PositionMode.FIXED, anchor, 0.0f, 0.0f);
            helper.assertTrue(unitOpened.success(), Component.literal("failed to open hoverScale=1 fixture"));
            var unitRuntime = manager.findActiveWindow(player.getUUID(), unitId).runtime("target");
            TextDisplayElement unitElement = (TextDisplayElement) unitRuntime.displayElement();
            Vector3f unitBaseScale = unitRuntime.baseScale();
            manager.tick();
            helper.assertTrue(unitRuntime.hovered(), Component.literal("hoverScale=1 fixture did not enter hover"));
            assertVector(helper, unitElement.getScale(), unitBaseScale, "hoverScale=1 changed scale");
        } finally {
            manager.removeWindow(player.getUUID(), scaledId);
            manager.removeWindow(player.getUUID(), unitId);
            VirtualWindowHolder.destroyAllPending(server);
        }

        helper.succeed();
    }

    private static WindowDefinition definition(String id, float hoverScale) {
        ButtonComponentDefinition button = new ButtonComponentDefinition(
                "target",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(4.0f, 4.0f),
                true,
                1.0f,
                "Hover",
                1.0f,
                "#AA222222",
                "#CC444444",
                null,
                ClickType.BOTH,
                ComponentAction.closeWindow(),
                hoverScale
        );
        return new WindowDefinition(
                id,
                new ComponentSize(4.0f, 4.0f),
                WindowOffset.zero(),
                LayoutMode.ABSOLUTE,
                List.of(button)
        );
    }

    private static void assertVector(GameTestHelper helper, Vector3f actual, Vector3f expected, String label) {
        float epsilon = 0.0001f;
        helper.assertTrue(Math.abs(actual.x - expected.x) <= epsilon
                        && Math.abs(actual.y - expected.y) <= epsilon
                        && Math.abs(actual.z - expected.z) <= epsilon,
                Component.literal(label + " expected=" + expected + " actual=" + actual));
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
