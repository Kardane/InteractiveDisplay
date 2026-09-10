package com.interactivedisplay.window;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.interaction.CallbackRegistry;
import com.interactivedisplay.core.interaction.CommandWhitelist;
import com.interactivedisplay.core.layout.LayoutMode;
import com.interactivedisplay.core.layout.MeditateLayoutEngine;
import com.interactivedisplay.core.positioning.CoordinateTransformer;
import com.interactivedisplay.core.positioning.WindowOffset;
import com.interactivedisplay.core.positioning.WindowPositionTracker;
import com.interactivedisplay.core.window.WindowDefinition;
import com.interactivedisplay.core.window.WindowManager;
import com.interactivedisplay.debug.DebugRecorder;
import com.interactivedisplay.entity.DisplayEntityFactory;
import com.interactivedisplay.schema.SchemaLoader;
import com.interactivedisplay.schema.SchemaValidator;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProgrammaticWindowRegistrationTest {
    @Test
    void programmaticWindowShouldSurviveReloadsAndRejectDuplicateRegistration(@TempDir Path tempDir) {
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
        WindowDefinition definition = new WindowDefinition(
                "economy:shop",
                new ComponentSize(3.0f, 2.0f),
                WindowOffset.defaults(),
                LayoutMode.ABSOLUTE,
                List.of()
        );

        assertTrue(manager.registerProgrammaticWindow(definition));
        assertFalse(manager.registerProgrammaticWindow(definition));
        assertTrue(manager.loadedWindowIds().contains("economy:shop"));

        assertTrue(manager.reloadOne("economy:shop").success());
        assertTrue(manager.loadedWindowIds().contains("economy:shop"));

        manager.reloadAll();

        assertTrue(manager.loadedWindowIds().contains("economy:shop"));
        assertTrue(manager.programmaticWindowIds().contains("economy:shop"));
    }
}
