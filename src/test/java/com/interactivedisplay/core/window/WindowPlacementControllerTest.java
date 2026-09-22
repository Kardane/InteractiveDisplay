package com.interactivedisplay.core.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.layout.LayoutMode;
import com.interactivedisplay.core.positioning.CoordinateTransformer;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.positioning.WindowOffset;
import com.interactivedisplay.core.positioning.WindowPositionTracker;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class WindowPlacementControllerTest {
    @Test
    void startAndStopShouldTrackStandaloneWindow() {
        WindowPlacementController controller = new WindowPlacementController(new CoordinateTransformer());
        UUID owner = UUID.randomUUID();
        WindowNavigationContext context = new WindowNavigationContext("main_menu", null, PositionMode.FIXED, Vec3.ZERO, 0.0f, 0.0f);

        controller.start(owner, context);
        assertTrue(controller.isTracking(owner, context));

        controller.stop(owner);
        assertFalse(controller.isTracking(owner, context));
    }

    @Test
    void commitStandalonePlayerFixedShouldUseCurrentViewRotation() {
        WindowPlacementController controller = new WindowPlacementController(new CoordinateTransformer());
        WindowDefinition definition = new WindowDefinition("main_menu", new ComponentSize(1.0f, 1.0f), new WindowOffset(2.0f, 0.0f, 0.0f), LayoutMode.ABSOLUTE, List.of());
        WindowInstance instance = new WindowInstance(UUID.randomUUID(), "main_menu", Level.OVERWORLD, PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f, null, Vec3.ZERO, 0.0f, 0.0f, Vec3.ZERO, 0.0f, 0.0f, 0L);

        WindowPlacementController.StandaloneCommit commit = controller.commitStandalone(instance, definition, new Vec3(0.0, 64.0, 0.0), 35.0f, -15.0f);

        assertEquals(35.0f, commit.fixedYaw(), 0.0001f);
        assertEquals(-15.0f, commit.fixedPitch(), 0.0001f);
    }

    @Test
    void placementPreviewShouldClampStandalonePitchAndAnchor() {
        CoordinateTransformer transformer = new CoordinateTransformer();
        WindowPlacementController controller = new WindowPlacementController(transformer);
        WindowOffset offset = new WindowOffset(2.0f, 0.0f, 0.0f);
        WindowDefinition definition = new WindowDefinition("main_menu", new ComponentSize(1.0f, 1.0f), offset, LayoutMode.ABSOLUTE, List.of());
        WindowInstance playerFixed = new WindowInstance(UUID.randomUUID(), "main_menu", Level.OVERWORLD, PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f, null, Vec3.ZERO, 0.0f, 0.0f, Vec3.ZERO, 0.0f, 0.0f, 0L);
        WindowInstance fixed = new WindowInstance(UUID.randomUUID(), "main_menu", Level.OVERWORLD, PositionMode.FIXED, Vec3.ZERO, 0.0f, 0.0f, null, Vec3.ZERO, 0.0f, 0.0f, Vec3.ZERO, 0.0f, 0.0f, 0L);
        Vec3 eyePos = new Vec3(0.0, 64.0, 0.0);

        WindowPositionTracker.WindowTransformState playerFixedPreview = controller.previewStandalone(playerFixed, definition, eyePos, 0.0f, 90.0f);
        WindowPositionTracker.WindowTransformState fixedPreview = controller.previewStandalone(fixed, definition, eyePos, 0.0f, -90.0f);

        assertEquals(60.0f, playerFixedPreview.pitch(), 0.0001f);
        assertVec3Equals(transformer.toPlayerFixedAnchor(eyePos, offset, 0.0f, 60.0f), playerFixedPreview.anchor());
        assertEquals(0.0f, fixedPreview.pitch(), 0.0001f);
        assertVec3Equals(transformer.toPlayerFixedAnchor(eyePos, offset, 0.0f, -60.0f), fixedPreview.anchor());
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
                new WindowInstance(UUID.randomUUID(), "settings", "menu_group", "settings", Level.OVERWORLD, PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f, null, Vec3.ZERO, 0.0f, 0.0f, Vec3.ZERO, 0.0f, 0.0f, 0L)
        );

        WindowPlacementController.GroupCommit commit = controller.commitGroup(groupInstance, groupDefinition, definition, new Vec3(0.0, 64.0, 0.0), 60.0f, -15.0f);

        assertEquals(35.0f, commit.baseYaw(), 0.0001f);
        assertEquals(-10.0f, commit.basePitch(), 0.0001f);
    }

    private static void assertVec3Equals(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, 0.0001);
        assertEquals(expected.y, actual.y, 0.0001);
        assertEquals(expected.z, actual.z, 0.0001);
    }
}
