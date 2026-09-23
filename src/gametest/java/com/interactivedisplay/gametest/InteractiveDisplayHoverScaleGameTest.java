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
import org.joml.Vector3fc;

public final class InteractiveDisplayHoverScaleGameTest implements CustomTestMethodInvoker {
    @SuppressWarnings("removal")
    @GameTest
    public void hoverShouldScaleAndRestoreWithoutTranslationDrift(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var manager = InteractiveDisplay.instance().windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setYRot(0.0f);
        player.setXRot(0.0f);
        player.setYHeadRot(0.0f);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(InteractiveDisplayItems.POINTER));

        String id = "qa:hover_scaled_" + UUID.randomUUID().toString().replace("-", "");
        Vec3 anchor = player.getEyePosition().add(0.0, 0.0, 2.0);

        try {
            helper.assertTrue(manager.registerProgrammaticWindow(definition(id, 1.5f)),
                    Component.literal("failed to register hover-scale fixture"));
            var opened = manager.createWindow(player, id, PositionMode.FIXED, anchor, 0.0f, 0.0f);
            helper.assertTrue(opened.success(), Component.literal("failed to open hover-scale fixture: " + opened.message()));

            var instance = manager.findActiveWindow(player.getUUID(), id);
            var runtime = instance == null ? null : instance.runtime("target");
            helper.assertTrue(runtime != null && runtime.displayElement() instanceof TextDisplayElement,
                    Component.literal("hover-scale fixture runtime missing"));
            TextDisplayElement element = (TextDisplayElement) runtime.displayElement();
            helper.assertTrue(runtime.backgroundElement() instanceof TextDisplayElement,
                    Component.literal("hover-scale fixture background runtime missing"));
            TextDisplayElement background = (TextDisplayElement) runtime.backgroundElement();
            helper.assertTrue(runtime.renderedComponent().displayElements().size() == 2,
                    Component.literal("button render bundle should contain two display elements"));
            helper.assertTrue(runtime.renderedComponent().primaryDisplay() == element,
                    Component.literal("button render bundle primary display mismatch"));
            helper.assertTrue(runtime.renderedComponent().backgroundDisplay() == background,
                    Component.literal("button render bundle background display mismatch"));
            Vector3f baseScale = runtime.baseScale();
            Vector3f backgroundBaseScale = runtime.baseScale(background);
            Vector3f baseTranslation = new Vector3f(element.getTranslation());
            Vector3f backgroundBaseTranslation = new Vector3f(background.getTranslation());

            helper.assertTrue(manager.findUiHit(player) != null,
                    Component.literal("hover-scale fixture is not raycastable"));
            manager.tick();
            helper.assertTrue(runtime.hovered(), Component.literal("hover enter was not detected"));
            assertVector(helper, element.getScale(), new Vector3f(baseScale).mul(1.5f), "hover enter label scale");
            assertVector(helper, background.getScale(), new Vector3f(backgroundBaseScale).mul(1.5f), "hover enter background scale");
            assertVector(helper, element.getTranslation(), baseTranslation, "hover enter label translation drift");
            assertVector(helper, background.getTranslation(), backgroundBaseTranslation, "hover enter background translation drift");

            player.setYRot(180.0f);
            player.setYHeadRot(180.0f);
            manager.tick();
            helper.assertTrue(!runtime.hovered(), Component.literal("hover exit was not detected"));
            assertVector(helper, element.getScale(), baseScale, "hover exit label scale restore");
            assertVector(helper, background.getScale(), backgroundBaseScale, "hover exit background scale restore");
            assertVector(helper, element.getTranslation(), baseTranslation, "hover exit label translation drift");
            assertVector(helper, background.getTranslation(), backgroundBaseTranslation, "hover exit background translation drift");
        } finally {
            manager.removeWindow(player.getUUID(), id);
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

    private static void assertVector(GameTestHelper helper, Vector3fc actual, Vector3fc expected, String label) {
        float epsilon = 0.0001f;
        helper.assertTrue(Math.abs(actual.x() - expected.x()) <= epsilon
                        && Math.abs(actual.y() - expected.y()) <= epsilon
                        && Math.abs(actual.z() - expected.z()) <= epsilon,
                Component.literal(label + " expected=" + expected + " actual=" + actual));
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
