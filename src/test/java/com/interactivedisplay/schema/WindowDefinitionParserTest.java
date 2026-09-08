package com.interactivedisplay.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ClickType;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.window.WindowDefinition;
import com.interactivedisplay.core.window.WindowTransitionType;
import com.interactivedisplay.debug.DebugRecorder;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowDefinitionParserTest {
    @Test
    void parsesYamlWindowAndPreservesMultilineText(@TempDir Path tempDir) throws Exception {
        Path fixture = copyFixture(tempDir, "/interactivedisplay/windows/valid.yaml");
        JsonNode root = new ConfigDocumentLoader().load(fixture);
        assertTrue(new SchemaValidator().validate(root, "valid.yaml").isEmpty());

        DebugRecorder recorder = new DebugRecorder(10);
        WindowDefinition definition = parser(tempDir, recorder).parse(root, "valid.yaml");

        TextComponentDefinition text = (TextComponentDefinition) definition.components().getFirst();
        assertEquals("parser_window", definition.id());
        assertEquals(0.25f, definition.offset().horizontal());
        assertEquals("첫 번째 줄\n두 번째 줄\n", text.content());
        assertEquals(0.6f, text.fontSize());
        assertEquals(20, text.refreshInterval());
        assertEquals(6, definition.transition().duration());
        assertEquals(WindowTransitionType.SCALE, definition.transition().enter());
        assertEquals(WindowTransitionType.SLIDE_DOWN, definition.transition().exit());
    }

    @Test
    void parsesButtonClickTypeAndHoverScale(@TempDir Path tempDir) throws Exception {
        Path fixture = tempDir.resolve("button.yaml");
        Files.writeString(fixture, """
                id: button_window
                size:
                  width: 2.0
                  height: 1.0
                components:
                  - id: action
                    type: button
                    position:
                      x: 0.0
                      y: 0.0
                      z: 0.0
                    size:
                      width: 1.0
                      height: 0.3
                    label: Action
                    clickType: both
                    hoverScale: 1.08
                    action:
                      type: close_window
                """);
        JsonNode root = new ConfigDocumentLoader().load(fixture);
        assertTrue(new SchemaValidator().validate(root, "button.yaml").isEmpty());

        DebugRecorder recorder = new DebugRecorder(10);
        WindowDefinition definition = parser(tempDir, recorder).parse(root, "button.yaml");
        ButtonComponentDefinition button = (ButtonComponentDefinition) definition.components().getFirst();

        assertEquals(ClickType.BOTH, button.clickType());
        assertEquals(1.08f, button.hoverScale(), 0.0001f);
    }

    @Test
    void parsesNestedPanelChildren(@TempDir Path tempDir) throws Exception {
        Path fixture = copyFixture(tempDir, "/interactivedisplay/windows/nested_panel.yaml");
        JsonNode root = new ConfigDocumentLoader().load(fixture);
        assertTrue(new SchemaValidator().validate(root, "nested_panel.yaml").isEmpty());

        DebugRecorder recorder = new DebugRecorder(10);
        WindowDefinition definition = parser(tempDir, recorder).parse(root, "nested_panel.yaml");

        PanelComponentDefinition panel = (PanelComponentDefinition) definition.components().getFirst();
        assertEquals(1, panel.children().size());
        assertEquals("child_text", panel.children().getFirst().id());
    }

    private static WindowDefinitionParser parser(Path tempDir, DebugRecorder recorder) {
        return new WindowDefinitionParser(
                new MapImageResolver(tempDir, new RemoteImageCache(tempDir, recorder)),
                recorder
        );
    }

    private static Path copyFixture(Path tempDir, String resourceName) throws Exception {
        Path fixture = tempDir.resolve(Path.of(resourceName).getFileName());
        try (InputStream input = Objects.requireNonNull(WindowDefinitionParserTest.class.getResourceAsStream(resourceName))) {
            Files.copy(input, fixture);
        }
        return fixture;
    }
}
