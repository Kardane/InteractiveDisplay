package com.interactivedisplay.api.window;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class WindowOpenOptionsTest {
    @Test
    void fixedModeShouldRequireAnchor() {
        assertThrows(NullPointerException.class, () -> WindowOpenOptions.fixed(null, 0.0f, 0.0f));
        assertThrows(NullPointerException.class, () -> new WindowOpenOptions(WindowPositionMode.FIXED, null, 0.0f, 0.0f));
    }

    @Test
    void playerAttachedModesShouldNotRequireFixedAnchor() {
        assertDoesNotThrow(WindowOpenOptions::playerView);
        assertDoesNotThrow(WindowOpenOptions::playerFixed);
        assertDoesNotThrow(() -> WindowOpenOptions.fixed(Vec3.ZERO, 0.0f, 0.0f));
    }
}
