package net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Menu;

import java.util.List;

/**
 * Handles input events for desktop context menus.
 */
public class MenuInputHandler {
    public boolean handleMouseClick(List<MenuOption> options, int x, int y, double mouseX, double mouseY,
                                   MenuOption openSubmenu) {
        // Check main menu options
        for (int i = 0; i < options.size(); i++) {
            MenuOption option = options.get(i);
            int optionY = y + i * MenuConstants.OPTION_HEIGHT;

            if (isMouseOverOption(mouseX, mouseY, x, optionY, MenuConstants.MENU_WIDTH)) {
                if (option.action != null && option.submenu.isEmpty()) {
                    option.action.run();
                    return true;
                }
                // Options with submenus don't have immediate actions
                return true;
            }
        }

        // Check submenus
        if (openSubmenu != null) {
            int optionIndex = options.indexOf(openSubmenu);
            if (optionIndex >= 0) {
                int optionY = y + optionIndex * MenuConstants.OPTION_HEIGHT;

                if (isMouseOverOption(mouseX, mouseY, x + MenuConstants.MENU_WIDTH, optionY, MenuConstants.SUBMENU_WIDTH)) {
                    int subIndex = (int) ((mouseY - optionY) / MenuConstants.OPTION_HEIGHT);
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

    public boolean isMouseOverMenu(List<MenuOption> options, int x, int y, double mouseX, double mouseY,
                                  MenuOption openSubmenu) {
        // Check main menu
        if (isMouseOverOption(mouseX, mouseY, x, y, MenuConstants.MENU_WIDTH) &&
            mouseY <= y + options.size() * MenuConstants.OPTION_HEIGHT) {
            return true;
        }

        // Check submenu if open
        if (openSubmenu != null) {
            int optionIndex = options.indexOf(openSubmenu);
            if (optionIndex >= 0) {
                int optionY = y + optionIndex * MenuConstants.OPTION_HEIGHT;
                if (isMouseOverOption(mouseX, mouseY, x + MenuConstants.MENU_WIDTH, optionY, MenuConstants.SUBMENU_WIDTH) &&
                    mouseY <= optionY + openSubmenu.submenu.size() * MenuConstants.OPTION_HEIGHT) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean mouseClicked(List<MenuOption> options, int x, int y, double mouseX, double mouseY, int button) {
        return handleMouseClick(options, x, y, mouseX, mouseY, null);
    }

    private boolean isMouseOverOption(double mouseX, double mouseY, int x, int optionY, int width) {
        return mouseX >= x && mouseX <= x + width &&
               mouseY >= optionY && mouseY <= optionY + MenuConstants.OPTION_HEIGHT;
    }
}