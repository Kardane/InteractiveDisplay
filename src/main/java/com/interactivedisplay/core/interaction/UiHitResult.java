package com.interactivedisplay.core.interaction;

import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.window.WindowNavigationContext;
import net.minecraft.world.phys.Vec3;
import com.interactivedisplay.core.window.WindowComponentRuntime;

public record UiHitResult(
        String windowId,
        WindowNavigationContext navigationContext,
        String componentId,
        WindowComponentRuntime runtime,
        ComponentAction action,
        Vec3 hitPosition,
        double distanceSquared
) {
}
