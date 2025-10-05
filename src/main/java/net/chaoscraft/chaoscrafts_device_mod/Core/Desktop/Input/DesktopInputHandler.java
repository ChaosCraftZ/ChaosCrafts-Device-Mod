package net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Input;

import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DesktopScreen;
import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DraggableWindow;
import net.chaoscraft.chaoscrafts_device_mod.Client.Sound.LaptopKeySoundManager;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Components.WindowManager;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Model.DesktopIcon;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Utils.DesktopConstants;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Utils.DesktopState;
import net.chaoscraft.chaoscrafts_device_mod.Core.Util.FileSystem.FilesManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles input events for the desktop.
 */
public class DesktopInputHandler {
    private final DesktopState state;
    private final WindowManager windowManager;
    private final DesktopScreen desktop;

    public DesktopInputHandler(DesktopState state, WindowManager windowManager, DesktopScreen desktop) {
        this.state = state;
        this.windowManager = windowManager;
        this.desktop = desktop;
    }

    public boolean handleMouseClicked(double mouseX, double mouseY, int button, int width, int height) {
        state.searchBox.setWidth(DesktopConstants.SEARCH_BOX_WIDTH);
        state.searchBox.setX(DesktopConstants.SEARCH_BOX_X);
        state.searchBox.setY(height - DesktopConstants.TASKBAR_HEIGHT + 4);

        // Left click -> trackpad sound; Right click -> separate mouse click sound (context menu or other)
        if (button == 0) {
            try { LaptopKeySoundManager.playTrackpadClick(); } catch (Exception ignored) {}
        } else if (button == 1) {
            try { LaptopKeySoundManager.playMouseClick(); } catch (Exception ignored) {}
        }

        if (state.contextMenu != null) {
            if (state.contextMenu.mouseClicked(mouseX, mouseY, button)) return true;
            state.contextMenu = null; return true;
        }

        if (button == 1) {
            boolean clickedOnIcon = false;
            if (state.renamingIcon != null && state.renameBox != null) {
                int rx = Math.round(state.renamingIcon.displayX); int ry = Math.round(state.renamingIcon.displayY + state.renamingIcon.iconSize + 4);
                int rw = Math.max(80, state.renamingIcon.iconSize); int rh = 16;
                if (mouseX >= rx && mouseX <= rx + rw && mouseY >= ry && mouseY <= ry + rh) { state.renameBox.setFocused(true); return true; }
                else {
                    String newName = state.renameBox.getValue().trim();
                    if (!newName.isEmpty() && !newName.equals(state.renamingIcon.name)) {
                        if (!attemptRenameDesktopIcon(state.renamingIcon.name, newName)) {
                            state.renameBox.setValue(state.renamingIcon.name);
                            state.renameBox.setFocused(true);
                            playConflictSound();
                            return true;
                        }
                    }
                    state.renamingIcon = null; state.renameBox = null; return false;
                }
            }
            for (DesktopIcon icon : state.desktopIcons) if (icon.isInside(mouseX, mouseY, state.iconSize)) { clickedOnIcon = true; break; }
            if (!clickedOnIcon) { showContextMenu((int)mouseX, (int)mouseY); return true; }
        }

        int tbY = height - DesktopConstants.TASKBAR_HEIGHT;
        if (!state.searchBox.getValue().isEmpty() && !state.searchResults.isEmpty()) {
            int popupW = Math.max(state.searchBox.getWidth(), 260); int entries = Math.min(state.searchResults.size(), 6); int popupH = entries * DesktopConstants.RESULT_HEIGHT + 6; int popupX = state.searchBox.getX(); int popupY = tbY - popupH - 6; if (popupY < 6) popupY = 6;
            if (mouseX >= popupX && mouseX <= popupX + popupW && mouseY >= popupY && mouseY <= popupY + popupH) {
                int idx = (int)((mouseY - (popupY + 6)) / DesktopConstants.RESULT_HEIGHT);
                if (idx >= 0 && idx < Math.min(state.searchResults.size(), 6)) { state.searchResults.get(idx).action.run(); state.searchBox.setValue(""); state.searchResults.clear(); playClick(); }
                return true;
            }
        }

        DraggableWindow top = windowManager.findWindowAt(mouseX, mouseY, DesktopConstants.TASKBAR_HEIGHT);
        if (top != null) {
            windowManager.bringToFront(top);
            int[] rr = top.getRenderRect(DesktopConstants.TASKBAR_HEIGHT);
            if (mouseY >= rr[1] && mouseY <= rr[1] + 26) { top.handleTitlebarClick(mouseX, mouseY, button, DesktopConstants.TASKBAR_HEIGHT); playClick(); return true; }
            boolean consumed = top.app.mouseClicked(top, mouseX, mouseY, button);
            if (consumed) { playClick(); return true; }
            return true;
        }

        if (mouseY >= tbY && mouseY <= height) {
            if (state.searchBox.mouseClicked(mouseX, mouseY, button)) {
                if (button == 0) playClick();
                return true;
            }

            int trayIconW = 28;
            int trayIconPad = 6;
            java.util.List<String> trayLabels = java.util.Arrays.asList("LAN", "🔊", "Upd", "✉", "12°C ☁", "/\\");

            int iconsTotalW = trayLabels.size() * trayIconW + (trayLabels.size() - 1) * trayIconPad + 8;
            net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
            String timeStr = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"));
            String dateStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy"));
            int timeW = font.width(timeStr);
            int dateW = font.width(dateStr);
            int clockW = Math.max(timeW, dateW) + 12;
            int trayTotalWidth = iconsTotalW + clockW + 12;
            int trayRightPadding = 10;
            int trayLeft = Math.max(DesktopConstants.SEARCH_BOX_X + state.searchBox.getWidth() + 16, width - trayTotalWidth - trayRightPadding);

            int perEntry = 52;
            int startX = DesktopConstants.SEARCH_BOX_X + state.searchBox.getWidth() + 8;
            int available = Math.max(0, trayLeft - startX - 8);
            int maxEntries = Math.max(0, available / perEntry);

            java.util.List<DraggableWindow> visibleWindows = new ArrayList<>();
            if (maxEntries > 0) {
                int start = Math.max(0, state.openApps.size() - maxEntries);
                for (int i = start; i < state.openApps.size(); i++) {
                    DraggableWindow w0 = state.openApps.get(i);
                    if (windowManager.isTaskbarEligible(w0)) {
                        visibleWindows.add(w0);
                    }
                }
            }

            int tx = startX;
            for (DraggableWindow w0 : visibleWindows) {
                int x0 = tx;
                int x1 = tx + perEntry - 8;
                int y0 = tbY + 2;
                int y1 = height - 4;
                if (mouseX >= x0 && mouseX <= x1 && mouseY >= y0 && mouseY <= y1) {
                    if (button == 0) {
                        if (w0.minimized) {
                            w0.restore();
                            windowManager.bringToFront(w0);
                        } else {
                            DraggableWindow topWindow = state.openApps.isEmpty() ? null : state.openApps.get(state.openApps.size() - 1);
                            if (topWindow == w0) {
                                int[] rect = w0.getRenderRect(DesktopConstants.TASKBAR_HEIGHT);
                                double minX = rect[0] + rect[2] - 38;
                                double minY = rect[1] + 12;
                                w0.handleTitlebarClick(minX, minY, 0, DesktopConstants.TASKBAR_HEIGHT);
                            } else {
                                windowManager.bringToFront(w0);
                            }
                        }
                        playClick();
                    }
                    return true;
                }
                tx += perEntry;
            }

            // No specific element consumed the click but keep it from hitting desktop
            return true;
        }

        for (DesktopIcon di : state.desktopIcons) {
            if (di.isInside(mouseX, mouseY, state.iconSize)) {
                long now = System.currentTimeMillis();
                if (state.selectedIcons.contains(di) && (now - state.lastClickTime) < DesktopConstants.DOUBLE_CLICK_MS) { di.onClick.run(); state.selectedIcons.clear(); state.iconPressed = null; state.iconDragging = false; playClick(); }
                else { state.selectedIcons.clear(); state.selectedIcons.add(di); state.iconPressed = di; state.iconDragging = false; state.iconDragStartX = mouseX; state.iconDragStartY = mouseY; state.iconStartPositions.clear(); for (DesktopIcon ic : state.selectedIcons) state.iconStartPositions.put(ic, new int[]{ic.targetX, ic.targetY}); state.lastClickTime = now; }
                return true;
            }
        }

        state.selectedIcons.clear(); state.iconPressed = null; state.iconDragging = false; state.selecting = true; state.selectStartX = state.selectEndX = (int)mouseX; state.selectStartY = state.selectEndY = (int)mouseY; return true;
    }

