package com.interactivedisplay.core.window;

import com.interactivedisplay.core.animation.AnimationDefinition;
import com.interactivedisplay.core.animation.AnimationRegistry;
import com.interactivedisplay.core.animation.AnimationRuntime;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.ComponentDefinition;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.component.TextInputComponentDefinition;
import eu.pb4.mapcanvas.api.core.PlayerCanvas;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

public final class WindowComponentRuntime {
    private static final double DEFAULT_MAX_DISTANCE = 6.0D;

    private final ResourceKey<Level> worldKey;
    private ComponentDefinition definition;
    private Vector3f localPosition;
    private final DisplayElement displayElement;
    private final VirtualElement virtualElement;
    private final DisplayElement backgroundElement;
    private final Vector3f baseScale;
    private final Vector3f baseTranslation;
    private final Vector3f backgroundBaseScale;
    private final Vector3f backgroundBaseTranslation;
    private PlayerCanvas mapCanvas;
    private boolean hovered;
    private String inputValue;
    private long lastTextRefreshTick = Long.MIN_VALUE;
    private final List<ScheduledAnimation> activeAnimations = new ArrayList<>();
    private long animationStartTick = Long.MIN_VALUE;
    private long animationGeneration;

    public WindowComponentRuntime(ResourceKey<Level> worldKey,
                                  ComponentDefinition definition,
                                  Vector3f localPosition,
                                  DisplayElement displayElement,
                                  PlayerCanvas mapCanvas) {
        this(worldKey, definition, localPosition, displayElement, mapCanvas, displayElement, null);
    }

    public WindowComponentRuntime(ResourceKey<Level> worldKey,
                                  ComponentDefinition definition,
                                  Vector3f localPosition,
                                  DisplayElement displayElement,
                                  PlayerCanvas mapCanvas,
                                  VirtualElement virtualElement) {
        this(worldKey, definition, localPosition, displayElement, mapCanvas, virtualElement, null);
    }

    public WindowComponentRuntime(ResourceKey<Level> worldKey,
                                  ComponentDefinition definition,
                                  Vector3f localPosition,
                                  DisplayElement displayElement,
                                  PlayerCanvas mapCanvas,
                                  VirtualElement virtualElement,
                                  DisplayElement backgroundElement) {
        this.worldKey = worldKey;
        this.definition = definition;
        this.localPosition = new Vector3f(localPosition);
        this.displayElement = displayElement;
        this.virtualElement = virtualElement;
        this.backgroundElement = backgroundElement;
        this.mapCanvas = mapCanvas;
        this.inputValue = definition instanceof TextInputComponentDefinition input ? input.initialValue() : null;
        this.baseScale = displayElement == null ? new Vector3f(1.0f) : new Vector3f(displayElement.getScale());
        this.baseTranslation = displayElement == null ? new Vector3f() : new Vector3f(displayElement.getTranslation());
        this.backgroundBaseScale = backgroundElement == null ? new Vector3f(1.0f) : new Vector3f(backgroundElement.getScale());
        this.backgroundBaseTranslation = backgroundElement == null ? new Vector3f() : new Vector3f(backgroundElement.getTranslation());
    }

    public ResourceKey<Level> worldKey() {
        return this.worldKey;
    }

    public ComponentDefinition definition() {
        return this.definition;
    }

    public void redefine(ComponentDefinition definition, Vector3f localPosition) {
        cancelAnimations();
        this.definition = definition;
        this.localPosition = new Vector3f(localPosition);
        this.hovered = false;
        this.inputValue = definition instanceof TextInputComponentDefinition input ? input.initialValue() : null;
        this.lastTextRefreshTick = Long.MIN_VALUE;
        if (definition instanceof TextComponentDefinition text && !text.animations().isEmpty()) {
            configureAnimations(text.animations());
        }
    }

    public Vector3f localPosition() {
        return new Vector3f(this.localPosition);
    }

    public DisplayElement displayElement() {
        return this.displayElement;
    }

    public VirtualElement virtualElement() {
        return this.virtualElement;
    }

    public DisplayElement backgroundElement() {
        return this.backgroundElement;
    }

    public List<VirtualElement> virtualElements() {
        if (this.virtualElement == null) {
            return this.backgroundElement == null ? List.of() : List.of(this.backgroundElement);
        }
        if (this.backgroundElement == null || this.backgroundElement == this.virtualElement) {
            return List.of(this.virtualElement);
        }
        return List.of(this.backgroundElement, this.virtualElement);
    }

    public Vector3f baseScale() {
        return new Vector3f(this.baseScale);
    }

    public Vector3f baseTranslation() {
        return new Vector3f(this.baseTranslation);
    }

    public Vector3f baseScale(DisplayElement element) {
        if (element != null && element == this.backgroundElement) {
            return new Vector3f(this.backgroundBaseScale);
        }
        return baseScale();
    }

