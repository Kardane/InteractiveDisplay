package com.interactivedisplay.core.component;

public interface ComponentDefinition {
    String id();

    ComponentType type();

    ComponentPosition position();

    ComponentSize size();

    boolean visible();

    float opacity();
}
