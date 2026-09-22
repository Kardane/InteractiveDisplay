package com.interactivedisplay.core.window;

import com.interactivedisplay.core.animation.AnimationDefinition;
import com.interactivedisplay.core.animation.AnimationRegistry;
import com.interactivedisplay.core.animation.AnimationRuntime;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.ComponentDefinition;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.component.TextInputComponentDefinition;
import com.interactivedisplay.entity.RenderedComponent;
import eu.pb4.mapcanvas.api.core.PlayerCanvas;
import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

public final class WindowComponentRuntime {
    private static final double DEFAULT_MAX_DISTANCE = 6.0D;

    private final ResourceKey<Level> worldKey;
    private ComponentDefinition definition;
    private Vector3f localPosition;
    private final RenderedComponent renderedComponent;
    private final Map<DisplayElement, Vector3f> baseScales = new IdentityHashMap<>();
    private final Map<DisplayElement, Vector3f> baseTranslations = new IdentityHashMap<>();
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
        this(worldKey, definition, localPosition, RenderedComponent.single(displayElement), mapCanvas);
    }

    public WindowComponentRuntime(ResourceKey<Level> worldKey,
                                  ComponentDefinition definition,
                                  Vector3f localPosition,
                                  DisplayElement displayElement,
                                  PlayerCanvas mapCanvas,
                                  VirtualElement virtualElement) {
        this(
                worldKey,
                definition,
                localPosition,
                RenderedComponent.of(displayElement, virtualElement, null),
                mapCanvas
        );
    }

    public WindowComponentRuntime(ResourceKey<Level> worldKey,
                                  ComponentDefinition definition,
                                  Vector3f localPosition,
                                  DisplayElement displayElement,
                                  PlayerCanvas mapCanvas,
                                  VirtualElement virtualElement,
                                  DisplayElement backgroundElement) {
        this(
                worldKey,
                definition,
                localPosition,
                RenderedComponent.of(displayElement, virtualElement, backgroundElement),
                mapCanvas
        );
    }

    public WindowComponentRuntime(ResourceKey<Level> worldKey,
                                  ComponentDefinition definition,
                                  Vector3f localPosition,
                                  RenderedComponent renderedComponent,
                                  PlayerCanvas mapCanvas) {
        this.worldKey = worldKey;
        this.definition = definition;
        this.localPosition = new Vector3f(localPosition);
        this.renderedComponent = renderedComponent == null
                ? RenderedComponent.of(null, null, null)
                : renderedComponent;
        this.mapCanvas = mapCanvas;
        this.inputValue = definition instanceof TextInputComponentDefinition input ? input.initialValue() : null;
        for (DisplayElement display : this.renderedComponent.displayElements()) {
            this.baseScales.put(display, new Vector3f(display.getScale()));
            this.baseTranslations.put(display, new Vector3f(display.getTranslation()));
        }
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

    public RenderedComponent renderedComponent() {
        return this.renderedComponent;
    }

    public DisplayElement displayElement() {
        return this.renderedComponent.primaryDisplay();
    }

    public VirtualElement virtualElement() {
        return this.renderedComponent.primaryVirtual();
    }

    public DisplayElement backgroundElement() {
        return this.renderedComponent.backgroundDisplay();
    }

    public List<VirtualElement> virtualElements() {
        return this.renderedComponent.virtualElements();
    }

    public Vector3f baseScale() {
        return baseScale(displayElement());
    }

    public Vector3f baseTranslation() {
        return baseTranslation(displayElement());
    }

    public Vector3f baseScale(DisplayElement element) {
        Vector3f scale = this.baseScales.get(element);
        return scale == null ? new Vector3f(1.0f) : new Vector3f(scale);
    }

    public Vector3f baseTranslation(DisplayElement element) {
        Vector3f translation = this.baseTranslations.get(element);
        return translation == null ? new Vector3f() : new Vector3f(translation);
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
     * Interactive component origins are the lower edge of their configured box.
     * Shift the hit center upward by half the configured height so rendering and
     * raycast geometry share the same box coordinates.
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
