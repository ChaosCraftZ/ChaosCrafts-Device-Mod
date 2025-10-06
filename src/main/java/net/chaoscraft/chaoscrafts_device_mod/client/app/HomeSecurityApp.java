package net.chaoscraft.chaoscrafts_device_mod.client.app;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DraggableWindow;

import java.util.concurrent.CopyOnWriteArrayList;

// for future usage
public class HomeSecurityApp implements IApp {
    private DraggableWindow window;
    private Screen currentScreen = Screen.DASHBOARD;
    private final CopyOnWriteArrayList<SecurityDevice> devices = new CopyOnWriteArrayList<>();
    private final AsyncTaskManager asyncManager = AsyncTaskManager.getInstance();

    private enum Screen {
        DASHBOARD, DEVICES, SETTINGS, HISTORY
    }

    @Override
    public void onOpen(DraggableWindow window) {
        this.window = window;

        asyncManager.submitIOTask(() -> {
            devices.add(new SecurityDevice("Front Door Camera", "Camera", true));
            devices.add(new SecurityDevice("Back Door Sensor", "Sensor", true));
            devices.add(new SecurityDevice("Living Room Motion", "Motion Sensor", false));
            devices.add(new SecurityDevice("Garage Camera", "Camera", true));

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 32;
        int cw = r[2] - 16, ch = r[3] - 40;

        guiGraphics.fill(cx, cy, cx + cw, cy + 30, 0xFF2B2B2B);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Home Security - " + currentScreen.toString()), cx + 10, cy + 10, 0xFFFFFFFF, false);

        guiGraphics.fill(cx, cy + 30, cx + 150, cy + ch, 0xFF1F1F1F);
        String[] navOptions = {"Dashboard", "Devices", "History", "Settings"};
        for (int i = 0; i < navOptions.length; i++) {
            int optionY = cy + 50 + i * 25;
            boolean selected = currentScreen.ordinal() == i;

            if (selected) {
                guiGraphics.fill(cx, optionY, cx + 150, optionY + 20, 0xFF4C7BD1);
            }

            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(navOptions[i]), cx + 10, optionY + 5,
                    selected ? 0xFFFFFFFF : 0xFFCCCCCC, false);
        }

        switch (currentScreen) {
            case DASHBOARD:
                renderDashboard(guiGraphics, cx, cy, cw, ch);
                break;
            case DEVICES:
                renderDevices(guiGraphics, cx, cy, cw, ch);
                break;
            case HISTORY:
                renderHistory(guiGraphics, cx, cy, cw, ch);
                break;
            case SETTINGS:
                renderSettings(guiGraphics, cx, cy, cw, ch);
                break;
        }
    }

    private void renderDashboard(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch) {
        int contentX = cx + 160;
        int contentY = cy + 40;
        int contentW = cw - 170;
        int contentH = ch - 10;

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Security Overview"), contentX + 10, contentY + 10, 0xFFFFFFFF, false);

        int onlineDevices = (int) devices.stream().filter(d -> d.online).count();
        int totalDevices = devices.size();

        guiGraphics.fill(contentX + 10, contentY + 40, contentX + 160, contentY + 100, 0xFF2B2B2B);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Online Devices"), contentX + 20, contentY + 50, 0xFFFFFFFF, false);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(onlineDevices + "/" + totalDevices), contentX + 20, contentY + 70, 0xFF4C7BD1, false);

        guiGraphics.fill(contentX + 170, contentY + 40, contentX + 320, contentY + 100, 0xFF2B2B2B);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Security Status"), contentX + 180, contentY + 50, 0xFFFFFFFF, false);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Armed"), contentX + 180, contentY + 70, 0xFF57C07D, false);

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Recent Activity"), contentX + 10, contentY + 120, 0xFFFFFFFF, false);
        guiGraphics.fill(contentX + 10, contentY + 130, contentX + contentW - 10, contentY + 200, 0xFF2B2B2B);

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Front Door Camera - Motion detected"), contentX + 20, contentY + 140, 0xFFFFFFFF, false);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("2 minutes ago"), contentX + 20, contentY + 155, 0xFF999999, false);

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("System - Armed"), contentX + 20, contentY + 175, 0xFFFFFFFF, false);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("5 minutes ago"), contentX + 20, contentY + 190, 0xFF999999, false);
    }

    private void renderDevices(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch) {
        int contentX = cx + 160;
        int contentY = cy + 40;
        int contentW = cw - 170;

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Connected Devices"), contentX + 10, contentY + 10, 0xFFFFFFFF, false);

        guiGraphics.fill(contentX + contentW - 120, contentY + 5, contentX + contentW - 10, contentY + 25, 0xFF4C7BD1);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Add Device"), contentX + contentW - 110, contentY + 10, 0xFFFFFFFF, false);

        int deviceY = contentY + 40;
        for (SecurityDevice device : devices) {
            guiGraphics.fill(contentX + 10, deviceY, contentX + contentW - 10, deviceY + 50, 0xFF2B2B2B);

            int statusColor = device.online ? 0xFF57C07D : 0xFFF94144;
            guiGraphics.fill(contentX + 15, deviceY + 10, contentX + 20, deviceY + 40, statusColor);

            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(device.name), contentX + 30, deviceY + 10, 0xFFFFFFFF, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(device.type), contentX + 30, deviceY + 25, 0xFFCCCCCC, false);

            String statusText = device.online ? "Online" : "Offline";
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(statusText), contentX + contentW - 80, deviceY + 18, statusColor, false);

            deviceY += 60;
        }
    }

    private void renderHistory(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch) {
        int contentX = cx + 160;
        int contentY = cy + 40;

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Security History"), contentX + 10, contentY + 10, 0xFFFFFFFF, false);

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("No history records available"), contentX + 10, contentY + 50, 0xFF999999, false);
    }

    private void renderSettings(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch) {
        int contentX = cx + 160;
        int contentY = cy + 40;

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("System Settings"), contentX + 10, contentY + 10, 0xFFFFFFFF, false);

        String[] settings = {"Notifications", "Automation", "Users", "System"};
        int settingY = contentY + 40;

        for (String setting : settings) {
            guiGraphics.fill(contentX + 10, settingY, contentX + cw - 170, settingY + 30, 0xFF2B2B2B);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(setting), contentX + 20, settingY + 8, 0xFFFFFFFF, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("▶"), contentX + cw - 190, settingY + 8, 0xFFFFFFFF, false);
            settingY += 40;
        }
    }

    @Override
    public boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 32;

        if (mouseRelX >= cx && mouseRelX <= cx + 150) {
            for (int i = 0; i < 4; i++) {
                int optionY = cy + 50 + i * 25;
                if (mouseRelY >= optionY && mouseRelY <= optionY + 20) {
                    currentScreen = Screen.values()[i];
                    return true;
                }
            }
        }

        if (currentScreen == Screen.DEVICES) {
            int contentX = cx + 160;
            int contentY = cy + 40;
            int contentW = r[2] - 170 - 16;

            if (mouseRelX >= contentX + contentW - 120 && mouseRelX <= contentX + contentW - 10 &&
                    mouseRelY >= contentY + 5 && mouseRelY <= contentY + 25) {
                asyncManager.submitIOTask(() -> {
                    devices.add(new SecurityDevice("New Device " + (devices.size() + 1), "Sensor", true));
                });
                return true;
            }
        }

        return false;
    }

    @Override public void mouseReleased(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {}
    @Override public boolean mouseDragged(DraggableWindow window, double mouseRelX, double mouseRelY, double dx, double dy) { return false; }
    @Override public boolean charTyped(DraggableWindow window, char codePoint, int modifiers) { return false; }
    @Override public boolean keyPressed(DraggableWindow window, int keyCode, int scanCode, int modifiers) { return false; }
    @Override public boolean onClose(DraggableWindow window) {
        this.window = null;
        return true;
    }

    private static class SecurityDevice {
        String name;
        String type;
        boolean online;

        SecurityDevice(String name, String type, boolean online) {
            this.name = name;
            this.type = type;
            this.online = online;
        }
    }
}
