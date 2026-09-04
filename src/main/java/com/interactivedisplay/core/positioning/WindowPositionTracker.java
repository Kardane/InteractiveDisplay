package com.interactivedisplay.core.positioning;

import com.interactivedisplay.core.window.WindowInstance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

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

    public WindowTransformState resolve(ServerPlayer player,
                                        PositionMode positionMode,
                                        WindowOffset offset,
                                        Vec3 fixedAnchor,
                                        float fixedYaw,
                                        float fixedPitch) {
        return resolve(positionMode, offset, player.getEyePosition(), player.getViewVector(1.0f), player.getYRot(), player.getXRot(), fixedAnchor, fixedYaw, fixedPitch);
    }

    WindowTransformState resolve(PositionMode positionMode,
                                 WindowOffset offset,
                                 Vec3 eyePos,
                                 Vec3 look,
                                 float playerYaw,
                                 float playerPitch,
                                 Vec3 fixedAnchor,
                                 float fixedYaw,
                                 float fixedPitch) {
        float resolvedYaw = switch (positionMode) {
            case FIXED, PLAYER_FIXED -> fixedYaw;
            case PLAYER_VIEW -> Mth.wrapDegrees(playerYaw + fixedYaw);
        };
        float resolvedPitch = switch (positionMode) {
            case FIXED -> 0.0f;
            case PLAYER_FIXED -> fixedPitch;
            case PLAYER_VIEW -> Mth.clamp(playerPitch + fixedPitch, -90.0f, 90.0f);
        };
        Vec3 resolvedLook = positionMode == PositionMode.PLAYER_VIEW ? Vec3.directionFromRotation(resolvedPitch, resolvedYaw) : look;
        Vec3 anchor = switch (positionMode) {
            case FIXED -> fixedAnchor != null
                    ? fixedAnchor
                    : this.transformer.toFixedAnchorFromPlayerEye(eyePos, resolvedLook, offset);
            case PLAYER_FIXED -> this.transformer.toPlayerFixedAnchor(eyePos, offset, fixedYaw, fixedPitch);
            case PLAYER_VIEW -> this.transformer.toPlayerViewAnchor(eyePos, resolvedLook, offset);
        };
        return new WindowTransformState(anchor, resolvedYaw, resolvedPitch, eyePos);
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
        if (instance.currentAnchor().distanceToSqr(nextState.anchor()) > APPLY_POSITION_EPSILON_SQUARED) {
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
        Vec3 targetAnchor = rawState.anchor();
        if (instance.targetAnchor().distanceToSqr(rawState.anchor()) < PLAYER_FIXED_POSITION_DEADZONE_SQUARED) {
            targetAnchor = instance.targetAnchor();
        }
        return new WindowTransformState(targetAnchor, rawState.yaw(), rawState.pitch(), rawState.focusPoint());
    }

    private WindowTransformState applyPlayerViewDeadzone(WindowInstance instance, WindowTransformState rawState) {
        Vec3 targetAnchor = rawState.anchor();
        if (instance.targetAnchor().distanceToSqr(rawState.anchor()) < PLAYER_VIEW_POSITION_DEADZONE_SQUARED) {
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

    private static Vec3 smoothPosition(Vec3 current, Vec3 target, float alpha, double snapDistance) {
        if (current.distanceToSqr(target) <= snapDistance * snapDistance) {
            return target;
        }
        return current.lerp(target, alpha);
    }

    private static float smoothAngle(float current, float target, float alpha, float snapThreshold) {
        float delta = angleDelta(current, target);
        if (Math.abs(delta) <= snapThreshold) {
            return Mth.wrapDegrees(target);
        }
        return Mth.wrapDegrees(current + (delta * alpha));
    }

    private static float smoothLinear(float current, float target, float alpha, float snapThreshold) {
        float delta = target - current;
        if (Math.abs(delta) <= snapThreshold) {
            return target;
        }
        return current + (delta * alpha);
    }

    private static float angleDelta(float current, float target) {
        return Mth.wrapDegrees(target - current);
    }

    public record WindowTransformState(Vec3 anchor, float yaw, float pitch, Vec3 focusPoint) {
        public WindowTransformState(Vec3 anchor, float yaw, float pitch) {
            this(anchor, yaw, pitch, null);
        }
    }
}
