package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.api.InteractiveDisplayApi;
import com.interactivedisplay.api.window.WindowSpec;
import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.window.WindowComponentRuntime;
import com.interactivedisplay.debug.DebugRecorder;
import com.interactivedisplay.entity.DisplayEntityFactory;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.lang.reflect.Method;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

public final class InteractiveDisplayServerGameTest implements CustomTestMethodInvoker {
    @GameTest
    public void runtimeBootsAndLoadsDefaultDefinitions(GameTestHelper helper) {
        InteractiveDisplay mod = InteractiveDisplay.instance();
        helper.assertTrue(mod != null, "InteractiveDisplay initializer did not run");
        helper.assertTrue(mod.windowManager() != null, "WindowManager was not attached after server start");

        Set<String> windows = mod.windowManager().loadedWindowIds();
        helper.assertTrue(windows.contains("main_menu"), "default main_menu window was not loaded");
        helper.assertTrue(windows.contains("main_menu2"), "default main_menu2 window was not loaded");
        helper.assertTrue(windows.contains("gallery"), "default gallery window was not loaded");
        helper.assertTrue(mod.windowManager().loadedGroupIds().contains("menu_group"), "default menu_group was not loaded");
        helper.assertTrue(mod.windowManager().brokenWindowIds().isEmpty(), "default windows contain broken definitions: " + mod.windowManager().brokenWindowIds());
        helper.assertTrue(mod.windowManager().brokenGroupIds().isEmpty(), "default groups contain broken definitions: " + mod.windowManager().brokenGroupIds());
        helper.succeed();
    }

    @GameTest
    public void publicApiIsAvailableAndRegistersWindowAgainstLiveManager(GameTestHelper helper) {
        InteractiveDisplayApi api = InteractiveDisplayApi.get();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("qa", "gametest_runtime");
        WindowSpec spec = WindowSpec.builder(id)
                .size(2.0f, 1.0f)
                .text("status", text -> text.content("server gametest"))
                .build();

        var result = api.windows().register(spec);

        helper.assertTrue(result.success(), "live public API window registration failed: " + result.message());
        helper.assertTrue(api.windows().registeredIds().contains(id), "registeredIds did not contain live registration");
        helper.assertTrue(InteractiveDisplay.instance().windowManager().hasDefinition("qa:gametest_runtime"), "WindowManager did not receive public API registration");
        helper.succeed();
    }

    @GameTest
    public void commandTreeAndPolymerTextRefreshWorkAfterBootstrap(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(
                server.getCommands().getDispatcher().getRoot().getChild("interactivedisplay") != null,
                "interactivedisplay command root was not registered"
        );

        TextDisplayElement element = new TextDisplayElement();
        element.setText(Component.literal("same"));
        TextComponentDefinition original = text("same");
        WindowComponentRuntime runtime = new WindowComponentRuntime(
                Level.OVERWORLD,
                original,
                new Vector3f(),
                element,
                null
        );
        DisplayEntityFactory factory = new DisplayEntityFactory(new DebugRecorder(20));

        helper.assertFalse(factory.refreshText(server, null, runtime, original), "unchanged rendered text should not mark an update");
        TextComponentDefinition changed = text("changed");
        helper.assertTrue(factory.refreshText(server, null, runtime, changed), "changed rendered text should update the element");
        helper.assertTrue("changed".equals(element.getText().getString()), "TextDisplayElement did not receive refreshed text");
        helper.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }

    private static TextComponentDefinition text(String content) {
        return new TextComponentDefinition(
                "status",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(1.0f, 0.3f),
                true,
                1.0f,
                content,
                0.5f,
                "#FFFFFF",
                "left",
                200,
                true,
                "#00000000",
                1
        );
    }
}
