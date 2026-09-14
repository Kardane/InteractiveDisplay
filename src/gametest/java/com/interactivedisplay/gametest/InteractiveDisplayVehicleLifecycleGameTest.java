package com.interactivedisplay.gametest;

import com.interactivedisplay.InteractiveDisplay;
import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.entity.VirtualWindowHolder;
import java.lang.reflect.Method;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Items;

public final class InteractiveDisplayVehicleLifecycleGameTest implements CustomTestMethodInvoker {
    @SuppressWarnings("removal")
    @GameTest
    public void playerFixedHudRuntimeShouldSurviveBoatMountAndDismount(GameTestHelper helper) {
        var manager = InteractiveDisplay.instance().windowManager();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        var opened = manager.createWindow(player, "main_menu", PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
        helper.assertTrue(opened.success(), Component.literal("vehicle fixture window open failed: " + opened.message()));
        var instance = manager.findActiveWindow(player.getUUID(), "main_menu");
        helper.assertTrue(instance != null, Component.literal("vehicle fixture runtime missing"));
        VirtualWindowHolder holder = instance.virtualHolder();
        int entityCount = instance.entityCount();
        int bindingCount = manager.bindingSnapshots(player.getUUID()).size();
        Set<Integer> entityIds = Set.copyOf(instance.entityIds());
        helper.assertTrue(holder.isWatching(), Component.literal("vehicle fixture holder not watched before mount"));
        helper.assertTrue(entityCount > 0, Component.literal("vehicle fixture has no virtual entities"));
        helper.assertTrue(bindingCount > 0, Component.literal("vehicle fixture has no interaction bindings"));

        Boat boat = new Boat(EntityType.OAK_BOAT, helper.getLevel(), () -> Items.OAK_BOAT);
        boat.setPos(player.getX(), player.getY(), player.getZ());
        helper.assertTrue(helper.getLevel().addFreshEntity(boat), Component.literal("vehicle fixture boat spawn failed"));

        try {
            helper.assertTrue(player.startRiding(boat, true), Component.literal("player failed to mount QA boat"));
            helper.assertTrue(player.isPassenger() && player.getVehicle() == boat,
                    Component.literal("player vehicle relation missing after mount"));

            manager.tick();
            var mounted = manager.findActiveWindow(player.getUUID(), "main_menu");
            helper.assertTrue(mounted == instance,
                    Component.literal("PLAYER_FIXED runtime was rebuilt while mounting boat"));
            helper.assertTrue(mounted.virtualHolder() == holder && holder.isWatching(),
                    Component.literal("PLAYER_FIXED holder lost ownership/watch state while mounted"));
            helper.assertTrue(mounted.entityCount() == entityCount && Set.copyOf(mounted.entityIds()).equals(entityIds),
                    Component.literal("PLAYER_FIXED virtual entity identity changed while mounted"));
            helper.assertTrue(manager.bindingSnapshots(player.getUUID()).size() == bindingCount,
                    Component.literal("PLAYER_FIXED interaction bindings changed while mounted"));
            helper.assertTrue(player.isPassenger() && player.getVehicle() == boat,
                    Component.literal("InteractiveDisplay tick disturbed the real vehicle relation"));

            player.stopRiding();
            helper.assertTrue(!player.isPassenger() && player.getVehicle() == null,
                    Component.literal("player failed to dismount QA boat"));

            manager.tick();
            var dismounted = manager.findActiveWindow(player.getUUID(), "main_menu");
            helper.assertTrue(dismounted == instance,
                    Component.literal("PLAYER_FIXED runtime was rebuilt after boat dismount"));
            helper.assertTrue(dismounted.virtualHolder() == holder && holder.isWatching(),
                    Component.literal("PLAYER_FIXED holder lost ownership/watch state after dismount"));
            helper.assertTrue(dismounted.entityCount() == entityCount && Set.copyOf(dismounted.entityIds()).equals(entityIds),
                    Component.literal("PLAYER_FIXED virtual entity identity changed after dismount"));
            helper.assertTrue(manager.bindingSnapshots(player.getUUID()).size() == bindingCount,
                    Component.literal("PLAYER_FIXED interaction bindings changed after dismount"));
        } finally {
            player.stopRiding();
            boat.discard();
            manager.removeAll(player.getUUID());
            VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
        }

        helper.assertTrue(manager.ownerWindows(player.getUUID()).isEmpty(),
                Component.literal("vehicle fixture leaked owner window state"));
        helper.assertTrue(holder.entityCount() == 0,
                Component.literal("vehicle fixture leaked virtual holder entities"));
        helper.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
