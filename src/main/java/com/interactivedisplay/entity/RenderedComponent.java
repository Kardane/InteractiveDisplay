package com.interactivedisplay.entity;

import eu.pb4.polymer.virtualentity.api.elements.DisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class RenderedComponent {
    private final DisplayElement primaryDisplay;
    private final VirtualElement primaryVirtual;
    private final DisplayElement backgroundDisplay;
    private final List<VirtualElement> virtualElements;
    private final List<DisplayElement> displayElements;

    private RenderedComponent(DisplayElement primaryDisplay,
                              VirtualElement primaryVirtual,
                              DisplayElement backgroundDisplay) {
        this.primaryDisplay = primaryDisplay;
        this.primaryVirtual = primaryVirtual;
        this.backgroundDisplay = backgroundDisplay;

        List<VirtualElement> virtuals = new ArrayList<>(2);
        if (backgroundDisplay != null) {
            virtuals.add(backgroundDisplay);
        }
        if (primaryVirtual != null && primaryVirtual != backgroundDisplay) {
            virtuals.add(primaryVirtual);
        }
        this.virtualElements = List.copyOf(virtuals);

        Map<DisplayElement, Boolean> seen = new IdentityHashMap<>();
        List<DisplayElement> displays = new ArrayList<>(2);
        for (VirtualElement element : this.virtualElements) {
            if (element instanceof DisplayElement display && seen.put(display, Boolean.TRUE) == null) {
                displays.add(display);
            }
        }
        if (primaryDisplay != null && seen.put(primaryDisplay, Boolean.TRUE) == null) {
            displays.add(primaryDisplay);
        }
        this.displayElements = List.copyOf(displays);
    }

    public static RenderedComponent of(DisplayElement primaryDisplay,
                                       VirtualElement primaryVirtual,
                                       DisplayElement backgroundDisplay) {
        return new RenderedComponent(primaryDisplay, primaryVirtual, backgroundDisplay);
    }

    public static RenderedComponent single(DisplayElement display) {
        return new RenderedComponent(display, display, null);
    }

    public static RenderedComponent composite(DisplayElement primaryDisplay, DisplayElement backgroundDisplay) {
        return new RenderedComponent(primaryDisplay, primaryDisplay, backgroundDisplay);
    }

    public static RenderedComponent virtualOnly(VirtualElement virtualElement) {
        return new RenderedComponent(null, virtualElement, null);
    }

    public DisplayElement primaryDisplay() {
        return this.primaryDisplay;
    }

    public VirtualElement primaryVirtual() {
        return this.primaryVirtual;
    }

    public DisplayElement backgroundDisplay() {
        return this.backgroundDisplay;
    }

    public List<VirtualElement> virtualElements() {
        return this.virtualElements;
    }

    public List<DisplayElement> displayElements() {
        return this.displayElements;
    }
}
