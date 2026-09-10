package com.interactivedisplay.internal.api;

import com.interactivedisplay.InteractiveDisplay;
import net.minecraft.resources.ResourceLocation;

final class PublicIdCodec {
    private PublicIdCodec() {
    }

    static String toInternalWindowId(ResourceLocation id) {
        return toInternalId(id);
    }

    static ResourceLocation toPublicWindowId(String internalId) {
        return toPublicId(internalId, "window");
    }

    static String toInternalGroupId(ResourceLocation id) {
        return toInternalId(id);
    }

    static ResourceLocation toPublicGroupId(String internalId) {
        return toPublicId(internalId, "group");
    }

    private static String toInternalId(ResourceLocation id) {
        if (InteractiveDisplay.MOD_ID.equals(id.getNamespace())) {
            return id.getPath();
        }
        return id.toString();
    }

    private static ResourceLocation toPublicId(String internalId, String kind) {
        ResourceLocation parsed = internalId.indexOf(':') >= 0
                ? ResourceLocation.tryParse(internalId)
                : ResourceLocation.fromNamespaceAndPath(InteractiveDisplay.MOD_ID, internalId);
        if (parsed == null) {
            throw new IllegalArgumentException("invalid " + kind + " id: " + internalId);
        }
        return parsed;
    }
}
