package net.chaoscraft.chaoscrafts_device_mod.client.screen;

import net.chaoscraft.chaoscrafts_device_mod.ConfigHandler;
import net.chaoscraft.chaoscrafts_device_mod.client.app.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.client.fs.FilesManager;
import net.minecraft.resources.ResourceLocation;

import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;
import net.chaoscraft.chaoscrafts_device_mod.sound.ModSounds;
import net.chaoscraft.chaoscrafts_device_mod.sound.LaptopFanLoopSound;
import net.chaoscraft.chaoscrafts_device_mod.sound.LaptopKeySoundManager;
import net.chaoscraft.chaoscrafts_device_mod.network.NetworkHandler;
import net.chaoscraft.chaoscrafts_device_mod.network.packet.LaptopTypingPacket;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.io.File;
import java.io.IOException;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Locale;

public class DesktopScreen extends Screen {
    private EditBox searchBox;
    private final List<DesktopIcon> desktopIcons = new ArrayList<>();
    private final List<DraggableWindow> openApps = new ArrayList<>();
    private DraggableWindow focusedWindow = null;
    private final int taskbarHeight = 28;
    private ExactWindowSwitcher windowSwitcher;

    private final AtomicReference<String> trayTemperature = new AtomicReference<>("--°C ☁");
    private static final Gson GSON = new Gson();

    private boolean selecting = false;
    private int selectStartX, selectStartY, selectEndX, selectEndY;
    private final LinkedHashSet<DesktopIcon> selectedIcons = new LinkedHashSet<>();
    private DesktopIcon iconPressed = null;
    private boolean iconDragging = false;
    private double iconDragStartX = 0, iconDragStartY = 0;
    private final Map<DesktopIcon, int[]> iconStartPositions = new HashMap<>();
    private long lastClickTime = 0;
    private static final int DOUBLE_CLICK_MS = 400;
    private static final int ICON_GRID = 40;
    private static final float ICON_LERP = 0.18f;

    private static final int SEARCH_RECT_X = 0;
    private static final int SEARCH_RECT_Y_OFFSET = 0;
    private static final int SEARCH_RECT_WIDTH = 180;
    private static final int SEARCH_RECT_HEIGHT = 27;
    private static final int SEARCH_RECT_OUTLINE_THICKNESS = 1;
    private static final int SEARCH_RECT_BG_COLOR = 0x881e2123;
    private static final int SEARCH_RECT_OUTLINE_COLOR = 0x00000000;

    private DraggableWindow lastTaskbarClickWindow = null;
    private long lastTaskbarClickTime = 0;

    private final List<SearchResult> searchResults = new ArrayList<>();
    private static final int RESULT_HEIGHT = 28;

    private DesktopContextMenu contextMenu = null;
    private int iconSize = 32;

    private long lastTimeUpdate = 0;
    private String currentTime = "";

    private final RiftOSLoadingScreen loadingScreen = new RiftOSLoadingScreen();
    private final AsyncTaskManager asyncManager = AsyncTaskManager.getInstance();
    private final java.util.Random rng = new java.util.Random();
    private static final java.util.Set<String> missingIconLog = new java.util.HashSet<>();

    private boolean ctrlPressed = false;
    private boolean shiftPressed = false;

    private DesktopIcon renamingIcon = null;
    private EditBox renameBox = null;

    private final File desktopDir = new File(FilesManager.getPlayerDataDir(), "Desktop");

    private LaptopFanLoopSound fanLoopSound;
    private final java.util.Random soundRand = new java.util.Random();
    private long lastKeyTypeMillis = 0;
    private static final long KEY_PACKET_COOLDOWN_MS = 55;

    private final BlockPos devicePos;
    private boolean fanStarted = false;
    private static final int FAN_PREDELAY_TICKS = 30 * 20;

    private static final int TASKBAR_ICON_SIZE_PX = 24;
    private static final int TASKBAR_ICON_GAP_PX = 10;
    private static final int TASKBAR_SLOT_WIDTH_PX = TASKBAR_ICON_SIZE_PX + TASKBAR_ICON_GAP_PX;

    public DesktopScreen() { this(null); }
    public DesktopScreen(BlockPos devicePos) {
        super(Component.literal("Desktop"));
        this.devicePos = devicePos;
        this.windowSwitcher = new ExactWindowSwitcher(this);
        File base = Minecraft.getInstance().gameDirectory;
        File saveDir = new File(base, "chaoscrafts_device_mod");
        FilesManager.init(saveDir);
        if (devicePos != null) LaptopKeySoundManager.setDevicePos(devicePos);

        try { asyncManager.submitIOTask(this::fetchTrayTemperature); } catch (Exception ignored) {}

        asyncManager.executeOnMainThread(() -> {
            try { AppRegistry.getInstance(); } catch (Exception ignored) {}
            try { SettingsApp.loadSettings(); } catch (Exception ignored) {}
        });
        asyncManager.scheduleTask(() -> asyncManager.executeOnMainThread(() -> loadingScreen.setShowLoadingOverlay(false)), 5000, java.util.concurrent.TimeUnit.MILLISECONDS);
        try { if (!desktopDir.exists() && !desktopDir.mkdirs()) System.err.println("Warning: failed to create desktop dir: " + desktopDir.getAbsolutePath()); } catch (Exception ignored) {}
    }

