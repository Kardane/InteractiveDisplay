package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.api.InteractiveDisplayApi;
import com.interactivedisplay.api.event.EventApi;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.window.WindowGroupInstance;
import com.interactivedisplay.core.window.WindowInstance;
import com.interactivedisplay.entity.VirtualWindowHolder;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class InteractiveDisplayReloadDedupGameTest implements CustomTestMethodInvoker {
    private static final String SECOND_GROUP_ID = "qa_reload_group";

    @SuppressWarnings("removal")
    @GameTest
    public void reloadAllShouldPreserveActiveTopologyBindingsAndCleanOldRuntimes(GameTestHelper helper) throws Exception {
        var manager = InteractiveDisplay.instance().windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        UUID owner = player.getUUID();
        Path groupsDir = FabricLoader.getInstance().getConfigDir().resolve("interactivedisplay").resolve("groups");
        Path secondGroupFile = groupsDir.resolve(SECOND_GROUP_ID + ".yaml");
        Files.createDirectories(groupsDir);
        Files.writeString(secondGroupFile, secondGroupYaml(), StandardCharsets.UTF_8);

        try {
            var fixtureReload = manager.reloadAll();
            helper.assertTrue(fixtureReload.success(), Component.literal("failed to load second QA group: " + fixtureReload.message()));
            helper.assertTrue(manager.loadedGroupIds().contains(SECOND_GROUP_ID), Component.literal("second QA group was not loaded"));

            var mainOpened = manager.createWindow(player, "main_menu", PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
            var galleryOpened = manager.createWindow(player, "gallery", PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
            var groupOpened = manager.createGroup(player, "menu_group", PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
            var secondGroupOpened = manager.createGroup(player, SECOND_GROUP_ID, PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
            helper.assertTrue(mainOpened.success(), Component.literal("main_menu open failed before reload dedup test: " + mainOpened.message()));
            helper.assertTrue(galleryOpened.success(), Component.literal("gallery open failed before reload dedup test: " + galleryOpened.message()));
            helper.assertTrue(groupOpened.success(), Component.literal("menu_group open failed before reload dedup test: " + groupOpened.message()));
            helper.assertTrue(secondGroupOpened.success(), Component.literal("second group open failed before reload dedup test: " + secondGroupOpened.message()));

            WindowInstance mainBefore = manager.findActiveWindow(owner, "main_menu");
            WindowInstance galleryBefore = manager.findActiveWindow(owner, "gallery");
            WindowGroupInstance groupBefore = manager.findActiveGroup(owner, "menu_group");
            WindowGroupInstance secondGroupBefore = manager.findActiveGroup(owner, SECOND_GROUP_ID);
            helper.assertTrue(mainBefore != null && galleryBefore != null && groupBefore != null && secondGroupBefore != null,
                    Component.literal("active topology missing before reload"));

            int ownerWindowCountBefore = manager.ownerWindows(owner).size();
            List<com.interactivedisplay.core.window.WindowManager.BindingSnapshot> bindingsBefore = List.copyOf(manager.bindingSnapshots(owner));
            helper.assertTrue(ownerWindowCountBefore == 4,
                    Component.literal("expected two standalone windows plus two group current windows before reload, got " + ownerWindowCountBefore));
            helper.assertTrue(!bindingsBefore.isEmpty(), Component.literal("expected interactive bindings before reload"));

            var mainOldHolder = mainBefore.virtualHolder();
            var galleryOldHolder = galleryBefore.virtualHolder();
            var groupOldHolder = groupBefore.currentWindow().virtualHolder();
            var secondGroupOldHolder = secondGroupBefore.currentWindow().virtualHolder();

            List<String> lifecycleEvents = new ArrayList<>();
            EventApi eventApi = InteractiveDisplayApi.get().events();
            EventApi.Subscription openedSubscription = eventApi.onWindowOpened(event -> {
                if (owner.equals(event.ownerId())) {
                    lifecycleEvents.add("opened:" + event.windowId());
                }
            });
            EventApi.Subscription closedSubscription = eventApi.onWindowClosed(event -> {
                if (owner.equals(event.ownerId())) {
                    lifecycleEvents.add("closed:" + event.windowId());
                }
            });
            try {
                var reload = manager.reloadAll();
                helper.assertTrue(reload.success(), Component.literal("reloadAll failed with active windows/groups: " + reload.message()));
                helper.assertTrue(lifecycleEvents.isEmpty(),
                        Component.literal("internal reload emitted public lifecycle events: " + lifecycleEvents));
            } finally {
                openedSubscription.close();
                closedSubscription.close();
            }

            WindowInstance mainAfter = manager.findActiveWindow(owner, "main_menu");
            WindowInstance galleryAfter = manager.findActiveWindow(owner, "gallery");
            WindowGroupInstance groupAfter = manager.findActiveGroup(owner, "menu_group");
            WindowGroupInstance secondGroupAfter = manager.findActiveGroup(owner, SECOND_GROUP_ID);
            helper.assertTrue(mainAfter != null && galleryAfter != null && groupAfter != null && secondGroupAfter != null,
                    Component.literal("active topology missing after reload"));
            helper.assertTrue(manager.ownerWindows(owner).size() == ownerWindowCountBefore,
                    Component.literal("owner active window count changed after reload"));

            var bindingsAfter = List.copyOf(manager.bindingSnapshots(owner));
            helper.assertTrue(bindingsAfter.size() == bindingsBefore.size(),
                    Component.literal("binding count changed after reload: before=" + bindingsBefore.size() + " after=" + bindingsAfter.size()));
            helper.assertTrue(new HashSet<>(bindingsAfter).equals(new HashSet<>(bindingsBefore)),
                    Component.literal("interaction bindings changed after reload"));

            helper.assertTrue(mainAfter != mainBefore, Component.literal("main_menu runtime was not rebuilt"));
            helper.assertTrue(galleryAfter != galleryBefore, Component.literal("gallery runtime was not rebuilt"));
            helper.assertTrue(groupAfter.currentWindow() != groupBefore.currentWindow(), Component.literal("menu_group current-window runtime was not rebuilt"));
            helper.assertTrue(secondGroupAfter.currentWindow() != secondGroupBefore.currentWindow(), Component.literal("second group current-window runtime was not rebuilt"));
            helper.assertTrue(mainAfter.virtualHolder() != mainOldHolder, Component.literal("main_menu holder was reused across reload"));
            helper.assertTrue(galleryAfter.virtualHolder() != galleryOldHolder, Component.literal("gallery holder was reused across reload"));
            helper.assertTrue(groupAfter.currentWindow().virtualHolder() != groupOldHolder, Component.literal("menu_group holder was reused across reload"));
            helper.assertTrue(secondGroupAfter.currentWindow().virtualHolder() != secondGroupOldHolder, Component.literal("second group holder was reused across reload"));

            VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
            helper.assertTrue(mainOldHolder.entityCount() == 0, Component.literal("old main_menu holder retained entities after reload cleanup"));
            helper.assertTrue(galleryOldHolder.entityCount() == 0, Component.literal("old gallery holder retained entities after reload cleanup"));
            helper.assertTrue(groupOldHolder.entityCount() == 0, Component.literal("old menu_group holder retained entities after reload cleanup"));
            helper.assertTrue(secondGroupOldHolder.entityCount() == 0, Component.literal("old second-group holder retained entities after reload cleanup"));
        } finally {
            manager.removeAll(owner);
            VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
            Files.deleteIfExists(secondGroupFile);
            manager.reloadAll();
        }

        helper.assertTrue(manager.ownerWindows(owner).isEmpty(), Component.literal("owner windows leaked after reload test cleanup"));
        helper.assertTrue(manager.bindingSnapshots(owner).isEmpty(), Component.literal("bindings leaked after reload test cleanup"));
        helper.succeed();
    }

    private static String secondGroupYaml() {
        return """
                id: qa_reload_group
                initialWindowId: main_menu2
                defaultMode: player_fixed
                windows:
                  - windowId: main_menu2
                    offset:
                      forward: 2.0
                      horizontal: -0.75
                      vertical: 0.5
                    orbit:
                      yaw: -25.0
                      pitch: 0.0
                """;
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
