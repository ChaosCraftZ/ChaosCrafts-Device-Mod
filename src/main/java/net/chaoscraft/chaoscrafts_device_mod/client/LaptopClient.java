package net.chaoscraft.chaoscrafts_device_mod.client;

import net.chaoscraft.chaoscrafts_device_mod.client.screen.DesktopScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LaptopClient {
    public static void openDesktopScreen() {
        try {
            Minecraft.getInstance().setScreen(new DesktopScreen());
        } catch (Exception e) {
            // swallow to avoid crashes during dev
        }
    }

    public static void openDesktopScreenAt(BlockPos pos) {
        try {
            Minecraft.getInstance().setScreen(new DesktopScreen(pos));
        } catch (Exception e) {
            // swallow
        }
    }
}

