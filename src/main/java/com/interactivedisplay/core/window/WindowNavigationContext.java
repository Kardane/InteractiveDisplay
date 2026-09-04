package com.interactivedisplay.core.window;

import com.interactivedisplay.core.positioning.PositionMode;
import net.minecraft.world.phys.Vec3;

public record WindowNavigationContext(
        String windowId,
        String groupId,
        PositionMode positionMode,
        Vec3 fixedAnchor,
        float fixedYaw,
        float fixedPitch
) {
}
