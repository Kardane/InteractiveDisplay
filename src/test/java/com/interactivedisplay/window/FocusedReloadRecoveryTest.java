package com.interactivedisplay.window;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.core.interaction.CallbackRegistry;
import com.interactivedisplay.core.interaction.CommandWhitelist;
import com.interactivedisplay.core.layout.MeditateLayoutEngine;
import com.interactivedisplay.core.positioning.CoordinateTransformer;
import com.interactivedisplay.core.positioning.WindowPositionTracker;
import com.interactivedisplay.core.window.ReloadWindowResult;
import com.interactivedisplay.core.window.WindowManager;
import com.interactivedisplay.debug.DebugRecorder;
import com.interactivedisplay.entity.DisplayEntityFactory;
import com.interactivedisplay.schema.SchemaLoader;
import com.interactivedisplay.schema.SchemaValidator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FocusedReloadRecoveryTest {
    @Test
    void invalidFocusedReloadShouldPreserveDefinitionAndRecoverAfterFix(@TempDir Path tempDir) throws Exception {
        Path windows = tempDir.resolve("interactivedisplay/windows");
        Files.createDirectories(windows);
        Path target = windows.resolve("qa.yaml");
        Path unrelated = windows.resolve("other.yaml");
        Files.writeString(target, validYaml("qa_focus", "before"), StandardCharsets.UTF_8);
        Files.writeString(unrelated, validYaml("qa_other", "other"), StandardCharsets.UTF_8);

        WindowManager manager = manager(tempDir);
        assertTrue(manager.reloadAll().success());
        assertTrue(manager.loadedWindowIds().contains("qa_focus"));
        assertTrue(manager.loadedWindowIds().contains("qa_other"));

        // Break both files after the initial load. Focused reload of qa_focus must not parse or evict qa_other.
        Files.writeString(unrelated, "id: qa_other\ncomponents: broken\n", StandardCharsets.UTF_8);
        Files.writeString(target, """
                id: qa_focus
                size: { width: 3.0, height: 2.0 }
                components:
                  - id: broken
                    type: button
                    label: Broken
                    action: { type: callback }
                """, StandardCharsets.UTF_8);

        ReloadWindowResult failed = manager.reloadOne("qa_focus");
        assertFalse(failed.success());
        assertTrue(manager.loadedWindowIds().contains("qa_focus"));
        assertTrue(manager.brokenWindowIds().contains("qa_focus"));
        assertTrue(manager.loadedWindowIds().contains("qa_other"));
        assertFalse(manager.brokenWindowIds().contains("qa_other"));

        Files.writeString(target, validYaml("qa_focus", "after"), StandardCharsets.UTF_8);
        ReloadWindowResult recovered = manager.reloadOne("qa_focus");

        assertTrue(recovered.success());
        assertTrue(manager.loadedWindowIds().contains("qa_focus"));
        assertFalse(manager.brokenWindowIds().contains("qa_focus"));
        assertTrue(manager.loadedWindowIds().contains("qa_other"));
        assertFalse(manager.brokenWindowIds().contains("qa_other"));
    }

    private static String validYaml(String id, String content) {
        return """
                id: %s
                size: { width: 3.0, height: 2.0 }
                components:
                  - id: title
                    type: text
                    position: { x: 0.0, y: 0.0, z: 0.0 }
                    content: %s
                """.formatted(id, content);
    }

    private static WindowManager manager(Path tempDir) {
        DebugRecorder debugRecorder = new DebugRecorder(20);
        CoordinateTransformer transformer = new CoordinateTransformer();
        return new WindowManager(
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
    }
}
