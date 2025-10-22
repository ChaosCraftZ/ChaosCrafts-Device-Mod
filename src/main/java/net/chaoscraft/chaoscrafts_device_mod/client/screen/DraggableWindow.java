package net.chaoscraft.chaoscrafts_device_mod.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.chaoscraft.chaoscrafts_device_mod.client.app.IAsyncApp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.client.app.IApp;
import net.minecraft.resources.ResourceLocation;

import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;
import net.chaoscraft.chaoscrafts_device_mod.ConfigHandler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DraggableWindow {
    private static final ResourceLocation BTN_CLOSE = ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "textures/gui/buttons/close_button.png");
    private static final ResourceLocation BTN_FULL = ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "textures/gui/buttons/fullscreen_button.png");
    private static final ResourceLocation BTN_MIN = ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "textures/gui/buttons/minimize_button.png");

    public int x, y, width, height;
    public boolean dragging = false;
    public boolean minimized = false;
    public boolean maximized = false;
    public boolean exclusiveFullscreen = false;
    public boolean preview = false;
    public boolean closing = false;
    public boolean removeRequested = false;

    public String appName;
    public IApp app;

    float displayX;
    float displayY;
    private float opacity = 1f, targetOpacity = 1f;
    private int dragOffsetX, dragOffsetY;

    private int savedX = 0, savedY = 0, savedWidth = 0, savedHeight = 0;
    private boolean savedMaximized = false;
    private boolean savedDragging = false;
    private boolean savedStateExists = false;

    private boolean prevExclusiveFullscreen = false;

    public int previewX = 0, previewY = 0, previewW = 0, previewH = 0;

    public float previewDisplayX = 0f, previewDisplayY = 0f, previewDisplayW = 0f, previewDisplayH = 0f;

    public float previewOpacity = 0f;
    public float previewTargetOpacity = 0f;

    public static boolean darkTheme = true;
    public static int accentColorARGB = 0xFF4C7BD1;

    public static int textPrimaryColor() {
        return darkTheme ? 0xFFDDDDDD : 0xFF111111;
    }

    public static int textSecondaryColor() {
        return darkTheme ? 0xFFBBBBBB : 0xFF333333;
    }

    public static int selectionOverlayColor() {
        return 0x664C7BD1;
    }

    public static int contrastingColorFor(int backgroundColor) {
        int r = (backgroundColor >> 16) & 0xFF;
        int g = (backgroundColor >> 8) & 0xFF;
        int b = backgroundColor & 0xFF;
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
        return luminance > 0.5 ? 0xFF000000 : 0xFFFFFFFF;
    }

    private static final List<DraggableWindow> ALL_WINDOWS = new CopyOnWriteArrayList<>();

    public DraggableWindow(String appName, IApp app, int width, int height, int startX, int startY) {
        this.appName = appName;
        this.app = app;
        this.width = Math.max(100, width);
        this.height = Math.max(80, height);
        this.x = startX;
        this.y = startY;
        this.displayX = startX;
        this.displayY = startY;
        this.targetOpacity = 1f;
        ALL_WINDOWS.add(this);
        if (this.app != null) this.app.onOpen(this);
    }

    private final AsyncTaskManager asyncManager = AsyncTaskManager.getInstance();

    private static float lerp(float a, float b, float f) { return a + (b - a) * f; }

    public static int lightenRgb(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int nr = Math.min(255, (int) (r + (255 - r) * amount));
        int ng = Math.min(255, (int) (g + (255 - g) * amount));
        int nb = Math.min(255, (int) (b + (255 - b) * amount));
        return (nr << 16) | (ng << 8) | nb;
    }

    public static int darkenRgb(int rgb, float amount) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int nr = Math.max(0, (int) (r * (1f - amount)));
        int ng = Math.max(0, (int) (g * (1f - amount)));
        int nb = Math.max(0, (int) (b * (1f - amount)));
        return (nr << 16) | (ng << 8) | nb;
    }

    private static String formatTitleName(String name) {
        if (name == null || name.isEmpty()) return "";
        String base = name;
        if (base.toLowerCase(java.util.Locale.ROOT).endsWith(".txt")) base = base.substring(0, base.length() - 4);
        StringBuilder sb = new StringBuilder();
        boolean capNext = true;
        for (char c : base.toCharArray()) {
            if (Character.isWhitespace(c) || c == '_' || c == '-') { sb.append(c); capNext = true; continue; }
            if (capNext) { sb.append(Character.toUpperCase(c)); capNext = false; } else sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    public void render(GuiGraphics guiGraphics, PoseStack poseStack, int mouseX, int mouseY, boolean focused, int taskbarHeight, float partialTick) {
        if (!dragging) {
            displayX = lerp(displayX, x, 0.18f);
            displayY = lerp(displayY, y, 0.18f);
        } else {
            displayX = x;
            displayY = y;
        }
        opacity = lerp(opacity, targetOpacity, 0.18f);

        if (exclusiveFullscreen && !prevExclusiveFullscreen) {
            savedX = this.x; savedY = this.y; savedWidth = this.width; savedHeight = this.height;
            savedMaximized = this.maximized;
            savedDragging = this.dragging;
            savedStateExists = true;

            this.x = 0; this.y = 0;
            this.displayX = 0f; this.displayY = 0f;
            this.dragging = false;
        } else if (!exclusiveFullscreen && prevExclusiveFullscreen) {
            if (savedStateExists) {
                this.x = savedX; this.y = savedY; this.width = savedWidth; this.height = savedHeight;
                this.maximized = savedMaximized;
                this.dragging = savedDragging;
                this.displayX = this.x; this.displayY = this.y;
                savedStateExists = false;
            }
        }
        prevExclusiveFullscreen = exclusiveFullscreen;

        if (closing && opacity < 0.03f) { removeRequested = true; return; }

        if (minimized && !closing) return;

        int alpha = Math.round(255 * opacity) & 0xFF;
        int bgBase = darkTheme ? 0x222222 : 0xFFFFFFFF;
        int bgColor = ((alpha << 24) | (bgBase & 0x00FFFFFF));

        int titleColor = ((alpha << 24) | (accentColorARGB & 0x00FFFFFF));

        float uiScale = ConfigHandler.uiScaleFactor();
        int fullW = Math.round(Minecraft.getInstance().getWindow().getGuiScaledWidth() / uiScale);
        int fullH = Math.round(Minecraft.getInstance().getWindow().getGuiScaledHeight() / uiScale);

        int renderX, renderY, renderW, renderH;
        if (exclusiveFullscreen) {
            renderX = 0; renderY = 0; renderW = fullW; renderH = fullH;
        } else if (maximized) {
            renderX = 0; renderY = 0; renderW = fullW; renderH = Math.max(0, fullH - taskbarHeight);
        } else {
            renderX = Math.round(displayX);
            renderY = Math.round(displayY);
            renderW = width;
            renderH = height;
        }

        int shadowAlpha = Math.round(alpha * 0.25f);
        guiGraphics.fill(renderX - 6, renderY - 6, renderX + renderW + 6, renderY + renderH + 6, (shadowAlpha << 24) | 0x000000);

        if (focused) {
            int glowColor = ((Math.round(alpha * 0.15f) << 24) | (accentColorARGB & 0x00FFFFFF));
            guiGraphics.fill(renderX - 2, renderY - 2, renderX + renderW + 2, renderY + renderH + 2, glowColor);
        }

        guiGraphics.fill(renderX, renderY, renderX + renderW, renderY + renderH, bgColor);

        int titleH = 26;
        int topH = Math.max(1, Math.round(titleH * 0.12f));
        int bottomH = Math.max(1, Math.round(titleH * 0.12f));
        int middleH = Math.max(0, titleH - topH - bottomH);

        int accentRgb = accentColorARGB & 0x00FFFFFF;
        int lighterRgb = lightenRgb(accentRgb, 0.12f);
        int darkerRgb = darkenRgb(accentRgb, 0.12f);

        int titleAlpha = (titleColor >>> 24) & 0xFF;
        int lighterColor = (titleAlpha << 24) | (lighterRgb & 0x00FFFFFF);
        int normalColor = (titleAlpha << 24) | (accentRgb & 0x00FFFFFF);
        int darkerColor = (titleAlpha << 24) | (darkerRgb & 0x00FFFFFF);

        guiGraphics.fill(renderX, renderY, renderX + renderW, renderY + topH, lighterColor);
        guiGraphics.fill(renderX, renderY + topH, renderX + renderW, renderY + topH + middleH, normalColor);
        guiGraphics.fill(renderX, renderY + topH + middleH, renderX + renderW, renderY + titleH, darkerColor);

        int textColor = contrastingColorFor(accentColorARGB);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(formatTitleName(appName)), renderX + 8, renderY + 6, textColor, false);

        int btnY = renderY + 6;

        int btnW = 12; int btnH = 14;
        int closeX = renderX + renderW - 20; int closeY = btnY;
        int minX = renderX + renderW - 44; int minY = btnY;
        int fullX = renderX + renderW - 68; int fullY = btnY;

        int iconSize = Math.min(16, Math.max(8, btnH - 2));

        try {
            int ix = closeX + (btnW - iconSize) / 2;
            int iy = closeY + (btnH - iconSize) / 2;
            guiGraphics.blit(BTN_CLOSE, ix, iy, iconSize, iconSize, 0, 0, 16, 16, 16, 16);
        } catch (Exception e) {
            guiGraphics.fill(closeX, closeY, closeX + btnW, closeY + btnH, 0xFFFF6666 | ((alpha << 24) & 0xFF000000));
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("X"), closeX + 1, closeY, contrastingColorFor(0xFFFF6666), false);
        }

        try {
            int ix = minX + (btnW - iconSize) / 2;
            int iy = minY + (btnH - iconSize) / 2;
            guiGraphics.blit(BTN_MIN, ix, iy, iconSize, iconSize, 0, 0, 16, 16, 16, 16);
        } catch (Exception e) {
            guiGraphics.fill(minX, minY, minX + btnW, minY + btnH, 0xFFFFFF66 | ((alpha << 24) & 0xFF000000));
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("-"), minX + 1, minY, contrastingColorFor(0xFFFFFF66), false);
        }

        try {
            int ix = fullX + (btnW - iconSize) / 2;
            int iy = fullY + (btnH - iconSize) / 2;
            guiGraphics.blit(BTN_FULL, ix, iy, iconSize, iconSize, 0, 0, 16, 16, 16, 16);
        } catch (Exception e) {
            int accent = accentColorARGB & 0x00FFFFFF;
            int accentWithAlpha = ((alpha << 24) | (accent & 0x00FFFFFF));
            guiGraphics.fill(fullX, fullY, fullX + btnW, fullY + btnH, accentWithAlpha);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("▢"), fullX + 3, fullY, contrastingColorFor(accentWithAlpha), false);
        }

         DebugOverlay.drawHitbox(guiGraphics, renderX + renderW - 20, btnY, renderX + renderW - 8, btnY + 14, "btn:close");
         DebugOverlay.drawHitbox(guiGraphics, renderX + renderW - 44, btnY, renderX + renderW - 32, btnY + 14, "btn:minimize");
         DebugOverlay.drawHitbox(guiGraphics, renderX + renderW - 68, btnY, renderX + renderW - 56, btnY + 14, "btn:maximize");

        if (app != null && !minimized) {
            try {
                app.tick();
            } catch (Exception ignored) {}

            guiGraphics.pose().pushPose();

            int scissorX = renderX;
            int scissorY = renderY + 26;
            int scissorWidth = renderW;
            int scissorHeight = renderH - 26;

            float animatedScissorX = displayX;
            float animatedScissorY = displayY + 26;
            float animatedScissorWidth = renderW;
            float animatedScissorHeight = renderH - 26;

            int px0 = Math.round(animatedScissorX * uiScale);
            int py0 = Math.round(animatedScissorY * uiScale);
            int px1 = Math.round((animatedScissorX + animatedScissorWidth) * uiScale);
            int py1 = Math.round((animatedScissorY + animatedScissorHeight) * uiScale);

            guiGraphics.enableScissor(px0, py0, px1, py1);

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(displayX - renderX, displayY - renderY, 0);

            int animatedMouseX = mouseX - Math.round(displayX);
            int animatedMouseY = mouseY - Math.round(displayY);

            app.renderContent(guiGraphics, poseStack, this, animatedMouseX, animatedMouseY, partialTick);

            try { app.debugRender(guiGraphics, poseStack, this, animatedMouseX, animatedMouseY, partialTick); } catch (Exception ignored) {}

            guiGraphics.pose().popPose();
            guiGraphics.disableScissor();
            guiGraphics.pose().popPose();
        }
    }

    public void drawPreview(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!preview || previewW <= 0 || previewH <= 0 || previewOpacity <= 0.02f) return;
        int px = Math.round(previewDisplayX);
        int py = Math.round(previewDisplayY);
        int pw = Math.round(previewDisplayW);
        int ph = Math.round(previewDisplayH);
         int alpha = Math.round(255 * previewOpacity) & 0xFF;
         int shadowAlpha = Math.round(alpha * 0.25f);
         guiGraphics.fill(px - 4, py - 4, px + pw + 4, py + ph + 4, (shadowAlpha << 24) | 0x000000);
         int bgBase = darkTheme ? 0x222222 : 0xFFFFFFFF;
         int bgColor = ((alpha << 24) | (bgBase & 0x00FFFFFF));
         guiGraphics.fill(px, py, px + pw, py + ph, bgColor);

        int titleH = Math.max(18, (int)(pw * 0.12f));
        int topH = Math.max(1, Math.round(titleH * 0.12f));
        int bottomH = Math.max(1, Math.round(titleH * 0.12f));
        int middleH = Math.max(0, titleH - topH - bottomH);

        int accentRgb = accentColorARGB & 0x00FFFFFF;
        int lighterRgb = lightenRgb(accentRgb, 0.12f);
        int darkerRgb = darkenRgb(accentRgb, 0.12f);

        int titleAlpha = alpha;
        int lighterColor = (titleAlpha << 24) | (lighterRgb & 0x00FFFFFF);
        int normalColor = (titleAlpha << 24) | (accentRgb & 0x00FFFFFF);
        int darkerColor = (titleAlpha << 24) | (darkerRgb & 0x00FFFFFF);

        guiGraphics.fill(px, py, px + pw, py + topH, lighterColor);
        guiGraphics.fill(px, py + topH, px + pw, py + topH + middleH, normalColor);
        guiGraphics.fill(px, py + topH + middleH, px + pw, py + titleH, darkerColor);

        int textColor = contrastingColorFor(accentColorARGB);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(formatTitleName(appName)), px + 6, py + 4, textColor, false);

        int innerTop = py + titleH + 4;
        int innerLeft = px + 4;
        int innerRight = px + pw - 4;
        int innerBottom = py + ph - 4;
        if (innerRight <= innerLeft || innerBottom <= innerTop) return;

        int innerW = innerRight - innerLeft;
        int innerH = innerBottom - innerTop;

        if (app == null) {
            int innerBg = ((alpha << 24) | (darkTheme ? 0xFF1B1B1B : 0xFFEFEFEF));
            guiGraphics.fill(innerLeft, innerTop, innerRight, innerBottom, innerBg);
            int hintColor = contrastingColorFor(innerBg);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Click to open"), innerLeft + 6, innerTop + 6, hintColor, false);
            return;
        }

        int contentW = Math.max(1, this.width);
        int contentH = Math.max(1, this.height - 26);

        float scaleX = (float) innerW / (float) contentW;
        float scaleY = (float) innerH / (float) contentH;
        float scale = Math.min(scaleX, scaleY);
        if (scale <= 0f) scale = 0.0001f;

        int scaledW = Math.round(contentW * scale);
        int scaledH = Math.round(contentH * scale);
        float offsetX = (innerW - scaledW) * 0.5f;
        float offsetY = (innerH - scaledH) * 0.5f;

        float uiScale = ConfigHandler.uiScaleFactor();
        int scX0 = Math.round((innerLeft + offsetX) * uiScale);
        int scY0 = Math.round((innerTop + offsetY) * uiScale);
        int scX1 = Math.round((innerLeft + offsetX + scaledW) * uiScale);
        int scY1 = Math.round((innerTop + offsetY + scaledH) * uiScale);

        int innerBg = ((alpha << 24) | (darkTheme ? 0xFF1B1B1B : 0xFFEFEFEF));
        guiGraphics.fill(innerLeft, innerTop, innerRight, innerBottom, innerBg);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(innerLeft + offsetX, innerTop + offsetY, 0f);
        guiGraphics.pose().scale(scale, scale, 1f);

        try {
            guiGraphics.enableScissor(scX0, scY0, scX1, scY1);
            try {
                int localMouseX = -10000;
                int localMouseY = -10000;

                app.renderContent(guiGraphics, guiGraphics.pose(), this, localMouseX, localMouseY, partialTick);

                try { app.debugRender(guiGraphics, guiGraphics.pose(), this, localMouseX, localMouseY, partialTick); } catch (Exception ignored) {}
            } finally {
                guiGraphics.disableScissor();
            }
        } catch (Exception e) {
            int hintColor = contrastingColorFor(innerBg);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Click to open"), innerLeft + 6, innerTop + 6, hintColor, false);
        }

        guiGraphics.pose().popPose();
    }

    public int[] getRenderRect(int taskbarHeight) {
        float uiScale = ConfigHandler.uiScaleFactor();
        int fullW = Math.round(Minecraft.getInstance().getWindow().getGuiScaledWidth() / uiScale);
        int fullH = Math.round(Minecraft.getInstance().getWindow().getGuiScaledHeight() / uiScale);
        if (exclusiveFullscreen) return new int[]{0, 0, fullW, fullH};
        if (maximized) return new int[]{0, 0, fullW, Math.max(0, fullH - taskbarHeight)};
        return new int[]{Math.round(displayX), Math.round(displayY), width, height};
    }

    @SuppressWarnings("unused")
    public boolean handleTitlebarClick(double mouseX, double mouseY, int button, int taskbarHeight) {
        int[] r = getRenderRect(taskbarHeight);
        int renderX = r[0], renderY = r[1], renderW = r[2];
        int mx = Math.round((float) mouseX);
        int my = Math.round((float) mouseY);

        if (mx >= renderX + renderW - 20 && mx < renderX + renderW - 8 && my >= renderY + 6 && my < renderY + 20) {
            requestClose();
            return true;
        }

        if (mx >= renderX + renderW - 44 && mx < renderX + renderW - 32 && my >= renderY + 6 && my < renderY + 20) {
            minimized = true;
            targetOpacity = 0f;
            return true;
        }

        if (mx >= renderX + renderW - 68 && mx < renderX + renderW - 56 && my >= renderY + 6 && my < renderY + 20) {
            boolean wantMaximized = !this.maximized;
            setMaximized(wantMaximized, taskbarHeight);
            if (wantMaximized) { exclusiveFullscreen = false; x = 0; y = 0; }
            dragging = false;
            return true;
        }

        if (!maximized && !exclusiveFullscreen && mx >= renderX && mx < renderX + renderW && my >= renderY && my < renderY + 26) {
            dragging = true;
            dragOffsetX = (int) (mouseX - x);
            dragOffsetY = (int) (mouseY - y);
            return true;
        }
        return false;
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        if (app != null) app.mouseReleased(this, mouseX, mouseY, button);
    }

    public void mouseDragged(double mouseX, double mouseY) {
        if (dragging && !maximized && !exclusiveFullscreen && !closing) {
            x = (int) Math.round(mouseX - dragOffsetX);
            y = (int) Math.round(mouseY - dragOffsetY);
        } else if (app != null) {
            app.mouseDragged(this, mouseX, mouseY, 0, 0);
        }
    }

    public void finalizeAnimationState() {
        if (closing && opacity < 0.03f) {
            removeRequested = true;
            ALL_WINDOWS.remove(this);
        } else if (targetOpacity == 0f && !closing && opacity < 0.03f) {
            minimized = true;
            targetOpacity = 1f;
            opacity = 0f;
        }
    }

    public void restore() {
        this.minimized = false;
        this.targetOpacity = 1f;
        this.opacity = 0f;
    }

    public boolean isInside(double mouseX, double mouseY, int taskbarHeight) {
        int[] r = getRenderRect(taskbarHeight);
        int rx = r[0], ry = r[1], rw = r[2], rh = r[3];
        int mx = Math.round((float) mouseX);
        int my = Math.round((float) mouseY);
        return mx >= rx && mx < rx + rw && my >= ry && my < ry + rh;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (app != null && !minimized) {
            int[] r = getRenderRect(0);
            int renderX = r[0], renderY = r[1];

            if (app instanceof IAsyncApp) {
                asyncManager.submitCPUTask(() -> {
                    ((IAsyncApp) app).mouseScrolledAsync(mouseX, mouseY, delta);
                });
                return true;
            } else {
                return app.mouseScrolled(mouseX, mouseY, delta);
            }
        }
        return false;
    }

    public void requestClose() {
        this.closing = true;
        this.targetOpacity = 0f;
    }

    public void setPreviewActive(boolean active) {
        if (active) {
            this.preview = true;
            this.previewTargetOpacity = 1f;
        } else {
            this.previewTargetOpacity = 0f;
        }
    }

    public void updatePreviewAnimation(float partialTick) {
        previewOpacity = previewOpacity + (previewTargetOpacity - previewOpacity) * 0.25f;

        previewDisplayX = previewDisplayX + (previewX - previewDisplayX) * 0.25f;
        previewDisplayY = previewDisplayY + (previewY - previewDisplayY) * 0.25f;
        previewDisplayW = previewDisplayW + (previewW - previewDisplayW) * 0.25f;
        previewDisplayH = previewDisplayH + (previewH - previewDisplayH) * 0.25f;

        if (previewTargetOpacity <= 0f && previewOpacity < 0.02f) {
            preview = false;
            previewW = previewH = 0;
            previewDisplayW = previewDisplayH = 0f;
        }
    }

    public static void closeAllWindows() {
        for (DraggableWindow w : ALL_WINDOWS) {
            if (w != null) w.requestClose();
        }
    }


    public float getDisplayX() {
        return displayX;
    }

    public float getDisplayY() {
        return displayY;
    }

    public int[] getAnimatedRenderRect(int taskbarHeight) {
        float uiScale = ConfigHandler.uiScaleFactor();
        int fullW = Math.round(Minecraft.getInstance().getWindow().getGuiScaledWidth() / uiScale);
        int fullH = Math.round(Minecraft.getInstance().getWindow().getGuiScaledHeight() / uiScale);
        if (exclusiveFullscreen) return new int[]{0, 0, fullW, fullH};
        if (maximized) return new int[]{0, 0, fullW, Math.max(0, fullH - taskbarHeight)};
        return new int[]{Math.round(displayX), Math.round(displayY), width, height};
    }

    public void setExclusiveFullscreen(boolean enabled) {
        if (this.exclusiveFullscreen == enabled) return;
        if (enabled) {
            savedX = this.x; savedY = this.y; savedWidth = this.width; savedHeight = this.height;
            savedMaximized = this.maximized;
            savedDragging = this.dragging;
            savedStateExists = true;

            this.exclusiveFullscreen = true;
            this.x = 0; this.y = 0;
            this.displayX = 0f; this.displayY = 0f;
            this.dragging = false;
        } else {
            this.exclusiveFullscreen = false;
            if (savedStateExists) {
                this.x = savedX; this.y = savedY; this.width = savedWidth; this.height = savedHeight;
                this.maximized = savedMaximized;
                this.dragging = savedDragging;
                this.displayX = this.x; this.displayY = this.y;
                savedStateExists = false;
            }
        }
        prevExclusiveFullscreen = this.exclusiveFullscreen;
    }

    public void setMaximized(boolean enabled, int taskbarHeight) {
        if (this.maximized == enabled) return;
        if (enabled) {
            savedX = this.x; savedY = this.y; savedWidth = this.width; savedHeight = this.height;
            savedMaximized = this.maximized;
            savedDragging = this.dragging;
            savedStateExists = true;

            float uiScale = ConfigHandler.uiScaleFactor();
            int fullW = Math.round(Minecraft.getInstance().getWindow().getGuiScaledWidth() / uiScale);
            int fullH = Math.round(Minecraft.getInstance().getWindow().getGuiScaledHeight() / uiScale);

            this.maximized = true;
            this.x = 0; this.y = 0;
            this.width = fullW;
            this.height = Math.max(0, fullH - taskbarHeight);
            this.displayX = 0f; this.displayY = 0f;
            this.dragging = false;
        } else {
            this.maximized = false;
            if (savedStateExists) {
                this.x = savedX; this.y = savedY; this.width = savedWidth; this.height = savedHeight;
                this.maximized = savedMaximized;
                this.dragging = savedDragging;
                this.displayX = this.x; this.displayY = this.y;
                savedStateExists = false;
            }
        }
    }

    public boolean isExclusiveFullscreen() {
        return this.exclusiveFullscreen;
    }
}
