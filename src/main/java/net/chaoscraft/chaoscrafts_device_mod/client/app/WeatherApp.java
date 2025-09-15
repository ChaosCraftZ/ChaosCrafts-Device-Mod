package net.chaoscraft.chaoscrafts_device_mod.client.app;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DraggableWindow;

import java.util.concurrent.atomic.AtomicReference;

public class WeatherApp implements IApp {
    private DraggableWindow window;
    private final AsyncTaskManager asyncManager = AsyncTaskManager.getInstance();
    private final AtomicReference<String> currentWeather = new AtomicReference<>("Loading...");
    private final AtomicReference<String> temperature = new AtomicReference<>("--°C");
    private final AtomicReference<String> location = new AtomicReference<>("Unknown Location");

    @Override
    public void onOpen(DraggableWindow window) {
        this.window = window;
        fetchWeatherData();
    }

    private void fetchWeatherData() {
        asyncManager.submitIOTask(() -> {
            try {
                // Simulate API call delay
                Thread.sleep(1500);

                // In a real implementation, you would fetch from a weather API
                // For now, we'll use mock data
                String[] weatherConditions = {"Sunny", "Cloudy", "Rainy", "Snowy", "Partly Cloudy"};
                String[] temperatures = {"22°C", "18°C", "15°C", "-2°C", "20°C"};

                int randomIndex = (int) (Math.random() * weatherConditions.length);

                asyncManager.executeOnMainThread(() -> {
                    currentWeather.set(weatherConditions[randomIndex]);
                    temperature.set(temperatures[randomIndex]);
                    location.set("New York, NY"); // Default location
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16, ch = r[3] - 40;

        // Header
        guiGraphics.fill(cx, cy, cx + cw, cy + 30, 0xFF2B2B2B);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Weather"), cx + 10, cy + 8, 0xFFFFFFFF, false);

        // Refresh button
        guiGraphics.fill(cx + cw - 80, cy + 5, cx + cw - 10, cy + 25, 0xFF4C7BD1);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Refresh"), cx + cw - 75, cy + 10, 0xFFFFFFFF, false);

        // Weather content
        int contentY = cy + 40;
        guiGraphics.fill(cx, contentY, cx + cw, contentY + ch - 40, 0xFF1E1E1E);

        // Location
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Location: " + location.get()), cx + 10, contentY + 20, 0xFFFFFFFF, false);

        // Temperature (large)
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(temperature.get()), cx + 10, contentY + 50, 0xFFFFFFFF, false);

        // Weather condition
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(currentWeather.get()), cx + 10, contentY + 80, 0xFFFFFFFF, false);

        // Weather icon based on condition
        int iconX = cx + cw - 100;
        int iconY = contentY + 40;
        int iconSize = 60;

        if (currentWeather.get().toLowerCase().contains("sunny")) {
            guiGraphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFFFFFF00); // Yellow sun
        } else if (currentWeather.get().toLowerCase().contains("cloud")) {
            guiGraphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFFCCCCCC); // Gray cloud
        } else if (currentWeather.get().toLowerCase().contains("rain")) {
            guiGraphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF3498DB); // Blue rain
        } else if (currentWeather.get().toLowerCase().contains("snow")) {
            guiGraphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFFFFFFFF); // White snow
        } else {
            guiGraphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF888888); // Default
        }

        // Forecast section
        int forecastY = contentY + 120;
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("5-Day Forecast:"), cx + 10, forecastY, 0xFFFFFFFF, false);

        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri"};
        String[] forecastTemps = {"20°C", "22°C", "19°C", "18°C", "21°C"};
        String[] forecastConditions = {"Sunny", "Partly Cloudy", "Cloudy", "Rainy", "Sunny"};

        int forecastX = cx + 10;
        for (int i = 0; i < 5; i++) {
            int dayX = forecastX + i * ((cw - 20) / 5);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(days[i]), dayX, forecastY + 20, 0xFFFFFFFF, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(forecastTemps[i]), dayX, forecastY + 40, 0xFFFFFFFF, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(forecastConditions[i]), dayX, forecastY + 60, 0xFFFFFFFF, false);
        }
    }

    @Override
    public boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16;

        // Refresh button
        if (mouseRelX >= cx + cw - 80 && mouseRelX <= cx + cw - 10 &&
                mouseRelY >= cy + 5 && mouseRelY <= cy + 25) {
            fetchWeatherData();
            return true;
        }

        return false;
    }

    @Override
    public void tick() {}

    @Override
    public void mouseReleased(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        // Empty implementation
    }

    @Override
    public boolean mouseDragged(DraggableWindow window, double mouseRelX, double mouseRelY, double dx, double dy) {
        return false;
    }

    @Override
    public boolean charTyped(DraggableWindow window, char codePoint, int modifiers) {
        return false;
    }

    @Override
    public boolean keyPressed(DraggableWindow window, int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public boolean onClose(DraggableWindow window) {
        // Allow the app window to close and clear references
        this.window = null;
        return true;
    }
}