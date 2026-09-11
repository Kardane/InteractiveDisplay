package com.interactivedisplay.gametest;

import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

public final class InteractiveDisplayCommandServerGameTest implements CustomTestMethodInvoker {
    @GameTest
    public void listDebugReloadAndGroupListExecuteOnLiveServer(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var source = server.createCommandSourceStack();

        assertCommand(helper, dispatcher.execute("interactivedisplay list", source), "list");
        assertCommand(helper, dispatcher.execute("interactivedisplay debug status", source), "debug status");
        assertCommand(helper, dispatcher.execute("interactivedisplay debug recent", source), "debug recent");
        assertCommand(helper, dispatcher.execute("interactivedisplay group list", source), "group list");
        assertCommand(helper, dispatcher.execute("interactivedisplay reload main_menu", source), "reload main_menu");

        helper.assertTrue(
                com.interactivedisplay.InteractiveDisplay.instance().windowManager().loadedWindowIds().contains("main_menu"),
                Component.literal("main_menu was not loaded after live focused reload command")
        );
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
