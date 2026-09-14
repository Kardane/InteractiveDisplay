package com.interactivedisplay.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.interactivedisplay.core.positioning.PositionMode;
import com.mojang.brigadier.CommandDispatcher;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InteractiveDisplayCommandTreeTest {
    @Test
    void groupSubcommandsShouldUseIndependentPermissions() {
        TestHandlers handlers = new TestHandlers();
        CommandDispatcher<TestSource> dispatcher = new CommandDispatcher<>();
        InteractiveDisplayCommandTree.register(
                dispatcher,
                handlers,
                source -> source.permissions.contains("create"),
                source -> source.permissions.contains("remove"),
                source -> source.permissions.contains("reload"),
                source -> source.permissions.contains("list"),
                source -> source.permissions.contains("debug"),
                (context, builder) -> builder.suggest("main_menu").buildFuture()
        );

        var group = dispatcher.getRoot().getChild("interactivedisplay").getChild("group");
        TestSource createOnly = new TestSource(Set.of("create"));
        TestSource removeOnly = new TestSource(Set.of("remove"));
        TestSource listOnly = new TestSource(Set.of("list"));

        assertTrue(group.canUse(createOnly));
        assertTrue(group.getChild("create").canUse(createOnly));
        assertFalse(group.getChild("remove").canUse(createOnly));
        assertFalse(group.getChild("list").canUse(createOnly));

        assertTrue(group.canUse(removeOnly));
        assertFalse(group.getChild("create").canUse(removeOnly));
        assertTrue(group.getChild("remove").canUse(removeOnly));
        assertFalse(group.getChild("list").canUse(removeOnly));

        assertTrue(group.canUse(listOnly));
        assertFalse(group.getChild("create").canUse(listOnly));
        assertFalse(group.getChild("remove").canUse(listOnly));
        assertTrue(group.getChild("list").canUse(listOnly));
    }

    private static final class TestSource {
        final Set<String> permissions;

        private TestSource(Set<String> permissions) {
            this.permissions = new HashSet<>(permissions);
        }
    }

    private static final class TestHandlers implements InteractiveDisplayCommandTree.Handlers<TestSource> {
        @Override
        public int create(com.mojang.brigadier.context.CommandContext<TestSource> context,
                          String windowId,
                          PositionMode positionMode,
                          net.minecraft.world.phys.Vec3 position,
                          InteractiveDisplayCommandTree.Rotation rotation) {
            return 1;
        }

        @Override
        public int remove(com.mojang.brigadier.context.CommandContext<TestSource> context, String windowId) {
            return 1;
        }

        @Override
        public int reload(com.mojang.brigadier.context.CommandContext<TestSource> context, String windowId) {
            return 1;
        }

        @Override
        public int debugStatus(com.mojang.brigadier.context.CommandContext<TestSource> context) {
            return 1;
        }

        @Override
        public int debugRecent(com.mojang.brigadier.context.CommandContext<TestSource> context) {
            return 1;
        }

        @Override
        public int debugWindow(com.mojang.brigadier.context.CommandContext<TestSource> context, String windowId) {
            return 1;
        }

        @Override
        public int debugBindings(com.mojang.brigadier.context.CommandContext<TestSource> context) {
            return 1;
        }

        @Override
        public int list(com.mojang.brigadier.context.CommandContext<TestSource> context) {
            return 1;
        }

        @Override
        public int groupCreate(com.mojang.brigadier.context.CommandContext<TestSource> context,
                               String groupId,
                               PositionMode positionMode,
                               net.minecraft.world.phys.Vec3 position,
                               InteractiveDisplayCommandTree.Rotation rotation) {
            return 1;
        }

        @Override
        public int groupRemove(com.mojang.brigadier.context.CommandContext<TestSource> context, String groupId) {
            return 1;
        }

        @Override
        public int groupList(com.mojang.brigadier.context.CommandContext<TestSource> context) {
            return 1;
        }
    }
}
