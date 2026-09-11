package com.interactivedisplay.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.interactivedisplay.core.positioning.PositionMode;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class FocusedReloadCommandTreeTest {
    @Test
    void reloadWindowIdCommandShouldDispatchExactlyThatId() throws Exception {
        CommandDispatcher<Source> dispatcher = new CommandDispatcher<>();
        Handlers handlers = new Handlers();
        InteractiveDisplayCommandTree.register(
                dispatcher,
                handlers,
                source -> true,
                source -> true,
                source -> true,
                source -> true,
                source -> true,
                (context, builder) -> builder.suggest("main_menu").buildFuture()
        );

        int result = dispatcher.execute("interactivedisplay reload main_menu", new Source());

        assertEquals(1, result);
        assertEquals("main_menu", handlers.reloadedId);
    }

    private static final class Source {
    }

    private static final class Handlers implements InteractiveDisplayCommandTree.Handlers<Source> {
        String reloadedId;

        @Override
        public int create(CommandContext<Source> context, String windowId, PositionMode positionMode, Vec3 position, InteractiveDisplayCommandTree.Rotation rotation) {
            return 1;
        }

        @Override
        public int remove(CommandContext<Source> context, String windowId) {
            return 1;
        }

        @Override
        public int reload(CommandContext<Source> context, String windowId) {
            this.reloadedId = windowId;
            return 1;
        }

        @Override
        public int debugStatus(CommandContext<Source> context) {
            return 1;
        }

        @Override
        public int debugRecent(CommandContext<Source> context) {
            return 1;
        }

        @Override
        public int debugWindow(CommandContext<Source> context, String windowId) {
            return 1;
        }

        @Override
        public int debugBindings(CommandContext<Source> context) {
            return 1;
        }

        @Override
        public int list(CommandContext<Source> context) {
            return 1;
        }

        @Override
        public int groupCreate(CommandContext<Source> context, String groupId, PositionMode positionMode, Vec3 position, InteractiveDisplayCommandTree.Rotation rotation) {
            return 1;
        }

        @Override
        public int groupRemove(CommandContext<Source> context, String groupId) {
            return 1;
        }

        @Override
        public int groupList(CommandContext<Source> context) {
            return 1;
        }
    }
}
