package net.chaoscraft.chaoscrafts_device_mod.client.screen;

import net.chaoscraft.chaoscrafts_device_mod.ConfigHandler;
import net.chaoscraft.chaoscrafts_device_mod.client.app.AppFactory;
import net.chaoscraft.chaoscrafts_device_mod.client.app.AppRegistry;
import net.chaoscraft.chaoscrafts_device_mod.client.app.IconManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.client.app.IApp;
import net.chaoscraft.chaoscrafts_device_mod.client.fs.FilesManager;
import net.minecraft.resources.ResourceLocation;

import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;
import net.chaoscraft.chaoscrafts_device_mod.sound.ModSounds;
import net.chaoscraft.chaoscrafts_device_mod.sound.LaptopFanLoopSound;
import net.chaoscraft.chaoscrafts_device_mod.sound.LaptopKeySoundManager;
import net.chaoscraft.chaoscrafts_device_mod.network.NetworkHandler;
import net.chaoscraft.chaoscrafts_device_mod.network.packet.LaptopTypingPacket;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DesktopScreen extends Screen {
    private EditBox searchBox;
    private final List<DesktopIcon> desktopIcons = new ArrayList<>();
    private final List<DraggableWindow> openApps = new ArrayList<>();
    private final int taskbarHeight = 28;

    // icon drag / selection state
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

    // search results
    private final List<SearchResult> searchResults = new ArrayList<>();
    private static final int RESULT_HEIGHT = 28;

    // Context menu
    private DesktopContextMenu contextMenu = null;
    private int iconSize = 32;

    private long lastTimeUpdate = 0;
    private String currentTime = "";

    // Loading overlay (RiftOS)
    private boolean showLoadingOverlay = true;
    private final long loadingStartMillis = System.currentTimeMillis();
    private final java.util.List<LoadingParticle> loadingParticles = new java.util.ArrayList<>();
    // animated progress & time state
    private float currentLoadingProgress = 0f; // smoothed progress shown in UI
    private long lastRenderMillis = System.currentTimeMillis();
    private static final long MIN_LOADING_MS = 5000;
    private static class LoadingParticle { float x,y,vx,vy,life; LoadingParticle(float x,float y,float vx,float vy,float life){this.x=x;this.y=y;this.vx=vx;this.vy=vy;this.life=life;} }

    private boolean showDebugInfo = false;
    private final AsyncTaskManager asyncManager = AsyncTaskManager.getInstance();
    private final java.util.Random rng = new java.util.Random();

    // Key binding state
    private boolean ctrlPressed = false;
    private boolean shiftPressed = false;

    // Inline renaming state
    private DesktopIcon renamingIcon = null;
    private EditBox renameBox = null;

    // File system integration
    private final File desktopDir = new File(FilesManager.getPlayerDataDir(), "Desktop");

    // --- Added sound & typing packet state ---
    private LaptopFanLoopSound fanLoopSound;
    private final java.util.Random soundRand = new java.util.Random();
    private long lastKeyTypeMillis = 0;
    private static final long KEY_PACKET_COOLDOWN_MS = 55; // ms throttle for global typing packet

    // new: block position of opened device
    private final BlockPos devicePos;
    private boolean fanStarted = false;
    private static final int FAN_PREDELAY_TICKS = 30 * 20; // 30s silent pre-delay inside sound instance

    public DesktopScreen() { this(null); }
    public DesktopScreen(BlockPos devicePos) {
        super(Component.literal("Desktop"));
        this.devicePos = devicePos;
        File base = Minecraft.getInstance().gameDirectory;
        File saveDir = new File(base, "chaoscrafts_device_mod");
        FilesManager.init(saveDir);
        if (devicePos != null) LaptopKeySoundManager.setDevicePos(devicePos);

        asyncManager.executeOnMainThread(() -> {
            try { AppRegistry.getInstance(); } catch (Exception ignored) {}
            try { net.chaoscraft.chaoscrafts_device_mod.client.app.SettingsApp.loadSettings(); } catch (Exception ignored) {}
        });
        asyncManager.scheduleTask(() -> asyncManager.executeOnMainThread(() -> showLoadingOverlay = false), MIN_LOADING_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        try { if (!desktopDir.exists() && !desktopDir.mkdirs()) System.err.println("Warning: failed to create desktop dir: " + desktopDir.getAbsolutePath()); } catch (Exception ignored) {}
    }

    @Override
    protected void init() {
        super.init();
        // Ensure fan sound gets created only if experimental settings enabled
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

        searchBox = new EditBox(this.font, 6, height - taskbarHeight + 4, 140, 20, Component.literal("Search"));
        searchBox.setMaxLength(100);
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

        // remove immediate fan start; will start after delay
    }

    @Override
    public void onClose() {
        if (fanLoopSound != null && fanStarted && !fanLoopSound.isFinished()) fanLoopSound.requestFadeOut();
        LaptopKeySoundManager.clearDevicePos();
        super.onClose();
    }

    @Override
    public void removed() {
        // Extra safety: ensure fade-out also occurs if the screen is removed via setScreen or world change.
        if (fanLoopSound != null && fanStarted && !fanLoopSound.isFinished() && !fanLoopSound.isFadingOut()) {
            fanLoopSound.requestFadeOut();
        }
        super.removed();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // wallpaper
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
                    // fallback to solid fill if texture fails
                    int bg = DraggableWindow.darkTheme ? 0xFF0F0F12 : 0xFF9AA6B2;
                    guiGraphics.fill(0, 0, width, height, bg);
                }
            } else {
                int bg = DraggableWindow.darkTheme ? 0xFF0F0F12 : 0xFF9AA6B2;
                guiGraphics.fill(0, 0, width, height, bg);
            }
        }

        if (showDebugInfo) renderDebugInfo(guiGraphics);

        // desktop icons
        for (DesktopIcon icon : desktopIcons) {
            icon.displayX = lerp(icon.displayX, icon.targetX, ICON_LERP);
            icon.displayY = lerp(icon.displayY, icon.targetY, ICON_LERP);
            icon.render(guiGraphics, mouseX, mouseY, selectedIcons.contains(icon), iconSize);
        }

        // Update search box position every frame so its hitbox/focus are correct
        searchBox.setWidth(180);
        searchBox.setX(6); searchBox.setY(height - taskbarHeight + 4);

        // windows rendering (below taskbar)
        for (DraggableWindow w : openApps) {
            if (!w.minimized || w.preview || w.closing) {
                boolean focused = (w == (openApps.isEmpty() ? null : openApps.get(openApps.size() - 1)));
                w.render(guiGraphics, guiGraphics.pose(), mouseX, mouseY, focused, taskbarHeight, partialTick);
                w.finalizeAnimationState();
            }
        }

        // loading overlay on top
        if (showLoadingOverlay) {
            long now = System.currentTimeMillis();
            long elapsed = now - loadingStartMillis;
            int w = width; int h = height;

            // update animation timing
            long nowMs = System.currentTimeMillis();
            float deltaSec = (nowMs - lastRenderMillis) / 1000f;
            lastRenderMillis = nowMs;

            // subtle vignetting gradient (top -> bottom)
            int topColor = 0xFF071018;
            int bottomColor = 0xFF0D1624;
            for (int i = 0; i < h; i++) {
                int mix = i * 255 / Math.max(1, h - 1);
                int rcol = ((topColor >> 16) & 0xFF) * (255 - mix) / 255 + ((bottomColor >> 16) & 0xFF) * mix / 255;
                int gcol = ((topColor >> 8) & 0xFF) * (255 - mix) / 255 + ((bottomColor >> 8) & 0xFF) * mix / 255;
                int bcol = (topColor & 0xFF) * (255 - mix) / 255 + (bottomColor & 0xFF) * mix / 255;
                guiGraphics.fill(0, i, w, i + 1, (0xFF << 24) | (rcol << 16) | (gcol << 8) | bcol);
            }

            // slight dark overlay for contrast
            guiGraphics.fill(0, 0, w, h, 0x22000000);

            // Center the loading group vertically to feel more balanced
            String title = "RiftOS";
            float titleScale = 2.0f;
            int centerY = h / 2; // base center for the loading group

            // draw scaled title centered at centerY - 60
            int tw = font.width(title);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(titleScale, titleScale, 1f);
            int scaledX = (int)((w / titleScale - tw) / 2f);
            int scaledY = (int)((centerY - 60) / titleScale);
            guiGraphics.drawString(font, Component.literal(title), scaledX + 1, scaledY + 1, DraggableWindow.darkTheme ? 0x66000000 : 0x66FFFFFF, false);
            guiGraphics.drawString(font, Component.literal(title), scaledX, scaledY, DraggableWindow.textPrimaryColor(), false);
            guiGraphics.pose().popPose();

            // subtitle under title
            String baseSub = "Loading apps & settings";
            int dots = (int) ((elapsed / 450) % 4);
            String sub = baseSub + ".".repeat(Math.max(0, dots));
            guiGraphics.drawString(font, Component.literal(sub), (w - font.width(sub)) / 2, centerY - 24, DraggableWindow.textSecondaryColor(), false);

            // Progress bar (centered around centerY)
            float targetProg = Math.min(1f, (float) elapsed / (float) MIN_LOADING_MS);
            currentLoadingProgress = lerp(currentLoadingProgress, targetProg, 0.06f);
            int barW = Math.min(600, w - 160);
            int bx = (w - barW) / 2; int by = centerY + 4;
            int barH = 12;
            guiGraphics.fill(bx - 3, by - 3, bx + barW + 3, by + barH + 3, DraggableWindow.darkTheme ? 0x44707070 : 0x44BBBBBB);
            guiGraphics.fill(bx, by, bx + barW, by + barH, DraggableWindow.darkTheme ? 0xFF1F2428 : 0xFFF0F0F0);
            int filled = bx + Math.round(barW * currentLoadingProgress);
            guiGraphics.fill(bx, by, filled, by + barH, DraggableWindow.accentColorARGB);
            guiGraphics.fill(Math.max(bx, filled - 6), by, Math.min(bx + barW, filled + 6), by + barH, (DraggableWindow.accentColorARGB & 0x00FFFFFF) | 0x33FFFFFF);

            // percentage: draw centered inside the progress bar
            String pct = String.format("%d%%", Math.round(currentLoadingProgress * 100f));
            int pctX = bx + (barW - font.width(pct)) / 2;
            int pctY = by + Math.max(0, (barH - 8) / 2);
            guiGraphics.drawString(font, Component.literal(pct), pctX, pctY, DraggableWindow.textPrimaryColor(), false);

            // Particles (more varied and softer), spawn around the centered group
            synchronized (loadingParticles) {
                if (loadingParticles.size() < 60 && rng.nextInt(4) == 0) {
                    float px = w / 2f + rng.nextInt(360) - 180;
                    float py = centerY + rng.nextInt(140) - 70;
                    float vx = (rng.nextFloat() - 0.5f) * 1.8f;
                    float vy = -0.6f - rng.nextFloat() * 0.8f;
                    float life = 50 + rng.nextInt(140);
                    loadingParticles.add(new LoadingParticle(px, py, vx, vy, life));
                    if (rng.nextInt(12) == 0) loadingParticles.add(new LoadingParticle(px + rng.nextInt(20)-10, py + rng.nextInt(10)-5, vx*0.4f, vy*0.2f, 30 + rng.nextInt(40)));
                }
                Iterator<LoadingParticle> it = loadingParticles.iterator();
                while (it.hasNext()) {
                    LoadingParticle p = it.next();
                    p.x += p.vx; p.y += p.vy; p.vy += 0.01f; p.vx *= 0.995f; p.vy *= 0.998f;
                    p.life -= 1;
                    float lifeFrac = Math.max(0f, (p.life) / 160f);
                    int alpha = Math.max(0, Math.min(255, Math.round(200 * lifeFrac)));
                    int r = 0xCC; int g = 0xEE; int b = 0xFF;
                    if (rng.nextInt(6) == 0) { r = 0xFF; g = 0xD8; b = 0xA8; }
                    int col = (alpha << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
                    int size = (int) Math.max(1, Math.round(1 + 2 * lifeFrac));
                    guiGraphics.fill(Math.round(p.x), Math.round(p.y), Math.round(p.x) + size, Math.round(p.y) + size, col);
                    int trailAlpha = Math.max(0, alpha - 100);
                    if (trailAlpha > 0) guiGraphics.fill(Math.round(p.x - p.vx*2), Math.round(p.y - p.vy*2), Math.round(p.x), Math.round(p.y), (trailAlpha << 24) | 0x66CCCCFF);
                    if (p.life <= 0) it.remove();
                }
            }

            // Small footer hint
            String hint = "Press ESC to close";
            guiGraphics.drawString(font, Component.literal(hint), w - font.width(hint) - 10, h - 24, DraggableWindow.darkTheme ? 0x66FFFFFF : 0x66000000, false);

            // Keep overlay for minimum time
            return;
        }

        // selection rect (soft fill + 1px lighter outline)
        if (selecting && !iconDragging) {
            int x0 = Math.min(selectStartX, selectEndX), y0 = Math.min(selectStartY, selectEndY);
            int x1 = Math.max(selectStartX, selectEndX), y1 = Math.max(selectStartY, selectEndY);
            int fillCol = ((DraggableWindow.accentColorARGB & 0x00FFFFFF) | (DraggableWindow.darkTheme ? 0x220000FF : 0x22FFFFFF));
            guiGraphics.fill(x0, y0, x1, y1, fillCol);
            int outline = DraggableWindow.darkTheme ? 0x88AAD8FF : 0x88000000;
            guiGraphics.fill(x0, y0, x1, y0 + 1, outline);
            guiGraphics.fill(x0, y1 - 1, x1, y1, outline);
            guiGraphics.fill(x0, y0, x0 + 1, y1, outline);
            guiGraphics.fill(x1 - 1, y0, x1, y1, outline);
        }

        // update search results
        updateSearchResults();

        // taskbar
        boolean hideTaskbar = false;
        if (!openApps.isEmpty()) hideTaskbar = openApps.get(openApps.size() - 1).exclusiveFullscreen;
        if (!hideTaskbar) {
            int tbY = height - taskbarHeight;
            // position the search box but render it after drawing the taskbar background
            searchBox.setX(6); searchBox.setY(tbY + 4);

            // taskbar base - semi-transparent greyish with subtle green undertone
            int taskbarBase = 0xCC2B2F33; // more greyish, slightly transparent
            int topSheen = 0x22FFFFFF;
            guiGraphics.fill(0, tbY, width, height, taskbarBase);
            // subtle top sheen strip
            guiGraphics.fill(0, tbY, width, tbY + 3, DraggableWindow.darkTheme ? topSheen : 0x22FFFFFF);
            // soft shadow above the bar
            guiGraphics.fill(0, tbY - 2, width, tbY, 0x22000000);

            // area reserved on right for system tray and stacked clock
            final int trayIconW = 28;
            final int trayIconH = 18;
            final int trayIconPad = 6;

            List<String> trayLabels = Arrays.asList("LAN", "🔊", "Upd", "✉", "12°C ☁", "📷", "/\\");

            int iconsTotalW = trayLabels.size() * trayIconW + (trayLabels.size() - 1) * trayIconPad + 8;
            String timeStr = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"));
            String dateStr = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy"));
            int timeW = font.width(timeStr);
            int dateW = font.width(dateStr);
            int clockW = Math.max(timeW, dateW) + 12; // padding
            int trayTotalWidth = iconsTotalW + clockW + 12; // extra spacing to right edge

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
                guiGraphics.drawString(font, Component.literal(lbl), lx, ly, DraggableWindow.textPrimaryColor(), false);
                x += trayIconW + trayIconPad;
            }

            int clockRightEdge = width - trayRightPadding;
            int clockLeft = clockRightEdge - Math.max(timeW, dateW);
            int minClockLeft = trayLeft + iconsTotalW + 6;
            if (clockLeft < minClockLeft) clockLeft = minClockLeft;
            int timeY = tbY + Math.max(2, (taskbarHeight - (font.lineHeight * 2)) / 2);
            int dateY = timeY + Math.max(6, font.lineHeight - 2);
            guiGraphics.drawString(font, Component.literal(timeStr), clockLeft, timeY, DraggableWindow.textPrimaryColor(), false);
            guiGraphics.drawString(font, Component.literal(dateStr), clockLeft, dateY, DraggableWindow.textSecondaryColor(), false);

            // render the search box on top so it is visible and receives input reliably
            try { searchBox.render(guiGraphics, mouseX, mouseY, partialTick); } catch (Exception ignored) {}

            // Draw search results popup above the taskbar if there are results
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
                    boolean hovered = mouseX >= popupX + 6 && mouseX <= popupX + 6 + rw && mouseY >= ry && mouseY <= ry + RESULT_HEIGHT;
                    if (hovered) guiGraphics.fill(popupX + 6, ry, popupX + 6 + rw, ry + RESULT_HEIGHT, DraggableWindow.selectionOverlayColor());
                    int iconSizePx = 20;
                    int iconX = popupX + 10;
                    if (r.iconRes != null) {
                        try { guiGraphics.blit(r.iconRes, iconX, ry + (RESULT_HEIGHT - iconSizePx) / 2, 0, 0, iconSizePx, iconSizePx, iconSizePx, iconSizePx); } catch (Exception ignored) {}
                    }
                    String label = r.displayName;
                    int textX = popupX + 12 + iconSizePx + 6;
                    int availTextW = popupW - (textX - popupX) - 12;
                    if (font.width(label) > availTextW) label = font.plainSubstrByWidth(label, availTextW - 8) + "...";
                    guiGraphics.drawString(font, Component.literal(label), textX, ry + 6, DraggableWindow.textPrimaryColor(), false);
                }
            }

            // draw open-window taskbar icons starting after the search box and before trayLeft
            int perEntry = 52;
            int startX = 6 + searchBox.getWidth() + 8;
            int available = Math.max(0, trayLeft - startX - 8);
            int maxEntries = Math.max(0, available / perEntry);
            List<DraggableWindow> visibleWindows = new ArrayList<>();
            if (maxEntries > 0) {
                int start = Math.max(0, openApps.size() - maxEntries);
                for (int i = start; i < openApps.size(); i++) {
                    DraggableWindow w0 = openApps.get(i);
                    if (isTaskbarEligible(w0)) visibleWindows.add(w0);
                }
            }

            int tx = startX;
            for (DraggableWindow w : visibleWindows) {
                int x0 = tx, y0 = tbY + 2, x1 = tx + perEntry - 8, y1 = height - 4;
                boolean hovered = (mouseX >= x0 && mouseX <= x1 && mouseY >= y0 && mouseY <= y1);
                int cx = tx + perEntry / 2 - 6;
                int cy = tbY + taskbarHeight / 2 - 1;
                if (hovered) guiGraphics.fill(x0, y0, x1, y1, DraggableWindow.selectionOverlayColor());
                w.preview = hovered;
                int tbIconSize = Math.min(24, perEntry - 28);
                try {
                    if (w.appName != null) {
                        ResourceLocation appIconRes = IconManager.getIconResource(normalizeAppNameForIcon(w.appName));
                        guiGraphics.blit(appIconRes, cx - tbIconSize / 2, cy - tbIconSize / 2, 0, 0, tbIconSize, tbIconSize, tbIconSize, tbIconSize);
                    }
                } catch (Exception ignored) {}

                if (w.minimized) guiGraphics.fill(cx - 3, tbY + taskbarHeight - 8, cx + 3, tbY + taskbarHeight - 6, DraggableWindow.textSecondaryColor());

                // hover tooltip for app name
                if (hovered) {
                    String name = w.appName == null ? "<app>" : w.appName;
                    int nw = font.width(name) + 8;
                    int nx = Math.max(6, Math.min(width - nw - 6, cx - nw / 2));
                    int ny = tbY - 22;
                    guiGraphics.fill(nx, ny, nx + nw, ny + 16, DraggableWindow.darkTheme ? 0xEE222222 : 0xEEFFFFFF);
                    guiGraphics.drawString(font, Component.literal(name), nx + 4, ny + 3, DraggableWindow.textPrimaryColor(), false);
                }

                tx += perEntry;
            }
        }

        // context menu
        if (contextMenu != null) contextMenu.render(guiGraphics, mouseX, mouseY, partialTick);

        // cleanup closed windows
        List<DraggableWindow> rem = new ArrayList<>(); for (DraggableWindow w : openApps) if (w.removeRequested) rem.add(w); openApps.removeAll(rem);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    // helper: decide if a window should appear on the taskbar
    private boolean isTaskbarEligible(DraggableWindow w) {
        if (w == null) return false;
        if (w.removeRequested || w.closing) return false;
        if (w.appName == null) return false;
        // Normalize the display/title to the internal app id used by the registry/icon manager
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
        searchResults.clear(); String q = searchBox.getValue().trim().toLowerCase(); if (q.isEmpty()) return;
        // Desktop icons
        for (DesktopIcon di : desktopIcons) {
            if (di.name.toLowerCase().contains(q)) {
                String key = di.name.contains(".") ? null : normalizeAppNameForIcon(di.name);
                ResourceLocation icon = IconManager.getIconResource(key);
                searchResults.add(new SearchResult(toTitleCase(di.name), icon, di.onClick));
            }
        }
        // Open windows / apps
        for (DraggableWindow w : openApps) {
            String appNameForMatch = w.appName == null ? "" : w.appName;
            if (appNameForMatch.toLowerCase().contains(q)) {
                String normalized = normalizeAppNameForIcon(appNameForMatch);
                ResourceLocation icon = IconManager.getIconResource(normalized);
                searchResults.add(new SearchResult(appNameForMatch, icon, () -> bringToFront(w)));
            }
        }
    }

    private static float lerp(float a, float b, float f) { return a + (b - a) * f; }

    private static String normalizeAppNameForIcon(String displayName) {
        if (displayName == null) return null;
        String n = displayName;
        if (n.contains(" - ")) n = n.substring(n.lastIndexOf(" - ") + 3);
        return n.trim().toLowerCase();
    }

    private static String toTitleCase(String s) {
        if (s == null || s.isEmpty()) return s;
        String base = s; if (base.toLowerCase().endsWith(".txt")) base = base.substring(0, base.length() - 4);
        StringBuilder sb = new StringBuilder(); boolean capNext = true;
        for (char c : base.toCharArray()) {
            if (Character.isWhitespace(c) || c == '_' || c == '-') { sb.append(c); capNext = true; continue; }
            if (capNext) { sb.append(Character.toUpperCase(c)); capNext = false; } else sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private DraggableWindow findWindowAt(double mouseX, double mouseY) {
        ListIterator<DraggableWindow> it = openApps.listIterator(openApps.size());
        while (it.hasPrevious()) { DraggableWindow w = it.previous(); if (!w.minimized && w.isInside(mouseX, mouseY, taskbarHeight)) return w; }
        return null;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        super.tick();
        // If experimental setting was turned off while open, immediately stop fan
        if (fanStarted && fanLoopSound != null && !ConfigHandler.experimentalEnabled() && !fanLoopSound.isFinished()) {
            fanLoopSound.requestFadeOut();
            fanStarted = false; // prevent re-starting this session
        }
        // Removed delayed creation logic; fan handled entirely by pre-delay inside sound.
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        searchBox.setWidth(180); searchBox.setX(6); searchBox.setY(height - taskbarHeight + 4);
        super.mouseClicked(mouseX, mouseY, button);
        // Left click -> trackpad sound; Right click -> separate mouse click sound (context menu or other)
        if (button == 0) {
            try { LaptopKeySoundManager.playTrackpadClick(); } catch (Exception ignored) {}
        } else if (button == 1) {
            try { LaptopKeySoundManager.playMouseClick(); } catch (Exception ignored) {}
        }

        if (contextMenu != null) {
            if (contextMenu.mouseClicked(mouseX, mouseY, button)) return true;
            contextMenu = null; return true;
        }

        if (button == 1) {
            boolean clickedOnIcon = false;
            if (renamingIcon != null && renameBox != null) {
                int rx = Math.round(renamingIcon.displayX); int ry = Math.round(renamingIcon.displayY + renamingIcon.iconSize + 4);
                int rw = Math.max(80, renamingIcon.iconSize); int rh = 16;
                if (mouseX >= rx && mouseX <= rx + rw && mouseY >= ry && mouseY <= ry + rh) { renameBox.setFocused(true); return true; }
                else {
                    String newName = renameBox.getValue().trim();
                    if (!newName.isEmpty() && !newName.equals(renamingIcon.name)) {
                        File oldFile = new File(desktopDir, renamingIcon.name); File newFile = new File(desktopDir, newName);
                        if (!newFile.exists()) { if (oldFile.exists()) oldFile.renameTo(newFile); FilesManager.getInstance().removeDesktopIcon(renamingIcon.name); FilesManager.getInstance().addDesktopIcon(newName, renamingIcon.targetX, renamingIcon.targetY); refreshDesktopIcons(); }
                    }
                    renamingIcon = null; renameBox = null; return false;
                }
            }
            for (DesktopIcon icon : desktopIcons) if (icon.isInside(mouseX, mouseY, iconSize)) { clickedOnIcon = true; break; }
            if (!clickedOnIcon) { showContextMenu((int)mouseX, (int)mouseY); return true; }
        }

        int tbY = height - taskbarHeight;
        if (!searchBox.getValue().isEmpty() && !searchResults.isEmpty()) {
            int popupW = Math.max(searchBox.getWidth(), 260); int entries = Math.min(searchResults.size(), 6); int popupH = entries * RESULT_HEIGHT + 6; int popupX = searchBox.getX(); int popupY = tbY - popupH - 6; if (popupY < 6) popupY = 6;
            if (mouseX >= popupX && mouseX <= popupX + popupW && mouseY >= popupY && mouseY <= popupY + popupH) {
                int idx = (int)((mouseY - (popupY + 6)) / RESULT_HEIGHT);
                if (idx >= 0 && idx < Math.min(searchResults.size(), 6)) { searchResults.get(idx).action.run(); searchBox.setValue(""); searchResults.clear(); playClick(); }
                return super.mouseClicked(mouseX, mouseY, button);
            }
        }

        DraggableWindow top = findWindowAt(mouseX, mouseY);
        if (top != null) {
            bringToFront(top);
            int[] rr = top.getRenderRect(taskbarHeight);
            if (mouseY >= rr[1] && mouseY <= rr[1] + 26) { top.handleTitlebarClick(mouseX, mouseY, button, taskbarHeight); playClick(); return super.mouseClicked(mouseX, mouseY, button); }
            boolean consumed = top.app.mouseClicked(top, mouseX, mouseY, button);
            if (consumed) { playClick(); return super.mouseClicked(mouseX, mouseY, button); }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (mouseY >= tbY && mouseY <= height) {
            int tx = 6 + searchBox.getWidth() + 8; int perEntry = 44; int rightReserved = 120; int available = Math.max(0, width - tx - rightReserved); int maxEntries = Math.max(0, available / perEntry);
            List<DraggableWindow> visibleWindows = new ArrayList<>(); if (maxEntries > 0) { int start = Math.max(0, openApps.size() - maxEntries); for (int i = start; i < openApps.size(); i++) { DraggableWindow w0 = openApps.get(i); if (isTaskbarEligible(w0)) visibleWindows.add(w0); } }
            int tx2 = tx; for (DraggableWindow w : visibleWindows) { int x0 = tx2, y0 = tbY + 2, x1 = tx2 + perEntry - 6, y1 = height - 4; if (mouseX >= x0 && mouseX <= x1 && mouseY >= y0 && mouseY <= y1) { if (w.minimized) { w.restore(); bringToFront(w); } else if (openApps.get(openApps.size()-1) != w) bringToFront(w); else { w.minimized = true; } playClick(); return super.mouseClicked(mouseX, mouseY, button); } tx2 += perEntry; }
            selectedIcons.clear(); return super.mouseClicked(mouseX, mouseY, button);
        }

        for (DesktopIcon di : desktopIcons) {
            if (di.isInside(mouseX, mouseY, iconSize)) {
                long now = System.currentTimeMillis();
                if (selectedIcons.contains(di) && (now - lastClickTime) < DOUBLE_CLICK_MS) { di.onClick.run(); selectedIcons.clear(); iconPressed = null; iconDragging = false; playClick(); }
                else { selectedIcons.clear(); selectedIcons.add(di); iconPressed = di; iconDragging = false; iconDragStartX = mouseX; iconDragStartY = mouseY; iconStartPositions.clear(); for (DesktopIcon ic : selectedIcons) iconStartPositions.put(ic, new int[]{ic.targetX, ic.targetY}); lastClickTime = now; }
                return super.mouseClicked(mouseX, mouseY, button);
            }
        }

        selectedIcons.clear(); iconPressed = null; iconDragging = false; selecting = true; selectStartX = selectEndX = (int)mouseX; selectStartY = selectEndY = (int)mouseY; return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
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
                if (renameBox != null) renameBox.mouseReleased(mouseX, mouseY, button);
                int ix0 = Math.round(ic.displayX), iy0 = Math.round(ic.displayY);
                int ix1 = ix0 + iconSize, iy1 = iy0 + iconSize;
                if (ix1 >= x0 && ix0 <= x1 && iy1 >= y0 && iy0 <= y1) selectedIcons.add(ic);
            }
        }
        iconDragging = false; iconPressed = null; iconStartPositions.clear(); selecting = false;
        searchBox.mouseReleased(mouseX, mouseY, button);
        for (DraggableWindow w : openApps) w.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (iconPressed != null && button == 0) {
            if (!iconDragging) { iconDragging = true; iconStartPositions.clear(); for (DesktopIcon ic : selectedIcons) iconStartPositions.put(ic, new int[]{ic.targetX, ic.targetY}); iconDragStartX = mouseX; iconDragStartY = mouseY; }
            int deltaX = (int)(mouseX - iconDragStartX); int deltaY = (int)(mouseY - iconDragStartY);
            for (DesktopIcon ic : selectedIcons) { int[] s = iconStartPositions.getOrDefault(ic, new int[]{ic.targetX, ic.targetY}); ic.targetX = s[0] + deltaX; ic.targetY = s[1] + deltaY; }
            return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        }
        if (selecting && !iconDragging) {
            selectEndX = (int) mouseX; selectEndY = (int) mouseY;
            int x0 = Math.min(selectStartX, selectEndX), y0 = Math.min(selectStartY, selectEndY);
            int x1 = Math.max(selectStartX, selectEndX), y1 = Math.max(selectStartY, selectEndY);
            selectedIcons.clear();
            for (DesktopIcon ic : desktopIcons) {
                int ix0 = Math.round(ic.displayX), iy0 = Math.round(ic.displayY);
                int ix1 = ix0 + iconSize, iy1 = iy0 + iconSize;
                if (ix1 >= x0 && ix0 <= x1 && iy1 >= y0 && iy0 <= y1) selectedIcons.add(ic);
            }
        }
        DraggableWindow fw = findWindowAt(mouseX, mouseY); if (fw != null) fw.mouseDragged(mouseX, mouseY);
        searchBox.mouseDragged(mouseX, mouseY, button, dx, dy);
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        DraggableWindow top = findWindowAt(mouseX, mouseY);
        if (top != null && !top.minimized) return top.mouseScrolled(mouseX, mouseY, delta);
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 341 || keyCode == 345) { ctrlPressed = true; return true; }
        if (keyCode == 340 || keyCode == 344) { shiftPressed = true; return true; }
        if (keyCode == 32) { // spacebar
            try { LaptopKeySoundManager.playKey(' '); } catch (Exception ignored) {}
            sendTypingPacketMaybe();
            return true;
        }
        if (ctrlPressed) {
            switch (keyCode) {
                case 65: selectAllIcons(); return true;
                case 67: copySelectedIcons(); return true;
                case 86: pasteFromClipboard(); return true;
                case 88: cutSelectedIcons(); return true;
                case 78: createNewFolderOnDesktop(); return true;
                case 83: return handleAppSpecificKeybind(keyCode, scanCode, modifiers);
                case 87:
                    if (shiftPressed) net.chaoscraft.chaoscrafts_device_mod.client.screen.DraggableWindow.closeAllWindows();
                    else if (!openApps.isEmpty()) { DraggableWindow top = openApps.get(openApps.size()-1); if (top != null) { top.requestClose(); playClick(); } }
                    return true;
            }
        }
        if (keyCode == 292) { showDebugInfo = !showDebugInfo; return true; }
        if (keyCode == 256) { if (contextMenu != null) { contextMenu = null; return true; } Minecraft.getInstance().setScreen(null); return true; }
        if (!openApps.isEmpty()) { DraggableWindow top = openApps.get(openApps.size()-1); if (top != null && !top.minimized) { boolean consumed = top.app.keyPressed(top, keyCode, scanCode, modifiers); if (consumed) return true; } }
        if (searchBox.isFocused()) return searchBox.keyPressed(keyCode, scanCode, modifiers);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 341 || keyCode == 345) { ctrlPressed = false; return true; }
        if (keyCode == 340 || keyCode == 344) { shiftPressed = false; return true; }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char typedChar, int keyCode) {
        if (typedChar != 0 && typedChar != ' ' && !Character.isISOControl(typedChar)) {
            try { LaptopKeySoundManager.playKey(typedChar); } catch (Exception ignored) {}
            sendTypingPacketMaybe();
        }
        if (!openApps.isEmpty()) {
            DraggableWindow top = openApps.get(openApps.size()-1);
            if (top != null && !top.minimized) {
                boolean consumed = top.app.charTyped(top, typedChar, keyCode);
                if (consumed) return true;
            }
        }
        if (searchBox.isFocused()) return searchBox.charTyped(typedChar, keyCode);
        return super.charTyped(typedChar, keyCode);
    }

    private boolean handleAppSpecificKeybind(int keyCode, int scanCode, int modifiers) {
        if (!openApps.isEmpty()) { DraggableWindow top = openApps.get(openApps.size()-1); if (top != null && !top.minimized) return top.app.keyPressed(top, keyCode, scanCode, modifiers); }
        return false;
    }

    private void playClick() { Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F)); }

    public void openAppSingle(String name, int w, int h) {
        for (DraggableWindow w0 : openApps) { if (w0.appName.equalsIgnoreCase(name)) { w0.restore(); w0.removeRequested = false; bringToFront(w0); return; } }
        IApp app = AppFactory.create(name); if (app == null) return;
        int startX = Math.max(40, (this.width - w) / 2);
        int startY = Math.max(40, (this.height - h) / 3);
        DraggableWindow wdw = new DraggableWindow(name, app, Math.min(w, this.width - 80), Math.min(h, this.height - 80), startX, startY);
        openApps.add(wdw); bringToFront(wdw);
    }

    private void bringToFront(DraggableWindow w) { openApps.remove(w); openApps.add(w); }

    public void showContextMenu(int x, int y) { this.contextMenu = new DesktopContextMenu(this, x, y); }

    public void refreshDesktopIcons() {
        desktopIcons.clear(); List<FilesManager.DesktopIconState> iconStates = FilesManager.getInstance().getDesktopIcons();
        for (FilesManager.DesktopIconState state : iconStates) {
            if (AppRegistry.getInstance().isInstalled(state.name)) {
                desktopIcons.add(new DesktopIcon(state.name, state.x, state.y, () -> {
                    if (state.name.endsWith(".txt")) openAppSingle("Files", 780, 520); else openAppSingle(state.name, 900, 600);
                }));
            }
        }
    }

    public void setIconSize(int size) { this.iconSize = size; for (DesktopIcon icon : desktopIcons) icon.iconSize = size; }
    public void refresh() { FilesManager.getInstance().saveState(); }
    public void openSettingsApp() { openAppSingle("Settings", 520, 480); }
    public void sortIconsByName() { desktopIcons.sort((a,b)->a.name.compareToIgnoreCase(b.name)); arrangeIconsInGrid(); }
    public void sortIconsByDate() { sortIconsByName(); }
    public void sortIconsBySize() { sortIconsByName(); }

    private void arrangeIconsInGrid() {
        int cols = Math.max(1, (width - 100) / (iconSize + 80)); int x = 50; int y = 60; int col = 0;
        for (DesktopIcon icon : desktopIcons) {
            icon.targetX = x; icon.targetY = y; col++;
            if (col >= cols) { col = 0; x = 50; y += iconSize + 40; } else { x += iconSize + 80; }
            FilesManager.getInstance().updateDesktopIconPosition(icon.name, icon.targetX, icon.targetY);
        }
    }

    private void selectAllIcons() { selectedIcons.clear(); selectedIcons.addAll(desktopIcons); }
    private void copySelectedIcons() {}
    private void cutSelectedIcons() {}
    private void pasteFromClipboard() {}

    public void createNewFolderOnDesktop() {
        String baseName = "New Folder"; String name = baseName; int counter = 1;
        while (new File(desktopDir, name).exists()) { name = baseName + " (" + counter + ")"; counter++; }
        File newFolder = new File(desktopDir, name);
        if (newFolder.mkdir()) { FilesManager.getInstance().addDesktopIcon(name, (int)(Math.random()*(width-100))+50, (int)(Math.random()*(height-100))+50); refreshDesktopIcons(); }
    }

    public void createNewTextFileOnDesktop() {
        String name = "New Text File.txt"; int counter = 1;
        while (new File(desktopDir, name).exists()) { name = "New Text File (" + counter + ").txt"; counter++; }
        File newFile = new File(desktopDir, name);
        try { if (newFile.createNewFile()) { FilesManager.getInstance().addDesktopIcon(name, (int)(Math.random()*(width-100))+50, (int)(Math.random()*(height-100))+50); refreshDesktopIcons(); } }
        catch (IOException e) { System.err.println("Failed to create new text file: " + e); }
    }

    // adjust sendTypingPacketMaybe to include devicePos
    private void sendTypingPacketMaybe() {
        long now = System.currentTimeMillis();
        if (now - lastKeyTypeMillis >= KEY_PACKET_COOLDOWN_MS) {
            lastKeyTypeMillis = now;
            try { NetworkHandler.sendToServer(new LaptopTypingPacket(devicePos)); } catch (Exception ignored) {}
        }
    }

    private static class SearchResult { final String displayName; final ResourceLocation iconRes; final Runnable action; SearchResult(String d, ResourceLocation i, Runnable a){displayName=d;iconRes=i;action=a;} }

    private static class DesktopIcon {
        final String name; int targetX, targetY; float displayX, displayY; final Runnable onClick; int iconSize = 32;
        DesktopIcon(String name, int x, int y, Runnable onClick) { this.name = name; this.targetX = (x / ICON_GRID) * ICON_GRID; this.targetY = (y / ICON_GRID) * ICON_GRID; this.displayX = this.targetX; this.displayY = this.targetY; this.onClick = onClick; }
        void render(GuiGraphics g, int mouseX, int mouseY, boolean selected, int currentIconSize) {
            this.iconSize = currentIconSize; int dx = Math.round(displayX), dy = Math.round(displayY);
            boolean hover = mouseX >= dx && mouseX <= dx + iconSize && mouseY >= dy && mouseY <= dy + iconSize;
            if (selected) g.fill(dx - 6, dy - 6, dx + iconSize + 6, dy + iconSize + 14, 0x2233AAFF);
            g.fill(dx - 1, dy + iconSize + 1, dx + iconSize, dy + iconSize + 3, 0xAA000000);
            String key = name.contains(".") ? null : normalizeAppNameForIcon(name);
            ResourceLocation iconRes = IconManager.getIconResource(key);
            try { g.blit(iconRes, dx + 2, dy + 2, 0, 0, iconSize - 4, iconSize - 4, iconSize - 4, iconSize - 4); } catch (Exception ignored) {}
            if (hover) g.fill(dx - 2, dy - 2, dx + iconSize + 2, dy + iconSize + 2, DraggableWindow.selectionOverlayColor());
            String displayName = toTitleCase(name);
            if (Minecraft.getInstance().font.width(displayName) > iconSize + 10) displayName = Minecraft.getInstance().font.plainSubstrByWidth(displayName, iconSize + 5) + "...";
            g.drawString(Minecraft.getInstance().font, Component.literal(displayName), dx, dy + iconSize + 4, DraggableWindow.textPrimaryColor(), false);
        }
        boolean isInside(double mouseX, double mouseY, int currentIconSize) { return mouseX >= displayX && mouseX <= displayX + currentIconSize && mouseY >= displayY && mouseY <= displayY + currentIconSize; }
    }
}
