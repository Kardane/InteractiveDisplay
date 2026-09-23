package com.interactivedisplay.core.window;

import com.interactivedisplay.core.component.ComponentDefinition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.layout.LayoutMode;
import com.interactivedisplay.core.layout.LayoutOptions;
import com.interactivedisplay.core.positioning.WindowOffset;
import java.util.List;

public record WindowDefinition(
        String id,
        ComponentSize size,
        WindowOffset offset,
        LayoutOptions layoutOptions,
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
        this(id, size, offset, LayoutOptions.defaults(layoutMode), components, WindowTransition.none());
    }

    public WindowDefinition(
            String id,
            ComponentSize size,
            WindowOffset offset,
            LayoutMode layoutMode,
            List<ComponentDefinition> components,
            WindowTransition transition
    ) {
        this(id, size, offset, LayoutOptions.defaults(layoutMode), components, transition);
    }

    public WindowDefinition {
        layoutOptions = layoutOptions == null ? LayoutOptions.defaults(LayoutMode.ABSOLUTE) : layoutOptions;
        transition = transition == null ? WindowTransition.none() : transition;
    }

    public LayoutMode layoutMode() {
        return layoutOptions.mode();
    }
}
