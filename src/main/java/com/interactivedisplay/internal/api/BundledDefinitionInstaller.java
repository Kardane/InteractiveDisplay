package com.interactivedisplay.internal.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.schema.ConfigDocumentLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resources.ResourceLocation;

final class BundledDefinitionInstaller {
    private BundledDefinitionInstaller() {
    }

    static InstallReport installAll(FabricLoader loader, Path configDir) {
        List<String> errors = new ArrayList<>();
        int installed = 0;
        int skipped = 0;

        for (ModContainer mod : loader.getAllMods()) {
            String modId = mod.getMetadata().getId();
            for (DefinitionKind kind : DefinitionKind.values()) {
                Path targetDir = configDir.resolve("interactivedisplay").resolve(kind.directory());
                String resourcePath = "data/" + modId + "/interactivedisplay/" + kind.directory();
                var sourceDir = mod.findPath(resourcePath);
                if (sourceDir.isEmpty()) {
                    continue;
                }
                try {
                    DirectoryInstallResult result = installDirectory(modId, sourceDir.get(), targetDir, kind);
                    installed += result.installed();
                    skipped += result.skipped();
                    errors.addAll(result.errors());
                } catch (IOException exception) {
                    errors.add(modId + ": bundled " + kind.label() + " scan failed: " + exception.getMessage());
                }
            }
        }
        return new InstallReport(installed, skipped, List.copyOf(errors));
    }

    static DirectoryInstallResult installDirectory(
            String modId,
            Path sourceDir,
            Path targetDir,
            DefinitionKind kind
    ) throws IOException {
        if (!Files.isDirectory(sourceDir)) {
            return new DirectoryInstallResult(0, 0, List.of());
        }
        Path normalizedTargetDir = targetDir.toAbsolutePath().normalize();
        Files.createDirectories(normalizedTargetDir);
        ConfigDocumentLoader documentLoader = new ConfigDocumentLoader();
        Set<String> existingIds = existingDefinitionIds(normalizedTargetDir, documentLoader);
        List<String> errors = new ArrayList<>();
        int installed = 0;
        int skipped = 0;

        try (var files = Files.list(sourceDir)) {
            for (Path source : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".yaml"))
                    .sorted()
                    .toList()) {
                String sourceName = modId + ":" + kind.directory() + "/" + source.getFileName();
                try {
                    JsonNode root = documentLoader.load(source);
                    String rawId = root.path("id").isTextual() ? root.path("id").textValue() : null;
                    ResourceLocation id = rawId == null ? null : ResourceLocation.tryParse(rawId);
                    if (id == null || rawId.indexOf(':') < 0 || !modId.equals(id.getNamespace())) {
                        errors.add(sourceName + ": bundled " + kind.label() + " id must be namespaced with '" + modId + "'");
                        skipped++;
                        continue;
                    }
                    if (existingIds.contains(rawId)) {
                        skipped++;
                        continue;
                    }

                    String targetName = modId + "__" + source.getFileName();
                    Path target = normalizedTargetDir.resolve(targetName).normalize();
                    if (!target.startsWith(normalizedTargetDir)) {
                        errors.add(sourceName + ": invalid target path");
                        skipped++;
                        continue;
                    }
                    if (Files.exists(target)) {
                        skipped++;
                        continue;
                    }
                    Files.copy(source, target);
                    existingIds.add(rawId);
                    installed++;
                } catch (Exception exception) {
                    errors.add(sourceName + ": bundled " + kind.label() + " install failed: " + exception.getMessage());
                    skipped++;
                }
            }
        }
        return new DirectoryInstallResult(installed, skipped, List.copyOf(errors));
    }

    private static Set<String> existingDefinitionIds(Path targetDir, ConfigDocumentLoader documentLoader) throws IOException {
        Set<String> ids = new HashSet<>();
        try (var files = Files.list(targetDir)) {
            for (Path path : files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".yaml"))
                    .toList()) {
                try {
                    JsonNode root = documentLoader.load(path);
                    if (root.path("id").isTextual()) {
                        ids.add(root.path("id").textValue());
                    }
                } catch (Exception ignored) {
                    // SchemaLoader reports malformed operator configuration later. It should not abort bundled discovery.
                }
            }
        }
        return ids;
    }

    static void log(InstallReport report) {
        if (report.installed() > 0) {
            InteractiveDisplay.LOGGER.info(
                    "[{}] installed {} bundled public API definition(s)",
                    InteractiveDisplay.MOD_ID,
                    report.installed()
            );
        }
        for (String error : report.errors()) {
            InteractiveDisplay.LOGGER.warn("[{}] {}", InteractiveDisplay.MOD_ID, error);
        }
    }

    enum DefinitionKind {
        WINDOW("windows", "window"),
        GROUP("groups", "group");

        private final String directory;
        private final String label;

        DefinitionKind(String directory, String label) {
            this.directory = directory;
            this.label = label;
        }

        String directory() {
            return this.directory;
        }

        String label() {
            return this.label;
        }
    }

    record InstallReport(int installed, int skipped, List<String> errors) {
    }

    record DirectoryInstallResult(int installed, int skipped, List<String> errors) {
    }
}
