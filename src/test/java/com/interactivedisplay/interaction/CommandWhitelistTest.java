package com.interactivedisplay.interaction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.core.interaction.CommandWhitelist;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandWhitelistTest {
    @Test
    void missingWhitelistShouldBeCopiedFromYamlResource(@TempDir Path tempDir) throws Exception {
        CommandWhitelist whitelist = new CommandWhitelist(tempDir);

        whitelist.reload();

        assertTrue(Files.exists(tempDir.resolve("interactivedisplay").resolve("command_whitelist.yaml")));
        assertFalse(whitelist.isAllowed("say hello"));
    }

    @Test
    void legacyJsonWhitelistShouldNotBeUsedAsFallback(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("interactivedisplay");
        Files.createDirectories(config);
        Files.writeString(config.resolve("command_whitelist.json"),
                "{\"allowedPrefixes\":[\"say \"]}\n",
                StandardCharsets.UTF_8);

        CommandWhitelist whitelist = new CommandWhitelist(tempDir);
        whitelist.reload();

        assertTrue(Files.exists(config.resolve("command_whitelist.yaml")));
        assertFalse(whitelist.isAllowed("say hello"));
    }

    @Test
    void slashPrefixedCommandShouldBeNormalized(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("interactivedisplay");
        Files.createDirectories(config);
        try (InputStream input = Objects.requireNonNull(CommandWhitelistTest.class.getResourceAsStream(
                "/interactivedisplay/command_whitelist.yaml"
        ))) {
            Files.copy(input, config.resolve("command_whitelist.yaml"));
        }

        CommandWhitelist whitelist = new CommandWhitelist(tempDir);
        whitelist.reload();

        assertTrue(whitelist.isAllowed("/say hello"));
        assertTrue(whitelist.isAllowed("title @s actionbar Hello"));
        assertFalse(whitelist.isAllowed("stop"));
        assertFalse(whitelist.isAllowed("op Steve"));
    }

    @Test
    void prefixBoundaryTrimAndNullShouldBeHandled(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("interactivedisplay");
        Files.createDirectories(config);
        Files.writeString(config.resolve("command_whitelist.yaml"),
                "allowedPrefixes:\n  - \"say \"\n",
                StandardCharsets.UTF_8);

        CommandWhitelist whitelist = new CommandWhitelist(tempDir);
        whitelist.reload();

        assertTrue(whitelist.isAllowed("  /say hello  "));
        assertFalse(whitelist.isAllowed("say"));
        assertFalse(whitelist.isAllowed("/say"));
        assertFalse(whitelist.isAllowed(null));
    }

    @Test
    void emptyWhitelistShouldDenyEveryCommand(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("interactivedisplay");
        Files.createDirectories(config);
        Files.writeString(config.resolve("command_whitelist.yaml"),
                "allowedPrefixes: []\n",
                StandardCharsets.UTF_8);

        CommandWhitelist whitelist = new CommandWhitelist(tempDir);
        whitelist.reload();

        assertFalse(whitelist.isAllowed("say hello"));
        assertFalse(whitelist.isAllowed("/say hello"));
        assertFalse(whitelist.isAllowed("title @s actionbar Hello"));
    }
}
