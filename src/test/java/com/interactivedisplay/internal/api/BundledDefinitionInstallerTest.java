package com.interactivedisplay.internal.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.debug.DebugRecorder;
import com.interactivedisplay.schema.SchemaLoader;
import com.interactivedisplay.schema.SchemaValidator;
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
        assertTrue(Files.readString(targetDir.resolve("economy__shop_group.yaml")).contains("windowId: economy:shop"));
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

    @Test
    void malformedBundledYamlShouldBeSkippedWithError(@TempDir Path tempDir) throws Exception {
        Path sourceDir = tempDir.resolve("malformed-source");
        Path targetDir = tempDir.resolve("malformed-target");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("bad.yaml"), "id: economy:bad\ncomponents: [\n", StandardCharsets.UTF_8);

        var result = BundledDefinitionInstaller.installDirectory(
                "economy", sourceDir, targetDir, BundledDefinitionInstaller.DefinitionKind.WINDOW
        );

        assertEquals(0, result.installed());
        assertEquals(1, result.skipped());
        assertEquals(1, result.errors().size());
        assertFalse(Files.exists(targetDir.resolve("economy__bad.yaml")));
    }

    @Test
    void duplicateBundledIdsShouldInstallOnlyFirstDefinition(@TempDir Path tempDir) throws Exception {
        Path sourceDir = tempDir.resolve("duplicate-source");
        Path targetDir = tempDir.resolve("duplicate-target");
        Files.createDirectories(sourceDir);
        String yaml = "id: economy:shared\nsize: { width: 2.0, height: 1.0 }\ncomponents: []\n";
        Files.writeString(sourceDir.resolve("a.yaml"), yaml, StandardCharsets.UTF_8);
        Files.writeString(sourceDir.resolve("b.yaml"), yaml, StandardCharsets.UTF_8);

        var result = BundledDefinitionInstaller.installDirectory(
                "economy", sourceDir, targetDir, BundledDefinitionInstaller.DefinitionKind.WINDOW
        );

        assertEquals(1, result.installed());
        assertEquals(1, result.skipped());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void foreignModCannotOverwriteAnotherModsNamespacedId(@TempDir Path tempDir) throws Exception {
        Path targetDir = tempDir.resolve("shared-target");
        Path economySource = tempDir.resolve("economy-source");
        Path otherSource = tempDir.resolve("other-source");
        Files.createDirectories(economySource);
        Files.createDirectories(otherSource);
        String yaml = "id: economy:shared\nsize: { width: 2.0, height: 1.0 }\ncomponents: []\n";
        Files.writeString(economySource.resolve("shared.yaml"), yaml, StandardCharsets.UTF_8);
        Files.writeString(otherSource.resolve("shared.yaml"), yaml, StandardCharsets.UTF_8);

        var first = BundledDefinitionInstaller.installDirectory(
                "economy", economySource, targetDir, BundledDefinitionInstaller.DefinitionKind.WINDOW
        );
        var second = BundledDefinitionInstaller.installDirectory(
                "other", otherSource, targetDir, BundledDefinitionInstaller.DefinitionKind.WINDOW
        );

        assertEquals(1, first.installed());
        assertEquals(0, second.installed());
        assertEquals(1, second.skipped());
        assertEquals(1, second.errors().size());
        assertTrue(Files.exists(targetDir.resolve("economy__shared.yaml")));
        assertFalse(Files.exists(targetDir.resolve("other__shared.yaml")));
    }

    @Test
    void schemaInvalidBundledWindowShouldBeReportedBrokenBySchemaLoader(@TempDir Path tempDir) throws Exception {
        Path sourceDir = tempDir.resolve("schema-invalid-source");
        Path targetDir = tempDir.resolve("interactivedisplay/windows");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("invalid.yaml"), """
                id: economy:invalid
                size: { width: 0.0, height: 1.0 }
                components: []
                """, StandardCharsets.UTF_8);

        var install = BundledDefinitionInstaller.installDirectory(
                "economy", sourceDir, targetDir, BundledDefinitionInstaller.DefinitionKind.WINDOW
        );
        assertEquals(1, install.installed());

        var load = new SchemaLoader(tempDir, new SchemaValidator(), new DebugRecorder(20)).loadAll();
        assertTrue(load.brokenWindowIds().contains("economy:invalid"));
        assertFalse(load.definitions().containsKey("economy:invalid"));
    }
}
