package com.interactivedisplay.core.positioning;

import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class CoordinateTransformer {
    private static final Vector3f WORLD_UP = new Vector3f(0.0f, 1.0f, 0.0f);

    public Vec3d toWorld(Vec3d anchor, Vector3f local) {
        return new Vec3d(anchor.x + local.x, anchor.y + local.y, anchor.z + local.z);
    }

    public Vec3d toFixedAnchorFromPlayerEye(Vec3d eyePos, Vec3d lookDirection, WindowOffset offset) {
        Vector3f look = normalize(lookDirection);
        Vector3f right = right(look);
        Vector3f up = up(look, right);
        Vector3f anchor = new Vector3f((float) eyePos.x, (float) eyePos.y, (float) eyePos.z);
        anchor.add(look.mul(offset.forward(), new Vector3f()));
        anchor.add(right.mul(offset.horizontal(), new Vector3f()));
        anchor.add(up.mul(offset.vertical(), new Vector3f()));
        return new Vec3d(anchor.x, anchor.y, anchor.z);
    }

    public Vec3d toPlayerFixedAnchor(Vec3d eyePos, WindowOffset offset) {
        return new Vec3d(
                eyePos.x + offset.horizontal(),
                eyePos.y + offset.vertical(),
                eyePos.z + offset.forward()
        );
    }

    public Vec3d toPlayerViewAnchor(Vec3d eyePos, Vec3d lookDirection, WindowOffset offset) {
        return toFixedAnchorFromPlayerEye(eyePos, lookDirection, offset);
    }

    public Quaternionf toViewRotation(Vec3d lookDirection) {
        Vector3f look = normalize(lookDirection).negate();
        return new Quaternionf().lookAlong(look, WORLD_UP);
    }

    private static Vector3f normalize(Vec3d vector) {
        return new Vector3f((float) vector.x, (float) vector.y, (float) vector.z).normalize();
    }

    private static Vector3f right(Vector3f look) {
        Vector3f right = look.cross(WORLD_UP, new Vector3f());
        if (right.lengthSquared() < 1.0E-6f) {
            return new Vector3f(1.0f, 0.0f, 0.0f);
        }
        return right.normalize();
    }

    private static Vector3f up(Vector3f look, Vector3f right) {
        return right.cross(look, new Vector3f()).normalize();
    }
}
