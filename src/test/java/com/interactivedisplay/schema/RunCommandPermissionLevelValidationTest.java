package com.interactivedisplay.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunCommandPermissionLevelValidationTest {
    private final SchemaValidator validator = new SchemaValidator();
    private final YAMLMapper mapper = new YAMLMapper();

    @Test
    void permissionLevelsZeroThroughFourShouldBeAccepted() throws Exception {
        for (int permissionLevel = 0; permissionLevel <= 4; permissionLevel++) {
            List<String> errors = validator.validate(parse(permissionLevel), "permission-" + permissionLevel + ".yaml");
            assertTrue(errors.isEmpty(), "permissionLevel " + permissionLevel + " should be accepted: " + errors);
        }
    }

    @Test
    void permissionLevelsOutsideZeroThroughFourShouldBeRejected() throws Exception {
        for (int permissionLevel : new int[]{-1, 5}) {
            List<String> errors = validator.validate(parse(permissionLevel), "permission-" + permissionLevel + ".yaml");
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(message -> message.contains("permissionLevel")));
        }
    }

    private JsonNode parse(int permissionLevel) throws Exception {
        return mapper.readTree("""
                id: permission_test
                size:
                  width: 2.0
                  height: 1.0
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
                      permissionLevel: %d
                """.formatted(permissionLevel));
    }
}
