package com.interactivedisplay.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.interactivedisplay.core.component.ButtonBoxModel;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ButtonHorizontalAlignment;
import com.interactivedisplay.core.component.ButtonSizeMode;
import com.interactivedisplay.core.component.ButtonVerticalAlignment;
import com.interactivedisplay.core.component.ClickType;
import com.interactivedisplay.core.component.PanelComponentDefinition;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.component.TextInputComponentDefinition;
import com.interactivedisplay.core.layout.LayoutComponent;
import com.interactivedisplay.core.layout.LayoutMode;
import com.interactivedisplay.core.layout.MeditateLayoutEngine;
import com.interactivedisplay.core.window.WindowComponentRuntime;
import com.interactivedisplay.core.window.WindowDefinition;
import com.interactivedisplay.core.window.WindowTransitionType;
import com.interactivedisplay.debug.DebugRecorder;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
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
    void parsesButtonPaddingAndAlignment(@TempDir Path tempDir) throws Exception {
        Path fixture = tempDir.resolve("button_box.yaml");
        Files.writeString(fixture, """
                id: button_box
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
                      width: 1.5
                      height: 0.5
                    padding:
                      horizontal: 0.12
                      vertical: 0.06
                    alignment:
                      horizontal: right
                      vertical: top
                    sizing:
                      width: fixed
                      height: content
                    label: Action
                    action:
                      type: close_window
                """);
        JsonNode root = new ConfigDocumentLoader().load(fixture);
        assertTrue(new SchemaValidator().validate(root, "button_box.yaml").isEmpty());

        WindowDefinition definition = parser(tempDir, new DebugRecorder(10)).parse(root, "button_box.yaml");
        ButtonComponentDefinition button = (ButtonComponentDefinition) definition.components().getFirst();

        assertEquals(0.12f, button.padding().horizontal(), 0.0001f);
        assertEquals(0.06f, button.padding().vertical(), 0.0001f);
        assertEquals(ButtonHorizontalAlignment.RIGHT, button.horizontalAlignment());
        assertEquals(ButtonVerticalAlignment.TOP, button.verticalAlignment());
        assertEquals(ButtonSizeMode.FIXED, button.sizing().width());
        assertEquals(ButtonSizeMode.CONTENT, button.sizing().height());
    }

    @Test
    void parsesTextInputComponent(@TempDir Path tempDir) throws Exception {
        Path fixture = tempDir.resolve("text_input.yaml");
        Files.writeString(fixture, """
                id: input_window
                size:
                  width: 2.5
                  height: 1.0
                components:
                  - id: search
                    type: text_input
                    position:
                      x: 0.0
                      y: 0.0
                      z: 0.01
                    size:
                      width: 2.0
                      height: 0.35
                    initialValue: hello
                    placeholder: Search...
                    maxLength: 48
                    clickType: both
                    dialogTitle: Search
                    dialogLabel: Query
                    confirmLabel: Apply
                    cancelLabel: Back
                """);
        JsonNode root = new ConfigDocumentLoader().load(fixture);
        assertTrue(new SchemaValidator().validate(root, "text_input.yaml").isEmpty());

        WindowDefinition definition = parser(tempDir, new DebugRecorder(10)).parse(root, "text_input.yaml");
        TextInputComponentDefinition input = (TextInputComponentDefinition) definition.components().getFirst();

        assertEquals("hello", input.initialValue());
        assertEquals("Search...", input.placeholder());
        assertEquals(48, input.maxLength());
        assertEquals(ClickType.BOTH, input.clickType());
        assertEquals("Search", input.dialogTitle());
        assertEquals("Query", input.dialogLabel());
        assertEquals("Apply", input.confirmLabel());
        assertEquals("Back", input.cancelLabel());
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

    @Test
    void parsesConfiguredFlowGapAndNestedGridLayout(@TempDir Path tempDir) throws Exception {
        Path fixture = tempDir.resolve("layout_options.yaml");
        Files.writeString(fixture, """
                id: layout_options
                size:
                  width: 4.0
                  height: 3.0
                layout:
                  type: vertical
                  gap: 0.12
                components:
                  - id: grid
                    type: panel
                    position: { x: 0.0, y: 0.0, z: 0.0 }
                    size: { width: 3.0, height: 2.0 }
                    layout:
                      type: grid
                      columns: 2
                      rowGap: 0.1
                      columnGap: 0.2
                      justifyItems: center
                      alignItems: end
                    children:
                      - id: first
                        type: text
                        position: { x: 0.0, y: 0.0, z: 0.0 }
                        size: { width: 1.0, height: 0.2 }
                        content: First
                      - id: second
                        type: text
                        position: { x: 0.0, y: 0.0, z: 0.0 }
                        size: { width: 0.8, height: 0.3 }
                        content: Second
                """);
        JsonNode root = new ConfigDocumentLoader().load(fixture);
        assertTrue(new SchemaValidator().validate(root, "layout_options.yaml").isEmpty());

        WindowDefinition definition = parser(tempDir, new DebugRecorder(10)).parse(root, "layout_options.yaml");
        PanelComponentDefinition panel = (PanelComponentDefinition) definition.components().getFirst();

        assertEquals(LayoutMode.VERTICAL, definition.layoutMode());
        assertEquals(0.12f, definition.layoutOptions().gap(), 0.0001f);
        assertEquals(LayoutMode.GRID, panel.layoutMode());
        assertEquals(2, panel.layoutOptions().columns());
        assertEquals(0.1f, panel.layoutOptions().rowGap(), 0.0001f);
        assertEquals(0.2f, panel.layoutOptions().columnGap(), 0.0001f);
        assertEquals(com.interactivedisplay.core.layout.ItemAlignment.CENTER,
                panel.layoutOptions().justifyItems());
        assertEquals(com.interactivedisplay.core.layout.ItemAlignment.END,
                panel.layoutOptions().alignItems());
    }

    @Test
    void bundledSampleIndexGridShouldPreserveButtonPositionsAndHitGeometry(@TempDir Path tempDir) throws Exception {
        Path fixture = copyFixture(tempDir, "/defaults/interactivedisplay/windows/sample_index.yaml");
        JsonNode root = new ConfigDocumentLoader().load(fixture);
        assertTrue(new SchemaValidator().validate(root, "sample_index.yaml").isEmpty());

        WindowDefinition definition = parser(tempDir, new DebugRecorder(10)).parse(root, "sample_index.yaml");
        Map<String, LayoutComponent> layoutById = new HashMap<>();
        for (LayoutComponent layout : new MeditateLayoutEngine().calculate(definition)) {
            layoutById.put(layout.definition().id(), layout);
        }

        PanelComponentDefinition menuGrid = (PanelComponentDefinition) layoutById.get("menu_grid").definition();
        assertEquals(LayoutMode.GRID, menuGrid.layoutMode());
        assertEquals(1, menuGrid.layoutOptions().columns());
        assertEquals(2, menuGrid.children().size());
        assertEquals("menu", menuGrid.children().getFirst().id());
        assertEquals("display", menuGrid.children().get(1).id());
        assertEquals(-0.62f, layoutById.get("menu").localPosition().y(), 0.0001f);
        assertEquals(0.0f, layoutById.get("display").localPosition().y(), 0.0001f);
        assertEquals(-1.08f, layoutById.get("close").localPosition().y(), 0.0001f);

        for (String id : List.of("menu", "display", "close")) {
            LayoutComponent layout = layoutById.get(id);
            ButtonComponentDefinition button = (ButtonComponentDefinition) layout.definition();
            Vector3f position = layout.localPosition();
            WindowComponentRuntime runtime = new WindowComponentRuntime(
                    Level.OVERWORLD,
                    button,
                    position,
                    null,
                    null
            );
            var resolved = ButtonBoxModel.resolve(button);

            assertEquals(position.x, runtime.localPosition().x, 0.0001f);
            assertEquals(position.y, runtime.localPosition().y, 0.0001f);
            assertEquals(position.z, runtime.localPosition().z, 0.0001f);
            assertEquals(resolved.width() / 2.0f, runtime.hitHalfWidth(), 0.0001f);
            assertEquals(resolved.height() / 2.0f, runtime.hitHalfHeight(), 0.0001f);
            assertEquals(position.x, runtime.hitCenterLocalPosition().x, 0.0001f);
            assertEquals(position.y + resolved.height() / 2.0f,
                    runtime.hitCenterLocalPosition().y, 0.0001f);
            assertEquals(position.z, runtime.hitCenterLocalPosition().z, 0.0001f);
        }
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
