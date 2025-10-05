package net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Components;

import net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager.AppRegistry;
import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DesktopScreen;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Model.DesktopIcon;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Utils.DesktopState;
import net.chaoscraft.chaoscrafts_device_mod.Core.Util.FileSystem.FilesManager;

import java.util.List;
import java.util.Locale;

/**
 * Manages desktop icons: loading, rendering, selection, dragging, sorting.
 */
public class DesktopIconManager {
    private final DesktopState state;
    private final DesktopScreen desktopScreen;

    public DesktopIconManager(DesktopState state, DesktopScreen desktopScreen) {
        this.state = state;
        this.desktopScreen = desktopScreen;
    }

    public void refreshDesktopIcons() {
        state.desktopIcons.clear();

        FilesManager manager;
        try {
            manager = FilesManager.getInstance();
        } catch (Exception e) {
            return;
        }
        if (manager == null) {
            return;
        }

        List<FilesManager.DesktopIconState> iconStates = manager.getDesktopIcons();
        AppRegistry registry;
        try {
            registry = AppRegistry.getInstance();
        } catch (Exception e) {
            registry = null;
        }

        for (FilesManager.DesktopIconState iconState : iconStates) {
            if (iconState == null || iconState.name == null) {
                continue;
            }
            final String iconName = iconState.name;
            final boolean isApp = registry != null && registry.isInstalled(iconName);
            state.desktopIcons.add(new DesktopIcon(iconName, iconState.x, iconState.y, () -> {
                if (isApp) {
                    desktopScreen.openAppSingle(iconName, 900, 600);
                } else if (iconName.toLowerCase(Locale.ROOT).endsWith(".txt")) {
                    desktopScreen.openAppSingle("notepad", 800, 600);
                } else {
                    desktopScreen.openAppSingle("files", 780, 520);
                }
            }));
        }
        fixIconOverlaps();
        // applyResponsiveLayout(); // Layout handled by DesktopScreen
    }

    public void setIconSize(int size) {
        state.iconSize = size;
        for (DesktopIcon icon : state.desktopIcons) {
            icon.iconSize = size;
        }
        // applyResponsiveLayout();
    }

    public void sortIconsByName() {
        state.desktopIcons.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        arrangeIconsInGrid();
    }

    public void sortIconsByDate() {
        sortIconsByName(); // Placeholder
    }

    public void sortIconsBySize() {
        sortIconsByName(); // Placeholder
    }

    private void arrangeIconsInGrid() {
        arrangeIconsInGrid(800); // Default width
    }

    private void arrangeIconsInGrid(int width) {
        int cols = Math.max(1, (width - 100) / (state.iconSize + 80));
        int x = 50;
        int y = 60;
        int col = 0;
        FilesManager manager;
        try {
            manager = FilesManager.getInstance();
        } catch (Exception e) {
            manager = null;
        }
        for (DesktopIcon icon : state.desktopIcons) {
            icon.targetX = x;
            icon.targetY = y;
            col++;
            if (col >= cols) {
                col = 0;
                x = 50;
                y += state.iconSize + 40;
            } else {
                x += state.iconSize + 80;
            }
            if (manager != null) {
                manager.updateDesktopIconPosition(icon.name, icon.targetX, icon.targetY);
            }
        }
    }

    public boolean isPositionOccupied(int x, int y, DesktopIcon exclude) {
        for (DesktopIcon icon : state.desktopIcons) {
            if (icon != exclude && icon.targetX == x && icon.targetY == y) {
                return true;
            }
        }
        return false;
    }

    private void fixIconOverlaps() {
        for (int i = 0; i < state.desktopIcons.size(); i++) {
            DesktopIcon icon = state.desktopIcons.get(i);
            if (icon == null) continue;
            int originalX = icon.targetX;
            int originalY = icon.targetY;
            while (isPositionOccupied(icon.targetX, icon.targetY, icon)) {
                icon.targetX += 80;
                if (icon.targetX > 700) {
                    icon.targetX = 200;
                    icon.targetY += 80;
                }
            }
            if (icon.targetX != originalX || icon.targetY != originalY) {
                icon.displayX = icon.targetX;
                icon.displayY = icon.targetY;
                FilesManager manager;
                try {
                    manager = FilesManager.getInstance();
                } catch (Exception e) {
                    manager = null;
                }
                if (manager != null) {
                    manager.updateDesktopIconPosition(icon.name, icon.targetX, icon.targetY);
                }
            }
        }
    }

    public void selectAllIcons() {
        state.selectedIcons.clear();
        state.selectedIcons.addAll(state.desktopIcons);
    }

    public void copySelectedIcons() {
        state.clipboardIcons.clear();
        state.clipboardIcons.addAll(state.selectedIcons);
        state.clipboardCut = false;
    }

    public void cutSelectedIcons() {
        state.clipboardIcons.clear();
        state.clipboardIcons.addAll(state.selectedIcons);
        state.clipboardCut = true;
    }

    public void pasteFromClipboard() {
        if (state.clipboardIcons.isEmpty()) return;

        // Find a good position to paste the icons
        int baseX = 200;
        int baseY = 100;
        int offset = 0;

        for (DesktopIcon icon : state.clipboardIcons) {
            // Create a copy of the icon at a new position
            DesktopIcon newIcon = new DesktopIcon(icon.name, baseX + offset, baseY + offset, icon.onClick);
            state.desktopIcons.add(newIcon);
            offset += 20; // Slight offset for each pasted icon
        }

        // If this was a cut operation, remove the original icons
        if (state.clipboardCut) {
            state.desktopIcons.removeAll(state.clipboardIcons);
            state.selectedIcons.removeAll(state.clipboardIcons);
            state.clipboardIcons.clear();
            state.clipboardCut = false;
        }
    }

    public void createNewFolderOnDesktop() {
        // Find an available position for the new icon
        int x = 200;
        int y = 100;
        while (isPositionOccupied(x, y, null)) {
            x += 80;
            if (x > 700) {
                x = 200;
                y += 80;
            }
        }

        // Create a desktop icon for the new folder
        DesktopIcon newIcon = new DesktopIcon("New Folder", x, y, () -> {
            desktopScreen.openAppSingle("Files", 780, 520);
        });
        state.desktopIcons.add(newIcon);

        // Also create the actual folder in the virtual filesystem
        FilesManager manager;
        try {
            manager = FilesManager.getInstance();
        } catch (Exception e) {
            manager = null;
        }
        if (manager != null) {
            manager.createFile("/Desktop", "New Folder", true);
            manager.addDesktopIcon("New Folder", x, y);
        }
    }

    public void createNewTextFileOnDesktop() {
        // Find an available position for the new icon
        int x = 200;
        int y = 100;
        while (isPositionOccupied(x, y, null)) {
            x += 80;
            if (x > 700) {
                x = 200;
                y += 80;
            }
        }

        // Create a desktop icon for the new text file
        DesktopIcon newIcon = new DesktopIcon("New Text Document.txt", x, y, () -> {
            desktopScreen.openAppSingle("notepad", 800, 600);
        });
        state.desktopIcons.add(newIcon);

        // Also create the actual file in the virtual filesystem
        FilesManager manager;
        try {
            manager = FilesManager.getInstance();
        } catch (Exception e) {
            manager = null;
        }
        if (manager != null) {
            manager.createFileWithContent("/Desktop", "New Text Document.txt", "");
            manager.addDesktopIcon("New Text Document.txt", x, y);
        }
    }
}