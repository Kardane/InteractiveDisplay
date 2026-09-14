package com.interactivedisplay.gametest;

import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.window.WindowTransition;
import com.interactivedisplay.core.window.WindowTransitionType;
import com.interactivedisplay.entity.VirtualWindowHolder;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class InteractiveDisplayTransitionGameTest implements CustomTestMethodInvoker {
    private static final float EPSILON = 0.0001f;
    private static final float MIN_SCALE_FACTOR = 0.01f;
    private static final float SLIDE_DISTANCE = 0.35f;
    private static final int DURATION = 5;

    @SuppressWarnings("removal")
    @GameTest
    public void enterTransitionsShouldPrepareExpectedInitialTransformAndRestoreBase(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        verifyEnter(helper, player, WindowTransitionType.NONE);
        verifyEnter(helper, player, WindowTransitionType.SCALE);
        verifyEnter(helper, player, WindowTransitionType.SLIDE_UP);
        verifyEnter(helper, player, WindowTransitionType.SLIDE_DOWN);

        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest
    public void exitTransitionsShouldApplyExpectedTargetAndCleanUp(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        verifyExit(helper, player, WindowTransitionType.NONE);
        verifyExit(helper, player, WindowTransitionType.SCALE);
        verifyExit(helper, player, WindowTransitionType.SLIDE_UP);
        verifyExit(helper, player, WindowTransitionType.SLIDE_DOWN);

        helper.succeed();
    }

    private static void verifyEnter(GameTestHelper helper, ServerPlayer player, WindowTransitionType type) {
        Vector3f baseScale = new Vector3f(1.5f, 1.25f, 0.75f);
        Vector3f baseTranslation = new Vector3f(0.1f, 0.2f, -0.3f);
        VirtualWindowHolder holder = new VirtualWindowHolder(helper.getLevel(), player.position());
        TextDisplayElement element = element(baseScale, baseTranslation);

        holder.configure(PositionMode.PLAYER_FIXED, player);
        holder.addElement(element);
        holder.startWatching(player);
        holder.setTransition(new WindowTransition(DURATION, type, WindowTransitionType.NONE));

        switch (type) {
            case NONE -> {
                assertVector(helper, element.getScale(), baseScale, "NONE enter scale");
                assertVector(helper, element.getTranslation(), baseTranslation, "NONE enter translation");
            }
            case SCALE -> {
                assertVector(helper, element.getScale(), new Vector3f(baseScale).mul(MIN_SCALE_FACTOR), "SCALE enter initial scale");
                assertVector(helper, element.getTranslation(), baseTranslation, "SCALE enter translation");
            }
            case SLIDE_UP -> {
                assertVector(helper, element.getScale(), baseScale, "SLIDE_UP enter scale");
                assertVector(helper, element.getTranslation(), new Vector3f(baseTranslation).add(0.0f, -SLIDE_DISTANCE, 0.0f),
                        "SLIDE_UP enter initial translation");
            }
            case SLIDE_DOWN -> {
                assertVector(helper, element.getScale(), baseScale, "SLIDE_DOWN enter scale");
                assertVector(helper, element.getTranslation(), new Vector3f(baseTranslation).add(0.0f, SLIDE_DISTANCE, 0.0f),
                        "SLIDE_DOWN enter initial translation");
            }
        }

        holder.playEnterTransition();
        assertVector(helper, element.getScale(), baseScale, type + " enter restored scale");
        assertVector(helper, element.getTranslation(), baseTranslation, type + " enter restored translation");

        holder.destroy();
        helper.assertTrue(holder.entityCount() == 0,
                Component.literal(type + " enter-only holder did not destroy immediately"));
    }

    private static void verifyExit(GameTestHelper helper, ServerPlayer player, WindowTransitionType type) {
        Vector3f baseScale = new Vector3f(1.2f, 0.8f, 0.5f);
        Vector3f baseTranslation = new Vector3f(-0.1f, 0.25f, 0.4f);
        VirtualWindowHolder holder = new VirtualWindowHolder(helper.getLevel(), player.position());
        TextDisplayElement element = element(baseScale, baseTranslation);

        holder.configure(PositionMode.PLAYER_FIXED, player);
        holder.addElement(element);
        holder.setTransition(new WindowTransition(DURATION, WindowTransitionType.NONE, type));
        holder.startWatching(player);
        holder.tick();
        holder.destroy();

        if (type == WindowTransitionType.NONE) {
            helper.assertTrue(holder.entityCount() == 0,
                    Component.literal("NONE exit should destroy holder immediately"));
            return;
        }

        switch (type) {
            case SCALE -> {
                assertVector(helper, element.getScale(), new Vector3f(baseScale).mul(MIN_SCALE_FACTOR), "SCALE exit target scale");
                assertVector(helper, element.getTranslation(), baseTranslation, "SCALE exit translation");
            }
            case SLIDE_UP -> {
                assertVector(helper, element.getScale(), baseScale, "SLIDE_UP exit scale");
                assertVector(helper, element.getTranslation(), new Vector3f(baseTranslation).add(0.0f, SLIDE_DISTANCE, 0.0f),
                        "SLIDE_UP exit target translation");
            }
            case SLIDE_DOWN -> {
                assertVector(helper, element.getScale(), baseScale, "SLIDE_DOWN exit scale");
                assertVector(helper, element.getTranslation(), new Vector3f(baseTranslation).add(0.0f, -SLIDE_DISTANCE, 0.0f),
                        "SLIDE_DOWN exit target translation");
            }
            case NONE -> throw new AssertionError("unreachable NONE branch");
        }

        helper.assertTrue(holder.entityCount() > 0,
                Component.literal(type + " exit destroyed holder before transition cleanup"));
        VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
        helper.assertTrue(holder.entityCount() == 0,
                Component.literal(type + " exit holder retained entities after cleanup"));
    }

    private static TextDisplayElement element(Vector3f scale, Vector3f translation) {
        TextDisplayElement element = new TextDisplayElement();
        element.setScale(new Vector3f(scale));
        element.setTranslation(new Vector3f(translation));
        return element;
    }

    private static void assertVector(GameTestHelper helper, Vector3fc actual, Vector3fc expected, String label) {
        helper.assertTrue(Math.abs(actual.x() - expected.x()) <= EPSILON
                        && Math.abs(actual.y() - expected.y()) <= EPSILON
                        && Math.abs(actual.z() - expected.z()) <= EPSILON,
                Component.literal(label + " expected=" + expected + " actual=" + actual));
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
