package com.interactivedisplay.core.positioning;

import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class CoordinateTransformer {
    private static final Vector3f WORLD_UP = new Vector3f(0.0f, 1.0f, 0.0f);
    private static final WindowBasis WORLD_FIXED_BASIS = new WindowBasis(
            new Vec3d(-1.0, 0.0, 0.0),
            new Vec3d(0.0, 1.0, 0.0),
            new Vec3d(0.0, 0.0, -1.0)
    );

    public Vec3d toWorld(Vec3d anchor, Vector3f local, PositionMode positionMode, float yaw, float pitch) {
        WindowBasis basis = basis(positionMode, yaw, pitch);
        return anchor
                .add(basis.right().multiply(local.x))
                .add(basis.up().multiply(local.y))
                .add(basis.normal().multiply(local.z));
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

    public double raycastQuadDistance(Vec3d start,
                                      Vec3d direction,
                                      Vec3d center,
                                      Vec3d right,
                                      Vec3d up,
                                      Vec3d normal,
                                      float halfWidth,
                                      float halfHeight,
                                      double maxDistance) {
        double denominator = direction.dotProduct(normal);
        if (Math.abs(denominator) < 1.0E-6D) {
            return -1.0D;
        }

        double distance = center.subtract(start).dotProduct(normal) / denominator;
        if (distance < 0.0D || distance > maxDistance) {
            return -1.0D;
        }

        Vec3d hit = start.add(direction.multiply(distance));
        Vec3d offset = hit.subtract(center);
        double projectedRight = offset.dotProduct(right);
        double projectedUp = offset.dotProduct(up);
        if (Math.abs(projectedRight) > halfWidth || Math.abs(projectedUp) > halfHeight) {
            return -1.0D;
        }
        return distance;
    }

    public WindowBasis basis(PositionMode positionMode, float yaw, float pitch) {
        if (positionMode == PositionMode.PLAYER_FIXED) {
            return WORLD_FIXED_BASIS;
        }

        Vec3d lookDirection = Vec3d.fromPolar(pitch, yaw);
        Vector3f look = normalize(lookDirection);
        Vector3f right = right(look);
        Vector3f up = up(look, right);
        Vector3f normal = look.negate(new Vector3f());
        return new WindowBasis(
                new Vec3d(right.x, right.y, right.z),
                new Vec3d(up.x, up.y, up.z),
                new Vec3d(normal.x, normal.y, normal.z)
        );
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

    public record WindowBasis(Vec3d right, Vec3d up, Vec3d normal) {
    }
}
