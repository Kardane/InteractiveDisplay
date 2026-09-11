package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.window.WindowGroupInstance;
import com.interactivedisplay.core.window.WindowInstance;
import com.interactivedisplay.entity.VirtualWindowHolder;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class InteractiveDisplayReloadDedupGameTest implements CustomTestMethodInvoker {
    @SuppressWarnings("removal")
    @GameTest
    public void reloadAllShouldPreserveActiveTopologyBindingsAndCleanOldRuntimes(GameTestHelper helper) {
        var manager = InteractiveDisplay.instance().windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        UUID owner = player.getUUID();

        var mainOpened = manager.createWindow(player, "main_menu", PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
        var galleryOpened = manager.createWindow(player, "gallery", PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
        var groupOpened = manager.createGroup(player, "menu_group", PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
        helper.assertTrue(mainOpened.success(), Component.literal("main_menu open failed before reload dedup test: " + mainOpened.message()));
        helper.assertTrue(galleryOpened.success(), Component.literal("gallery open failed before reload dedup test: " + galleryOpened.message()));
        helper.assertTrue(groupOpened.success(), Component.literal("menu_group open failed before reload dedup test: " + groupOpened.message()));

        WindowInstance mainBefore = manager.findActiveWindow(owner, "main_menu");
        WindowInstance galleryBefore = manager.findActiveWindow(owner, "gallery");
        WindowGroupInstance groupBefore = manager.findActiveGroup(owner, "menu_group");
        helper.assertTrue(mainBefore != null && galleryBefore != null && groupBefore != null,
                Component.literal("active topology missing before reload"));

        int ownerWindowCountBefore = manager.ownerWindows(owner).size();
        List<com.interactivedisplay.core.window.WindowManager.BindingSnapshot> bindingsBefore = List.copyOf(manager.bindingSnapshots(owner));
        helper.assertTrue(ownerWindowCountBefore == 3,
                Component.literal("expected two standalone windows plus one group current window before reload, got " + ownerWindowCountBefore));
        helper.assertTrue(!bindingsBefore.isEmpty(), Component.literal("expected interactive bindings before reload"));

        var mainOldHolder = mainBefore.virtualHolder();
        var galleryOldHolder = galleryBefore.virtualHolder();
        var groupOldHolder = groupBefore.currentWindow().virtualHolder();

        var reload = manager.reloadAll();
        helper.assertTrue(reload.success(), Component.literal("reloadAll failed with active windows/groups: " + reload.message()));

        WindowInstance mainAfter = manager.findActiveWindow(owner, "main_menu");
        WindowInstance galleryAfter = manager.findActiveWindow(owner, "gallery");
        WindowGroupInstance groupAfter = manager.findActiveGroup(owner, "menu_group");
        helper.assertTrue(mainAfter != null && galleryAfter != null && groupAfter != null,
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
        helper.assertTrue(groupAfter.currentWindow() != groupBefore.currentWindow(), Component.literal("group current-window runtime was not rebuilt"));
        helper.assertTrue(mainAfter.virtualHolder() != mainOldHolder, Component.literal("main_menu holder was reused across reload"));
        helper.assertTrue(galleryAfter.virtualHolder() != galleryOldHolder, Component.literal("gallery holder was reused across reload"));
        helper.assertTrue(groupAfter.currentWindow().virtualHolder() != groupOldHolder, Component.literal("group holder was reused across reload"));

        // Exit transitions may defer destruction. Flush the server-owned pending queue and verify
        // every superseded holder becomes empty rather than being retained indefinitely.
        VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
        helper.assertTrue(mainOldHolder.entityCount() == 0, Component.literal("old main_menu holder retained entities after reload cleanup"));
        helper.assertTrue(galleryOldHolder.entityCount() == 0, Component.literal("old gallery holder retained entities after reload cleanup"));
        helper.assertTrue(groupOldHolder.entityCount() == 0, Component.literal("old group holder retained entities after reload cleanup"));

        manager.removeAll(owner);
        VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
        helper.assertTrue(manager.ownerWindows(owner).isEmpty(), Component.literal("owner windows leaked after reload test cleanup"));
        helper.assertTrue(manager.bindingSnapshots(owner).isEmpty(), Component.literal("bindings leaked after reload test cleanup"));
        helper.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
