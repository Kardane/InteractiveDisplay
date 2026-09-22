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
    private static final float TEXT_PIXEL_SCALE = 0.025f;
    private static final float TEXT_LINE_HEIGHT_PIXELS = 10.0f;
    private static final float TEXT_BACKGROUND_PADDING_PIXELS = 1.0f;
    private static final float MIN_FONT_SIZE = 0.1f;

    private final ResourceKey<Level> worldKey;
    private ComponentDefinition definition;
    private Vector3f localPosition;
    private final DisplayElement displayElement;
    private final VirtualElement virtualElement;
    private final Vector3f baseScale;
    private final Vector3f baseTranslation;
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
        this(worldKey, definition, localPosition, displayElement, mapCanvas, displayElement);
    }

    public WindowComponentRuntime(ResourceKey<Level> worldKey,
                                  ComponentDefinition definition,
                                  Vector3f localPosition,
                                  DisplayElement displayElement,
                                  PlayerCanvas mapCanvas,
                                  VirtualElement virtualElement) {
        this.worldKey = worldKey;
        this.definition = definition;
        this.localPosition = new Vector3f(localPosition);
        this.displayElement = displayElement;
        this.virtualElement = virtualElement;
        this.mapCanvas = mapCanvas;
        this.inputValue = definition instanceof TextInputComponentDefinition input ? input.initialValue() : null;
        this.baseScale = displayElement == null ? new Vector3f(1.0f) : new Vector3f(displayElement.getScale());
        this.baseTranslation = displayElement == null ? new Vector3f() : new Vector3f(displayElement.getTranslation());
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

    public Vector3f baseScale() {
        return new Vector3f(this.baseScale);
    }

    public Vector3f baseTranslation() {
        return new Vector3f(this.baseTranslation);
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
        return this.virtualElement == null ? 0 : this.virtualElement.getEntityIds().size();
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
            return buttonHitWidth(button) / 2.0f;
        }
        if (this.definition instanceof TextInputComponentDefinition input) {
            return input.size().width() / 2.0f;
        }
        return 0.0f;
    }

    public float hitHalfHeight() {
        if (this.definition instanceof ButtonComponentDefinition button) {
            return buttonHitHeight(button) / 2.0f;
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

    private static float buttonHitWidth(ButtonComponentDefinition button) {
        float fontSize = normalizedFontSize(button);
        float onePixel = TEXT_PIXEL_SCALE * fontSize;
        // DisplayEntityFactory derives TextDisplay.lineWidth from the configured
        // button width. The background quad therefore spans the full configured
        // width even when the label itself is short.
        return Math.max(button.size().width(), onePixel * TEXT_BACKGROUND_PADDING_PIXELS);
    }

    private static float buttonHitHeight(ButtonComponentDefinition button) {
        float fontSize = normalizedFontSize(button);
        float lineHeight = TEXT_PIXEL_SCALE * TEXT_LINE_HEIGHT_PIXELS * fontSize;
        float availableWidth = Math.max(button.size().width(), TEXT_PIXEL_SCALE * fontSize);
        float textWidth = estimatedTextWidth(button.label(), fontSize);
        int lineCount = Math.max(1, (int) Math.ceil(textWidth / availableWidth));
        return lineHeight * lineCount;
    }

    private static float estimatedTextWidth(String label, float fontSize) {
        return estimateTextUnits(label) * TEXT_LINE_HEIGHT_PIXELS * TEXT_PIXEL_SCALE * fontSize;
    }

    private static float normalizedFontSize(ButtonComponentDefinition button) {
        return Math.max(button.fontSize(), MIN_FONT_SIZE);
    }

    private static float estimateTextUnits(String label) {
        if (label == null || label.isEmpty()) {
            return 1.0f;
        }

        float units = 0.0f;
        for (int index = 0; index < label.length();) {
            int codePoint = label.codePointAt(index);
            units += glyphUnit(codePoint);
            index += Character.charCount(codePoint);
        }
        return Math.max(1.0f, units);
    }

    private static float glyphUnit(int codePoint) {
        if (Character.isWhitespace(codePoint)) {
            return 0.35f;
        }
        if (isAsciiLetterOrDigit(codePoint)) {
            return 0.62f;
        }
        if (isAsciiPunctuation(codePoint)) {
            return 0.5f;
        }
        if (isWideGlyph(codePoint)) {
            return 1.0f;
        }
        return 0.8f;
    }

    private static boolean isAsciiLetterOrDigit(int codePoint) {
        return codePoint <= 0x7F && Character.isLetterOrDigit(codePoint);
    }

    private static boolean isAsciiPunctuation(int codePoint) {
        return codePoint <= 0x7F && !Character.isLetterOrDigit(codePoint) && !Character.isWhitespace(codePoint);
    }

    private static boolean isWideGlyph(int codePoint) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        return block == Character.UnicodeBlock.HANGUL_SYLLABLES
                || block == Character.UnicodeBlock.HANGUL_JAMO
                || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.ENCLOSED_CJK_LETTERS_AND_MONTHS
                || Character.getType(codePoint) == Character.OTHER_SYMBOL;
    }

    private record ScheduledAnimation(long generation, AnimationRuntime animation) {
    }
}
