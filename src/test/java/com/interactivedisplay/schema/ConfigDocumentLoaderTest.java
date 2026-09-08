package com.interactivedisplay.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigDocumentLoaderTest {
    private final ConfigDocumentLoader loader = new ConfigDocumentLoader();

    @Test
    void shouldLoadUtf8YamlObjectAndPreserveYamlValues(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("window.yaml");
        Files.writeString(path, """
                id: main_menu
                content: |-
                  첫 번째 줄
                  두 번째 줄
                color: "#FFFFFF"
                value: "https://example.com/image.png"
                identifier: "minecraft:diamond"
                """, StandardCharsets.UTF_8);

        JsonNode root = loader.load(path);

        assertTrue(root.isObject());
        assertEquals("main_menu", root.path("id").textValue());
        assertEquals("첫 번째 줄\n두 번째 줄", root.path("content").textValue());
        assertEquals("#FFFFFF", root.path("color").textValue());
        assertEquals("https://example.com/image.png", root.path("value").textValue());
        assertEquals("minecraft:diamond", root.path("identifier").textValue());
    }

    @Test
    void shouldExposeScalarAndArrayRoots(@TempDir Path tempDir) throws Exception {
        Path scalar = tempDir.resolve("scalar.yaml");
        Path array = tempDir.resolve("array.yaml");
        Files.writeString(scalar, "hello\n", StandardCharsets.UTF_8);
        Files.writeString(array, "- one\n- two\n", StandardCharsets.UTF_8);

        assertEquals("hello", loader.load(scalar).textValue());
        assertEquals(2, loader.load(array).size());
    }

    @Test
    void shouldRejectEmptyDocument(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("empty.yaml");
        Files.writeString(path, "\n# comment only\n", StandardCharsets.UTF_8);

        IOException exception = assertThrows(IOException.class, () -> loader.load(path));

        assertTrue(exception.getMessage().contains("must not be empty"));
    }

    @Test
    void shouldRejectInvalidYamlSyntax(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("invalid.yaml");
        Files.writeString(path, "id: [unterminated\n", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> loader.load(path));
    }

    @Test
    void shouldRejectDuplicateKeys(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("duplicate.yaml");
        Files.writeString(path, "id: one\nid: two\n", StandardCharsets.UTF_8);

        IOException exception = assertThrows(IOException.class, () -> loader.load(path));

        assertTrue(exception.getMessage().toLowerCase().contains("duplicate"));
    }

    @Test
    void shouldRejectMultipleDocuments(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("multiple.yaml");
        Files.writeString(path, "---\nid: one\n---\nid: two\n", StandardCharsets.UTF_8);

        IOException exception = assertThrows(IOException.class, () -> loader.load(path));

        assertTrue(exception.getMessage().contains("multiple YAML documents"));
    }

    @Test
    void shouldRejectCustomTags(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("tagged.yaml");
        Files.writeString(path, "id: !custom value\n", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> loader.load(path));
    }
}