    public boolean handleMouseReleased(double mouseX, double mouseY, int button) {
        if (state.iconDragging && !state.selectedIcons.isEmpty()) {
            if (state.selectedIcons.size() > 1) {
                // Handle multiple icons: arrange in grid
                List<DesktopIcon> selected = new ArrayList<>(state.selectedIcons);
                selected.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
                int baseX = selected.get(0).targetX;
                int baseY = selected.get(0).targetY;
                baseX = (baseX / DesktopConstants.ICON_GRID) * DesktopConstants.ICON_GRID;
                baseY = (baseY / DesktopConstants.ICON_GRID) * DesktopConstants.ICON_GRID;
                int cols = (int) Math.ceil(Math.sqrt(selected.size()));
                int index = 0;
                boolean conflict = false;
                for (DesktopIcon ic : selected) {
                    int row = index / cols;
                    int col = index % cols;
                    int newX = baseX + col * (state.iconSize + 10);
                    int newY = baseY + row * (state.iconSize + 10);
                    if (desktop.isPositionOccupied(newX, newY, ic)) {
                        conflict = true;
                        break;
                    }
                    index++;
                }
                if (!conflict) {
                    index = 0;
                    for (DesktopIcon ic : selected) {
                        int row = index / cols;
                        int col = index % cols;
                        ic.targetX = baseX + col * (state.iconSize + 10);
                        ic.targetY = baseY + row * (state.iconSize + 10);
                        desktop.updateIconPosition(ic.name, ic.targetX, ic.targetY);
                        index++;
                    }
                } else {
                    // Revert all to start positions
                    for (DesktopIcon ic : state.selectedIcons) {
                        int[] start = state.iconStartPositions.get(ic);
                        if (start != null) {
                            ic.targetX = start[0];
                            ic.targetY = start[1];
                        }
                    }
                    playConflictSound();
                }
            } else {
                // Single icon
                DesktopIcon ic = state.selectedIcons.iterator().next();
                int[] startPos = state.iconStartPositions.get(ic);
                if (startPos != null) {
                    int originalX = startPos[0];
                    int originalY = startPos[1];
                    ic.targetX = (ic.targetX / DesktopConstants.ICON_GRID) * DesktopConstants.ICON_GRID;
                    ic.targetY = (ic.targetY / DesktopConstants.ICON_GRID) * DesktopConstants.ICON_GRID;
                    if (desktop.isPositionOccupied(ic.targetX, ic.targetY, ic)) {
                        // Revert to original position
                        ic.targetX = originalX;
                        ic.targetY = originalY;
                        playConflictSound();
                    } else {
                        // Update position
                        desktop.updateIconPosition(ic.name, ic.targetX, ic.targetY);
                    }
                }
            }
        }
        if (state.selecting && !state.iconDragging) {
            int x0 = Math.min(state.selectStartX, state.selectEndX), y0 = Math.min(state.selectStartY, state.selectEndY);
            int x1 = Math.max(state.selectStartX, state.selectEndX), y1 = Math.max(state.selectStartY, state.selectEndY);
            state.selectedIcons.clear();
            for (DesktopIcon ic : state.desktopIcons) {
                if (state.renameBox != null) state.renameBox.mouseReleased(mouseX, mouseY, button);
                int ix0 = Math.round(ic.displayX), iy0 = Math.round(ic.displayY);
                int ix1 = ix0 + state.iconSize, iy1 = iy0 + state.iconSize;
                if (ix1 >= x0 && ix0 <= x1 && iy1 >= y0 && iy0 <= y1) state.selectedIcons.add(ic);
            }
        }
        state.iconDragging = false; state.iconPressed = null; state.iconStartPositions.clear(); state.selecting = false;
        state.searchBox.mouseReleased(mouseX, mouseY, button);
        for (DraggableWindow w : state.openApps) w.mouseReleased(mouseX, mouseY, button);
        return true;
    }

