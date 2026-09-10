package com.interactivedisplay.api.group;

import com.interactivedisplay.api.window.WindowPositionMode;
import java.util.Objects;
import net.minecraft.world.phys.Vec3;

public record GroupOpenOptions(
        WindowPositionMode mode,
        Vec3 fixedAnchor,
        float fixedYaw,
        float fixedPitch
) {
    public GroupOpenOptions {
        Objects.requireNonNull(mode, "mode");
        if (mode == WindowPositionMode.FIXED) {
            Objects.requireNonNull(fixedAnchor, "fixedAnchor");
        }
    }

    public static GroupOpenOptions playerView() {
        return new GroupOpenOptions(WindowPositionMode.PLAYER_VIEW, null, 0.0f, 0.0f);
    }

    public static GroupOpenOptions playerFixed() {
        return new GroupOpenOptions(WindowPositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
    }

    public static GroupOpenOptions fixed(Vec3 anchor, float yaw, float pitch) {
        return new GroupOpenOptions(WindowPositionMode.FIXED, Objects.requireNonNull(anchor, "anchor"), yaw, pitch);
    }
}