    private void fetchTrayTemperature() {
        try {
            JsonObject geo = httpGetJson("https://ipapi.co/json/");
            double lat = Double.NaN, lon = Double.NaN;
            if (geo != null) {
                try {
                    JsonElement latEl = geo.get("latitude");
                    JsonElement lonEl = geo.get("longitude");
                    if (latEl != null && lonEl != null && !latEl.isJsonNull() && !lonEl.isJsonNull()) {
                        lat = latEl.getAsDouble(); lon = lonEl.getAsDouble();
                    }
                } catch (Exception ignored) {}
            }
            if (Double.isNaN(lat) || Double.isNaN(lon)) {
                JsonObject alt = httpGetJson("https://ipinfo.io/json");
                if (alt != null) {
                    try {
                        String loc = alt.has("loc") && !alt.get("loc").isJsonNull() ? alt.get("loc").getAsString() : null;
                        if (loc != null && loc.contains(",")) {
                            String[] p = loc.split(","); lat = Double.parseDouble(p[0]); lon = Double.parseDouble(p[1]);
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (Double.isNaN(lat) || Double.isNaN(lon)) return;

            String meteourl = String.format(Locale.ROOT,
                    "https://api.open-meteo.com/v1/forecast?latitude=%f&longitude=%f&current_weather=true&timezone=auto",
                    lat, lon);
            JsonObject weatherJson = httpGetJson(meteourl);
            if (weatherJson != null && weatherJson.has("current_weather") && !weatherJson.get("current_weather").isJsonNull()) {
                try {
                    JsonObject current = weatherJson.getAsJsonObject("current_weather");
                    double temp = current.has("temperature") ? current.get("temperature").getAsDouble() : Double.NaN;
                    int code = current.has("weathercode") ? current.get("weathercode").getAsInt() : -1;
                    final String tempStr = Double.isNaN(temp) ? "--°C" : String.format(Locale.ROOT, "%.0f°C", temp);
                    final String icon = weatherCodeToIcon(code);
                    asyncManager.executeOnMainThread(() -> trayTemperature.set(tempStr + " " + icon));
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private static JsonObject httpGetJson(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "pc-ui-tray-weather/1.0");
            int code = conn.getResponseCode();
            if (code != 200) return null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line);
                return GSON.fromJson(sb.toString(), JsonObject.class);
            }
        } catch (Exception e) { return null; } finally { if (conn != null) conn.disconnect(); }
    }

    private static String weatherCodeToIcon(int code) {
        switch (code) {
            case 0: return "☀";
            case 1: case 2: return "🌤";
            case 3: return "☁";
            case 45: case 48: return "🌫";
            case 51: case 53: case 55: return "💧";
            case 61: case 63: case 65: return "🌧";
            case 71: case 73: case 75: return "❄";
            case 80: case 81: case 82: return "🌦";
            case 95: case 96: case 99: return "⚡";
            default: return "☁";
        }
    }

    @Override
    protected void init() {
        super.init();
        if (!fanStarted && ConfigHandler.experimentalEnabled()) {
            try {
                double fx, fy, fz;
                if (devicePos != null) {
                    fx = devicePos.getX() + 0.5; fy = devicePos.getY() + 0.5; fz = devicePos.getZ() + 0.5;
                } else {
                    var player = Minecraft.getInstance().player;
                    if (player != null) { fx = player.getX(); fy = player.getY(); fz = player.getZ(); } else { fx = 0; fy = 0; fz = 0; }
                }
                fanLoopSound = new LaptopFanLoopSound(ModSounds.LAPTOP_FAN_NOISE.get(), fx, fy, fz, FAN_PREDELAY_TICKS);
                Minecraft.getInstance().getSoundManager().play(fanLoopSound);
                fanStarted = true;
            } catch (Exception e) {
                System.out.println("[DesktopScreen] Failed to start fan loop: " + e.getMessage());
            }
        }

        forceMessengerInstallation();

        searchBox = new EditBox(this.font, SEARCH_RECT_X, SEARCH_RECT_Y_OFFSET, SEARCH_RECT_WIDTH, SEARCH_RECT_HEIGHT, Component.literal("Search")) {
            @Override
            public void render(GuiGraphics gfx, int mouseX, int mouseY, float partial) {
            }
        };
        searchBox.setMaxLength(100);
        try {
            searchBox.setBordered(false);
            try { searchBox.setTextColor(0x00FFFFFF); } catch (NoSuchMethodError ignored) {}
        } catch (Exception ignored) {}
        this.addRenderableWidget(searchBox);

        if (renamingIcon != null) {
            int rx = Math.round(renamingIcon.displayX);
            int ry = Math.round(renamingIcon.displayY + renamingIcon.iconSize + 4);
            int rw = Math.max(80, renamingIcon.iconSize);
            renameBox = new EditBox(this.font, rx, ry, rw, 16, Component.literal("Rename"));
            renameBox.setValue(renamingIcon.name);
            this.addRenderableWidget(renameBox);
        }

        try {
            AppRegistry reg = AppRegistry.getInstance();
            reg.getLoadedFuture().whenComplete((v, ex) -> asyncManager.executeOnMainThread(this::refreshDesktopIcons));
        } catch (Exception e) {
            asyncManager.submitIOTask(this::refreshDesktopIcons);
        }
    }

    @Override
    public void onClose() {
        if (fanLoopSound != null && fanStarted && !fanLoopSound.isFinished()) fanLoopSound.requestFadeOut();
        LaptopKeySoundManager.clearDevicePos();
        super.onClose();
    }

    @Override
    public void removed() {
        if (fanLoopSound != null && fanStarted && !fanLoopSound.isFinished() && !fanLoopSound.isFadingOut()) {
            fanLoopSound.requestFadeOut();
        }
        super.removed();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        float uiScale = ConfigHandler.uiScaleFactor();
        int sMouseX = Math.round(mouseX / uiScale);
        int sMouseY = Math.round(mouseY / uiScale);

        int width = Math.round(this.width / uiScale);
        int height = Math.round(this.height / uiScale);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(uiScale, uiScale, 1f);

        if (loadingScreen.isShowing()) {
            loadingScreen.render(guiGraphics, width, height, font, partialTick);
            guiGraphics.pose().popPose();
            return;
        }

        FilesManager fm = FilesManager.getInstance();
        if (fm != null && fm.isCurrentWallpaperColor()) {
            int color = fm.getCurrentWallpaperColor();
            guiGraphics.fill(0, 0, width, height, color);
        } else {
            ResourceLocation texId = fm != null ? fm.getCurrentWallpaperResource() : null;
            if (texId != null) {
                try {
                    guiGraphics.blit(texId, 0, 0, 0, 0, width, height, width, height);
                } catch (Exception e) {
                    int bg = DraggableWindow.darkTheme ? 0xFF0F0F12 : 0xFF9AA6B2;
                    guiGraphics.fill(0, 0, width, height, bg);
                }
            } else {
                int bg = DraggableWindow.darkTheme ? 0xFF0F0F12 : 0xFF9AA6B2;
                guiGraphics.fill(0, 0, width, height, bg);
            }
        }

        if (showDebugInfo) renderDebugInfo(guiGraphics);

        for (DesktopIcon icon : desktopIcons) {
            icon.displayX = lerp(icon.displayX, icon.targetX, ICON_LERP);
            icon.displayY = lerp(icon.displayY, icon.targetY, ICON_LERP);
            icon.render(guiGraphics, sMouseX, sMouseY, selectedIcons.contains(icon), iconSize);
        }

        searchBox.setWidth(SEARCH_RECT_WIDTH);
        searchBox.setX(SEARCH_RECT_X);
        searchBox.setY(height - taskbarHeight + SEARCH_RECT_Y_OFFSET);

        DraggableWindow topWinEarly = getTopWindow();
        boolean hideTaskbar = (topWinEarly != null) && topWinEarly.exclusiveFullscreen;

        if (hideTaskbar && searchBox != null && searchBox.isFocused()) searchBox.setFocused(false);

        if (!hideTaskbar) {
            int rectX = searchBox.getX();
            int rectY = searchBox.getY();
            int rectW = searchBox.getWidth();
            int rectH = SEARCH_RECT_HEIGHT;

            int bgColor = SEARCH_RECT_BG_COLOR;
            int outlineColor = SEARCH_RECT_OUTLINE_COLOR;

            guiGraphics.fill(rectX, rectY, rectX + rectW, rectY + rectH, bgColor);
        }

        {
            int tbY = height - taskbarHeight;
            int topStripH = taskbarHeight;
            final float BAND_RATIO = 0.08f;
            int topBand = Math.max(1, Math.round(topStripH * BAND_RATIO));
            int bottomBand = Math.max(1, Math.round(topStripH * BAND_RATIO));
            int middleBand = Math.max(0, topStripH - topBand - bottomBand);

            int taskbarBase = 0xCC2B2F33;
            int baseAlpha = (taskbarBase >>> 24) & 0xFF;
            int baseRgb = taskbarBase & 0x00FFFFFF;

            java.util.function.BiFunction<Integer, Float, Integer> lighten = (rgb, amt) -> {
                int r = (rgb >> 16) & 0xFF; int g = (rgb >> 8) & 0xFF; int b = rgb & 0xFF;
                int nr = Math.min(255, (int)(r + (255 - r) * amt));
                int ng = Math.min(255, (int)(g + (255 - g) * amt));
                int nb = Math.min(255, (int)(b + (255 - b) * amt));
                return (nr << 16) | (ng << 8) | nb;
            };
            java.util.function.BiFunction<Integer, Float, Integer> darken = (rgb, amt) -> {
                int r = (rgb >> 16) & 0xFF; int g = (rgb >> 8) & 0xFF; int b = rgb & 0xFF;
                int nr = Math.max(0, (int)(r * (1f - amt))); int ng = Math.max(0, (int)(g * (1f - amt))); int nb = Math.max(0, (int)(b * (1f - amt)));
                return (nr << 16) | (ng << 8) | nb;
            };

            final float LIGHTEN_AMOUNT = 0.12f;
            final float DARKEN_AMOUNT = 0.12f;

            int lighterRgb = DraggableWindow.lightenRgb(baseRgb, LIGHTEN_AMOUNT);
            int darkerRgb = DraggableWindow.darkenRgb(baseRgb, DARKEN_AMOUNT);

            int lighterAlpha = baseAlpha;
            int lighterColor = (lighterAlpha << 24) | (lighterRgb & 0x00FFFFFF);
            int normalColor = (baseAlpha << 24) | (baseRgb & 0x00FFFFFF);
            int darkerColor = (baseAlpha << 24) | (darkerRgb & 0x00FFFFFF);

            guiGraphics.fill(0, tbY, width, tbY + topBand, lighterColor);
            guiGraphics.fill(0, tbY + topBand, width, tbY + topBand + middleBand, normalColor);
            guiGraphics.fill(0, tbY + topBand + middleBand, width, tbY + topStripH, darkerColor);

            guiGraphics.fill(0, tbY - 2, width, tbY, 0x22000000);

            int txBase = 6 + searchBox.getWidth() + 8;
            int perEntry = 52;
            int slotWidthForLayout = TASKBAR_SLOT_WIDTH_PX;
            int available = Math.max(0, width - txBase - 8);
            int maxEntries = Math.max(0, available / slotWidthForLayout);
            List<DraggableWindow> visibleWindowsForPreview = new ArrayList<>();
            if (maxEntries > 0) {
                int start = Math.max(0, openApps.size() - maxEntries);
                for (int i = start; i < openApps.size(); i++) {
                    DraggableWindow w0 = openApps.get(i);
                    if (isTaskbarEligible(w0)) visibleWindowsForPreview.add(w0);
                }
            }

            for (DraggableWindow w0 : openApps) {
                try { w0.setPreviewActive(false); } catch (Exception ignored) {}
            }

            int tx2 = txBase;
            for (DraggableWindow w0 : visibleWindowsForPreview) {
                int cx = tx2 + slotWidthForLayout / 2;
                int iconLeft = cx - TASKBAR_ICON_SIZE_PX / 2;
                int iconRight = iconLeft + TASKBAR_ICON_SIZE_PX;
                int y0 = tbY + 2, y1 = height - 4;
                boolean hovered = (sMouseX >= iconLeft && sMouseX < iconRight && sMouseY >= y0 && sMouseY < y1);
                if (hovered) {
                    int maxPreviewW = Math.min(360, Math.max(120, width / 5));
                    int rawW = Math.round(w0.width * 0.35f);
                    int previewW = Math.min(maxPreviewW, Math.max(80, rawW));
                    float aspect = (float) Math.max(1, w0.width) / Math.max(1, w0.height);
                    int previewH = Math.max(48, Math.round(previewW / aspect));
                    int px = Math.max(6, Math.min(width - previewW - 6, cx - previewW / 2));
                    int py = tbY - previewH - 8;
                    if (py < 6) py = 6;
                    DraggableWindow prev = null;
                    for (DraggableWindow p : openApps) {
                        if (p != null && p.preview && p.previewOpacity > 0.02f) { prev = p; break; }
                    }
                    if (prev != null && prev != w0) {
                        prev.previewTargetOpacity = 0f;
                        w0.previewDisplayX = prev.previewDisplayX;
                        w0.previewDisplayY = prev.previewDisplayY;
                        w0.previewDisplayW = prev.previewDisplayW;
                        w0.previewDisplayH = prev.previewDisplayH;
                    } else if (!w0.preview) {
                        w0.previewDisplayX = px; w0.previewDisplayY = py; w0.previewDisplayW = previewW; w0.previewDisplayH = previewH;
                    }
                    w0.previewX = px; w0.previewY = py; w0.previewW = previewW; w0.previewH = previewH;
                    w0.setPreviewActive(true);
                }
                tx2 += slotWidthForLayout;
            }

            for (DraggableWindow w0 : openApps) {
                try {
                    if (w0.previewW > 0 && w0.previewH > 0) {
                        boolean hoveringPreviewRect = sMouseX >= w0.previewX && sMouseX < w0.previewX + w0.previewW && sMouseY >= w0.previewY && sMouseY < w0.previewY + w0.previewH;
                        if (hoveringPreviewRect) w0.setPreviewActive(true);
                    }
                } catch (Exception ignored) {}
            }

            for (DraggableWindow w0 : openApps) {
                try { w0.updatePreviewAnimation(partialTick); } catch (Exception ignored) {}
            }
        }

        for (DraggableWindow w : openApps) {
            if (!w.minimized || w.closing) {
                boolean focused = (w == getTopWindow());
                w.render(guiGraphics, guiGraphics.pose(), sMouseX, sMouseY, focused, taskbarHeight, partialTick);
                w.finalizeAnimationState();
            }
        }

        for (DraggableWindow w : openApps) {
            try {
                if (w.preview && w.previewDisplayW > 0 && w.previewDisplayH > 0 && w.previewOpacity > 0.02f) {
                    int px = Math.round(w.previewDisplayX);
                    int py = Math.round(w.previewDisplayY);
                    int pw = Math.max(1, Math.round(w.previewDisplayW));
                    int ph = Math.max(1, Math.round(w.previewDisplayH));
                    try {
                        if (this.windowSwitcher instanceof ExactWindowSwitcher) {
                            ((ExactWindowSwitcher)this.windowSwitcher).renderThumbnail(guiGraphics, w, px, py, pw, ph, partialTick, false);
                        } else {
                            w.drawPreview(guiGraphics, sMouseX, sMouseY, partialTick);
                        }
                    } catch (Exception e) {
                        try { w.drawPreview(guiGraphics, sMouseX, sMouseY, partialTick); } catch (Exception ignored) {}
                    }

                    if (w.previewOpacity < 0.999f) {
                        int fadeAlpha = Math.round((1f - w.previewOpacity) * 255f) & 0xFF;
                        if (fadeAlpha > 0) guiGraphics.fill(px, py, px + pw, py + ph, (fadeAlpha << 24) | 0x000000);
                    }
                } else {
                }
            } catch (Exception ignored) {}
        }

        if (selecting && !iconDragging) {
            int x0 = Math.min(selectStartX, selectEndX), y0 = Math.min(selectStartY, selectEndY);
            int x1 = Math.max(selectStartX, selectEndX), y1 = Math.max(selectStartY, selectEndY);
            int fillCol = DraggableWindow.selectionOverlayColor();
            guiGraphics.fill(x0, y0, x1, y1, fillCol);
            int outline = (0x88 << 24) | (DraggableWindow.accentColorARGB & 0x00FFFFFF);
            guiGraphics.fill(x0, y0, x1, y0 + 1, outline);
            guiGraphics.fill(x0, y1 - 1, x1, y1, outline);
            guiGraphics.fill(x0, y0, x0 + 1, y1, outline);
            guiGraphics.fill(x1 - 1, y0, x1, y1, outline);
        }

        updateSearchResults();

        DraggableWindow topWinLater = getTopWindow();
        hideTaskbar = (topWinLater != null) && topWinLater.exclusiveFullscreen;
        if (!hideTaskbar) {
            int tbY = height - taskbarHeight;
            searchBox.setX(SEARCH_RECT_X);
            searchBox.setY(tbY + SEARCH_RECT_Y_OFFSET);

            int topStripH = taskbarHeight;
            final float BAND_RATIO = 0.08f;
            int topBand = Math.max(1, Math.round(topStripH * BAND_RATIO));
            int bottomBand = Math.max(1, Math.round(topStripH * BAND_RATIO));
            int middleBand = Math.max(0, topStripH - topBand - bottomBand);

            int taskbarBase = 0xCC2B2F33;
            int baseAlpha = (taskbarBase >>> 24) & 0xFF;
            int baseRgb = taskbarBase & 0x00FFFFFF;

            java.util.function.BiFunction<Integer, Float, Integer> lighten = (rgb, amt) -> {
                int r = (rgb >> 16) & 0xFF; int g = (rgb >> 8) & 0xFF; int b = rgb & 0xFF;
                int nr = Math.min(255, (int)(r + (255 - r) * amt));
                int ng = Math.min(255, (int)(g + (255 - g) * amt));
                int nb = Math.min(255, (int)(b + (255 - b) * amt));
                return (nr << 16) | (ng << 8) | nb;
            };
            java.util.function.BiFunction<Integer, Float, Integer> darken = (rgb, amt) -> {
                int r = (rgb >> 16) & 0xFF; int g = (rgb >> 8) & 0xFF; int b = rgb & 0xFF;
                int nr = Math.max(0, (int)(r * (1f - amt))); int ng = Math.max(0, (int)(g * (1f - amt))); int nb = Math.max(0, (int)(b * (1f - amt)));
                return (nr << 16) | (ng << 8) | nb;
            };

            final float LIGHTEN_AMOUNT = 0.12f;
            final float DARKEN_AMOUNT = 0.12f;

            int lighterRgb = DraggableWindow.lightenRgb(baseRgb, LIGHTEN_AMOUNT);
            int darkerRgb = DraggableWindow.darkenRgb(baseRgb, DARKEN_AMOUNT);

            int lighterAlpha = baseAlpha;
            int lighterColor = (lighterAlpha << 24) | (lighterRgb & 0x00FFFFFF);
            int normalColor = (baseAlpha << 24) | (baseRgb & 0x00FFFFFF);
            int darkerColor = (baseAlpha << 24) | (darkerRgb & 0x00FFFFFF);

            guiGraphics.fill(0, tbY, width, tbY + topBand, lighterColor);
            guiGraphics.fill(0, tbY + topBand, width, tbY + topBand + middleBand, normalColor);
            guiGraphics.fill(0, tbY + topBand + middleBand, width, tbY + topStripH, darkerColor);

            guiGraphics.fill(0, tbY - 2, width, tbY, 0x22000000);

            final int trayIconW = 28;
            final int trayIconH = 18;
            final int trayIconPad = 6;

            List<String> trayLabels = Arrays.asList("LAN", "🔊", "Upd", "✉", trayTemperature.get(), "📷", "/\\");

            int iconsTotalW = trayLabels.size() * trayIconW + (trayLabels.size() - 1) * trayIconPad + 8;
            String timeStr = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"));
            String dateStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy"));
            int timeW = font.width(timeStr);
            int dateW = font.width(dateStr);
            int clockW = Math.max(timeW, dateW) + 12;
            int trayTotalWidth = iconsTotalW + clockW + 12;

            int trayRightPadding = 10;
            int trayLeft = Math.max(6 + searchBox.getWidth() + 16, width - trayTotalWidth - trayRightPadding);
            int x = trayLeft;
            int iconY = tbY + (taskbarHeight - trayIconH) / 2;

            for (String lbl : trayLabels) {
                guiGraphics.fill(x, iconY, x + trayIconW, iconY + trayIconH, 0x20000000);
                guiGraphics.fill(x + 1, iconY + 1, x + trayIconW - 1, iconY + trayIconH - 1, 0x33000000);
                int lw = font.width(lbl);
                int lx = x + Math.max(4, (trayIconW - lw) / 2);
                int ly = iconY + Math.max(1, (trayIconH - 8) / 2);
                drawShadowedString(guiGraphics, font, lbl, lx, ly);
                x += trayIconW + trayIconPad;
            }

            int clockRightEdge = width - trayRightPadding;
            int clockLeft = clockRightEdge - Math.max(timeW, dateW);
            int minClockLeft = trayLeft + iconsTotalW + 6;
            if (clockLeft < minClockLeft) clockLeft = minClockLeft;
            int timeY = tbY + Math.max(2, (taskbarHeight - (font.lineHeight * 2)) / 2);
            int dateY = timeY + Math.max(6, font.lineHeight - 2);
            drawShadowedString(guiGraphics, font, timeStr, clockLeft, timeY);
            drawShadowedString(guiGraphics, font, dateStr, clockLeft, dateY);

            if (!searchBox.getValue().isEmpty() && !searchResults.isEmpty()) {
                int popupW = Math.max(searchBox.getWidth(), 260);
                int entries = Math.min(searchResults.size(), 6);
                int popupH = entries * RESULT_HEIGHT + 6;
                int popupX = searchBox.getX();
                int popupY = tbY - popupH - 6; if (popupY < 6) popupY = 6;

                guiGraphics.fill(popupX - 4, popupY - 4, popupX + popupW + 4, popupY + popupH + 4, DraggableWindow.darkTheme ? 0xEE111111 : 0xEEFFFFFF);
                guiGraphics.fill(popupX - 3, popupY - 3, popupX + popupW + 3, popupY + popupH + 3, DraggableWindow.darkTheme ? 0xCC1F1F1F : 0xCCDDDDDD);

                for (int i = 0; i < entries; i++) {
                    SearchResult r = searchResults.get(i);
                    int ry = popupY + 6 + i * RESULT_HEIGHT;
                    int rw = popupW - 12;
                    boolean hovered = sMouseX >= popupX + 6 && sMouseX < popupX + 6 + rw && sMouseY >= ry && sMouseY < ry + RESULT_HEIGHT;
                    if (hovered) guiGraphics.fill(popupX + 6, ry, popupX + 6 + rw, ry + RESULT_HEIGHT, DraggableWindow.selectionOverlayColor());
                    int iconSizePx = 20;
                    int iconX = popupX + 10;

                    ResourceLocation iconToUse = r.iconRes;
                    if (iconToUse == null) {
                        try {
                            String derived = normalizeAppNameForIcon(r.displayName);
                            if (derived != null && !derived.isEmpty()) iconToUse = IconManager.getIconResource(derived);
                        } catch (Exception ignored) { iconToUse = null; }
                    }
                    if (iconToUse == null) {
                        try { iconToUse = IconManager.getIconResource(r.displayName.toLowerCase()); } catch (Exception ignored) { iconToUse = null; }
                    }

                    if (iconToUse != null) {
                        try { guiGraphics.blit(iconToUse, iconX, ry + (RESULT_HEIGHT - iconSizePx) / 2, 0, 0, iconSizePx, iconSizePx, iconSizePx, iconSizePx); } catch (Exception ignored) {}
                    }

                    String label = r.displayName;
                    int labelX = popupX + 12 + iconSizePx + 6;
                    int availTextW = popupW - (labelX - popupX) - 12;
                    if (font.width(label) > availTextW) label = font.plainSubstrByWidth(label, availTextW - 8) + "...";
                    drawShadowedString(guiGraphics, font, label, labelX, ry + 6);
                }
            }

            int tx = 6 + searchBox.getWidth() + 8;
            int perEntry = 52;
            int slotWidth = TASKBAR_SLOT_WIDTH_PX;
            int startX = tx;
            int available = Math.max(0, width - startX - 8);
            int maxEntries = Math.max(0, available / slotWidth);
            List<DraggableWindow> visibleWindows = new ArrayList<>();
            if (maxEntries > 0) {
                int start = Math.max(0, openApps.size() - maxEntries);
                for (int i = start; i < openApps.size(); i++) {
                    DraggableWindow w0 = openApps.get(i);
                    if (isTaskbarEligible(w0)) visibleWindows.add(w0);
                }
            }

            for (DraggableWindow w : visibleWindows) {
                int y0 = tbY + 2, y1 = height - 4;
                int cx = tx + slotWidth / 2;
                int tbIconSize = TASKBAR_ICON_SIZE_PX;
                int iconLeft = cx - tbIconSize / 2;
                int iconRight = iconLeft + tbIconSize;
                boolean hovered = (sMouseX >= iconLeft && sMouseX < iconRight && sMouseY >= y0 && sMouseY < y1);
                int cy = tbY + taskbarHeight / 2 - 1;
                if (hovered) {
                    int squareSize = taskbarHeight;
                    int left = cx - squareSize / 2;
                    int top = tbY;
                    int right = left + squareSize;
                    int bottom = tbY + taskbarHeight;

                    guiGraphics.fill(left, top, right, bottom, 0x11FFFFFF);
                }
                if (!w.minimized) {
                    int squareSize = taskbarHeight;
                    int left = cx - squareSize / 2;
                    int top = tbY;
                    int right = left + squareSize;
                    int bottom = tbY + taskbarHeight;

                    guiGraphics.fill(left, top, right, bottom, 0x22FFFFFF);
                }

                try {
                    if (w.appName != null) {
                        ResourceLocation appIconRes = IconManager.getIconResource(normalizeAppNameForIcon(w.appName));
                        guiGraphics.blit(appIconRes, cx - tbIconSize / 2, cy - tbIconSize / 2, 0, 0, tbIconSize, tbIconSize, tbIconSize, tbIconSize);
                    }
                } catch (Exception ignored) {}

                tx += slotWidth;
            }
        }

        if (ConfigHandler.debugButtonsEnabled()) {
            try {
                int sx = searchBox.getX(); int sy = searchBox.getY(); int sw = searchBox.getWidth(); int sh = searchBox.getHeight();
                DebugOverlay.drawHitbox(guiGraphics, sx, sy, sx + sw, sy + sh, "searchBox");
            } catch (Exception ignored) {}

            int tbY = height - taskbarHeight;
            int tx = 6 + searchBox.getWidth() + 8;
            int perEntry = 52;
            int slotWidth = TASKBAR_SLOT_WIDTH_PX;
            int startX = tx;
            int available = Math.max(0, width - startX - 8);
            int maxEntries = Math.max(0, available / slotWidth);
            if (maxEntries > 0) {
                int start = Math.max(0, openApps.size() - maxEntries);
                int tx2 = tx;
                for (int i = start; i < openApps.size(); i++) {
                    DraggableWindow w0 = openApps.get(i);
                    if (!isTaskbarEligible(w0)) { tx2 += perEntry; continue; }
                    int cx = tx2 + TASKBAR_SLOT_WIDTH_PX / 2;
                    int iconLeft = cx - TASKBAR_ICON_SIZE_PX / 2;
                    int iconRight = iconLeft + TASKBAR_ICON_SIZE_PX;
                    int y0 = tbY + 2, y1 = height - 4;
                    DebugOverlay.drawHitbox(guiGraphics, iconLeft, y0, iconRight, y1, "taskbar-window:" + (w0.appName == null ? "<app>" : w0.appName));
                    tx2 += TASKBAR_SLOT_WIDTH_PX;
                }
            }

            for (DraggableWindow w : openApps) {
                int[] r = w.getRenderRect(taskbarHeight);
                int rx = r[0], ry = r[1], rw = r[2];
                int btnY = ry + 6;
                DebugOverlay.drawHitbox(guiGraphics, rx + rw - 20, btnY, rx + rw - 8, btnY + 14, "btn:close");
                DebugOverlay.drawHitbox(guiGraphics, rx + rw - 44, btnY, rx + rw - 32, btnY + 14, "btn:minimize");
                DebugOverlay.drawHitbox(guiGraphics, rx + rw - 68, btnY, rx + rw - 56, btnY + 14, "btn:maximize");
            }
        }

        List<DraggableWindow> rem = new ArrayList<>(); for (DraggableWindow w : openApps) if (w.removeRequested) rem.add(w); openApps.removeAll(rem);

        super.render(guiGraphics, sMouseX, sMouseY, partialTick);

        try {
            if (!hideTaskbar) {
                int rectX2 = searchBox.getX();
                int rectY2 = searchBox.getY();
                int rectW2 = searchBox.getWidth();
                int rectH2 = SEARCH_RECT_HEIGHT;
                int bgColor2 = SEARCH_RECT_BG_COLOR;
                int outlineColor2 = SEARCH_RECT_OUTLINE_COLOR;
                guiGraphics.fill(rectX2, rectY2, rectX2 + rectW2, rectY2 + rectH2, bgColor2);

                String val2 = searchBox.getValue();
                Font f2 = this.font;
                int textX2 = rectX2 + 6;
                int textY2 = rectY2 + Math.max(1, (rectH2 - f2.lineHeight) / 2);
                if (val2 == null || val2.isEmpty()) {
                    if (!searchBox.isFocused()) drawShadowedString(guiGraphics, f2, "Type here to search", textX2, textY2);
                    else drawShadowedString(guiGraphics, f2, "", textX2, textY2);
                } else {
                    drawShadowedString(guiGraphics, f2, val2, textX2, textY2);
                }

                if (searchBox.isFocused()) {
                    boolean caretOn = ((System.currentTimeMillis() / 500L) & 1L) == 0L;
                    if (caretOn) {
                        int textWidth = (val2 == null || val2.isEmpty()) ? 0 : f2.width(val2);
                        int caretX = textX2 + textWidth;
                        int caretY0 = rectY2 + Math.max(2, (rectH2 - f2.lineHeight) / 2);
                        int caretY1 = caretY0 + f2.lineHeight;

                        int rightPadding = 4;
                        int maxCaretX = rectX2 + rectW2 - rightPadding;
                        if (caretX >= maxCaretX) caretX = maxCaretX - 1;
                        if (caretX < textX2) caretX = textX2;

                        guiGraphics.fill(caretX, caretY0, caretX + 1, caretY1, 0xFFFFFFFF);
                    }
                }
            }
        } catch (Exception ignored) {}

        if (contextMenu != null) contextMenu.render(guiGraphics, sMouseX, sMouseY, partialTick);

        try {
            if (this.windowSwitcher != null && this.windowSwitcher.isActive()) {
                this.windowSwitcher.render(guiGraphics, sMouseX, sMouseY, partialTick);
            }
        } catch (Exception ignored) {}

        guiGraphics.pose().popPose();
    }

    private boolean showDebugInfo = false;

    private boolean isTaskbarEligible(DraggableWindow w) {
        if (w == null) return false;
        if (w.removeRequested || w.closing) return false;
        if (w.appName == null) return false;
        String normalized = normalizeAppNameForIcon(w.appName);
        if (normalized == null || normalized.isEmpty()) return false;
        if (normalized.equalsIgnoreCase("apps") || normalized.equalsIgnoreCase("launcher") || normalized.equalsIgnoreCase("system")) return false;
        try { return AppRegistry.getInstance().isInstalled(normalized); } catch (Exception ignored) { return false; }
    }

    private void renderDebugInfo(GuiGraphics guiGraphics) {
        int activeTasks = asyncManager.getActiveTaskCount();
        long totalTasks = asyncManager.getTotalTasksExecuted();
        guiGraphics.fill(10, 10, 360, 140, 0x80000000);
        guiGraphics.drawString(font, "Async Tasks Debug", 15, 15, 0xFFFFFF, false);
        guiGraphics.drawString(font, "Active: " + activeTasks, 15, 30, 0xFFFFFF, false);
        guiGraphics.drawString(font, "Total: " + totalTasks, 15, 45, 0xFFFFFF, false);
        guiGraphics.drawString(font, "Open windows:", 15, 62, 0xFFFFFF, false);
        int y = 76; int maxShow = 8; int count = 0;
        for (DraggableWindow w : openApps) {
            if (count++ >= maxShow) break;
            String n = (w == null || w.appName == null) ? "<null>" : w.appName;
            String flags = (w == null) ? "" : (w.minimized ? "[min]" : "") + (w.removeRequested ? "[remReq]" : "") + (w.closing ? "[closing]" : "");
            guiGraphics.drawString(font, (count) + ". " + n + " " + flags, 15, y, 0xFFFFFF, false);
            y += 12;
        }
        if (openApps.size() > maxShow) guiGraphics.drawString(font, "... (" + openApps.size() + " total)", 15, y, 0xAAAAAA, false);
    }

    private void updateSearchResults() {
        searchResults.clear();
        String q = searchBox.getValue().trim().toLowerCase();
        if (q.isEmpty()) return;

        for (DesktopIcon di : desktopIcons) {
            try {
                if (di.name.toLowerCase().contains(q)) {
                    String display = toTitleCase(di.name);
                    ResourceLocation icon = null;
                    try {
                        String candidate = normalizeAppNameForIcon(di.name.replaceAll("\\.\\w+$", ""));
                        if (candidate != null && !candidate.isEmpty()) icon = IconManager.getIconResource(candidate);
                    } catch (Exception ignored) {
                        icon = null;
                    }
                    if (icon == null) {
                        try { icon = IconManager.getIconResource(di.name.toLowerCase()); } catch (Exception ignored) { icon = null; }
                    }
                    searchResults.add(new SearchResult(display, icon, di.onClick));
                }
            } catch (Exception ignored) {}
        }
    }

    private static float lerp(float a, float b, float f) { return a + (b - a) * f; }

    private static void drawShadowedString(GuiGraphics g, Font font, String text, int x, int y) {
        int shadow = 0x66000000;
        g.drawString(font, Component.literal(text), x + 1, y + 1, shadow, false);
        g.drawString(font, Component.literal(text), x - 1, y + 1, shadow, false);
        g.drawString(font, Component.literal(text), x + 1, y - 1, shadow, false);
        g.drawString(font, Component.literal(text), x - 1, y - 1, shadow, false);
        g.drawString(font, Component.literal(text), x, y, 0xFFFFFFFF, false);
    }

    private static String normalizeAppNameForIcon(String displayName) {
        if (displayName == null) return null;
        String n = displayName;
        if (n.contains(" - ")) n = n.substring(n.lastIndexOf(" - ") + 3);
        return n.trim().toLowerCase();
    }

    public static String toTitleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        String base = s;
        if (base.toLowerCase().endsWith(".txt")) base = base.substring(0, base.length() - 4);
        StringBuilder sb = new StringBuilder(); boolean capNext = true;
        for (char c : base.toCharArray()) {
            if (Character.isWhitespace(c) || c == '_' || c == '-') { sb.append(c); capNext = true; continue; }
            if (capNext) { sb.append(Character.toUpperCase(c)); capNext = false; } else sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private DraggableWindow findWindowAt ( double mouseX, double mouseY){
        ListIterator<DraggableWindow> it = openApps.listIterator(openApps.size());
        while (it.hasPrevious()) {
            DraggableWindow w = it.previous();
            if (!w.minimized && w.isInside(mouseX, mouseY, taskbarHeight)) return w;
        }
        return null;
    }

    public void bringToFront(DraggableWindow w) {
        if (w == null) return;
        w.minimized = false;
        w.removeRequested = false;
        if (!openApps.contains(w)) openApps.add(w);
        focusedWindow = w;
    }

    private DraggableWindow getTopWindow() {
        try {
            if (focusedWindow != null && openApps.contains(focusedWindow) && !focusedWindow.removeRequested && !focusedWindow.closing) return focusedWindow;
        } catch (Exception ignored) {}
        ListIterator<DraggableWindow> it = openApps.listIterator(openApps.size());
        while (it.hasPrevious()) {
            DraggableWindow w = it.previous();
            if (w == null) continue;
            if (!w.removeRequested && !w.closing) return w;
        }
        return null;
    }

    @Override
    public void tick () {
        super.tick();
        if (fanStarted && fanLoopSound != null && !ConfigHandler.experimentalEnabled() && !fanLoopSound.isFinished()) {
            fanLoopSound.requestFadeOut();
            fanStarted = false;
        }
    }

    @Override
    public boolean mouseClicked ( double mouseX, double mouseY, int button){
        float uiScale = ConfigHandler.uiScaleFactor();
        double sx = mouseX / uiScale, sy = mouseY / uiScale;
        int width = logicalWidth();
        int height = logicalHeight();

        try {
            if (this.windowSwitcher != null && this.windowSwitcher.isActive()) {
                if (this.windowSwitcher.mouseClicked(sx, sy, button)) return true;
                return true;
            }
        } catch (Exception ignored) {}

        DraggableWindow currentTop = getTopWindow();
        boolean hideTaskbar = (currentTop != null) && currentTop.exclusiveFullscreen;

        searchBox.setWidth(SEARCH_RECT_WIDTH);
        searchBox.setX(SEARCH_RECT_X);
        searchBox.setY(height - taskbarHeight + SEARCH_RECT_Y_OFFSET);

        super.mouseClicked(sx, sy, button);

        int mx = Math.round((float) sx);
        int my = Math.round((float) sy);

        int sRectX = searchBox.getX();
        int sRectY = searchBox.getY();
        int sRectW = searchBox.getWidth();
        int sRectH = SEARCH_RECT_HEIGHT;
        boolean clickedInSearchRect = mx >= sRectX && mx < sRectX + sRectW && my >= sRectY && my < sRectY + sRectH;
        if (!hideTaskbar && clickedInSearchRect) {
            searchBox.setFocused(true);
            try { LaptopKeySoundManager.playTrackpadClick(); } catch (Exception ignored) {}
            return true;
        } else {
            boolean clickedInSearchPopup = false;
            try {
                int tbY_early = height - taskbarHeight;
                if (!hideTaskbar && !searchBox.getValue().isEmpty() && !searchResults.isEmpty()) {
                    int popupW_early = Math.max(searchBox.getWidth(), 260);
                    int entries_early = Math.min(searchResults.size(), 6);
                    int popupH_early = entries_early * RESULT_HEIGHT + 6;
                    int popupX_early = searchBox.getX();
                    int popupY_early = tbY_early - popupH_early - 6; if (popupY_early < 6) popupY_early = 6;
                    if (mx >= popupX_early && mx < popupX_early + popupW_early && my >= popupY_early && my < popupY_early + popupH_early) {
                        clickedInSearchPopup = true;
                    }
                }
            } catch (Exception ignored) {}

            if (!clickedInSearchPopup) {
                try {
                    if (!searchBox.getValue().isEmpty()) searchBox.setValue("");
                } catch (Exception ignored) {}
                if (searchBox.isFocused()) searchBox.setFocused(false);
                searchResults.clear();

            }
        }

        if (button == 0) {
            try { LaptopKeySoundManager.playTrackpadClick(); } catch (Exception ignored) {}
        } else if (button == 1) {
            try { LaptopKeySoundManager.playMouseClick(); } catch (Exception ignored) {}
        }

        if (contextMenu != null) {
            if (contextMenu.mouseClicked(sx, sy, button)) return true;
            contextMenu = null;
            return true;
        }

        if (button == 1) {
            boolean clickedOnIcon = false;
            if (renamingIcon != null && renameBox != null) {
                int rx = Math.round(renamingIcon.displayX);
                int ry = Math.round(renamingIcon.displayY + renamingIcon.iconSize + 4);
                int rw = Math.max(80, renamingIcon.iconSize);
                int rh = 16;
                if (mx >= rx && mx < rx + rw && my >= ry && my < ry + rh) {
                    renameBox.setFocused(true);
                    return true;
                } else {
                    String newName = renameBox.getValue().trim();
                    if (!newName.isEmpty() && !newName.equals(renamingIcon.name)) {
                        File oldFile = new File(desktopDir, renamingIcon.name);
                        File newFile = new File(desktopDir, newName);
                        if (!newFile.exists()) {
                            if (oldFile.exists()) oldFile.renameTo(newFile);
                            FilesManager.getInstance().removeDesktopIcon(renamingIcon.name);
                            FilesManager.getInstance().addDesktopIcon(newName, renamingIcon.targetX, renamingIcon.targetY);
                            refreshDesktopIcons();
                        }
                    }
                    renamingIcon = null;
                    renameBox = null;
                    return false;
                }
            }
            for (DesktopIcon icon : desktopIcons)
                if (icon.isInside(sx, sy, iconSize)) {
                    clickedOnIcon = true;
                    break;
                }
            if (!clickedOnIcon && findWindowAt(sx, sy) == null) {
                showContextMenu((int) Math.round(sx), (int) Math.round(sy));
                return true;
            }
        }

        for (DraggableWindow w : openApps) {
            try {
                if (w.previewW > 0 && w.previewH > 0 && w.previewOpacity > 0.02f) {
                    int px = w.previewX, py = w.previewY, pw = w.previewW, ph = w.previewH;
                    if (mx >= px && mx < px + pw && my >= py && my < py + ph) {
                        if (w.minimized) w.restore();
                        this.bringToFront(w);
                        playClick();
                        return super.mouseClicked(sx, sy, button);
                    }
                }
            } catch (Exception ignored) {}
        }

        int tbY = height - taskbarHeight;
        if (!hideTaskbar && !searchBox.getValue().isEmpty() && !searchResults.isEmpty()) {
            int popupW = Math.max(searchBox.getWidth(), 260);
            int entries = Math.min(searchResults.size(), 6);
            int popupH = entries * RESULT_HEIGHT + 6;
            int popupX = searchBox.getX();
            int popupY = tbY - popupH - 6;
            if (popupY < 6) popupY = 6;
            if (mx >= popupX && mx < popupX + popupW && my >= popupY && my < popupY + popupH) {
                int idx = (int) ((my - (popupY + 6)) / RESULT_HEIGHT);
                if (idx >= 0 && idx < Math.min(searchResults.size(), 6)) {
                    searchResults.get(idx).action.run();
                    searchBox.setValue("");
                    searchResults.clear();
                    playClick();
                }
                return super.mouseClicked(sx, sy, button);
            }
        }

        DraggableWindow top = findWindowAt(sx, sy);
        if (top != null) {
            this.bringToFront(top);
            int[] rr = top.getRenderRect(taskbarHeight);
            if (my >= rr[1] && my < rr[1] + 26) {
                top.handleTitlebarClick(sx, sy, button, taskbarHeight);
                playClick();
                return super.mouseClicked(sx, sy, button);
            }
            boolean consumed = top.app.mouseClicked(top, sx, sy, button);
            if (consumed) {
                playClick();
                return super.mouseClicked(sx, sy, button);
            }
            return super.mouseClicked(sx, sy, button);
        }

        if (my >= tbY && my < height) {
            int tx = 6 + searchBox.getWidth() + 8;
            int perEntry = 44;
            int slotWidthForMouse = TASKBAR_SLOT_WIDTH_PX;
            int rightReserved = 120;
            int available = Math.max(0, width - tx - rightReserved);
            int maxEntries = Math.max(0, available / slotWidthForMouse);
            List<DraggableWindow> visibleWindows = new ArrayList<>();
            if (maxEntries > 0) {
                int start = Math.max(0, openApps.size() - maxEntries);
                for (int i = start; i < openApps.size(); i++) {
                    DraggableWindow w0 = openApps.get(i);
                    if (isTaskbarEligible(w0)) visibleWindows.add(w0);
                }
            }
            int tx2 = tx;
            for (DraggableWindow w : visibleWindows) {
                int cx = tx2 + slotWidthForMouse / 2;
                int iconLeft = cx - TASKBAR_ICON_SIZE_PX / 2;
                int iconRight = iconLeft + TASKBAR_ICON_SIZE_PX;
                int y0 = tbY + 2, y1 = height - 4;
                if (mx >= iconLeft && mx < iconRight && my >= y0 && my < y1) {
                    long now = System.currentTimeMillis();
                    boolean isDoubleClick = (lastTaskbarClickWindow == w) && (now - lastTaskbarClickTime) < DOUBLE_CLICK_MS;
                    lastTaskbarClickWindow = w;
                    lastTaskbarClickTime = now;

                    if (isDoubleClick) {
                        w.restore();
                        this.bringToFront(w);
                    } else {
                        if (w.minimized) {
                            w.restore();
                            this.bringToFront(w);
                        } else if (getTopWindow() != w) this.bringToFront(w);
                        else {
                            w.minimized = true;
                        }
                    }
                    playClick();
                    return super.mouseClicked(sx, sy, button);
                }
                tx2 += slotWidthForMouse;
            }
            selectedIcons.clear();
            return super.mouseClicked(sx, sy, button);
        }

        for (DesktopIcon di : desktopIcons) {
            if (di.isInside(sx, sy, iconSize)) {
                long now = System.currentTimeMillis();
                if (selectedIcons.contains(di) && (now - lastClickTime) < DOUBLE_CLICK_MS) {
                    di.onClick.run();
                    selectedIcons.clear();
                    iconPressed = null;
                    iconDragging = false;
                    playClick();
                } else {
                    selectedIcons.clear();
                    selectedIcons.add(di);
                    iconPressed = di;
                    iconDragging = false;
                    iconDragStartX = sx;
                    iconDragStartY = sy;
                    iconStartPositions.clear();
                    for (DesktopIcon ic : selectedIcons)
                        iconStartPositions.put(ic, new int[]{ic.targetX, ic.targetY});
                    lastClickTime = now;
                }
                return super.mouseClicked(sx, sy, button);
            }
        }

        selectedIcons.clear();
        iconPressed = null;
        iconDragging = false;
        selecting = true;
        selectStartX = selectEndX = (int) sx;
        selectStartY = selectEndY = (int) sy;
        return super.mouseClicked(sx, sy, button);
    }

    @Override
    public boolean mouseReleased ( double mouseX, double mouseY, int button){
        float uiScale = ConfigHandler.uiScaleFactor();
        double sx = mouseX / uiScale, sy = mouseY / uiScale;
        int width = logicalWidth();
        int height = logicalHeight();

        int mx = Math.round((float) sx);
        int my = Math.round((float) sy);

        if (iconDragging && !selectedIcons.isEmpty()) {
            for (DesktopIcon ic : selectedIcons) {
                ic.targetX = (ic.targetX / ICON_GRID) * ICON_GRID;
                ic.targetY = (ic.targetY / ICON_GRID) * ICON_GRID;
                FilesManager.getInstance().updateDesktopIconPosition(ic.name, ic.targetX, ic.targetY);
            }
        }

        if (selecting && !iconDragging) {
            int x0 = Math.min(selectStartX, selectEndX), y0 = Math.min(selectStartY, selectEndY);
            int x1 = Math.max(selectStartX, selectEndX), y1 = Math.max(selectStartY, selectEndY);
            selectedIcons.clear();
            for (DesktopIcon ic : desktopIcons) {
                if (renameBox != null) renameBox.mouseReleased(sx, sy, button);
                int ix0 = Math.round(ic.displayX), iy0 = Math.round(ic.displayY);
                int ix1 = ix0 + iconSize, iy1 = iy0 + iconSize;
                if (ix1 >= x0 && ix0 < x1 && iy1 >= y0 && iy0 < y1) selectedIcons.add(ic);
            }
        }
        iconDragging = false;
        iconPressed = null;
        iconStartPositions.clear();
        selecting = false;
        searchBox.mouseReleased(sx, sy, button);
        for (DraggableWindow w : openApps) w.mouseReleased(sx, sy, button);
        return super.mouseReleased(sx, sy, button);
    }

    @Override
    public boolean mouseDragged ( double mouseX, double mouseY, int button, double dx, double dy){
        float uiScale = ConfigHandler.uiScaleFactor();
        double sx = mouseX / uiScale, sy = mouseY / uiScale;
        if (iconPressed != null && button == 0) {
            if (!iconDragging) {
                iconDragging = true;
                iconStartPositions.clear();
                for (DesktopIcon ic : selectedIcons) iconStartPositions.put(ic, new int[]{ic.targetX, ic.targetY});
                iconDragStartX = sx;
                iconDragStartY = sy;
            }
            int deltaX = (int) (sx - iconDragStartX);
            int deltaY = (int) (sy - iconDragStartY);
            for (DesktopIcon ic : selectedIcons) {
                int[] s = iconStartPositions.getOrDefault(ic, new int[]{ic.targetX, ic.targetY});
                ic.targetX = s[0] + deltaX;
                ic.targetY = s[1] + deltaY;
            }
            return super.mouseDragged(sx, sy, button, dx, dy);
        }
        if (selecting && !iconDragging) {
            selectEndX = (int) sx;
            selectEndY = (int) sy;
            int x0 = Math.min(selectStartX, selectEndX), y0 = Math.min(selectStartY, selectEndY);
            int x1 = Math.max(selectStartX, selectEndX), y1 = Math.max(selectStartY, selectEndY);
            selectedIcons.clear();
            for (DesktopIcon ic : desktopIcons) {
                int ix0 = Math.round(ic.displayX), iy0 = Math.round(ic.displayY);
                int ix1 = ix0 + iconSize, iy1 = iy0 + iconSize;
                if (ix1 >= x0 && ix0 < x1 && iy1 >= y0 && iy0 < y1) selectedIcons.add(ic);
            }
        }
        DraggableWindow fw = findWindowAt(sx, sy);
        if (fw != null) fw.mouseDragged(sx, sy);
        searchBox.mouseDragged(sx, sy, button, dx, dy);
        return super.mouseDragged(sx, sy, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled ( double mouseX, double mouseY, double delta){
        float uiScale = ConfigHandler.uiScaleFactor();
        double sx = mouseX / uiScale, sy = mouseY / uiScale;
        DraggableWindow top = findWindowAt(sx, sy);
        if (top != null && !top.minimized) return top.mouseScrolled(sx, sy, delta);
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed ( int keyCode, int scanCode, int modifiers){
        if (keyCode == 258) {
            try {
                if (this.windowSwitcher == null) this.windowSwitcher = new ExactWindowSwitcher(this);
                this.windowSwitcher.toggle();
                return true;
            } catch (Exception ignored) {}
        }

        try { if (this.windowSwitcher != null && this.windowSwitcher.isActive()) return true; } catch (Exception ignored) {}

        if (keyCode == 341 || keyCode == 345) {
            ctrlPressed = true;
            return true;
        }
        if (keyCode == 340 || keyCode == 344) {
            shiftPressed = true;
            return true;
        }
        if (keyCode == 32) {
            try {
                LaptopKeySoundManager.playKey(' ');
            } catch (Exception ignored) {
            }
            sendTypingPacketMaybe();
            return true;
        }
        if (ctrlPressed) {
            switch (keyCode) {
                case 65:
                    selectAllIcons();
                    return true;
                case 67:
                    copySelectedIcons();
                    return true;
                case 86:
                    pasteFromClipboard();
                    return true;
                case 88:
                    cutSelectedIcons();
                    return true;
                case 78:
                    createNewFolderOnDesktop();
                    return true;
                case 83:
                    return handleAppSpecificKeybind(keyCode, scanCode, modifiers);
                case 87:
                    if (shiftPressed)
                        DraggableWindow.closeAllWindows();
                    else if (!openApps.isEmpty()) {
                        DraggableWindow topcw = getTopWindow();
                        if (topcw != null) {
                            topcw.requestClose();
                            playClick();
                        }
                    }
                    return true;
            }
        }
        if (keyCode == 292) {
            showDebugInfo = !showDebugInfo;
            return true;
        }
        if (keyCode == 256) {
            if (contextMenu != null) {
                contextMenu = null;
                return true;
            }
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        DraggableWindow topForKeys = getTopWindow();
        if (topForKeys != null && !topForKeys.minimized) {
            boolean consumed = topForKeys.app.keyPressed(topForKeys, keyCode, scanCode, modifiers);
            if (consumed) return true;
        }
        DraggableWindow topForHide = getTopWindow();
        boolean hideTaskbarForKeys = (topForHide != null) && topForHide.exclusiveFullscreen;
        if (!hideTaskbarForKeys && searchBox.isFocused()) return searchBox.keyPressed(keyCode, scanCode, modifiers);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased ( int keyCode, int scanCode, int modifiers){
        if (keyCode == 341 || keyCode == 345) {
            ctrlPressed = false;
            return true;
        }
        if (keyCode == 340 || keyCode == 344) {
            shiftPressed = false;
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped ( char typedChar, int keyCode){
        if (typedChar != 0 && typedChar != ' ' && !Character.isISOControl(typedChar)) {
            try {
                LaptopKeySoundManager.playKey(typedChar);
            } catch (Exception ignored) {
            }
            sendTypingPacketMaybe();
        }
        DraggableWindow topForChar = getTopWindow();
        if (topForChar != null && !topForChar.minimized) {
            boolean consumed = topForChar.app.charTyped(topForChar, typedChar, keyCode);
            if (consumed) return true;
        }
        if (searchBox.isFocused()) return searchBox.charTyped(typedChar, keyCode);
        return super.charTyped(typedChar, keyCode);
    }

    private boolean handleAppSpecificKeybind ( int keyCode, int scanCode, int modifiers){
        DraggableWindow top = getTopWindow();
        if (top != null && !top.minimized) return top.app.keyPressed(top, keyCode, scanCode, modifiers);
        return false;
    }

    private void playClick () {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private int logicalWidth() {
        return Math.round(this.width / ConfigHandler.uiScaleFactor());
    }

    private int logicalHeight() {
        return Math.round(this.height / ConfigHandler.uiScaleFactor());
    }

    public void openAppSingle (String name,int w, int h){
        for (DraggableWindow w0 : openApps) {
            if (w0.appName.equalsIgnoreCase(name)) {
                w0.restore();
                w0.removeRequested = false;
                this.bringToFront(w0);
                return;
            }
        }
        IApp app = AppFactory.create(name);
        if (app == null) return;
        int lw = logicalWidth();
        int lh = logicalHeight();
        int startX = Math.max(40, (lw - w) / 2);
        int startY = Math.max(40, (lh - h) / 3);
        DraggableWindow wdw = new DraggableWindow(name, app, Math.min(w, lw - 80), Math.min(h, lh - 80), startX, startY);
        openApps.add(wdw);
        this.bringToFront(wdw);
    }

    public void showContextMenu ( int x, int y){
        this.contextMenu = new DesktopContextMenu(this, x, y);
    }

    public void refreshDesktopIcons() {
        desktopIcons.clear();
        List<FilesManager.DesktopIconState> iconStates = FilesManager.getInstance().getDesktopIcons();
        for (FilesManager.DesktopIconState state : iconStates) {
            if (AppRegistry.getInstance().isInstalled(state.name)) {
                int gridX = (state.x / ICON_GRID) * ICON_GRID;
                int gridY = (state.y / ICON_GRID) * ICON_GRID;

                desktopIcons.add(new DesktopIcon(state.name, gridX, gridY, () -> {
                    if (state.name.endsWith(".txt")) openAppSingle("Files", 780, 520);
                    else openAppSingle(state.name, 900, 600);
                }));

                if (state.x != gridX || state.y != gridY) {
                    FilesManager.getInstance().updateDesktopIconPosition(state.name, gridX, gridY);
                }
            }
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        try {
            super.resize(minecraft, width, height);
        } catch (Throwable ignored) {}
        this.width = width;
        this.height = height;

        try {
            FilesManager fm = FilesManager.getInstance();
            if (fm != null && fm.isCurrentWallpaperColor()) {
                int color = fm.getCurrentWallpaperColor();
            }
        } catch (Exception ignored) {}
    }

    public void setIconSize ( int size){
        this.iconSize = size;
        for (DesktopIcon icon : desktopIcons) icon.iconSize = size;
    }
    public void refresh () {
        FilesManager.getInstance().saveState();
    }
    public void openSettingsApp () {
        openAppSingle("Settings", 520, 480);
    }
    public void sortIconsByName() {
        desktopIcons.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        arrangeIconsInGrid();
    }

    public void sortIconsByDate() {
        desktopIcons.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        arrangeIconsInGrid();
    }

    public void sortIconsBySize() {
        desktopIcons.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        arrangeIconsInGrid();
    }

    private void arrangeIconsInGrid() {
        int cols = Math.max(1, (logicalWidth() - 100) / (iconSize + 80));
        int x = 50;
        int y = 60;
        int col = 0;

        for (DesktopIcon icon : desktopIcons) {
            int gridX = (x / ICON_GRID) * ICON_GRID;
            int gridY = (y / ICON_GRID) * ICON_GRID;

            icon.targetX = gridX;
            icon.targetY = gridY;

            FilesManager.getInstance().updateDesktopIconPosition(icon.name, icon.targetX, icon.targetY);

            col++;
            if (col >= cols) {
                col = 0;
                x = 50;
                y += iconSize + 40;
            } else {
                x += iconSize + 80;
            }
        }
    }

    private void selectAllIcons () {
        selectedIcons.clear();
        selectedIcons.addAll(desktopIcons);
    }
    private void copySelectedIcons () {
    }
    private void cutSelectedIcons () {
    }
    private void pasteFromClipboard () {
    }

    public void createNewFolderOnDesktop () {
        String baseName = "New Folder";
        String name = baseName;
        int counter = 1;
        while (new File(desktopDir, name).exists()) {
            name = baseName + " (" + counter + ")";
            counter++;
        }
        File newFolder = new File(desktopDir, name);
        if (newFolder.mkdir()) {
            FilesManager.getInstance().addDesktopIcon(name, (int) (Math.random() * (logicalWidth() - 100)) + 50, (int) (Math.random() * (logicalHeight() - 100)) + 50);
            refreshDesktopIcons();
        }
    }

    public void createNewTextFileOnDesktop () {
        String name = "New Text File.txt";
        int counter = 1;
        while (new File(desktopDir, name).exists()) {
            name = "New Text File (" + counter + ").txt";
            counter++;
        }
        File newFile = new File(desktopDir, name);
        try {
            if (newFile.createNewFile()) {
                FilesManager.getInstance().addDesktopIcon(name, (int) (Math.random() * (logicalWidth() - 100)) + 50, (int) (Math.random() * (logicalHeight() - 100)) + 50);
                refreshDesktopIcons();
            }
        } catch (IOException e) {
            System.err.println("Failed to create new text file: " + e);
        }
    }

    private void sendTypingPacketMaybe () {
        long now = System.currentTimeMillis();
        if (now - lastKeyTypeMillis >= KEY_PACKET_COOLDOWN_MS) {
            lastKeyTypeMillis = now;
            try {
                NetworkHandler.sendToServer(new LaptopTypingPacket(devicePos));
            } catch (Exception ignored) {
            }
        }
    }

    private static class SearchResult {
        final String displayName;
        final ResourceLocation iconRes;
        final Runnable action;

        SearchResult(String d, ResourceLocation i, Runnable a) {
            displayName = d;
            iconRes = i;
            action = a;
        }
    }

    private static class DesktopIcon {
        final String name;
        int targetX, targetY;
        float displayX, displayY;
        final Runnable onClick;
        int iconSize = 32;

        DesktopIcon(String name, int x, int y, Runnable onClick) {
            this.name = name;
            this.targetX = (x / ICON_GRID) * ICON_GRID;
            this.targetY = (y / ICON_GRID) * ICON_GRID;
            this.displayX = this.targetX;
            this.displayY = this.targetY;
            this.onClick = onClick;
        }

        void render(GuiGraphics g, int mouseX, int mouseY, boolean selected, int currentIconSize) {
            this.iconSize = currentIconSize; int dx = Math.round(displayX), dy = Math.round(displayY);
            boolean hover = mouseX >= dx && mouseX < dx + iconSize && mouseY >= dy && mouseY < dy + iconSize;
            if (selected) g.fill(dx - 6, dy - 6, dx + iconSize + 6, dy + iconSize + 14, 0x2233AAFF);
            g.fill(dx - 1, dy + iconSize + 1, dx + iconSize, dy + iconSize + 3, 0xAA000000);
            String key = name.contains(".") ? null : normalizeAppNameForIcon(name);
            ResourceLocation iconRes = IconManager.getIconResource(key);
            try { g.blit(iconRes, dx + 2, dy + 2, 0, 0, iconSize - 4, iconSize - 4, iconSize - 4, iconSize - 4); } catch (Exception ignored) {}
            if (hover) g.fill(dx - 2, dy - 2, dx + iconSize + 2, dy + iconSize + 2, DraggableWindow.selectionOverlayColor());
            String displayName = toTitleCase(name);
            if (Minecraft.getInstance().font.width(displayName) > iconSize + 10) displayName = Minecraft.getInstance().font.plainSubstrByWidth(displayName, iconSize + 5) + "...";
            drawShadowedString(g, Minecraft.getInstance().font, displayName, dx, dy + iconSize + 4);

            DebugOverlay.drawHitbox(g, dx, dy, dx + iconSize, dy + iconSize, "icon:" + name);
        }
        boolean isInside(double mouseX, double mouseY, int currentIconSize) { return mouseX >= displayX && mouseX < displayX + currentIconSize && mouseY >= displayY && mouseY < displayY + currentIconSize; }
    }
    private void forceMessengerInstallation() {
        try {
            AppRegistry registry = AppRegistry.getInstance();
            if (!registry.isInstalled("messenger")) {
                System.out.println("[DesktopScreen] Messenger not installed, forcing installation...");
                registry.installApp("messenger", "Messenger", "WhatsApp-style messaging app", "1.0")
                        .thenRun(() -> {
                            System.out.println("[DesktopScreen] Messenger installed successfully");
                            refreshDesktopIcons();
                        });
            } else {
                System.out.println("[DesktopScreen] Messenger is already installed");
            }
        } catch (Exception e) {
            System.out.println("[DesktopScreen] Error installing messenger: " + e.getMessage());
        }
    }

    public List<DraggableWindow> getOpenApps() {
        return this.openApps;
    }
}
