package net.chaoscraft.chaoscrafts_device_mod.Core.PC.item.client;

import net.chaoscraft.chaoscrafts_device_mod.CDM;
import net.chaoscraft.chaoscrafts_device_mod.Core.PC.item.custom.LaptopItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class LaptopItemModel extends GeoModel<LaptopItem> {
    @Override
    public ResourceLocation getModelResource(LaptopItem LaptopItem) {
        return ResourceLocation.fromNamespaceAndPath(CDM.MOD_ID, "geo/laptop.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(LaptopItem item) {
        String tex = (item != null && item.isWhiteItem()) ? "textures/block/laptop_uv_white.png" : "textures/block/laptop_uv_black.png";
        return ResourceLocation.fromNamespaceAndPath(CDM.MOD_ID, tex);
    }

    @Override
    public ResourceLocation getAnimationResource(LaptopItem LaptopItem) {
        return ResourceLocation.fromNamespaceAndPath(CDM.MOD_ID, "animations/laptop.animation.json");
    }
}