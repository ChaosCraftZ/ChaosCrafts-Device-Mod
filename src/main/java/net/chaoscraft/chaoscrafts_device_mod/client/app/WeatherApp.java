package net.chaoscraft.chaoscrafts_device_mod.client.app;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DraggableWindow;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public class WeatherApp implements IApp {
    private DraggableWindow window;
    private final AsyncTaskManager asyncManager = AsyncTaskManager.getInstance();
    private final AtomicReference<String> currentWeather = new AtomicReference<>("Loading...");
    private final AtomicReference<String> temperature = new AtomicReference<>("--°C");
    private final AtomicReference<String> location = new AtomicReference<>("Unknown Location");

    private final AtomicReference<String[]> forecastDays = new AtomicReference<>(new String[] {"Mon","Tue","Wed","Thu","Fri"});
    private final AtomicReference<String[]> forecastTemps = new AtomicReference<>(new String[] {"--°C","--°C","--°C","--°C","--°C"});
    private final AtomicReference<String[]> forecastConditions = new AtomicReference<>(new String[] {"--","--","--","--","--"});

    private static final Gson GSON = new Gson();

    @Override
    public void onOpen(DraggableWindow window) {
        this.window = window;
        fetchWeatherData();
    }

    private void fetchWeatherData() {
        asyncManager.submitIOTask(() -> {
            try {
                JsonObject geo = httpGetJson("https://ipapi.co/json/");
                double lat = Double.NaN;
                double lon = Double.NaN;
                String city = null;
                String region = null;
                String country = null;

                if (geo != null) {
                    try {
                        JsonElement latEl = geo.get("latitude");
                        JsonElement lonEl = geo.get("longitude");
                        if (latEl != null && lonEl != null && !latEl.isJsonNull() && !lonEl.isJsonNull()) {
                            lat = latEl.getAsDouble();
                            lon = lonEl.getAsDouble();
                        }
                        city = getStringSafe(geo, "city");
                        region = getStringSafe(geo, "region");
                        country = getStringSafe(geo, "country_name");
                    } catch (Exception ignored) {}
                }

                if (Double.isNaN(lat) || Double.isNaN(lon)) {
                    JsonObject alt = httpGetJson("https://ipinfo.io/json");
                    if (alt != null) {
                        try {
                            String loc = getStringSafe(alt, "loc");
                            if (loc != null && loc.contains(",")) {
                                String[] parts = loc.split(",");
                                lat = Double.parseDouble(parts[0]);
                                lon = Double.parseDouble(parts[1]);
                            }
                            city = city == null ? getStringSafe(alt, "city") : city;
                            region = region == null ? getStringSafe(alt, "region") : region;
                            country = country == null ? getStringSafe(alt, "country") : country;
                        } catch (Exception ignored) {}
                    }
                }

                if (Double.isNaN(lat) || Double.isNaN(lon)) {
                    applyFallbackData();
                    return;
                }

                final double fLat = lat;
                final double fLon = lon;

                String meteourl = String.format(Locale.ROOT,
                        "https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f&current_weather=true&daily=temperature_2m_max,temperature_2m_min,weathercode&timezone=auto",
                        fLat, fLon);

                JsonObject weatherJson = httpGetJson(meteourl);
                if (weatherJson != null) {
                    try {
                        JsonObject current = weatherJson.getAsJsonObject("current_weather");
                        JsonObject daily = weatherJson.getAsJsonObject("daily");

                        if (current != null) {
                            double temp = current.has("temperature") ? current.get("temperature").getAsDouble() : Double.NaN;
                            int weatherCode = current.has("weathercode") ? current.get("weathercode").getAsInt() : -1;

                            String tempStr = Double.isNaN(temp) ? "--°C" : String.format(Locale.ROOT, "%.0f°C", temp);
                            String condition = weatherCodeToString(weatherCode);

                            final String displayLocation = buildLocationString(city, region, country, fLat, fLon);

                            String[] daysArr = new String[5];
                            String[] tempsArr = new String[5];
                            String[] condArr = new String[5];

                            boolean dailyOk = false;
                            if (daily != null && daily.has("time")) {
                                try {
                                    var times = daily.getAsJsonArray("time");
                                    var tmax = daily.getAsJsonArray("temperature_2m_max");
                                    var tmin = daily.getAsJsonArray("temperature_2m_min");
                                    var wcode = daily.getAsJsonArray("weathercode");

                                    int count = Math.min(5, times.size());
                                    DateTimeFormatter df = DateTimeFormatter.ISO_LOCAL_DATE;
                                    for (int i = 0; i < count; i++) {
                                        String dateStr = times.get(i).getAsString();
                                        LocalDate ld = LocalDate.parse(dateStr, df);
                                        String dayName = ld.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                                        daysArr[i] = dayName;

                                        double maxt = tmax.size() > i ? tmax.get(i).getAsDouble() : Double.NaN;
                                        double mint = tmin.size() > i ? tmin.get(i).getAsDouble() : Double.NaN;
                                        String tempJoin;
                                        if (Double.isNaN(maxt) && Double.isNaN(mint)) tempJoin = "--°C";
                                        else if (Double.isNaN(mint)) tempJoin = String.format(Locale.ROOT, "%.0f°C", maxt);
                                        else tempJoin = String.format(Locale.ROOT, "%.0f/%.0f°C", maxt, mint);
                                        tempsArr[i] = tempJoin;

                                        int wc = wcode.size() > i ? wcode.get(i).getAsInt() : -1;
                                        condArr[i] = weatherCodeToString(wc);
                                    }

                                    for (int i = 0; i < 5; i++) {
                                        if (daysArr[i] == null) daysArr[i] = "--";
                                        if (tempsArr[i] == null) tempsArr[i] = "--°C";
                                        if (condArr[i] == null) condArr[i] = "--";
                                    }
                                    dailyOk = true;
                                } catch (Exception ignored) {
                                    dailyOk = false;
                                }
                            }

                            final boolean finalDailyOk = dailyOk;
                            final String[] finalDays = daysArr;
                            final String[] finalTemps = tempsArr;
                            final String[] finalConds = condArr;

                            asyncManager.executeOnMainThread(() -> {
                                currentWeather.set(condition);
                                temperature.set(tempStr);
                                location.set(displayLocation);

                                if (finalDailyOk) {
                                    forecastDays.set(finalDays);
                                    forecastTemps.set(finalTemps);
                                    forecastConditions.set(finalConds);
                                } else {

                                    forecastDays.set(new String[] {"--","--","--","--","--"});
                                    forecastTemps.set(new String[] {"--°C","--°C","--°C","--°C","--°C"});
                                    forecastConditions.set(new String[] {"--","--","--","--","--"});
                                }
                            });
                            return;
                        }
                    } catch (Exception e) {

                    }
                }

                applyFallbackData();
            } catch (Exception e) {
                applyFallbackData();
            }
        });
    }

    private void applyFallbackData() {
        String[] weatherConditions = {"Sunny", "Cloudy", "Rainy", "Snowy", "Partly Cloudy"};
        String[] temperatures = {"22°C", "18°C", "15°C", "-2°C", "20°C"};
        int randomIndex = (int) (Math.random() * weatherConditions.length);
        asyncManager.executeOnMainThread(() -> {
            currentWeather.set(weatherConditions[randomIndex]);
            temperature.set(temperatures[randomIndex]);
            location.set("Unknown Location");

            forecastDays.set(new String[] {"--","--","--","--","--"});
            forecastTemps.set(new String[] {"22°C","18°C","15°C","-2°C","20°C"});
            forecastConditions.set(new String[] {"Sunny","Cloudy","Rainy","Snowy","Partly Cloudy"});
        });
    }

    private static String getStringSafe(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return null;
        try { return obj.get(key).getAsString(); } catch (Exception e) { return null; }
    }

    private static JsonObject httpGetJson(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "pc-ui-weather/1.0");

            int code = conn.getResponseCode();
            if (code != 200) return null;

            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                return GSON.fromJson(sb.toString(), JsonObject.class);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String buildLocationString(String city, String region, String country, double lat, double lon) {
        if (city != null && region != null && country != null) return city + ", " + region + ", " + country;
        if (city != null && region != null) return city + ", " + region;
        if (city != null) return city + String.format(Locale.ROOT, " (%.3f, %.3f)", lat, lon);
        return String.format(Locale.ROOT, "%.3f, %.3f", lat, lon);
    }

    private static String weatherCodeToString(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: case 2: return "Mainly Clear";
            case 3: return "Overcast";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 61: case 63: case 65: return "Rain";
            case 71: case 73: case 75: return "Snow";
            case 80: case 81: case 82: return "Rain Shower";
            case 95: case 96: case 99: return "Thunderstorm";
            default: return "Unknown";
        }
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16, ch = r[3] - 40;

        guiGraphics.fill(cx, cy, cx + cw, cy + 30, 0xFF2B2B2B);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Weather"), cx + 10, cy + 8, 0xFFFFFFFF, false);

        guiGraphics.fill(cx + cw - 80, cy + 5, cx + cw - 10, cy + 25, 0xFF4C7BD1);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Refresh"), cx + cw - 75, cy + 10, 0xFFFFFFFF, false);

        int contentY = cy + 40;
        guiGraphics.fill(cx, contentY, cx + cw, contentY + ch - 40, 0xFF1E1E1E);

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Location: " + location.get()), cx + 10, contentY + 20, 0xFFFFFFFF, false);

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(temperature.get()), cx + 10, contentY + 50, 0xFFFFFFFF, false);

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(currentWeather.get()), cx + 10, contentY + 80, 0xFFFFFFFF, false);

        int iconX = cx + cw - 100;
        int iconY = contentY + 40;
        int iconSize = 60;

        String cond = currentWeather.get().toLowerCase(Locale.ROOT);
        if (cond.contains("clear") || cond.contains("sun")) {
            guiGraphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFFFFFF00);
        } else if (cond.contains("cloud") || cond.contains("overcast")) {
            guiGraphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFFCCCCCC);
        } else if (cond.contains("rain") || cond.contains("drizzle")) {
            guiGraphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF3498DB);
        } else if (cond.contains("snow")) {
            guiGraphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFFFFFFFF);
        } else if (cond.contains("thunder")) {
            guiGraphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFFAA66FF);
        } else {
            guiGraphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0xFF888888);
        }

        int forecastY = contentY + 120;
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("5-Day Forecast:"), cx + 10, forecastY, 0xFFFFFFFF, false);

        String[] days = forecastDays.get();
        String[] fTemps = forecastTemps.get();
        String[] fConds = forecastConditions.get();

        int forecastX = cx + 10;
        for (int i = 0; i < 5; i++) {
            int dayX = forecastX + i * ((cw - 20) / 5);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(days[i]), dayX, forecastY + 20, 0xFFFFFFFF, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(fTemps[i]), dayX, forecastY + 40, 0xFFFFFFFF, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(fConds[i]), dayX, forecastY + 60, 0xFFFFFFFF, false);
        }
    }

    @Override
    public boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16;

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
        this.window = null;
        return true;
    }
}