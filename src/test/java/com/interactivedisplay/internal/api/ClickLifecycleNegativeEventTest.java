package com.interactivedisplay.internal.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.interactivedisplay.api.event.EventApi;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ClickType;
import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.interaction.ClickHandler;
import com.interactivedisplay.core.interaction.UiHitResult;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.window.ActionExecutionResult;
import com.interactivedisplay.core.window.CreateWindowResult;
import com.interactivedisplay.core.window.RemoveWindowResult;
import com.interactivedisplay.core.window.WindowActionExecutor;
import com.interactivedisplay.core.window.WindowComponentRuntime;
import com.interactivedisplay.core.window.WindowNavigationContext;
import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.debug.DebugRecorder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ClickLifecycleNegativeEventTest {
    @Test
    void failedOpenShouldEmitButtonClickButNoWindowLifecycleEvent() {
        List<String> events = new ArrayList<>();
        EventApi api = PublicEventDispatcher.api();
        EventApi.Subscription clicked = api.onButtonClicked(event -> events.add("clicked:" + event.componentId()));
        EventApi.Subscription closed = api.onWindowClosed(event -> events.add("closed:" + event.windowId()));
        EventApi.Subscription opened = api.onWindowOpened(event -> events.add("opened:" + event.windowId()));
        try {
            ClickHandler handler = new ClickHandler(new Executor(false), new DebugRecorder(20));
            handler.handle(UUID.randomUUID(), "Steve", hit("main", ComponentAction.openWindow("missing")));
            assertEquals(List.of("clicked:button"), events);
        } finally {
            clicked.close();
            closed.close();
            opened.close();
        }
    }

    @Test
    void nullHitShouldEmitNoPublicEvent() {
        List<String> events = new ArrayList<>();
        EventApi api = PublicEventDispatcher.api();
        EventApi.Subscription clicked = api.onButtonClicked(event -> events.add("clicked"));
        EventApi.Subscription closed = api.onWindowClosed(event -> events.add("closed"));
        EventApi.Subscription opened = api.onWindowOpened(event -> events.add("opened"));
        try {
            ClickHandler handler = new ClickHandler(new Executor(true), new DebugRecorder(20));
            handler.handle(UUID.randomUUID(), "Steve", null);
            assertEquals(List.of(), events);
        } finally {
            clicked.close();
            closed.close();
            opened.close();
        }
    }

    @Test
    void closeWindowShouldEmitExactlyOneClosedEvent() {
        List<String> events = new ArrayList<>();
        EventApi api = PublicEventDispatcher.api();
        EventApi.Subscription closed = api.onWindowClosed(event -> events.add("closed:" + event.windowId()));
        EventApi.Subscription opened = api.onWindowOpened(event -> events.add("opened:" + event.windowId()));
        try {
            ClickHandler handler = new ClickHandler(new Executor(true), new DebugRecorder(20));
            handler.handle(UUID.randomUUID(), "Steve", hit("main", ComponentAction.closeWindow()));
            assertEquals(List.of("closed:interactivedisplay:main"), events);
        } finally {
            closed.close();
            opened.close();
        }
    }

    private static UiHitResult hit(String sourceWindowId, ComponentAction action) {
        ButtonComponentDefinition button = new ButtonComponentDefinition(
                "button",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(1.0f, 0.3f),
                true,
                1.0f,
                "Button",
                1.0f,
                "#AA222222",
                "#CC444444",
                null,
                ClickType.RIGHT,
                action
        );
        WindowComponentRuntime runtime = new WindowComponentRuntime(Level.OVERWORLD, button, new Vector3f(), null, null);
        return new UiHitResult(
                sourceWindowId,
                new WindowNavigationContext(sourceWindowId, null, PositionMode.FIXED, Vec3.ZERO, 0.0f, 0.0f),
                "button",
                runtime,
                action,
                Vec3.ZERO,
                1.0D
        );
    }

    private static final class Executor implements WindowActionExecutor {
        private final boolean openSuccess;

        private Executor(boolean openSuccess) {
            this.openSuccess = openSuccess;
        }

        @Override
        public RemoveWindowResult closeWindow(UUID owner, WindowNavigationContext context) {
            return RemoveWindowResult.success(owner, context.windowId(), 1, "closed");
        }

        @Override
        public CreateWindowResult openWindow(UUID owner, WindowNavigationContext context, String windowId) {
            return openSuccess
                    ? CreateWindowResult.success(owner, "Steve", windowId, null, 0, 0, "opened")
                    : CreateWindowResult.failure(DebugReason.WINDOW_NOT_FOUND, owner, "Steve", windowId, "missing");
        }

        @Override
        public CreateWindowResult switchMode(UUID owner, WindowNavigationContext context, PositionMode positionMode) {
            return CreateWindowResult.success(owner, "Steve", context.windowId(), null, 0, 0, "switched");
        }

        @Override
        public ActionExecutionResult runCommand(UUID owner, UiHitResult hitResult, Integer permissionLevel, String command) {
            return ActionExecutionResult.success("command");
        }

        @Override
        public ActionExecutionResult executeCallback(UUID owner, String windowId, String componentId, String callbackId) {
            return ActionExecutionResult.success("callback");
        }

        @Override
        public ActionExecutionResult togglePlacementTracking(UUID owner, WindowNavigationContext context) {
            return ActionExecutionResult.success("tracking");
        }
    }
}
