package net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Components;

import net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager.AppFactory;
import net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager.IApp;
import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DraggableWindow;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Utils.DesktopConstants;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Utils.DesktopState;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * Manages open windows: adding, removing, minimizing, bringing to front.
 */
public class WindowManager {
    private final DesktopState state;

    public WindowManager(DesktopState state) {
        this.state = state;
    }

    public void openAppSingle(String name, int requestedWidth, int requestedHeight, int screenWidth, int screenHeight) {
        for (DraggableWindow w0 : state.openApps) {
            if (w0.appName.equalsIgnoreCase(name)) {
                w0.restore();
                w0.removeRequested = false;
                bringToFront(w0);
                return;
            }
        }
        IApp app = AppFactory.create(name);
        if (app == null) return;
        int usableWidth = Math.max(200, screenWidth - 80);
        int usableHeight = Math.max(160, screenHeight - DesktopConstants.TASKBAR_HEIGHT - 40);
        int windowWidth = Math.min(requestedWidth, usableWidth);
        int windowHeight = Math.min(requestedHeight, usableHeight);

        int startX = Math.max(40, (screenWidth - windowWidth) / 2);
        int maxStartX = Math.max(40, screenWidth - windowWidth - 40);
        startX = Math.min(startX, maxStartX);

        int availableHeight = Math.max(windowHeight, screenHeight - DesktopConstants.TASKBAR_HEIGHT);
        int startY = Math.max(20, (availableHeight - windowHeight) / 2);
        int maxStartY = Math.max(0, screenHeight - DesktopConstants.TASKBAR_HEIGHT - windowHeight);
        startY = Math.min(startY, maxStartY);

        DraggableWindow wdw = new DraggableWindow(name, app, windowWidth, windowHeight, startX, startY);
        state.openApps.add(wdw);
        bringToFront(wdw);
    }

    public void bringToFront(DraggableWindow w) {
        state.openApps.remove(w);
        state.openApps.add(w);
    }

    public DraggableWindow findWindowAt(double mouseX, double mouseY, int taskbarHeight) {
        ListIterator<DraggableWindow> it = state.openApps.listIterator(state.openApps.size());
        while (it.hasPrevious()) {
            DraggableWindow w = it.previous();
            if (!w.minimized && w.isInside(mouseX, mouseY, taskbarHeight)) return w;
        }
        return null;
    }

    public void cleanupClosedWindows() {
        List<DraggableWindow> rem = new ArrayList<>();
        for (DraggableWindow w : state.openApps) if (w.removeRequested) rem.add(w);
        state.openApps.removeAll(rem);
    }

    public boolean isTaskbarEligible(DraggableWindow w) {
        if (w == null) return false;
        if (w.removeRequested || w.closing) return false;
        if (w.appName == null) return false;
        // Normalize the display/title to the internal app id used by the registry/icon manager
        String normalized = normalizeAppNameForIcon(w.appName);
        if (normalized == null || normalized.isEmpty()) return false;
        try {
            return net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager.AppRegistry.getInstance().isInstalled(normalized);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String normalizeAppNameForIcon(String displayName) {
        if (displayName == null) return null;
        String n = displayName;
        if (n.contains(" - ")) n = n.substring(n.lastIndexOf(" - ") + 3);
        return n.trim().toLowerCase();
    }
}