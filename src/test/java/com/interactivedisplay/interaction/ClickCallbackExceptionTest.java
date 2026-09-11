package com.interactivedisplay.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
import java.util.UUID;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ClickCallbackExceptionTest {
    @Test
    void callbackExceptionShouldBeContainedByClickHandler() {
        ClickHandler handler = new ClickHandler(new ThrowingCallbackExecutor(), new DebugRecorder(20));

        var result = handler.handle(UUID.randomUUID(), "Steve", callbackHit());

        assertFalse(result.consumed());
        assertEquals(DebugReason.ACTION_EXECUTION_FAILED, result.reasonCode());
        assertEquals("qa:throwing_callback", result.targetWindowId());
    }

    private static UiHitResult callbackHit() {
        ComponentAction action = ComponentAction.callback("qa:throwing_callback");
        ButtonComponentDefinition button = new ButtonComponentDefinition(
                "callback",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(1.0f, 0.3f),
                true,
                1.0f,
                "Callback",
                1.0f,
                "#AA222222",
                "#CC444444",
                null,
                ClickType.RIGHT,
                action
        );
        WindowComponentRuntime runtime = new WindowComponentRuntime(Level.OVERWORLD, button, new Vector3f(), null, null);
        return new UiHitResult(
                "main",
                new WindowNavigationContext("main", null, PositionMode.FIXED, Vec3.ZERO, 0.0f, 0.0f),
                "callback",
                runtime,
                action,
                Vec3.ZERO,
                1.0D
        );
    }

    private static final class ThrowingCallbackExecutor implements WindowActionExecutor {
        @Override
        public RemoveWindowResult closeWindow(UUID owner, WindowNavigationContext context) {
            return RemoveWindowResult.success(owner, context.windowId(), 1, "closed");
        }

        @Override
        public CreateWindowResult openWindow(UUID owner, WindowNavigationContext context, String windowId) {
            return CreateWindowResult.success(owner, "Steve", windowId, null, 0, 0, "opened");
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
            throw new IllegalStateException("expected QA callback failure");
        }

        @Override
        public ActionExecutionResult togglePlacementTracking(UUID owner, WindowNavigationContext context) {
            return ActionExecutionResult.success("tracking");
        }
    }
}
