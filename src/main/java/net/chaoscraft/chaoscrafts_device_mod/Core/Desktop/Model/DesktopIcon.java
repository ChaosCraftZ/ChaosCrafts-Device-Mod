package net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager.IconManager;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Utils.DesktopConstants;

/**
 * Represents a desktop icon with rendering and interaction logic.
 */
public class DesktopIcon {
    public final String name;
    public int targetX, targetY;
    public float displayX, displayY;
    public final Runnable onClick;
    public int iconSize = DesktopConstants.DEFAULT_ICON_SIZE;

    public DesktopIcon(String name, int x, int y, Runnable onClick) {
        this.name = name;
        this.targetX = (x / DesktopConstants.ICON_GRID) * DesktopConstants.ICON_GRID;
        this.targetY = (y / DesktopConstants.ICON_GRID) * DesktopConstants.ICON_GRID;
        this.displayX = this.targetX;
        this.displayY = this.targetY;
        this.onClick = onClick;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, boolean selected, int currentIconSize, int textColor, int shadowColor) {
        this.iconSize = currentIconSize > 0 ? currentIconSize : DesktopConstants.DEFAULT_ICON_SIZE;
        int dx = Math.round(displayX), dy = Math.round(displayY);
        boolean hover = mouseX >= dx && mouseX <= dx + iconSize && mouseY >= dy && mouseY <= dy + iconSize;
        if (selected) g.fill(dx - 6, dy - 6, dx + iconSize + 6, dy + iconSize + 14, 0x2233AAFF);
        g.fill(dx - 1, dy + iconSize + 1, dx + iconSize, dy + iconSize + 3, shadowColor);
        String key = name.contains(".") ? null : normalizeAppNameForIcon(name);
        ResourceLocation iconRes = IconManager.getIconResource(key);
        try { g.blit(iconRes, dx + 2, dy + 2, 0, 0, iconSize - 4, iconSize - 4, iconSize - 4, iconSize - 4); } catch (Exception ignored) {}
        if (hover) g.fill(dx - 2, dy - 2, dx + iconSize + 2, dy + iconSize + 2, 0x22FFFFFF);
        String displayName = toTitleCase(name);
        Font font = Minecraft.getInstance().font;
        if (font.width(displayName) > iconSize + 10) displayName = font.plainSubstrByWidth(displayName, iconSize + 5) + "...";
        g.drawString(font, Component.literal(displayName), dx, dy + iconSize + 4, textColor, false);
    }

    public boolean isInside(double mouseX, double mouseY, int currentIconSize) {
        return mouseX >= displayX && mouseX <= displayX + currentIconSize && mouseY >= displayY && mouseY <= displayY + currentIconSize;
    }

    private static String normalizeAppNameForIcon(String displayName) {
        if (displayName == null) return null;
        String n = displayName;
        if (n.contains(" - ")) n = n.substring(n.lastIndexOf(" - ") + 3);
        return n.trim().toLowerCase();
    }

    private static String toTitleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        String base = s; if (base.toLowerCase().endsWith(".txt")) base = base.substring(0, base.length() - 4);
        StringBuilder sb = new StringBuilder(); boolean capNext = true;
        for (char c : base.toCharArray()) {
            if (Character.isWhitespace(c) || c == '_' || c == '-') { sb.append(c); capNext = true; continue; }
            if (capNext) { sb.append(Character.toUpperCase(c)); capNext = false; } else sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}