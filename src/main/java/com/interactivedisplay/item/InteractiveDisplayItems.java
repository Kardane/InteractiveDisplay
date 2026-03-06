package com.interactivedisplay.item;

import com.interactivedisplay.InteractiveDisplay;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class InteractiveDisplayItems {
    public static final Identifier POINTER_ID = Identifier.of(InteractiveDisplay.MOD_ID, "pointer");
    private static final RegistryKey<Item> POINTER_KEY = RegistryKey.of(RegistryKeys.ITEM, POINTER_ID);
    public static final UiPointerItem POINTER = new UiPointerItem(new Item.Settings().registryKey(POINTER_KEY).maxCount(1));

    private static boolean registered;

    private InteractiveDisplayItems() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        Registry.register(Registries.ITEM, POINTER_ID, POINTER);
        registered = true;
    }

    public static boolean isPointer(ItemStack stack) {
        return stack != null && stack.getItem() instanceof UiPointerItem;
    }
}
