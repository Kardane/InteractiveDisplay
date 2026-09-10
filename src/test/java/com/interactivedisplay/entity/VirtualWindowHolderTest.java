package com.interactivedisplay.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VirtualWindowHolderTest {
    @Test
    void passengerRidingOffsetShouldUseVanillaThreeQuarterHeight() {
        assertEquals(1.35D, VirtualWindowHolder.passengerRidingOffset(1.8D), 0.000001D);
        assertEquals(1.125D, VirtualWindowHolder.passengerRidingOffset(1.5D), 0.000001D);
    }
}
