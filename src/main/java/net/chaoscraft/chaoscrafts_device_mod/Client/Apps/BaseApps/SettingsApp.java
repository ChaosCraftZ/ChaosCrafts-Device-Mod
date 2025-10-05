package net.chaoscraft.chaoscrafts_device_mod.Client.Apps.BaseApps;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Font;
import net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager.IApp;
import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DraggableWindow;
import net.chaoscraft.chaoscrafts_device_mod.Core.AsyncorAPI.AsyncRuntime;
import net.chaoscraft.chaoscrafts_device_mod.Core.Util.FileSystem.FilesManager;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Settings UI (overhauled wallpaper UX + Accessibility category + optimizations)
 */
public class SettingsApp implements IApp {
    private DraggableWindow window;
    private EditBox accentColorInput;
    private boolean accentInputFocused = false;
    private final AsyncRuntime asyncRuntime = AsyncRuntime.get();
    private Font font;

    // Static settings storage
    private static final Map<String, Object> SETTINGS = new HashMap<>();
    private static File settingsFile;
    private static final AtomicBoolean SETTINGS_LOADED = new AtomicBoolean(false);

    // Accent color palette (kept)
    private final int[] accentPalette = new int[]{
            0xFF4C7BD1, 0xFFDB5C5C, 0xFF57C07D, 0xFFF0A84B,
            0xFF9B64E7, 0xFF2FB3C6, 0xFFD8D8D8, 0xFF000000
    };

    // Wallpaper state for UI
    private List<String> wallpaperList = new ArrayList<>();
    private boolean wallpapersLoaded = false;
    private String selectedWallpaper = null; // preview & selected

    // Thumbnail scrolling state
    private float thumbOffset = 0f; // horizontal pixel offset
    private float thumbScrollVelocity = 0f;
    private boolean draggingThumb = false;
    private int thumbDragStartX = 0;
    private float thumbDragStartOffset = 0f;

    // visual settings
    // Removed unused HSV fields and color picker flag

    // UI categories
    private final String[] categories = new String[]{"General", "Appearance", "Accessibility", "Wallpapers"};
    private int selectedCategoryIndex = 2; // default to Accessibility

    // Hover smoothing for thumbnails
    private int hoverIndex = -1;
    private float hoverAlpha = 0f;

    // constructor/static init: load settings
    static {
        loadSettingsAsync().exceptionally(e -> {
            try { Minecraft.getInstance().execute(() -> setDefaultSettings()); } catch (Exception ignored) {}
            return null;
        });
    }

