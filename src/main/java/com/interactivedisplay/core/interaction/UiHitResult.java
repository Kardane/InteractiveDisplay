package com.interactivedisplay.core.interaction;

import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.window.WindowComponentRuntime;
import net.minecraft.util.math.Vec3d;

public record UiHitResult(
        String windowId,
        String componentId,
        WindowComponentRuntime runtime,
        ComponentAction action,
        Vec3d hitPosition,
        double distanceSquared
) {
}
