package com.interactivedisplay.core.component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClickTypeTest {
    @Test
    void leftShouldOnlyAllowAttackInput() {
        assertTrue(ClickType.LEFT.allows(true));
        assertFalse(ClickType.LEFT.allows(false));
    }

    @Test
    void rightShouldOnlyAllowUseInput() {
        assertFalse(ClickType.RIGHT.allows(true));
        assertTrue(ClickType.RIGHT.allows(false));
    }

    @Test
    void bothShouldAllowEitherInput() {
        assertTrue(ClickType.BOTH.allows(true));
        assertTrue(ClickType.BOTH.allows(false));
    }
}
