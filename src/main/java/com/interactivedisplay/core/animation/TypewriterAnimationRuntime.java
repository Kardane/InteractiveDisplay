package com.interactivedisplay.core.animation;

import com.interactivedisplay.core.window.WindowComponentRuntime;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import net.minecraft.network.chat.Component;

final class TypewriterAnimationRuntime implements AnimationRuntime {
    private final String componentId;
    private final TextDisplayElement target;
    private final Component finalText;
    private final StyledTextSequence sequence;
    private final int delay;
    private final int interval;
    private final int charsPerStep;
    private int visibleCount = -1;

    TypewriterAnimationRuntime(WindowComponentRuntime runtime, AnimationDefinition definition) {
        if (!(runtime.displayElement() instanceof TextDisplayElement textDisplay)) {
            throw new IllegalArgumentException("typewriter animation requires a text display");
        }
        this.componentId = runtime.definition().id();
        this.target = textDisplay;
        this.finalText = textDisplay.getText().copy();
        this.sequence = StyledTextSequence.of(this.finalText);
        this.delay = definition.delay();
        this.interval = definition.interval();
        this.charsPerStep = definition.charsPerStep();
    }

    @Override
    public String componentId() {
        return this.componentId;
    }

    @Override
    public void prepare() {
        this.visibleCount = 0;
        this.target.setText(Component.empty());
    }

    @Override
    public boolean tick(long elapsedTick) {
        if (this.sequence.size() == 0) {
            this.target.setText(this.finalText);
            return true;
        }
        if (elapsedTick < this.delay) {
            return false;
        }

        long animationTick = elapsedTick - this.delay;
        long steps = animationTick / this.interval + 1L;
        long requested = Math.min((long) this.sequence.size(), steps * this.charsPerStep);
        int nextVisible = (int) requested;
        if (nextVisible != this.visibleCount) {
            this.visibleCount = nextVisible;
            this.target.setText(this.sequence.prefix(nextVisible));
        }
        if (nextVisible < this.sequence.size()) {
            return false;
        }

        this.target.setText(this.finalText);
        return true;
    }

    @Override
    public boolean affectsTextContent() {
        return true;
    }

    @Override
    public void cancel() {
        this.target.setText(this.finalText);
    }
}
