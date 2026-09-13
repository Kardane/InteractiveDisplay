package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.entity.VirtualWindowHolder;
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

public final class InteractiveDisplayRemoteMapFailureGameTest implements CustomTestMethodInvoker {
    private static final String WINDOW_ID = "qa_remote_failure";

    @SuppressWarnings("removal")
    @GameTest
    public void unreachableRemoteMapShouldFailCleanlyAndServerShouldRemainUsable(GameTestHelper helper) throws Exception {
        var manager = InteractiveDisplay.instance().windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Path windows = FabricLoader.getInstance().getConfigDir().resolve("interactivedisplay/windows");
        Path fixture = windows.resolve(WINDOW_ID + ".yaml");
        Files.createDirectories(windows);

        try {
            Files.writeString(fixture, remoteYaml("http://127.0.0.1:1/unreachable.png"), StandardCharsets.UTF_8);
            var failed = manager.reloadOne(WINDOW_ID);
            helper.assertTrue(!failed.success(), Component.literal("unreachable remote MAP unexpectedly loaded"));
            helper.assertTrue(manager.brokenWindowIds().contains(WINDOW_ID),
                    Component.literal("failed remote MAP was not tracked as broken"));
            helper.assertTrue(manager.findActiveWindow(player.getUUID(), WINDOW_ID) == null,
                    Component.literal("failed remote MAP created active runtime"));

            // A failed remote fetch must not poison the live manager or server tick path.
            manager.tick();
            var normalOpen = manager.createWindow(player, "main_menu", PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
            helper.assertTrue(normalOpen.success(),
                    Component.literal("normal window failed after remote MAP failure: " + normalOpen.message()));
            helper.assertTrue(manager.removeWindow(player.getUUID(), "main_menu").success(),
                    Component.literal("normal window cleanup failed after remote MAP failure"));
            VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());

            // Replace the failed URL with the generated local sample and prove focused recovery.
            Files.writeString(fixture, remoteYaml("sample_local.png"), StandardCharsets.UTF_8);
            var recovered = manager.reloadOne(WINDOW_ID);
            helper.assertTrue(recovered.success(),
                    Component.literal("remote MAP failure fixture did not recover after valid source: " + recovered.message()));
            helper.assertTrue(!manager.brokenWindowIds().contains(WINDOW_ID),
                    Component.literal("recovered MAP fixture remained in broken set"));

            var recoveredOpen = manager.createWindow(player, WINDOW_ID, PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
            helper.assertTrue(recoveredOpen.success(),
                    Component.literal("recovered MAP fixture failed to open: " + recoveredOpen.message()));
            helper.assertTrue(manager.removeWindow(player.getUUID(), WINDOW_ID).success(),
                    Component.literal("recovered MAP fixture failed to close"));
            VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
        } finally {
            manager.removeWindow(player.getUUID(), WINDOW_ID);
            Files.deleteIfExists(fixture);
            manager.reloadAll();
            VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
        }

        helper.succeed();
    }

    private static String remoteYaml(String value) {
        return """
                id: qa_remote_failure
                size: { width: 2.0, height: 1.5 }
                components:
                  - id: map
                    type: image
                    position: { x: 0.0, y: 0.0, z: 0.0 }
                    size: { width: 1.0, height: 1.0 }
                    imageType: map
                    value: "%s"
                    scale: 1.0
                """.formatted(value);
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
