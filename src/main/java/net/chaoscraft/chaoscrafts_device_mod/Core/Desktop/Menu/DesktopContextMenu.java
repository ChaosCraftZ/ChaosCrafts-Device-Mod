package net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Menu;

import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DesktopScreen;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

public class DesktopContextMenu {
    private final MenuRenderer renderer;
    private final MenuInputHandler inputHandler;
    private final List<MenuOption> options;
    private final int x, y;

    public DesktopContextMenu(DesktopScreen desktop, int x, int y) {
        this.x = x;
        this.y = y;

        if (desktop != null) {
            options = List.of(
                new MenuOption("Refresh", desktop::refreshDesktopIcons),
                new MenuOption("Sort by Name", desktop::sortIconsByName),
                new MenuOption("Sort by Date", desktop::sortIconsByDate),
                new MenuOption("Sort by Size", desktop::sortIconsBySize),
                new MenuOption("New Folder", desktop::createNewFolderOnDesktop),
                new MenuOption("New Text File", desktop::createNewTextFileOnDesktop),
                new MenuOption("Settings", desktop::openSettingsApp)
            );
        } else {
            options = List.of();
        }

        renderer = new MenuRenderer();
        inputHandler = new MenuInputHandler();
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderer.render(guiGraphics, options, x, y, mouseX, mouseY, partialTick);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return inputHandler.mouseClicked(options, x, y, mouseX, mouseY, button);
    }
}