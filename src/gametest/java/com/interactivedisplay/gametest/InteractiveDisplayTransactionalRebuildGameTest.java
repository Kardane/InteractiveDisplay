package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.entity.VirtualWindowHolder;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class InteractiveDisplayTransactionalRebuildGameTest implements CustomTestMethodInvoker {
    private static final String WINDOW_ID = "qa_transaction_rebuild";

    @SuppressWarnings("removal")
    @GameTest
    public void failedReplacementShouldPreserveOldRuntimeAndSuccessfulRetryShouldSwapAtomically(GameTestHelper helper) throws Exception {
        var manager = InteractiveDisplay.instance().windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Path configRoot = FabricLoader.getInstance().getConfigDir().resolve("interactivedisplay");
        Path windows = configRoot.resolve("windows");
        Path images = configRoot.resolve("images");
        Path windowFile = windows.resolve(WINDOW_ID + ".yaml");
        Path imageFile = images.resolve(WINDOW_ID + ".png");
        Path sourceImage = images.resolve("sample_local.png");

        Files.createDirectories(windows);
        Files.createDirectories(images);
        helper.assertTrue(Files.isRegularFile(sourceImage), Component.literal("bundled sample_local.png is missing"));

        try {
            Files.copy(sourceImage, imageFile, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(windowFile, yaml(), StandardCharsets.UTF_8);

            var loaded = manager.reloadOne(WINDOW_ID);
            helper.assertTrue(loaded.success(), Component.literal("QA transactional window did not load: " + loaded.message()));

            var opened = manager.createWindow(player, WINDOW_ID, PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
            helper.assertTrue(opened.success(), Component.literal("initial QA window open failed: " + opened.message()));
            var original = manager.findActiveWindow(player.getUUID(), WINDOW_ID);
            helper.assertTrue(original != null, Component.literal("initial active QA window missing"));
            var originalHolder = original.virtualHolder();
            int originalEntityCount = originalHolder.entityCount();
            List<com.interactivedisplay.core.window.WindowManager.BindingSnapshot> originalBindings = List.copyOf(manager.bindingSnapshots(player.getUUID()));
            helper.assertTrue(originalEntityCount > 0, Component.literal("initial QA window created no virtual entities"));
            helper.assertTrue(originalBindings.stream().anyMatch(binding -> WINDOW_ID.equals(binding.windowId()) && "close".equals(binding.componentId())),
                    Component.literal("initial QA close-button binding missing"));

            // The already-loaded definition still points at this path. Removing only the image makes
            // replacement spawning fail after the interactive button runtime has started to build.
            Files.delete(imageFile);
            var failed = manager.rebuildWindow(player.getUUID(), WINDOW_ID);
            helper.assertFalse(failed.success(), Component.literal("replacement rebuild unexpectedly succeeded without MAP source"));
            helper.assertTrue(failed.reasonCode() == DebugReason.ENTITY_SPAWN_FAILED,
                    Component.literal("replacement failure returned wrong reason: " + failed.reasonCode()));

            var afterFailure = manager.findActiveWindow(player.getUUID(), WINDOW_ID);
            helper.assertTrue(afterFailure == original, Component.literal("failed replacement swapped out the original WindowInstance"));
            helper.assertTrue(afterFailure.virtualHolder() == originalHolder, Component.literal("failed replacement swapped out the original holder"));
            helper.assertTrue(originalHolder.entityCount() == originalEntityCount,
                    Component.literal("failed replacement damaged original virtual entities"));
            helper.assertTrue(manager.bindingSnapshots(player.getUUID()).equals(originalBindings),
                    Component.literal("failed replacement changed original interaction bindings"));

            Files.copy(sourceImage, imageFile, StandardCopyOption.REPLACE_EXISTING);
            var recovered = manager.rebuildWindow(player.getUUID(), WINDOW_ID);
            helper.assertTrue(recovered.success(), Component.literal("replacement rebuild did not recover after MAP source restore: " + recovered.message()));

            var replacement = manager.findActiveWindow(player.getUUID(), WINDOW_ID);
            helper.assertTrue(replacement != null && replacement != original,
                    Component.literal("successful rebuild did not atomically replace WindowInstance"));
            helper.assertTrue(replacement.virtualHolder() != originalHolder,
                    Component.literal("successful rebuild reused old virtual holder"));
            helper.assertTrue(manager.bindingSnapshots(player.getUUID()).equals(originalBindings),
                    Component.literal("successful rebuild changed interaction bindings"));

            VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
            helper.assertTrue(originalHolder.entityCount() == 0,
                    Component.literal("old holder retained entities after successful atomic swap cleanup"));
        } finally {
            manager.removeWindow(player.getUUID(), WINDOW_ID);
            VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
            Files.deleteIfExists(windowFile);
            Files.deleteIfExists(imageFile);
            manager.reloadAll();
        }

        helper.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }

    private static String yaml() {
        return """
                id: qa_transaction_rebuild
                size: { width: 3.0, height: 2.0 }
                components:
                  - id: close
                    type: button
                    position: { x: 0.0, y: -0.5, z: 0.01 }
                    size: { width: 0.6, height: 0.35 }
                    label: Close
                    clickType: both
                    action:
                      type: close_window
                  - id: map
                    type: image
                    position: { x: 0.0, y: 0.2, z: 0.0 }
                    size: { width: 1.0, height: 1.0 }
                    imageType: map
                    value: qa_transaction_rebuild.png
                    scale: 1.0
                """;
    }
}
