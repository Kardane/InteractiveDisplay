package com.interactivedisplay.internal.api;

import com.interactivedisplay.InteractiveDisplay;
import net.minecraft.resources.ResourceLocation;

final class PublicIdCodec {
    private PublicIdCodec() {
    }

    static String toInternalWindowId(ResourceLocation id) {
        if (InteractiveDisplay.MOD_ID.equals(id.getNamespace())) {
            return id.getPath();
        }
        return id.toString();
    }

    static ResourceLocation toPublicWindowId(String internalId) {
        ResourceLocation parsed = internalId.indexOf(':') >= 0
                ? ResourceLocation.tryParse(internalId)
                : ResourceLocation.fromNamespaceAndPath(InteractiveDisplay.MOD_ID, internalId);
        if (parsed == null) {
            throw new IllegalArgumentException("invalid window id: " + internalId);
        }
        return parsed;
    }
}
