package net.chaoscraft.chaoscrafts_device_mod.client.app;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;
import net.chaoscraft.chaoscrafts_device_mod.client.fs.FilesManager;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DraggableWindow;
import net.minecraft.resources.ResourceLocation;
import net.chaoscraft.chaoscrafts_device_mod.ConfigHandler;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DebugOverlay;

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

public class SettingsApp implements IApp {
    private DraggableWindow window;
    private EditBox accentColorInput;
    private boolean accentInputFocused = false;
    private final AsyncTaskManager asyncManager = AsyncTaskManager.getInstance();

    private static final Map<String, Object> SETTINGS = new HashMap<>();
    private static File settingsFile;
    private static final AtomicBoolean SETTINGS_LOADED = new AtomicBoolean(false);

    private final int[] accentPalette = new int[]{
            0xFF4C7BD1, 0xFFDB5C5C, 0xFF57C07D, 0xFFF0A84B,
            0xFF9B64E7, 0xFF2FB3C6, 0xFFD8D8D8, 0xFF000000
    };

    private List<String> wallpaperList = new ArrayList<>();
    private boolean wallpapersLoaded = false;
    private String selectedWallpaper = null;

    private float thumbOffset = 0f;
    private float thumbScrollVelocity = 0f;
    private boolean draggingThumb = false;
    private int thumbDragStartX = 0;
    private float thumbDragStartOffset = 0f;

    private final String[] categories = new String[]{"General", "Appearance", "Accessibility", "Wallpapers"};
    private int selectedCategoryIndex = 2;

    private int hoverIndex = -1;
    private float hoverAlpha = 0f;

    static {
        loadSettingsAsync().exceptionally(e -> {
            try { Minecraft.getInstance().execute(() -> setDefaultSettings()); } catch (Exception ignored) {}
            return null;
        });
    }

