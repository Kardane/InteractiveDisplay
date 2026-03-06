package com.interactivedisplay.core.window;

import com.interactivedisplay.core.positioning.WindowOffset;

public record WindowGroupEntry(
        String windowId,
        WindowOffset offset,
        WindowOrbit orbit
) {
}
