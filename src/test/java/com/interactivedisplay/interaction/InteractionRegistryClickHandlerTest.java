package com.interactivedisplay.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.interaction.ClickHandleResult;
import com.interactivedisplay.core.interaction.ClickHandler;
import com.interactivedisplay.core.interaction.InteractionBinding;
import com.interactivedisplay.core.interaction.InteractionRegistry;
import com.interactivedisplay.core.window.ActionExecutionResult;
import com.interactivedisplay.core.window.CreateWindowResult;
import com.interactivedisplay.core.window.RemoveWindowResult;
import com.interactivedisplay.core.window.WindowActionExecutor;
import com.interactivedisplay.debug.DebugReason;
import com.interactivedisplay.debug.DebugRecorder;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InteractionRegistryClickHandlerTest {
    @Test
    void interactionNotFoundShouldReturnReason() {
        ClickHandler handler = new ClickHandler(new InteractionRegistry(), new TrackingExecutor(), new DebugRecorder(20));

        ClickHandleResult result = handler.handle(UUID.randomUUID(), "Alex", UUID.randomUUID(), false);

        assertEquals(false, result.consumed());
        assertEquals(DebugReason.INTERACTION_NOT_FOUND, result.reasonCode());
    }

    @Test
    void ownerMismatchShouldBeIgnored() {
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        UUID interaction = UUID.randomUUID();

        InteractionRegistry registry = new InteractionRegistry();
        registry.register(interaction, new InteractionBinding(owner, "main", "btn", ComponentAction.closeWindow()));

        TrackingExecutor executor = new TrackingExecutor();
        ClickHandler handler = new ClickHandler(registry, executor, new DebugRecorder(20));

        ClickHandleResult result = handler.handle(attacker, "Alex", interaction, false);
        assertEquals(false, result.consumed());
        assertEquals(DebugReason.OWNER_MISMATCH, result.reasonCode());
        assertEquals(0, executor.closeCalls);
    }

    @Test
    void leftClickShouldAlwaysBeIgnored() {
        UUID owner = UUID.randomUUID();
        UUID interaction = UUID.randomUUID();

        InteractionRegistry registry = new InteractionRegistry();
        registry.register(interaction, new InteractionBinding(owner, "main", "btn_close", ComponentAction.closeWindow()));

        TrackingExecutor executor = new TrackingExecutor();
        ClickHandler handler = new ClickHandler(registry, executor, new DebugRecorder(20));

        ClickHandleResult result = handler.handle(owner, "Steve", interaction, true);
        assertEquals(false, result.consumed());
        assertEquals(DebugReason.CLICK_TYPE_MISMATCH, result.reasonCode());
        assertEquals(0, executor.closeCalls);
    }

    @Test
    void closeWindowActionShouldDispatchOnRightClick() {
        UUID owner = UUID.randomUUID();
        UUID interaction = UUID.randomUUID();

        InteractionRegistry registry = new InteractionRegistry();
        registry.register(interaction, new InteractionBinding(owner, "main", "btn_close", ComponentAction.closeWindow()));

        TrackingExecutor executor = new TrackingExecutor();
        ClickHandler handler = new ClickHandler(registry, executor, new DebugRecorder(20));

        ClickHandleResult result = handler.handle(owner, "Steve", interaction, false);
        assertEquals(true, result.consumed());
        assertNull(result.reasonCode());
        assertEquals(1, executor.closeCalls);
    }

    @Test
    void openWindowWithoutTargetShouldReturnReason() {
        UUID owner = UUID.randomUUID();
        UUID interaction = UUID.randomUUID();
        InteractionRegistry registry = new InteractionRegistry();
        registry.register(interaction, new InteractionBinding(owner, "main", "btn_open_invalid", new com.interactivedisplay.core.component.ComponentAction(com.interactivedisplay.core.component.ComponentActionType.OPEN_WINDOW, null)));

        ClickHandleResult result = new ClickHandler(registry, new TrackingExecutor(), new DebugRecorder(20)).handle(owner, "Steve", interaction, false);
        assertEquals(false, result.consumed());
        assertEquals(DebugReason.ACTION_TARGET_NOT_FOUND, result.reasonCode());
    }

    @Test
    void runCommandShouldDispatch() {
        UUID owner = UUID.randomUUID();
        UUID interaction = UUID.randomUUID();
        InteractionRegistry registry = new InteractionRegistry();
        registry.register(interaction, new InteractionBinding(owner, "main", "run", ComponentAction.runCommand("say hi")));

        TrackingExecutor executor = new TrackingExecutor();
        ClickHandleResult result = new ClickHandler(registry, executor, new DebugRecorder(20)).handle(owner, "Steve", interaction, false);

        assertEquals(true, result.consumed());
        assertEquals(1, executor.commandCalls);
    }

    @Test
    void callbackFailureShouldSurfaceUnderlyingReason() {
        UUID owner = UUID.randomUUID();
        UUID interaction = UUID.randomUUID();
        InteractionRegistry registry = new InteractionRegistry();
        registry.register(interaction, new InteractionBinding(owner, "main", "cb", ComponentAction.callback("missing")));

        TrackingExecutor executor = new TrackingExecutor();
        executor.callbackResult = ActionExecutionResult.failure(DebugReason.ACTION_EXECUTION_FAILED, "등록되지 않은 callback id");
        ClickHandleResult result = new ClickHandler(registry, executor, new DebugRecorder(20)).handle(owner, "Steve", interaction, false);

        assertEquals(false, result.consumed());
        assertEquals(DebugReason.ACTION_EXECUTION_FAILED, result.reasonCode());
    }

    private static final class TrackingExecutor implements WindowActionExecutor {
        int closeCalls;
        int openCalls;
        int commandCalls;
        int callbackCalls;
        String lastOpenedWindow;
        RemoveWindowResult closeResult = RemoveWindowResult.success(UUID.randomUUID(), "main", 1, "닫기");
        CreateWindowResult openResult = CreateWindowResult.success(UUID.randomUUID(), "Steve", "settings", null, 0, 0, "열기");
        ActionExecutionResult commandResult = ActionExecutionResult.success("run_command 처리 완료");
        ActionExecutionResult callbackResult = ActionExecutionResult.success("callback 처리 완료");

        @Override
        public RemoveWindowResult closeWindow(UUID owner, String windowId) {
            this.closeCalls++;
            return this.closeResult;
        }

        @Override
        public CreateWindowResult openWindow(UUID owner, String windowId) {
            this.openCalls++;
            this.lastOpenedWindow = windowId;
            return this.openResult;
        }

        @Override
        public ActionExecutionResult runCommand(UUID owner, String windowId, String componentId, String command) {
            this.commandCalls++;
            return this.commandResult;
        }

        @Override
        public ActionExecutionResult executeCallback(UUID owner, String windowId, String componentId, String callbackId) {
            this.callbackCalls++;
            return this.callbackResult;
        }
    }
}
