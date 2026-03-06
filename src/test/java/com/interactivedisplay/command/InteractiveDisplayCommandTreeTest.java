package com.interactivedisplay.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.core.positioning.PositionMode;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InteractiveDisplayCommandTreeTest {
    @Test
    void createCommandShouldUseEntitySelectorArgumentAndFixedPositionNodes() {
        TestHandlers handlers = new TestHandlers();
        CommandDispatcher<TestSource> dispatcher = new CommandDispatcher<>();
        register(dispatcher, handlers, source -> source.permissions.contains("create"), source -> true, source -> true, source -> true, source -> true);

        var root = dispatcher.getRoot().getChild("interactivedisplay");
        var create = root.getChild("create");
        var windowId = create.getChild("windowId");
        var player = windowId.getChild("player");
        var fixed = player.getChild("fixed");

        assertTrue(player instanceof ArgumentCommandNode<?, ?>);
        assertEquals("EntityArgumentType", ((ArgumentCommandNode<?, ?>) player).getType().getClass().getSimpleName());
        assertNotNull(fixed.getChild("x").getChild("y").getChild("z"));
    }

    @Test
    void playerViewCreateLiteralShouldBeExecutable() {
        TestHandlers handlers = new TestHandlers();
        CommandDispatcher<TestSource> dispatcher = new CommandDispatcher<>();
        register(dispatcher, handlers, source -> source.permissions.contains("create"), source -> true, source -> true, source -> true, source -> true);

        var playerView = dispatcher.getRoot()
                .getChild("interactivedisplay")
                .getChild("create")
                .getChild("windowId")
                .getChild("player")
                .getChild("player_view");

        assertNotNull(playerView.getCommand());
    }

    @Test
    void listCommandShouldParse() throws Exception {
        TestHandlers handlers = new TestHandlers();
        CommandDispatcher<TestSource> dispatcher = new CommandDispatcher<>();
        register(dispatcher, handlers, source -> true, source -> true, source -> true, source -> true, source -> true);

        int result = dispatcher.execute("interactivedisplay list", new TestSource(Set.of("create")));

        assertEquals(1, result);
        assertEquals("list", handlers.lastCall);
    }

    @Test
    void windowSuggestionsShouldIncludeConfiguredCandidates() {
        TestHandlers handlers = new TestHandlers();
        CommandDispatcher<TestSource> dispatcher = new CommandDispatcher<>();
        InteractiveDisplayCommandTree.register(dispatcher, handlers,
                source -> true,
                source -> true,
                source -> true,
                source -> true,
                source -> true,
                (context, builder) -> builder.suggest("main_menu").suggest("gallery").buildFuture());

        var parse = dispatcher.parse("interactivedisplay create ", new TestSource(Set.of("create")));
        var suggestions = dispatcher.getCompletionSuggestions(parse).join();

        assertTrue(suggestions.getList().stream().anyMatch(s -> "main_menu".equals(s.getText())));
        assertTrue(suggestions.getList().stream().anyMatch(s -> "gallery".equals(s.getText())));
    }

    private static void register(CommandDispatcher<TestSource> dispatcher,
                                 TestHandlers handlers,
                                 java.util.function.Predicate<TestSource> canCreate,
                                 java.util.function.Predicate<TestSource> canRemove,
                                 java.util.function.Predicate<TestSource> canReload,
                                 java.util.function.Predicate<TestSource> canList,
                                 java.util.function.Predicate<TestSource> canDebug) {
        InteractiveDisplayCommandTree.register(dispatcher, handlers,
                canCreate,
                canRemove,
                canReload,
                canList,
                canDebug,
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
                          PositionMode positionMode,
                          net.minecraft.util.math.Vec3d position) {
            this.lastCall = "create:" + windowId + ":" + positionMode;
            return 1;
        }

        @Override
        public int remove(com.mojang.brigadier.context.CommandContext<TestSource> context, String windowId) {
            this.lastCall = "remove:" + windowId;
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
        public int debugRecent(com.mojang.brigadier.context.CommandContext<TestSource> context) {
            this.lastCall = "debugRecent";
            return 1;
        }

        @Override
        public int debugWindow(com.mojang.brigadier.context.CommandContext<TestSource> context, String windowId) {
            this.lastCall = "debugWindow:" + windowId;
            return 1;
        }

        @Override
        public int debugBindings(com.mojang.brigadier.context.CommandContext<TestSource> context) {
            this.lastCall = "debugBindings";
            return 1;
        }

        @Override
        public int list(com.mojang.brigadier.context.CommandContext<TestSource> context) {
            this.lastCall = "list";
            return 1;
        }
    }
}
