package net.chaoscraft.chaoscrafts_device_mod.block.entity.client;

import net.chaoscraft.chaoscrafts_device_mod.CDM;
import net.chaoscraft.chaoscrafts_device_mod.block.entity.LaptopEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class LaptopModel extends GeoModel<LaptopEntity> {
    @Override
    public ResourceLocation getModelResource(LaptopEntity laptopEntity) {
        return ResourceLocation.fromNamespaceAndPath(CDM.MOD_ID, "geo/laptop.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(LaptopEntity laptopEntity) {
        String tex = laptopEntity != null && laptopEntity.isWhiteVariant() ? "textures/block/laptop_uv_white.png" : "textures/block/laptop_uv_black.png";
        return ResourceLocation.fromNamespaceAndPath(CDM.MOD_ID, tex);
    }

    @Override
    public ResourceLocation getAnimationResource(LaptopEntity laptopEntity) {
        return ResourceLocation.fromNamespaceAndPath(CDM.MOD_ID, "animations/laptop.animation.json");
    }
}