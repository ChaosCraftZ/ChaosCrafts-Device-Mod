package net.chaoscraft.chaoscrafts_device_mod.Core.Desktop;

import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DesktopScreen;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Menu.MenuOption;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the desktop context menu options.
 */
public class DesktopMenuBuilder {
    private final DesktopScreen desktop;

    public DesktopMenuBuilder(DesktopScreen desktop) {
        this.desktop = desktop;
    }

    public List<MenuOption> buildOptions() {
        List<MenuOption> options = new ArrayList<>();
        addViewOptions(options);
        addSortOptions(options);
        addRefreshOption(options);
        addNewOptions(options);
        addPersonalizationOptions(options);
        addBackgroundOptions(options);
        return options;
    }

    private void addViewOptions(List<MenuOption> options) {
        MenuOption viewOption = new MenuOption("View", () -> {});
        viewOption.submenu.add(new MenuOption("Large icons", () -> desktop.setIconSize(48)));
        viewOption.submenu.add(new MenuOption("Medium icons", () -> desktop.setIconSize(32)));
        viewOption.submenu.add(new MenuOption("Small icons", () -> desktop.setIconSize(24)));
        options.add(viewOption);
    }

    private void addSortOptions(List<MenuOption> options) {
        MenuOption sortOption = new MenuOption("Sort by", () -> {});
        sortOption.submenu.add(new MenuOption("Name", desktop::sortIconsByName));
        sortOption.submenu.add(new MenuOption("Date", desktop::sortIconsByDate));
        sortOption.submenu.add(new MenuOption("Size", desktop::sortIconsBySize));
        options.add(sortOption);
    }

    private void addRefreshOption(List<MenuOption> options) {
        options.add(new MenuOption("Refresh", desktop::refresh));
    }

    private void addNewOptions(List<MenuOption> options) {
        MenuOption newOption = new MenuOption("New", () -> {});
        newOption.submenu.add(new MenuOption("Folder", desktop::createNewFolderOnDesktop));
        newOption.submenu.add(new MenuOption("Text Document", desktop::createNewTextFileOnDesktop));
        options.add(newOption);
    }

    private void addPersonalizationOptions(List<MenuOption> options) {
        options.add(new MenuOption("Display settings", desktop::openSettingsApp));
        options.add(new MenuOption("Personalize", desktop::openSettingsApp));
    }

    private void addBackgroundOptions(List<MenuOption> options) {
        MenuOption background = new MenuOption("Background", () -> {});
        // Pictures: open the Files app so user can navigate to wallpapers folder
        background.submenu.add(new MenuOption("Pictures (open Files)", () -> desktop.openAppSingle("Files", 780, 520)));
        // Solid color submenu with presets
        MenuOption solid = new MenuOption("Solid color", () -> {});
        solid.submenu.add(new MenuOption("Black", () -> net.chaoscraft.chaoscrafts_device_mod.Core.Util.FileSystem.FilesManager.getInstance().setCurrentWallpaperColor(0xFF000000)));
        solid.submenu.add(new MenuOption("White", () -> net.chaoscraft.chaoscrafts_device_mod.Core.Util.FileSystem.FilesManager.getInstance().setCurrentWallpaperColor(0xFFFFFFFF)));
        solid.submenu.add(new MenuOption("Dark gray", () -> net.chaoscraft.chaoscrafts_device_mod.Core.Util.FileSystem.FilesManager.getInstance().setCurrentWallpaperColor(0xFF2B2B2B)));
        solid.submenu.add(new MenuOption("Blue", () -> net.chaoscraft.chaoscrafts_device_mod.Core.Util.FileSystem.FilesManager.getInstance().setCurrentWallpaperColor(0xFF1E90FF)));
        solid.submenu.add(new MenuOption("Custom (use Settings)", () -> desktop.openSettingsApp()));
        background.submenu.add(solid);
        // Clear wallpaper
        background.submenu.add(new MenuOption("Clear wallpaper", () -> net.chaoscraft.chaoscrafts_device_mod.Core.Util.FileSystem.FilesManager.getInstance().setCurrentWallpaperName(null)));
        options.add(background);
    }
}