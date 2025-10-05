package net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Menu;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a menu option with text, action, and optional submenu.
 */
public class MenuOption {
    public final String text;
    public final Runnable action;
    public final List<MenuOption> submenu = new ArrayList<>();

    public MenuOption(String text, Runnable action) {
        this.text = text;
        this.action = action;
    }
}