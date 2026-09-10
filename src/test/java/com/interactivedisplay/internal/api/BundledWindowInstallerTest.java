package com.interactivedisplay.internal.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundledWindowInstallerTest {
    @Test
    void shouldInstallNamespacedYamlWithoutOverwritingOperatorCopy(@TempDir Path tempDir) throws Exception {
        Path sourceDir = tempDir.resolve("jar-windows");
        Path targetDir = tempDir.resolve("config-windows");
        Files.createDirectories(sourceDir);
        Path source = sourceDir.resolve("shop.yaml");
        Files.writeString(source, """
                id: economy:shop
                size:
                  width: 3.0
                  height: 2.0
                components:
                  - id: title
                    type: text
                    position: { x: 0.0, y: 0.0, z: 0.0 }
                    content: Shop
                """, StandardCharsets.UTF_8);

        BundledWindowInstaller.DirectoryInstallResult first =
                BundledWindowInstaller.installDirectory("economy", sourceDir, targetDir);

        Path installed = targetDir.resolve("economy__shop.yaml");
        assertEquals(1, first.installed());
        assertEquals(0, first.skipped());
        assertTrue(first.errors().isEmpty());
        assertTrue(Files.exists(installed));

        Files.writeString(installed, "operator override", StandardCharsets.UTF_8);
        BundledWindowInstaller.DirectoryInstallResult second =
                BundledWindowInstaller.installDirectory("economy", sourceDir, targetDir);

        assertEquals(0, second.installed());
        assertEquals(1, second.skipped());
        assertEquals("operator override", Files.readString(installed, StandardCharsets.UTF_8));
    }

    @Test
    void shouldRejectUnnamespacedOrForeignWindowIds(@TempDir Path tempDir) throws Exception {
        Path sourceDir = tempDir.resolve("jar-windows");
        Path targetDir = tempDir.resolve("config-windows");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("bad.yaml"), """
                id: shop
                size: { width: 3.0, height: 2.0 }
                components: []
                """, StandardCharsets.UTF_8);
        Files.writeString(sourceDir.resolve("foreign.yaml"), """
                id: other:shop
                size: { width: 3.0, height: 2.0 }
                components: []
                """, StandardCharsets.UTF_8);

        BundledWindowInstaller.DirectoryInstallResult result =
                BundledWindowInstaller.installDirectory("economy", sourceDir, targetDir);

        assertEquals(0, result.installed());
        assertEquals(2, result.skipped());
        assertEquals(2, result.errors().size());
        assertFalse(Files.exists(targetDir.resolve("economy__bad.yaml")));
        assertFalse(Files.exists(targetDir.resolve("economy__foreign.yaml")));
    }
}
