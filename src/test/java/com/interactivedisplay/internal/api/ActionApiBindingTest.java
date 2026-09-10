package com.interactivedisplay.internal.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.api.window.WindowSpec;
import com.interactivedisplay.core.interaction.CallbackRegistry;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ActionApiBindingTest {
    @Test
    void shouldRegisterNamespacedActionAndBindParametersThroughCallbackPipeline() {
        CallbackRegistry callbacks = new CallbackRegistry();
        InteractiveDisplayApiImpl api = new InteractiveDisplayApiImpl(callbacks);
        ResourceLocation actionId = ResourceLocation.fromNamespaceAndPath("economy", "purchase");

        assertTrue(api.actions().register(actionId, context -> { }).success());
        assertFalse(api.actions().register(actionId, context -> { }).success());

        WindowSpec.ButtonAction action = api.actions().bind(actionId, Map.of("product", "diamond_sword"));
        WindowSpec.CallbackAction callbackAction = assertInstanceOf(WindowSpec.CallbackAction.class, action);

        assertEquals("interactivedisplay", callbackAction.callbackId().getNamespace());
        assertTrue(callbackAction.callbackId().getPath().startsWith("bound_action/"));
        assertTrue(callbacks.find(callbackAction.callbackId().toString()).isPresent());
    }

    @Test
    void boundActionShouldNotOverwriteExistingCallbackId() {
        CallbackRegistry callbacks = new CallbackRegistry();
        callbacks.register("interactivedisplay:bound_action/1", (player, windowId, componentId) -> { });
        InteractiveDisplayApiImpl api = new InteractiveDisplayApiImpl(callbacks);
        ResourceLocation actionId = ResourceLocation.fromNamespaceAndPath("economy", "purchase_collision");
        assertTrue(api.actions().register(actionId, context -> { }).success());

        WindowSpec.CallbackAction callbackAction = assertInstanceOf(
                WindowSpec.CallbackAction.class,
                api.actions().bind(actionId)
        );

        assertEquals("interactivedisplay:bound_action/2", callbackAction.callbackId().toString());
        assertTrue(callbacks.find("interactivedisplay:bound_action/1").isPresent());
        assertTrue(callbacks.find("interactivedisplay:bound_action/2").isPresent());
    }
}
