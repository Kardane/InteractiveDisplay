package com.interactivedisplay.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.interactivedisplay.core.animation.AnimationInterpolation;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.window.WindowDefinition;
import com.interactivedisplay.debug.DebugRecorder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextAnimationParserTest {
    @Test
    void parsesTypewriterAndFadeAnimations(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("animated.yaml");
        Files.writeString(file, """
                id: animated
                size:
                  width: 2.0
                  height: 1.0
                components:
                  - id: dialogue
                    type: text
                    position:
                      x: 0.0
                      y: 0.0
                      z: 0.0
                    content: Hello
                    animations:
                      - type: typewriter
                        delay: 4
                        interval: 2
                        charsPerStep: 1
                      - type: fade
                        start: 1
                        duration: 8
                        interpolation: smooth
                """);
        JsonNode root = new ConfigDocumentLoader().load(file);
        assertTrue(new SchemaValidator().validate(root, "animated.yaml").isEmpty());

        WindowDefinition definition = parser(tempDir).parse(root, "animated.yaml");
        TextComponentDefinition text = (TextComponentDefinition) definition.components().getFirst();

        assertEquals(2, text.animations().size());
        assertEquals("typewriter", text.animations().getFirst().type());
        assertEquals(4, text.animations().getFirst().delay());
        assertEquals(2, text.animations().getFirst().interval());
        assertEquals(1, text.animations().getFirst().charsPerStep());
        assertEquals("fade", text.animations().get(1).type());
        assertEquals(8, text.animations().get(1).duration());
        assertEquals(AnimationInterpolation.SMOOTH, text.animations().get(1).interpolation());
    }

    @Test
    void rejectsUnknownAnimation(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("bad-animation.yaml");
        Files.writeString(file, """
                id: animated
                size:
                  width: 2.0
                  height: 1.0
                components:
                  - id: dialogue
                    type: text
                    position:
                      x: 0.0
                      y: 0.0
                      z: 0.0
                    content: Hello
                    animations:
                      - type: unknown
                """);
        JsonNode root = new ConfigDocumentLoader().load(file);

        assertThrows(SchemaValidationException.class, () -> parser(tempDir).parse(root, "bad-animation.yaml"));
    }

    @Test
    void rejectsZeroTypewriterStep(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("bad-step.yaml");
        Files.writeString(file, """
                id: animated
                size:
                  width: 2.0
                  height: 1.0
                components:
                  - id: dialogue
                    type: text
                    position:
                      x: 0.0
                      y: 0.0
                      z: 0.0
                    content: Hello
                    animations:
                      - type: typewriter
                        charsPerStep: 0
                """);
        JsonNode root = new ConfigDocumentLoader().load(file);

        assertThrows(SchemaValidationException.class, () -> parser(tempDir).parse(root, "bad-step.yaml"));
    }

    private static WindowDefinitionParser parser(Path tempDir) {
        DebugRecorder recorder = new DebugRecorder(10);
        return new WindowDefinitionParser(
                new MapImageResolver(tempDir, new RemoteImageCache(tempDir, recorder)),
                recorder
        );
    }
}
