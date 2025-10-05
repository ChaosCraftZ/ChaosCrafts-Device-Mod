package net.chaoscraft.chaoscrafts_device_mod.Core.PC.block.entity.client;

import net.chaoscraft.chaoscrafts_device_mod.Core.PC.block.entity.LaptopEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class LaptopRenderer extends GeoBlockRenderer<LaptopEntity> {
    public LaptopRenderer(BlockEntityRendererProvider.Context context) {
        super(new LaptopModel());
    }
}