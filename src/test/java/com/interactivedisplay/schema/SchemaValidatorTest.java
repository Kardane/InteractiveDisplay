package com.interactivedisplay.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaValidatorTest {
    private final SchemaValidator validator = new SchemaValidator();
    private final YAMLMapper mapper = new YAMLMapper();

    private JsonNode parse(String source) {
        try {
            return this.mapper.readTree(source);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void validComplexWindowShouldPass() {
        JsonNode root = parse("""
                id: main_menu
                size:
                  width: 3.0
                  height: 2.0
                layout: vertical
                components:
                  - id: title
                    type: text
                    position:
                      x: 0.0
                      y: 0.0
                      z: 0.01
                    width: 3.0
                    height: 0.3
                    content: hello
                  - id: panel
                    type: panel
                    position:
                      x: 0.0
                      y: 0.2
                      z: 0.0
                    size:
                      width: 2.0
                      height: 1.0
                    layout: horizontal
                    children:
                      - id: map
                        type: image
                        position:
                          x: 0.0
                          y: 0.0
                          z: 0.0
                        size:
                          width: 1.0
                          height: 1.0
                        imageType: MAP
                        value: sample_local.png
                      - id: run
                        type: button
                        position:
                          x: 0.0
                          y: 0.0
                          z: 0.0
                        size:
                          width: 1.0
                          height: 0.3
                        label: run
                        action:
                          type: run_command
                          command: 'say hi'
                          permissionLevel: 2
                """);

        List<String> errors = this.validator.validate(root, "main_menu.yaml");

        assertTrue(errors.isEmpty());
    }

    @Test
    void textComponentWithoutExplicitSizeShouldUseDefaults() {
        JsonNode root = parse("""
                id: main_menu
                size:
                  width: 3.0
                  height: 2.0
                components:
                  - id: title
                    type: text
                    position:
                      x: 0.0
                      y: 0.0
                      z: 0.0
                    content: InteractiveDisplay
                """);

        List<String> errors = this.validator.validate(root, "main_menu.yaml");

        assertTrue(errors.isEmpty());
    }

    @Test
    void invalidWindowShouldReportActionAndImageErrors() {
        JsonNode root = parse("""
                id: bad
                size:
                  width: 0
                  height: 2.0
                components:
                  - id: img
                    type: image
                    position:
                      x: 0.0
                      y: 0.0
                      z: 0.0
                    size:
                      width: 1.0
                      height: 1.0
                    imageType: BAD
                    value: x
                  - id: cb
                    type: button
                    position:
                      x: 0.0
                      y: 0.0
                      z: 0.0
                    size:
                      width: 1.0
                      height: 0.3
                    label: x
                    action:
                      type: callback
                """);

        List<String> errors = this.validator.validate(root, "bad.yaml");

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(message -> message.contains("width must be > 0")));
        assertTrue(errors.stream().anyMatch(message -> message.contains("imageType")));
        assertTrue(errors.stream().anyMatch(message -> message.contains("id must be string") || message.contains(".action: id must be string")));
    }

    @Test
    void runCommandPermissionLevelShouldBeValidated() {
        JsonNode root = parse("""
                id: menu
                size:
                  width: 3.0
                  height: 2.0
                components:
                  - id: run
                    type: button
                    position:
                      x: 0.0
                      y: 0.0
                      z: 0.0
                    size:
                      width: 1.0
                      height: 0.3
                    label: run
                    action:
                      type: run_command
                      command: 'say hi'
                      permissionLevel: 5
                """);

        List<String> errors = this.validator.validate(root, "menu.yaml");

        assertTrue(errors.stream().anyMatch(message -> message.contains("permissionLevel")));
    }

    @Test
    void togglePlacementTrackingActionShouldPassValidation() {
        JsonNode root = parse("""
                id: menu
                size:
                  width: 3.0
                  height: 2.0
                components:
                  - id: track
                    type: button
                    position:
                      x: 0.0
                      y: 0.0
                      z: 0.0
                    size:
                      width: 1.0
                      height: 0.3
                    label: track
                    action:
                      type: toggle_placement_tracking
                """);

        List<String> errors = this.validator.validate(root, "menu.yaml");

        assertTrue(errors.isEmpty());
    }

    @Test
    void duplicateComponentIdsShouldBeRejected() {
        JsonNode root = parse("""
                id: menu
                size:
                  width: 3.0
                  height: 2.0
                components:
                  - id: shared
                    type: text
                    position:
                      x: 0.0
                      y: 0.0
                      z: 0.0
                    content: title
                  - id: shared
                    type: button
                    position:
                      x: 0.0
                      y: -0.4
                      z: 0.0
                    size:
                      width: 1.0
                      height: 0.3
                    label: button
                    action:
                      type: close_window
                """);

        List<String> errors = this.validator.validate(root, "menu.yaml");

        assertTrue(errors.stream().anyMatch(message -> message.contains("duplicate component id shared")));
    }

    @Test
    void yamlStringAndNumberTypesShouldBeStrict() {
        JsonNode root = parse("""
                id: 123
                size:
                  width: 3.0
                  height: 2.0
                layout: 123
                components:
                  - id: button
                    type: button
                    position:
                      x: 0.0
                      y: 0.0
                      z: 0.0
                    size:
                      width: 1.0
                      height: 0.3
                    label: 456
                    fontSize: '1.0'
                    clickSound: 789
                    action:
                      type: close_window
                """);

        List<String> errors = this.validator.validate(root, "types.yaml");

        assertTrue(errors.stream().anyMatch(message -> message.contains("id must be string")));
        assertTrue(errors.stream().anyMatch(message -> message.contains("layout must be string")));
        assertTrue(errors.stream().anyMatch(message -> message.contains("label must be string")));
        assertTrue(errors.stream().anyMatch(message -> message.contains("fontSize must be number")));
        assertTrue(errors.stream().anyMatch(message -> message.contains("clickSound must be string")));
    }

    @Test
    void quotedColorsShouldBeStringsAndUnquotedHashShouldBeRejected() {
        JsonNode valid = parse("""
                id: menu
                size:
                  width: 3.0
                  height: 2.0
                components:
                  - id: title
                    type: text
                    position:
                      x: 0.0
                      y: 0.0
                      z: 0.0
                    content: hello
                    color: "#FFFFFF"
                    background: "#00000000"
                """);
        JsonNode invalid = parse("""
                id: menu
                size:
                  width: 3.0
                  height: 2.0
                components:
                  - id: title
                    type: text
                    position:
                      x: 0.0
                      y: 0.0
                      z: 0.0
                    content: hello
                    color: #FFFFFF
                """);

        assertTrue(this.validator.validate(valid, "quoted.yaml").isEmpty());
        assertTrue(this.validator.validate(invalid, "unquoted.yaml").stream()
                .anyMatch(message -> message.contains("color must be string")));
    }

    @Test
    void scalarAndArrayRootsShouldBeRejectedBySchemaValidator() {
        assertTrue(this.validator.validate(parse("hello"), "scalar.yaml").stream()
                .anyMatch(message -> message.contains("root must be object")));
        assertTrue(this.validator.validate(parse("- hello"), "array.yaml").stream()
                .anyMatch(message -> message.contains("root must be object")));
    }
}
