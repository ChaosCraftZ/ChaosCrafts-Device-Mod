package net.chaoscraft.chaoscrafts_device_mod.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class WindowSwitcher {
    private final DesktopScreen parent;
    private boolean active = false;

    public WindowSwitcher(DesktopScreen parent) {
        this.parent = parent;
    }

    public void activate() { this.active = true; }
    public void deactivate() { this.active = false; }
    public void toggle() { this.active = !this.active; }
    public boolean isActive() { return this.active; }

    private static String toTitleCaseLocal(String s) {
        if (s == null || s.isEmpty()) return s;
        String base = s;
        if (base.toLowerCase(java.util.Locale.ROOT).endsWith(".txt")) base = base.substring(0, base.length() - 4);
        StringBuilder sb = new StringBuilder(); boolean capNext = true;
        for (char c : base.toCharArray()) {
            if (Character.isWhitespace(c) || c == '_' || c == '-') { sb.append(c); capNext = true; continue; }
            if (capNext) { sb.append(Character.toUpperCase(c)); capNext = false; } else sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        if (!active) return;
        float uiScale = net.chaoscraft.chaoscrafts_device_mod.ConfigHandler.uiScaleFactor();
        int lw = Math.round(Minecraft.getInstance().getWindow().getGuiScaledWidth() / uiScale);
        int lh = Math.round(Minecraft.getInstance().getWindow().getGuiScaledHeight() / uiScale);

        gui.fill(0, 0, lw, lh, 0xCC000000);

        List<DraggableWindow> windows = new ArrayList<>();
        try { windows.addAll(parent.getOpenApps()); } catch (Exception ignored) {}

        if (windows.isEmpty()) {
            Font font = Minecraft.getInstance().font;
            String msg = "No open windows";
            int w = font.width(msg);
            gui.drawString(font, Component.literal(msg), (lw - w) / 2, lh / 2, 0xFFFFFFFF, false);
            return;
        }

        int count = windows.size();
        int cols = Math.min(5, Math.max(1, count));
        int rows = (int) Math.ceil((double) count / cols);
        int padding = 24;
        int totalPadW = (cols + 1) * padding;
        int totalPadH = (rows + 1) * padding;

        int availW = Math.max(200, lw - totalPadW);
        int availH = Math.max(150, lh - totalPadH);

        int thumbW = Math.max(120, availW / cols);
        int thumbH = Math.max(80, Math.min(thumbW * 3 / 4, availH / rows));

        int gridW = cols * thumbW + (cols - 1) * padding;
        int gridH = rows * thumbH + (rows - 1) * padding;
        int startX = (lw - gridW) / 2;
        int startY = (lh - gridH) / 4;

        Font font = Minecraft.getInstance().font;

        for (int i = 0; i < windows.size(); i++) {
            DraggableWindow w = windows.get(i);
            int col = i % cols;
            int row = i / cols;
            int x = startX + col * (thumbW + padding);
            int y = startY + row * (thumbH + padding);

            int bg = 0xFF111111;
            gui.fill(x, y, x + thumbW, y + thumbH, bg);

            int innerPad = 6;
            int cx = x + innerPad;
            int cy = y + innerPad;
            int cw = thumbW - innerPad * 2;
            int ch = thumbH - innerPad * 2 - font.lineHeight - 6;

            gui.fill(cx, cy, cx + cw, cy + ch, 0xFF000000);

            try {
                if (w.app != null) {
                    gui.pose().pushPose();
                    float scaleX = (float) cw / Math.max(1, w.width);
                    float scaleY = (float) ch / Math.max(1, Math.max(1, w.height - 26));
                    float scale = Math.min(scaleX, scaleY);
                    if (scale <= 0f) scale = 0.0001f;
                    gui.pose().translate(cx, cy, 0f);
                    gui.pose().scale(scale, scale, 1f);
                    try {
                        w.app.renderContent(gui, gui.pose(), w, -10000, -10000, partialTick);
                    } catch (Exception ignored) {
                        gui.fill(0, 0, Math.max(1, cw), Math.max(1, ch), 0xFF101010);
                    }
                    gui.pose().popPose();
                }
            } catch (Exception ignored) {
                gui.fill(cx, cy, cx + cw, cy + ch, 0xFF101010);
            }

            int titleY = y + thumbH - font.lineHeight - 6;
            int titleBg = 0xAA000000;
            gui.fill(x + 1, titleY, x + thumbW - 1, titleY + font.lineHeight + 4, titleBg);
            String name = w.appName == null ? "<app>" : w.appName;
            String pretty = toTitleCaseLocal(name);
            if (font.width(pretty) > thumbW - 8) pretty = font.plainSubstrByWidth(pretty, thumbW - 12) + "...";
            gui.drawString(font, Component.literal(pretty), x + 6, titleY + 2, 0xFFFFFFFF, false);

            boolean hovered = (mouseX >= x && mouseX < x + thumbW && mouseY >= y && mouseY < y + thumbH);
            if (hovered) {
                gui.fill(x, y, x + thumbW, y + 2, 0x66FFFFFF);
                gui.fill(x, y + thumbH - 2, x + thumbW, y + thumbH, 0x66FFFFFF);
                gui.fill(x, y, x + 2, y + thumbH, 0x66FFFFFF);
                gui.fill(x + thumbW - 2, y, x + thumbW, y + thumbH, 0x66FFFFFF);
            }
        }

        String hint = "Press Tab again to close • Click a window to open";
        int hw = font.width(hint);
        gui.drawString(font, Component.literal(hint), (lw - hw) / 2, startY + gridH + 16, 0xFFCCCCCC, false);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active) return false;
        int mx = Math.round((float) mouseX);
        int my = Math.round((float) mouseY);

        float uiScale = net.chaoscraft.chaoscrafts_device_mod.ConfigHandler.uiScaleFactor();
        int lw = Math.round(Minecraft.getInstance().getWindow().getGuiScaledWidth() / uiScale);
        int lh = Math.round(Minecraft.getInstance().getWindow().getGuiScaledHeight() / uiScale);

        List<DraggableWindow> windows = new ArrayList<>();
        try { windows.addAll(parent.getOpenApps()); } catch (Exception ignored) {}
        if (windows.isEmpty()) return true;

        int count = windows.size();
        int cols = Math.min(5, Math.max(1, count));
        int rows = (int) Math.ceil((double) count / cols);
        int padding = 24;
        int totalPadW = (cols + 1) * padding;
        int totalPadH = (rows + 1) * padding;

        int availW = Math.max(200, lw - totalPadW);
        int availH = Math.max(150, lh - totalPadH);

        int thumbW = Math.max(120, availW / cols);
        int thumbH = Math.max(80, Math.min(thumbW * 3 / 4, availH / rows));

        int gridW = cols * thumbW + (cols - 1) * padding;
        int gridH = rows * thumbH + (rows - 1) * padding;
        int startX = (lw - gridW) / 2;
        int startY = (lh - gridH) / 4;

        for (int i = 0; i < windows.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = startX + col * (thumbW + padding);
            int y = startY + row * (thumbH + padding);
            if (mx >= x && mx < x + thumbW && my >= y && my < y + thumbH) {
                DraggableWindow w = windows.get(i);
                if (w.minimized) w.restore();
                parent.bringToFront(w);
                playClick();
                this.deactivate();
                return true;
            }
        }

        this.deactivate();
        return true;
    }

    private void playClick() {
        try { Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F)); } catch (Exception ignored) {}
    }
}