    private static CompletableFuture<Void> loadSettingsAsync() {
        return AsyncTaskManager.getInstance().submitIOTask(() -> {
            settingsFile = new File(FilesManager.getPlayerDataDir(), "settings.json");
            if (settingsFile.exists()) {
                try (FileReader reader = new FileReader(settingsFile)) {
                    Gson gson = new Gson();
                    Type type = new TypeToken<HashMap<String, Object>>(){}.getType();
                    Map<String, Object> loadedSettings = gson.fromJson(reader, type);
                    if (loadedSettings != null) {
                        SETTINGS.clear(); SETTINGS.putAll(loadedSettings);
                        Minecraft.getInstance().execute(() -> { applyLoadedSettings(); SETTINGS_LOADED.set(true); });
                    }
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            } else {
                Minecraft.getInstance().execute(() -> { setDefaultSettings(); saveSettingsAsync(); SETTINGS_LOADED.set(true); });
            }
        });
    }

    public static void loadSettings() { loadSettingsAsync(); }

    private static void applyLoadedSettings() {
        if (SETTINGS.containsKey("darkTheme") && SETTINGS.get("darkTheme") instanceof Boolean) {
            DraggableWindow.darkTheme = (Boolean) SETTINGS.get("darkTheme");
        } else {
            DraggableWindow.darkTheme = true;
            SETTINGS.put("darkTheme", true);
            saveSettingsAsync();
        }

        if (SETTINGS.containsKey("accentColor") && SETTINGS.get("accentColor") instanceof String) {
            try { DraggableWindow.accentColorARGB = 0xFF000000 | Integer.parseInt(((String)SETTINGS.get("accentColor")).replace("#",""), 16); } catch (Exception ignored) {}
        }
        boolean isColor = false;
        if (SETTINGS.containsKey("wallpaperIsColor") && SETTINGS.get("wallpaperIsColor") instanceof Boolean) {
            isColor = (Boolean) SETTINGS.get("wallpaperIsColor");
        }
        if (isColor) {
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
        return AsyncTaskManager.getInstance().submitIOTask(() -> {
            try { settingsFile.getParentFile().mkdirs(); try (FileWriter w = new FileWriter(settingsFile)) { Gson g = new GsonBuilder().setPrettyPrinting().create(); g.toJson(SETTINGS, w); } } catch (IOException e) { throw new RuntimeException(e); }
        });
    }

    @Override
    public void onOpen(DraggableWindow window) {
        this.window = window;
        this.accentColorInput = new EditBox(Minecraft.getInstance().font, 0, 0, 80, 16, Component.literal("Accent"));
        this.accentColorInput.setMaxLength(7);
        if (SETTINGS_LOADED.get()) initializeUI(); else {
            asyncManager.submitCPUTask(() -> { while (!SETTINGS_LOADED.get()) { try { Thread.sleep(10);} catch (InterruptedException ex){Thread.currentThread().interrupt();break;} } asyncManager.executeOnMainThread(this::initializeUI); });
        }
    }

    private void initializeUI() {
        this.accentColorInput.setValue(String.format("#%06X", DraggableWindow.accentColorARGB & 0xFFFFFF));
        loadWallpapersAsync();
    }

    private void loadWallpapersAsync() {
        asyncManager.submitIOTask(() -> {
            List<String> list = FilesManager.getInstance().listWallpapers();
            asyncManager.executeOnMainThread(() -> {
                wallpaperList = list;
                wallpapersLoaded = true;
                String cur = FilesManager.getInstance().getCurrentWallpaperName();
                if (cur != null && wallpaperList.contains(cur)) selectedWallpaper = cur;
                prefetchPreviewsInBatches();
            });
        });
    }

    private void prefetchPreviewsInBatches() {
        final int batchSize = 4;
        asyncManager.submitCPUTask(() -> {
            for (int i = 0; i < wallpaperList.size(); i += batchSize) {
                final int start = i;
                asyncManager.executeOnMainThread(() -> {
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

        int sidebarW = 140;
        guiGraphics.fill(cx, cy, cx + sidebarW, cy + 220, DraggableWindow.darkTheme ? 0xFF1A1A1A : 0xFFCCCCCC);
        for (int i = 0; i < categories.length; i++) {
            int itemY = cy + 8 + i * 34;
            int textColor = (i == selectedCategoryIndex) ? DraggableWindow.accentColorARGB : DraggableWindow.textSecondaryColor();
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(categories[i]), cx + 12, itemY, textColor, false);
        }

        int mainX = cx + sidebarW + 12;
        int mainW = cw - sidebarW - 24;

        String cat = categories[selectedCategoryIndex];
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(cat), mainX + 8, cy + 6, DraggableWindow.textPrimaryColor(), false);

        if ("Accessibility".equals(cat)) {
            int by = cy + 28;
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Dark Mode"), mainX + 8, by + 6, DraggableWindow.textPrimaryColor(), false);
             int toggleX = mainX + 150, toggleY = by + 2, toggleW = 40, toggleH = 16;
             int toggleBg = DraggableWindow.darkTheme ? DraggableWindow.accentColorARGB : 0xFF777777;
             guiGraphics.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, toggleBg);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(DraggableWindow.darkTheme ? "On" : "Off"), toggleX + 6, toggleY + 2, DraggableWindow.contrastingColorFor(toggleBg), false);

            int accentY = by + 34;
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Accent Color"), mainX + 8, accentY, DraggableWindow.textPrimaryColor(), false);
             accentColorInput.setX(mainX + 8); accentColorInput.setY(accentY + 14); accentColorInput.render(guiGraphics, mouseRelX, mouseRelY, partialTick);

            int swX = mainX + 120, swY = accentY + 14; int swSize = 18; for (int i=0;i<accentPalette.length;i++){
                int sx = swX + i*(swSize+6);
                guiGraphics.fill(sx, swY, sx+swSize, swY+swSize, accentPalette[i]);
                if ((DraggableWindow.accentColorARGB & 0x00FFFFFF) == (accentPalette[i] & 0x00FFFFFF)) {
                    guiGraphics.fill(sx-2, swY-2, sx+swSize+2, swY+swSize+2, DraggableWindow.selectionOverlayColor());
                }
                if (mouseRelX >= sx && mouseRelX <= sx+swSize && mouseRelY >= swY && mouseRelY <= swY+swSize) {
                    guiGraphics.fill(sx, swY, sx+swSize, swY+swSize, DraggableWindow.selectionOverlayColor());
                }
            }

            int otherY = accentY + 48;
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("High Contrast Text"), mainX + 8, otherY, DraggableWindow.textPrimaryColor(), false);
            boolean highContrast = SETTINGS.containsKey("highContrast") && (Boolean)SETTINGS.get("highContrast") == true;
            int hcX = mainX + 220, hcY = otherY - 2; guiGraphics.fill(hcX, hcY, hcX+40, hcY+16, highContrast ? DraggableWindow.accentColorARGB : 0xFF777777);

            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Large Text"), mainX + 8, otherY + 22, DraggableWindow.textPrimaryColor(), false);
            boolean largeText = SETTINGS.containsKey("largeText") && (Boolean)SETTINGS.get("largeText") == true;
            int ltX = mainX + 220, ltY = otherY + 20; guiGraphics.fill(ltX, ltY, ltX+40, ltY+16, largeText ? DraggableWindow.accentColorARGB : 0xFF777777);
            if (ConfigHandler.debugButtonsEnabled()) {
                DebugOverlay.drawHitbox(guiGraphics, toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, "toggle:darkMode");
                DebugOverlay.drawHitbox(guiGraphics, accentColorInput.getX(), accentColorInput.getY(), accentColorInput.getX() + accentColorInput.getWidth(), accentColorInput.getY() + 16, "input:accentColor");
                for (int i=0;i<accentPalette.length;i++){
                    int sx = swX + i*(swSize+6);
                    DebugOverlay.drawHitbox(guiGraphics, sx, swY, sx+swSize, swY+swSize, "swatch:"+i);
                }
                DebugOverlay.drawHitbox(guiGraphics, hcX, hcY, hcX+40, hcY+16, "toggle:highContrast");
                DebugOverlay.drawHitbox(guiGraphics, ltX, ltY, ltX+40, ltY+16, "toggle:largeText");
            }

            return;
        }

        if ("Wallpapers".equals(cat)) {
            int by = cy + 28;
            if (!wallpapersLoaded) {
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Loading wallpapers..."), mainX + 8, by + 10, 0xFF999999, false);
                return;
            }

            int previewX = mainX + mainW / 6;
            int previewY = by + 8;
            int previewW = mainW - (mainW / 3);
            int previewH = 180;
            guiGraphics.fill(previewX - 4, previewY - 4, previewX + previewW + 4, previewY + previewH + 4, DraggableWindow.darkTheme ? 0xFF222222 : 0xFFE0E0E0);
            guiGraphics.fill(previewX, previewY, previewX + previewW, previewY + previewH, DraggableWindow.darkTheme ? 0xFF0F0F0F : 0xFFFFFFFF);

            String previewName = selectedWallpaper != null ? selectedWallpaper : FilesManager.getInstance().getCurrentWallpaperName();
            boolean curIsColor = FilesManager.getInstance().isCurrentWallpaperColor();
            if (curIsColor) {
                int col = FilesManager.getInstance().getCurrentWallpaperColor();
                guiGraphics.fill(previewX + 8, previewY + 8, previewX + previewW - 8, previewY + previewH - 8, col);
            } else {
                ResourceLocation previewRes = previewName == null ? null : FilesManager.getInstance().getWallpaperPreviewResource(previewName);
                if (previewRes != null) {
                    try { guiGraphics.blit(previewRes, previewX + 8, previewY + 8, 0, 0, previewW - 16, previewH - 16, previewW - 16, previewH - 16); } catch (Exception ignored) {}
                } else {
                    guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("No preview"), previewX + 8, previewY + 8, DraggableWindow.textSecondaryColor(), false);
                }
            }

            int thumbY = previewY + previewH + 14;
            int thumbSize = 64;
            int thumbPad = 10;
            int thumbsVisibleW = previewW - 8;

            int totalThumbsW = wallpaperList.size() * (thumbSize + thumbPad);
            float maxOffset = Math.max(0, totalThumbsW - thumbsVisibleW + 4);
            clampThumbOffset(maxOffset);

            int newHoverIndex = -1;
            for (int i = 0; i < wallpaperList.size(); i++) {
                int baseX = previewX + 4 + i * (thumbSize + thumbPad) - Math.round(thumbOffset);
                if (baseX + thumbSize < previewX || baseX > previewX + thumbsVisibleW) continue;

                if (mouseRelX >= baseX && mouseRelX <= baseX + thumbSize && mouseRelY >= thumbY && mouseRelY <= thumbY + thumbSize) {
                    newHoverIndex = i;
                }
            }

            if (newHoverIndex != hoverIndex) {
                hoverIndex = newHoverIndex;
                hoverAlpha = 0f;
            }
            if (hoverIndex != -1) hoverAlpha = Math.min(1f, hoverAlpha + 0.14f); else hoverAlpha = Math.max(0f, hoverAlpha - 0.14f);

            for (int i = 0; i < wallpaperList.size(); i++) {
                int baseX = previewX + 4 + i * (thumbSize + thumbPad) - Math.round(thumbOffset);
                if (baseX + thumbSize < previewX || baseX > previewX + thumbsVisibleW) continue;
                String name = wallpaperList.get(i);

                float hoverProgress = (i == hoverIndex) ? hoverAlpha : 0f;
                float scale = 1.0f + 0.12f * hoverProgress;
                int scaledSize = Math.round(thumbSize * scale);
                int centerX = baseX + thumbSize / 2;
                int drawX = centerX - scaledSize / 2;
                int drawY = thumbY + (thumbSize - scaledSize) / 2;

                guiGraphics.fill(drawX - 2, drawY - 2, drawX + scaledSize + 2, drawY + scaledSize + 2, DraggableWindow.darkTheme ? 0xFF111111 : 0xFFDDDDDD);

                ResourceLocation thumb = FilesManager.getInstance().getWallpaperPreviewResource(name);
                if (thumb != null) {
                    try {
                        guiGraphics.blit(thumb, drawX, drawY, 0, 0, scaledSize, scaledSize, scaledSize, scaledSize);
                    } catch (Exception ignored) {
                        guiGraphics.fill(drawX, drawY, drawX + scaledSize, drawY + scaledSize, DraggableWindow.darkTheme ? 0xFF333333 : 0xFFAAAAAA);
                    }
                } else {
                    guiGraphics.fill(drawX, drawY, drawX + scaledSize, drawY + scaledSize, DraggableWindow.darkTheme ? 0xFF333333 : 0xFFAAAAAA);
                }

                if (hoverProgress > 0f) {
                    int alpha = Math.round(hoverProgress * 0x66);
                    int overlay = (alpha << 24) | (DraggableWindow.darkTheme ? 0xFFFFFF : 0x000000);
                    guiGraphics.fill(drawX, drawY, drawX + scaledSize, drawY + scaledSize, overlay);
                }

                boolean isSel = name.equals(selectedWallpaper) || (selectedWallpaper == null && name.equals(FilesManager.getInstance().getCurrentWallpaperName()));
                if (isSel) guiGraphics.fill(drawX - 2, drawY - 2, drawX + scaledSize + 2, drawY + scaledSize + 2, DraggableWindow.selectionOverlayColor());
                if (ConfigHandler.debugButtonsEnabled()) {
                    DebugOverlay.drawHitbox(guiGraphics, drawX, drawY, drawX + scaledSize, drawY + scaledSize, "thumb:" + i);
                }
            }

            int scrollbarX = previewX + 4;
            int scrollbarY = thumbY + thumbSize + 10;
            int scrollbarH = 6;
            guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + thumbsVisibleW, scrollbarY + scrollbarH, 0xFF222222);
            if (!wallpaperList.isEmpty()) {
                float viewRatio = Math.min(1f, (float) thumbsVisibleW / (float) totalThumbsW);
                int handleW = Math.max(16, Math.round(thumbsVisibleW * viewRatio));
                int handleX = scrollbarX + Math.round((thumbsVisibleW - handleW) * (thumbOffset / Math.max(1, maxOffset)));
                guiGraphics.fill(handleX, scrollbarY, handleX + handleW, scrollbarY + scrollbarH, 0xFF555555);
                if (ConfigHandler.debugButtonsEnabled()) DebugOverlay.drawHitbox(guiGraphics, handleX, scrollbarY, handleX + handleW, scrollbarY + scrollbarH, "scrollbar:handle");
            }

            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Put PNG/JPG in chaoscrafts_device_mod/wallpapers"), mainX, scrollbarY + scrollbarH + 8, DraggableWindow.textSecondaryColor(), false);
            int presetsX = mainX + 8;
            int presetsY = scrollbarY + scrollbarH + 28;
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Solid color presets:"), presetsX, presetsY, DraggableWindow.textSecondaryColor(), false);
            int[] presets = new int[]{0xFF000000, 0xFFFFFFFF, 0xFF2B2B2B, 0xFF1E90FF, 0xFF57C07D, 0xFFF0A84B};
            int sx = presetsX + 140, sy = presetsY;
            for (int i = 0; i < presets.length; i++) {
                int px = sx + i * 26;
                guiGraphics.fill(px, sy, px + 20, sy + 20, presets[i]);
                if (FilesManager.getInstance().isCurrentWallpaperColor() && (FilesManager.getInstance().getCurrentWallpaperColor() & 0x00FFFFFF) == (presets[i] & 0x00FFFFFF)) {
                    guiGraphics.fill(px - 2, sy - 2, px + 22, sy + 22, 0x66FFFFFF);
                }
                if (ConfigHandler.debugButtonsEnabled()) DebugOverlay.drawHitbox(guiGraphics, px, sy, px + 20, sy + 20, "preset:" + i);
            }
            return;
        }

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Accent Color:"), mainX + 8, cy + 36, DraggableWindow.textPrimaryColor(), false);
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

        for (int i = 0; i < categories.length; i++) {
            int itemY = r[1] + 32 + 8 + i * 34;
            if (mouseRelX >= cx && mouseRelX <= cx + sidebarW && mouseRelY >= itemY && mouseRelY <= itemY + 20) {
                selectedCategoryIndex = i;
                return true;
            }
        }

        String cat = categories[selectedCategoryIndex];

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
            int otherY = accentY + 48;
            int hcX = mainX + 220, hcY = otherY - 2; if (mouseRelX >= hcX && mouseRelX <= hcX+40 && mouseRelY >= hcY && mouseRelY <= hcY+16) {
                boolean highContrast = SETTINGS.containsKey("highContrast") && (Boolean)SETTINGS.get("highContrast") == true;
                SETTINGS.put("highContrast", !highContrast);
                saveSettingsAsync();
                return true;
            }
            int ltX = mainX + 220, ltY = otherY + 20; if (mouseRelX >= ltX && mouseRelX <= ltX+40 && mouseRelY >= ltY && mouseRelY <= ltY+16) {
                boolean largeText = SETTINGS.containsKey("largeText") && (Boolean)SETTINGS.get("largeText") == true;
                SETTINGS.put("largeText", !largeText);
                saveSettingsAsync();
                return true;
            }

            return false;
        }

