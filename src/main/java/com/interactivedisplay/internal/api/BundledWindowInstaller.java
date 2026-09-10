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

final class BundledWindowInstaller {
    private BundledWindowInstaller() {
    }

    static InstallReport installAll(FabricLoader loader, Path configDir) {
        Path targetDir = configDir.resolve("interactivedisplay").resolve("windows");
        List<String> errors = new ArrayList<>();
        int installed = 0;
        int skipped = 0;

        try {
            Files.createDirectories(targetDir);
        } catch (IOException exception) {
            return new InstallReport(0, 0, List.of("cannot prepare bundled window target: " + exception.getMessage()));
        }

        for (ModContainer mod : loader.getAllMods()) {
            String modId = mod.getMetadata().getId();
            String resourcePath = "data/" + modId + "/interactivedisplay/windows";
            var sourceDir = mod.findPath(resourcePath);
            if (sourceDir.isEmpty()) {
                continue;
            }
            try {
                DirectoryInstallResult result = installDirectory(modId, sourceDir.get(), targetDir);
                installed += result.installed();
                skipped += result.skipped();
                errors.addAll(result.errors());
            } catch (IOException exception) {
                errors.add(modId + ": bundled window scan failed: " + exception.getMessage());
            }
        }
        return new InstallReport(installed, skipped, List.copyOf(errors));
    }

    static DirectoryInstallResult installDirectory(String modId, Path sourceDir, Path targetDir) throws IOException {
        if (!Files.isDirectory(sourceDir)) {
            return new DirectoryInstallResult(0, 0, List.of());
        }
        Path normalizedTargetDir = targetDir.toAbsolutePath().normalize();
        Files.createDirectories(normalizedTargetDir);
        ConfigDocumentLoader documentLoader = new ConfigDocumentLoader();
        Set<String> existingWindowIds = existingWindowIds(normalizedTargetDir, documentLoader);
        List<String> errors = new ArrayList<>();
        int installed = 0;
        int skipped = 0;

        try (var files = Files.list(sourceDir)) {
            for (Path source : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".yaml"))
                    .sorted()
                    .toList()) {
                String sourceName = modId + ":" + source.getFileName();
                try {
                    JsonNode root = documentLoader.load(source);
                    String rawId = root.path("id").isTextual() ? root.path("id").textValue() : null;
                    ResourceLocation id = rawId == null ? null : ResourceLocation.tryParse(rawId);
                    if (id == null || rawId.indexOf(':') < 0 || !modId.equals(id.getNamespace())) {
                        errors.add(sourceName + ": bundled window id must be namespaced with '" + modId + "'");
                        skipped++;
                        continue;
                    }
                    if (existingWindowIds.contains(rawId)) {
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
                    existingWindowIds.add(rawId);
                    installed++;
                } catch (Exception exception) {
                    errors.add(sourceName + ": bundled window install failed: " + exception.getMessage());
                    skipped++;
                }
            }
        }
        return new DirectoryInstallResult(installed, skipped, List.copyOf(errors));
    }

    private static Set<String> existingWindowIds(Path targetDir, ConfigDocumentLoader documentLoader) throws IOException {
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
            InteractiveDisplay.LOGGER.info("[{}] installed {} bundled public API window definition(s)", InteractiveDisplay.MOD_ID, report.installed());
        }
        for (String error : report.errors()) {
            InteractiveDisplay.LOGGER.warn("[{}] {}", InteractiveDisplay.MOD_ID, error);
        }
    }

    record InstallReport(int installed, int skipped, List<String> errors) {
    }

    record DirectoryInstallResult(int installed, int skipped, List<String> errors) {
    }
}
