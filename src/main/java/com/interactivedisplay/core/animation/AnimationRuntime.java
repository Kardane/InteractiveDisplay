package com.interactivedisplay.core.animation;

public interface AnimationRuntime {
    String componentId();

    void prepare();

    /**
     * Advances the animation using ticks elapsed since the component animation set was started.
     *
     * @return true when the animation has reached its terminal state
     */
    boolean tick(long elapsedTick);

    default boolean affectsTextContent() {
        return false;
    }

    default void cancel() {
    }
}
