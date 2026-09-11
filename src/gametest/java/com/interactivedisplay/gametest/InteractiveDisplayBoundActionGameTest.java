package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.api.InteractiveDisplayApi;
import com.interactivedisplay.api.window.WindowSpec;
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
import com.interactivedisplay.debug.DebugRecorder;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class InteractiveDisplayBoundActionGameTest implements CustomTestMethodInvoker {
    @SuppressWarnings("removal")
    @GameTest
    public void boundActionShouldInvokeThroughRealClickPipelineWithImmutableParameters(GameTestHelper helper) {
        var api = InteractiveDisplayApi.get();
        var manager = InteractiveDisplay.instance().windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ResourceLocation actionId = ResourceLocation.fromNamespaceAndPath("qa", "gametest_bound_action");
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Map<String, String>> capturedParameters = new AtomicReference<>();
        AtomicReference<ServerPlayer> capturedPlayer = new AtomicReference<>();
        AtomicBoolean immutable = new AtomicBoolean(false);

        var registration = api.actions().register(actionId, context -> {
            calls.incrementAndGet();
            capturedParameters.set(context.parameters());
            capturedPlayer.set(context.player());
            try {
                context.parameters().put("mutated", "no");
            } catch (UnsupportedOperationException expected) {
                immutable.set(true);
            }
        });
        helper.assertTrue(registration.success(), Component.literal("bound action registration failed: " + registration.message()));

        WindowSpec.ButtonAction publicAction = api.actions().bind(actionId, Map.of(
                "product", "diamond_sword",
                "amount", "2"
        ));
        helper.assertTrue(publicAction instanceof WindowSpec.CallbackAction,
                Component.literal("ActionApi.bind did not return a callback-backed action"));
        ResourceLocation callbackId = ((WindowSpec.CallbackAction) publicAction).callbackId();

        ComponentAction internalAction = ComponentAction.callback(callbackId.toString());
        ButtonComponentDefinition button = new ButtonComponentDefinition(
                "buy",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(1.0f, 0.35f),
                true,
                1.0f,
                "Buy",
                0.5f,
                "#CC222222",
                "#EE444444",
                null,
                ClickType.RIGHT,
                internalAction
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
                "buy",
                runtime,
                internalAction,
                Vec3.ZERO,
                1.0D
        );

        var result = new ClickHandler(manager, new DebugRecorder(20))
                .handle(player.getUUID(), player.getGameProfile().getName(), hit);

        helper.assertTrue(result.consumed(), Component.literal("bound action click was not consumed: " + result.message()));
        helper.assertTrue(calls.get() == 1, Component.literal("bound action handler call count was " + calls.get()));
        helper.assertTrue(capturedPlayer.get() == player, Component.literal("bound action received wrong ServerPlayer"));
        helper.assertTrue(capturedParameters.get() != null
                        && "diamond_sword".equals(capturedParameters.get().get("product"))
                        && "2".equals(capturedParameters.get().get("amount")),
                Component.literal("bound action parameters were not delivered intact: " + capturedParameters.get()));
        helper.assertTrue(immutable.get(), Component.literal("bound action parameters map was mutable"));
        helper.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
