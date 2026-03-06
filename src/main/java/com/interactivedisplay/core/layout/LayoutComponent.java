package com.interactivedisplay.core.layout;

import com.interactivedisplay.core.component.ComponentDefinition;
import org.joml.Vector3f;

public record LayoutComponent(ComponentDefinition definition, Vector3f localPosition) {
}
