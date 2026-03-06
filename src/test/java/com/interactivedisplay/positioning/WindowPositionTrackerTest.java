package com.interactivedisplay.core.positioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.interactivedisplay.core.window.WindowInstance;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

class WindowPositionTrackerTest {
    private final WindowPositionTracker tracker = new WindowPositionTracker(new com.interactivedisplay.core.positioning.CoordinateTransformer());

    @Test
    void playerFixedDeadzoneShouldKeepAnchorAndRecalculateFacing() {
        WindowInstance instance = new WindowInstance(
                UUID.randomUUID(),
                "main_menu",
                World.OVERWORLD,
                PositionMode.PLAYER_FIXED,
                null,
                90.0f,
                0.0f,
                UUID.randomUUID(),
                new Vec3d(-2.0, 64.0, 0.0),
                90.0f,
                0.0f,
                new Vec3d(-2.0, 64.0, 0.0),
                90.0f,
                0.0f,
                0L
        );

        WindowPositionTracker.WindowTransformState raw = new WindowPositionTracker.WindowTransformState(
                new Vec3d(-1.99, 64.0, 0.0),
                90.0f,
                12.0f,
                new Vec3d(0.0, 64.0, 0.0)
        );

        WindowPositionTracker.WindowTransformState target = tracker.applyDeadzone(instance, raw);

        assertEquals(-2.0, target.anchor().x, 0.0001);
        assertEquals(90.0f, target.yaw(), 0.0001f);
        assertEquals(12.0f, target.pitch(), 0.0001f);
    }

    @Test
    void playerFixedSmoothingShouldOnlyInterpolateAnchor() {
        WindowInstance instance = new WindowInstance(
                UUID.randomUUID(),
                "main_menu",
                World.OVERWORLD,
                PositionMode.PLAYER_FIXED,
                null,
                0.0f,
                0.0f,
                UUID.randomUUID(),
                new Vec3d(1.0, 0.0, 0.0),
                45.0f,
                10.0f,
                Vec3d.ZERO,
                0.0f,
                0.0f,
                0L
        );

        WindowPositionTracker.WindowTransformState smoothed = tracker.smooth(instance, new WindowPositionTracker.WindowTransformState(new Vec3d(1.0, 0.0, 0.0), 45.0f, 10.0f));

        assertEquals(0.45, smoothed.anchor().x, 0.0001);
        assertEquals(45.0f, smoothed.yaw(), 0.0001f);
        assertEquals(10.0f, smoothed.pitch(), 0.0001f);
    }

    @Test
    void playerViewDeadzoneShouldKeepTargetRotationUntilThreshold() {
        WindowInstance instance = new WindowInstance(
                UUID.randomUUID(),
                "main_menu",
                World.OVERWORLD,
                PositionMode.PLAYER_VIEW,
                null,
                0.0f,
                0.0f,
                UUID.randomUUID(),
                new Vec3d(1.0, 64.0, 2.0),
                20.0f,
                5.0f,
                new Vec3d(1.0, 64.0, 2.0),
                20.0f,
                5.0f,
                0L
        );

        WindowPositionTracker.WindowTransformState target = tracker.applyDeadzone(instance, new WindowPositionTracker.WindowTransformState(new Vec3d(1.01, 64.0, 2.0), 20.9f, 5.5f));

        assertEquals(1.0, target.anchor().x, 0.0001);
        assertEquals(20.0f, target.yaw(), 0.0001f);
        assertEquals(5.0f, target.pitch(), 0.0001f);
    }

    @Test
    void playerViewSmoothingShouldInterpolatePositionAndRotationSeparately() {
        WindowInstance instance = new WindowInstance(
                UUID.randomUUID(),
                "main_menu",
                World.OVERWORLD,
                PositionMode.PLAYER_VIEW,
                null,
                0.0f,
                0.0f,
                UUID.randomUUID(),
                new Vec3d(1.0, 0.5, -0.5),
                90.0f,
                30.0f,
                Vec3d.ZERO,
                0.0f,
                0.0f,
                0L
        );

        WindowPositionTracker.WindowTransformState smoothed = tracker.smooth(instance, new WindowPositionTracker.WindowTransformState(new Vec3d(1.0, 0.5, -0.5), 90.0f, 30.0f));

        assertEquals(0.35, smoothed.anchor().x, 0.0001);
        assertEquals(0.175, smoothed.anchor().y, 0.0001);
        assertEquals(-0.175, smoothed.anchor().z, 0.0001);
        assertEquals(27.0f, smoothed.yaw(), 0.0001f);
        assertEquals(9.0f, smoothed.pitch(), 0.0001f);
    }

    @Test
    void playerViewShouldSkipRotationUpdateBelowThreshold() {
        WindowInstance instance = new WindowInstance(
                UUID.randomUUID(),
                "main_menu",
                World.OVERWORLD,
                PositionMode.PLAYER_VIEW,
                null,
                0.0f,
                0.0f,
                UUID.randomUUID(),
                Vec3d.ZERO,
                0.0f,
                0.0f,
                Vec3d.ZERO,
                0.0f,
                0.0f,
                0L
        );

        boolean shouldUpdate = tracker.shouldUpdate(instance, new WindowPositionTracker.WindowTransformState(Vec3d.ZERO, 0.2f, 0.1f), 2L);

        assertFalse(shouldUpdate);
    }

    @Test
    void playerFixedResolveShouldKeepConfiguredPitch() {
        WindowPositionTracker.WindowTransformState state = tracker.resolve(
                PositionMode.PLAYER_FIXED,
                new WindowOffset(2.0f, 0.0f, 0.0f),
                new Vec3d(0.0, 64.0, 0.0),
                new Vec3d(0.0, 0.0, 1.0),
                0.0f,
                0.0f,
                null,
                35.0f,
                -20.0f
        );

        assertEquals(35.0f, state.yaw(), 0.0001f);
        assertEquals(-20.0f, state.pitch(), 0.0001f);
    }

    @Test
    void playerViewResolveShouldAddStoredYawPitchOffsets() {
        WindowPositionTracker.WindowTransformState state = tracker.resolve(
                PositionMode.PLAYER_VIEW,
                new WindowOffset(2.0f, 0.0f, 0.0f),
                new Vec3d(0.0, 64.0, 0.0),
                Vec3d.fromPolar(-15.0f, 20.0f),
                20.0f,
                -15.0f,
                null,
                30.0f,
                10.0f
        );

        Vec3d expectedAnchor = new CoordinateTransformer().toPlayerFixedAnchor(
                new Vec3d(0.0, 64.0, 0.0),
                new WindowOffset(2.0f, 0.0f, 0.0f),
                50.0f,
                -5.0f
        );

        assertEquals(expectedAnchor.x, state.anchor().x, 0.0001);
        assertEquals(expectedAnchor.y, state.anchor().y, 0.0001);
        assertEquals(expectedAnchor.z, state.anchor().z, 0.0001);
        assertEquals(50.0f, state.yaw(), 0.0001f);
        assertEquals(-5.0f, state.pitch(), 0.0001f);
    }
}
