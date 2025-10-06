package net.chaoscraft.chaoscrafts_device_mod.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class DesktopContextMenu {
    private final DesktopScreen desktop;
    private final int x;
    private final int y;
    private final List<MenuOption> options = new ArrayList<>();
    private MenuOption hoveredOption = null;
    private MenuOption hoveredSubmenuOption = null;
    private MenuOption openSubmenu = null;
    private long openSubmenuTime = 0;

    public DesktopContextMenu(DesktopScreen desktop, int x, int y) {
        this.desktop = desktop;
        this.x = x;
        this.y = y;
        addDesktopOptions();
    }

    public void addDesktopOptions() {
        MenuOption viewOption = new MenuOption("View", () -> {});
        viewOption.submenu.add(new MenuOption("Large icons", () -> desktop.setIconSize(48)));
        viewOption.submenu.add(new MenuOption("Medium icons", () -> desktop.setIconSize(32)));
        viewOption.submenu.add(new MenuOption("Small icons", () -> desktop.setIconSize(24)));
        options.add(viewOption);

        MenuOption sortOption = new MenuOption("Sort by", () -> {});
        sortOption.submenu.add(new MenuOption("Name", desktop::sortIconsByName));
        sortOption.submenu.add(new MenuOption("Date", desktop::sortIconsByDate));
        sortOption.submenu.add(new MenuOption("Size", desktop::sortIconsBySize));
        options.add(sortOption);

        options.add(new MenuOption("Refresh", desktop::refresh));

        MenuOption newOption = new MenuOption("New", () -> {});
        newOption.submenu.add(new MenuOption("Folder", desktop::createNewFolderOnDesktop));
        newOption.submenu.add(new MenuOption("Text Document", desktop::createNewTextFileOnDesktop));
        options.add(newOption);

        options.add(new MenuOption("Display settings", desktop::openSettingsApp));
        options.add(new MenuOption("Personalize", desktop::openSettingsApp));

        MenuOption background = new MenuOption("Background", () -> {});
        background.submenu.add(new MenuOption("Pictures (open Files)", () -> desktop.openAppSingle("Files", 780, 520)));
        MenuOption solid = new MenuOption("Solid color", () -> {});
        solid.submenu.add(new MenuOption("Black", () -> net.chaoscraft.chaoscrafts_device_mod.client.fs.FilesManager.getInstance().setCurrentWallpaperColor(0xFF000000)));
        solid.submenu.add(new MenuOption("White", () -> net.chaoscraft.chaoscrafts_device_mod.client.fs.FilesManager.getInstance().setCurrentWallpaperColor(0xFFFFFFFF)));
        solid.submenu.add(new MenuOption("Dark gray", () -> net.chaoscraft.chaoscrafts_device_mod.client.fs.FilesManager.getInstance().setCurrentWallpaperColor(0xFF2B2B2B)));
        solid.submenu.add(new MenuOption("Blue", () -> net.chaoscraft.chaoscrafts_device_mod.client.fs.FilesManager.getInstance().setCurrentWallpaperColor(0xFF1E90FF)));
        solid.submenu.add(new MenuOption("Custom (use Settings)", () -> desktop.openSettingsApp()));
        background.submenu.add(solid);
        background.submenu.add(new MenuOption("Clear wallpaper", () -> net.chaoscraft.chaoscrafts_device_mod.client.fs.FilesManager.getInstance().setCurrentWallpaperName(null)));
        options.add(background);
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int width = 180;
        int height = options.size() * 20;

        guiGraphics.fill(x, y, x + width, y + height, 0xFF2B2B2B);
        guiGraphics.fill(x, y, x + width, y + height, 0xCC000000);

        hoveredOption = null;
        hoveredSubmenuOption = null;

        boolean mouseOverMenu = false;

        for (int i = 0; i < options.size(); i++) {
            MenuOption option = options.get(i);
            int optionY = y + i * 20;

            boolean isHovered = mouseX >= x && mouseX <= x + width &&
                    mouseY >= optionY && mouseY <= optionY + 20;

            if (isHovered) {
                hoveredOption = option;
                mouseOverMenu = true;

                if (!option.submenu.isEmpty() && openSubmenu != option) {
                    if (System.currentTimeMillis() - openSubmenuTime > 300) {
                        openSubmenu = option;
                        openSubmenuTime = System.currentTimeMillis();
                    }
                }

                guiGraphics.fill(x, optionY, x + width, optionY + 20, 0x553333FF);
            }

            if (openSubmenu == option) {
                guiGraphics.fill(x, optionY, x + width, optionY + 20, 0x553333FF);
            }

            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(option.text), x + 5, optionY + 6, 0xFFFFFFFF, false);

            if (!option.submenu.isEmpty()) {
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("▶"), x + width - 15, optionY + 6, 0xFFFFFFFF, false);
            }
        }

        if (openSubmenu != null) {
            int optionIndex = options.indexOf(openSubmenu);
            if (optionIndex >= 0) {
                int optionY = y + optionIndex * 20;
                renderSubmenu(guiGraphics, openSubmenu, x + width, optionY, mouseX, mouseY);

                if (mouseX >= x + width && mouseX <= x + width + 150 &&
                        mouseY >= optionY && mouseY <= optionY + openSubmenu.submenu.size() * 20) {
                    mouseOverMenu = true;
                }
            }
        }

        if (!mouseOverMenu && openSubmenu != null) {
            if (System.currentTimeMillis() - openSubmenuTime > 500) {
                openSubmenu = null;
            }
        }
    }

    private void renderSubmenu(GuiGraphics guiGraphics, MenuOption parent, int x, int y, int mouseX, int mouseY) {
        int width = 150;
        int height = parent.submenu.size() * 20;

        guiGraphics.fill(x, y, x + width, y + height, 0xFF2B2B2B);
        guiGraphics.fill(x, y, x + width, y + height, 0xCC000000);

        for (int i = 0; i < parent.submenu.size(); i++) {
            MenuOption option = parent.submenu.get(i);
            int optionY = y + i * 20;

            boolean isHovered = mouseX >= x && mouseX <= x + width &&
                    mouseY >= optionY && mouseY <= optionY + 20;

            if (isHovered) {
                hoveredSubmenuOption = option;
                guiGraphics.fill(x, optionY, x + width, optionY + 20, 0x553333FF);
            }

            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(option.text), x + 5, optionY + 6, 0xFFFFFFFF, false);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = 0; i < options.size(); i++) {
            MenuOption option = options.get(i);
            int optionY = y + i * 20;

            if (mouseX >= x && mouseX <= x + 180 && mouseY >= optionY && mouseY <= optionY + 20) {
                if (option.action != null && option.submenu.isEmpty()) {
                    option.action.run();
                    return true;
                }
                return true;
            }
        }

        if (openSubmenu != null) {
            int optionIndex = options.indexOf(openSubmenu);
            if (optionIndex >= 0) {
                int optionY = y + optionIndex * 20;

                if (mouseX >= x + 180 && mouseX <= x + 330 &&
                        mouseY >= optionY && mouseY <= optionY + openSubmenu.submenu.size() * 20) {

                    int subIndex = (int) ((mouseY - optionY) / 20);
                    if (subIndex >= 0 && subIndex < openSubmenu.submenu.size()) {
                        MenuOption subOption = openSubmenu.submenu.get(subIndex);
                        if (subOption.action != null) {
                            subOption.action.run();
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        if (mouseX >= x && mouseX <= x + 180 && mouseY >= y && mouseY <= y + options.size() * 20) {
            return true;
        }

        if (openSubmenu != null) {
            int optionIndex = options.indexOf(openSubmenu);
            if (optionIndex >= 0) {
                int optionY = y + optionIndex * 20;
                if (mouseX >= x + 180 && mouseX <= x + 330 &&
                        mouseY >= optionY && mouseY <= optionY + openSubmenu.submenu.size() * 20) {
                    return true;
                }
            }
        }

        return false;
    }

    private static class MenuOption {
        String text;
        Runnable action;
        List<MenuOption> submenu = new ArrayList<>();

        MenuOption(String text, Runnable action) {
            this.text = text;
            this.action = action;
        }
    }
}