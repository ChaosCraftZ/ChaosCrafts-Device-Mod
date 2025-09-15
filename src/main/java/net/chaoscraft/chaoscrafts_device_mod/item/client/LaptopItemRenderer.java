package net.chaoscraft.chaoscrafts_device_mod.item.client;

import net.chaoscraft.chaoscrafts_device_mod.item.custom.LaptopItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class LaptopItemRenderer extends GeoItemRenderer<LaptopItem> {
    public LaptopItemRenderer() {
        super(new LaptopItemModel());
    }
}