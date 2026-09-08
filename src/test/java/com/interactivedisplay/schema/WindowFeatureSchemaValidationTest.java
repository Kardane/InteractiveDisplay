package com.interactivedisplay.schema;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowFeatureSchemaValidationTest {
    @Test
    void rejectsInvalidRefreshHoverAndTransitionValues(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("invalid.yaml");
        Files.writeString(file, """
                id: invalid
                size:
                  width: 2.0
                  height: 1.0
                transition:
                  duration: -1
                  enter: explode
                components:
                  - id: text
                    type: text
                    position:
                      x: 0.0
                      y: 0.0
                      z: 0.0
                    content: test
                    refreshInterval: -5
                  - id: button
                    type: button
                    position:
                      x: 0.0
                      y: -0.4
                      z: 0.0
                    size:
                      width: 1.0
                      height: 0.3
                    label: button
                    hoverScale: 0.0
                    action:
                      type: close_window
                """);

        JsonNode root = new ConfigDocumentLoader().load(file);
        List<String> errors = new SchemaValidator().validate(root, "invalid.yaml");

        assertTrue(errors.stream().anyMatch(error -> error.contains("transition.duration")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("transition") && error.contains("enter")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("refreshInterval")));
        assertTrue(errors.stream().anyMatch(error -> error.contains("hoverScale")));
    }
}
