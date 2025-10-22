package net.chaoscraft.chaoscrafts_device_mod.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.ConfigHandler;

public class LoadingWrapperScreen extends Screen {
    private final RiftOSLoadingScreen loading;
    private final DesktopScreen desktop;
    private final long startMillis;
    private boolean loginShown = false;
    private static final long LOADING_MS = 5000L;

    public LoadingWrapperScreen(DesktopScreen desktop) {
        super(Component.literal("RiftOS Loading"));
        this.desktop = desktop;
        this.loading = new RiftOSLoadingScreen();
        this.startMillis = System.currentTimeMillis();
    }

    public LoadingWrapperScreen() {
        this(null);
    }

    @Override
    public void tick() {
        super.tick();
        if (!loginShown) {
            long now = System.currentTimeMillis();
            if (now - startMillis >= LOADING_MS) {
                loginShown = true;
                try {
                    Minecraft.getInstance().setScreen(new RiftLoginScreen(desktop));
                } catch (Exception e) {
                    System.err.println("[LoadingWrapperScreen] Failed to open RiftLoginScreen: " + e);
                }
            }
        }
    }

    @Override
    @SuppressWarnings("null")
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        try {
            float uiScale = ConfigHandler.uiScaleFactor();
            int logicalW = Math.round(this.width / uiScale);
            int logicalH = Math.round(this.height / uiScale);
            Font f = this.font;

            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(uiScale, uiScale, 1f);
            loading.render(guiGraphics, logicalW, logicalH, f, partialTick);
            guiGraphics.pose().popPose();
        } catch (Exception ignored) {}
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
