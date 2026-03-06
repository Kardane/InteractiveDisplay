package com.interactivedisplay.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.debug.DebugRecorder;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaLoaderDebugTest {
    @Test
    void defaultWindowShouldContainBackgroundTitleContentAndClose(@TempDir Path tempDir) throws Exception {
        DebugRecorder recorder = new DebugRecorder(10);
        SchemaLoader loader = new SchemaLoader(tempDir, new SchemaValidator(), recorder);

        SchemaLoader.LoadResult result = loader.loadAll();
        JsonObject root = JsonParser.parseString(Files.readString(
                tempDir.resolve("interactivedisplay").resolve("windows").resolve("main_menu.json"),
                StandardCharsets.UTF_8
        )).getAsJsonObject();
        JsonArray components = root.getAsJsonArray("components");

        assertFalse(result.hasErrors());
        assertEquals(4, components.size());
        assertEquals("background", components.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("panel", components.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("title", components.get(1).getAsJsonObject().get("id").getAsString());
        assertEquals("content", components.get(2).getAsJsonObject().get("id").getAsString());
        assertEquals("close", components.get(3).getAsJsonObject().get("id").getAsString());
        assertTrue(components.get(0).getAsJsonObject().get("backgroundColor").getAsString().startsWith("#88"));
        assertEquals(0.7f, components.get(1).getAsJsonObject().get("fontSize").getAsFloat());
        assertEquals(0.5f, components.get(2).getAsJsonObject().get("fontSize").getAsFloat());
        assertTrue(components.get(1).getAsJsonObject().get("position").getAsJsonObject().get("y").getAsFloat()
                > components.get(2).getAsJsonObject().get("position").getAsJsonObject().get("y").getAsFloat());
        assertEquals("#CC992222", components.get(3).getAsJsonObject().get("backgroundColor").getAsString());
        assertEquals("☒", components.get(3).getAsJsonObject().get("label").getAsString());
        assertEquals(1.0f, components.get(3).getAsJsonObject().get("fontSize").getAsFloat());
        assertEquals("minecraft:ui.button.click", components.get(3).getAsJsonObject().get("clickSound").getAsString());
        assertEquals(0.45f, components.get(3).getAsJsonObject().getAsJsonObject("size").get("width").getAsFloat());
    }

    @Test
    void invalidSchemaShouldBeRecorded(@TempDir Path tempDir) throws Exception {
        DebugRecorder recorder = new DebugRecorder(10);
        Path windows = tempDir.resolve("interactivedisplay").resolve("windows");
        Files.createDirectories(windows);
        Files.writeString(windows.resolve("bad.json"), """
                {
                  "id": "bad",
                  "size": {"width": 3.0, "height": 2.0},
                  "components": [
                    {
                      "id": "btn",
                      "type": "button",
                      "position": {"x": 0.0, "y": 0.0, "z": 0.0},
                      "label": "bad",
                      "action": {"type": "open_window"}
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        SchemaLoader loader = new SchemaLoader(tempDir, new SchemaValidator(), recorder);
        SchemaLoader.LoadResult result = loader.loadAll();

        assertTrue(result.hasErrors());
        assertEquals(DebugReason.SCHEMA_VALIDATION_FAILED, recorder.latestFailure(null, null).orElseThrow().reasonCode());
    }

    @Test
    void localMapWindowShouldLoadFromImagesDirectory(@TempDir Path tempDir) throws Exception {
        DebugRecorder recorder = new DebugRecorder(10);
        Path root = tempDir.resolve("interactivedisplay");
        Path windows = root.resolve("windows");
        Path images = root.resolve("images");
        Files.createDirectories(windows);
        Files.createDirectories(images);

        Files.writeString(windows.resolve("gallery.json"), """
                {
                  "id": "gallery",
                  "size": {"width": 4.0, "height": 2.5},
                  "components": [
                    {
                      "id": "local_map",
                      "type": "image",
                      "position": {"x": 0.0, "y": 0.0, "z": 0.0},
                      "size": {"width": 1.0, "height": 1.0},
                      "imageType": "MAP",
                      "value": "sample_local.png"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, Color.WHITE.getRGB());
        ImageIO.write(image, "png", images.resolve("sample_local.png").toFile());

        SchemaLoader loader = new SchemaLoader(tempDir, new SchemaValidator(), recorder);
        SchemaLoader.LoadResult result = loader.loadAll();

        assertFalse(result.hasErrors());
        assertTrue(result.definitions().containsKey("gallery"));
    }

    @Test
    void textComponentWithoutFontSizeShouldUseSmallerDefault(@TempDir Path tempDir) throws Exception {
        DebugRecorder recorder = new DebugRecorder(10);
        Path windows = tempDir.resolve("interactivedisplay").resolve("windows");
        Files.createDirectories(windows);
        Files.writeString(windows.resolve("text.json"), """
                {
                  "id": "text_only",
                  "size": {"width": 3.0, "height": 2.0},
                  "components": [
                    {
                      "id": "body",
                      "type": "text",
                      "position": {"x": 0.0, "y": 0.0, "z": 0.0},
                      "content": "hello"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        SchemaLoader loader = new SchemaLoader(tempDir, new SchemaValidator(), recorder);
        SchemaLoader.LoadResult result = loader.loadAll();
        var text = (com.interactivedisplay.core.component.TextComponentDefinition) result.definitions()
                .get("text_only")
                .components()
                .getFirst();

        assertFalse(result.hasErrors());
        assertEquals(0.5f, text.fontSize());
        assertEquals("#00000000", text.background());
    }

    @Test
    void buttonComponentShouldSupportExplicitFontSizeAndClickSound(@TempDir Path tempDir) throws Exception {
        DebugRecorder recorder = new DebugRecorder(10);
        Path windows = tempDir.resolve("interactivedisplay").resolve("windows");
        Files.createDirectories(windows);
        Files.writeString(windows.resolve("button.json"), """
                {
                  "id": "button_only",
                  "size": {"width": 3.0, "height": 2.0},
                  "components": [
                    {
                      "id": "close",
                      "type": "button",
                      "position": {"x": 0.0, "y": 0.0, "z": 0.0},
                      "size": {"width": 1.0, "height": 0.35},
                      "label": "닫기",
                      "fontSize": 0.7,
                      "clickSound": "minecraft:ui.button.click",
                      "action": {"type": "close_window"}
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        SchemaLoader loader = new SchemaLoader(tempDir, new SchemaValidator(), recorder);
        SchemaLoader.LoadResult result = loader.loadAll();
        var button = (com.interactivedisplay.core.component.ButtonComponentDefinition) result.definitions()
                .get("button_only")
                .components()
                .getFirst();

        assertFalse(result.hasErrors());
        assertEquals(0.7f, button.fontSize());
        assertEquals("minecraft:ui.button.click", button.clickSound());
    }

    @Test
    void groupDefinitionShouldLoadWithOffsetAndOrbit(@TempDir Path tempDir) throws Exception {
        DebugRecorder recorder = new DebugRecorder(10);
        Path root = tempDir.resolve("interactivedisplay");
        Path windows = root.resolve("windows");
        Path groups = root.resolve("groups");
        Files.createDirectories(windows);
        Files.createDirectories(groups);

        Files.writeString(windows.resolve("main_menu.json"), """
                {
                  "id": "main_menu",
                  "size": {"width": 3.0, "height": 2.0},
                  "components": [
                    {
                      "id": "title",
                      "type": "text",
                      "position": {"x": 0.0, "y": 0.0, "z": 0.0},
                      "content": "hello"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(windows.resolve("settings.json"), """
                {
                  "id": "settings",
                  "size": {"width": 3.0, "height": 2.0},
                  "components": [
                    {
                      "id": "title",
                      "type": "text",
                      "position": {"x": 0.0, "y": 0.0, "z": 0.0},
                      "content": "settings"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(groups.resolve("menu_group.json"), """
                {
                  "id": "menu_group",
                  "initialWindowId": "main_menu",
                  "defaultMode": "player_fixed",
                  "windows": [
                    {
                      "windowId": "main_menu",
                      "offset": {"forward": 2.0, "horizontal": 0.0, "vertical": 0.5},
                      "orbit": {"yaw": 0.0, "pitch": 0.0}
                    },
                    {
                      "windowId": "settings",
                      "offset": {"forward": 2.0, "horizontal": 1.0, "vertical": 0.5},
                      "orbit": {"yaw": 90.0, "pitch": 0.0}
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        SchemaLoader loader = new SchemaLoader(tempDir, new SchemaValidator(), recorder);
        SchemaLoader.LoadResult result = loader.loadAll();
        var group = result.groups().get("menu_group");

        assertFalse(result.hasErrors());
        assertTrue(result.groups().containsKey("menu_group"));
        assertEquals("main_menu", group.initialWindowId());
        assertEquals(com.interactivedisplay.core.positioning.PositionMode.PLAYER_FIXED, group.defaultMode());
        assertEquals(2, group.windows().size());
        assertEquals("settings", group.windows().get(1).windowId());
        assertEquals(1.0f, group.windows().get(1).offset().horizontal());
        assertEquals(90.0f, group.windows().get(1).orbit().yaw());
    }

    @Test
    void buttonActionShouldSupportModeSwitchTypes(@TempDir Path tempDir) throws Exception {
        DebugRecorder recorder = new DebugRecorder(10);
        Path windows = tempDir.resolve("interactivedisplay").resolve("windows");
        Files.createDirectories(windows);
        Files.writeString(windows.resolve("button.json"), """
                {
                  "id": "button_only",
                  "size": {"width": 3.0, "height": 2.0},
                  "components": [
                    {
                      "id": "switch_fixed",
                      "type": "button",
                      "position": {"x": 0.0, "y": 0.0, "z": 0.0},
                      "size": {"width": 1.0, "height": 0.35},
                      "label": "고정",
                      "action": {"type": "switch_mode_fixed"}
                    },
                    {
                      "id": "switch_player_fixed",
                      "type": "button",
                      "position": {"x": 0.0, "y": -0.4, "z": 0.0},
                      "size": {"width": 1.0, "height": 0.35},
                      "label": "플레이어 고정",
                      "action": {"type": "switch_mode_player_fixed"}
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        SchemaLoader loader = new SchemaLoader(tempDir, new SchemaValidator(), recorder);
        SchemaLoader.LoadResult result = loader.loadAll();

        assertFalse(result.hasErrors());
        assertEquals(
                com.interactivedisplay.core.component.ComponentActionType.SWITCH_MODE_FIXED,
                ((com.interactivedisplay.core.component.ButtonComponentDefinition) result.definitions().get("button_only").components().get(0)).action().type()
        );
        assertEquals(
                com.interactivedisplay.core.component.ComponentActionType.SWITCH_MODE_PLAYER_FIXED,
                ((com.interactivedisplay.core.component.ButtonComponentDefinition) result.definitions().get("button_only").components().get(1)).action().type()
        );
    }
}
