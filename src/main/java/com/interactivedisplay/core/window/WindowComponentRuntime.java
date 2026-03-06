package com.interactivedisplay.core.window;

import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.ComponentDefinition;
import eu.pb4.mapcanvas.api.core.PlayerCanvas;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.joml.Vector3f;

public final class WindowComponentRuntime {
    private static final double DEFAULT_MAX_DISTANCE = 6.0D;
    private static final float MIN_BUTTON_WIDTH = 0.2f;
    private static final float MIN_BUTTON_HEIGHT = 0.2f;
    private static final float GLYPH_WIDTH_FACTOR = 0.72f;
    private static final float HORIZONTAL_PADDING_FACTOR = 0.5f;

    private final RegistryKey<World> worldKey;
    private final String signature;
    private ComponentDefinition definition;
    private Vector3f localPosition;
    private UUID displayEntityId;
    private PlayerCanvas mapCanvas;
    private boolean hovered;

    public WindowComponentRuntime(RegistryKey<World> worldKey,
                                  String signature,
                                  ComponentDefinition definition,
                                  Vector3f localPosition,
                                  UUID displayEntityId,
                                  PlayerCanvas mapCanvas) {
        this.worldKey = worldKey;
        this.signature = signature;
        this.definition = definition;
        this.localPosition = new Vector3f(localPosition);
        this.displayEntityId = displayEntityId;
        this.mapCanvas = mapCanvas;
    }

    public RegistryKey<World> worldKey() {
        return this.worldKey;
    }

    public String signature() {
        return this.signature;
    }

    public ComponentDefinition definition() {
        return this.definition;
    }

    public void redefine(ComponentDefinition definition, Vector3f localPosition) {
        this.definition = definition;
        this.localPosition = new Vector3f(localPosition);
        this.hovered = false;
    }

    public Vector3f localPosition() {
        return new Vector3f(this.localPosition);
    }

    public UUID displayEntityId() {
        return this.displayEntityId;
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

    public boolean interactive() {
        return this.definition instanceof ButtonComponentDefinition;
    }

    public float hitHalfWidth() {
        if (this.definition instanceof ButtonComponentDefinition button) {
            return buttonHitWidth(button) / 2.0f;
        }
        return 0.0f;
    }

    public float hitHalfHeight() {
        if (this.definition instanceof ButtonComponentDefinition button) {
            return buttonHitHeight(button) / 2.0f;
        }
        return 0.0f;
    }

    public double maxDistance() {
        return DEFAULT_MAX_DISTANCE;
    }

    public ComponentAction action() {
        if (this.definition instanceof ButtonComponentDefinition button) {
            return button.action();
        }
        return null;
    }

    public List<UUID> entityIds() {
        List<UUID> ids = new ArrayList<>();
        if (this.displayEntityId != null) {
            ids.add(this.displayEntityId);
        }
        return ids;
    }

    private static float buttonHitWidth(ButtonComponentDefinition button) {
        float baseHeight = Math.max(button.size().height() * Math.max(button.fontSize(), 0.1f), MIN_BUTTON_HEIGHT);
        float availableWidth = Math.max(button.size().width(), MIN_BUTTON_WIDTH);
        float textWidth = estimateTextUnits(button.label()) * baseHeight * GLYPH_WIDTH_FACTOR;
        float paddedWidth = Math.max(baseHeight, textWidth + (baseHeight * HORIZONTAL_PADDING_FACTOR));
        return Math.min(availableWidth, Math.max(MIN_BUTTON_WIDTH, paddedWidth));
    }

    private static float buttonHitHeight(ButtonComponentDefinition button) {
        float baseHeight = Math.max(button.size().height() * Math.max(button.fontSize(), 0.1f), MIN_BUTTON_HEIGHT);
        float availableWidth = buttonHitWidth(button);
        float textWidth = estimateTextUnits(button.label()) * baseHeight * GLYPH_WIDTH_FACTOR;
        int lineCount = Math.max(1, (int) Math.ceil(textWidth / Math.max(availableWidth, MIN_BUTTON_WIDTH)));
        return baseHeight * lineCount;
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
}
