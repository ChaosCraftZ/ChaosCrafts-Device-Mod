package net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager.IconManager;
import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DraggableWindow;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Utils.DesktopConstants;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Utils.DesktopState;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Renders the taskbar and handles taskbar interactions.
 */
public class TaskbarRenderer {
    private final DesktopState state;
    private final WindowManager windowManager;

    public TaskbarRenderer(DesktopState state, WindowManager windowManager) {
        this.state = state;
        this.windowManager = windowManager;
    }

    public void renderTaskbar(GuiGraphics guiGraphics, int width, int height, int mouseX, int mouseY) {
        int tbY = height - DesktopConstants.TASKBAR_HEIGHT;
        // position the search box but render it after drawing the taskbar background
        state.searchBox.setX(DesktopConstants.SEARCH_BOX_X);
        state.searchBox.setY(tbY + 4);

        // taskbar base - semi-transparent greyish with subtle green undertone
        int taskbarBase = 0xCC2B2F33; // more greyish, slightly transparent
        int topSheen = 0x22FFFFFF;
        guiGraphics.fill(0, tbY, width, height, taskbarBase);
        // subtle top sheen strip
        guiGraphics.fill(0, tbY, width, tbY + 3, topSheen);
        // soft shadow above the bar
        guiGraphics.fill(0, tbY - 2, width, tbY, 0x22000000);

        // area reserved on right for system tray and stacked clock
        final int trayIconW = 28;
        final int trayIconH = 18;
        final int trayIconPad = 6;

        List<String> trayLabels = Arrays.asList("LAN", "🔊", "Upd", "✉", "12°C ☁", "/\\");

        int iconsTotalW = trayLabels.size() * trayIconW + (trayLabels.size() - 1) * trayIconPad + 8;
        String timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"));
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("M/d/yyyy"));
        Font font = Minecraft.getInstance().font;
        int timeW = font.width(timeStr);
        int dateW = font.width(dateStr);
        int clockW = Math.max(timeW, dateW) + 12; // padding
        int trayTotalWidth = iconsTotalW + clockW + 12; // extra spacing to right edge

        int trayRightPadding = 10;
        int trayLeft = Math.max(6 + DesktopConstants.SEARCH_BOX_WIDTH + 16, width - trayTotalWidth - trayRightPadding);
        int x = trayLeft;
        int iconY = tbY + (DesktopConstants.TASKBAR_HEIGHT - trayIconH) / 2;

        for (String lbl : trayLabels) {
            guiGraphics.fill(x, iconY, x + trayIconW, iconY + trayIconH, 0x20000000);
            guiGraphics.fill(x + 1, iconY + 1, x + trayIconW - 1, iconY + trayIconH - 1, 0x33000000);
            int lw = font.width(lbl);
            int lx = x + Math.max(4, (trayIconW - lw) / 2);
            int ly = iconY + Math.max(1, (trayIconH - 8) / 2);
            guiGraphics.drawString(font, Component.literal(lbl), lx, ly, 0xFFFFFFFF, false);
            x += trayIconW + trayIconPad;
        }

        int clockRightEdge = width - trayRightPadding;
        int clockLeft = clockRightEdge - Math.max(timeW, dateW);
        int minClockLeft = trayLeft + iconsTotalW + 6;
        if (clockLeft < minClockLeft) clockLeft = minClockLeft;
        int timeY = tbY + Math.max(2, (DesktopConstants.TASKBAR_HEIGHT - (font.lineHeight * 2)) / 2);
        int dateY = timeY + Math.max(6, font.lineHeight - 2);
        guiGraphics.drawString(font, Component.literal(timeStr), clockLeft, timeY, 0xFFFFFFFF, false);
        guiGraphics.drawString(font, Component.literal(dateStr), clockLeft, dateY, 0xFFCCCCCC, false);

        // render the search box on top so it is visible and receives input reliably
        try { state.searchBox.render(guiGraphics, mouseX, mouseY, 0); } catch (Exception ignored) {}

        // Draw search results popup above the taskbar if there are results
        renderSearchResults(guiGraphics, width, tbY, mouseX, mouseY);

