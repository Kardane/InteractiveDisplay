package com.interactivedisplay.item;

import com.interactivedisplay.InteractiveDisplay;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class InteractiveDisplayItems {
    public static final ResourceLocation POINTER_ID = ResourceLocation.fromNamespaceAndPath(InteractiveDisplay.MOD_ID, "pointer");
    private static final ResourceKey<Item> POINTER_KEY = ResourceKey.create(Registries.ITEM, POINTER_ID);
    public static final UiPointerItem POINTER = new UiPointerItem(new Item.Properties().setId(POINTER_KEY).stacksTo(1));

    private static boolean registered;

    private InteractiveDisplayItems() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        Registry.register(BuiltInRegistries.ITEM, POINTER_ID, POINTER);
        registered = true;
    }

    public static boolean isPointer(ItemStack stack) {
        return stack != null && stack.getItem() instanceof UiPointerItem;
    }
}
