package com.interactivedisplay.api.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.interactivedisplay.api.window.WindowPositionMode;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class GroupOpenOptionsTest {
    @Test
    void fixedModeShouldRequireAnchor() {
        assertThrows(
                NullPointerException.class,
                () -> new GroupOpenOptions(WindowPositionMode.FIXED, null, 0.0f, 0.0f)
        );
    }

    @Test
    void factoriesShouldPreserveModeAndAnchor() {
        assertEquals(WindowPositionMode.PLAYER_VIEW, GroupOpenOptions.playerView().mode());
        assertEquals(WindowPositionMode.PLAYER_FIXED, GroupOpenOptions.playerFixed().mode());

        Vec3 anchor = new Vec3(1.0, 2.0, 3.0);
        GroupOpenOptions fixed = GroupOpenOptions.fixed(anchor, 25.0f, -10.0f);
        assertEquals(WindowPositionMode.FIXED, fixed.mode());
        assertEquals(anchor, fixed.fixedAnchor());
        assertEquals(25.0f, fixed.fixedYaw());
        assertEquals(-10.0f, fixed.fixedPitch());
    }
}
