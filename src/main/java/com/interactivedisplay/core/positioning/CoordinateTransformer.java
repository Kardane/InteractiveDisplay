package com.interactivedisplay.core.positioning;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class CoordinateTransformer {
    private static final Vector3f WORLD_UP = new Vector3f(0.0f, 1.0f, 0.0f);

    public Vec3 toWorld(Vec3 anchor, Vector3f local, PositionMode positionMode, float yaw, float pitch) {
        WindowBasis basis = basis(positionMode, yaw, pitch);
        return anchor
                .add(basis.right().scale(local.x))
                .add(basis.up().scale(local.y))
                .add(basis.normal().scale(local.z));
    }

    public Vec3 toFixedAnchorFromPlayerEye(Vec3 eyePos, Vec3 lookDirection, WindowOffset offset) {
        Vector3f look = normalize(lookDirection);
        Vector3f right = right(look);
        Vector3f up = up(look, right);
        Vector3f anchor = new Vector3f((float) eyePos.x, (float) eyePos.y, (float) eyePos.z);
        anchor.add(look.mul(offset.forward(), new Vector3f()));
        anchor.add(right.mul(offset.horizontal(), new Vector3f()));
        anchor.add(up.mul(offset.vertical(), new Vector3f()));
        return new Vec3(anchor.x, anchor.y, anchor.z);
    }

    public Vec3 toPlayerFixedAnchor(Vec3 eyePos, WindowOffset offset) {
        return toPlayerFixedAnchor(eyePos, offset, 0.0f, 0.0f);
    }

    public Vec3 toPlayerFixedAnchor(Vec3 eyePos, WindowOffset offset, float yaw, float pitch) {
        Vec3 orbitOffset = orbitOffset(offset, yaw, pitch);
        return eyePos.add(orbitOffset);
    }

    public Vec3 orbitOffset(WindowOffset offset, float yaw, float pitch) {
        Vec3 orbitDirection = Vec3.directionFromRotation(pitch, yaw);
        Vector3f look = normalize(orbitDirection);
        Vector3f right = right(look);
        Vector3f up = up(look, right);
        Vector3f delta = new Vector3f();
        delta.add(look.mul(offset.forward(), new Vector3f()));
        delta.add(right.mul(offset.horizontal(), new Vector3f()));
        delta.add(up.mul(offset.vertical(), new Vector3f()));
        return new Vec3(delta.x, delta.y, delta.z);
    }

    public ViewRotation facingRotation(Vec3 anchor, Vec3 targetEyePos) {
        Vec3 direction = anchor.subtract(targetEyePos);
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (horizontal < 1.0E-6D && Math.abs(direction.y) < 1.0E-6D) {
            return new ViewRotation(0.0f, 0.0f);
        }
        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        float pitch = (float) Mth.clamp(-Math.toDegrees(Math.atan2(direction.y, horizontal)), -90.0D, 90.0D);
        return new ViewRotation(yaw, pitch);
    }

    public ViewRotation facingYawOnly(Vec3 anchor, Vec3 targetEyePos) {
        Vec3 direction = anchor.subtract(targetEyePos);
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (horizontal < 1.0E-6D) {
            return new ViewRotation(0.0f, 0.0f);
        }
        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        return new ViewRotation(yaw, 0.0f);
    }

    public Vec3 toPlayerViewAnchor(Vec3 eyePos, Vec3 lookDirection, WindowOffset offset) {
        return toFixedAnchorFromPlayerEye(eyePos, lookDirection, offset);
    }

    public Quaternionf toViewRotation(Vec3 lookDirection) {
        Vector3f look = normalize(lookDirection).negate();
        return new Quaternionf().lookAlong(look, WORLD_UP);
    }

    public double raycastQuadDistance(Vec3 start,
                                      Vec3 direction,
                                      Vec3 center,
                                      Vec3 right,
                                      Vec3 up,
                                      Vec3 normal,
                                      float halfWidth,
                                      float halfHeight,
                                      double maxDistance) {
        double denominator = direction.dot(normal);
        if (Math.abs(denominator) < 1.0E-6D) {
            return -1.0D;
        }

        double distance = center.subtract(start).dot(normal) / denominator;
        if (distance < 0.0D || distance > maxDistance) {
            return -1.0D;
        }

        Vec3 hit = start.add(direction.scale(distance));
        Vec3 offset = hit.subtract(center);
        double projectedRight = offset.dot(right);
        double projectedUp = offset.dot(up);
        if (Math.abs(projectedRight) > halfWidth || Math.abs(projectedUp) > halfHeight) {
            return -1.0D;
        }
        return distance;
    }

    public WindowBasis basis(PositionMode positionMode, float yaw, float pitch) {
        Vec3 lookDirection = Vec3.directionFromRotation(pitch, yaw);
        Vector3f look = normalize(lookDirection);
        Vector3f right = right(look);
        Vector3f up = up(look, right);
        Vector3f normal = look.negate(new Vector3f());
        return new WindowBasis(
                new Vec3(right.x, right.y, right.z),
                new Vec3(up.x, up.y, up.z),
                new Vec3(normal.x, normal.y, normal.z)
        );
    }

    private static Vector3f normalize(Vec3 vector) {
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

    public record WindowBasis(Vec3 right, Vec3 up, Vec3 normal) {
    }

    public record ViewRotation(float yaw, float pitch) {
    }
}
