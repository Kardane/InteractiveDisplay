package com.interactivedisplay.item;

import com.interactivedisplay.InteractiveDisplay;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

public final class UiPointerItem extends SimplePolymerItem {
    private static final ResourceLocation MODEL_ID = ResourceLocation.fromNamespaceAndPath(InteractiveDisplay.MOD_ID, "pointer");

    public UiPointerItem(Item.Properties settings) {
        super(settings, Items.STICK, true);
    }

    @Override
    public @Nullable ResourceLocation getPolymerItemModel(ItemStack stack, PacketContext context) {
        return MODEL_ID;
    }
}
