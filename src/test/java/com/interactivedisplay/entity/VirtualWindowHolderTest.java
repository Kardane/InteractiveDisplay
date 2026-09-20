package com.interactivedisplay.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class VirtualWindowHolderTest {
    @Test
    void unattachedRenderOriginShouldUseConfiguredAnchor() {
        Vec3 anchor = new Vec3(1.0D, 2.0D, 3.0D);
        VirtualWindowHolder holder = new VirtualWindowHolder(null, anchor);

        assertEquals(anchor, holder.passengerRenderOrigin());
    }
}
