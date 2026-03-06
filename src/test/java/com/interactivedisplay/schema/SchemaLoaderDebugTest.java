package com.interactivedisplay.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.debug.DebugRecorder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaLoaderDebugTest {
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
}
