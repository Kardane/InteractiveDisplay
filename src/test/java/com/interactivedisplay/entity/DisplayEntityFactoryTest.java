package com.interactivedisplay.entity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DisplayEntityFactoryTest {
    @Test
    void buildDisplayDataShouldIncludeInterpolationKeys() {
        String snbt = DisplayEntityFactory.buildDisplayDataSnbt("fixed", 1.0f);

        assertTrue(snbt.contains("start_interpolation:0"));
        assertTrue(snbt.contains("interpolation_duration:3"));
        assertTrue(snbt.contains("teleport_duration:3"));
    }
}
