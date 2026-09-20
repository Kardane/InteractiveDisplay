package com.interactivedisplay.gametest;

import com.interactivedisplay.core.positioning.PositionMode;
import com.interactivedisplay.entity.VirtualWindowHolder;
import eu.pb4.polymer.virtualentity.api.elements.TextDisplayElement;
import java.lang.reflect.Method;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

public final class InteractiveDisplayPassengerOriginGameTest implements CustomTestMethodInvoker {
    private static final double EPSILON = 1.0E-5D;

    @SuppressWarnings("removal")
    @GameTest
    public void passengerRenderOriginShouldTrackCommonPlayerPoses(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        VirtualWindowHolder holder = new VirtualWindowHolder(helper.getLevel(), player.position());
        holder.configure(PositionMode.PLAYER_FIXED, player);
        holder.addElement(new TextDisplayElement());

        verifyPose(helper, holder, player, Pose.STANDING);
        verifyPose(helper, holder, player, Pose.CROUCHING);

        player.setPose(Pose.STANDING);
        holder.destroy();
        helper.assertTrue(holder.entityCount() == 0,
                Component.literal("passenger-origin fixture leaked virtual entities"));
        helper.succeed();
    }

    private static void verifyPose(
            GameTestHelper helper,
            VirtualWindowHolder holder,
            ServerPlayer player,
            Pose pose
    ) {
        player.setPose(pose);
        double expectedHeight = player.getDimensions(pose).height();
        helper.assertTrue(Math.abs(player.getBbHeight() - expectedHeight) <= EPSILON,
                Component.literal(pose + " player dimensions did not apply expectedHeight=" + expectedHeight
                        + " actual=" + player.getBbHeight()));

        Vec3 origin = holder.passengerRenderOrigin();
        Vec3 expectedOrigin = player.getPassengerRidingPosition(player);
        helper.assertTrue(origin.distanceToSqr(expectedOrigin) <= EPSILON * EPSILON,
                Component.literal(pose + " passenger render origin mismatch expected=" + expectedOrigin + " actual=" + origin));
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
