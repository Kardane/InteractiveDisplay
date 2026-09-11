package com.interactivedisplay.core.animation;

import com.interactivedisplay.core.window.WindowComponentRuntime;

@FunctionalInterface
public interface AnimationFactory {
    AnimationRuntime create(WindowComponentRuntime target, AnimationDefinition definition);
}
