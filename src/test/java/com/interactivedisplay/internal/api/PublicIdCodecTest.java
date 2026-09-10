package com.interactivedisplay.internal.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class PublicIdCodecTest {
    @Test
    void builtInNamespaceShouldAdaptToLegacyWindowId() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("interactivedisplay", "main_menu");

        assertEquals("main_menu", PublicIdCodec.toInternalWindowId(id));
        assertEquals(id, PublicIdCodec.toPublicWindowId("main_menu"));
    }

    @Test
    void foreignNamespaceShouldStayCanonical() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("economy", "shop/main");

        assertEquals("economy:shop/main", PublicIdCodec.toInternalWindowId(id));
        assertEquals(id, PublicIdCodec.toPublicWindowId("economy:shop/main"));
    }
}
