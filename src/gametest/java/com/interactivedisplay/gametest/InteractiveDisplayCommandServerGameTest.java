package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.entity.VirtualWindowHolder;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class InteractiveDisplayCommandServerGameTest implements CustomTestMethodInvoker {
    @SuppressWarnings("removal")
    @GameTest
    public void createRemoveListDebugReloadAndGroupCommandsExecuteOnLiveServer(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var source = server.createCommandSourceStack();
        var manager = InteractiveDisplay.instance().windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        String playerName = player.getGameProfile().getName();

        assertCommand(helper, dispatcher.execute("interactivedisplay list", source), "list");
        assertCommand(helper, dispatcher.execute("interactivedisplay debug status", source), "debug status");
        assertCommand(helper, dispatcher.execute("interactivedisplay debug recent", source), "debug recent");
        assertCommand(helper, dispatcher.execute("interactivedisplay group list", source), "group list");
        assertCommand(helper, dispatcher.execute("interactivedisplay reload main_menu", source), "reload main_menu");
        helper.assertTrue(manager.loadedWindowIds().contains("main_menu"),
                Component.literal("main_menu was not loaded after live focused reload command"));

        assertCommand(helper,
                dispatcher.execute("interactivedisplay create main_menu " + playerName + " player_fixed", source),
                "create main_menu " + playerName + " player_fixed");
        helper.assertTrue(manager.findActiveWindow(player.getUUID(), "main_menu") != null,
                Component.literal("live create command did not create main_menu"));
        assertCommand(helper,
                dispatcher.execute("interactivedisplay debug window main_menu " + playerName, source),
                "debug window main_menu " + playerName);
        assertCommand(helper,
                dispatcher.execute("interactivedisplay debug bindings " + playerName, source),
                "debug bindings " + playerName);

        assertCommand(helper,
                dispatcher.execute("interactivedisplay remove main_menu " + playerName, source),
                "remove main_menu " + playerName);
        VirtualWindowHolder.destroyAllPending(server);
        helper.assertTrue(manager.findActiveWindow(player.getUUID(), "main_menu") == null,
                Component.literal("live remove command left main_menu active"));
        helper.assertTrue(manager.bindingSnapshots(player.getUUID()).isEmpty(),
                Component.literal("live remove command left window bindings"));

        assertCommand(helper,
                dispatcher.execute("interactivedisplay group create menu_group " + playerName + " player_fixed", source),
                "group create menu_group " + playerName + " player_fixed");
        helper.assertTrue(manager.findActiveGroup(player.getUUID(), "menu_group") != null,
                Component.literal("live group create command did not create menu_group"));
        assertCommand(helper,
                dispatcher.execute("interactivedisplay debug window main_menu " + playerName, source),
                "debug window main_menu " + playerName + " while grouped");
        assertCommand(helper,
                dispatcher.execute("interactivedisplay debug bindings " + playerName, source),
                "debug bindings " + playerName + " while grouped");

        assertCommand(helper,
                dispatcher.execute("interactivedisplay group remove menu_group " + playerName, source),
                "group remove menu_group " + playerName);
        VirtualWindowHolder.destroyAllPending(server);
        helper.assertTrue(manager.findActiveGroup(player.getUUID(), "menu_group") == null,
                Component.literal("live group remove command left menu_group active"));
        helper.assertTrue(manager.ownerWindows(player.getUUID()).isEmpty(),
                Component.literal("live group remove command left owner window state"));
        helper.assertTrue(manager.bindingSnapshots(player.getUUID()).isEmpty(),
                Component.literal("live group remove command left bindings"));

        helper.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }

    private static void assertCommand(GameTestHelper helper, int result, String command) {
        helper.assertTrue(result == 1, Component.literal("live command returned " + result + ": " + command));
    }
}
