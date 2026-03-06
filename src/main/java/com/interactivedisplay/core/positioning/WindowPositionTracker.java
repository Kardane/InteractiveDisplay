package com.interactivedisplay.core.positioning;

import com.interactivedisplay.core.window.WindowInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class WindowPositionTracker {
    private static final double POSITION_EPSILON_SQUARED = 0.0001D;
    private static final float ROTATION_EPSILON = 0.5f;

    private final CoordinateTransformer transformer;

    public WindowPositionTracker(CoordinateTransformer transformer) {
        this.transformer = transformer;
    }

    public WindowTransformState resolve(ServerPlayerEntity player,
                                        PositionMode positionMode,
                                        WindowOffset offset,
                                        Vec3d fixedAnchor,
                                        float fixedYaw) {
        Vec3d look = player.getRotationVec(1.0f);
        Vec3d anchor = switch (positionMode) {
            case FIXED -> fixedAnchor != null
                    ? fixedAnchor
                    : this.transformer.toFixedAnchorFromPlayerEye(player.getEyePos(), look, offset);
            case PLAYER_FIXED -> this.transformer.toPlayerFixedAnchor(player.getEyePos(), offset);
            case PLAYER_VIEW -> this.transformer.toPlayerViewAnchor(player.getEyePos(), look, offset);
        };
        float resolvedYaw = switch (positionMode) {
            case FIXED -> fixedYaw;
            case PLAYER_FIXED -> 0.0f;
            case PLAYER_VIEW -> player.getYaw();
        };
        float resolvedPitch = positionMode == PositionMode.PLAYER_VIEW ? player.getPitch() : 0.0f;
        return new WindowTransformState(anchor, resolvedYaw, resolvedPitch);
    }

    public boolean shouldUpdate(WindowInstance instance, WindowTransformState nextState, long tick) {
        if (tick - instance.lastUpdateTick() < 2L) {
            return false;
        }
        if (instance.positionMode() == PositionMode.FIXED) {
            return true;
        }
        if (instance.currentAnchor().squaredDistanceTo(nextState.anchor()) > POSITION_EPSILON_SQUARED) {
            return true;
        }
        return Math.abs(instance.currentYaw() - nextState.yaw()) >= ROTATION_EPSILON
                || Math.abs(instance.currentPitch() - nextState.pitch()) >= ROTATION_EPSILON;
    }

    public record WindowTransformState(Vec3d anchor, float yaw, float pitch) {
    }
}
