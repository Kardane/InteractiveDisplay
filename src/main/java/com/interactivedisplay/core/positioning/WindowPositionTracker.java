package com.interactivedisplay.core.positioning;

import com.interactivedisplay.core.window.WindowInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class WindowPositionTracker {
    private static final long UPDATE_INTERVAL_TICKS = 2L;
    private static final double APPLY_POSITION_EPSILON_SQUARED = 1.0E-6D;
    private static final float APPLY_ROTATION_EPSILON = 0.01f;

    private static final double PLAYER_FIXED_POSITION_DEADZONE_SQUARED = 0.02D * 0.02D;
    private static final double PLAYER_FIXED_POSITION_SNAP_DISTANCE = 0.005D;
    private static final float PLAYER_FIXED_POSITION_ALPHA = 0.45f;

    private static final float PLAYER_VIEW_YAW_DEADZONE = 1.5f;
    private static final float PLAYER_VIEW_PITCH_DEADZONE = 1.0f;
    private static final double PLAYER_VIEW_POSITION_DEADZONE_SQUARED = 0.015D * 0.015D;
    private static final double PLAYER_VIEW_POSITION_SNAP_DISTANCE = 0.005D;
    private static final float PLAYER_VIEW_POSITION_ALPHA = 0.35f;
    private static final float PLAYER_VIEW_ROTATION_ALPHA = 0.30f;
    private static final float PLAYER_VIEW_ROTATION_APPLY_THRESHOLD = 0.35f;
    private static final float PLAYER_VIEW_ROTATION_SNAP_THRESHOLD = 0.15f;

    private final CoordinateTransformer transformer;

    public WindowPositionTracker(CoordinateTransformer transformer) {
        this.transformer = transformer;
    }

    public WindowTransformState resolve(ServerPlayerEntity player,
                                        PositionMode positionMode,
                                        WindowOffset offset,
                                        Vec3d fixedAnchor,
                                        float fixedYaw,
                                        float fixedPitch) {
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d anchor = switch (positionMode) {
            case FIXED -> fixedAnchor != null
                    ? fixedAnchor
                    : this.transformer.toFixedAnchorFromPlayerEye(player.getEyePos(), look, offset);
            case PLAYER_FIXED -> this.transformer.toPlayerFixedAnchor(player.getEyePos(), offset, fixedYaw, fixedPitch);
            case PLAYER_VIEW -> this.transformer.toPlayerViewAnchor(player.getEyePos(), look, offset);
        };
        if (positionMode == PositionMode.PLAYER_FIXED) {
            CoordinateTransformer.ViewRotation facing = this.transformer.facingYawOnly(anchor, player.getEyePos());
            return new WindowTransformState(anchor, facing.yaw(), 0.0f, player.getEyePos());
        }
        float resolvedYaw = positionMode == PositionMode.PLAYER_VIEW ? player.getYaw() : fixedYaw;
        float resolvedPitch = positionMode == PositionMode.PLAYER_VIEW ? player.getPitch() : 0.0f;
        return new WindowTransformState(anchor, resolvedYaw, resolvedPitch, player.getEyePos());
    }

    public WindowTransformState applyDeadzone(WindowInstance instance, WindowTransformState rawState) {
        return switch (instance.positionMode()) {
            case FIXED -> rawState;
            case PLAYER_FIXED -> applyPlayerFixedDeadzone(instance, rawState);
            case PLAYER_VIEW -> applyPlayerViewDeadzone(instance, rawState);
        };
    }

    public WindowTransformState smooth(WindowInstance instance, WindowTransformState targetState) {
        return switch (instance.positionMode()) {
            case FIXED -> targetState;
            case PLAYER_FIXED -> new WindowTransformState(
                    smoothPosition(instance.currentAnchor(), targetState.anchor(), PLAYER_FIXED_POSITION_ALPHA, PLAYER_FIXED_POSITION_SNAP_DISTANCE),
                    targetState.yaw(),
                    targetState.pitch(),
                    targetState.focusPoint()
            );
            case PLAYER_VIEW -> new WindowTransformState(
                    smoothPosition(instance.currentAnchor(), targetState.anchor(), PLAYER_VIEW_POSITION_ALPHA, PLAYER_VIEW_POSITION_SNAP_DISTANCE),
                    smoothAngle(instance.currentYaw(), targetState.yaw(), PLAYER_VIEW_ROTATION_ALPHA, PLAYER_VIEW_ROTATION_SNAP_THRESHOLD),
                    smoothLinear(instance.currentPitch(), targetState.pitch(), PLAYER_VIEW_ROTATION_ALPHA, PLAYER_VIEW_ROTATION_SNAP_THRESHOLD),
                    targetState.focusPoint()
            );
        };
    }

    public boolean shouldUpdate(WindowInstance instance, WindowTransformState nextState, long tick) {
        if (tick - instance.lastUpdateTick() < UPDATE_INTERVAL_TICKS) {
            return false;
        }
        if (instance.positionMode() == PositionMode.FIXED) {
            return false;
        }
        if (instance.currentAnchor().squaredDistanceTo(nextState.anchor()) > APPLY_POSITION_EPSILON_SQUARED) {
            return true;
        }
        if (instance.positionMode() == PositionMode.PLAYER_FIXED) {
            return Math.abs(angleDelta(instance.currentYaw(), nextState.yaw())) >= APPLY_ROTATION_EPSILON
                    || Math.abs(instance.currentPitch() - nextState.pitch()) >= APPLY_ROTATION_EPSILON;
        }
        return Math.abs(angleDelta(instance.currentYaw(), nextState.yaw())) >= PLAYER_VIEW_ROTATION_APPLY_THRESHOLD
                || Math.abs(instance.currentPitch() - nextState.pitch()) >= PLAYER_VIEW_ROTATION_APPLY_THRESHOLD;
    }

    private WindowTransformState applyPlayerFixedDeadzone(WindowInstance instance, WindowTransformState rawState) {
        Vec3d targetAnchor = rawState.anchor();
        if (instance.targetAnchor().squaredDistanceTo(rawState.anchor()) < PLAYER_FIXED_POSITION_DEADZONE_SQUARED) {
            targetAnchor = instance.targetAnchor();
        }
        CoordinateTransformer.ViewRotation facing = rawState.focusPoint() == null
                ? new CoordinateTransformer.ViewRotation(rawState.yaw(), rawState.pitch())
                : this.transformer.facingYawOnly(targetAnchor, rawState.focusPoint());
        return new WindowTransformState(targetAnchor, facing.yaw(), 0.0f, rawState.focusPoint());
    }

    private WindowTransformState applyPlayerViewDeadzone(WindowInstance instance, WindowTransformState rawState) {
        Vec3d targetAnchor = rawState.anchor();
        if (instance.targetAnchor().squaredDistanceTo(rawState.anchor()) < PLAYER_VIEW_POSITION_DEADZONE_SQUARED) {
            targetAnchor = instance.targetAnchor();
        }
        float targetYaw = Math.abs(angleDelta(instance.targetYaw(), rawState.yaw())) < PLAYER_VIEW_YAW_DEADZONE
                ? instance.targetYaw()
                : rawState.yaw();
        float targetPitch = Math.abs(instance.targetPitch() - rawState.pitch()) < PLAYER_VIEW_PITCH_DEADZONE
                ? instance.targetPitch()
                : rawState.pitch();
        return new WindowTransformState(targetAnchor, targetYaw, targetPitch, rawState.focusPoint());
    }

    private static Vec3d smoothPosition(Vec3d current, Vec3d target, float alpha, double snapDistance) {
        if (current.squaredDistanceTo(target) <= snapDistance * snapDistance) {
            return target;
        }
        return current.lerp(target, alpha);
    }

    private static float smoothAngle(float current, float target, float alpha, float snapThreshold) {
        float delta = angleDelta(current, target);
        if (Math.abs(delta) <= snapThreshold) {
            return MathHelper.wrapDegrees(target);
        }
        return MathHelper.wrapDegrees(current + (delta * alpha));
    }

    private static float smoothLinear(float current, float target, float alpha, float snapThreshold) {
        float delta = target - current;
        if (Math.abs(delta) <= snapThreshold) {
            return target;
        }
        return current + (delta * alpha);
    }

    private static float angleDelta(float current, float target) {
        return MathHelper.wrapDegrees(target - current);
    }

    public record WindowTransformState(Vec3d anchor, float yaw, float pitch, Vec3d focusPoint) {
        public WindowTransformState(Vec3d anchor, float yaw, float pitch) {
            this(anchor, yaw, pitch, null);
        }
    }
}
