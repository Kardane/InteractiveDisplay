package com.interactivedisplay.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.core.interaction.CallbackRegistry;
import com.interactivedisplay.core.interaction.CommandWhitelist;
import com.interactivedisplay.core.layout.MeditateLayoutEngine;
import com.interactivedisplay.core.positioning.CoordinateTransformer;
import com.interactivedisplay.core.positioning.WindowPositionTracker;
import com.interactivedisplay.core.window.ReloadWindowResult;
import com.interactivedisplay.core.window.RemoveWindowResult;
import com.interactivedisplay.core.window.WindowManager;
import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.debug.DebugRecorder;
import com.interactivedisplay.entity.DisplayEntityFactory;
import com.interactivedisplay.schema.SchemaLoader;
import com.interactivedisplay.schema.SchemaValidator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowManagerReloadTest {
    @Test
    void reloadAllShouldKeepPreviousDefinitionsOnFailure(@TempDir Path tempDir) throws Exception {
        DebugRecorder debugRecorder = new DebugRecorder(20);
        Path windows = tempDir.resolve("interactivedisplay").resolve("windows");
        Files.createDirectories(windows);

        Path target = windows.resolve("menu.json");
        Files.writeString(target, """
                {
                  "id": "main_menu",
                  "size": {"width": 3.0, "height": 2.0},
                  "components": [
                    {
                      "id": "title",
                      "type": "text",
                      "position": {"x": 0.0, "y": 0.0, "z": 0.0},
                      "width": 1.0,
                      "height": 0.3,
                      "content": "ok"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        CoordinateTransformer transformer = new CoordinateTransformer();
        WindowManager manager = new WindowManager(
                null,
                new SchemaLoader(tempDir, new SchemaValidator(), debugRecorder),
                new MeditateLayoutEngine(),
                transformer,
                new WindowPositionTracker(transformer),
                new DisplayEntityFactory(debugRecorder),
                debugRecorder,
                new CommandWhitelist(tempDir),
                new CallbackRegistry()
        );

        ReloadWindowResult first = manager.reloadAll();
        assertEquals(true, first.success());
        assertTrue(manager.loadedWindowIds().contains("main_menu"));

        Files.writeString(target, """
                {
                  "id": "main_menu",
                  "size": {"width": 3.0, "height": 2.0},
                  "components": [
                    {
                      "id": "btn",
                      "type": "button",
                      "position": {"x": 0.0, "y": 0.0, "z": 0.0},
                      "label": "bad",
                      "action": {"type": "callback"}
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        ReloadWindowResult second = manager.reloadAll();
        assertEquals(false, second.success());
        assertEquals(DebugReason.SCHEMA_VALIDATION_FAILED, second.reasonCode());
        assertTrue(manager.loadedWindowIds().contains("main_menu"));
        assertTrue(manager.brokenWindowIds().contains("main_menu"));
    }

    @Test
    void removeWindowShouldReturnNoActiveWindowReason(@TempDir Path tempDir) {
        DebugRecorder debugRecorder = new DebugRecorder(10);
        CoordinateTransformer transformer = new CoordinateTransformer();
        WindowManager manager = new WindowManager(
                null,
                new SchemaLoader(tempDir, new SchemaValidator(), debugRecorder),
                new MeditateLayoutEngine(),
                transformer,
                new WindowPositionTracker(transformer),
                new DisplayEntityFactory(debugRecorder),
                debugRecorder,
                new CommandWhitelist(tempDir),
                new CallbackRegistry()
        );

        RemoveWindowResult result = manager.removeWindow(java.util.UUID.randomUUID(), "missing");
        assertEquals(false, result.success());
        assertEquals(DebugReason.NO_ACTIVE_WINDOW, result.reasonCode());
    }

    @Test
    void reloadOneShouldKeepTargetDefinitionWhileUpdatingBrokenGroupIds(@TempDir Path tempDir) throws Exception {
        DebugRecorder debugRecorder = new DebugRecorder(20);
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
                      "content": "main"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(windows.resolve("gallery.json"), """
                {
                  "id": "gallery",
                  "size": {"width": 3.0, "height": 2.0},
                  "components": [
                    {
                      "id": "title",
                      "type": "text",
                      "position": {"x": 0.0, "y": 0.0, "z": 0.0},
                      "content": "before"
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
                      "windowId": "main_menu"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        CoordinateTransformer transformer = new CoordinateTransformer();
        WindowManager manager = new WindowManager(
                null,
                new SchemaLoader(tempDir, new SchemaValidator(), debugRecorder),
                new MeditateLayoutEngine(),
                transformer,
                new WindowPositionTracker(transformer),
                new DisplayEntityFactory(debugRecorder),
                debugRecorder,
                new CommandWhitelist(tempDir),
                new CallbackRegistry()
        );

        ReloadWindowResult first = manager.reloadAll();
        assertEquals(true, first.success());
        assertTrue(manager.loadedWindowIds().contains("gallery"));
        assertTrue(manager.loadedGroupIds().contains("menu_group"));

        Files.writeString(windows.resolve("gallery.json"), """
                {
                  "id": "gallery",
                  "size": {"width": 3.0, "height": 2.0},
                  "components": [
                    {
                      "id": "title",
                      "type": "text",
                      "position": {"x": 0.0, "y": 0.0, "z": 0.0},
                      "content": "after"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(groups.resolve("menu_group.json"), """
                {
                  "id": "menu_group",
                  "defaultMode": "player_fixed",
                  "windows": [
                    {
                      "windowId": "main_menu"
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        ReloadWindowResult second = manager.reloadOne("gallery");

        assertEquals(false, second.success());
        assertEquals(DebugReason.SCHEMA_VALIDATION_FAILED, second.reasonCode());
        assertTrue(manager.loadedWindowIds().contains("gallery"));
        assertTrue(manager.brokenGroupIds().contains("menu_group"));
    }
}
