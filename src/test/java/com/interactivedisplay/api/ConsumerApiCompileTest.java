package com.interactivedisplay.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConsumerApiCompileTest {
    @Test
    void externalConsumerShouldCompileAgainstPublicApiOnly(@TempDir Path tempDir) throws Exception {
        String source = """
                package qa.consumer;

                import com.interactivedisplay.api.InteractiveDisplayEntrypoint;
                import com.interactivedisplay.api.InteractiveDisplayRegistrar;
                import com.interactivedisplay.api.window.WindowSpec;
                import net.minecraft.resources.ResourceLocation;

                public final class ConsumerEntrypoint implements InteractiveDisplayEntrypoint {
                    @Override
                    public void register(InteractiveDisplayRegistrar registrar) {
                        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("example", "status");
                        WindowSpec spec = WindowSpec.builder(id)
                                .size(2.0f, 1.0f)
                                .text("status", text -> text
                                        .content("Status: online")
                                        .fontSize(0.4f))
                                .build();
                        registrar.windows().register(spec);
                    }
                }
                """;

        assertFalse(source.contains("com.interactivedisplay.internal"));
        assertFalse(source.contains("com.interactivedisplay.core"));
        assertFalse(source.contains("polymer"));

        Path sourceRoot = tempDir.resolve("src/qa/consumer");
        Path output = tempDir.resolve("classes");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(output);
        Path sourceFile = sourceRoot.resolve("ConsumerEntrypoint.java");
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "tests require a JDK, not a JRE");
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            var units = fileManager.getJavaFileObjects(sourceFile.toFile());
            int result = compiler.run(
                    null,
                    null,
                    null,
                    "-proc:none",
                    "-classpath",
                    System.getProperty("java.class.path"),
                    "-d",
                    output.toString(),
                    sourceFile.toString()
            );
            assertEquals(0, result);
        }

        assertNotNull(Files.find(output, 8, (path, attrs) -> path.getFileName().toString().equals("ConsumerEntrypoint.class"))
                .findFirst()
                .orElse(null));
    }
}
