package net.chaoscraft.chaoscrafts_device_mod.Core.PC.block;

import net.chaoscraft.chaoscrafts_device_mod.CDM;
import net.chaoscraft.chaoscrafts_device_mod.Core.PC.block.custom.Laptop;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, CDM.MOD_ID);

    // Register two separate blocks that use the same Laptop class and model but are distinct blocks
    public static final RegistryObject<Block> LAPTOP_BLACK = BLOCKS.register("laptop_black",
            () -> new Laptop(BlockBehaviour.Properties.copy(Blocks.IRON_BARS).noOcclusion()));

    public static final RegistryObject<Block> LAPTOP_WHITE = BLOCKS.register("laptop_white",
            () -> new Laptop(BlockBehaviour.Properties.copy(Blocks.IRON_BARS).noOcclusion()));


    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}