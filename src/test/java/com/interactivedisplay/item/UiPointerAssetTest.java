package com.interactivedisplay.item;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UiPointerAssetTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources", "assets", "interactivedisplay");

    @Test
    void pointerItemAssetsShouldExist() {
        assertTrue(Files.exists(RESOURCES.resolve("items").resolve("pointer.json")));
        assertTrue(Files.exists(RESOURCES.resolve("models").resolve("item").resolve("pointer.json")));
        assertTrue(Files.exists(RESOURCES.resolve("textures").resolve("item").resolve("pointer.png")));
    }

    @Test
    void pointerItemDefinitionShouldReferencePointerModel() throws Exception {
        String itemDefinition = Files.readString(RESOURCES.resolve("items").resolve("pointer.json"));

        assertTrue(itemDefinition.contains("interactivedisplay:item/pointer"));
    }

    @Test
    void pointerLanguageKeyShouldExist() throws Exception {
        String lang = Files.readString(RESOURCES.resolve("lang").resolve("ko_kr.json"));

        assertTrue(lang.contains("item.interactivedisplay.pointer"));
    }
}
