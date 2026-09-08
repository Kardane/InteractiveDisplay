package com.interactivedisplay.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.debug.DebugRecorder;
import java.util.UUID;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class UiHitClickHandlerTest {
    @Test
    void nullHitShouldReturnReason() {
        ClickHandler handler = new ClickHandler(new TrackingExecutor(), new DebugRecorder(20));
        ClickHandleResult result = handler.handle(UUID.randomUUID(), "Alex", null);
        assertEquals(false, result.consumed());
        assertEquals(DebugReason.INTERACTION_NOT_FOUND, result.reasonCode());
    }

    @Test
    void closeWindowActionShouldDispatch() {
        UUID owner = UUID.randomUUID();
        TrackingExecutor executor = new TrackingExecutor();
        ClickHandler handler = new ClickHandler(executor, new DebugRecorder(20));
        ClickHandleResult result = handler.handle(owner, "Steve", closeHit());
        assertEquals(true, result.consumed());
        assertNull(result.reasonCode());
        assertEquals(1, executor.closeCalls);
    }

    @Test
    void openWindowWithoutTargetShouldReturnReason() {
        ClickHandleResult result = new ClickHandler(new TrackingExecutor(), new DebugRecorder(20)).handle(UUID.randomUUID(), "Steve", buttonHit(new com.interactivedisplay.core.component.ComponentAction(com.interactivedisplay.core.component.ComponentActionType.OPEN_WINDOW, null, null), "btn_open_invalid"));
        assertEquals(false, result.consumed());
        assertEquals(DebugReason.ACTION_TARGET_NOT_FOUND, result.reasonCode());
    }

    @Test
    void openWindowActionShouldDispatchTarget() {
        TrackingExecutor executor = new TrackingExecutor();
        ClickHandleResult result = new ClickHandler(executor, new DebugRecorder(20)).handle(UUID.randomUUID(), "Steve", buttonHit(ComponentAction.openWindow("settings"), "open"));
        assertEquals(true, result.consumed());
        assertEquals(1, executor.openCalls);
        assertEquals("settings", executor.lastWindowId);
    }

    @Test
    void modeSwitchActionsShouldDispatchTheirRequestedModes() {
        TrackingExecutor executor = new TrackingExecutor();
        ClickHandler handler = new ClickHandler(executor, new DebugRecorder(20));
        assertEquals(true, handler.handle(UUID.randomUUID(), "Steve", buttonHit(ComponentAction.switchModeFixed(), "fixed")).consumed());
        assertEquals(PositionMode.FIXED, executor.lastPositionMode);
        assertEquals(true, handler.handle(UUID.randomUUID(), "Steve", buttonHit(ComponentAction.switchModePlayerFixed(), "player_fixed")).consumed());
        assertEquals(PositionMode.PLAYER_FIXED, executor.lastPositionMode);
        assertEquals(2, executor.switchModeCalls);
    }

    @Test
    void closeWindowFailureShouldPassUnderlyingReason() {
        UUID owner = UUID.randomUUID();
        TrackingExecutor executor = new TrackingExecutor();
        executor.closeResult = RemoveWindowResult.failure(DebugReason.ACTION_EXECUTION_FAILED, owner, "main", "닫기 실패");
        ClickHandleResult result = new ClickHandler(executor, new DebugRecorder(20)).handle(owner, "Steve", closeHit());
        assertFalse(result.consumed());
        assertEquals(DebugReason.ACTION_EXECUTION_FAILED, result.reasonCode());
        assertEquals(1, executor.closeCalls);
    }

    @Test
    void runCommandShouldDispatch() {
        TrackingExecutor executor = new TrackingExecutor();
        ClickHandleResult result = new ClickHandler(executor, new DebugRecorder(20)).handle(UUID.randomUUID(), "Steve", buttonHit(ComponentAction.runCommand("say hi", 3), "run"));
        assertEquals(true, result.consumed());
        assertEquals(1, executor.commandCalls);
        assertEquals(3, executor.lastPermissionLevel);
        assertEquals(new Vec3(0.0, 0.0, 0.0), executor.lastHit.hitPosition());
    }

    @Test
    void runCommandWithoutCommandShouldNotDispatch() {
        TrackingExecutor executor = new TrackingExecutor();
        ClickHandleResult result = new ClickHandler(executor, new DebugRecorder(20)).handle(UUID.randomUUID(), "Steve", buttonHit(ComponentAction.runCommand(null, 2), "run_invalid"));
        assertFalse(result.consumed());
        assertEquals(DebugReason.ACTION_TARGET_NOT_FOUND, result.reasonCode());
        assertEquals(0, executor.commandCalls);
    }

    @Test
    void callbackFailureShouldSurfaceUnderlyingReason() {
        TrackingExecutor executor = new TrackingExecutor();
        executor.callbackResult = ActionExecutionResult.failure(DebugReason.ACTION_EXECUTION_FAILED, "등록되지 않은 callback id");
        ClickHandleResult result = new ClickHandler(executor, new DebugRecorder(20)).handle(UUID.randomUUID(), "Steve", buttonHit(ComponentAction.callback("missing"), "cb"));
        assertEquals(false, result.consumed());
        assertEquals(DebugReason.ACTION_EXECUTION_FAILED, result.reasonCode());
    }

    @Test
    void callbackWithoutIdShouldNotDispatch() {
        TrackingExecutor executor = new TrackingExecutor();
        ClickHandleResult result = new ClickHandler(executor, new DebugRecorder(20)).handle(UUID.randomUUID(), "Steve", buttonHit(ComponentAction.callback(null), "callback_invalid"));
        assertFalse(result.consumed());
        assertEquals(DebugReason.ACTION_TARGET_NOT_FOUND, result.reasonCode());
        assertEquals(0, executor.callbackCalls);
    }

    @Test
    void togglePlacementTrackingShouldDispatch() {
        TrackingExecutor executor = new TrackingExecutor();
        ClickHandleResult result = new ClickHandler(executor, new DebugRecorder(20)).handle(UUID.randomUUID(), "Steve", buttonHit(ComponentAction.togglePlacementTracking(), "track"));
        assertEquals(true, result.consumed());
        assertEquals(1, executor.placementCalls);
    }

    private static UiHitResult closeHit() {
        return buttonHit(ComponentAction.closeWindow(), "btn_close");
    }

    private static UiHitResult buttonHit(ComponentAction action, String componentId) {
        ButtonComponentDefinition button = new ButtonComponentDefinition(componentId, new ComponentPosition(0.0f, 0.0f, 0.0f), new ComponentSize(1.0f, 0.3f), true, 1.0f, "버튼", 1.0f, "#AA2222", "#44FFFFFF", null, com.interactivedisplay.core.component.ClickType.RIGHT, action);
        WindowComponentRuntime runtime = new WindowComponentRuntime(Level.OVERWORLD, button, new Vector3f(), null, null);
        return new UiHitResult("main", new WindowNavigationContext("main", null, PositionMode.FIXED, Vec3.ZERO, 0.0f, 0.0f), componentId, runtime, action, Vec3.ZERO, 1.0D);
    }

    private static final class TrackingExecutor implements WindowActionExecutor {
        int closeCalls;
        int openCalls;
        int switchModeCalls;
        int commandCalls;
        int callbackCalls;
        int placementCalls;
        Integer lastPermissionLevel;
        String lastWindowId;
        PositionMode lastPositionMode;
        UiHitResult lastHit;
        RemoveWindowResult closeResult = RemoveWindowResult.success(UUID.randomUUID(), "main", 1, "닫기");
        CreateWindowResult openResult = CreateWindowResult.success(UUID.randomUUID(), "Steve", "settings", null, 0, 0, "열기");
        ActionExecutionResult commandResult = ActionExecutionResult.success("run_command 처리 완료");
        ActionExecutionResult callbackResult = ActionExecutionResult.success("callback 처리 완료");
        ActionExecutionResult placementResult = ActionExecutionResult.success("toggle_placement_tracking 처리 완료");

        @Override
        public RemoveWindowResult closeWindow(UUID owner, WindowNavigationContext context) {
            this.closeCalls++;
            return this.closeResult;
        }

        @Override
        public CreateWindowResult openWindow(UUID owner, WindowNavigationContext context, String windowId) {
            this.openCalls++;
            this.lastWindowId = windowId;
            return this.openResult;
        }

        @Override
        public CreateWindowResult switchMode(UUID owner, WindowNavigationContext context, PositionMode positionMode) {
            this.switchModeCalls++;
            this.lastPositionMode = positionMode;
            return this.openResult;
        }

        @Override
        public ActionExecutionResult runCommand(UUID owner, UiHitResult hitResult, Integer permissionLevel, String command) {
            this.commandCalls++;
            this.lastPermissionLevel = permissionLevel;
            this.lastHit = hitResult;
            return this.commandResult;
        }

        @Override
        public ActionExecutionResult executeCallback(UUID owner, String windowId, String componentId, String callbackId) {
            this.callbackCalls++;
            return this.callbackResult;
        }

        @Override
        public ActionExecutionResult togglePlacementTracking(UUID owner, WindowNavigationContext context) {
            this.placementCalls++;
            return this.placementResult;
        }
    }
}