    private static CompletableFuture<Void> loadSettingsAsync() {
        return AsyncRuntime.get().submitIo(() -> {
            settingsFile = new File(FilesManager.getPlayerDataDir(), "settings.json");
            if (settingsFile.exists()) {
                try (FileReader reader = new FileReader(settingsFile)) {
                    Gson gson = new Gson();
                    Type type = new TypeToken<HashMap<String, Object>>(){}.getType();
                    Map<String, Object> loadedSettings = gson.fromJson(reader, type);
                    if (loadedSettings != null) {
                        SETTINGS.clear(); SETTINGS.putAll(loadedSettings);
                        AsyncRuntime.get().runOnClientThread(() -> { applyLoadedSettings(); SETTINGS_LOADED.set(true); });
                    }
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            } else {
                AsyncRuntime.get().runOnClientThread(() -> { setDefaultSettings(); saveSettingsAsync(); SETTINGS_LOADED.set(true); });
            }
        });
    }

    public static void loadSettings() { loadSettingsAsync(); }

    private static void applyLoadedSettings() {
        if (SETTINGS.containsKey("darkTheme") && SETTINGS.get("darkTheme") instanceof Boolean) DraggableWindow.darkTheme = (Boolean) SETTINGS.get("darkTheme");
        if (SETTINGS.containsKey("accentColor") && SETTINGS.get("accentColor") instanceof String) {
            try { DraggableWindow.accentColorARGB = 0xFF000000 | Integer.parseInt(((String)SETTINGS.get("accentColor")).replace("#",""), 16); } catch (Exception ignored) {}
        }
        // Wallpaper handling: support both file-based wallpaper and solid-color wallpaper
        boolean isColor = false;
        if (SETTINGS.containsKey("wallpaperIsColor") && SETTINGS.get("wallpaperIsColor") instanceof Boolean) {
            isColor = (Boolean) SETTINGS.get("wallpaperIsColor");
        }
        if (isColor) {
            // color may be stored as a number (Double from Gson) or as an Integer; handle both
            if (SETTINGS.containsKey("wallpaperColor")) {
                Object c = SETTINGS.get("wallpaperColor");
                try {
                    int colorInt;
                    if (c instanceof Number) colorInt = ((Number)c).intValue(); else colorInt = Integer.parseInt(c.toString());
                    FilesManager.getInstance().setCurrentWallpaperColor(colorInt);
                } catch (Exception ignored) {}
            }
        } else {
            if (SETTINGS.containsKey("wallpaper") && SETTINGS.get("wallpaper") instanceof String) {
                FilesManager.getInstance().setCurrentWallpaperName((String)SETTINGS.get("wallpaper"));
            }
        }
    }

    private static void setDefaultSettings() {
        SETTINGS.put("darkTheme", DraggableWindow.darkTheme);
        SETTINGS.put("accentColor", String.format("#%06X", DraggableWindow.accentColorARGB & 0xFFFFFF));
        // Persist wallpaper choice with explicit color flag
        FilesManager fm = FilesManager.getInstance();
        if (fm != null && fm.isCurrentWallpaperColor()) {
            SETTINGS.put("wallpaperIsColor", true);
            SETTINGS.put("wallpaperColor", fm.getCurrentWallpaperColor());
            SETTINGS.remove("wallpaper");
        } else {
            SETTINGS.put("wallpaperIsColor", false);
            SETTINGS.put("wallpaper", fm != null ? fm.getCurrentWallpaperName() : null);
        }
    }

    private static CompletableFuture<Void> saveSettingsAsync() {
        return AsyncRuntime.get().submitIo(() -> {
            try { settingsFile.getParentFile().mkdirs(); try (FileWriter w = new FileWriter(settingsFile)) { Gson g = new GsonBuilder().setPrettyPrinting().create(); g.toJson(SETTINGS, w); } } catch (IOException e) { throw new RuntimeException(e); }
        });
    }

    @Override
    public void onOpen(DraggableWindow window) {
    this.window = window;
    this.font = Minecraft.getInstance().font;
    this.accentColorInput = new EditBox(font, 0, 0, 80, 16, Component.literal("Accent"));
        this.accentColorInput.setMaxLength(7);
        if (SETTINGS_LOADED.get()) initializeUI(); else {
            asyncRuntime.submitCompute(() -> {
                while (!SETTINGS_LOADED.get()) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                asyncRuntime.runOnClientThread(this::initializeUI);
            });
        }
    }

    private void initializeUI() {
        this.accentColorInput.setValue(String.format("#%06X", DraggableWindow.accentColorARGB & 0xFFFFFF));
        loadWallpapersAsync();
    }

    private void loadWallpapersAsync() {
        asyncRuntime.submitIo(() -> {
            List<String> list = FilesManager.getInstance().listWallpapers();
            asyncRuntime.runOnClientThread(() -> {
                wallpaperList = list;
                wallpapersLoaded = true;
                String cur = FilesManager.getInstance().getCurrentWallpaperName();
                if (cur != null && wallpaperList.contains(cur)) selectedWallpaper = cur;
                // Prefetch previews in small batches on main thread to avoid hitching
                prefetchPreviewsInBatches();
            });
        });
    }

    // Prefetch previews gradually on the main thread to avoid large frame stalls
    private void prefetchPreviewsInBatches() {
        final int batchSize = 4;
        asyncRuntime.submitCompute(() -> {
            for (int i = 0; i < wallpaperList.size(); i += batchSize) {
                final int start = i;
                asyncRuntime.runOnClientThread(() -> {
                    int end = Math.min(start + batchSize, wallpaperList.size());
                    for (int j = start; j < end; j++) {
                        try { FilesManager.getInstance().getWallpaperPreviewResource(wallpaperList.get(j)); } catch (Exception ignored) {}
                    }
                });
                try { Thread.sleep(40); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        });
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 32, cw = r[2] - 16;

        // Sidebar for categories
        int sidebarW = 140;
        guiGraphics.fill(cx, cy, cx + sidebarW, cy + 220, DraggableWindow.darkTheme ? 0xFF1A1A1A : 0xFFEFEFEF);
        for (int i = 0; i < categories.length; i++) {
            int itemY = cy + 8 + i * 34;
            int textColor = (i == selectedCategoryIndex) ? DraggableWindow.accentColorARGB : (DraggableWindow.darkTheme ? 0xFFDDDDDD : 0xFF333333);
            guiGraphics.drawString(this.font != null ? this.font : Minecraft.getInstance().font, Component.literal(categories[i]), cx + 12, itemY, textColor, false);
        }

        // Main area
        int mainX = cx + sidebarW + 12;
        int mainW = cw - sidebarW - 24;

        // Render based on selected category
        String cat = categories[selectedCategoryIndex];
    guiGraphics.drawString(this.font != null ? this.font : Minecraft.getInstance().font, Component.literal(cat), mainX + 8, cy + 6, 0xFFFFFFFF, false);

        if ("Accessibility".equals(cat)) {
            int by = cy + 28;
            // Dark Mode toggle
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Dark Mode"), mainX + 8, by + 6, 0xFFFFFFFF, false);
            int toggleX = mainX + 150, toggleY = by + 2, toggleW = 40, toggleH = 16;
            int toggleBg = DraggableWindow.darkTheme ? DraggableWindow.accentColorARGB : 0xFF777777;
            guiGraphics.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, toggleBg);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(DraggableWindow.darkTheme ? "On" : "Off"), toggleX + 6, toggleY + 2, 0xFF000000, false);

            // Accent color input + palette
            int accentY = by + 34;
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Accent Color"), mainX + 8, accentY, 0xFFFFFFFF, false);
            accentColorInput.setX(mainX + 8); accentColorInput.setY(accentY + 14); accentColorInput.render(guiGraphics, mouseRelX, mouseRelY, partialTick);

            // Palette swatches
            int swX = mainX + 120, swY = accentY + 14; int swSize = 18; for (int i=0;i<accentPalette.length;i++){
                int sx = swX + i*(swSize+6);
                guiGraphics.fill(sx, swY, sx+swSize, swY+swSize, accentPalette[i]);
                // border for selected
                if ((DraggableWindow.accentColorARGB & 0x00FFFFFF) == (accentPalette[i] & 0x00FFFFFF)) {
                    guiGraphics.fill(sx-2, swY-2, sx+swSize+2, swY+swSize+2, 0x55FFFFFF);
                }
                // hover outline
                if (mouseRelX >= sx && mouseRelX <= sx+swSize && mouseRelY >= swY && mouseRelY <= swY+swSize) {
                    guiGraphics.fill(sx, swY, sx+swSize, swY+swSize, 0x33FFFFFF);
                }
            }

            // Additional accessibility options (simple examples)
            int otherY = accentY + 48;
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("High Contrast Text"), mainX + 8, otherY, 0xFFFFFFFF, false);
            boolean highContrast = SETTINGS.containsKey("highContrast") && (Boolean)SETTINGS.get("highContrast") == true;
            int hcX = mainX + 220, hcY = otherY - 2; guiGraphics.fill(hcX, hcY, hcX+40, hcY+16, highContrast ? DraggableWindow.accentColorARGB : 0xFF777777);

            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Large Text"), mainX + 8, otherY + 22, 0xFFFFFFFF, false);
            boolean largeText = SETTINGS.containsKey("largeText") && (Boolean)SETTINGS.get("largeText") == true;
            int ltX = mainX + 220, ltY = otherY + 20; guiGraphics.fill(ltX, ltY, ltX+40, ltY+16, largeText ? DraggableWindow.accentColorARGB : 0xFF777777);

            return;
        }

        if ("Wallpapers".equals(cat)) {
            int by = cy + 28;
            if (!wallpapersLoaded) {
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Loading wallpapers..."), mainX + 8, by + 10, 0xFF999999, false);
                return;
            }

            // Large preview area at the top
            int previewX = mainX + mainW / 6; // center-ish
            int previewY = by + 8;
            int previewW = mainW - (mainW / 3);
            int previewH = 180;
            guiGraphics.fill(previewX - 4, previewY - 4, previewX + previewW + 4, previewY + previewH + 4, 0xFF222222);
            guiGraphics.fill(previewX, previewY, previewX + previewW, previewY + previewH, 0xFF0F0F0F);

            String previewName = selectedWallpaper != null ? selectedWallpaper : FilesManager.getInstance().getCurrentWallpaperName();
            // If the current wallpaper is a solid color, show that color; otherwise show image preview if available
            boolean curIsColor = FilesManager.getInstance().isCurrentWallpaperColor();
            if (curIsColor) {
                int col = FilesManager.getInstance().getCurrentWallpaperColor();
                guiGraphics.fill(previewX + 8, previewY + 8, previewX + previewW - 8, previewY + previewH - 8, col);
            } else {
                ResourceLocation previewRes = previewName == null ? null : FilesManager.getInstance().getWallpaperPreviewResource(previewName);
                if (previewRes != null) {
                    try { guiGraphics.blit(previewRes, previewX + 8, previewY + 8, 0, 0, previewW - 16, previewH - 16, previewW - 16, previewH - 16); } catch (Exception ignored) {}
                } else {
                    guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("No preview"), previewX + 8, previewY + 8, 0xFFAAAAAA, false);
                }
            }

            // Thumbnails strip (below the main preview)
            int thumbY = previewY + previewH + 14;
            int thumbSize = 64;
            int thumbPad = 10;
            int thumbsVisibleW = previewW - 8; // leave a small margin

            // compute total width and clamp thumbOffset
            int totalThumbsW = wallpaperList.size() * (thumbSize + thumbPad);
            float maxOffset = Math.max(0, totalThumbsW - thumbsVisibleW + 4);
            clampThumbOffset(maxOffset);

            // track hover and compute smooth alpha
            int newHoverIndex = -1;
            for (int i = 0; i < wallpaperList.size(); i++) {
                int baseX = previewX + 4 + i * (thumbSize + thumbPad) - Math.round(thumbOffset);
                if (baseX + thumbSize < previewX || baseX > previewX + thumbsVisibleW) continue; // skip offscreen

                // detect hover (use unscaled bounds)
                if (mouseRelX >= baseX && mouseRelX <= baseX + thumbSize && mouseRelY >= thumbY && mouseRelY <= thumbY + thumbSize) {
                    newHoverIndex = i;
                }
            }

            if (newHoverIndex != hoverIndex) {
                hoverIndex = newHoverIndex;
                hoverAlpha = 0f;
            }
            if (hoverIndex != -1) hoverAlpha = Math.min(1f, hoverAlpha + 0.14f); else hoverAlpha = Math.max(0f, hoverAlpha - 0.14f);

            // now render thumbnails with hover scale/overlay
            for (int i = 0; i < wallpaperList.size(); i++) {
                int baseX = previewX + 4 + i * (thumbSize + thumbPad) - Math.round(thumbOffset);
                if (baseX + thumbSize < previewX || baseX > previewX + thumbsVisibleW) continue; // skip offscreen
                String name = wallpaperList.get(i);

                float hoverProgress = (i == hoverIndex) ? hoverAlpha : 0f;
                float scale = 1.0f + 0.12f * hoverProgress; // up to +12%
                int scaledSize = Math.round(thumbSize * scale);
                int centerX = baseX + thumbSize / 2;
                int drawX = centerX - scaledSize / 2;
                int drawY = thumbY + (thumbSize - scaledSize) / 2;

                // background/border
                guiGraphics.fill(drawX - 2, drawY - 2, drawX + scaledSize + 2, drawY + scaledSize + 2, 0xFF111111);

                ResourceLocation thumb = FilesManager.getInstance().getWallpaperPreviewResource(name);
                if (thumb != null) {
                    try {
                        guiGraphics.blit(thumb, drawX, drawY, 0, 0, scaledSize, scaledSize, scaledSize, scaledSize);
                    } catch (Exception ignored) {
                        guiGraphics.fill(drawX, drawY, drawX + scaledSize, drawY + scaledSize, 0xFF333333);
                    }
                } else {
                    guiGraphics.fill(drawX, drawY, drawX + scaledSize, drawY + scaledSize, 0xFF333333);
                }

                // hover overlay: compute alpha smoothly
                if (hoverProgress > 0f) {
                    int alpha = Math.round(hoverProgress * 0x66); // up to 0x66 alpha
                    int overlay = (alpha << 24);
                    guiGraphics.fill(drawX, drawY, drawX + scaledSize, drawY + scaledSize, overlay);
                }

                // selected outline
                boolean isSel = name.equals(selectedWallpaper) || (selectedWallpaper == null && name.equals(FilesManager.getInstance().getCurrentWallpaperName()));
                if (isSel) guiGraphics.fill(drawX - 2, drawY - 2, drawX + scaledSize + 2, drawY + scaledSize + 2, 0x66FFFFFF);
            }

            // Simple scrollbar indicator under thumbnails
            int scrollbarX = previewX + 4;
            int scrollbarY = thumbY + thumbSize + 10;
            int scrollbarH = 6;
            guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + thumbsVisibleW, scrollbarY + scrollbarH, 0xFF222222);
            if (!wallpaperList.isEmpty()) {
                float viewRatio = Math.min(1f, (float) thumbsVisibleW / (float) totalThumbsW);
                int handleW = Math.max(16, Math.round(thumbsVisibleW * viewRatio));
                int handleX = scrollbarX + Math.round((thumbsVisibleW - handleW) * (thumbOffset / Math.max(1, maxOffset)));
                guiGraphics.fill(handleX, scrollbarY, handleX + handleW, scrollbarY + scrollbarH, 0xFF555555);
            }

            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Put PNG/JPG in chaoscrafts_device_mod/wallpapers"), mainX, scrollbarY + scrollbarH + 8, 0xFFBBBBBB, false);
            // Render solid-color presets under the thumbnails
            int presetsX = mainX + 8;
            int presetsY = scrollbarY + scrollbarH + 28;
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Solid color presets:"), presetsX, presetsY, 0xFFCCCCCC, false);
            int[] presets = new int[]{0xFF000000, 0xFFFFFFFF, 0xFF2B2B2B, 0xFF1E90FF, 0xFF57C07D, 0xFFF0A84B};
            int sx = presetsX + 140, sy = presetsY;
            for (int i = 0; i < presets.length; i++) {
                int px = sx + i * 26;
                guiGraphics.fill(px, sy, px + 20, sy + 20, presets[i]);
                if (FilesManager.getInstance().isCurrentWallpaperColor() && (FilesManager.getInstance().getCurrentWallpaperColor() & 0x00FFFFFF) == (presets[i] & 0x00FFFFFF)) {
                    guiGraphics.fill(px - 2, sy - 2, px + 22, sy + 22, 0x66FFFFFF);
                }
            }
            return;
        }

        // Fallback: show basic appearance controls
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Accent Color:"), mainX + 8, cy + 36, 0xFFFFFFFF, false);
        accentColorInput.setX(mainX + 8);
        accentColorInput.setY(cy + 56);
        accentColorInput.render(guiGraphics, mouseRelX, mouseRelY, partialTick);

    }

    private void clampThumbOffset(float maxOffset) {
        if (thumbOffset < 0) thumbOffset = 0;
        if (thumbOffset > maxOffset) thumbOffset = maxOffset;
    }

    @Override
    public boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8; int cw = r[2] - 16;
        int sidebarW = 140; int mainX = cx + sidebarW + 12; int mainW = cw - sidebarW - 24;

        // Check sidebar category clicks
        for (int i = 0; i < categories.length; i++) {
            int itemY = r[1] + 32 + 8 + i * 34;
            if (mouseRelX >= cx && mouseRelX <= cx + sidebarW && mouseRelY >= itemY && mouseRelY <= itemY + 20) {
                selectedCategoryIndex = i;
                return true;
            }
        }

        String cat = categories[selectedCategoryIndex];

        // Handle Accessibility toggles & palette clicks
        if ("Accessibility".equals(cat)) {
            int by = r[1] + 32 + 28;
            int toggleX = mainX + 150, toggleY = by + 2, toggleW = 40, toggleH = 16;
            if (mouseRelX >= toggleX && mouseRelX <= toggleX+toggleW && mouseRelY >= toggleY && mouseRelY <= toggleY+toggleH) {
                DraggableWindow.darkTheme = !DraggableWindow.darkTheme;
                SETTINGS.put("darkTheme", DraggableWindow.darkTheme);
                saveSettingsAsync();
                return true;
            }
            int accentY = by + 34;
            int swX = mainX + 120, swY = accentY + 14; int swSize = 18; for (int i=0;i<accentPalette.length;i++){
                int sx = swX + i*(swSize+6);
                if (mouseRelX >= sx && mouseRelX <= sx+swSize && mouseRelY >= swY && mouseRelY <= swY+swSize) {
                    DraggableWindow.accentColorARGB = accentPalette[i];
                    SETTINGS.put("accentColor", String.format("#%06X", DraggableWindow.accentColorARGB & 0xFFFFFF));
                    saveSettingsAsync();
                    return true;
                }
            }
            // Additional toggles
            int otherY = accentY + 48;
            int hcX = mainX + 220, hcY = otherY - 2; if (mouseRelX >= hcX && mouseRelX <= hcX+40 && mouseRelY >= hcY && mouseRelY <= hcY+16) {
                boolean cur = SETTINGS.containsKey("highContrast") && (Boolean)SETTINGS.get("highContrast") == true;
                SETTINGS.put("highContrast", !cur); saveSettingsAsync(); return true; }
            int ltX = mainX + 220, ltY = otherY + 20; if (mouseRelX >= ltX && mouseRelX <= ltX+40 && mouseRelY >= ltY && mouseRelY <= ltY+16) {
                boolean cur = SETTINGS.containsKey("largeText") && (Boolean)SETTINGS.get("largeText") == true;
                SETTINGS.put("largeText", !cur); saveSettingsAsync(); return true; }

            // Accent text input focus
            int aiX = mainX + 8, aiY = accentY + 14, aiW = 80, aiH = 16;
            if (mouseRelX >= aiX && mouseRelX <= aiX+aiW && mouseRelY >= aiY && mouseRelY <= aiY+aiH) {
                accentInputFocused = true; return true;
            } else accentInputFocused = false;

            return false;
        }

        if ("Wallpapers".equals(cat)) {
            int by = r[1] + 32 + 28;
            int previewX = mainX + mainW / 6; // match render
            int previewY = by + 8;
            int previewW = mainW - (mainW / 3);
            int previewH = 180;
            int thumbY = previewY + previewH + 14; int thumbSize = 64; int thumbPad = 10;

            // detect thumbnail click & start dragging
            for (int i = 0; i < wallpaperList.size(); i++) {
                int baseX = previewX + 4 + i * (thumbSize + thumbPad) - Math.round(thumbOffset);
                if (mouseRelX >= baseX && mouseRelX <= baseX + thumbSize && mouseRelY >= thumbY && mouseRelY <= thumbY + thumbSize) {
                    String name = wallpaperList.get(i);
                    selectedWallpaper = name; // update main preview immediately
                    FilesManager.getInstance().setCurrentWallpaperName(name); // apply in-game
                    // update settings to record file wallpaper
                    SETTINGS.put("wallpaper", name);
                    SETTINGS.put("wallpaperIsColor", false);
                    SETTINGS.remove("wallpaperColor");
                    saveSettingsAsync();
                    return true;
                }
            }

            // start dragging thumbnails if clicked within strip area
            if (mouseRelX >= previewX + 4 && mouseRelX <= previewX + previewW - 4 && mouseRelY >= thumbY && mouseRelY <= thumbY + thumbSize) {
                draggingThumb = true; thumbDragStartX = (int) mouseRelX; thumbDragStartOffset = thumbOffset; return true;
            }

            // Compute scrollbar position/size (same as renderContent) so presets can reference it
            int scrollbarY = thumbY + thumbSize + 10;
            int scrollbarH = 6;

            // Check color presets click
            int presetsX = mainX + 8;
            int presetsY = scrollbarY + scrollbarH + 28;
            int[] presets = new int[]{0xFF000000, 0xFFFFFFFF, 0xFF2B2B2B, 0xFF1E90FF, 0xFF57C07D, 0xFFF0A84B};
            int pxStart = presetsX + 140; int py = presetsY;
            for (int i = 0; i < presets.length; i++) {
                int px = pxStart + i * 26;
                if (mouseRelX >= px && mouseRelX <= px + 20 && mouseRelY >= py && mouseRelY <= py + 20) {
                    int chosen = presets[i];
                    FilesManager.getInstance().setCurrentWallpaperColor(chosen);
                    selectedWallpaper = null;
                    SETTINGS.put("wallpaperIsColor", true);
                    SETTINGS.put("wallpaperColor", chosen);
                    SETTINGS.remove("wallpaper");
                    saveSettingsAsync();
                    return true;
                }
            }
            return false;
        }

        // fallback: check accent input
        int[] rr = window.getRenderRect(26); int mainCX = rr[0]+8 + 140 + 12; int aiX = mainCX + 8, aiY = rr[1]+32 + 36, aiW = 80, aiH = 16;
        if (mouseRelX >= aiX && mouseRelX <= aiX+aiW && mouseRelY >= aiY && mouseRelY <= aiY+aiH) { accentInputFocused = true; return true; }
        accentInputFocused = false;
        return false;
    }

    // allow scrolling thumbnails with mouse wheel
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!wallpapersLoaded || wallpaperList.isEmpty()) return false;
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8; int cw = r[2] - 16; int sidebarW = 140; int mainW = cw - sidebarW - 24;
        int wallpaperY = (r[1] + 32) + 28;
        int previewX = (cx + sidebarW + 12) + mainW / 6; int previewY = wallpaperY + 8; int previewW = mainW - (mainW / 3); int previewH = 180; int thumbY = previewY + previewH + 14; int thumbSize = 64;
        if (mouseX >= previewX + 4 && mouseX <= previewX + (previewW - 8) && mouseY >= thumbY && mouseY <= thumbY + thumbSize) {
            // add to velocity for smooth inertial scrolling
            thumbScrollVelocity += (float)(-delta * 18.0); // explicit cast to float
            return true;
        }
        return false;
    }

    @Override public boolean charTyped(DraggableWindow window, char codePoint, int modifiers) { if (accentInputFocused) { boolean r = accentColorInput.charTyped(codePoint, modifiers); if (r) { try{ String hex = accentColorInput.getValue().replace("#",""); if (hex.length()==6) { DraggableWindow.accentColorARGB = 0xFF000000 | Integer.parseInt(hex,16); SETTINGS.put("accentColor", String.format("#%06X", DraggableWindow.accentColorARGB & 0xFFFFFF)); saveSettingsAsync(); } } catch (NumberFormatException ignored) {} } return r; } return false; }
    @Override public boolean keyPressed(DraggableWindow window, int keyCode, int scanCode, int modifiers) { if (accentInputFocused) return accentColorInput.keyPressed(keyCode, scanCode, modifiers); return false; }
    @Override public void mouseReleased(DraggableWindow window, double mouseRelX, double mouseRelY, int button) { draggingThumb = false; }
    @Override public boolean mouseDragged(DraggableWindow window, double mouseRelX, double mouseRelY, double dx, double dy) {
        if (draggingThumb) {
            // update thumbOffset based on drag delta
            float delta = (float) (mouseRelX - thumbDragStartX);
            thumbOffset = thumbDragStartOffset - delta;
            // clamp while dragging
            int[] r = window.getRenderRect(26);
            int cw = r[2] - 16; int sidebarW = 140; int mainW = cw - sidebarW - 24;
            int previewW = mainW - (mainW / 3);
            int thumbsVisibleW = previewW - 8;
            int thumbSize = 64; int thumbPad = 10;
            int totalThumbsW = wallpaperList.size() * (thumbSize + thumbPad);
            float maxOffset = Math.max(0, totalThumbsW - thumbsVisibleW + 4);
            clampThumbOffset(maxOffset);
            return true;
        }
        return false;
    }
    @Override public boolean onClose(DraggableWindow window) { return true; }
    @Override public void tick() {
        // smooth scrolling velocity decay
        if (Math.abs(thumbScrollVelocity) > 0.01f) {
            thumbOffset += thumbScrollVelocity;
            thumbScrollVelocity *= 0.86f;
            // clamp against bounds
            int[] r = window.getRenderRect(26);
            int cw = r[2] - 16;
            int sidebarW = 140; int mainW = cw - sidebarW - 24;
            int previewW = mainW - (mainW / 3);
            int thumbsVisibleW = previewW - 8;
            int thumbSize = 64; int thumbPad = 10;
            int totalThumbsW = wallpaperList.size() * (thumbSize + thumbPad);
            float maxOffset = Math.max(0, totalThumbsW - thumbsVisibleW + 4);
            if (thumbOffset < 0) { thumbOffset = 0; thumbScrollVelocity = 0; }
            if (thumbOffset > maxOffset) { thumbOffset = maxOffset; thumbScrollVelocity = 0; }
        }
    }
}

