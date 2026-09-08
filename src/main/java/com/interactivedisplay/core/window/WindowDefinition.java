package com.interactivedisplay.core.window;

import com.interactivedisplay.core.component.ComponentDefinition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.layout.LayoutMode;
import com.interactivedisplay.core.positioning.WindowOffset;
import java.util.List;

public record WindowDefinition(
        String id,
        ComponentSize size,
        WindowOffset offset,
        LayoutMode layoutMode,
        List<ComponentDefinition> components,
        WindowTransition transition
) {
    public WindowDefinition(
            String id,
            ComponentSize size,
            WindowOffset offset,
            LayoutMode layoutMode,
            List<ComponentDefinition> components
    ) {
        this(id, size, offset, layoutMode, components, WindowTransition.none());
    }

    public WindowDefinition {
        transition = transition == null ? WindowTransition.none() : transition;
    }
}
