package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.entity.VirtualWindowHolder;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class InteractiveDisplayPlaceholderIsolationGameTest implements CustomTestMethodInvoker {
    private static final String WINDOW_ID = "qa_placeholder_isolation";

    @SuppressWarnings("removal")
    @GameTest
    public void playerSpecificPlaceholderShouldRenderPerOwnerAndCleanUpAfterClose(GameTestHelper helper) throws Exception {
        var manager = InteractiveDisplay.instance().windowManager();
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        Path windows = FabricLoader.getInstance().getConfigDir()
                .resolve("interactivedisplay")
                .resolve("windows");
        Path windowFile = windows.resolve(WINDOW_ID + ".yaml");
        Files.createDirectories(windows);

        try {
            Files.writeString(windowFile, yaml(), StandardCharsets.UTF_8);
            var reload = manager.reloadOne(WINDOW_ID);
            helper.assertTrue(reload.success(), Component.literal("placeholder QA window failed to load: " + reload.message()));

            var firstOpen = manager.createWindow(first, WINDOW_ID, PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
            var secondOpen = manager.createWindow(second, WINDOW_ID, PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
            helper.assertTrue(firstOpen.success(), Component.literal("first owner placeholder window failed: " + firstOpen.message()));
            helper.assertTrue(secondOpen.success(), Component.literal("second owner placeholder window failed: " + secondOpen.message()));

            var firstInstance = manager.findActiveWindow(first.getUUID(), WINDOW_ID);
            var secondInstance = manager.findActiveWindow(second.getUUID(), WINDOW_ID);
            helper.assertTrue(firstInstance != null && secondInstance != null,
                    Component.literal("placeholder window missing for one of the owners"));

            var firstRuntime = firstInstance.runtime("owner_uuid");
            var secondRuntime = secondInstance.runtime("owner_uuid");
            helper.assertTrue(firstRuntime != null && secondRuntime != null,
                    Component.literal("placeholder text runtime missing"));
            helper.assertTrue(firstRuntime.displayElement() instanceof TextDisplayElement,
                    Component.literal("first placeholder runtime is not TextDisplayElement"));
            helper.assertTrue(secondRuntime.displayElement() instanceof TextDisplayElement,
                    Component.literal("second placeholder runtime is not TextDisplayElement"));

            String firstText = ((TextDisplayElement) firstRuntime.displayElement()).getText().getString();
            String secondText = ((TextDisplayElement) secondRuntime.displayElement()).getText().getString();
            helper.assertTrue(firstText.contains(first.getUUID().toString()),
                    Component.literal("first owner placeholder did not resolve its UUID: " + firstText));
            helper.assertTrue(secondText.contains(second.getUUID().toString()),
                    Component.literal("second owner placeholder did not resolve its UUID: " + secondText));
            helper.assertFalse(firstText.equals(secondText),
                    Component.literal("player-specific placeholders resolved to the same value for distinct owners"));

            var firstHolder = firstInstance.virtualHolder();
            var secondHolder = secondInstance.virtualHolder();
            helper.assertTrue(manager.removeWindow(first.getUUID(), WINDOW_ID).success(),
                    Component.literal("first placeholder window close failed"));
            helper.assertTrue(manager.removeWindow(second.getUUID(), WINDOW_ID).success(),
                    Component.literal("second placeholder window close failed"));
            VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());

            helper.assertTrue(firstHolder.entityCount() == 0 && secondHolder.entityCount() == 0,
                    Component.literal("placeholder holders retained virtual entities after close"));
            helper.assertTrue(manager.ownerWindows(first.getUUID()).isEmpty(),
                    Component.literal("first owner retained placeholder refresh/runtime state after close"));
            helper.assertTrue(manager.ownerWindows(second.getUUID()).isEmpty(),
                    Component.literal("second owner retained placeholder refresh/runtime state after close"));
        } finally {
            manager.removeAll(first.getUUID());
            manager.removeAll(second.getUUID());
            VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
            Files.deleteIfExists(windowFile);
            manager.reloadAll();
        }

        helper.succeed();
    }

    private static String yaml() {
        return """
                id: qa_placeholder_isolation
                size: { width: 3.0, height: 1.0 }
                components:
                  - id: owner_uuid
                    type: text
                    position: { x: 0.0, y: 0.0, z: 0.0 }
                    width: 2.8
                    height: 0.35
                    content: "Owner: %player:uuid%"
                    refreshInterval: 1
                    alignment: center
                    color: "#FFFFFF"
                """;
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
