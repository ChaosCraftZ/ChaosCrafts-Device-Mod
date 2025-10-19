package net.chaoscraft.chaoscrafts_device_mod.client.screen;

import net.chaoscraft.chaoscrafts_device_mod.ConfigHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class DebugOverlay {
    private DebugOverlay() {}

    public static void drawHitbox(GuiGraphics g, int x0, int y0, int x1, int y1, String label) {
        if (!ConfigHandler.debugButtonsEnabled()) return;
        int fill = 0x44FF4444;
        int outline = 0x88FF4444;
        g.fill(x0, y0, x1, y1, fill);
        g.fill(x0, y0, x1, Math.min(y0 + 1, y1), outline);
        g.fill(x0, Math.max(y1 - 1, y0), x1, y1, outline);
        g.fill(x0, y0, Math.min(x0 + 1, x1), y1, outline);
        g.fill(Math.max(x1 - 1, x0), y0, x1, y1, outline);

        if (label != null && !label.isEmpty()) {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.font != null) {
                    int lx = x0 + 2;
                    int ly = y0 + 2;
                    g.drawString(mc.font, Component.literal(label), lx, ly, 0xFFFFFF, false);
                }
            } catch (Exception ignored) {}
        }
    }
}