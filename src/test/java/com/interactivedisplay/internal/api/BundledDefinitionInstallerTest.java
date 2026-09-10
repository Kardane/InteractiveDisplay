package com.interactivedisplay.internal.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundledDefinitionInstallerTest {
    @Test
    void shouldInstallNamespacedWindowWithoutOverwritingOperatorCopy(@TempDir Path tempDir) throws Exception {
        Path sourceDir = tempDir.resolve("jar-windows");
        Path targetDir = tempDir.resolve("config-windows");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("shop.yaml"), """
                id: economy:shop
                size: { width: 3.0, height: 2.0 }
                components: []
                """, StandardCharsets.UTF_8);

        BundledDefinitionInstaller.DirectoryInstallResult first = BundledDefinitionInstaller.installDirectory(
                "economy",
                sourceDir,
                targetDir,
                BundledDefinitionInstaller.DefinitionKind.WINDOW
        );
        Path installed = targetDir.resolve("economy__shop.yaml");
        assertEquals(1, first.installed());
        assertEquals(0, first.skipped());
        assertTrue(first.errors().isEmpty());
        assertTrue(Files.exists(installed));

        Files.writeString(installed, "operator override", StandardCharsets.UTF_8);
        BundledDefinitionInstaller.DirectoryInstallResult second = BundledDefinitionInstaller.installDirectory(
                "economy",
                sourceDir,
                targetDir,
                BundledDefinitionInstaller.DefinitionKind.WINDOW
        );
        assertEquals(0, second.installed());
        assertEquals(1, second.skipped());
        assertEquals("operator override", Files.readString(installed, StandardCharsets.UTF_8));
    }

    @Test
    void existingConfigWithSameIdShouldPreventBundledInstall(@TempDir Path tempDir) throws Exception {
        Path sourceDir = tempDir.resolve("jar-windows");
        Path targetDir = tempDir.resolve("config-windows");
        Files.createDirectories(sourceDir);
        Files.createDirectories(targetDir);
        Files.writeString(sourceDir.resolve("shop.yaml"), """
                id: economy:shop
                size: { width: 3.0, height: 2.0 }
                components: []
                """, StandardCharsets.UTF_8);
        Files.writeString(targetDir.resolve("operator-shop.yaml"), """
                id: economy:shop
                size: { width: 4.0, height: 3.0 }
                components: []
                """, StandardCharsets.UTF_8);

        BundledDefinitionInstaller.DirectoryInstallResult result = BundledDefinitionInstaller.installDirectory(
                "economy",
                sourceDir,
                targetDir,
                BundledDefinitionInstaller.DefinitionKind.WINDOW
        );
        assertEquals(0, result.installed());
        assertEquals(1, result.skipped());
        assertTrue(result.errors().isEmpty());
        assertFalse(Files.exists(targetDir.resolve("economy__shop.yaml")));
    }

    @Test
    void shouldInstallNamespacedGroupDefinition(@TempDir Path tempDir) throws Exception {
        Path sourceDir = tempDir.resolve("jar-groups");
        Path targetDir = tempDir.resolve("config-groups");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("shop_group.yaml"), """
                id: economy:shop_group
                initialWindowId: economy:shop
                defaultMode: player_view
                windows:
                  - windowId: economy:shop
                """, StandardCharsets.UTF_8);

        BundledDefinitionInstaller.DirectoryInstallResult result = BundledDefinitionInstaller.installDirectory(
                "economy",
                sourceDir,
                targetDir,
                BundledDefinitionInstaller.DefinitionKind.GROUP
        );
        assertEquals(1, result.installed());
        assertEquals(0, result.skipped());
        assertTrue(result.errors().isEmpty());
        assertTrue(Files.exists(targetDir.resolve("economy__shop_group.yaml")));
    }

    @Test
    void shouldRejectUnnamespacedOrForeignIdsForBothKinds(@TempDir Path tempDir) throws Exception {
        for (BundledDefinitionInstaller.DefinitionKind kind : BundledDefinitionInstaller.DefinitionKind.values()) {
            Path sourceDir = tempDir.resolve("jar-" + kind.name().toLowerCase());
            Path targetDir = tempDir.resolve("config-" + kind.name().toLowerCase());
            Files.createDirectories(sourceDir);
            Files.writeString(sourceDir.resolve("bad.yaml"), "id: local_only\n", StandardCharsets.UTF_8);
            Files.writeString(sourceDir.resolve("foreign.yaml"), "id: other:foreign\n", StandardCharsets.UTF_8);

            BundledDefinitionInstaller.DirectoryInstallResult result = BundledDefinitionInstaller.installDirectory(
                    "economy", sourceDir, targetDir, kind
            );
            assertEquals(0, result.installed());
            assertEquals(2, result.skipped());
            assertEquals(2, result.errors().size());
        }
    }
}
