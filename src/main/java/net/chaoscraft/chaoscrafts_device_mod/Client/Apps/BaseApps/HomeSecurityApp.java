package net.chaoscraft.chaoscrafts_device_mod.Client.Apps.BaseApps;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager.IApp;
import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DraggableWindow;
import net.chaoscraft.chaoscrafts_device_mod.Core.AsyncorAPI.AsyncRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class HomeSecurityApp implements IApp {
    private Screen currentScreen = Screen.DASHBOARD;
    private final CopyOnWriteArrayList<SecurityDevice> devices = new CopyOnWriteArrayList<>();
    private final AsyncRuntime asyncRuntime = AsyncRuntime.get();

    private enum Screen {
        DASHBOARD, DEVICES, SETTINGS, HISTORY
    }

    @Override
    public void onOpen(DraggableWindow window) {
        // Load devices asynchronously
        asyncRuntime.submitCompute(() -> {
            List<SecurityDevice> loaded = new ArrayList<>();
            loaded.add(new SecurityDevice("Front Door Camera", "Camera", true));
            loaded.add(new SecurityDevice("Back Door Sensor", "Sensor", true));
            loaded.add(new SecurityDevice("Living Room Motion", "Motion Sensor", false));
            loaded.add(new SecurityDevice("Garage Camera", "Camera", true));

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            asyncRuntime.runOnClientThread(() -> {
                devices.clear();
                devices.addAll(loaded);
            });
        });
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 32;
        int cw = r[2] - 16, ch = r[3] - 40;

        // Header
        guiGraphics.fill(cx, cy, cx + cw, cy + 30, 0xFF2B2B2B);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Home Security - " + currentScreen.toString()), cx + 10, cy + 10, 0xFFFFFFFF, false);

        // Navigation sidebar
        guiGraphics.fill(cx, cy + 30, cx + 150, cy + ch, 0xFF1F1F1F);

        // Navigation options
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

        // Main content area
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

        // Dashboard title
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Security Overview"), contentX + 10, contentY + 10, 0xFFFFFFFF, false);

        // Status cards
        int onlineDevices = (int) devices.stream().filter(d -> d.online).count();
        int totalDevices = devices.size();

        // Online devices card
        guiGraphics.fill(contentX + 10, contentY + 40, contentX + 160, contentY + 100, 0xFF2B2B2B);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Online Devices"), contentX + 20, contentY + 50, 0xFFFFFFFF, false);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(onlineDevices + "/" + totalDevices), contentX + 20, contentY + 70, 0xFF4C7BD1, false);

        // Security status card
        guiGraphics.fill(contentX + 170, contentY + 40, contentX + 320, contentY + 100, 0xFF2B2B2B);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Security Status"), contentX + 180, contentY + 50, 0xFFFFFFFF, false);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Armed"), contentX + 180, contentY + 70, 0xFF57C07D, false);

        // Recent activity
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Recent Activity"), contentX + 10, contentY + 120, 0xFFFFFFFF, false);
        guiGraphics.fill(contentX + 10, contentY + 130, contentX + contentW - 10, contentY + 200, 0xFF2B2B2B);

        // Sample activity entries
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Front Door Camera - Motion detected"), contentX + 20, contentY + 140, 0xFFFFFFFF, false);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("2 minutes ago"), contentX + 20, contentY + 155, 0xFF999999, false);

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("System - Armed"), contentX + 20, contentY + 175, 0xFFFFFFFF, false);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("5 minutes ago"), contentX + 20, contentY + 190, 0xFF999999, false);
    }

    private void renderDevices(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch) {
        int contentX = cx + 160;
        int contentY = cy + 40;
        int contentW = cw - 170;

        // Devices title
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Connected Devices"), contentX + 10, contentY + 10, 0xFFFFFFFF, false);

        // Add device button
        guiGraphics.fill(contentX + contentW - 120, contentY + 5, contentX + contentW - 10, contentY + 25, 0xFF4C7BD1);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Add Device"), contentX + contentW - 110, contentY + 10, 0xFFFFFFFF, false);

        // Device list
        int deviceY = contentY + 40;
        for (SecurityDevice device : devices) {
            guiGraphics.fill(contentX + 10, deviceY, contentX + contentW - 10, deviceY + 50, 0xFF2B2B2B);

            // Device status indicator
            int statusColor = device.online ? 0xFF57C07D : 0xFFF94144;
            guiGraphics.fill(contentX + 15, deviceY + 10, contentX + 20, deviceY + 40, statusColor);

            // Device info
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(device.name), contentX + 30, deviceY + 10, 0xFFFFFFFF, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(device.type), contentX + 30, deviceY + 25, 0xFFCCCCCC, false);

            // Online status
            String statusText = device.online ? "Online" : "Offline";
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(statusText), contentX + contentW - 80, deviceY + 18, statusColor, false);

            deviceY += 60;
        }
    }

    private void renderHistory(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch) {
        int contentX = cx + 160;
        int contentY = cy + 40;

        // History title
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Security History"), contentX + 10, contentY + 10, 0xFFFFFFFF, false);

        // Placeholder message
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("No history records available"), contentX + 10, contentY + 50, 0xFF999999, false);
    }

    private void renderSettings(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch) {
        int contentX = cx + 160;
        int contentY = cy + 40;

        // Settings title
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("System Settings"), contentX + 10, contentY + 10, 0xFFFFFFFF, false);

        // Settings options
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

        // Navigation sidebar clicks
        if (mouseRelX >= cx && mouseRelX <= cx + 150) {
            for (int i = 0; i < 4; i++) {
                int optionY = cy + 50 + i * 25;
                if (mouseRelY >= optionY && mouseRelY <= optionY + 20) {
                    currentScreen = Screen.values()[i];
                    return true;
                }
            }
        }

        // Devices screen - Add device button
        if (currentScreen == Screen.DEVICES) {
            int contentX = cx + 160;
            int contentY = cy + 40;
            int contentW = r[2] - 170 - 16;

            if (mouseRelX >= contentX + contentW - 120 && mouseRelX <= contentX + contentW - 10 &&
                    mouseRelY >= contentY + 5 && mouseRelY <= contentY + 25) {
                // Add a new device asynchronously
                asyncRuntime.submitCompute(() -> {
                    SecurityDevice device = new SecurityDevice("New Device " + (devices.size() + 1), "Sensor", true);
                    asyncRuntime.runOnClientThread(() -> devices.add(device));
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
        // Allow the window to close
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
