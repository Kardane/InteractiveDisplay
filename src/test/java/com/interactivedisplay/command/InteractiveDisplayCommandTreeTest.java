package com.interactivedisplay.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.core.positioning.PositionMode;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InteractiveDisplayCommandTreeTest {
    @Test
    void fixedCreateCommandShouldParseExplicitPosition() throws Exception {
        TestHandlers handlers = new TestHandlers();
        CommandDispatcher<TestSource> dispatcher = new CommandDispatcher<>();
        register(dispatcher, handlers, source -> source.permissions.contains("create"), source -> true, source -> true, source -> true);

        int result = dispatcher.execute("interactivedisplay create main_menu Steve fixed 1 2 3", new TestSource(Set.of("create")));

        assertEquals(1, result);
        assertEquals("create:main_menu:Steve:FIXED:(1.0,2.0,3.0)", handlers.lastCall);
    }

    @Test
    void playerViewCreateCommandShouldParseWithoutPosition() throws Exception {
        TestHandlers handlers = new TestHandlers();
        CommandDispatcher<TestSource> dispatcher = new CommandDispatcher<>();
        register(dispatcher, handlers, source -> source.permissions.contains("create"), source -> true, source -> true, source -> true);

        int result = dispatcher.execute("interactivedisplay create main_menu Steve player_view", new TestSource(Set.of("create")));

        assertEquals(1, result);
        assertEquals("create:main_menu:Steve:PLAYER_VIEW:null", handlers.lastCall);
    }

    @Test
    void permissionDeniedShouldFail() {
        TestHandlers handlers = new TestHandlers();
        CommandDispatcher<TestSource> dispatcher = new CommandDispatcher<>();
        register(dispatcher, handlers, source -> false, source -> false, source -> false, source -> false);

        assertThrows(CommandSyntaxException.class,
                () -> dispatcher.execute("interactivedisplay create main Steve fixed", new TestSource(Set.of())));
    }

    @Test
    void playerArgumentShouldProvideSuggestions() {
        TestHandlers handlers = new TestHandlers();
        CommandDispatcher<TestSource> dispatcher = new CommandDispatcher<>();
        InteractiveDisplayCommandTree.register(dispatcher, handlers,
                source -> true,
                source -> true,
                source -> true,
                source -> true,
                (context, builder) -> builder.suggest("Karned").suggest("Alex").buildFuture(),
                (context, builder) -> builder.suggest("main_menu").buildFuture());

        var parse = dispatcher.parse("interactivedisplay create main_menu ", new TestSource(Set.of("create")));
        var suggestions = dispatcher.getCompletionSuggestions(parse).join();

        assertTrue(suggestions.getList().stream().anyMatch(s -> "Karned".equals(s.getText())));
        assertTrue(suggestions.getList().stream().anyMatch(s -> "Alex".equals(s.getText())));
    }

    private static void register(CommandDispatcher<TestSource> dispatcher,
                                 TestHandlers handlers,
                                 java.util.function.Predicate<TestSource> canCreate,
                                 java.util.function.Predicate<TestSource> canRemove,
                                 java.util.function.Predicate<TestSource> canReload,
                                 java.util.function.Predicate<TestSource> canDebug) {
        InteractiveDisplayCommandTree.register(dispatcher, handlers,
                canCreate,
                canRemove,
                canReload,
                canDebug,
                (context, builder) -> builder.suggest("Steve").buildFuture(),
                (context, builder) -> builder.suggest("main_menu").buildFuture());
    }

    private static final class TestSource {
        final Set<String> permissions;

        private TestSource(Set<String> permissions) {
            this.permissions = new HashSet<>(permissions);
        }
    }

    private static final class TestHandlers implements InteractiveDisplayCommandTree.Handlers<TestSource> {
        String lastCall;

        @Override
        public int create(com.mojang.brigadier.context.CommandContext<TestSource> context,
                          String windowId,
                          String playerName,
                          PositionMode positionMode,
                          net.minecraft.util.math.Vec3d position) {
            if (position == null) {
                this.lastCall = "create:" + windowId + ":" + playerName + ":" + positionMode + ":null";
            } else {
                this.lastCall = "create:" + windowId + ":" + playerName + ":" + positionMode + ":(" + position.x + "," + position.y + "," + position.z + ")";
            }
            return 1;
        }

        @Override
        public int remove(com.mojang.brigadier.context.CommandContext<TestSource> context, String windowId, String playerName) {
            this.lastCall = "remove:" + windowId + ":" + playerName;
            return 1;
        }

        @Override
        public int reload(com.mojang.brigadier.context.CommandContext<TestSource> context, String windowId) {
            this.lastCall = "reload:" + windowId;
            return 1;
        }

        @Override
        public int debugStatus(com.mojang.brigadier.context.CommandContext<TestSource> context) {
            this.lastCall = "debugStatus";
            return 1;
        }

        @Override
        public int debugRecent(com.mojang.brigadier.context.CommandContext<TestSource> context, String playerName) {
            this.lastCall = "debugRecent:" + playerName;
            return 1;
        }

        @Override
        public int debugWindow(com.mojang.brigadier.context.CommandContext<TestSource> context, String windowId, String playerName) {
            this.lastCall = "debugWindow:" + windowId + ":" + playerName;
            return 1;
        }

        @Override
        public int debugBindings(com.mojang.brigadier.context.CommandContext<TestSource> context, String playerName) {
            this.lastCall = "debugBindings:" + playerName;
            return 1;
        }
    }
}
