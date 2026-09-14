package com.interactivedisplay.internal.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.api.window.WindowSpec;
import com.interactivedisplay.core.interaction.CallbackRegistry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CallbackApiRegistrationTest {
    @Test
    void duplicateCallbackRegistrationShouldFailWithoutReplacingOriginalBinding() {
        CallbackRegistry callbacks = new CallbackRegistry();
        InteractiveDisplayApiImpl api = new InteractiveDisplayApiImpl(callbacks);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("qa", "callback_collision");

        assertTrue(api.callbacks().register(id, context -> { }).success());
        var original = callbacks.find(id.toString()).orElseThrow();

        assertFalse(api.callbacks().register(id, context -> { }).success());
        var afterDuplicate = callbacks.find(id.toString()).orElseThrow();

        assertSame(original, afterDuplicate);
    }

    @Test
    void duplicatePublicWindowRegistrationShouldFailAndKeepOriginalIdRegistered() {
        InteractiveDisplayApiImpl api = new InteractiveDisplayApiImpl(new CallbackRegistry());
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("qa", "window_collision");
        WindowSpec original = WindowSpec.builder(id).size(2.0f, 1.0f).build();
        WindowSpec duplicate = WindowSpec.builder(id).size(4.0f, 2.0f).build();

        assertTrue(api.windows().register(original).success());
        assertFalse(api.windows().register(duplicate).success());
        assertTrue(api.windows().registeredIds().contains(id));
    }
}
