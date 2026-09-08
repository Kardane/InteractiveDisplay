package com.interactivedisplay.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.debug.DebugRecorder;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaLoaderDebugTest {
    @Test
    void emptyConfigShouldCopyAllSchemaDefaultAssets(@TempDir Path tempDir) throws Exception {
        SchemaLoader.LoadResult result = new SchemaLoader(
                tempDir,
                new SchemaValidator(),
                new DebugRecorder(10)
        ).loadAll();
        Path configRoot = tempDir.resolve("interactivedisplay");

        assertFalse(result.hasErrors());
        for (String relativePath : new String[]{
                "windows/main_menu.yaml",
                "windows/main_menu2.yaml",
                "windows/gallery.yaml",
                "windows/gallery_remote.example.yaml.disabled",
                "groups/menu_group.yaml",
                "images/sample_local.png"
        }) {
            assertTrue(Files.exists(configRoot.resolve(relativePath)), relativePath);
        }
    }

    @Test
    void defaultWindowShouldContainBackgroundTitleContentAndClose(@TempDir Path tempDir) throws Exception {
        DebugRecorder recorder = new DebugRecorder(10);
        SchemaLoader loader = new SchemaLoader(tempDir, new SchemaValidator(), recorder);

        SchemaLoader.LoadResult result = loader.loadAll();
        JsonNode root = new ConfigDocumentLoader().load(
                tempDir.resolve("interactivedisplay").resolve("windows").resolve("main_menu.yaml")
        );
        JsonNode components = root.get("components");

        assertFalse(result.hasErrors());
        assertEquals(4, components.size());
        assertEquals("background", components.get(0).get("id").textValue());
        assertEquals("panel", components.get(0).get("type").textValue());
        assertEquals("title", components.get(1).get("id").textValue());
        assertEquals("content", components.get(2).get("id").textValue());
        assertEquals("close", components.get(3).get("id").textValue());
        assertTrue(components.get(0).get("backgroundColor").textValue().startsWith("#88"));
        assertEquals(0.7f, components.get(1).get("fontSize").floatValue());
        assertEquals(0.5f, components.get(2).get("fontSize").floatValue());
        assertTrue(components.get(1).get("position").get("y").floatValue()
                > components.get(2).get("position").get("y").floatValue());
        assertEquals("#CC992222", components.get(3).get("backgroundColor").textValue());
        assertEquals("☒", components.get(3).get("label").textValue());
        assertEquals(1.0f, components.get(3).get("fontSize").floatValue());
        assertEquals("minecraft:ui.button.click", components.get(3).get("clickSound").textValue());
        assertEquals(0.45f, components.get(3).get("size").get("width").floatValue());
    }

    @Test
    void legacyJsonAndYmlShouldBeIgnoredByYamlOnlyDiscovery(@TempDir Path tempDir) throws Exception {
        Path windows = tempDir.resolve("interactivedisplay").resolve("windows");
        Path groups = tempDir.resolve("interactivedisplay").resolve("groups");
        Files.createDirectories(windows);
        Files.createDirectories(groups);
        Files.writeString(windows.resolve("legacy.json"), "{ not a supported config", StandardCharsets.UTF_8);
        Files.writeString(windows.resolve("legacy.yml"), "id: legacy_yml\n", StandardCharsets.UTF_8);
        Files.writeString(groups.resolve("legacy.json"), "{ not a supported config", StandardCharsets.UTF_8);
        Files.writeString(groups.resolve("legacy.yml"), "id: legacy_group_yml\n", StandardCharsets.UTF_8);

        SchemaLoader loader = new SchemaLoader(
                tempDir,
                new SchemaValidator(),
                new DebugRecorder(10)
        );
        SchemaLoader.LoadResult result = loader.loadAll();

        assertFalse(result.hasErrors());
        assertFalse(result.definitions().containsKey("legacy_json"));
        assertFalse(result.definitions().containsKey("legacy_yml"));
        assertFalse(result.groups().containsKey("legacy_group_json"));
        assertFalse(result.groups().containsKey("legacy_group_yml"));
        assertFalse(loader.discoverWindowIds().contains("legacy"));
        assertFalse(loader.discoverWindowIds().contains("legacy_yml"));
        assertFalse(loader.discoverGroupIds().contains("legacy"));
        assertFalse(loader.discoverGroupIds().contains("legacy_group_yml"));
    }

    @Test
    void yamlResourceFixturesShouldLoadThroughSchemaLoader(@TempDir Path tempDir) throws Exception {
        Path windows = tempDir.resolve("interactivedisplay").resolve("windows");
        Path groups = tempDir.resolve("interactivedisplay").resolve("groups");
        Files.createDirectories(windows);
        Files.createDirectories(groups);
        copyFixture("/interactivedisplay/windows/valid.yaml", windows.resolve("valid.yaml"));
        copyFixture("/interactivedisplay/groups/valid.yaml", groups.resolve("valid.yaml"));

        SchemaLoader.LoadResult result = new SchemaLoader(
                tempDir,
                new SchemaValidator(),
                new DebugRecorder(10)
        ).loadAll();

        assertFalse(result.hasErrors());
        assertTrue(result.definitions().containsKey("parser_window"));
        assertTrue(result.groups().containsKey("parser_group"));
    }

    @Test
    void existingDefaultYamlShouldNotBeOverwritten(@TempDir Path tempDir) throws Exception {
        Path windows = tempDir.resolve("interactivedisplay").resolve("windows");
        Files.createDirectories(windows);
        String existing = """
                id: preserved
                size:
                  width: 2.0
                  height: 1.0
                components: []
                """;
        Path existingPath = windows.resolve("main_menu.yaml");
        Files.writeString(existingPath, existing, StandardCharsets.UTF_8);

        SchemaLoader.LoadResult result = new SchemaLoader(
                tempDir,
                new SchemaValidator(),
                new DebugRecorder(10)
        ).loadAll();

        assertFalse(result.hasErrors());
        assertEquals(existing, Files.readString(existingPath, StandardCharsets.UTF_8));
        assertTrue(result.definitions().containsKey("preserved"));
        assertFalse(result.definitions().containsKey("main_menu"));
    }

    @Test
    void invalidSchemaShouldBeRecorded(@TempDir Path tempDir) throws Exception {
        DebugRecorder recorder = new DebugRecorder(10);
        Path windows = tempDir.resolve("interactivedisplay").resolve("windows");
        Files.createDirectories(windows);
        copyFixture("/interactivedisplay/windows/invalid.yaml", windows.resolve("invalid.yaml"));

        SchemaLoader loader = new SchemaLoader(tempDir, new SchemaValidator(), recorder);
        SchemaLoader.LoadResult result = loader.loadAll();

        assertTrue(result.hasErrors());
        assertTrue(result.brokenWindowIds().contains("invalid_window"));
        assertEquals(DebugReason.SCHEMA_VALIDATION_FAILED, recorder.latestFailure(null, null).orElseThrow().reasonCode());
    }

    @Test
    void invalidYamlShouldUseYamlStemForBrokenId(@TempDir Path tempDir) throws Exception {
        Path windows = tempDir.resolve("interactivedisplay").resolve("windows");
        Files.createDirectories(windows);
        Files.writeString(windows.resolve("broken_syntax.yaml"), "id: [", StandardCharsets.UTF_8);

        SchemaLoader.LoadResult result = new SchemaLoader(
                tempDir,
                new SchemaValidator(),
                new DebugRecorder(10)
        ).loadAll();

        assertTrue(result.hasErrors());
        assertTrue(result.brokenWindowIds().contains("broken_syntax"));
    }

    @Test
    void invalidGroupShouldBeTrackedAsBroken(@TempDir Path tempDir) throws Exception {
        DebugRecorder recorder = new DebugRecorder(10);
        Path groups = tempDir.resolve("interactivedisplay").resolve("groups");
        Files.createDirectories(groups);
        copyFixture("/interactivedisplay/groups/invalid.yaml", groups.resolve("invalid.yaml"));

        SchemaLoader.LoadResult result = new SchemaLoader(tempDir, new SchemaValidator(), recorder).loadAll();

        assertTrue(result.hasErrors());
        assertTrue(result.brokenGroupIds().contains("invalid_group"));
    }

    @Test
    void localMapWindowShouldLoadFromImagesDirectory(@TempDir Path tempDir) throws Exception {
        DebugRecorder recorder = new DebugRecorder(10);
        Path root = tempDir.resolve("interactivedisplay");
        Path windows = root.resolve("windows");
        Path images = root.resolve("images");
        Files.createDirectories(windows);
        Files.createDirectories(images);

        Files.writeString(windows.resolve("gallery.yaml"), """
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
        Files.writeString(windows.resolve("text.yaml"), """
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
        Files.writeString(windows.resolve("button.yaml"), """
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

        Files.writeString(windows.resolve("main_menu.yaml"), """
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
        Files.writeString(windows.resolve("settings.yaml"), """
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
        Files.writeString(groups.resolve("menu_group.yaml"), """
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
        Files.writeString(windows.resolve("button.yaml"), """
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

    @Test
    void buttonActionsShouldSupportPlacementTrackingAndCommandPermissionLevel(@TempDir Path tempDir) throws Exception {
        DebugRecorder recorder = new DebugRecorder(10);
        Path windows = tempDir.resolve("interactivedisplay").resolve("windows");
        Files.createDirectories(windows);
        Files.writeString(windows.resolve("button.yaml"), """
                {
                  "id": "button_only",
                  "size": {"width": 3.0, "height": 2.0},
                  "components": [
                    {
                      "id": "track",
                      "type": "button",
                      "position": {"x": 0.0, "y": 0.0, "z": 0.0},
                      "size": {"width": 1.0, "height": 0.35},
                      "label": "배치",
                      "action": {"type": "toggle_placement_tracking"}
                    },
                    {
                      "id": "run",
                      "type": "button",
                      "position": {"x": 0.0, "y": -0.4, "z": 0.0},
                      "size": {"width": 1.0, "height": 0.35},
                      "label": "실행",
                      "action": {"type": "run_command", "command": "say hi", "permissionLevel": 2}
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        SchemaLoader loader = new SchemaLoader(tempDir, new SchemaValidator(), recorder);
        SchemaLoader.LoadResult result = loader.loadAll();
        var first = (com.interactivedisplay.core.component.ButtonComponentDefinition) result.definitions().get("button_only").components().get(0);
        var second = (com.interactivedisplay.core.component.ButtonComponentDefinition) result.definitions().get("button_only").components().get(1);

        assertFalse(result.hasErrors());
        assertEquals(com.interactivedisplay.core.component.ComponentActionType.TOGGLE_PLACEMENT_TRACKING, first.action().type());
        assertEquals(com.interactivedisplay.core.component.ComponentActionType.RUN_COMMAND, second.action().type());
        assertEquals(2, second.action().permissionLevel());
    }

    private static void copyFixture(String resourceName, Path target) throws Exception {
        try (InputStream input = Objects.requireNonNull(SchemaLoaderDebugTest.class.getResourceAsStream(resourceName))) {
            Files.copy(input, target);
        }
    }
}
