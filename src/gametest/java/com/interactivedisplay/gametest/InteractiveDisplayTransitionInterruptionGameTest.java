package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ClickType;
import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.interaction.ClickHandler;
import com.interactivedisplay.core.interaction.UiHitResult;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.window.WindowComponentRuntime;
import com.interactivedisplay.core.window.WindowNavigationContext;
import com.interactivedisplay.debug.DebugRecorder;
import com.interactivedisplay.entity.VirtualWindowHolder;
import java.lang.reflect.Field;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class InteractiveDisplayTransitionInterruptionGameTest implements CustomTestMethodInvoker {
    private static final String SOURCE_ID = "qa_transition_interrupt_source";
    private static final String TARGET_ID = "qa_transition_interrupt_target";

    @SuppressWarnings("removal")
    @GameTest
    public void closeNavigationAndReloadShouldCleanTransitioningRuntimes(GameTestHelper helper) throws Exception {
        var manager = InteractiveDisplay.instance().windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Path windowsDir = FabricLoader.getInstance().getConfigDir().resolve("interactivedisplay").resolve("windows");
        Path sourceFile = windowsDir.resolve(SOURCE_ID + ".yaml");
        Path targetFile = windowsDir.resolve(TARGET_ID + ".yaml");
        Files.createDirectories(windowsDir);
        Files.writeString(sourceFile, windowYaml(SOURCE_ID), StandardCharsets.UTF_8);
        Files.writeString(targetFile, windowYaml(TARGET_ID), StandardCharsets.UTF_8);

        try {
            var fixtureReload = manager.reloadAll();
            helper.assertTrue(fixtureReload.success(), Component.literal("transition interruption fixtures failed to load: " + fixtureReload.message()));
            helper.assertTrue(manager.loadedWindowIds().contains(SOURCE_ID) && manager.loadedWindowIds().contains(TARGET_ID),
                    Component.literal("transition interruption fixtures were not loaded"));

            verifyImmediateClose(helper, manager, player);
            verifyImmediateNavigation(helper, manager, player);
            verifyImmediateReload(helper, manager, player);
        } finally {
            manager.removeAll(player.getUUID());
            VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
            Files.deleteIfExists(sourceFile);
            Files.deleteIfExists(targetFile);
            manager.reloadAll();
        }

        helper.assertTrue(manager.ownerWindows(player.getUUID()).isEmpty(),
                Component.literal("transition interruption fixture leaked owner windows"));
        helper.assertTrue(manager.bindingSnapshots(player.getUUID()).isEmpty(),
                Component.literal("transition interruption fixture leaked bindings"));
        helper.succeed();
    }

    private static void verifyImmediateClose(
            GameTestHelper helper,
            com.interactivedisplay.core.window.WindowManager manager,
            ServerPlayer player
    ) throws Exception {
        var opened = manager.createWindow(player, SOURCE_ID, PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
        helper.assertTrue(opened.success(), Component.literal("transition close fixture open failed: " + opened.message()));
        var instance = manager.findActiveWindow(player.getUUID(), SOURCE_ID);
        helper.assertTrue(instance != null, Component.literal("transition close fixture runtime missing"));
        VirtualWindowHolder oldHolder = instance.virtualHolder();

        var removed = manager.removeWindow(player.getUUID(), SOURCE_ID);
        helper.assertTrue(removed.success(), Component.literal("transition close failed: " + removed.message()));
        helper.assertTrue(manager.findActiveWindow(player.getUUID(), SOURCE_ID) == null,
                Component.literal("transition close left source runtime active"));
        assertPendingExit(helper, oldHolder, "immediate close");

        VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
        helper.assertTrue(oldHolder.entityCount() == 0,
                Component.literal("transition close old holder retained entities after pending cleanup"));
    }

    private static void verifyImmediateNavigation(
            GameTestHelper helper,
            com.interactivedisplay.core.window.WindowManager manager,
            ServerPlayer player
    ) throws Exception {
        var opened = manager.createWindow(player, SOURCE_ID, PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
        helper.assertTrue(opened.success(), Component.literal("transition navigation fixture open failed: " + opened.message()));
        var source = manager.findActiveWindow(player.getUUID(), SOURCE_ID);
        helper.assertTrue(source != null, Component.literal("transition navigation source runtime missing"));
        VirtualWindowHolder oldHolder = source.virtualHolder();

        ClickHandler clickHandler = new ClickHandler(manager, new DebugRecorder(40));
        var result = clickHandler.handle(
                player.getUUID(),
                player.getGameProfile().getName(),
                openHit(source.currentAnchor(), source.currentYaw(), source.currentPitch())
        );
        helper.assertTrue(result.consumed(), Component.literal("transition navigation open_window was not consumed"));
        helper.assertTrue(manager.findActiveWindow(player.getUUID(), SOURCE_ID) == null,
                Component.literal("transition navigation left source runtime active"));
        helper.assertTrue(manager.findActiveWindow(player.getUUID(), TARGET_ID) != null,
                Component.literal("transition navigation did not create target runtime"));
        assertPendingExit(helper, oldHolder, "immediate navigation");

        var removed = manager.removeWindow(player.getUUID(), TARGET_ID);
        helper.assertTrue(removed.success(), Component.literal("transition navigation target cleanup failed"));
        VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
        helper.assertTrue(oldHolder.entityCount() == 0,
                Component.literal("transition navigation source holder retained entities after cleanup"));
    }

    private static void verifyImmediateReload(
            GameTestHelper helper,
            com.interactivedisplay.core.window.WindowManager manager,
            ServerPlayer player
    ) throws Exception {
        var opened = manager.createWindow(player, SOURCE_ID, PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
        helper.assertTrue(opened.success(), Component.literal("transition reload fixture open failed: " + opened.message()));
        var before = manager.findActiveWindow(player.getUUID(), SOURCE_ID);
        helper.assertTrue(before != null, Component.literal("transition reload runtime missing before reload"));
        VirtualWindowHolder oldHolder = before.virtualHolder();

        var reload = manager.reloadAll();
        helper.assertTrue(reload.success(), Component.literal("reloadAll failed during transition fixture: " + reload.message()));
        var after = manager.findActiveWindow(player.getUUID(), SOURCE_ID);
        helper.assertTrue(after != null && after != before,
                Component.literal("transition reload did not replace active runtime"));
        helper.assertTrue(after.virtualHolder() != oldHolder,
                Component.literal("transition reload reused old holder"));
        assertPendingExit(helper, oldHolder, "immediate reload");

        VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
        helper.assertTrue(oldHolder.entityCount() == 0,
                Component.literal("transition reload old holder retained entities after pending cleanup"));
        helper.assertTrue(after.entityCount() > 0,
                Component.literal("transition reload cleanup destroyed replacement runtime"));

        var removed = manager.removeWindow(player.getUUID(), SOURCE_ID);
        helper.assertTrue(removed.success(), Component.literal("transition reload replacement cleanup failed"));
        VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
    }

    private static void assertPendingExit(GameTestHelper helper, VirtualWindowHolder holder, String label) throws Exception {
        helper.assertTrue(holder.entityCount() > 0,
                Component.literal(label + " destroyed holder immediately instead of playing exit transition"));
        helper.assertTrue(booleanField(holder, "pendingDestroy"),
                Component.literal(label + " did not leave holder in pending-destroy state"));
    }

    private static boolean booleanField(VirtualWindowHolder holder, String name) throws Exception {
        Field field = VirtualWindowHolder.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(holder);
    }

    private static UiHitResult openHit(Vec3 anchor, float yaw, float pitch) {
        ComponentAction action = ComponentAction.openWindow(TARGET_ID);
        ButtonComponentDefinition button = new ButtonComponentDefinition(
                "navigate",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(1.0f, 0.35f),
                true,
                1.0f,
                "Navigate",
                0.5f,
                "#CC222222",
                "#EE444444",
                null,
                ClickType.RIGHT,
                action
        );
        WindowComponentRuntime runtime = new WindowComponentRuntime(Level.OVERWORLD, button, new Vector3f(), null, null);
        WindowNavigationContext context = new WindowNavigationContext(
                SOURCE_ID,
                null,
                PositionMode.PLAYER_FIXED,
                anchor,
                yaw,
                pitch
        );
        return new UiHitResult(SOURCE_ID, context, "navigate", runtime, action, Vec3.ZERO, 1.0D);
    }

    private static String windowYaml(String id) {
        return """
                id: %s
                size:
                  width: 2.0
                  height: 1.0
                layout: absolute
                transition:
                  duration: 40
                  enter: scale
                  exit: slide_down
                components:
                  - id: label
                    type: text
                    position:
                      x: 0.0
                      y: 0.0
                      z: 0.0
                    size:
                      width: 1.0
                      height: 0.25
                    content: "transition interruption fixture"
                """.formatted(id);
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
