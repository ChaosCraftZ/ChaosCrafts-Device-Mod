package net.chaoscraft.chaoscrafts_device_mod.Core.PC.block.entity;

import net.chaoscraft.chaoscrafts_device_mod.CDM;
import net.chaoscraft.chaoscrafts_device_mod.Core.PC.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, CDM.MOD_ID);

    public static final RegistryObject<BlockEntityType<LaptopEntity>> LAPTOP_ENTITY =
            BLOCK_ENTITIES.register("laptop_entity", () ->
                    BlockEntityType.Builder.of(LaptopEntity::new,
                            ModBlocks.LAPTOP_BLACK.get(), ModBlocks.LAPTOP_WHITE.get()).build(null));


    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}