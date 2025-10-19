package net.chaoscraft.chaoscrafts_device_mod.client.app.messenger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatContextMenu {
    private final MessengerApp app;
    private final int x;
    private final int y;
    private final List<MenuOption> options = new ArrayList<>();
    private MenuOption hovered = null;
    private boolean wasHovered = false;

    public ChatContextMenu(MessengerApp app, int x, int y, UUID targetId, boolean isGroup) {
        this.app = app;
        this.x = x;
        this.y = y;
        buildOptions(targetId, isGroup);
    }

    private void buildOptions(UUID targetId, boolean isGroup) {
        if (isGroup) {
            options.add(new MenuOption("Group Settings", () -> app.openGroupSettings(targetId)));
            options.add(new MenuOption("Leave Group", () -> {
                app.leaveGroup(targetId);
                app.closeContextMenu();
            }));
        } else {
            options.add(new MenuOption("Create Groupchat", () -> app.openCreateGroupWith(targetId)));
            options.add(new MenuOption("Add to Groupchat", () -> app.openAddToGroupFor(targetId)));
            boolean blocked = MessengerNetworkManager.getInstance().isBlocked(targetId);
            if (blocked) options.add(new MenuOption("Unblock", () -> app.toggleBlockContact(targetId)));
            else options.add(new MenuOption("Block", () -> app.toggleBlockContact(targetId)));
        }
    }

    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int width = 160;
        int height = options.size() * 20;

        guiGraphics.fill(x, y, x + width, y + height, 0xFF2B2B2B);
        guiGraphics.fill(x, y, x + width, y + height, 0xCC000000);

        hovered = null;

        for (int i = 0; i < options.size(); i++) {
            MenuOption opt = options.get(i);
            int oy = y + i * 20;
            boolean isHovered = mouseX >= x && mouseX <= x + width && mouseY >= oy && mouseY <= oy + 20;

            if (isHovered) {
                hovered = opt;
                guiGraphics.fill(x, oy, x + width, oy + 20, 0x553333FF);
            }

            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(opt.text), x + 6, oy + 6, 0xFFFFFFFF, false);
        }
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return false;
        int width = 160;
        int height = options.size() * 20;
        if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            int idx = (mouseY - y) / 20;
            if (idx >= 0 && idx < options.size()) {
                MenuOption opt = options.get(idx);
                if (opt.action != null) {
                    opt.action.run();
                    return true;
                }
            }
        }
        return false;
    }

    public boolean contains(int mouseX, int mouseY) {
        int width = 160;
        int height = options.size() * 20;
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static class MenuOption {
        String text;
        Runnable action;

        MenuOption(String text, Runnable action) {
            this.text = text;
            this.action = action;
        }
    }
}