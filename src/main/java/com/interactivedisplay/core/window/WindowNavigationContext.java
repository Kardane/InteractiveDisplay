package com.interactivedisplay.core.window;

import com.interactivedisplay.core.positioning.PositionMode;
import net.minecraft.util.math.Vec3d;

public record WindowNavigationContext(
        String windowId,
        String groupId,
        PositionMode positionMode,
        Vec3d fixedAnchor,
        float fixedYaw,
        float fixedPitch
) {
}
