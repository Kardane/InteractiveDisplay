package com.interactivedisplay.gametest;

import com.interactivedisplay.api.InteractiveDisplayApi;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ClickType;
import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.interaction.ClickHandler;
import com.interactivedisplay.core.interaction.UiHitResult;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.window.WindowComponentRuntime;
import com.interactivedisplay.core.window.WindowNavigationContext;
import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.debug.DebugRecorder;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class InteractiveDisplayCallbackExceptionGameTest implements CustomTestMethodInvoker {
    @SuppressWarnings("removal")
    @GameTest
    public void publicCallbackExceptionShouldBeContainedByRealClickPipeline(GameTestHelper helper) {
        var manager = com.interactivedisplay.InteractiveDisplay.instance().windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ResourceLocation callbackId = ResourceLocation.fromNamespaceAndPath("qa", "gametest_throw_callback");

        var registration = InteractiveDisplayApi.get().callbacks().register(callbackId, context -> {
            throw new IllegalStateException("expected callback failure");
        });
        helper.assertTrue(registration.success(), Component.literal("QA callback registration failed: " + registration.message()));

        ComponentAction action = ComponentAction.callback(callbackId.toString());
        ButtonComponentDefinition button = new ButtonComponentDefinition(
                "throwing_callback",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(1.0f, 0.35f),
                true,
                1.0f,
                "Throw",
                0.5f,
                "#CC222222",
                "#EE444444",
                null,
                ClickType.RIGHT,
                action
        );
        WindowComponentRuntime runtime = new WindowComponentRuntime(
                Level.OVERWORLD,
                button,
                new Vector3f(),
                null,
                null
        );
        WindowNavigationContext navigation = new WindowNavigationContext(
                "main_menu",
                null,
                PositionMode.PLAYER_FIXED,
                null,
                0.0f,
                0.0f
        );
        UiHitResult hit = new UiHitResult(
                "main_menu",
                navigation,
                "throwing_callback",
                runtime,
                action,
                Vec3.ZERO,
                1.0D
        );

        DebugRecorder recorder = new DebugRecorder(20);
        var result = new ClickHandler(manager, recorder)
                .handle(player.getUUID(), player.getGameProfile().getName(), hit);

        helper.assertFalse(result.consumed(), Component.literal("throwing callback was incorrectly reported as consumed"));
        helper.assertTrue(result.reasonCode() == DebugReason.ACTION_EXECUTION_FAILED,
                Component.literal("throwing callback returned wrong reason: " + result.reasonCode()));
        helper.assertTrue(callbackId.toString().equals(result.targetWindowId()),
                Component.literal("throwing callback result lost callback target id"));
        helper.assertTrue(recorder.recentFailureCount() > 0,
                Component.literal("throwing callback did not record a click failure"));
        helper.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
