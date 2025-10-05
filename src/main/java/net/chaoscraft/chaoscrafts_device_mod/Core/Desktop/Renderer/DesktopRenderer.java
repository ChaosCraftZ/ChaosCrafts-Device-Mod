package net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DraggableWindow;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Components.LoadingScreenRenderer;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Components.TaskbarRenderer;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Model.DesktopIcon;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Utils.DesktopConstants;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Utils.DesktopState;
import net.chaoscraft.chaoscrafts_device_mod.Core.Util.FileSystem.FilesManager;

/**
 * Main renderer for the desktop.
 */
public class DesktopRenderer {
    private final DesktopState state;
    private final LoadingScreenRenderer loadingRenderer;
    private final TaskbarRenderer taskbarRenderer;

    public DesktopRenderer(DesktopState state, LoadingScreenRenderer loadingRenderer, TaskbarRenderer taskbarRenderer) {
        this.state = state;
        this.loadingRenderer = loadingRenderer;
        this.taskbarRenderer = taskbarRenderer;
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, int width, int height) {
        // wallpaper
        FilesManager fm = FilesManager.getInstance();
        int fallbackColor = DraggableWindow.darkTheme ? 0xFF0F0F12 : 0xFF9AA6B2;
        if (fm != null && fm.isCurrentWallpaperColor()) {
            int color = fm.getCurrentWallpaperColor();
            guiGraphics.fill(0, 0, width, height, color);
        } else {
            ResourceLocation texId = fm != null ? fm.getCurrentWallpaperResource() : null;
            if (texId != null) {
                try {
                    guiGraphics.blit(texId, 0, 0, 0, 0, width, height, width, height);
                } catch (Exception e) {
                    // fallback to solid fill if texture fails
                    guiGraphics.fill(0, 0, width, height, fallbackColor);
                }
            } else {
                guiGraphics.fill(0, 0, width, height, fallbackColor);
            }
        }

        // Set text color based on dark mode
        if (DraggableWindow.darkTheme) {
            state.desktopTextColor = 0xFFFFFFFF; // white
            state.desktopTextShadowColor = 0xAA000000; // dark shadow
        } else {
            state.desktopTextColor = 0xFF000000; // black
            state.desktopTextShadowColor = 0xAAFFFFFF; // light shadow
        }

        if (state.showDebugInfo) renderDebugInfo(guiGraphics);

        // desktop icons
        for (DesktopIcon icon : state.desktopIcons) {
            icon.displayX = lerp(icon.displayX, icon.targetX, DesktopConstants.ICON_LERP);
            icon.displayY = lerp(icon.displayY, icon.targetY, DesktopConstants.ICON_LERP);
            icon.render(guiGraphics, mouseX, mouseY, state.selectedIcons.contains(icon), state.iconSize, state.desktopTextColor, state.desktopTextShadowColor);
        }

        // windows rendering (below taskbar)
        for (DraggableWindow w : state.openApps) {
            if (!w.minimized || w.preview || w.closing) {
                boolean focused = (w == (state.openApps.isEmpty() ? null : state.openApps.get(state.openApps.size() - 1)));
                w.render(guiGraphics, guiGraphics.pose(), mouseX, mouseY, focused, DesktopConstants.TASKBAR_HEIGHT, partialTick);
                w.finalizeAnimationState();
            }
        }

        // loading overlay on top. Pineapple :D
        if (loadingRenderer.renderLoadingOverlay(guiGraphics, width, height)) {
            return;
        }

        // selection rect (soft fill + 1px lighter outline)
        if (state.selecting && !state.iconDragging) {
            int x0 = Math.min(state.selectStartX, state.selectEndX), y0 = Math.min(state.selectStartY, state.selectEndY);
            int x1 = Math.max(state.selectStartX, state.selectEndX), y1 = Math.max(state.selectStartY, state.selectEndY);
            int fillCol = ((0xFF33AAFF & 0x00FFFFFF) | 0x220000FF);
            guiGraphics.fill(x0, y0, x1, y1, fillCol);
            int outline = 0x88AAD8FF;
            guiGraphics.fill(x0, y0, x1, y0 + 1, outline);
            guiGraphics.fill(x0, y1 - 1, x1, y1, outline);
            guiGraphics.fill(x0, y0, x0 + 1, y1, outline);
            guiGraphics.fill(x1 - 1, y0, x1, y1, outline);
        }

        // taskbar
        boolean hideTaskbar = false;
        if (!state.openApps.isEmpty()) hideTaskbar = state.openApps.get(state.openApps.size() - 1).exclusiveFullscreen;
        if (!hideTaskbar) {
            taskbarRenderer.renderTaskbar(guiGraphics, width, height, mouseX, mouseY);
        }

        // context menu
        if (state.contextMenu != null) state.contextMenu.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderDebugInfo(GuiGraphics guiGraphics) {
        net.chaoscraft.chaoscrafts_device_mod.Core.AsyncorAPI.AsyncRuntime.AsyncStats stats = net.chaoscraft.chaoscrafts_device_mod.Core.AsyncorAPI.AsyncRuntime.get().snapshotStats();
        guiGraphics.fill(10, 10, 360, 150, 0x80000000);
        guiGraphics.drawString(Minecraft.getInstance().font, "Async Tasks Debug", 15, 15, 0xFFFFFF, false);
        guiGraphics.drawString(Minecraft.getInstance().font, "IO Workers: " + stats.ioWorkers(), 15, 30, 0xFFFFFF, false);
        guiGraphics.drawString(Minecraft.getInstance().font, "CPU Workers: " + stats.computeWorkers(), 15, 43, 0xFFFFFF, false);
        guiGraphics.drawString(Minecraft.getInstance().font, "Queued: " + stats.queuedTasks(), 15, 56, 0xFFFFFF, false);
        guiGraphics.drawString(Minecraft.getInstance().font, "Submitted: " + stats.totalSubmitted(), 15, 69, 0xFFFFFF, false);
        guiGraphics.drawString(Minecraft.getInstance().font, "Completed: " + stats.totalCompleted(), 15, 82, 0xFFFFFF, false);
        guiGraphics.drawString(Minecraft.getInstance().font, "Rejected: " + stats.totalRejected(), 15, 95, 0xFFFFFF, false);
        guiGraphics.drawString(Minecraft.getInstance().font, "Open windows:", 15, 62, 0xFFFFFF, false);
        int y = 109; int maxShow = 8; int count = 0;
        for (DraggableWindow w : state.openApps) {
            if (count++ >= maxShow) break;
            String n = (w == null || w.appName == null) ? "<null>" : w.appName;
            String flags = (w == null) ? "" : (w.minimized ? "[min]" : "") + (w.removeRequested ? "[remReq]" : "") + (w.closing ? "[closing]" : "");
            guiGraphics.drawString(Minecraft.getInstance().font, (count) + ". " + n + " " + flags, 15, y, 0xFFFFFF, false);
            y += 12;
        }
        if (state.openApps.size() > maxShow) guiGraphics.drawString(Minecraft.getInstance().font, "... (" + state.openApps.size() + " total)", 15, y, 0xAAAAAA, false);
    }

    private static float lerp(float a, float b, float f) { return a + (b - a) * f; }
}