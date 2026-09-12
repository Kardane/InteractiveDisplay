package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.entity.VirtualWindowHolder;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class InteractiveDisplayDimensionLifecycleGameTest implements CustomTestMethodInvoker {
    @SuppressWarnings("removal")
    @GameTest
    public void playerBoundWindowsMigrateAndFixedWindowClosesAcrossDimensionChange(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var manager = InteractiveDisplay.instance().windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        helper.assertTrue(player.level().dimension().equals(Level.OVERWORLD),
                Component.literal("dimension fixture did not start in overworld"));
        helper.assertTrue(server.getLevel(Level.NETHER) != null,
                Component.literal("nether level unavailable in GameTest server"));

        verifyPlayerBoundMigration(helper, dispatcher, manager, player, PositionMode.PLAYER_FIXED);
        verifyPlayerBoundMigration(helper, dispatcher, manager, player, PositionMode.PLAYER_VIEW);
        verifyFixedRemoval(helper, dispatcher, manager, player);

        helper.succeed();
    }

    private static void verifyPlayerBoundMigration(
            GameTestHelper helper,
            com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
            com.interactivedisplay.core.window.WindowManager manager,
            ServerPlayer player,
            PositionMode mode
    ) throws Exception {
        var opened = manager.createWindow(player, "main_menu", mode, null, 0.0f, 0.0f);
        helper.assertTrue(opened.success(), Component.literal(mode + " open failed: " + opened.message()));
        var overworldInstance = manager.findActiveWindow(player.getUUID(), "main_menu");
        helper.assertTrue(overworldInstance != null && overworldInstance.worldKey().equals(Level.OVERWORLD),
                Component.literal(mode + " did not open in overworld"));
        var overworldHolder = overworldInstance.virtualHolder();
        int initialBindings = manager.bindingSnapshots(player.getUUID()).size();
        helper.assertTrue(initialBindings > 0, Component.literal(mode + " fixture has no interaction binding"));

        teleport(helper, dispatcher, player, "minecraft:the_nether", 0, 80, 0);
        helper.assertTrue(player.level().dimension().equals(Level.NETHER),
                Component.literal(mode + " player did not enter nether"));
        manager.tick();

        var netherInstance = manager.findActiveWindow(player.getUUID(), "main_menu");
        helper.assertTrue(netherInstance != null && netherInstance != overworldInstance,
                Component.literal(mode + " runtime was not rebuilt after overworld -> nether"));
        helper.assertTrue(netherInstance.worldKey().equals(Level.NETHER),
                Component.literal(mode + " rebuilt runtime is not in nether"));
        helper.assertTrue(netherInstance.positionMode() == mode,
                Component.literal(mode + " migration changed position mode"));
        helper.assertTrue(netherInstance.virtualHolder() != overworldHolder,
                Component.literal(mode + " migration reused old holder"));
        helper.assertTrue(overworldHolder.entityCount() == 0,
                Component.literal(mode + " overworld holder retained entities after migration"));
        helper.assertTrue(manager.bindingSnapshots(player.getUUID()).size() == initialBindings,
                Component.literal(mode + " bindings changed after overworld -> nether"));

        var netherHolder = netherInstance.virtualHolder();
        teleport(helper, dispatcher, player, "minecraft:overworld", 0, 80, 0);
        helper.assertTrue(player.level().dimension().equals(Level.OVERWORLD),
                Component.literal(mode + " player did not return to overworld"));
        manager.tick();

        var returnedInstance = manager.findActiveWindow(player.getUUID(), "main_menu");
        helper.assertTrue(returnedInstance != null && returnedInstance != netherInstance,
                Component.literal(mode + " runtime was not rebuilt after nether -> overworld"));
        helper.assertTrue(returnedInstance.worldKey().equals(Level.OVERWORLD),
                Component.literal(mode + " returned runtime is not in overworld"));
        helper.assertTrue(returnedInstance.positionMode() == mode,
                Component.literal(mode + " return migration changed position mode"));
        helper.assertTrue(netherHolder.entityCount() == 0,
                Component.literal(mode + " nether holder retained entities after return migration"));
        helper.assertTrue(manager.bindingSnapshots(player.getUUID()).size() == initialBindings,
                Component.literal(mode + " bindings changed after nether -> overworld"));

        var removed = manager.removeWindow(player.getUUID(), "main_menu");
        helper.assertTrue(removed.success(), Component.literal(mode + " cleanup remove failed"));
        VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
        helper.assertTrue(manager.findActiveWindow(player.getUUID(), "main_menu") == null,
                Component.literal(mode + " fixture leaked active window"));
        helper.assertTrue(manager.bindingSnapshots(player.getUUID()).isEmpty(),
                Component.literal(mode + " fixture leaked bindings"));
    }

    private static void verifyFixedRemoval(
            GameTestHelper helper,
            com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
            com.interactivedisplay.core.window.WindowManager manager,
            ServerPlayer player
    ) throws Exception {
        Vec3 anchor = player.position().add(0.0, 2.0, 2.0);
        var opened = manager.createWindow(player, "main_menu", PositionMode.FIXED, anchor, 35.0f, 0.0f);
        helper.assertTrue(opened.success(), Component.literal("FIXED open failed: " + opened.message()));
        var fixed = manager.findActiveWindow(player.getUUID(), "main_menu");
        helper.assertTrue(fixed != null && fixed.worldKey().equals(Level.OVERWORLD),
                Component.literal("FIXED fixture did not open in overworld"));
        var fixedHolder = fixed.virtualHolder();

        teleport(helper, dispatcher, player, "minecraft:the_nether", 0, 80, 0);
        manager.tick();
        helper.assertTrue(manager.findActiveWindow(player.getUUID(), "main_menu") == null,
                Component.literal("FIXED window remained active after owner changed dimension"));
        helper.assertTrue(manager.bindingSnapshots(player.getUUID()).isEmpty(),
                Component.literal("FIXED bindings remained after owner changed dimension"));
        VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
        helper.assertTrue(fixedHolder.entityCount() == 0,
                Component.literal("FIXED old-world holder retained entities after exit cleanup"));

        teleport(helper, dispatcher, player, "minecraft:overworld", 0, 80, 0);
    }

    private static void teleport(
            GameTestHelper helper,
            com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
            ServerPlayer player,
            String dimension,
            double x,
            double y,
            double z
    ) throws Exception {
        var source = player.createCommandSourceStack().withPermission(4);
        String command = "execute in " + dimension + " run tp @s " + x + " " + y + " " + z;
        int result = dispatcher.execute(command, source);
        helper.assertTrue(result > 0, Component.literal("dimension teleport command failed: " + command));
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
