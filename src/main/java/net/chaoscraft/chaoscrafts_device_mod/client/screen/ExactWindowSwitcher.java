package net.chaoscraft.chaoscrafts_device_mod.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public class ExactWindowSwitcher {
    private final DesktopScreen parent;
    private boolean active = false;

    private final Map<DraggableWindow, Float> hoverScales = new WeakHashMap<>();
    private long lastRenderTime = -1L;

    private long activationStart = -1L;
    private static final int THUMB_ANIM_DURATION_MS = 300;
    private static final int THUMB_ANIM_STAGGER_MS = 60;

    private boolean closingAnimation = false;
    private long closeStart = -1L;
    private static final int CLOSE_ANIM_DURATION_MS = 240;

    public ExactWindowSwitcher(DesktopScreen parent) {
        this.parent = parent;
    }

    public void activate() {
        this.active = true;
        this.closingAnimation = false;
        this.closeStart = -1L;
        this.activationStart = System.currentTimeMillis();
    }

    public void deactivate() {
        if (!this.active || this.closingAnimation) return;

        try {
            List<DraggableWindow> apps = parent.getOpenApps();
            if (apps == null || apps.isEmpty()) {
                this.active = false;
                this.closingAnimation = false;
                this.activationStart = -1L;
                this.closeStart = -1L;
                return;
            }
        } catch (Exception ignored) {}

        this.closingAnimation = true;
        this.closeStart = System.currentTimeMillis();
    }

    public void toggle() {
        if (!this.active) {
            this.active = true;
            this.closingAnimation = false;
            this.closeStart = -1L;
            this.activationStart = System.currentTimeMillis();
        } else {
            if (this.closingAnimation) {
                this.closingAnimation = false;
                this.activationStart = System.currentTimeMillis();
                this.closeStart = -1L;
            } else {
                try {
                    List<DraggableWindow> apps = parent.getOpenApps();
                    if (apps == null || apps.isEmpty()) {
                        this.active = false;
                        this.closingAnimation = false;
                        this.activationStart = -1L;
                        this.closeStart = -1L;
                        return;
                    }
                } catch (Exception ignored) {}

                this.closingAnimation = true;
                this.closeStart = System.currentTimeMillis();
            }
        }
    }

    public boolean isActive() { return this.active; }

    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        if (!active) return;

        float uiScale = net.chaoscraft.chaoscrafts_device_mod.ConfigHandler.uiScaleFactor();
        int lw = Math.round(Minecraft.getInstance().getWindow().getGuiScaledWidth() / uiScale);
        int lh = Math.round(Minecraft.getInstance().getWindow().getGuiScaledHeight() / uiScale);

        List<DraggableWindow> windows = new ArrayList<>();
        try {
            for (DraggableWindow window : parent.getOpenApps()) {
                if (!window.closing && !window.removeRequested) {
                    windows.add(window);
                }
            }
        } catch (Exception ignored) {}

        if (windows.isEmpty()) {
            Font font = Minecraft.getInstance().font;
            String msg = "No open Windows";
            int w = font.width(msg);
            gui.drawString(font, Component.literal(msg), (lw - w) / 2, lh / 2, 0xFFFFFFFF, false);
            this.lastRenderTime = System.currentTimeMillis();
            return;
        }

        int count = windows.size();
        int maxCols = Math.min(4, Math.max(1, count));
        int rows = (int) Math.ceil((double) count / maxCols);

        int padding = 20;
        int availableWidth = lw - (padding * 2);
        int availableHeight = lh - (padding * 2) - 40;

        int thumbWidth = Math.min(350, (availableWidth - (maxCols - 1) * padding) / maxCols);
        int thumbHeight = Math.min(250, (availableHeight - (rows - 1) * padding) / rows);

        int gridWidth = maxCols * thumbWidth + (maxCols - 1) * padding;
        int gridHeight = rows * thumbHeight + (rows - 1) * padding;
        int startX = (lw - gridWidth) / 2;
        int startY = (lh - gridHeight) / 3;

        Font font = Minecraft.getInstance().font;

        long now = System.currentTimeMillis();
        long dtMs = (this.lastRenderTime > 0) ? Math.max(0L, Math.min(200L, now - this.lastRenderTime)) : 16L;
        this.lastRenderTime = now;
        float smoothFactor = Math.min(1f, dtMs / 80f);

        boolean animateOpen = this.activationStart > 0 && !this.closingAnimation && now >= this.activationStart;
        long openBaseElapsed = animateOpen ? now - this.activationStart : THUMB_ANIM_DURATION_MS;
        boolean animateClose = this.closingAnimation && this.closeStart > 0 && now >= this.closeStart;
        long closeBaseElapsed = animateClose ? now - this.closeStart : CLOSE_ANIM_DURATION_MS;

        int targetBackdropAlpha = 0xCC;
        int backdropAlphaByte = targetBackdropAlpha;
        if (animateOpen) {
            long totalOpenMs = THUMB_ANIM_DURATION_MS + THUMB_ANIM_STAGGER_MS * Math.max(0, count - 1);
            double p = Math.max(0.0, Math.min(1.0, (double)(now - this.activationStart) / Math.max(1L, totalOpenMs)));
            p = easeOutCubic((float)p);
            backdropAlphaByte = (int)Math.round(targetBackdropAlpha * p);
        } else if (animateClose) {
            long totalCloseMs = CLOSE_ANIM_DURATION_MS + THUMB_ANIM_STAGGER_MS * Math.max(0, count - 1);
            double p = Math.max(0.0, Math.min(1.0, (double)(now - this.closeStart) / Math.max(1L, totalCloseMs)));
            p = easeInCubic((float)(1.0 - p));
            backdropAlphaByte = (int)Math.round(targetBackdropAlpha * p);
        } else {
            backdropAlphaByte = targetBackdropAlpha;
        }
        int backdropColor = (backdropAlphaByte << 24);
        gui.fill(0, 0, lw, lh, backdropColor);

        hoverScales.keySet().removeIf(k -> !windows.contains(k));

        for (int i = 0; i < windows.size(); i++) {
            DraggableWindow window = windows.get(i);
            int col = i % maxCols;
            int row = i / maxCols;
            int thumbX = startX + col * (thumbWidth + padding);
            int thumbY = startY + row * (thumbHeight + padding);

            boolean hovered = (mouseX >= thumbX && mouseX < thumbX + thumbWidth &&
                    mouseY >= thumbY && mouseY < thumbY + thumbHeight);

            float targetScale = hovered ? 1.08f : 1.0f;
            float currentScale = hoverScales.getOrDefault(window, 1.0f);
            float newScale = currentScale + (targetScale - currentScale) * smoothFactor;
            hoverScales.put(window, newScale);

            int scaledThumbWidth = Math.round(thumbWidth * newScale);
            int scaledThumbHeight = Math.round(thumbHeight * newScale);
            int scaledThumbX = thumbX - (scaledThumbWidth - thumbWidth) / 2;
            int scaledThumbY = thumbY - (scaledThumbHeight - thumbHeight) / 2;

            if (!animateOpen && !animateClose && hovered) {
                gui.fill(scaledThumbX - 3, scaledThumbY - 3, scaledThumbX + scaledThumbWidth + 3, scaledThumbY + scaledThumbHeight + 3, 0x44FFFFFF);
                gui.fill(scaledThumbX - 2, scaledThumbY - 2, scaledThumbX + scaledThumbWidth + 2, scaledThumbY + scaledThumbHeight + 2, 0x664C7BD1);
            }

            float thumbProgress = 1.0f;
            if (animateOpen) {
                long delay = i * THUMB_ANIM_STAGGER_MS;
                long localElapsed = openBaseElapsed - delay;
                if (localElapsed <= 0) {
                    thumbProgress = 0f;
                } else if (localElapsed >= THUMB_ANIM_DURATION_MS) {
                    thumbProgress = 1f;
                } else {
                    thumbProgress = (float) localElapsed / (float) THUMB_ANIM_DURATION_MS;
                }
                thumbProgress = easeOutCubic(thumbProgress);
            } else if (animateClose) {
                long delay = i * THUMB_ANIM_STAGGER_MS;
                long localElapsed = closeBaseElapsed - delay;
                if (localElapsed <= 0) {
                    thumbProgress = 1f;
                } else if (localElapsed >= CLOSE_ANIM_DURATION_MS) {
                    thumbProgress = 0f;
                } else {
                    float t = (float) localElapsed / (float) CLOSE_ANIM_DURATION_MS;
                    thumbProgress = 1f - t;
                }
                thumbProgress = easeInCubic(thumbProgress);
            } else {
                thumbProgress = 1.0f;
            }

            gui.pose().pushPose();
            float slideY = (1.0f - thumbProgress) * 30f;
            float scale = 0.96f + 0.04f * thumbProgress;
            gui.pose().translate(0, slideY, 0);
            gui.pose().scale(scale, scale, 1.0f);

            renderExactWindowThumbnail(gui, window, scaledThumbX, scaledThumbY, scaledThumbWidth, scaledThumbHeight, partialTick, true);

            if (thumbProgress < 1.0f) {
                int overlayAlpha = Math.round((1.0f - thumbProgress) * 200f);
                int overlayColor = (overlayAlpha << 24);
                gui.fill(scaledThumbX, scaledThumbY, scaledThumbX + scaledThumbWidth, scaledThumbY + scaledThumbHeight, overlayColor);
            }

            gui.pose().popPose();

            String title = window.appName == null ? "<app>" : window.appName;
            String prettyTitle = toTitleCase(title);
            if (font.width(prettyTitle) > scaledThumbWidth - 8) {
                prettyTitle = font.plainSubstrByWidth(prettyTitle, scaledThumbWidth - 12) + "...";
            }

            int titleY = scaledThumbY + scaledThumbHeight + 4;
            int titleWidth = font.width(prettyTitle);

            if (window.minimized) {
                prettyTitle = "[-] " + prettyTitle;
                titleWidth = font.width(prettyTitle);
            }

            gui.drawString(font, Component.literal(prettyTitle),
                    scaledThumbX + (scaledThumbWidth - titleWidth) / 2,
                    titleY, window.minimized ? 0xFFAAAAAA : 0xFFFFFFFF, false);
        }

        if (this.closingAnimation) {
            long totalCloseMs = CLOSE_ANIM_DURATION_MS + THUMB_ANIM_STAGGER_MS * Math.max(0, count - 1);
            if (now - this.closeStart >= totalCloseMs) {
                this.active = false;
                this.closingAnimation = false;
                this.activationStart = -1L;
                this.closeStart = -1L;
                return;
            }
        }

        String hint = "Press Tab again to close • Click a window to bring to front";
        int hintWidth = font.width(hint);
        gui.drawString(font, Component.literal(hint),
                (lw - hintWidth) / 2,
                startY + gridHeight + 20, 0xFFCCCCCC, false);
    }

    private static float easeOutCubic(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private static float easeInCubic(float t) {
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t * t * t;
    }

    private void renderExactWindowThumbnail(GuiGraphics gui, DraggableWindow window,
                                            int thumbX, int thumbY, int thumbWidth, int thumbHeight, float partialTick, boolean drawOverlays) {
        float originalDisplayX = window.getDisplayX();
        float originalDisplayY = window.getDisplayY();
        int originalX = window.x;
        int originalY = window.y;
        int originalWidth = window.width;
        int originalHeight = window.height;
        boolean originalMinimized = window.minimized;
        boolean originalMaximized = window.maximized;
        boolean originalExclusiveFullscreen = window.exclusiveFullscreen;
        boolean originalDragging = window.dragging;

        float windowAspect = (float) originalWidth / Math.max(1, originalHeight);
        float thumbAspect = (float) thumbWidth / thumbHeight;

        int scaledWidth, scaledHeight;
        int renderX, renderY;

        if (windowAspect > thumbAspect) {
            scaledWidth = thumbWidth - 10;
            scaledHeight = (int) (scaledWidth / windowAspect);
        } else {
            scaledHeight = thumbHeight - 10;
            scaledWidth = (int) (scaledHeight * windowAspect);
        }

        renderX = thumbX + (thumbWidth - scaledWidth) / 2;
        renderY = thumbY + (thumbHeight - scaledHeight) / 2;

        window.minimized = false;
        window.maximized = false;
        window.exclusiveFullscreen = false;
        window.dragging = false;

        gui.pose().pushPose();
        gui.pose().translate(renderX, renderY, 0);

        float scaleX = (float) scaledWidth / Math.max(1, originalWidth);
        float scaleY = (float) scaledHeight / Math.max(1, originalHeight);
        gui.pose().scale(scaleX, scaleY, 1.0f);

        try {
            window.x = 0;
            window.y = 0;
            window.displayX = 0;
            window.displayY = 0;

            com.mojang.blaze3d.vertex.PoseStack windowPose = new com.mojang.blaze3d.vertex.PoseStack();

            renderWindowDecorations(gui, window);

            float uiScale = net.chaoscraft.chaoscrafts_device_mod.ConfigHandler.uiScaleFactor();
            int contentX = renderX;
            int contentY = renderY + Math.round(26 * scaleY);
            int contentW = scaledWidth;
            int contentH = scaledHeight - Math.round(26 * scaleY);
            if (contentH < 1) contentH = Math.max(1, scaledHeight - 1);

            int scX0 = Math.round(contentX * uiScale);
            int scY0 = Math.round(contentY * uiScale);
            int scX1 = Math.round((contentX + contentW) * uiScale);
            int scY1 = Math.round((contentY + contentH) * uiScale);

            gui.enableScissor(scX0, scY0, scX1, scY1);
            try {
                renderWindowAppContent(gui, window, windowPose, 0, 0, partialTick);
            } finally {
                gui.disableScissor();
            }

        } catch (Exception e) {
            gui.pose().popPose();
            gui.pose().pushPose();
            gui.fill(thumbX, thumbY, thumbX + thumbWidth, thumbY + thumbHeight, 0xFF333333);
            Font font = Minecraft.getInstance().font;
            String errorMsg = "Preview unavailable";
            int msgWidth = font.width(errorMsg);
            gui.drawString(font, Component.literal(errorMsg),
                    thumbX + (thumbWidth - msgWidth) / 2,
                    thumbY + thumbHeight / 2, 0xFFFFFFFF, false);
        } finally {
            gui.pose().popPose();

            window.x = originalX;
            window.y = originalY;
            window.displayX = originalDisplayX;
            window.displayY = originalDisplayY;
            window.width = originalWidth;
            window.height = originalHeight;
            window.minimized = originalMinimized;
            window.maximized = originalMaximized;
            window.exclusiveFullscreen = originalExclusiveFullscreen;
            window.dragging = originalDragging;
        }

        gui.fill(thumbX - 1, thumbY - 1, thumbX + thumbWidth + 1, thumbY, 0xFF666666);
        gui.fill(thumbX - 1, thumbY + thumbHeight, thumbX + thumbWidth + 1, thumbY + thumbHeight + 1, 0xFF666666);
        gui.fill(thumbX - 1, thumbY, thumbX, thumbY + thumbHeight, 0xFF666666);
        gui.fill(thumbX + thumbWidth, thumbY, thumbX + thumbWidth + 1, thumbY + thumbHeight, 0xFF666666);

        if (originalMinimized && drawOverlays) {
            gui.fill(thumbX, thumbY, thumbX + thumbWidth, thumbY + thumbHeight, 0x66000000);
            Font font = Minecraft.getInstance().font;
            String minimizedText = "Minimized";
            int textWidth = font.width(minimizedText);
            gui.drawString(font, Component.literal(minimizedText),
                    thumbX + (thumbWidth - textWidth) / 2,
                    thumbY + thumbHeight / 2 - 4, 0xFFFFFFFF, false);
        }
    }

    private void renderWindowContentDirectly(GuiGraphics gui, DraggableWindow window,
                                             com.mojang.blaze3d.vertex.PoseStack poseStack,
                                             int mouseX, int mouseY, boolean focused,
                                             int taskbarHeight, float partialTick) {
        int alpha = 255;
        int bgBase = DraggableWindow.darkTheme ? 0x222222 : 0xFFFFFFFF;
        int bgColor = ((alpha << 24) | (bgBase & 0x00FFFFFF));

        int titleColor = ((alpha << 24) | (DraggableWindow.accentColorARGB & 0x00FFFFFF));

        int renderX = 0;
        int renderY = 0;
        int renderW = window.width;
        int renderH = window.height;

        int shadowAlpha = Math.round(alpha * 0.25f);
        gui.fill(renderX - 6, renderY - 6, renderX + renderW + 6, renderY + renderH + 6, (shadowAlpha << 24) | 0x000000);

        gui.fill(renderX, renderY, renderX + renderW, renderY + renderH, bgColor);

        int titleH = 26;
        int topH = Math.max(1, Math.round(titleH * 0.12f));
        int bottomH = Math.max(1, Math.round(titleH * 0.12f));
        int middleH = Math.max(0, titleH - topH - bottomH);

        int accentRgb = DraggableWindow.accentColorARGB & 0x00FFFFFF;
        int lighterRgb = DraggableWindow.lightenRgb(accentRgb, 0.12f);
        int darkerRgb = DraggableWindow.darkenRgb(accentRgb, 0.12f);

        int titleAlpha = (titleColor >>> 24) & 0xFF;
        int lighterColor = (titleAlpha << 24) | (lighterRgb & 0x00FFFFFF);
        int normalColor = (titleAlpha << 24) | (accentRgb & 0x00FFFFFF);
        int darkerColor = (titleAlpha << 24) | (darkerRgb & 0x00FFFFFF);

        gui.fill(renderX, renderY, renderX + renderW, renderY + topH, lighterColor);
        gui.fill(renderX, renderY + topH, renderX + renderW, renderY + topH + middleH, normalColor);
        gui.fill(renderX, renderY + topH + middleH, renderX + renderW, renderY + titleH, darkerColor);

        int textColor = DraggableWindow.contrastingColorFor(DraggableWindow.accentColorARGB);
        String title = window.appName == null ? "<app>" : window.appName;
        String formattedTitle = formatTitleName(title);
        gui.drawString(Minecraft.getInstance().font, Component.literal(formattedTitle), renderX + 8, renderY + 6, textColor, false);

        int btnY = renderY + 6;
        int btnW = 12;
        int btnH = 14;

        int closeX = renderX + renderW - 20;
        gui.fill(closeX, btnY, closeX + btnW, btnY + btnH, 0xFFFF6666);
        gui.drawString(Minecraft.getInstance().font, Component.literal("X"), closeX + 3, btnY, 0xFF000000, false);

        int minX = renderX + renderW - 44;
        gui.fill(minX, btnY, minX + btnW, btnY + btnH, 0xFFFFFF66);
        gui.drawString(Minecraft.getInstance().font, Component.literal("-"), minX + 3, btnY, 0xFF000000, false);

        int fullX = renderX + renderW - 68;
        gui.fill(fullX, btnY, fullX + btnW, btnY + btnH, 0xFF66FF66);
        gui.drawString(Minecraft.getInstance().font, Component.literal("□"), fullX + 3, btnY, 0xFF000000, false);
    }

    private void renderWindowDecorations(GuiGraphics gui, DraggableWindow window) {
        int alpha = 255;
        int bgBase = DraggableWindow.darkTheme ? 0x222222 : 0xFFFFFFFF;
        int bgColor = ((alpha << 24) | (bgBase & 0x00FFFFFF));

        int renderX = 0;
        int renderY = 0;
        int renderW = window.width;
        int renderH = window.height;

        int shadowAlpha = Math.round(alpha * 0.25f);
        gui.fill(renderX - 6, renderY - 6, renderX + renderW + 6, renderY + renderH + 6, (shadowAlpha << 24) | 0x000000);

        gui.fill(renderX, renderY, renderX + renderW, renderY + renderH, bgColor);

        int titleH = 26;
        int topH = Math.max(1, Math.round(titleH * 0.12f));
        int bottomH = Math.max(1, Math.round(titleH * 0.12f));
        int middleH = Math.max(0, titleH - topH - bottomH);

        int accentRgb = DraggableWindow.accentColorARGB & 0x00FFFFFF;
        int lighterRgb = DraggableWindow.lightenRgb(accentRgb, 0.12f);
        int darkerRgb = DraggableWindow.darkenRgb(accentRgb, 0.12f);

        int titleAlpha = (alpha << 24) >>> 24;
        int lighterColor = ((alpha) << 24) | (lighterRgb & 0x00FFFFFF);
        int normalColor = ((alpha) << 24) | (accentRgb & 0x00FFFFFF);
        int darkerColor = ((alpha) << 24) | (darkerRgb & 0x00FFFFFF);

        gui.fill(renderX, renderY, renderX + renderW, renderY + topH, lighterColor);
        gui.fill(renderX, renderY + topH, renderX + renderW, renderY + topH + middleH, normalColor);
        gui.fill(renderX, renderY + topH + middleH, renderX + renderW, renderY + titleH, darkerColor);

        int textColor = DraggableWindow.contrastingColorFor(DraggableWindow.accentColorARGB);
        String title = window.appName == null ? "<app>" : window.appName;
        String formattedTitle = formatTitleName(title);
        gui.drawString(Minecraft.getInstance().font, Component.literal(formattedTitle), renderX + 8, renderY + 6, textColor, false);

        int btnY = renderY + 6;
        int btnW = 12;
        int btnH = 14;

        int closeX = renderX + renderW - 20;
        gui.fill(closeX, btnY, closeX + btnW, btnY + btnH, 0xFFFF6666);
        gui.drawString(Minecraft.getInstance().font, Component.literal("X"), closeX + 3, btnY, 0xFF000000, false);

        int minX = renderX + renderW - 44;
        gui.fill(minX, btnY, minX + btnW, btnY + btnH, 0xFFFFFF66);
        gui.drawString(Minecraft.getInstance().font, Component.literal("-"), minX + 3, btnY, 0xFF000000, false);

        int fullX = renderX + renderW - 68;
        gui.fill(fullX, btnY, fullX + btnW, btnY + btnH, 0xFF66FF66);
        gui.drawString(Minecraft.getInstance().font, Component.literal("□"), fullX + 3, btnY, 0xFF000000, false);
    }

    private void renderWindowAppContent(GuiGraphics gui, DraggableWindow window,
                                        com.mojang.blaze3d.vertex.PoseStack poseStack,
                                        int mouseX, int mouseY, float partialTick) {
        int renderX = 0;
        int renderY = 0;
        int renderW = window.width;
        int renderH = window.height;

        if (window.app != null) {
            try {
                window.app.renderContent(gui, poseStack, window, mouseX - renderX, mouseY - renderY, partialTick);
            } catch (Exception e) {
                int contentX = renderX;
                int contentY = renderY + 26;
                int contentWidth = renderW;
                int contentHeight = renderH - 26;
                gui.fill(contentX, contentY, contentX + contentWidth, contentY + contentHeight, 0xFF1A1A1A);

                Font font = Minecraft.getInstance().font;
                String appContentText = window.appName + " Content";
                int textWidth = font.width(appContentText);
                gui.drawString(font, Component.literal(appContentText),
                        contentX + (contentWidth - textWidth) / 2,
                        contentY + contentHeight / 2 - 4, 0xFFFFFFFF, false);
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || button != 0) return false;

        float uiScale = net.chaoscraft.chaoscrafts_device_mod.ConfigHandler.uiScaleFactor();
        int lw = Math.round(Minecraft.getInstance().getWindow().getGuiScaledWidth() / uiScale);
        int lh = Math.round(Minecraft.getInstance().getWindow().getGuiScaledHeight() / uiScale);

        List<DraggableWindow> windows = new ArrayList<>();
        try {
            for (DraggableWindow window : parent.getOpenApps()) {
                if (!window.closing && !window.removeRequested) {
                    windows.add(window);
                }
            }
        } catch (Exception ignored) {}

        if (windows.isEmpty()) {
            this.active = false;
            this.closingAnimation = false;
            this.activationStart = -1L;
            this.closeStart = -1L;
            return true;
        }

        int count = windows.size();
        int maxCols = Math.min(4, Math.max(1, count));
        int rows = (int) Math.ceil((double) count / maxCols);
        int padding = 20;
        int availableWidth = lw - (padding * 2);
        int availableHeight = lh - (padding * 2) - 40;
        int thumbWidth = Math.min(350, (availableWidth - (maxCols - 1) * padding) / maxCols);
        int thumbHeight = Math.min(250, (availableHeight - (rows - 1) * padding) / rows);
        int gridWidth = maxCols * thumbWidth + (maxCols - 1) * padding;
        int gridHeight = rows * thumbHeight + (rows - 1) * padding;
        int startX = (lw - gridWidth) / 2;
        int startY = (lh - gridHeight) / 3;

        int mx = Math.round((float) mouseX);
        int my = Math.round((float) mouseY);

        for (int i = 0; i < windows.size(); i++) {
            DraggableWindow window = windows.get(i);
            int col = i % maxCols;
            int row = i / maxCols;
            int thumbX = startX + col * (thumbWidth + padding);
            int thumbY = startY + row * (thumbHeight + padding);

            float currentScale = hoverScales.getOrDefault(window, 1.0f);
            int scaledThumbWidth = Math.round(thumbWidth * currentScale);
            int scaledThumbHeight = Math.round(thumbHeight * currentScale);
            int scaledThumbX = thumbX - (scaledThumbWidth - thumbWidth) / 2;
            int scaledThumbY = thumbY - (scaledThumbHeight - thumbHeight) / 2;

            if (mx >= scaledThumbX && mx < scaledThumbX + scaledThumbWidth && my >= scaledThumbY && my < scaledThumbY + scaledThumbHeight) {

                if (window.minimized) {
                    window.restore();
                }
                parent.bringToFront(window);
                playClick();
                this.deactivate();
                return true;
            }
        }

        this.deactivate();
        return true;
    }

    public void renderThumbnail(GuiGraphics gui, DraggableWindow window,
                                int thumbX, int thumbY, int thumbWidth, int thumbHeight, float partialTick, boolean drawOverlays) {
        try {
            renderExactWindowThumbnail(gui, window, thumbX, thumbY, thumbWidth, thumbHeight, partialTick, drawOverlays);
        } catch (Exception e) {
             try {
                 gui.fill(thumbX, thumbY, thumbX + thumbWidth, thumbY + thumbHeight, 0xFF333333);
                 Font font = Minecraft.getInstance().font;
                 String errorMsg = "Preview unavailable";
                 int msgWidth = font.width(errorMsg);
                 gui.drawString(font, Component.literal(errorMsg),
                         thumbX + (thumbWidth - msgWidth) / 2,
                         thumbY + thumbHeight / 2, 0xFFFFFFFF, false);
             } catch (Exception ignored) {}
         }
     }

    private static String toTitleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        String base = s;
        if (base.toLowerCase(java.util.Locale.ROOT).endsWith(".txt")) {
            base = base.substring(0, base.length() - 4);
        }
        StringBuilder sb = new StringBuilder();
        boolean capNext = true;
        for (char c : base.toCharArray()) {
            if (Character.isWhitespace(c) || c == '_' || c == '-') {
                sb.append(c);
                capNext = true;
                continue;
            }
            if (capNext) {
                sb.append(Character.toUpperCase(c));
                capNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private static String formatTitleName(String name) {
        if (name == null || name.isEmpty()) return "";
        String base = name;
        if (base.toLowerCase(java.util.Locale.ROOT).endsWith(".txt")) {
            base = base.substring(0, base.length() - 4);
        }
        StringBuilder sb = new StringBuilder();
        boolean capNext = true;
        for (char c : base.toCharArray()) {
            if (Character.isWhitespace(c) || c == '_' || c == '-') {
                sb.append(c);
                capNext = true;
                continue;
            }
            if (capNext) {
                sb.append(Character.toUpperCase(c));
                capNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private void playClick() {
        try {
            Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F
                    )
            );
        } catch (Exception ignored) {}
    }
}
