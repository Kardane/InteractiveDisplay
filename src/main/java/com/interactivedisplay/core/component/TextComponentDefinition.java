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
        String background
) implements ComponentDefinition {
    @Override
    public ComponentType type() {
        return ComponentType.TEXT;
    }
}
