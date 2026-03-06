package com.interactivedisplay.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