    public boolean handleMouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (state.iconPressed != null && button == 0) {
            if (!state.iconDragging) { state.iconDragging = true; state.iconStartPositions.clear(); for (DesktopIcon ic : state.selectedIcons) state.iconStartPositions.put(ic, new int[]{ic.targetX, ic.targetY}); state.iconDragStartX = mouseX; state.iconDragStartY = mouseY; }
            int deltaX = (int)(mouseX - state.iconDragStartX); int deltaY = (int)(mouseY - state.iconDragStartY);
            for (DesktopIcon ic : state.selectedIcons) { int[] s = state.iconStartPositions.getOrDefault(ic, new int[]{ic.targetX, ic.targetY}); ic.targetX = s[0] + deltaX; ic.targetY = s[1] + deltaY; }
            return true;
        }
        if (state.selecting && !state.iconDragging) {
            state.selectEndX = (int) mouseX; state.selectEndY = (int) mouseY;
            int x0 = Math.min(state.selectStartX, state.selectEndX), y0 = Math.min(state.selectStartY, state.selectEndY);
            int x1 = Math.max(state.selectStartX, state.selectEndX), y1 = Math.max(state.selectStartY, state.selectEndY);
            state.selectedIcons.clear();
            for (DesktopIcon ic : state.desktopIcons) {
                int ix0 = Math.round(ic.displayX), iy0 = Math.round(ic.displayY);
                int ix1 = ix0 + state.iconSize, iy1 = iy0 + state.iconSize;
                if (ix1 >= x0 && ix0 <= x1 && iy1 >= y0 && iy0 <= y1) state.selectedIcons.add(ic);
            }
        }
        DraggableWindow fw = windowManager.findWindowAt(mouseX, mouseY, DesktopConstants.TASKBAR_HEIGHT); if (fw != null) fw.mouseDragged(mouseX, mouseY);
        state.searchBox.mouseDragged(mouseX, mouseY, button, dx, dy);
        return true;
    }

    public boolean handleMouseScrolled(double mouseX, double mouseY, double delta) {
        DraggableWindow top = windowManager.findWindowAt(mouseX, mouseY, DesktopConstants.TASKBAR_HEIGHT);
        if (top != null && !top.minimized) return top.mouseScrolled(mouseX, mouseY, delta);
        return false;
    }

    public boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 341 || keyCode == 345) { state.ctrlPressed = true; return true; }
        if (keyCode == 340 || keyCode == 344) { state.shiftPressed = true; return true; }
        if (keyCode == 32) { // spacebar
            try { LaptopKeySoundManager.playKey(' '); } catch (Exception ignored) {}
            // sendTypingPacketMaybe();
            return true;
        }
        if (state.ctrlPressed) {
            switch (keyCode) {
                case 65: // selectAllIcons(); return true;
                case 67: // copySelectedIcons(); return true;
                case 86: // pasteFromClipboard(); return true;
                case 88: // cutSelectedIcons(); return true;
                case 78: // createNewFolderOnDesktop(); return true;
                case 83: return handleAppSpecificKeybind(keyCode, scanCode, modifiers);
                case 87:
                    if (state.shiftPressed) net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DraggableWindow.closeAllWindows();
                    else if (!state.openApps.isEmpty()) { DraggableWindow top = state.openApps.get(state.openApps.size()-1); if (top != null) { top.requestClose(); playClick(); } }
                    return true;
            }
        }
        if (keyCode == 292) { state.showDebugInfo = !state.showDebugInfo; return true; }
        if (keyCode == 256) { if (state.contextMenu != null) { state.contextMenu = null; return true; } return false; } // Minecraft.getInstance().setScreen(null); return true; }
        if (!state.openApps.isEmpty()) { DraggableWindow top = state.openApps.get(state.openApps.size()-1); if (top != null && !top.minimized) { boolean consumed = top.app.keyPressed(top, keyCode, scanCode, modifiers); if (consumed) return true; } }
        if (state.searchBox.isFocused()) return state.searchBox.keyPressed(keyCode, scanCode, modifiers);
        return false;
    }

    public boolean handleKeyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 341 || keyCode == 345) { state.ctrlPressed = false; return true; }
        if (keyCode == 340 || keyCode == 344) { state.shiftPressed = false; return true; }
        return false;
    }

    public boolean handleCharTyped(char typedChar, int keyCode) {
        if (typedChar != 0 && typedChar != ' ' && !Character.isISOControl(typedChar)) {
            try { LaptopKeySoundManager.playKey(typedChar); } catch (Exception ignored) {}
            // sendTypingPacketMaybe();
        }
        if (!state.openApps.isEmpty()) {
            DraggableWindow top = state.openApps.get(state.openApps.size()-1);
            if (top != null && !top.minimized) {
                boolean consumed = top.app.charTyped(top, typedChar, keyCode);
                if (consumed) return true;
            }
        }
        if (state.searchBox.isFocused()) return state.searchBox.charTyped(typedChar, keyCode);
        return false;
    }

    private boolean handleAppSpecificKeybind(int keyCode, int scanCode, int modifiers) {
        if (!state.openApps.isEmpty()) { DraggableWindow top = state.openApps.get(state.openApps.size()-1); if (top != null && !top.minimized) return top.app.keyPressed(top, keyCode, scanCode, modifiers); }
        return false;
    }

    private void playClick() { net.minecraft.client.Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F)); }

    private void playConflictSound() { net.minecraft.client.Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.chaoscraft.chaoscrafts_device_mod.Client.Sound.ModSounds.ICON_ERROR.get(), 1.0F)); }

    private void showContextMenu(int x, int y) {
        if (desktop != null) {
            desktop.showContextMenu(x, y);
        }
    }

    private boolean attemptRenameDesktopIcon(String oldName, String newName) {
        FilesManager manager;
        try {
            manager = FilesManager.getInstance();
        } catch (Exception e) {
            manager = null;
        }
        if (manager == null) {
            return false;
        }

        if (manager.hasDesktopIcon(newName)) {
            return false;
        }

        boolean renamed = manager.renameNode("/Desktop/" + oldName, newName);
        if (renamed) {
            desktop.refreshDesktopIcons();
            state.selectedIcons.clear();
            for (DesktopIcon icon : state.desktopIcons) {
                if (icon.name.equals(newName)) {
                    state.selectedIcons.add(icon);
                    break;
                }
            }
        }
        return renamed;
    }
}