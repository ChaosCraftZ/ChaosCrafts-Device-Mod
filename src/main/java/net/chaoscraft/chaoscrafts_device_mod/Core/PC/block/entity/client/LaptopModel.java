package net.chaoscraft.chaoscrafts_device_mod.Core.PC.block.entity.client;

import net.chaoscraft.chaoscrafts_device_mod.Core.PC.block.entity.LaptopEntity;
import net.chaoscraft.chaoscrafts_device_mod.Core.Util.ResourceLocationHelper;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class LaptopModel extends GeoModel<LaptopEntity> {
    @Override
    public ResourceLocation getModelResource(LaptopEntity laptopEntity) {
        return ResourceLocationHelper.mod("geo/laptop.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(LaptopEntity laptopEntity) {
        // Choose texture based on the block entity's variant state
        String tex = laptopEntity != null && laptopEntity.isWhiteVariant() ? "textures/block/laptop_uv_white.png" : "textures/block/laptop_uv_black.png";
        return ResourceLocationHelper.mod(tex);
    }

    @Override
    public ResourceLocation getAnimationResource(LaptopEntity laptopEntity) {
        return ResourceLocationHelper.mod("animations/laptop.animation.json");
    }
}