    public Vector3f baseTranslation(DisplayElement element) {
        if (element != null && element == this.backgroundElement) {
            return new Vector3f(this.backgroundBaseTranslation);
        }
        return baseTranslation();
    }

    public boolean shouldRefreshText(long tick, int refreshInterval) {
        tickAnimations(tick);
        if (hasActiveTextAnimation()) {
            return false;
        }
        if (refreshInterval <= 0) {
            return false;
        }
        if (this.lastTextRefreshTick == Long.MIN_VALUE || tick - this.lastTextRefreshTick >= refreshInterval) {
            this.lastTextRefreshTick = tick;
            return true;
        }
        return false;
    }

    public void configureAnimations(List<AnimationDefinition> definitions) {
        cancelAnimations();
        this.animationStartTick = Long.MIN_VALUE;
        long generation = this.animationGeneration;
        for (AnimationDefinition animationDefinition : definitions) {
            AnimationRegistry.create(this, animationDefinition).ifPresent(animation -> {
                animation.prepare();
                this.activeAnimations.add(new ScheduledAnimation(generation, animation));
            });
        }
    }

    public void tickAnimations(long tick) {
        if (this.activeAnimations.isEmpty()) {
            return;
        }
        if (this.animationStartTick == Long.MIN_VALUE) {
            this.animationStartTick = tick;
        }
        long elapsedTick = Math.max(0L, tick - this.animationStartTick);
        Iterator<ScheduledAnimation> iterator = this.activeAnimations.iterator();
        while (iterator.hasNext()) {
            ScheduledAnimation scheduled = iterator.next();
            if (scheduled.generation() != this.animationGeneration || scheduled.animation().tick(elapsedTick)) {
                iterator.remove();
            }
        }
    }

    public boolean hasActiveTextAnimation() {
        for (ScheduledAnimation scheduled : this.activeAnimations) {
            if (scheduled.generation() == this.animationGeneration && scheduled.animation().affectsTextContent()) {
                return true;
            }
        }
        return false;
    }

    public int activeAnimationCount() {
        return this.activeAnimations.size();
    }

    public long animationGeneration() {
        return this.animationGeneration;
    }

    public void cancelAnimations() {
        this.animationGeneration++;
        for (ScheduledAnimation scheduled : this.activeAnimations) {
            scheduled.animation().cancel();
        }
        this.activeAnimations.clear();
        this.animationStartTick = Long.MIN_VALUE;
    }

    public int entityCount() {
        int count = 0;
        for (VirtualElement element : virtualElements()) {
            count += element.getEntityIds().size();
        }
        return count;
    }

    public PlayerCanvas mapCanvas() {
        return this.mapCanvas;
    }

    public void setMapCanvas(PlayerCanvas mapCanvas) {
        this.mapCanvas = mapCanvas;
    }

    public boolean hovered() {
        return this.hovered;
    }

    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    public String inputValue() {
        return this.inputValue == null ? "" : this.inputValue;
    }

    public void setInputValue(String value) {
        if (!(this.definition instanceof TextInputComponentDefinition input)) {
            return;
        }
        String normalized = value == null ? "" : value;
        if (normalized.length() > input.maxLength()) {
            normalized = normalized.substring(0, input.maxLength());
        }
        this.inputValue = normalized;
    }

    public boolean interactive() {
        return this.definition instanceof ButtonComponentDefinition
                || this.definition instanceof TextInputComponentDefinition;
    }

    public float hitHalfWidth() {
        if (this.definition instanceof ButtonComponentDefinition button) {
            return button.size().width() / 2.0f;
        }
        if (this.definition instanceof TextInputComponentDefinition input) {
            return input.size().width() / 2.0f;
        }
        return 0.0f;
    }

    public float hitHalfHeight() {
        if (this.definition instanceof ButtonComponentDefinition button) {
            return button.size().height() / 2.0f;
        }
        if (this.definition instanceof TextInputComponentDefinition input) {
            return input.size().height() / 2.0f;
        }
        return 0.0f;
    }

    /**
     * TextDisplay's background quad starts at its display origin and grows
     * upward by one font line per wrapped line. Center the hitbox on that
     * rendered quad so its top and bottom edges agree with the visible face.
     */
    public Vector3f hitCenterLocalPosition() {
        Vector3f center = new Vector3f(this.localPosition);
        if (this.definition instanceof ButtonComponentDefinition
                || this.definition instanceof TextInputComponentDefinition) {
            center.y += this.hitHalfHeight();
        }
        return center;
    }

    public double maxDistance() {
        return DEFAULT_MAX_DISTANCE;
    }

    public ComponentAction action() {
        if (this.definition instanceof ButtonComponentDefinition button) {
            return button.action();
        }
        if (this.definition instanceof TextInputComponentDefinition) {
            return ComponentAction.openTextInput();
        }
        return null;
    }

    private record ScheduledAnimation(long generation, AnimationRuntime animation) {
    }
}
