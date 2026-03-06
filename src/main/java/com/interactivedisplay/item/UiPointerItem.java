package com.interactivedisplay.item;

import com.interactivedisplay.InteractiveDisplay;
import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.packettweaker.PacketContext;

public final class UiPointerItem extends SimplePolymerItem {
    private static final Identifier MODEL_ID = Identifier.of(InteractiveDisplay.MOD_ID, "pointer");

    public UiPointerItem(Item.Settings settings) {
        super(settings, Items.STICK, true);
    }

    @Override
    public @Nullable Identifier getPolymerItemModel(ItemStack stack, PacketContext context) {
        return MODEL_ID;
    }
}
