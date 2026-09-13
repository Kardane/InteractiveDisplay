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

public final class InteractiveDisplayOwnerWatchGameTest implements CustomTestMethodInvoker {
    @SuppressWarnings("removal")
    @GameTest
    public void virtualHolderShouldOnlyAllowItsOwnerToWatch(GameTestHelper helper) {
        var manager = InteractiveDisplay.instance().windowManager();
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ServerPlayer other = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(!owner.getUUID().equals(other.getUUID()),
                Component.literal("owner-only fixture players unexpectedly share UUID"));

        var opened = manager.createWindow(owner, "main_menu", PositionMode.PLAYER_FIXED, null, 0.0f, 0.0f);
        helper.assertTrue(opened.success(), Component.literal("owner-only fixture failed to open: " + opened.message()));
        var instance = manager.findActiveWindow(owner.getUUID(), "main_menu");
        helper.assertTrue(instance != null, Component.literal("owner-only fixture runtime missing"));
        VirtualWindowHolder holder = instance.virtualHolder();

        helper.assertTrue(holder.isWatching(), Component.literal("owner was not watching its virtual holder after open"));

        holder.stopWatching(owner);
        helper.assertTrue(!holder.isWatching(), Component.literal("owner stopWatching did not clear watch state"));

        holder.startWatching(other);
        helper.assertTrue(!holder.isWatching(),
                Component.literal("non-owner was allowed to start watching another player's virtual holder"));

        holder.startWatching(owner);
        helper.assertTrue(holder.isWatching(), Component.literal("owner could not resume watching its own holder"));

        helper.assertTrue(manager.removeWindow(owner.getUUID(), "main_menu").success(),
                Component.literal("owner-only fixture cleanup failed"));
        VirtualWindowHolder.destroyAllPending(helper.getLevel().getServer());
        helper.assertTrue(holder.entityCount() == 0,
                Component.literal("owner-only holder retained entities after cleanup"));

        helper.succeed();
    }

    @Override
    public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
        method.invoke(this, helper);
    }
}
