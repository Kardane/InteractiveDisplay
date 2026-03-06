package com.interactivedisplay.core.component;

public record ImageComponentDefinition(
        String id,
        ComponentPosition position,
        ComponentSize size,
        boolean visible,
        float opacity,
        ImageType imageType,
        String value,
        float scale,
        ImageSource source
) implements ComponentDefinition {
    @Override
    public ComponentType type() {
        return ComponentType.IMAGE;
    }
}
