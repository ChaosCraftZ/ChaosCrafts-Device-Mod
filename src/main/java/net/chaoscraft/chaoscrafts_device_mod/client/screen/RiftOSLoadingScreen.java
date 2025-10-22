package net.chaoscraft.chaoscrafts_device_mod.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class RiftOSLoadingScreen {
    private boolean showLoadingOverlay = true;
    private final long loadingStartMillis;
    private final List<LoadingParticle> loadingParticles = new ArrayList<>();
    private float currentLoadingProgress = 0f;
    private long lastRenderMillis;
    private static final long MIN_LOADING_MS = 5000;
    private final Random rng = new Random();

    private static class LoadingParticle {
        float x, y, vx, vy, life;
        LoadingParticle(float x, float y, float vx, float vy, float life) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.life = life;
        }
    }

    public RiftOSLoadingScreen() {
        this.loadingStartMillis = System.currentTimeMillis();
        this.lastRenderMillis = System.currentTimeMillis();
    }

    public void setShowLoadingOverlay(boolean show) {
        this.showLoadingOverlay = show;
    }

    public boolean isShowing() {
        return showLoadingOverlay;
    }

    public void render(GuiGraphics guiGraphics, int width, int height, Font font, float partialTick) {
        if (!showLoadingOverlay) return;

        long now = System.currentTimeMillis();
        long elapsed = now - loadingStartMillis;

        lastRenderMillis = now;

        int topColor = 0xFF071018;
        int bottomColor = 0xFF0D1624;
        for (int i = 0; i < height; i++) {
            int mix = i * 255 / Math.max(1, height - 1);
            int rcol = ((topColor >> 16) & 0xFF) * (255 - mix) / 255 + ((bottomColor >> 16) & 0xFF) * mix / 255;
            int gcol = ((topColor >> 8) & 0xFF) * (255 - mix) / 255 + ((bottomColor >> 8) & 0xFF) * mix / 255;
            int bcol = (topColor & 0xFF) * (255 - mix) / 255 + (bottomColor & 0xFF) * mix / 255;
            guiGraphics.fill(0, i, width, i + 1, (0xFF << 24) | (rcol << 16) | (gcol << 8) | bcol);
        }

        guiGraphics.fill(0, 0, width, height, 0x22000000);

        String title = "RiftOS";
        float titleScale = 2.0f;
        int centerY = height / 2;

        int tw = font.width(title);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(titleScale, titleScale, 1f);
        int scaledX = (int)((width / titleScale - tw) / 2f);
        int scaledY = (int)((centerY - 60) / titleScale);
        drawShadowedString(guiGraphics, font, title, scaledX, scaledY);
        guiGraphics.pose().popPose();

        String baseSub = "Loading apps & settings";
        int dots = (int) ((elapsed / 450) % 4);
        String sub = baseSub + ".".repeat(Math.max(0, dots));
        int subX = (width - font.width(sub)) / 2;
        int subY = centerY - 24;
        drawShadowedString(guiGraphics, font, sub, subX, subY);

        float targetProg = Math.min(1f, (float) elapsed / (float) MIN_LOADING_MS);
        currentLoadingProgress = lerp(currentLoadingProgress, targetProg, 0.06f);
        int barW = Math.min(600, width - 160);
        int bx = (width - barW) / 2;
        int by = centerY + 4;
        int barH = 12;

        guiGraphics.fill(bx - 3, by - 3, bx + barW + 3, by + barH + 3, 0x44707070);
        guiGraphics.fill(bx, by, bx + barW, by + barH, 0xFF1F2428);

        int filled = bx + Math.round(barW * currentLoadingProgress);
        int accentColor = 0xFF33AAFF;
        guiGraphics.fill(bx, by, filled, by + barH, accentColor);
        guiGraphics.fill(Math.max(bx, filled - 6), by, Math.min(bx + barW, filled + 6), by + barH, (accentColor & 0x00FFFFFF) | 0x33FFFFFF);

        String pct = String.format("%d%%", Math.round(currentLoadingProgress * 100f));
        int pctX = bx + (barW - font.width(pct)) / 2;
        int pctY = by + Math.max(0, (barH - 8) / 2);
        drawShadowedString(guiGraphics, font, pct, pctX, pctY);

        updateAndRenderParticles(guiGraphics, width, centerY);

        String hint = "Press ESC to close";
        int hintX = width - font.width(hint) - 10;
        int hintY = height - 24;
        drawShadowedString(guiGraphics, font, hint, hintX, hintY);
    }

    private void updateAndRenderParticles(GuiGraphics guiGraphics, int width, int centerY) {
        synchronized (loadingParticles) {
            if (loadingParticles.size() < 60 && rng.nextInt(4) == 0) {
                float px = width / 2f + rng.nextInt(360) - 180;
                float py = centerY + rng.nextInt(140) - 70;
                float vx = (rng.nextFloat() - 0.5f) * 1.8f;
                float vy = -0.6f - rng.nextFloat() * 0.8f;
                float life = 50 + rng.nextInt(140);
                loadingParticles.add(new LoadingParticle(px, py, vx, vy, life));
                if (rng.nextInt(12) == 0) {
                    loadingParticles.add(new LoadingParticle(
                            px + rng.nextInt(20) - 10,
                            py + rng.nextInt(10) - 5,
                            vx * 0.4f,
                            vy * 0.2f,
                            30 + rng.nextInt(40)
                    ));
                }
            }

            Iterator<LoadingParticle> it = loadingParticles.iterator();
            while (it.hasNext()) {
                LoadingParticle p = it.next();
                p.x += p.vx;
                p.y += p.vy;
                p.vy += 0.01f;
                p.vx *= 0.995f;
                p.vy *= 0.998f;
                p.life -= 1;

                float lifeFrac = Math.max(0f, (p.life) / 160f);
                int alpha = Math.max(0, Math.min(255, Math.round(200 * lifeFrac)));
                int r = 0xCC; int g = 0xEE; int b = 0xFF;
                if (rng.nextInt(6) == 0) {
                    r = 0xFF; g = 0xD8; b = 0xA8;
                }
                int col = (alpha << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
                int size = Math.max(1, Math.round(1 + 2 * lifeFrac));

                guiGraphics.fill(Math.round(p.x), Math.round(p.y), Math.round(p.x) + size, Math.round(p.y) + size, col);

                int trailAlpha = Math.max(0, alpha - 100);
                if (trailAlpha > 0) {
                    guiGraphics.fill(
                            Math.round(p.x - p.vx * 2),
                            Math.round(p.y - p.vy * 2),
                            Math.round(p.x),
                            Math.round(p.y),
                            (trailAlpha << 24) | 0x66CCCCFF
                    );
                }

                if (p.life <= 0) it.remove();
            }
        }
    }

    private static float lerp(float a, float b, float f) {
        return a + (b - a) * f;
    }

    private static void drawShadowedString(GuiGraphics g, Font font, String text, int x, int y) {
        int shadow = 0x66000000;
        g.drawString(font, Component.literal(text), x + 1, y + 1, shadow, false);
        g.drawString(font, Component.literal(text), x - 1, y + 1, shadow, false);
        g.drawString(font, Component.literal(text), x + 1, y - 1, shadow, false);
        g.drawString(font, Component.literal(text), x - 1, y - 1, shadow, false);
        g.drawString(font, Component.literal(text), x, y, 0xFFFFFFFF, false);
    }
}