        if ("Wallpapers".equals(cat)) {
            int by = r[1] + 32 + 28;
            int previewX = mainX + mainW / 6;
            int previewY = by + 8;
            int previewW = mainW - (mainW / 3);
            int previewH = 180;
            int thumbY = previewY + previewH + 14; int thumbSize = 64; int thumbPad = 10;

            for (int i = 0; i < wallpaperList.size(); i++) {
                int baseX = previewX + 4 + i * (thumbSize + thumbPad) - Math.round(thumbOffset);
                if (mouseRelX >= baseX && mouseRelX <= baseX + thumbSize && mouseRelY >= thumbY && mouseRelY <= thumbY + thumbSize) {
                    String name = wallpaperList.get(i);
                    selectedWallpaper = name;
                    FilesManager.getInstance().setCurrentWallpaperName(name);
                    SETTINGS.put("wallpaper", name);
                    SETTINGS.put("wallpaperIsColor", false);
                    SETTINGS.remove("wallpaperColor");
                    saveSettingsAsync();
                    return true;
                }
            }

            if (mouseRelX >= previewX + 4 && mouseRelX <= previewX + previewW - 4 && mouseRelY >= thumbY && mouseRelY <= thumbY + thumbSize) {
                draggingThumb = true; thumbDragStartX = (int) mouseRelX; thumbDragStartOffset = thumbOffset; return true;
            }

            int thumbsVisibleW = previewW - 8;
            int scrollbarX = previewX + 4;
            int scrollbarY = thumbY + thumbSize + 10;
            int scrollbarH = 6;

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

        int[] rr = window.getRenderRect(26); int mainCX = rr[0]+8 + 140 + 12; int aiX = mainCX + 8, aiY = rr[1]+32 + 36, aiW = 80, aiH = 16;
        if (mouseRelX >= aiX && mouseRelX <= aiX+aiW && mouseRelY >= aiY && mouseRelY <= aiY+aiH) { accentInputFocused = true; return true; }
        accentInputFocused = false;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!wallpapersLoaded || wallpaperList.isEmpty()) return false;
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8; int cw = r[2] - 16; int sidebarW = 140; int mainX = cx + sidebarW + 12; int mainW = cw - sidebarW - 24;
        int wallpaperY = (r[1] + 32) + 28;
        int previewX = mainX + mainW / 6; int previewY = wallpaperY + 8; int previewW = mainW - (mainW / 3); int previewH = 180; int thumbY = previewY + previewH + 14; int thumbSize = 64;

