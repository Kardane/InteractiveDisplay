package com.interactivedisplay.api.window;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

public record WindowOpenOptions(
        WindowPositionMode mode,
        Vec3 fixedAnchor,
        float fixedYaw,
        float fixedPitch
) {
    public WindowOpenOptions {
        Objects.requireNonNull(mode, "mode");
    }

    public static WindowOpenOptions playerView() {
        return new WindowOpenOptions(WindowPositionMode.PLAYER_VIEW, null, 0.0f, 0.0f);
    }

    public static WindowOpenOptions playerFixed() {
        return new WindowOpenOptions(WindowPositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
    }

    public static WindowOpenOptions fixed(Vec3 anchor, float yaw, float pitch) {
        return new WindowOpenOptions(WindowPositionMode.FIXED, anchor, yaw, pitch);
    }
}
