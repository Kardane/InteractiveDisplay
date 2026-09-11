package com.interactivedisplay.core.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AnimationInterpolationTest {
    @Test
    void smoothUsesSmoothstepCurve() {
        assertEquals(0.0f, AnimationInterpolation.SMOOTH.apply(0.0f), 0.0001f);
        assertEquals(0.15625f, AnimationInterpolation.SMOOTH.apply(0.25f), 0.0001f);
        assertEquals(0.5f, AnimationInterpolation.SMOOTH.apply(0.5f), 0.0001f);
        assertEquals(1.0f, AnimationInterpolation.SMOOTH.apply(1.0f), 0.0001f);
    }

    @Test
    void cutChangesOnlyAtCompletion() {
        assertEquals(0.0f, AnimationInterpolation.CUT.apply(0.999f), 0.0001f);
        assertEquals(1.0f, AnimationInterpolation.CUT.apply(1.0f), 0.0001f);
    }

    @Test
    void interpolationClampsProgress() {
        assertEquals(0.0f, AnimationInterpolation.LINEAR.apply(-1.0f), 0.0001f);
        assertEquals(1.0f, AnimationInterpolation.LINEAR.apply(2.0f), 0.0001f);
    }
}
