package com.interactivedisplay.core.component;

import com.interactivedisplay.core.animation.AnimationDefinition;
import java.util.List;

public record TextComponentDefinition(
        String id,
        ComponentPosition position,
        ComponentSize size,
        boolean visible,
        float opacity,
        String content,
        float fontSize,
        String color,
        String alignment,
        int lineWidth,
        boolean shadow,
        String background,
        int refreshInterval,
        List<AnimationDefinition> animations
) implements ComponentDefinition {
    public TextComponentDefinition(
            String id,
            ComponentPosition position,
            ComponentSize size,
            boolean visible,
            float opacity,
            String content,
            float fontSize,
            String color,
            String alignment,
            int lineWidth,
            boolean shadow,
            String background
    ) {
        this(id, position, size, visible, opacity, content, fontSize, color, alignment, lineWidth, shadow, background, 0, List.of());
    }

    public TextComponentDefinition(
            String id,
            ComponentPosition position,
            ComponentSize size,
            boolean visible,
            float opacity,
            String content,
            float fontSize,
            String color,
            String alignment,
            int lineWidth,
            boolean shadow,
            String background,
            int refreshInterval
    ) {
        this(id, position, size, visible, opacity, content, fontSize, color, alignment, lineWidth, shadow, background, refreshInterval, List.of());
    }

    public TextComponentDefinition {
        refreshInterval = Math.max(0, refreshInterval);
        animations = animations == null ? List.of() : List.copyOf(animations);
    }

    @Override
    public ComponentType type() {
        return ComponentType.TEXT;
    }
}
