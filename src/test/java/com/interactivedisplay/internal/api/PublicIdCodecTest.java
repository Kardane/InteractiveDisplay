package com.interactivedisplay.internal.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class PublicIdCodecTest {
    @Test
    void builtInNamespaceShouldAdaptToLegacyIds() {
        ResourceLocation windowId = ResourceLocation.fromNamespaceAndPath("interactivedisplay", "main_menu");
        ResourceLocation groupId = ResourceLocation.fromNamespaceAndPath("interactivedisplay", "main_group");

        assertEquals("main_menu", PublicIdCodec.toInternalWindowId(windowId));
        assertEquals(windowId, PublicIdCodec.toPublicWindowId("main_menu"));
        assertEquals("main_group", PublicIdCodec.toInternalGroupId(groupId));
        assertEquals(groupId, PublicIdCodec.toPublicGroupId("main_group"));
    }

    @Test
    void foreignNamespaceShouldStayCanonical() {
        ResourceLocation windowId = ResourceLocation.fromNamespaceAndPath("economy", "shop/main");
        ResourceLocation groupId = ResourceLocation.fromNamespaceAndPath("economy", "shop/group");

        assertEquals("economy:shop/main", PublicIdCodec.toInternalWindowId(windowId));
        assertEquals(windowId, PublicIdCodec.toPublicWindowId("economy:shop/main"));
        assertEquals("economy:shop/group", PublicIdCodec.toInternalGroupId(groupId));
        assertEquals(groupId, PublicIdCodec.toPublicGroupId("economy:shop/group"));
    }
}
