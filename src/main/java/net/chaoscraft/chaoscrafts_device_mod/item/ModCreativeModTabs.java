package net.chaoscraft.chaoscrafts_device_mod.item;

import net.chaoscraft.chaoscrafts_device_mod.CDM;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CDM.MOD_ID);

    public static final RegistryObject<CreativeModeTab> CDM_TAB = CREATIVE_MODE_TABS.register("cdm_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.LAPTOP_BLACK_ITEM.get()))
                    .title(Component.translatable("creativetab.cdm_tab"))
                    .displayItems((pParameters, pOutput) -> {
                        // Add both laptop variants to the creative tab
                        pOutput.accept(ModItems.LAPTOP_BLACK_ITEM.get());
                        pOutput.accept(ModItems.LAPTOP_WHITE_ITEM.get());
                    })

                    .build());


    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}