package com.interactivedisplay.core.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.layout.LayoutMode;
import com.interactivedisplay.core.positioning.CoordinateTransformer;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.positioning.WindowOffset;
import java.util.List;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

class WindowPlacementControllerTest {
    @Test
    void startAndStopShouldTrackStandaloneWindow() {
        WindowPlacementController controller = new WindowPlacementController(new CoordinateTransformer());
        UUID owner = UUID.randomUUID();
        WindowNavigationContext context = new WindowNavigationContext("main_menu", null, PositionMode.FIXED, Vec3d.ZERO, 0.0f, 0.0f);

        controller.start(owner, context);
        assertTrue(controller.isTracking(owner, context));

        controller.stop(owner);
        assertFalse(controller.isTracking(owner, context));
    }

    @Test
    void commitStandalonePlayerFixedShouldUseCurrentViewRotation() {
        WindowPlacementController controller = new WindowPlacementController(new CoordinateTransformer());
        WindowDefinition definition = new WindowDefinition("main_menu", new ComponentSize(1.0f, 1.0f), new WindowOffset(2.0f, 0.0f, 0.0f), LayoutMode.ABSOLUTE, List.of());
        WindowInstance instance = new WindowInstance(UUID.randomUUID(), "main_menu", World.OVERWORLD, PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f, UUID.randomUUID(), Vec3d.ZERO, 0.0f, 0.0f, Vec3d.ZERO, 0.0f, 0.0f, 0L);

        WindowPlacementController.StandaloneCommit commit = controller.commitStandalone(instance, definition, new Vec3d(0.0, 64.0, 0.0), 35.0f, -15.0f);

        assertEquals(35.0f, commit.fixedYaw(), 0.0001f);
        assertEquals(-15.0f, commit.fixedPitch(), 0.0001f);
    }

    @Test
    void commitGroupPlayerFixedShouldSubtractCurrentOrbitFromBaseRotation() {
        WindowPlacementController controller = new WindowPlacementController(new CoordinateTransformer());
        WindowDefinition definition = new WindowDefinition("settings", new ComponentSize(1.0f, 1.0f), new WindowOffset(2.0f, 0.0f, 0.5f), LayoutMode.ABSOLUTE, List.of());
        WindowGroupDefinition groupDefinition = new WindowGroupDefinition(
                "menu_group",
                "settings",
                PositionMode.PLAYER_FIXED,
                List.of(new WindowGroupEntry("settings", WindowOffset.zero(), new WindowOrbit(25.0f, -5.0f)))
        );
        WindowGroupInstance groupInstance = new WindowGroupInstance(
                UUID.randomUUID(),
                "menu_group",
                null,
                0.0f,
                0.0f,
                PositionMode.PLAYER_FIXED,
                "settings",
                new WindowInstance(UUID.randomUUID(), "settings", "menu_group", "settings", World.OVERWORLD, PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f, UUID.randomUUID(), Vec3d.ZERO, 0.0f, 0.0f, Vec3d.ZERO, 0.0f, 0.0f, 0L)
        );

        WindowPlacementController.GroupCommit commit = controller.commitGroup(groupInstance, groupDefinition, definition, new Vec3d(0.0, 64.0, 0.0), 60.0f, -15.0f);

        assertEquals(35.0f, commit.baseYaw(), 0.0001f);
        assertEquals(-10.0f, commit.basePitch(), 0.0001f);
    }
}
