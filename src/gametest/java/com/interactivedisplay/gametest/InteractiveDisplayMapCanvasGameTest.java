package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.entity.VirtualWindowHolder;
import eu.pb4.mapcanvas.api.core.PlayerCanvas;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class InteractiveDisplayMapCanvasGameTest implements CustomTestMethodInvoker {
    private static final String WINDOW_ID = "qa_map_dirty_flush";
    private static final String IMAGE_NAME = "qa_map_dirty_flush.png";

    @SuppressWarnings("removal")
    @GameTest
    public void dirtyMapCanvasesShouldFlushOnManagerTickAndDestroyOnClose(GameTestHelper helper) throws Exception {
        var manager = InteractiveDisplay.instance().windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("interactivedisplay");
        Path windowsDir = configDir.resolve("windows");
        Path imagesDir = configDir.resolve("images");
        Path windowFile = windowsDir.resolve(WINDOW_ID + ".yaml");
        Path imageFile = imagesDir.resolve(IMAGE_NAME);
        Files.createDirectories(windowsDir);
        Files.createDirectories(imagesDir);

        try {
            BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < image.getWidth(); x++) {
                for (int y = 0; y < image.getHeight(); y++) {
                    image.setRGB(x, y, 0xFF336699);
                }
            }
            ImageIO.write(image, "png", imageFile.toFile());
            Files.writeString(windowFile, yaml(), StandardCharsets.UTF_8);

            var reload = manager.reloadOne(WINDOW_ID);
            helper.assertTrue(reload.success(), Component.literal("MAP dirty-flush QA window failed to load: " + reload.message()));
            var opened = manager.createWindow(player, WINDOW_ID, PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
            helper.assertTrue(opened.success(), Component.literal("MAP dirty-flush QA window failed to open: " + opened.message()));

            var instance = manager.findActiveWindow(player.getUUID(), WINDOW_ID);
            helper.assertTrue(instance != null, Component.literal("MAP dirty-flush active window missing"));
            var runtimeA = instance.runtime("map_a");
            var runtimeB = instance.runtime("map_b");
            helper.assertTrue(runtimeA != null && runtimeB != null,
                    Component.literal("MAP dirty-flush runtimes missing"));
            PlayerCanvas canvasA = runtimeA.mapCanvas();
            PlayerCanvas canvasB = runtimeB.mapCanvas();
            helper.assertTrue(canvasA != null && canvasB != null,
                    Component.literal("MAP dirty-flush PlayerCanvas missing"));
            helper.assertTrue(!canvasA.isDirty() && !canvasB.isDirty(),
                    Component.literal("initial MAP canvas remained dirty after creation sync"));

            canvasA.setRaw(0, 0, different(canvasA.getRaw(0, 0)));
            canvasB.setRaw(127, 127, different(canvasB.getRaw(127, 127)));
            helper.assertTrue(canvasA.isDirty() && canvasB.isDirty(),
                    Component.literal("setRaw did not mark both MAP canvases dirty"));

            manager.tick();
            helper.assertTrue(!canvasA.isDirty() && !canvasB.isDirty(),
                    Component.literal("WindowManager.tick did not flush all dirty MAP canvases"));

            canvasA.setRaw(64, 64, different(canvasA.getRaw(64, 64)));
            helper.assertTrue(canvasA.isDirty(), Component.literal("second MAP update did not mark canvas dirty"));
            manager.tick();
            helper.assertTrue(!canvasA.isDirty(), Component.literal("second MAP update was not flushed"));

            helper.assertTrue(manager.removeWindow(player.getUUID(), WINDOW_ID).success(),
                    Component.literal("MAP dirty-flush window close failed"));
            VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
            helper.assertTrue(canvasA.isDestroyed() && canvasB.isDestroyed(),
                    Component.literal("MAP canvases were not destroyed on window close"));
            helper.assertTrue(manager.ownerWindows(player.getUUID()).isEmpty(),
                    Component.literal("MAP dirty-flush owner runtime leaked after close"));
        } finally {
            manager.removeAll(player.getUUID());
            VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
            Files.deleteIfExists(windowFile);
            Files.deleteIfExists(imageFile);
            manager.reloadAll();
        }

        helper.succeed();
    }

    private static byte different(byte value) {
        return value == Byte.MAX_VALUE ? (byte) 0 : (byte) (value + 1);
    }

    private static String yaml() {
        return """
                id: qa_map_dirty_flush
                size: { width: 3.0, height: 1.5 }
                components:
                  - id: map_a
                    type: image
                    position: { x: -0.6, y: 0.0, z: 0.0 }
                    size: { width: 1.0, height: 1.0 }
                    imageType: map
                    value: qa_map_dirty_flush.png
                    scale: 1.0
                  - id: map_b
                    type: image
                    position: { x: 0.6, y: 0.0, z: 0.0 }
                    size: { width: 1.0, height: 1.0 }
                    imageType: map
                    value: qa_map_dirty_flush.png
                    scale: 1.0
                """;
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
