package net.chaoscraft.chaoscrafts_device_mod.Client.Apps.BaseApps;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager.IApp;
import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DraggableWindow;
import net.chaoscraft.chaoscrafts_device_mod.Core.AsyncorAPI.AsyncRuntime;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

public class WeatherApp implements IApp {
    private final AsyncRuntime asyncRuntime = AsyncRuntime.get();
    private final AtomicReference<String> currentWeather = new AtomicReference<>("Loading...");
    private final AtomicReference<String> temperature = new AtomicReference<>("--°C");
    private final AtomicReference<String> location = new AtomicReference<>("Unknown Location");

    @Override
    public void onOpen(DraggableWindow window) {
        fetchWeatherData();
    }

    private void fetchWeatherData() {
        asyncRuntime.submitCompute(() -> {
            try {
                // Get location from IP
                URI locationUri = new URI("https://ipinfo.io/json");
                URL locationUrl = locationUri.toURL();
                HttpURLConnection locConn = (HttpURLConnection) locationUrl.openConnection();
                locConn.setRequestMethod("GET");
                locConn.setRequestProperty("User-Agent", "MinecraftMod/1.0");
                locConn.setConnectTimeout(5000);
                locConn.setReadTimeout(5000);
                BufferedReader locReader = new BufferedReader(new InputStreamReader(locConn.getInputStream()));
                StringBuilder locResponse = new StringBuilder();
                String line;
                while ((line = locReader.readLine()) != null) {
                    locResponse.append(line);
                }
                locReader.close();
                JsonObject locJson = JsonParser.parseString(locResponse.toString()).getAsJsonObject();
                String city = locJson.has("city") && !locJson.get("city").isJsonNull() ? locJson.get("city").getAsString() : "Unknown";
                String region = locJson.has("region") && !locJson.get("region").isJsonNull() ? locJson.get("region").getAsString() : "";
                String locStr = locJson.get("loc").getAsString();
                String[] parts = locStr.split(",");
                double lat = Double.parseDouble(parts[0]);
                double lon = Double.parseDouble(parts[1]);

                // Get weather
                URL weatherUrl = new URL("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current_weather=true");
                HttpURLConnection weatherConn = (HttpURLConnection) weatherUrl.openConnection();
                weatherConn.setRequestMethod("GET");
                weatherConn.setRequestProperty("User-Agent", "MinecraftMod/1.0");
                weatherConn.setConnectTimeout(5000);
                weatherConn.setReadTimeout(5000);
                BufferedReader weatherReader = new BufferedReader(new InputStreamReader(weatherConn.getInputStream()));
                StringBuilder weatherResponse = new StringBuilder();
                while ((line = weatherReader.readLine()) != null) {
                    weatherResponse.append(line);
                }
                weatherReader.close();
                JsonObject weatherJson = JsonParser.parseString(weatherResponse.toString()).getAsJsonObject();
                JsonObject current = weatherJson.getAsJsonObject("current_weather");
                double temp = current.get("temperature").getAsDouble();
                int weathercode = current.get("weathercode").getAsInt();
                String condition = getWeatherCondition(weathercode);

                asyncRuntime.runOnClientThread(() -> {
                    location.set(city + (region.isEmpty() ? "" : ", " + region));
                    temperature.set(temp + "°C");
                    currentWeather.set(condition);
                });
            } catch (Exception e) {
                asyncRuntime.runOnClientThread(() -> {
                    currentWeather.set("Error loading weather");
                    temperature.set("--°C");
                    location.set("Unknown Location");
                });
            }
        });
    }

    private String getWeatherCondition(int code) {
        switch (code) {
            case 0: return "Clear sky";
            case 1: case 2: case 3: return "Partly cloudy";
            case 45: case 48: return "Foggy";
            case 51: case 53: case 55: return "Drizzle";
            case 56: case 57: return "Freezing Drizzle";
            case 61: case 63: case 65: return "Rain";
            case 66: case 67: return "Freezing Rain";
            case 71: case 73: case 75: return "Snow";
            case 77: return "Snow grains";
            case 80: case 81: case 82: return "Rain showers";
            case 85: case 86: return "Snow showers";
            case 95: return "Thunderstorm";
            case 96: case 99: return "Thunderstorm with hail";
            default: return "Unknown";
        }
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
        return true;
    }
}