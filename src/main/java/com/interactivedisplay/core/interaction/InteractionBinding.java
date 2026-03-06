package com.interactivedisplay.core.interaction;

import com.interactivedisplay.core.component.ComponentAction;
import java.util.UUID;

public record InteractionBinding(
        UUID owner,
        String windowId,
        String componentId,
        ComponentAction action
) {
}
