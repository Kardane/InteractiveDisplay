package com.interactivedisplay.core.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TypewriterAnimationRuntimeTest {
    @Test
    void builtInTypewriterAndFadeAreRegistered() {
        assertTrue(AnimationRegistry.isRegistered("typewriter"));
        assertTrue(AnimationRegistry.isRegistered("interactivedisplay:typewriter"));
        assertTrue(AnimationRegistry.isRegistered("fade"));
    }

    @Test
    void animationIdsUseInteractiveDisplayNamespaceByDefault() {
        assertEquals("interactivedisplay:typewriter", AnimationRegistry.normalize("typewriter"));
        assertEquals("example:custom", AnimationRegistry.normalize("EXAMPLE:CUSTOM"));
    }

    @Test
    void invalidTypewriterStepConfigurationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new AnimationDefinition(
                "typewriter", 0, 0, 0, 1, AnimationInterpolation.LINEAR
        ));
        assertThrows(IllegalArgumentException.class, () -> new AnimationDefinition(
                "typewriter", 0, 0, 1, 0, AnimationInterpolation.LINEAR
        ));
    }
}
