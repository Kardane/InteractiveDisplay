package com.interactivedisplay.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.core.component.ComponentAction;
import com.interactivedisplay.core.component.ComponentPosition;
import com.interactivedisplay.core.component.ComponentSize;
import com.interactivedisplay.core.component.TextComponentDefinition;
import com.interactivedisplay.core.interaction.InteractionBinding;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.core.window.WindowComponentRuntime;
import com.interactivedisplay.core.window.WindowInstance;
import com.interactivedisplay.debug.DebugEvent;
import com.interactivedisplay.debug.DebugEventType;
import com.interactivedisplay.debug.DebugLevel;
import com.interactivedisplay.debug.DebugReason;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class InteractiveDisplayCommandFormatTest {
    @Test
    void statusLinesShouldContainCountsAndLatestFailure() {
        DebugEvent latest = new DebugEvent(
                Instant.parse("2026-03-06T00:00:00Z"),
                DebugEventType.WINDOW_CREATE,
                DebugLevel.WARN,
                UUID.randomUUID(),
                "Steve",
                "main_menu",
                null,
                null,
                DebugReason.WINDOW_DEFINITION_NOT_FOUND,
                "창 정의를 찾을 수 없음",
                null
        );

        List<String> lines = InteractiveDisplayCommand.buildStatusLines(3, 2, 4, 5, 6, 1, latest);

        assertTrue(lines.get(0).contains("loaded=3"));
        assertTrue(lines.get(0).contains("pooled=5"));
        assertTrue(lines.get(0).contains("mapCache=6"));
        assertTrue(lines.get(1).contains("WINDOW_DEFINITION_NOT_FOUND"));
    }

    @Test
    void windowLinesShouldContainActiveInstanceState() {
        TextComponentDefinition text = new TextComponentDefinition(
                "title",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(1.0f, 0.2f),
                true,
                1.0f,
                "hello",
                1.0f,
                "#FFFFFF",
                "left",
                100,
                true,
                "#00000000"
        );
        TextComponentDefinition text2 = new TextComponentDefinition(
                "subtitle",
                new ComponentPosition(0.0f, 0.0f, 0.0f),
                new ComponentSize(1.0f, 0.2f),
                true,
                1.0f,
                "hello",
                1.0f,
                "#FFFFFF",
                "left",
                100,
                true,
                "#00000000"
        );
        WindowInstance instance = new WindowInstance(UUID.randomUUID(), "main_menu", World.OVERWORLD, PositionMode.FIXED, new Vec3d(1, 2, 3), new Vec3d(1, 2, 3), 0.0f, 0.0f, 0L);
        instance.addRuntime(new WindowComponentRuntime(World.OVERWORLD, "sig-a", text, new Vector3f(), UUID.randomUUID(), null, null));
        instance.addRuntime(new WindowComponentRuntime(World.OVERWORLD, "sig-b", text2, new Vector3f(), UUID.randomUUID(), UUID.randomUUID(), null));

        List<String> lines = InteractiveDisplayCommand.buildWindowLines("main_menu", true, instance, null);

        assertTrue(lines.get(0).contains("entityCount=3"));
        assertTrue(lines.get(0).contains("bindings=1"));
        assertTrue(lines.get(0).contains(PositionMode.FIXED.name()));
    }

    @Test
    void bindingLinesShouldContainBindingSummary() {
        Map<UUID, InteractionBinding> bindings = Map.of(
                UUID.randomUUID(),
                new InteractionBinding(
                        UUID.randomUUID(),
                        "main_menu",
                        "close",
                        ComponentAction.closeWindow()
                )
        );

        List<String> lines = InteractiveDisplayCommand.buildBindingLines("Steve", bindings);

        assertTrue(lines.get(0).contains("바인딩 수=1"));
        assertTrue(lines.get(1).contains("component=close"));
        assertTrue(lines.get(1).contains("action=CLOSE_WINDOW"));
    }

    @Test
    void listLinesShouldContainLoadedWindowsAndCandidates() {
        List<String> lines = InteractiveDisplayCommand.buildListLines(Set.of("main_menu"), Set.of("main_menu", "gallery"));

        assertTrue(lines.get(0).contains("loaded=1"));
        assertTrue(lines.get(1).contains("main_menu"));
        assertTrue(lines.get(2).contains("gallery"));
    }
}
