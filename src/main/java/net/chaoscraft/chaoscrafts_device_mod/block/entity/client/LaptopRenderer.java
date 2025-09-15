package net.chaoscraft.chaoscrafts_device_mod.block.entity.client;

import net.chaoscraft.chaoscrafts_device_mod.block.entity.LaptopEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class LaptopRenderer extends GeoBlockRenderer<LaptopEntity> {
    public LaptopRenderer(BlockEntityRendererProvider.Context context) {
        super(new LaptopModel());
    }
}