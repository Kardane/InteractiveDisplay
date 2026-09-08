package com.interactivedisplay.core.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WindowTransitionTest {
    @Test
    void noneShouldDisableEnterAndExit() {
        WindowTransition transition = WindowTransition.none();

        assertEquals(0, transition.duration());
        assertFalse(transition.hasEnter());
        assertFalse(transition.hasExit());
    }

    @Test
    void configuredTransitionShouldExposeEnabledSides() {
        WindowTransition transition = new WindowTransition(
                6,
                WindowTransitionType.SCALE,
                WindowTransitionType.SLIDE_DOWN
        );

        assertTrue(transition.hasEnter());
        assertTrue(transition.hasExit());
    }

    @Test
    void transitionTypeParsingShouldBeStable() {
        assertEquals(WindowTransitionType.SCALE, WindowTransitionType.fromString("scale"));
        assertEquals(WindowTransitionType.SLIDE_UP, WindowTransitionType.fromString("SLIDE_UP"));
        assertEquals(WindowTransitionType.SLIDE_DOWN, WindowTransitionType.fromString("slide_down"));
        assertEquals(WindowTransitionType.NONE, WindowTransitionType.fromString("unknown"));
    }
}
