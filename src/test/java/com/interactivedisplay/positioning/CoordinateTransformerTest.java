package com.interactivedisplay.positioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.core.positioning.CoordinateTransformer;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.positioning.WindowOffset;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class CoordinateTransformerTest {
    private final CoordinateTransformer transformer = new CoordinateTransformer();

    @Test
    void fixedYawShouldRotateLocalCoordinates() {
        Vec3d world = transformer.toWorld(new Vec3d(10.0, 64.0, 10.0), new Vector3f(1.0f, 0.25f, 0.5f), PositionMode.FIXED, 180.0f, 0.0f);
        assertEquals(11.0, world.x, 0.0001);
        assertEquals(64.25, world.y, 0.0001);
        assertEquals(10.5, world.z, 0.0001);
    }

    @Test
    void playerViewAnchorShouldApplyForwardOffset() {
        Vec3d anchor = transformer.toPlayerViewAnchor(new Vec3d(0.0, 64.0, 0.0), new Vec3d(0.0, 0.0, 1.0), new WindowOffset(2.0f, 0.0f, 0.5f));
        assertEquals(0.0, anchor.x, 0.0001);
        assertEquals(64.5, anchor.y, 0.0001);
        assertEquals(2.0, anchor.z, 0.0001);
    }

    @Test
    void raycastQuadShouldHitWithinBounds() {
        double distance = transformer.raycastQuadDistance(
                new Vec3d(0.0, 0.0, 0.0),
                new Vec3d(0.0, 0.0, 1.0),
                new Vec3d(0.0, 0.0, 2.0),
                new Vec3d(1.0, 0.0, 0.0),
                new Vec3d(0.0, 1.0, 0.0),
                new Vec3d(0.0, 0.0, -1.0),
                0.5f,
                0.5f,
                6.0D
        );

        assertTrue(distance > 0.0D);
        assertEquals(2.0D, distance, 0.0001D);
    }
}
