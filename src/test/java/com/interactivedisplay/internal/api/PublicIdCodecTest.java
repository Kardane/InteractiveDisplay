package com.interactivedisplay.internal.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class PublicIdCodecTest {
    @Test
    void builtInNamespaceShouldAdaptToLegacyIds() {
        Identifier windowId = Identifier.fromNamespaceAndPath("interactivedisplay", "main_menu");
        Identifier groupId = Identifier.fromNamespaceAndPath("interactivedisplay", "main_group");

        assertEquals("main_menu", PublicIdCodec.toInternalWindowId(windowId));
        assertEquals(windowId, PublicIdCodec.toPublicWindowId("main_menu"));
        assertEquals("main_group", PublicIdCodec.toInternalGroupId(groupId));
        assertEquals(groupId, PublicIdCodec.toPublicGroupId("main_group"));
    }

    @Test
    void foreignNamespaceShouldStayCanonical() {
        Identifier windowId = Identifier.fromNamespaceAndPath("economy", "shop/main");
        Identifier groupId = Identifier.fromNamespaceAndPath("economy", "shop/group");

        assertEquals("economy:shop/main", PublicIdCodec.toInternalWindowId(windowId));
        assertEquals(windowId, PublicIdCodec.toPublicWindowId("economy:shop/main"));
        assertEquals("economy:shop/group", PublicIdCodec.toInternalGroupId(groupId));
        assertEquals(groupId, PublicIdCodec.toPublicGroupId("economy:shop/group"));
    }
}
