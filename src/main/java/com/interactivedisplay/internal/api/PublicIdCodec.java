package com.interactivedisplay.internal.api;

import com.interactivedisplay.InteractiveDisplay;
import net.minecraft.resources.Identifier;

final class PublicIdCodec {
    private PublicIdCodec() {
    }

    static String toInternalWindowId(Identifier id) {
        return toInternalId(id);
    }

    static Identifier toPublicWindowId(String internalId) {
        return toPublicId(internalId, "window");
    }

    static String toInternalGroupId(Identifier id) {
        return toInternalId(id);
    }

    static Identifier toPublicGroupId(String internalId) {
        return toPublicId(internalId, "group");
    }

    private static String toInternalId(Identifier id) {
        if (InteractiveDisplay.MOD_ID.equals(id.getNamespace())) {
            return id.getPath();
        }
        return id.toString();
    }

    private static Identifier toPublicId(String internalId, String kind) {
        Identifier parsed = internalId.indexOf(':') >= 0
                ? Identifier.tryParse(internalId)
                : Identifier.fromNamespaceAndPath(InteractiveDisplay.MOD_ID, internalId);
        if (parsed == null) {
            throw new IllegalArgumentException("invalid " + kind + " id: " + internalId);
        }
        return parsed;
    }
}
