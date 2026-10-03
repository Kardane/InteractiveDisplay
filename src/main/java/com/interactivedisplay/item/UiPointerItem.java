package com.interactivedisplay.item;

import com.interactivedisplay.InteractiveDisplay;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.core.HolderLookup;

public final class UiPointerItem extends SimplePolymerItem {
    private static final Identifier MODEL_ID = Identifier.fromNamespaceAndPath(InteractiveDisplay.MOD_ID, "pointer");

    public UiPointerItem(Item.Properties settings) {
        super(settings, Items.STICK, true);
    }

    @Override
    public @Nullable Identifier getPolymerItemModel(ItemStack stack, PacketContext context, HolderLookup.Provider registries) {
        return MODEL_ID;
    }
}
