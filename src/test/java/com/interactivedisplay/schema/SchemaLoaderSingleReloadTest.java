package com.interactivedisplay.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.debug.DebugRecorder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaLoaderSingleReloadTest {
    @Test
    void loadWindowShouldIgnoreUnrelatedBrokenYaml(@TempDir Path configDir) throws Exception {
        Path windows = configDir.resolve("interactivedisplay").resolve("windows");
        Files.createDirectories(windows);
        Files.writeString(windows.resolve("target.yaml"), """
                id: target
                size:
                  width: 2.0
                  height: 1.0
                components:
                  - id: title
                    type: text
                    position:
                      x: 0.0
                      y: 0.0
                      z: 0.0
                    content: Target
                """);
        Files.writeString(windows.resolve("broken.yaml"), """
                id: broken
                components: not-an-array
                """);

        SchemaLoader loader = new SchemaLoader(configDir, new SchemaValidator(), new DebugRecorder(20));
        SchemaLoader.LoadResult result = loader.loadWindow("target");

        assertTrue(result.errors().isEmpty());
        assertTrue(result.brokenWindowIds().isEmpty());
        assertEquals(1, result.definitions().size());
        assertEquals("target", result.definitions().get("target").id());
        assertTrue(result.groups().isEmpty());
    }

    @Test
    void loadWindowShouldReportOnlyRequestedBrokenWindow(@TempDir Path configDir) throws Exception {
        Path windows = configDir.resolve("interactivedisplay").resolve("windows");
        Files.createDirectories(windows);
        Files.writeString(windows.resolve("target.yaml"), """
                id: target
                components: not-an-array
                """);

        SchemaLoader loader = new SchemaLoader(configDir, new SchemaValidator(), new DebugRecorder(20));
        SchemaLoader.LoadResult result = loader.loadWindow("target");

        assertTrue(result.definitions().isEmpty());
        assertTrue(result.brokenWindowIds().contains("target"));
        assertEquals(1, result.brokenWindowIds().size());
        assertTrue(result.hasErrors());
    }
}
