package com.interactivedisplay.interaction;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.core.interaction.CommandWhitelist;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandWhitelistTest {
    @Test
    void slashPrefixedCommandShouldBeNormalized(@TempDir Path tempDir) throws Exception {
        Path config = tempDir.resolve("interactivedisplay");
        Files.createDirectories(config);
        Files.writeString(config.resolve("command_whitelist.json"), """
                {"allowedPrefixes":["say ","trigger "]}
                """, StandardCharsets.UTF_8);

        CommandWhitelist whitelist = new CommandWhitelist(tempDir);
        whitelist.reload();

        assertTrue(whitelist.isAllowed("/say hello"));
        assertTrue(whitelist.isAllowed("trigger test"));
        assertFalse(whitelist.isAllowed("/op Steve"));
    }
}
