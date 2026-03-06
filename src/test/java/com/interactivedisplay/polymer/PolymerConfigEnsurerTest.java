package com.interactivedisplay.polymer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PolymerConfigEnsurerTest {
    @Test
    void ensureShouldPatchRequiredKeysWithoutRemovingOthers(@TempDir Path tempDir) throws Exception {
        PolymerConfigEnsurer ensurer = new PolymerConfigEnsurer(tempDir);
        Path polymerDir = tempDir.resolve("polymer");
        Files.createDirectories(polymerDir);
        Files.writeString(polymerDir.resolve("auto-host.json"), """
                {
                  "enabled": false,
                  "required": false,
                  "message": "keep"
                }
                """, StandardCharsets.UTF_8);

        ensurer.ensure();

        JsonObject autoHost = JsonParser.parseString(Files.readString(ensurer.autoHostPath(), StandardCharsets.UTF_8)).getAsJsonObject();
        JsonObject resourcePack = JsonParser.parseString(Files.readString(ensurer.resourcePackPath(), StandardCharsets.UTF_8)).getAsJsonObject();

        assertEquals(true, autoHost.get("enabled").getAsBoolean());
        assertEquals(true, autoHost.get("required").getAsBoolean());
        assertEquals("keep", autoHost.get("message").getAsString());
        assertEquals("polymer:automatic", autoHost.get("type").getAsString());
        assertEquals("polymer/resource_pack.zip", resourcePack.get("resource_pack_location").getAsString());
        assertTrue(Files.exists(ensurer.resourcePackPath()));
    }
}
