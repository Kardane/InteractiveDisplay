package com.interactivedisplay.positioning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.interactivedisplay.core.positioning.CoordinateTransformer;
import com.interactivedisplay.core.positioning.WindowOffset;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class CoordinateTransformerTest {
    private final CoordinateTransformer transformer = new CoordinateTransformer();

    @Test
    void worldTransformShouldAddLocalPositionToAnchor() {
        Vec3d world = transformer.toWorld(new Vec3d(10.0, 64.0, 10.0), new Vector3f(0.5f, 0.25f, 0.1f));
        assertEquals(10.5, world.x, 0.0001);
        assertEquals(64.25, world.y, 0.0001);
        assertEquals(10.1, world.z, 0.0001);
    }

    @Test
    void playerViewAnchorShouldApplyForwardOffset() {
        Vec3d anchor = transformer.toPlayerViewAnchor(new Vec3d(0.0, 64.0, 0.0), new Vec3d(0.0, 0.0, 1.0), new WindowOffset(2.0f, 0.0f, 0.5f));
        assertEquals(0.0, anchor.x, 0.0001);
        assertEquals(64.5, anchor.y, 0.0001);
        assertEquals(2.0, anchor.z, 0.0001);
    }
}
