package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.api.InteractiveDisplayApi;
import com.interactivedisplay.api.window.WindowOpenOptions;
import com.interactivedisplay.api.window.WindowSpec;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class InteractiveDisplayInvalidResourceGameTest implements CustomTestMethodInvoker {
    @SuppressWarnings("removal")
    @GameTest
    public void invalidProgrammaticItemAndBlockIdsShouldFailOpenWithoutLeakingState(GameTestHelper helper) {
        InteractiveDisplayApi api = InteractiveDisplayApi.get();
        var manager = InteractiveDisplay.instance().windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        ResourceLocation missingItem = ResourceLocation.fromNamespaceAndPath("qa", "definitely_missing_item");
        ResourceLocation missingBlock = ResourceLocation.fromNamespaceAndPath("qa", "definitely_missing_block");
        ResourceLocation itemWindow = ResourceLocation.fromNamespaceAndPath("qa", "invalid_item_window");
        ResourceLocation blockWindow = ResourceLocation.fromNamespaceAndPath("qa", "invalid_block_window");

        WindowSpec itemSpec = WindowSpec.builder(itemWindow)
                .item("icon", missingItem, image -> image.size(1.0f, 1.0f))
                .build();
        WindowSpec blockSpec = WindowSpec.builder(blockWindow)
                .block("icon", missingBlock, image -> image.size(1.0f, 1.0f))
                .build();

        var itemRegistration = api.windows().register(itemSpec);
        helper.assertTrue(itemRegistration.success(),
                Component.literal("invalid-item WindowSpec registration itself should remain structurally valid: " + itemRegistration.message()));
        var itemOpen = api.windows().open(player, itemWindow, WindowOpenOptions.playerFixed());
        helper.assertFalse(itemOpen.success(), Component.literal("missing item ID unexpectedly opened a window"));
        helper.assertFalse(api.windows().isOpen(player, itemWindow), Component.literal("missing item ID left public active state"));
        helper.assertTrue(manager.findActiveWindow(player.getUUID(), "qa:invalid_item_window") == null,
                Component.literal("missing item ID left WindowManager active state"));

        var blockRegistration = api.windows().register(blockSpec);
        helper.assertTrue(blockRegistration.success(),
                Component.literal("invalid-block WindowSpec registration itself should remain structurally valid: " + blockRegistration.message()));
        var blockOpen = api.windows().open(player, blockWindow, WindowOpenOptions.playerFixed());
        helper.assertFalse(blockOpen.success(), Component.literal("missing block ID unexpectedly opened a window"));
        helper.assertFalse(api.windows().isOpen(player, blockWindow), Component.literal("missing block ID left public active state"));
        helper.assertTrue(manager.findActiveWindow(player.getUUID(), "qa:invalid_block_window") == null,
                Component.literal("missing block ID left WindowManager active state"));

        helper.assertTrue(manager.ownerWindows(player.getUUID()).isEmpty(),
                Component.literal("invalid resource opens leaked owner window state"));
        helper.assertTrue(manager.bindingSnapshots(player.getUUID()).isEmpty(),
                Component.literal("invalid resource opens leaked bindings"));
        helper.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
