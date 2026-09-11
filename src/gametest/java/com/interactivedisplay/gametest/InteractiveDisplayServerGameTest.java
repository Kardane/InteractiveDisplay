package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.api.InteractiveDisplayApi;
import com.interactivedisplay.api.event.EventApi;
import com.interactivedisplay.api.group.GroupOpenOptions;
import com.interactivedisplay.api.window.WindowOpenOptions;
import com.interactivedisplay.api.window.WindowPositionMode;
import com.interactivedisplay.api.window.WindowSpec;
import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.window.WindowComponentRuntime;
import com.interactivedisplay.debug.DebugRecorder;
import com.interactivedisplay.entity.DisplayEntityFactory;
import com.interactivedisplay.internal.api.PublicActionDispatcher;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class InteractiveDisplayServerGameTest implements CustomTestMethodInvoker {
    @GameTest
    public void runtimeBootsAndLoadsDefaultDefinitions(GameTestHelper helper) {
        InteractiveDisplay mod = InteractiveDisplay.instance();
        helper.assertTrue(mod != null, Component.literal("InteractiveDisplay initializer did not run"));
        helper.assertTrue(mod.windowManager() != null, Component.literal("WindowManager was not attached after server start"));

        Set<String> windows = mod.windowManager().loadedWindowIds();
        helper.assertTrue(windows.contains("main_menu"), Component.literal("default main_menu window was not loaded"));
        helper.assertTrue(windows.contains("main_menu2"), Component.literal("default main_menu2 window was not loaded"));
        helper.assertTrue(windows.contains("gallery"), Component.literal("default gallery window was not loaded"));
        helper.assertTrue(mod.windowManager().loadedGroupIds().contains("menu_group"), Component.literal("default menu_group was not loaded"));
        helper.assertTrue(mod.windowManager().brokenWindowIds().isEmpty(), Component.literal("default windows contain broken definitions: " + mod.windowManager().brokenWindowIds()));
        helper.assertTrue(mod.windowManager().brokenGroupIds().isEmpty(), Component.literal("default groups contain broken definitions: " + mod.windowManager().brokenGroupIds()));
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

        helper.assertTrue(result.success(), Component.literal("live public API window registration failed: " + result.message()));
        helper.assertTrue(api.windows().registeredIds().contains(id), Component.literal("registeredIds did not contain live registration"));
        helper.assertTrue(InteractiveDisplay.instance().windowManager().hasDefinition("qa:gametest_runtime"), Component.literal("WindowManager did not receive public API registration"));
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest
    public void publicApisOperateAgainstConnectedServerPlayer(GameTestHelper helper) {
        InteractiveDisplayApi api = InteractiveDisplayApi.get();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ResourceLocation main = ResourceLocation.fromNamespaceAndPath(InteractiveDisplay.MOD_ID, "main_menu");
        ResourceLocation second = ResourceLocation.fromNamespaceAndPath(InteractiveDisplay.MOD_ID, "main_menu2");
        ResourceLocation groupId = ResourceLocation.fromNamespaceAndPath(InteractiveDisplay.MOD_ID, "menu_group");
        ResourceLocation unknown = ResourceLocation.fromNamespaceAndPath(InteractiveDisplay.MOD_ID, "qa_missing_window");
        ResourceLocation unknownGroup = ResourceLocation.fromNamespaceAndPath(InteractiveDisplay.MOD_ID, "qa_missing_group");

        List<EventApi.WindowEvent> opened = new ArrayList<>();
        List<EventApi.WindowEvent> closed = new ArrayList<>();
        EventApi.Subscription openSubscription = api.events().onWindowOpened(opened::add);
        EventApi.Subscription closeSubscription = api.events().onWindowClosed(closed::add);
        try {
            var missing = api.windows().open(player, unknown, WindowOpenOptions.playerFixed());
            helper.assertFalse(missing.success(), Component.literal("unknown window unexpectedly opened"));
            helper.assertTrue("window_not_found".equals(missing.reason()), Component.literal("unknown window returned wrong reason: " + missing.reason()));

            var missingGroup = api.groups().open(player, unknownGroup, GroupOpenOptions.playerFixed());
            helper.assertFalse(missingGroup.success(), Component.literal("unknown group unexpectedly opened"));
            helper.assertTrue("group_not_found".equals(missingGroup.reason()), Component.literal("unknown group returned wrong reason: " + missingGroup.reason()));

            assertWindowModeRoundTrip(helper, api, player, main, WindowOpenOptions.playerFixed(), WindowPositionMode.PLAYER_FIXED);
            assertWindowModeRoundTrip(helper, api, player, main, WindowOpenOptions.playerView(), WindowPositionMode.PLAYER_VIEW);
            assertWindowModeRoundTrip(helper, api, player, main, WindowOpenOptions.fixed(new Vec3(8.0, 70.0, 8.0), 25.0f, -10.0f), WindowPositionMode.FIXED);

            int closedBeforeCloseAll = closed.size();
            helper.assertTrue(api.windows().open(player, main, WindowOpenOptions.playerFixed()).success(), Component.literal("main_menu did not open before closeAll"));
            helper.assertTrue(api.windows().open(player, second, WindowOpenOptions.playerFixed()).success(), Component.literal("main_menu2 did not open before closeAll"));
            api.windows().closeAll(player);
            helper.assertFalse(api.windows().isOpen(player, main), Component.literal("main_menu remained open after closeAll"));
            helper.assertFalse(api.windows().isOpen(player, second), Component.literal("main_menu2 remained open after closeAll"));
            helper.assertTrue(closed.size() == closedBeforeCloseAll + 2, Component.literal("closeAll did not emit one close event per active window"));

            int openedBeforeGroup = opened.size();
            int closedBeforeGroup = closed.size();
            var groupOpen = api.groups().open(player, groupId, GroupOpenOptions.playerFixed());
            helper.assertTrue(groupOpen.success(), Component.literal("menu_group open failed: " + groupOpen.message()));
            helper.assertTrue(api.groups().isOpen(player, groupId), Component.literal("GroupApi.isOpen false after successful open"));
            var groupHandle = api.groups().find(player, groupId).orElseThrow();
            helper.assertTrue(groupId.equals(groupHandle.id()), Component.literal("GroupHandle.id mismatch"));
            helper.assertTrue(player.getUUID().equals(groupHandle.ownerId()), Component.literal("GroupHandle.ownerId mismatch"));
            helper.assertTrue(groupHandle.mode().orElseThrow() == WindowPositionMode.PLAYER_FIXED, Component.literal("GroupHandle.mode mismatch"));
            helper.assertTrue(groupHandle.currentWindowId().isPresent(), Component.literal("GroupHandle.currentWindowId missing"));
            helper.assertTrue(groupHandle.isOpen(), Component.literal("GroupHandle.isOpen false"));
            helper.assertTrue(opened.size() == openedBeforeGroup + 1, Component.literal("group open did not emit exactly one current-window open event"));
            var groupClose = groupHandle.close();
            helper.assertTrue(groupClose.success(), Component.literal("GroupHandle.close failed: " + groupClose.message()));
            helper.assertFalse(groupHandle.isOpen(), Component.literal("GroupHandle remained open after close"));
            helper.assertTrue(closed.size() == closedBeforeGroup + 1, Component.literal("group close did not emit exactly one current-window close event"));

            AtomicReference<com.interactivedisplay.api.callback.CallbackApi.CallbackContext> callbackContext = new AtomicReference<>();
            ResourceLocation callbackId = ResourceLocation.fromNamespaceAndPath("qa", "gametest_callback_" + UUID.randomUUID().toString().replace("-", ""));
            helper.assertTrue(api.callbacks().register(callbackId, callbackContext::set).success(), Component.literal("callback registration failed"));
            InteractiveDisplay.callbackRegistry().find(callbackId.toString()).orElseThrow().execute(player, "main_menu", "button");
            var callback = callbackContext.get();
            helper.assertTrue(callback != null, Component.literal("registered callback was not invoked"));
            helper.assertTrue(callback.player() == player, Component.literal("callback player context mismatch"));
            helper.assertTrue(main.equals(callback.windowId()), Component.literal("callback public window id mismatch: " + callback.windowId()));
            helper.assertTrue("button".equals(callback.componentId()), Component.literal("callback component id mismatch"));
            helper.assertTrue(callback.windows().registeredIds().equals(api.windows().registeredIds()), Component.literal("callback WindowApi registry view mismatch"));

            helper.assertTrue(api.windows().open(player, main, WindowOpenOptions.playerFixed()).success(), Component.literal("main_menu did not open before callback close"));
            ResourceLocation closingCallbackId = ResourceLocation.fromNamespaceAndPath("qa", "gametest_close_callback_" + UUID.randomUUID().toString().replace("-", ""));
            helper.assertTrue(api.callbacks().register(closingCallbackId, context -> {
                var close = context.windows().close(context.player(), context.windowId());
                if (!close.success()) {
                    throw new IllegalStateException(close.message());
                }
            }).success(), Component.literal("closing callback registration failed"));
            InteractiveDisplay.callbackRegistry().find(closingCallbackId.toString()).orElseThrow().execute(player, "main_menu", "button");
            helper.assertFalse(api.windows().isOpen(player, main), Component.literal("callback-triggered WindowApi.close did not close window"));

            AtomicReference<com.interactivedisplay.api.action.ActionApi.ActionContext> actionContext = new AtomicReference<>();
            ResourceLocation actionId = ResourceLocation.fromNamespaceAndPath("qa", "gametest_action_" + UUID.randomUUID().toString().replace("-", ""));
            helper.assertTrue(api.actions().register(actionId, actionContext::set).success(), Component.literal("custom action registration failed"));
            Map<String, String> mutableParameters = new HashMap<>();
            mutableParameters.put("product", "diamond");
            mutableParameters.put("amount", "2");
            var actionResult = PublicActionDispatcher.execute(player, "main_menu", "buy", actionId.toString(), mutableParameters);
            helper.assertTrue(actionResult.success(), Component.literal("registered custom action failed: " + actionResult.message()));
            var action = actionContext.get();
            helper.assertTrue(action != null, Component.literal("custom action handler was not invoked"));
            helper.assertTrue(action.player() == player, Component.literal("custom action player mismatch"));
            helper.assertTrue(main.equals(action.windowId()), Component.literal("custom action window id mismatch"));
            helper.assertTrue("buy".equals(action.componentId()), Component.literal("custom action component id mismatch"));
            helper.assertTrue(actionId.equals(action.actionId()), Component.literal("custom action id mismatch"));
            helper.assertTrue("diamond".equals(action.parameters().get("product")), Component.literal("custom action parameter missing"));
            mutableParameters.put("product", "emerald");
            helper.assertTrue("diamond".equals(action.parameters().get("product")), Component.literal("ActionContext parameters were not defensively copied"));
            boolean immutable = false;
            try {
                action.parameters().put("extra", "value");
            } catch (UnsupportedOperationException expected) {
                immutable = true;
            }
            helper.assertTrue(immutable, Component.literal("ActionContext parameters map is mutable"));

            ResourceLocation throwingActionId = ResourceLocation.fromNamespaceAndPath("qa", "gametest_throw_action_" + UUID.randomUUID().toString().replace("-", ""));
            helper.assertTrue(api.actions().register(throwingActionId, context -> {
                throw new IllegalStateException("expected QA failure");
            }).success(), Component.literal("throwing custom action registration failed"));
            var throwingResult = PublicActionDispatcher.execute(player, "main_menu", "buy", throwingActionId.toString(), Map.of());
            helper.assertFalse(throwingResult.success(), Component.literal("throwing custom action unexpectedly reported success"));
            helper.assertTrue(throwingResult.message().contains("custom action failed"), Component.literal("throwing custom action returned unexpected failure message"));
        } finally {
            api.windows().closeAll(player);
            if (api.groups().isOpen(player, groupId)) {
                api.groups().close(player, groupId);
            }
            openSubscription.close();
            closeSubscription.close();
        }

        helper.succeed();
    }

    @GameTest
    public void commandTreeAndPolymerTextRefreshWorkAfterBootstrap(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(
                server.getCommands().getDispatcher().getRoot().getChild("interactivedisplay") != null,
                Component.literal("interactivedisplay command root was not registered")
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

        helper.assertFalse(factory.refreshText(server, null, runtime, original), Component.literal("unchanged rendered text should not mark an update"));
        TextComponentDefinition changed = text("changed");
        helper.assertTrue(factory.refreshText(server, null, runtime, changed), Component.literal("changed rendered text should update the element"));
        helper.assertTrue("changed".equals(element.getText().getString()), Component.literal("TextDisplayElement did not receive refreshed text"));
        helper.succeed();
    }

    @GameTest
    public void generatedResourcePackContainsInteractiveDisplayAssets(GameTestHelper helper) {
        Path pack = Path.of("polymer", "resource_pack.zip");
        helper.assertTrue(Files.isRegularFile(pack), Component.literal("Polymer resource pack was not generated"));

        try (ZipFile zip = new ZipFile(pack.toFile())) {
            assertZipEntry(helper, zip, "assets/interactivedisplay/items/pointer.json");
            assertZipEntry(helper, zip, "assets/interactivedisplay/models/item/pointer.json");
            assertZipEntry(helper, zip, "assets/interactivedisplay/textures/item/pointer.png");
            assertZipEntry(helper, zip, "assets/interactivedisplay/lang/ko_kr.json");
        } catch (IOException exception) {
            throw new AssertionError("Failed to inspect generated Polymer resource pack", exception);
        }

        helper.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }

    private static void assertWindowModeRoundTrip(
            GameTestHelper helper,
            InteractiveDisplayApi api,
            ServerPlayer player,
            ResourceLocation windowId,
            WindowOpenOptions options,
            WindowPositionMode expectedMode
    ) {
        var open = api.windows().open(player, windowId, options);
        helper.assertTrue(open.success(), Component.literal("WindowApi.open failed for " + expectedMode + ": " + open.message()));
        helper.assertTrue(api.windows().isOpen(player, windowId), Component.literal("WindowApi.isOpen false for " + expectedMode));
        var handle = api.windows().find(player, windowId).orElseThrow();
        helper.assertTrue(windowId.equals(handle.id()), Component.literal("WindowHandle.id mismatch for " + expectedMode));
        helper.assertTrue(player.getUUID().equals(handle.ownerId()), Component.literal("WindowHandle.ownerId mismatch for " + expectedMode));
        helper.assertTrue(handle.mode().orElseThrow() == expectedMode, Component.literal("WindowHandle.mode mismatch for " + expectedMode));
        helper.assertTrue(handle.isOpen(), Component.literal("WindowHandle.isOpen false for " + expectedMode));
        var close = handle.close();
        helper.assertTrue(close.success(), Component.literal("WindowHandle.close failed for " + expectedMode + ": " + close.message()));
        helper.assertFalse(handle.isOpen(), Component.literal("WindowHandle remained open after close for " + expectedMode));
        helper.assertFalse(api.windows().find(player, windowId).isPresent(), Component.literal("WindowApi.find returned closed handle for " + expectedMode));
    }

    private static void assertZipEntry(GameTestHelper helper, ZipFile zip, String name) {
        helper.assertTrue(zip.getEntry(name) != null, Component.literal("Missing resource-pack entry: " + name));
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
                null,
                "left",
                200,
                true,
                "#00000000",
                1
        );
    }
}