        int thumbsVisibleW = previewW - 8;
        if (mouseX >= previewX + 4 && mouseX <= previewX + thumbsVisibleW && mouseY >= thumbY && mouseY <= thumbY + thumbSize) {
            thumbScrollVelocity += (float)(-delta * 18.0);
            return true;
        }
        return false;
    }

    @Override public boolean charTyped(DraggableWindow window, char codePoint, int modifiers) { if (accentInputFocused) { boolean r = accentColorInput.charTyped(codePoint, modifiers); if (r) { try{ String hex = accentColorInput.getValue().replace("#",""); if (hex.length()==6) { DraggableWindow.accentColorARGB = 0xFF000000 | Integer.parseInt(hex,16); SETTINGS.put("accentColor", String.format("#%06X", DraggableWindow.accentColorARGB & 0xFFFFFF)); saveSettingsAsync(); } } catch (NumberFormatException ignored) {} } return r; } return false; }
    @Override public boolean keyPressed(DraggableWindow window, int keyCode, int scanCode, int modifiers) { if (accentInputFocused) return accentColorInput.keyPressed(keyCode, scanCode, modifiers); return false; }
    @Override public void mouseReleased(DraggableWindow window, double mouseRelX, double mouseRelY, int button) { draggingThumb = false; }
    @Override public boolean mouseDragged(DraggableWindow window, double mouseRelX, double mouseRelY, double dx, double dy) {
        if (draggingThumb) {
            float delta = (float) (mouseRelX - thumbDragStartX);
            thumbOffset = thumbDragStartOffset - delta;
            int[] r = window.getRenderRect(26);
            int cx = r[0] + 8; int cw = r[2] - 16; int sidebarW = 140; int mainX = cx + sidebarW + 12; int mainW = cw - sidebarW - 24;
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
        if (Math.abs(thumbScrollVelocity) > 0.01f) {
            thumbOffset += thumbScrollVelocity;
            thumbScrollVelocity *= 0.86f;
            int[] r = window.getRenderRect(26);
            int cx = r[0] + 8; int cw = r[2] - 16;
            int sidebarW = 140; int mainX = cx + sidebarW + 12; int mainW = cw - sidebarW - 24;
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
