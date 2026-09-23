package com.interactivedisplay.core.component;

public record ComponentPosition(
        float x,
        float y,
        float z,
        ComponentAnchor anchor,
        ComponentMargin margin
) {
    public ComponentPosition(float x, float y, float z) {
        this(x, y, z, ComponentAnchor.NONE, ComponentMargin.zero());
    }

    public ComponentPosition {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
            throw new IllegalArgumentException("component position must be finite");
        }
        anchor = anchor == null ? ComponentAnchor.NONE : anchor;
        margin = margin == null ? ComponentMargin.zero() : margin;
    }

    public boolean anchored() {
        return anchor != ComponentAnchor.NONE;
    }
}
