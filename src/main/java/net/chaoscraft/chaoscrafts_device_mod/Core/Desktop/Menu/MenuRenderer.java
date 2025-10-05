package net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Handles rendering of desktop context menus and submenus. Can i render YOU? 
 */
public class MenuRenderer {

    public void renderMenu(GuiGraphics guiGraphics, List<MenuOption> options, int x, int y, int mouseX, int mouseY,
                          MenuOption hoveredOption, MenuOption openSubmenu) {
        final int width = MenuConstants.MENU_WIDTH;
        final int height = options.size() * MenuConstants.OPTION_HEIGHT;
        final var font = Minecraft.getInstance().font;

        // Draw background
        guiGraphics.fill(x, y, x + width, y + height, 0xFF2B2B2B);
        guiGraphics.fill(x, y, x + width, y + height, 0xCC000000);

        // Draw options
        for (int i = 0; i < options.size(); i++) {
            MenuOption option = options.get(i);
            int optionY = y + i * MenuConstants.OPTION_HEIGHT;

            boolean isHovered = isMouseOverOption(mouseX, mouseY, x, optionY, width);
            boolean isSubmenuOpen = openSubmenu == option;

            if (isHovered || isSubmenuOpen) {
                guiGraphics.fill(x, optionY, x + width, optionY + MenuConstants.OPTION_HEIGHT, 0x553333FF);
            }

            guiGraphics.drawString(font, Component.literal(option.text), x + MenuConstants.PADDING_X,
                                 optionY + MenuConstants.LABEL_Y_OFFSET, 0xFFFFFFFF, false);

            // Draw arrow if has submenu
            if (!option.submenu.isEmpty()) {
                guiGraphics.drawString(font, Component.literal("▶"), x + width - MenuConstants.ARROW_X_OFFSET,
                                     optionY + MenuConstants.LABEL_Y_OFFSET, 0xFFFFFFFF, false);
            }
        }
    }

    public void renderSubmenu(GuiGraphics guiGraphics, MenuOption parent, int x, int y, int mouseX, int mouseY) {
        int width = MenuConstants.SUBMENU_WIDTH;
        int height = parent.submenu.size() * MenuConstants.OPTION_HEIGHT;

        guiGraphics.fill(x, y, x + width, y + height, 0xFF2B2B2B);
        guiGraphics.fill(x, y, x + width, y + height, 0xCC000000);
        final var font = Minecraft.getInstance().font;

        for (int i = 0; i < parent.submenu.size(); i++) {
            MenuOption option = parent.submenu.get(i);
            int optionY = y + i * MenuConstants.OPTION_HEIGHT;

            boolean isHovered = isMouseOverOption(mouseX, mouseY, x, optionY, width);
            if (isHovered) {
                guiGraphics.fill(x, optionY, x + width, optionY + MenuConstants.OPTION_HEIGHT, 0x553333FF);
            }

            guiGraphics.drawString(font, Component.literal(option.text), x + MenuConstants.PADDING_X,
                                 optionY + MenuConstants.LABEL_Y_OFFSET, 0xFFFFFFFF, false);
        }
    }

    public void render(GuiGraphics guiGraphics, List<MenuOption> options, int x, int y, int mouseX, int mouseY, float partialTick) {
        renderMenu(guiGraphics, options, x, y, mouseX, mouseY, null, null);
    }

    private boolean isMouseOverOption(int mouseX, int mouseY, int x, int optionY, int width) {
        return mouseX >= x && mouseX <= x + width &&
               mouseY >= optionY && mouseY <= optionY + MenuConstants.OPTION_HEIGHT;
    }
}