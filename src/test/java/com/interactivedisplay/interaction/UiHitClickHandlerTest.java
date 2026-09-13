package com.interactivedisplay.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.interaction.ClickHandleResult;
import com.interactivedisplay.core.interaction.ClickHandler;
import com.interactivedisplay.core.interaction.UiHitResult;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.window.ActionExecutionResult;
import com.interactivedisplay.core.window.CreateWindowResult;
import com.interactivedisplay.core.window.RemoveWindowResult;
import com.interactivedisplay.core.window.WindowActionExecutor;
import com.interactivedisplay.core.window.WindowComponentRuntime;
import com.interactivedisplay.core.window.WindowNavigationContext;
import com.interactivedisplay.debug.DebugRecorder;
import java.util.UUID;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class UiHitClickHandlerTest {
    @Test
    void closeWindowActionShouldDispatch() {
        TrackingExecutor executor = new TrackingExecutor();
        ClickHandleResult result = new ClickHandler(executor, new DebugRecorder(20))
                .handle(UUID.randomUUID(), "Steve", buttonHit(ComponentAction.closeWindow(), "close"));
        assertTrueConsumed(result);
        assertEquals(1, executor.closeCalls);
    }

    @Test
    void openWindowActionShouldDispatchTarget() {
        TrackingExecutor executor = new TrackingExecutor();
        ClickHandleResult result = new ClickHandler(executor, new DebugRecorder(20))
                .handle(UUID.randomUUID(), "Steve", buttonHit(ComponentAction.openWindow("settings"), "open"));
        assertTrueConsumed(result);
        assertEquals(1, executor.openCalls);
        assertEquals("settings", executor.lastWindowId);
    }

    @Test
    void modeSwitchActionsShouldDispatchTheirRequestedModes() {
        TrackingExecutor executor = new TrackingExecutor();
        ClickHandler handler = new ClickHandler(executor, new DebugRecorder(20));

        assertTrueConsumed(handler.handle(UUID.randomUUID(), "Steve", buttonHit(ComponentAction.switchModeFixed(), "fixed")));
        assertEquals(PositionMode.FIXED, executor.lastPositionMode);
        assertTrueConsumed(handler.handle(UUID.randomUUID(), "Steve", buttonHit(ComponentAction.switchModePlayerFixed(), "player_fixed")));
        assertEquals(PositionMode.PLAYER_FIXED, executor.lastPositionMode);
        assertEquals(2, executor.switchModeCalls);
    }

    @Test
    void runCommandShouldDispatch() {
        TrackingExecutor executor = new TrackingExecutor();
        ClickHandleResult result = new ClickHandler(executor, new DebugRecorder(20))
                .handle(UUID.randomUUID(), "Steve", buttonHit(ComponentAction.runCommand("say hi", 3), "run"));
        assertTrueConsumed(result);
        assertEquals(1, executor.commandCalls);
        assertEquals(3, executor.lastPermissionLevel);
    }

    @Test
    void togglePlacementTrackingShouldDispatch() {
        TrackingExecutor executor = new TrackingExecutor();
        ClickHandleResult result = new ClickHandler(executor, new DebugRecorder(20))
                .handle(UUID.randomUUID(), "Steve", buttonHit(ComponentAction.togglePlacementTracking(), "track"));
        assertTrueConsumed(result);
        assertEquals(1, executor.placementCalls);
    }

    private static void assertTrueConsumed(ClickHandleResult result) {
        assertEquals(true, result.consumed());
        assertNull(result.reasonCode());
    }

    private static UiHitResult buttonHit(ComponentAction action, String componentId) {
        ButtonComponentDefinition button = new ButtonComponentDefinition(
                componentId,
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(1.0f, 0.3f),
                true,
                1.0f,
                "Button",
                1.0f,
                "#AA2222",
                "#44FFFFFF",
                null,
                com.interactivedisplay.core.component.ClickType.RIGHT,
                action
        );
        WindowComponentRuntime runtime = new WindowComponentRuntime(Level.OVERWORLD, button, new Vector3f(), null, null);
        return new UiHitResult(
                "main",
                new WindowNavigationContext("main", null, PositionMode.FIXED, Vec3.ZERO, 0.0f, 0.0f),
                componentId,
                runtime,
                action,
                Vec3.ZERO,
                1.0D
        );
    }

    private static final class TrackingExecutor implements WindowActionExecutor {
        int closeCalls;
        int openCalls;
        int switchModeCalls;
        int commandCalls;
        int placementCalls;
        Integer lastPermissionLevel;
        String lastWindowId;
        PositionMode lastPositionMode;

        @Override
        public RemoveWindowResult closeWindow(UUID owner, WindowNavigationContext context) {
            closeCalls++;
            return RemoveWindowResult.success(owner, context.windowId(), 1, "closed");
        }

        @Override
        public CreateWindowResult openWindow(UUID owner, WindowNavigationContext context, String windowId) {
            openCalls++;
            lastWindowId = windowId;
            return CreateWindowResult.success(owner, "Steve", windowId, null, 0, 0, "opened");
        }

        @Override
        public CreateWindowResult switchMode(UUID owner, WindowNavigationContext context, PositionMode positionMode) {
            switchModeCalls++;
            lastPositionMode = positionMode;
            return CreateWindowResult.success(owner, "Steve", context.windowId(), null, 0, 0, "switched");
        }

        @Override
        public ActionExecutionResult runCommand(UUID owner, UiHitResult hitResult, Integer permissionLevel, String command) {
            commandCalls++;
            lastPermissionLevel = permissionLevel;
            return ActionExecutionResult.success("command");
        }

        @Override
        public ActionExecutionResult executeCallback(UUID owner, String windowId, String componentId, String callbackId) {
            return ActionExecutionResult.success("callback");
        }

        @Override
        public ActionExecutionResult togglePlacementTracking(UUID owner, WindowNavigationContext context) {
            placementCalls++;
            return ActionExecutionResult.success("tracking");
        }
    }
}
