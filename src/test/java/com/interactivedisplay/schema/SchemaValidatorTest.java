package com.interactivedisplay.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaValidatorTest {
    private final SchemaValidator validator = new SchemaValidator();

    @Test
    void validComplexWindowShouldPass() {
        JsonObject root = JsonParser.parseString("""
                {
                  "id": "main_menu",
                  "size": {"width": 3.0, "height": 2.0},
                  "layout": "vertical",
                  "components": [
                    {
                      "id": "title",
                      "type": "text",
                      "position": {"x": 0.0, "y": 0.0, "z": 0.01},
                      "width": 3.0,
                      "height": 0.3,
                      "content": "hello"
                    },
                    {
                      "id": "panel",
                      "type": "panel",
                      "position": {"x": 0.0, "y": 0.2, "z": 0.0},
                      "size": {"width": 2.0, "height": 1.0},
                      "layout": "horizontal",
                      "children": [
                        {
                          "id": "map",
                          "type": "image",
                          "position": {"x": 0.0, "y": 0.0, "z": 0.0},
                          "size": {"width": 1.0, "height": 1.0},
                          "imageType": "MAP",
                          "value": "sample_local.png"
                        },
                        {
                          "id": "run",
                          "type": "button",
                          "position": {"x": 0.0, "y": 0.0, "z": 0.0},
                          "size": {"width": 1.0, "height": 0.3},
                          "label": "run",
                          "action": {"type": "run_command", "command": "say hi"}
                        }
                      ]
                    }
                  ]
                }
                """).getAsJsonObject();

        List<String> errors = validator.validate(root, "main_menu.json");
        assertTrue(errors.isEmpty());
    }

    @Test
    void invalidWindowShouldReportActionAndImageErrors() {
        JsonObject root = JsonParser.parseString("""
                {
                  "id": "bad",
                  "size": {"width": 0, "height": 2.0},
                  "components": [
                    {
                      "id": "img",
                      "type": "image",
                      "position": {"x": 0.0, "y": 0.0, "z": 0.0},
                      "size": {"width": 1.0, "height": 1.0},
                      "imageType": "BAD",
                      "value": "x"
                    },
                    {
                      "id": "cb",
                      "type": "button",
                      "position": {"x": 0.0, "y": 0.0, "z": 0.0},
                      "size": {"width": 1.0, "height": 0.3},
                      "label": "x",
                      "action": {"type": "callback"}
                    }
                  ]
                }
                """).getAsJsonObject();

        List<String> errors = validator.validate(root, "bad.json");
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(msg -> msg.contains("width must be > 0")));
        assertTrue(errors.stream().anyMatch(msg -> msg.contains("imageType")));
        assertTrue(errors.stream().anyMatch(msg -> msg.contains("id must be string") || msg.contains(".action: id must be string")));
    }
}
