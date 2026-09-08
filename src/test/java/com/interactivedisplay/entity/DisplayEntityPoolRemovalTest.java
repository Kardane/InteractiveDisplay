package com.interactivedisplay.entity;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DisplayEntityPoolRemovalTest {
    @Test
    void displayEntityPoolShouldNotBePresent() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.interactivedisplay.entity.DisplayEntityPool"));
    }
}
