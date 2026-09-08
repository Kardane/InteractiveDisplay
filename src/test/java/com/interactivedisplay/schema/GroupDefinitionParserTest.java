package com.interactivedisplay.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.interactivedisplay.core.window.WindowGroupDefinition;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GroupDefinitionParserTest {
    @Test
    void parsesYamlGroupWithOffsetAndOrbit(@TempDir Path tempDir) throws Exception {
        Path fixture = copyFixture(tempDir, "/interactivedisplay/groups/valid.yaml");
        JsonNode root = new ConfigDocumentLoader().load(fixture);
        assertTrue(new SchemaValidator().validateGroup(root, "valid.yaml").isEmpty());

        WindowGroupDefinition definition = new GroupDefinitionParser().parse(root);

        assertEquals("parser_group", definition.id());
        assertEquals("parser_window", definition.initialWindowId());
        assertEquals(1, definition.windows().size());
        assertEquals(0.5f, definition.windows().getFirst().offset().horizontal());
        assertEquals(15.0f, definition.windows().getFirst().orbit().yaw());
        assertEquals(-5.0f, definition.windows().getFirst().orbit().pitch());
    }

    private static Path copyFixture(Path tempDir, String resourceName) throws Exception {
        Path fixture = tempDir.resolve(Path.of(resourceName).getFileName());
        try (InputStream input = Objects.requireNonNull(GroupDefinitionParserTest.class.getResourceAsStream(resourceName))) {
            Files.copy(input, fixture);
        }
        return fixture;
    }
}
