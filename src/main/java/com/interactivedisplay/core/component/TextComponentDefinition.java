package com.interactivedisplay.core.component;

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
        int refreshInterval
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
        this(id, position, size, visible, opacity, content, fontSize, color, alignment, lineWidth, shadow, background, 0);
    }

    public TextComponentDefinition {
        refreshInterval = Math.max(0, refreshInterval);
    }

    @Override
    public ComponentType type() {
        return ComponentType.TEXT;
    }
}
