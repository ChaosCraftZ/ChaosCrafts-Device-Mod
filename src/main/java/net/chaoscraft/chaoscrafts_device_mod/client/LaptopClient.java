package net.chaoscraft.chaoscrafts_device_mod.client;

import net.chaoscraft.chaoscrafts_device_mod.client.screen.DesktopScreen;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.LoadingWrapperScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LaptopClient {
    public static void openDesktopScreen() {
        try {
            DesktopScreen ds = new DesktopScreen();
            LoadingWrapperScreen loading = new LoadingWrapperScreen(ds);
            Minecraft.getInstance().setScreen(loading);

        } catch (Exception e) {
            System.err.println("[LaptopClient] Failed to open DesktopScreen: " + e);
        }
    }
}