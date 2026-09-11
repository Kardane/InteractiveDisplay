package com.interactivedisplay.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.interactivedisplay.core.interaction.CallbackRegistry;
import com.interactivedisplay.internal.api.InteractiveDisplayApiImpl;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CustomActionYamlTest {
    @Test
    void namespacedActionShouldNormalizeAndValidateThroughCallbackPipeline(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("shop.yaml");
        Files.writeString(file, """
                id: economy:shop
                size: { width: 3.0, height: 2.0 }
                components:
                  - id: buy
                    type: button
                    position: { x: 0.0, y: 0.0, z: 0.0 }
                    size: { width: 1.0, height: 0.35 }
                    label: Buy
                    action:
                      type: economy:yaml_purchase
                      product: diamond_sword
                      amount: 2
                      silent: true
                """, StandardCharsets.UTF_8);

        JsonNode root = new ConfigDocumentLoader().load(file);
        JsonNode action = root.path("components").get(0).path("action");

        assertEquals("callback", action.path("type").textValue());
        String token = action.path("id").textValue();
        assertTrue(CustomActionToken.isToken(token));
        CustomActionToken.Decoded decoded = CustomActionToken.decode(token);
        assertEquals("economy:yaml_purchase", decoded.actionId().toString());
        assertEquals("diamond_sword", decoded.parameters().get("product"));
        assertEquals("2", decoded.parameters().get("amount"));
        assertEquals("true", decoded.parameters().get("silent"));
        assertTrue(new SchemaValidator().validate(root, "shop.yaml").isEmpty());

        CallbackRegistry callbacks = new CallbackRegistry();
        assertFalse(callbacks.find(token).isPresent());
        InteractiveDisplayApiImpl api = new InteractiveDisplayApiImpl(callbacks);
        assertTrue(api.actions().register(
                ResourceLocation.fromNamespaceAndPath("economy", "yaml_purchase"),
                context -> { }
        ).success());
        assertTrue(callbacks.find(token).isPresent());
    }

    @Test
    void nestedObjectCustomActionParameterShouldBeRejected(@TempDir Path tempDir) throws Exception {
        assertNonScalarRejected(tempDir, """
                product:
                  id: diamond_sword
                """);
    }

    @Test
    void arrayCustomActionParameterShouldBeRejected(@TempDir Path tempDir) throws Exception {
        assertNonScalarRejected(tempDir, """
                products:
                  - diamond_sword
                  - shield
                """);
    }

    private static void assertNonScalarRejected(Path tempDir, String parameterYaml) throws Exception {
        Path file = tempDir.resolve("bad-" + System.nanoTime() + ".yaml");
        Files.writeString(file, """
                id: economy:shop
                size: { width: 3.0, height: 2.0 }
                components:
                  - id: buy
                    type: button
                    position: { x: 0.0, y: 0.0, z: 0.0 }
                    size: { width: 1.0, height: 0.35 }
                    label: Buy
                    action:
                      type: economy:yaml_nested
                """ + parameterYaml.indent(6), StandardCharsets.UTF_8);

        try {
            new ConfigDocumentLoader().load(file);
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("custom action parameter must be scalar"));
            return;
        }
        throw new AssertionError("expected custom action parameter validation failure");
    }
}
