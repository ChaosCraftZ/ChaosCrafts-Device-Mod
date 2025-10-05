package net.chaoscraft.chaoscrafts_device_mod.Client.Screen;

import java.util.List;

import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.*;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Menu.*;

/**
 * Desktop context menu implementation.
 */
public class DesktopContextMenu {
    private final int x;
    private final int y;
    private final List<MenuOption> options;

    // transient UI state. What UI state now????
    private MenuOption hoveredOption = null;
    private MenuOption hoveredSubmenuOption = null;
    private MenuOption openSubmenu = null;
    private long openSubmenuTime = 0L;

    private final MenuRenderer renderer = new MenuRenderer();
    private final MenuInputHandler inputHandler = new MenuInputHandler();

    public DesktopContextMenu(DesktopScreen desktop, int x, int y) {
        this.x = x;
        this.y = y;
        this.options = new DesktopMenuBuilder(desktop).buildOptions();
    }

    public void render(net.minecraft.client.gui.GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Reset hover states
        hoveredOption = null;
        hoveredSubmenuOption = null;

        // Check if mouse is over any option
        boolean mouseOverMenu = false;

        // Render main menu
        renderer.renderMenu(guiGraphics, options, x, y, mouseX, mouseY, hoveredOption, openSubmenu);

        // Update hover states
        for (int i = 0; i < options.size(); i++) {
            MenuOption option = options.get(i);
            int optionY = y + i * MenuConstants.OPTION_HEIGHT;

            boolean isHovered = mouseX >= x && mouseX <= x + MenuConstants.MENU_WIDTH &&
                    mouseY >= optionY && mouseY <= optionY + MenuConstants.OPTION_HEIGHT;

            if (isHovered) {
                hoveredOption = option;
                mouseOverMenu = true;

                // Open submenu on hover with a slight delay
                if (!option.submenu.isEmpty() && openSubmenu != option) {
                    if (System.currentTimeMillis() - openSubmenuTime > MenuConstants.OPEN_SUBMENU_DELAY_MS) {
                        openSubmenu = option;
                        openSubmenuTime = System.currentTimeMillis();
                    }
                }
            }
        }

        // Show submenu if one is open
        if (openSubmenu != null) {
            int optionIndex = options.indexOf(openSubmenu);
            if (optionIndex >= 0) {
                int optionY = y + optionIndex * MenuConstants.OPTION_HEIGHT;
                renderer.renderSubmenu(guiGraphics, openSubmenu, x + MenuConstants.MENU_WIDTH, optionY, mouseX, mouseY);

                // Check if mouse is over submenu
                if (mouseX >= x + MenuConstants.MENU_WIDTH && mouseX <= x + MenuConstants.MENU_WIDTH + MenuConstants.SUBMENU_WIDTH &&
                        mouseY >= optionY && mouseY <= optionY + openSubmenu.submenu.size() * MenuConstants.OPTION_HEIGHT) {
                    mouseOverMenu = true;
                }
            }
        }

        // Close submenu if mouse is not over menu
        if (!mouseOverMenu && openSubmenu != null) {
            if (System.currentTimeMillis() - openSubmenuTime > MenuConstants.CLOSE_SUBMENU_DELAY_MS) {
                openSubmenu = null;
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return inputHandler.handleMouseClick(options, x, y, mouseX, mouseY, openSubmenu);
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return inputHandler.isMouseOverMenu(options, x, y, mouseX, mouseY, openSubmenu);
    }

    /**
     * Returns the currently hovered top-level option's text or null if none.
     */
    public String getHoveredOptionText() {
        return hoveredOption == null ? null : hoveredOption.text;
    }

    /**
     * Returns the currently hovered submenu option's text or null if none.
     */
    public String getHoveredSubmenuOptionText() {
        return hoveredSubmenuOption == null ? null : hoveredSubmenuOption.text;
    }
}