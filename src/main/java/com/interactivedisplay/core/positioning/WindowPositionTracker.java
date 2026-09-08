package com.interactivedisplay.core.positioning;

import com.interactivedisplay.core.window.WindowInstance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class WindowPositionTracker {
    private static final long UPDATE_INTERVAL_TICKS = 2L;
    private static final double APPLY_POSITION_EPSILON_SQUARED = 1.0E-6D;
    private static final float APPLY_ROTATION_EPSILON = 0.01f;
    private static final float PLAYER_VIEW_ROTATION_APPLY_THRESHOLD = 0.35f;

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
        return rawState;
    }

    public WindowTransformState smooth(WindowInstance instance, WindowTransformState targetState) {
        // Player-relative displays interpolate their transformation on the client. Keeping the server state exact
        // prevents a passenger from visually lagging behind its owner while walking or sprinting.
        return targetState;
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

    private static float angleDelta(float current, float target) {
        return Mth.wrapDegrees(target - current);
    }

    public record WindowTransformState(Vec3 anchor, float yaw, float pitch, Vec3 focusPoint) {
        public WindowTransformState(Vec3 anchor, float yaw, float pitch) {
            this(anchor, yaw, pitch, null);
        }
    }
}
