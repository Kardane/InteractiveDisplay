package com.interactivedisplay.core.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

class CommandActionSourceContextTest {
    @Test
    void sourceContextShouldKeepHitPositionRotationAndPermissionLevel() {
        CommandActionSourceContext context = CommandActionSourceContext.of(
                World.NETHER,
                new Vec3d(1.5, 64.0, -3.0),
                40.0f,
                -12.0f,
                3,
                "/say hi"
        );

        assertEquals(World.NETHER, context.worldKey());
        assertEquals(new Vec3d(1.5, 64.0, -3.0), context.position());
        assertEquals(40.0f, context.yaw(), 0.0001f);
        assertEquals(-12.0f, context.pitch(), 0.0001f);
        assertEquals(3, context.permissionLevel());
        assertEquals("say hi", context.normalizedCommand());
    }

    @Test
    void sourceContextShouldKeepNullPermissionLevelWhenNotRequested() {
        CommandActionSourceContext context = CommandActionSourceContext.of(
                World.OVERWORLD,
                Vec3d.ZERO,
                0.0f,
                0.0f,
                null,
                "trigger test"
        );

        assertEquals("trigger test", context.normalizedCommand());
        assertFalse(context.hasPermissionOverride());
    }
}
