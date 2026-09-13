package com.interactivedisplay.polymer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourcePackBootstrapTest {
    @Test
    void bootstrapShouldBeReadyOnlyWhenAssetsAndBuildSucceed(@TempDir Path tempDir) {
        FakePolymerBridge bridge = new FakePolymerBridge();
        ResourcePackBootstrap bootstrap = new ResourcePackBootstrap(new PolymerConfigEnsurer(tempDir), bridge);

        bootstrap.prepareFiles();
        boolean ready = bootstrap.bootstrap("interactivedisplay");

        assertTrue(ready);
        assertTrue(bootstrap.ready());
        assertEquals("interactivedisplay", bridge.lastModId);
        assertTrue(bridge.autoHostEnabled);
        assertTrue(bridge.packRequired);
    }

    @Test
    void bootstrapShouldFallbackToNotReadyOnFailure(@TempDir Path tempDir) {
        FakePolymerBridge bridge = new FakePolymerBridge();
        bridge.buildResult = false;
        ResourcePackBootstrap bootstrap = new ResourcePackBootstrap(new PolymerConfigEnsurer(tempDir), bridge);

        boolean ready = bootstrap.bootstrap("interactivedisplay");

        assertFalse(ready);
        assertFalse(bootstrap.ready());
    }

    @Test
    void bootstrapShouldNotModifyOperatorImageDirectory(@TempDir Path tempDir) throws Exception {
        Path images = tempDir.resolve("interactivedisplay/images");
        Files.createDirectories(images);
        Path sentinel = images.resolve("operator-map.png");
        byte[] original = new byte[]{1, 2, 3, 4, 5};
        Files.write(sentinel, original);

        FakePolymerBridge bridge = new FakePolymerBridge();
        ResourcePackBootstrap bootstrap = new ResourcePackBootstrap(new PolymerConfigEnsurer(tempDir), bridge);
        bootstrap.prepareFiles();
        assertTrue(bootstrap.bootstrap("interactivedisplay"));

        assertTrue(Files.exists(sentinel));
        assertArrayEquals(original, Files.readAllBytes(sentinel));
    }

    private static final class FakePolymerBridge extends PolymerBridge {
        boolean autoHostEnabled;
        boolean packRequired;
        boolean buildResult = true;
        String lastModId;

        @Override
        public void enableAutoHost() {
            this.autoHostEnabled = true;
        }

        @Override
        public boolean addModAssets(String modId) {
            this.lastModId = modId;
            return true;
        }

        @Override
        public void markPackRequired() {
            this.packRequired = true;
        }

        @Override
        public boolean buildMain() {
            return this.buildResult;
        }
    }
}
