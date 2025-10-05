package net.chaoscraft.chaoscrafts_device_mod.Core.PC.item.client;

import net.chaoscraft.chaoscrafts_device_mod.Core.PC.item.custom.LaptopItem;
import net.chaoscraft.chaoscrafts_device_mod.Core.Util.ResourceLocationHelper;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class LaptopItemModel extends GeoModel<LaptopItem> {
    @Override
    public ResourceLocation getModelResource(LaptopItem LaptopItem) {
        return ResourceLocationHelper.mod("geo/laptop.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(LaptopItem item) {
        String tex = (item != null && item.isWhiteItem()) ? "textures/block/laptop_uv_white.png" : "textures/block/laptop_uv_black.png";
        return ResourceLocationHelper.mod(tex);
    }

    @Override
    public ResourceLocation getAnimationResource(LaptopItem LaptopItem) {
        return ResourceLocationHelper.mod("animations/laptop.animation.json");
    }
}