        // draw open-window taskbar icons starting after the search box and before trayLeft
        renderWindowIcons(guiGraphics, width, height, tbY, trayLeft, mouseX, mouseY);
    }

    private void renderSearchResults(GuiGraphics guiGraphics, int width, int tbY, int mouseX, int mouseY) {
        if (state.searchBox.getValue().isEmpty() || state.searchResults.isEmpty()) return;

        int popupW = Math.max(state.searchBox.getWidth(), 260);
        int entries = Math.min(state.searchResults.size(), 6);
        int popupH = entries * DesktopConstants.RESULT_HEIGHT + 6;
        int popupX = state.searchBox.getX();
        int popupY = tbY - popupH - 6;
        if (popupY < 6) popupY = 6;

        guiGraphics.fill(popupX - 4, popupY - 4, popupX + popupW + 4, popupY + popupH + 4, 0xEE111111);
        guiGraphics.fill(popupX - 3, popupY - 3, popupX + popupW + 3, popupY + popupH + 3, 0xCC1F1F1F);

        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < entries; i++) {
            var r = state.searchResults.get(i);
            int ry = popupY + 6 + i * DesktopConstants.RESULT_HEIGHT;
            int rw = popupW - 12;
            boolean hovered = mouseX >= popupX + 6 && mouseX <= popupX + 6 + rw && mouseY >= ry && mouseY <= ry + DesktopConstants.RESULT_HEIGHT;
            if (hovered) guiGraphics.fill(popupX + 6, ry, popupX + 6 + rw, ry + DesktopConstants.RESULT_HEIGHT, 0x33FFFFFF);
            int iconSizePx = 20;
            int iconX = popupX + 10;
            if (r.iconRes != null) {
                try { guiGraphics.blit(r.iconRes, iconX, ry + (DesktopConstants.RESULT_HEIGHT - iconSizePx) / 2, 0, 0, iconSizePx, iconSizePx, iconSizePx, iconSizePx); } catch (Exception ignored) {}
            }
            String label = r.displayName;
            int textX = popupX + 12 + iconSizePx + 6;
            int availTextW = popupW - (textX - popupX) - 12;
            if (font.width(label) > availTextW) label = font.plainSubstrByWidth(label, availTextW - 8) + "...";
            guiGraphics.drawString(font, Component.literal(label), textX, ry + 6, 0xFFFFFFFF, false);
        }
    }

    private void renderWindowIcons(GuiGraphics guiGraphics, int width, int height, int tbY, int trayLeft, int mouseX, int mouseY) {
        int perEntry = 52;
        int startX = DesktopConstants.SEARCH_BOX_X + state.searchBox.getWidth() + 8;
        int available = Math.max(0, trayLeft - startX - 8);
        int maxEntries = Math.max(0, available / perEntry);
        List<DraggableWindow> visibleWindows = new java.util.ArrayList<>();
        if (maxEntries > 0) {
            int start = Math.max(0, state.openApps.size() - maxEntries);
            for (int i = start; i < state.openApps.size(); i++) {
                DraggableWindow w0 = state.openApps.get(i);
                if (windowManager.isTaskbarEligible(w0)) visibleWindows.add(w0);
            }
        }

        int tx = startX;
        Font font = Minecraft.getInstance().font;
        for (DraggableWindow w : visibleWindows) {
            int x0 = tx, y0 = tbY + 2, x1 = tx + perEntry - 8, y1 = height - 4;
            boolean hovered = (mouseX >= x0 && mouseX <= x1 && mouseY >= y0 && mouseY <= y1);
            int cx = tx + perEntry / 2 - 6;
            int cy = tbY + DesktopConstants.TASKBAR_HEIGHT / 2 - 1;
            if (hovered) guiGraphics.fill(x0, y0, x1, y1, 0x33FFFFFF);
            w.preview = hovered;
            int tbIconSize = Math.min(24, perEntry - 28);
            try {
                if (w.appName != null) {
                    ResourceLocation appIconRes = IconManager.getIconResource(normalizeAppNameForIcon(w.appName));
                    guiGraphics.blit(appIconRes, cx - tbIconSize / 2, cy - tbIconSize / 2, 0, 0, tbIconSize, tbIconSize, tbIconSize, tbIconSize);
                }
            } catch (Exception ignored) {}

            if (w.minimized) guiGraphics.fill(cx - 3, tbY + DesktopConstants.TASKBAR_HEIGHT - 8, cx + 3, tbY + DesktopConstants.TASKBAR_HEIGHT - 6, 0xFFBBBBBB);

            // hover tooltip for app name
            if (hovered) {
                String name = w.appName == null ? "<app>" : w.appName;
                int nw = font.width(name) + 8;
                int nx = Math.max(6, Math.min(width - nw - 6, cx - nw / 2));
                int ny = tbY - 22;
                guiGraphics.fill(nx, ny, nx + nw, ny + 16, 0xEE222222);
                guiGraphics.drawString(font, Component.literal(name), nx + 4, ny + 3, 0xFFFFFFFF, false);
            }

            tx += perEntry;
        }
    }

    private static String normalizeAppNameForIcon(String displayName) {
        if (displayName == null) return null;
        String n = displayName;
        if (n.contains(" - ")) n = n.substring(n.lastIndexOf(" - ") + 3);
        return n.trim().toLowerCase();
    }
}