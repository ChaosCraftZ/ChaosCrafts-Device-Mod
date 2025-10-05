package net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Components;

import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Model.LoadingParticle;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Utils.DesktopConstants;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Utils.DesktopState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Iterator;
import java.util.Random;

/**
 * Renders the loading screen overlay.
 */
public class LoadingScreenRenderer {
    private final DesktopState state;
    private final Random rng = new Random();

    public LoadingScreenRenderer(DesktopState state) {
        this.state = state;
    }

    public boolean renderLoadingOverlay(GuiGraphics guiGraphics, int width, int height) {
        if (!state.showLoadingOverlay) return false;

        long now = System.currentTimeMillis();
        long elapsed = now - state.loadingStartMillis;
        int w = width;
        int h = height;

        // update animation timing
        long nowMs = System.currentTimeMillis();
        state.lastRenderMillis = nowMs;

        // subtle vignetting gradient (top -> bottom)
        int topColor = 0xFF071018;
        int bottomColor = 0xFF0D1624;
        for (int i = 0; i < h; i++) {
            int mix = i * 255 / Math.max(1, h - 1);
            int rcol = ((topColor >> 16) & 0xFF) * (255 - mix) / 255 + ((bottomColor >> 16) & 0xFF) * mix / 255;
            int gcol = ((topColor >> 8) & 0xFF) * (255 - mix) / 255 + ((bottomColor >> 8) & 0xFF) * mix / 255;
            int bcol = (topColor & 0xFF) * (255 - mix) / 255 + (bottomColor & 0xFF) * mix / 255;
            guiGraphics.fill(0, i, w, i + 1, (0xFF << 24) | (rcol << 16) | (gcol << 8) | bcol);
        }

        // slight dark overlay for contrast
        guiGraphics.fill(0, 0, w, h, 0x22000000);

        // Center the loading group vertically to feel more balanced
        String title = "RiftOS";
        float titleScale = 2.0f;
        int centerY = h / 2; // base center for the loading group

        // draw scaled title centered at centerY - 60
        Font font = Minecraft.getInstance().font;
        int tw = font.width(title);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(titleScale, titleScale, 1f);
        int scaledX = (int)((w / titleScale - tw) / 2f);
        int scaledY = (int)((centerY - 60) / titleScale);
        guiGraphics.drawString(font, Component.literal(title), scaledX + 1, scaledY + 1, 0x66000000, false);
        guiGraphics.drawString(font, Component.literal(title), scaledX, scaledY, 0xFFFFFFFF, false);
        guiGraphics.pose().popPose();

        // subtitle under title
        String baseSub = "Loading apps & settings";
        int dots = (int) ((elapsed / 450) % 4);
        String sub = baseSub + ".".repeat(Math.max(0, dots));
        guiGraphics.drawString(font, Component.literal(sub), (w - font.width(sub)) / 2, centerY - 24, 0xFFBFC9D3, false);

        // Progress bar (centered around centerY)
        float targetProg = Math.min(1f, (float) elapsed / (float) DesktopConstants.MIN_LOADING_MS);
        state.currentLoadingProgress = lerp(state.currentLoadingProgress, targetProg, 0.06f);
        int barW = Math.min(600, w - 160);
        int bx = (w - barW) / 2;
        int by = centerY + 4;
        int barH = 12;
        guiGraphics.fill(bx - 3, by - 3, bx + barW + 3, by + barH + 3, 0x44707070);
        guiGraphics.fill(bx, by, bx + barW, by + barH, 0xFF1F2428);
        int filled = bx + Math.round(barW * state.currentLoadingProgress);
        guiGraphics.fill(bx, by, filled, by + barH, 0xFF33AAFF); // Assuming accent color
        guiGraphics.fill(Math.max(bx, filled - 6), by, Math.min(bx + barW, filled + 6), by + barH, 0x33FFFFFF);

        // percentage: draw centered inside the progress bar. Lietuva Peak
        String pct = String.format("%d%%", Math.round(state.currentLoadingProgress * 100f));
        int pctX = bx + (barW - font.width(pct)) / 2;
        int pctY = by + Math.max(0, (barH - 8) / 2);
        guiGraphics.drawString(font, Component.literal(pct), pctX, pctY, 0xFFDDE6EB, false);

        // Particles (more varied and softer), spawn around the centered group
        synchronized (state.loadingParticles) {
            if (state.loadingParticles.size() < 60 && rng.nextInt(4) == 0) {
                float px = w / 2f + rng.nextInt(360) - 180;
                float py = centerY + rng.nextInt(140) - 70;
                float vx = (rng.nextFloat() - 0.5f) * 1.8f;
                float vy = -0.6f - rng.nextFloat() * 0.8f;
                float life = 50 + rng.nextInt(140);
                state.loadingParticles.add(new LoadingParticle(px, py, vx, vy, life));
                if (rng.nextInt(12) == 0) state.loadingParticles.add(new LoadingParticle(px + rng.nextInt(20)-10, py + rng.nextInt(10)-5, vx*0.4f, vy*0.2f, 30 + rng.nextInt(40)));
            }
            Iterator<LoadingParticle> it = state.loadingParticles.iterator();
            while (it.hasNext()) {
                LoadingParticle p = it.next();
                p.x += p.vx; p.y += p.vy; p.vy += 0.01f; p.vx *= 0.995f; p.vy *= 0.998f;
                p.life -= 1;
                float lifeFrac = Math.max(0f, (p.life) / 160f);
                int alpha = Math.max(0, Math.min(255, Math.round(200 * lifeFrac)));
                int r = 0xCC; int g = 0xEE; int b = 0xFF;
                if (rng.nextInt(6) == 0) { r = 0xFF; g = 0xD8; b = 0xA8; }
                int col = (alpha << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
                int size = (int) Math.max(1, Math.round(1 + 2 * lifeFrac));
                guiGraphics.fill(Math.round(p.x), Math.round(p.y), Math.round(p.x) + size, Math.round(p.y) + size, col);
                int trailAlpha = Math.max(0, alpha - 100);
                if (trailAlpha > 0) guiGraphics.fill(Math.round(p.x - p.vx*2), Math.round(p.y - p.vy*2), Math.round(p.x), Math.round(p.y), (trailAlpha << 24) | 0x66CCCCFF);
                if (p.life <= 0) it.remove();
            }
        }

        // Small footer hint. You like feet? Ok ig???
        String hint = "Press ESC to close";
        guiGraphics.drawString(font, Component.literal(hint), w - font.width(hint) - 10, h - 24, 0x66FFFFFF, false);

        // Keep overlay for minimum time
        return true;
    }

    private static float lerp(float a, float b, float f) {
        return a + (b - a) * f;
    }
}