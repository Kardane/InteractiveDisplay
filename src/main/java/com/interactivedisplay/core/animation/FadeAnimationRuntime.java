package com.interactivedisplay.core.animation;

import com.interactivedisplay.core.window.WindowComponentRuntime;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;

final class FadeAnimationRuntime implements AnimationRuntime {
    private final String componentId;
    private final TextDisplayElement target;
    private final float finalOpacity;
    private final int delay;
    private final int duration;
    private final AnimationInterpolation interpolation;

    FadeAnimationRuntime(WindowComponentRuntime runtime, AnimationDefinition definition) {
        if (!(runtime.displayElement() instanceof TextDisplayElement textDisplay)) {
            throw new IllegalArgumentException("fade animation requires a text display");
        }
        this.componentId = runtime.definition().id();
        this.target = textDisplay;
        this.finalOpacity = Math.max(0.0f, Math.min(1.0f, runtime.definition().opacity()));
        this.delay = definition.delay();
        this.duration = definition.duration();
        this.interpolation = definition.interpolation();
    }

    @Override
    public String componentId() {
        return this.componentId;
    }

    @Override
    public void prepare() {
        this.target.setTextOpacity(toTextOpacity(0.0f));
    }

    @Override
    public boolean tick(long elapsedTick) {
        if (elapsedTick < this.delay) {
            return false;
        }
        if (this.duration <= 0) {
            this.target.setTextOpacity(toTextOpacity(this.finalOpacity));
            return true;
        }

        float progress = Math.min(1.0f, (float) (elapsedTick - this.delay) / (float) this.duration);
        float opacity = this.finalOpacity * this.interpolation.apply(progress);
        this.target.setTextOpacity(toTextOpacity(opacity));
        if (progress < 1.0f) {
            return false;
        }
        this.target.setTextOpacity(toTextOpacity(this.finalOpacity));
        return true;
    }

    @Override
    public void cancel() {
        this.target.setTextOpacity(toTextOpacity(this.finalOpacity));
    }

    private static byte toTextOpacity(float opacity) {
        return (byte) Math.max(0, Math.min(255, Math.round(opacity * 255.0f)));
    }
}
