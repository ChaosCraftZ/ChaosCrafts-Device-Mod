package net.chaoscraft.chaoscrafts_device_mod.client;

import net.chaoscraft.chaoscrafts_device_mod.client.screen.DesktopScreen;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.RiftLoginScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LaptopClient {
    private static final long LOGIN_DELAY_MS = 5000;

    public static void openDesktopScreen() {
        try {
            DesktopScreen ds = new DesktopScreen();
            Minecraft.getInstance().setScreen(ds);

            new Thread(() -> {
                try {
                    Thread.sleep(LOGIN_DELAY_MS);
                    Minecraft.getInstance().execute(() -> {
                        try {
                            Minecraft.getInstance().setScreen(new RiftLoginScreen(ds));
                        } catch (Exception e) {
                            System.err.println("[LaptopClient] Failed to open RiftLoginScreen: " + e);
                        }
                    });
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.err.println("[LaptopClient] Login scheduler interrupted: " + ie);
                }
            }, "pc-ui-login-scheduler").start();

        } catch (Exception e) {
            System.err.println("[LaptopClient] Failed to open DesktopScreen: " + e);
        }
    }
}