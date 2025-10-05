package net.chaoscraft.chaoscrafts_device_mod.Core.PC.item;

import net.chaoscraft.chaoscrafts_device_mod.CDM;
import net.chaoscraft.chaoscrafts_device_mod.Core.PC.block.ModBlocks;
import net.chaoscraft.chaoscrafts_device_mod.Core.PC.item.custom.LaptopItem;
import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, CDM.MOD_ID);

    public static final RegistryObject<Item> LAPTOP_BLACK_ITEM = ITEMS.register("laptop_black",
            () -> new LaptopItem(ModBlocks.LAPTOP_BLACK.get(), new Item.Properties(), false));

    public static final RegistryObject<Item> LAPTOP_WHITE_ITEM = ITEMS.register("laptop_white",
            () -> new LaptopItem(ModBlocks.LAPTOP_WHITE.get(), new Item.Properties(), true));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}