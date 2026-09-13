package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.api.InteractiveDisplayApi;
import com.interactivedisplay.api.event.EventApi;
import com.interactivedisplay.api.group.GroupOpenOptions;
import com.interactivedisplay.api.window.WindowOpenOptions;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ClickType;
import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.interaction.CallbackRegistry;
import com.interactivedisplay.core.interaction.ClickHandler;
import com.interactivedisplay.core.interaction.UiHitResult;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.window.WindowComponentRuntime;
import com.interactivedisplay.core.window.WindowNavigationContext;
import com.interactivedisplay.debug.DebugRecorder;
import com.interactivedisplay.entity.VirtualWindowHolder;
import com.interactivedisplay.internal.api.InteractiveDisplayApiImpl;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class InteractiveDisplayGroupNavigationGameTest implements CustomTestMethodInvoker {
    @SuppressWarnings("removal")
    @GameTest
    public void handleCloseAndGroupNavigationShouldEmitExactLifecycleAndPreserveTopology(GameTestHelper helper) {
        InteractiveDisplayApi api = InteractiveDisplayApi.get();
        var manager = InteractiveDisplay.instance().windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ResourceLocation main = publicId("main_menu");

        List<EventApi.WindowEvent> opened = new ArrayList<>();
        List<EventApi.WindowEvent> closed = new ArrayList<>();
        EventApi.Subscription openedSubscription = api.events().onWindowOpened(event -> {
            if (event.ownerId().equals(player.getUUID())) {
                opened.add(event);
            }
        });
        EventApi.Subscription closedSubscription = api.events().onWindowClosed(event -> {
            if (event.ownerId().equals(player.getUUID())) {
                closed.add(event);
            }
        });

        try {
            // WindowHandle.close must use the same public lifecycle path as WindowApi.close.
            helper.assertTrue(api.windows().open(player, main, WindowOpenOptions.playerFixed()).success(),
                    Component.literal("main_menu did not open before WindowHandle.close lifecycle test"));
            var handle = api.windows().find(player, main).orElseThrow();
            int closedBeforeHandle = closed.size();
            var handleClose = handle.close();
            helper.assertTrue(handleClose.success(), Component.literal("WindowHandle.close failed: " + handleClose.message()));
            helper.assertTrue(closed.size() == closedBeforeHandle + 1,
                    Component.literal("WindowHandle.close did not emit exactly one CLOSED event"));
            helper.assertTrue(main.equals(closed.get(closed.size() - 1).windowId()),
                    Component.literal("WindowHandle.close emitted CLOSED for wrong window"));

            // Seed a live group directly through WindowManager so only navigation events are measured below.
            var groupOpen = manager.createGroup(player, "menu_group", PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
            helper.assertTrue(groupOpen.success(), Component.literal("menu_group direct open failed: " + groupOpen.message()));
            var group = manager.findActiveGroup(player.getUUID(), "menu_group");
            helper.assertTrue(group != null && "main_menu".equals(group.currentWindowId()),
                    Component.literal("menu_group did not start at main_menu"));

            ClickHandler clickHandler = new ClickHandler(manager, new DebugRecorder(40));
            int openedBeforeInternalNav = opened.size();
            int closedBeforeInternalNav = closed.size();
            var internalNav = clickHandler.handle(
                    player.getUUID(),
                    player.getGameProfile().getName(),
                    hit("main_menu", "menu_group", group.baseAnchor(), group.baseYaw(), group.basePitch(), "gallery")
            );
            helper.assertTrue(internalNav.consumed(), Component.literal("group internal open_window navigation was not consumed"));

            var navigatedGroup = manager.findActiveGroup(player.getUUID(), "menu_group");
            helper.assertTrue(navigatedGroup != null && "gallery".equals(navigatedGroup.currentWindowId()),
                    Component.literal("group internal navigation did not switch current window to gallery"));
            helper.assertTrue(manager.findActiveWindow(player.getUUID(), "gallery") == null,
                    Component.literal("group internal navigation incorrectly created gallery as standalone"));
            assertNavigationEvents(helper, opened, closed, openedBeforeInternalNav, closedBeforeInternalNav, "main_menu", "gallery");

            // main_menu2 is not a menu_group entry. open_window must leave the group and create a standalone window.
            int openedBeforeStandalone = opened.size();
            int closedBeforeStandalone = closed.size();
            var standaloneNav = clickHandler.handle(
                    player.getUUID(),
                    player.getGameProfile().getName(),
                    hit("gallery", "menu_group", navigatedGroup.baseAnchor(), navigatedGroup.baseYaw(), navigatedGroup.basePitch(), "main_menu2")
            );
            helper.assertTrue(standaloneNav.consumed(), Component.literal("group-to-standalone open_window navigation was not consumed"));
            helper.assertTrue(manager.findActiveGroup(player.getUUID(), "menu_group") == null,
                    Component.literal("group remained active after navigation to non-group window"));
            helper.assertTrue(manager.findActiveWindow(player.getUUID(), "main_menu2") != null,
                    Component.literal("group-to-standalone navigation did not create main_menu2 standalone"));
            assertNavigationEvents(helper, opened, closed, openedBeforeStandalone, closedBeforeStandalone, "gallery", "main_menu2");
        } finally {
            manager.removeAll(player.getUUID());
            VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
            openedSubscription.close();
            closedSubscription.close();
        }

        helper.assertTrue(manager.ownerWindows(player.getUUID()).isEmpty(), Component.literal("navigation GameTest leaked active windows"));
        helper.assertTrue(manager.bindingSnapshots(player.getUUID()).isEmpty(), Component.literal("navigation GameTest leaked bindings"));
        helper.succeed();
    }

    @SuppressWarnings("removal")
    @GameTest
    public void publicOperationsShouldReportRuntimeNotReadyBeforeAttachAndAfterDetach(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var manager = InteractiveDisplay.instance().windowManager();
        InteractiveDisplayApiImpl detachedApi = new InteractiveDisplayApiImpl(new CallbackRegistry());
        ResourceLocation windowId = publicId("main_menu");
        ResourceLocation groupId = publicId("menu_group");

        var preAttachWindow = detachedApi.windows().open(player, windowId, WindowOpenOptions.playerFixed());
        helper.assertFalse(preAttachWindow.success(), Component.literal("pre-attach window open unexpectedly succeeded"));
        helper.assertTrue("runtime_not_ready".equals(preAttachWindow.reason()),
                Component.literal("pre-attach window open returned wrong reason: " + preAttachWindow.reason()));

        var preAttachGroup = detachedApi.groups().open(player, groupId, GroupOpenOptions.playerFixed());
        helper.assertFalse(preAttachGroup.success(), Component.literal("pre-attach group open unexpectedly succeeded"));
        helper.assertTrue("runtime_not_ready".equals(preAttachGroup.reason()),
                Component.literal("pre-attach group open returned wrong reason: " + preAttachGroup.reason()));

        detachedApi.attach(manager);
        helper.assertTrue(detachedApi.attached(), Component.literal("test API did not report attached state"));
        detachedApi.detach();
        helper.assertFalse(detachedApi.attached(), Component.literal("test API remained attached after detach"));

        var postDetachWindow = detachedApi.windows().open(player, windowId, WindowOpenOptions.playerFixed());
        helper.assertFalse(postDetachWindow.success(), Component.literal("post-detach window open unexpectedly succeeded"));
        helper.assertTrue("runtime_not_ready".equals(postDetachWindow.reason()),
                Component.literal("post-detach window open returned wrong reason: " + postDetachWindow.reason()));

        helper.assertTrue(manager.ownerWindows(player.getUUID()).isEmpty(),
                Component.literal("runtime readiness GameTest created unexpected manager state"));
        helper.succeed();
    }

    private static UiHitResult hit(
            String sourceWindowId,
            String groupId,
            Vec3 baseAnchor,
            float baseYaw,
            float basePitch,
            String targetWindowId
    ) {
        ComponentAction action = ComponentAction.openWindow(targetWindowId);
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
                sourceWindowId,
                groupId,
                PositionMode.PLAYER_FIXED,
                baseAnchor,
                baseYaw,
                basePitch
        );
        return new UiHitResult(sourceWindowId, context, "navigate", runtime, action, Vec3.ZERO, 1.0D);
    }

    private static void assertNavigationEvents(
            GameTestHelper helper,
            List<EventApi.WindowEvent> opened,
            List<EventApi.WindowEvent> closed,
            int openedBefore,
            int closedBefore,
            String source,
            String target
    ) {
        ResourceLocation expectedSource = publicId(source);
        ResourceLocation expectedTarget = publicId(target);
        helper.assertTrue(closed.size() == closedBefore + 1,
                Component.literal("navigation did not emit exactly one CLOSED event: " + source + " -> " + target));
        helper.assertTrue(opened.size() == openedBefore + 1,
                Component.literal("navigation did not emit exactly one OPENED event: " + source + " -> " + target));
        helper.assertTrue(expectedSource.equals(closed.get(closed.size() - 1).windowId()),
                Component.literal("navigation CLOSED wrong source: expected=" + expectedSource + " actual=" + closed.get(closed.size() - 1).windowId()));
        helper.assertTrue(expectedTarget.equals(opened.get(opened.size() - 1).windowId()),
                Component.literal("navigation OPENED wrong target: expected=" + expectedTarget + " actual=" + opened.get(opened.size() - 1).windowId()));
    }

    private static ResourceLocation publicId(String path) {
        return ResourceLocation.fromNamespaceAndPath(InteractiveDisplay.MOD_ID, path);
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
