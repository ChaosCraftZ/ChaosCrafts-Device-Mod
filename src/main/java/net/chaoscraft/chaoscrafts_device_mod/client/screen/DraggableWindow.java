package net.chaoscraft.chaoscrafts_device_mod.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.chaoscraft.chaoscrafts_device_mod.client.app.IAsyncApp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.client.app.IApp;

import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DraggableWindow {
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

    private float displayX, displayY;
    private float opacity = 1f, targetOpacity = 1f;
    private int dragOffsetX, dragOffsetY;

    public static boolean darkTheme = true;
    public static int accentColorARGB = 0xFF4C7BD1;

    public static int textPrimaryColor() {
        return darkTheme ? 0xFFDDDDDD : 0xFF111111;
    }

    public static int textSecondaryColor() {
        return darkTheme ? 0xFFBBBBBB : 0xFF333333;
    }

    public static int selectionOverlayColor() {
        return darkTheme ? 0x66FFFFFF : 0x66000000;
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

    public void render(GuiGraphics guiGraphics, PoseStack poseStack, int mouseX, int mouseY, boolean focused, int taskbarHeight, float partialTick) {
        if (!dragging) {
            displayX = lerp(displayX, x, 0.18f);
            displayY = lerp(displayY, y, 0.18f);
        } else {
            displayX = x;
            displayY = y;
        }
        opacity = lerp(opacity, targetOpacity, 0.18f);

        if (closing && opacity < 0.03f) { removeRequested = true; return; }
        if (minimized && !preview && !closing) return;

        int alpha = Math.round(255 * opacity) & 0xFF;
        int bgBase = darkTheme ? 0x222222 : 0xFFFFFFFF;
        int bgColor = ((alpha << 24) | (bgBase & 0x00FFFFFF));

        int titleColor = ((alpha << 24) | (accentColorARGB & 0x00FFFFFF));

        int fullW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int fullH = Minecraft.getInstance().getWindow().getGuiScaledHeight();

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
        guiGraphics.fill(renderX, renderY, renderX + renderW, renderY + 26, titleColor);

        int textColor = contrastingColorFor(accentColorARGB);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(appName), renderX + 8, renderY + 6, textColor, false);

        int btnY = renderY + 6;

        guiGraphics.fill(renderX + renderW - 20, btnY, renderX + renderW - 8, btnY + 14, 0xFFFF6666 | ((alpha << 24) & 0xFF000000));
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("X"), renderX + renderW - 19, btnY, contrastingColorFor(0xFFFF6666), false);

        guiGraphics.fill(renderX + renderW - 44, btnY, renderX + renderW - 32, btnY + 14, 0xFFFFFF66 | ((alpha << 24) & 0xFF000000));
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("-"), renderX + renderW - 43, btnY, contrastingColorFor(0xFFFFFF66), false);

        int accent = accentColorARGB & 0x00FFFFFF;
        int accentWithAlpha = ((alpha << 24) | (accent & 0x00FFFFFF));
        guiGraphics.fill(renderX + renderW - 68, btnY, renderX + renderW - 56, btnY + 14, accentWithAlpha);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("▢"), renderX + renderW - 65, btnY, contrastingColorFor(accentWithAlpha), false);

        if (app != null && !minimized) {
            try {
                app.tick();
            } catch (Exception ignored) {}
            app.renderContent(guiGraphics, poseStack, this, mouseX, mouseY, partialTick);
        }
    }


    public int[] getRenderRect(int taskbarHeight) {
        int fullW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int fullH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (exclusiveFullscreen) return new int[]{0, 0, fullW, fullH};
        if (maximized) return new int[]{0, 0, fullW, Math.max(0, fullH - taskbarHeight)};
        return new int[]{Math.round(displayX), Math.round(displayY), width, height};
    }
    @SuppressWarnings("unused")
    public boolean handleTitlebarClick(double mouseX, double mouseY, int button, int taskbarHeight) {
        int[] r = getRenderRect(taskbarHeight);
        int renderX = r[0], renderY = r[1], renderW = r[2];
        if (mouseX >= renderX + renderW - 20 && mouseX <= renderX + renderW - 8 && mouseY >= renderY + 6 && mouseY <= renderY + 20) {
            requestClose();
            return true;
        }

        if (mouseX >= renderX + renderW - 44 && mouseX <= renderX + renderW - 32 && mouseY >= renderY + 6 && mouseY <= renderY + 20) {
            minimized = true;
            targetOpacity = 0f;
            return true;
        }

        if (mouseX >= renderX + renderW - 68 && mouseX <= renderX + renderW - 56 && mouseY >= renderY + 6 && mouseY <= renderY + 20) {
            maximized = !maximized;
            if (maximized) { x = 0; y = 0; }
            dragging = false;
            return true;
        }

        if (!maximized && !exclusiveFullscreen && mouseX >= renderX && mouseX <= renderX + renderW && mouseY >= renderY && mouseY <= renderY + 26) {
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
            x = (int) (mouseX - dragOffsetX);
            y = (int) (mouseY - dragOffsetY);
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
        return mouseX >= rx && mouseX <= rx + rw && mouseY >= ry && mouseY <= ry + rh;
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

    public static void closeAllWindows() {
        for (DraggableWindow w : ALL_WINDOWS) {
            if (w != null) w.requestClose();
        }
    }
}