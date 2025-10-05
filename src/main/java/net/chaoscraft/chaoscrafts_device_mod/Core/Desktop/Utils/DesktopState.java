package net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Utils;

import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DraggableWindow;
import net.chaoscraft.chaoscrafts_device_mod.Client.Sound.LaptopFanLoopSound;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Menu.DesktopContextMenu;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Model.DesktopIcon;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Model.LoadingParticle;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Model.SearchResult;
import net.minecraft.client.gui.components.EditBox;

import java.util.*;

/**
 * Holds the state variables for the desktop.
 */
public class DesktopState {
    // Core UI components
    public EditBox searchBox;
    public final List<DesktopIcon> desktopIcons = new ArrayList<>();
    public final List<DraggableWindow> openApps = new ArrayList<>();
    public int desktopTextColor = 0xFFFFFFFF;
    public int desktopTextShadowColor = 0xAA000000;
    // Icon interaction state
    public boolean selecting = false;
    public int selectStartX, selectStartY, selectEndX, selectEndY;
    public final LinkedHashSet<DesktopIcon> selectedIcons = new LinkedHashSet<>();
    public DesktopIcon iconPressed = null;
    public boolean iconDragging = false;
    public double iconDragStartX = 0, iconDragStartY = 0;
    public final Map<DesktopIcon, int[]> iconStartPositions = new HashMap<>();
    public long lastClickTime = 0;

    // Search and context menu
    public final List<SearchResult> searchResults = new ArrayList<>();
    public DesktopContextMenu contextMenu = null;
    public int iconSize = DesktopConstants.DEFAULT_ICON_SIZE;

    // Loading overlay state
    public boolean showLoadingOverlay = true;
    public final long loadingStartMillis = System.currentTimeMillis();
    public final List<LoadingParticle> loadingParticles = new ArrayList<>();
    public float currentLoadingProgress = 0f;
    public long lastRenderMillis = System.currentTimeMillis();

    // Debug and utilities
    public boolean showDebugInfo = false;

    // Input state
    public boolean ctrlPressed = false;
    public boolean shiftPressed = false;

    // Renaming state
    public DesktopIcon renamingIcon = null;
    public EditBox renameBox = null;

    // Clipboard state for desktop icons
    public final List<DesktopIcon> clipboardIcons = new ArrayList<>();
    public boolean clipboardCut = false;

    // Sound and network state
    public LaptopFanLoopSound fanLoopSound;
    public long lastKeyTypeMillis = 0;
    public boolean fanStarted = false;
    public double originalGuiScale;
    public boolean guiScaleForced = false;
    public int lastScaledWidth = -1;
    public int lastScaledHeight = -1;
    public boolean handlingDynamicResize = false;
}