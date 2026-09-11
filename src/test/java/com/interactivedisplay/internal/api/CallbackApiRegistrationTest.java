package com.interactivedisplay.internal.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
