package net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Components;

import net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager.IconManager;
import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DraggableWindow;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Model.DesktopIcon;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Model.SearchResult;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Utils.DesktopState;

/**
 * Handles search box and results.
 */
public class SearchHandler {
    private final DesktopState state;
    private final WindowManager windowManager;

    public SearchHandler(DesktopState state, WindowManager windowManager) {
        this.state = state;
        this.windowManager = windowManager;
    }

    public void updateSearchResults() {
        state.searchResults.clear();
        String q = state.searchBox.getValue().trim().toLowerCase();
        if (q.isEmpty()) return;

        // Desktop icons
        for (DesktopIcon di : state.desktopIcons) {
            if (di.name.toLowerCase().contains(q)) {
                String key = di.name.contains(".") ? null : normalizeAppNameForIcon(di.name);
                var icon = IconManager.getIconResource(key);
                state.searchResults.add(new SearchResult(toTitleCase(di.name), icon, di.onClick));
            }
        }

        // Open windows / apps
        for (DraggableWindow w : state.openApps) {
            String appNameForMatch = w.appName == null ? "" : w.appName;
            if (appNameForMatch.toLowerCase().contains(q)) {
                String normalized = normalizeAppNameForIcon(appNameForMatch);
                var icon = IconManager.getIconResource(normalized);
                state.searchResults.add(new SearchResult(appNameForMatch, icon, () -> windowManager.bringToFront(w)));
            }
        }
    }

    private static String normalizeAppNameForIcon(String displayName) {
        if (displayName == null) return null;
        String n = displayName;
        if (n.contains(" - ")) n = n.substring(n.lastIndexOf(" - ") + 3);
        return n.trim().toLowerCase();
    }

    private static String toTitleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        String base = s;
        if (base.toLowerCase().endsWith(".txt")) base = base.substring(0, base.length() - 4);
        StringBuilder sb = new StringBuilder();
        boolean capNext = true;
        for (char c : base.toCharArray()) {
            if (Character.isWhitespace(c) || c == '_' || c == '-') {
                sb.append(c);
                capNext = true;
                continue;
            }
            if (capNext) {
                sb.append(Character.toUpperCase(c));
                capNext = false;
            } else sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }
}