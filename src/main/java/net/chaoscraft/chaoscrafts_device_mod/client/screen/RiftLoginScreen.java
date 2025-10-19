package net.chaoscraft.chaoscrafts_device_mod.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.player.AbstractClientPlayer;
import org.lwjgl.glfw.GLFW;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.*;
import java.io.IOException;
import net.chaoscraft.chaoscrafts_device_mod.ConfigHandler;

/**
 * Fullscreen Windows-style lock/login screen.
 * Avatars are drawn as true circles with perfectly smooth white outlines.
 */
public class RiftLoginScreen extends Screen {
    private final Screen previous;
    private final String displayName;
    private final String email;
    private ResourceLocation skinRes = null;

    private int avatarResolution = 256;

    private final Map<String, List<int[]>> avatarCellsCache = new HashMap<>();

    private final Map<String, ResourceLocation> avatarTextureCache = new HashMap<>();

    private int pinFieldX = -1, pinFieldY = -1;
    private int pinFieldWidth = 340, pinFieldHeight = 48;
    private int profileIconX = -1, profileIconY = -1;
    private int profileIconSize = 160;
    private int displayNameX = -1, displayNameY = -1;
    private float displayNameScale = 1.6f;

    private static final ResourceLocation WALLPAPER_PNG = ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "textures/login_wallpaper/login_wallpaper.png");
    private static final ResourceLocation WALLPAPER_BLUR = ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "textures/login_wallpaper/login_wallpaper_blurred.png");

    private boolean showSignIn;
    private float transition = 0f;
    private static final float TRANSITION_SPEED = 0.12f;

    private boolean closingTransition = false;
    private float closingProgress = 0f;
    private static final float CLOSING_SPEED = 0.08f;

    private long lastTileClickTime = 0L;
    private static final long DOUBLE_CLICK_MS = 400L;

    private long lastBgClickTime = 0L;

    private boolean showTile = false;

    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("hh:mm a");
    private final DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("M/d/yyyy");

    private ResourceLocation lastSkinRes = null;

    private final java.util.Set<String> bakingTextures = new java.util.HashSet<>();

    public RiftLoginScreen(Screen previous) {
        super(Component.literal("RiftOS Login"));
        this.previous = previous;
        String uname = "Player";
        try {
            if (Minecraft.getInstance().player != null) uname = Minecraft.getInstance().player.getGameProfile().getName();
        } catch (Exception ignored) {}
        this.displayName = uname;
        this.email = uname.toLowerCase(java.util.Locale.ROOT) + "@rift.com";
        try {
            if (Minecraft.getInstance().player != null) {
                skinRes = Minecraft.getInstance().player.getSkinTextureLocation();
            }
        } catch (Exception ignored) { skinRes = null; }

        this.showSignIn = false;
    }

    @Override
    protected void init() {
        super.init();
        this.showSignIn = false;
    }

    @Override
    public void tick() {
        super.tick();
        float target = showSignIn ? 1f : 0f;
        if (transition < target) transition = Math.min(target, transition + TRANSITION_SPEED);
        else if (transition > target) transition = Math.max(target, transition - TRANSITION_SPEED);

        if (closingTransition) {
            closingProgress = Math.min(1f, closingProgress + CLOSING_SPEED);
            if (closingProgress >= 1f) {
                try {
                    Minecraft.getInstance().setScreen(previous);
                } catch (Exception ignored) {}
                return;
            }
        }

        if (!showSignIn && showTile) {
            showTile = false;
        }

        try {
            if (skinRes != null) {
                String largeKey = (skinRes == null ? "default" : skinRes.toString()) + "#" + profileIconSize + "#" + avatarResolution + "#1";
                String smallKey = (skinRes == null ? "default" : skinRes.toString()) + "#" + 48 + "#" + avatarResolution + "#1";
                if (!avatarTextureCache.containsKey(largeKey)) {
                    ensureAvatarTextureAsync(skinRes, profileIconSize);
                }
                if (!avatarTextureCache.containsKey(smallKey)) {
                    ensureAvatarTextureAsync(skinRes, 48);
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (transition > 0.3f) { onLogin(); return true; }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (showSignIn) { showSignIn = false; showTile = false; return true; }
            Minecraft.getInstance().setScreen(previous); return true;
        }

        if (keyCode == GLFW.GLFW_KEY_EQUAL) {
            avatarResolution = Math.min(256, avatarResolution + 16);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_MINUS) {
            avatarResolution = Math.max(16, avatarResolution - 16);
            return true;
        }

        if (Minecraft.getInstance().options.renderDebug) {
            if (keyCode == GLFW.GLFW_KEY_UP) pinFieldY -= 5;
            if (keyCode == GLFW.GLFW_KEY_DOWN) pinFieldY += 5;
            if (keyCode == GLFW.GLFW_KEY_LEFT) pinFieldX -= 5;
            if (keyCode == GLFW.GLFW_KEY_RIGHT) pinFieldX += 5;
            if (keyCode == GLFW.GLFW_KEY_PAGE_UP) { pinFieldWidth += 10; pinFieldHeight += 5; }
            if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) { pinFieldWidth = Math.max(100, pinFieldWidth - 10); pinFieldHeight = Math.max(30, pinFieldHeight - 5); }

            if (keyCode == GLFW.GLFW_KEY_W) profileIconY -= 5;
            if (keyCode == GLFW.GLFW_KEY_S) profileIconY += 5;
            if (keyCode == GLFW.GLFW_KEY_A) profileIconX -= 5;
            if (keyCode == GLFW.GLFW_KEY_D) profileIconX += 5;
            if (keyCode == GLFW.GLFW_KEY_Q) profileIconSize = Math.max(50, profileIconSize - 10);
            if (keyCode == GLFW.GLFW_KEY_E) profileIconSize = Math.min(300, profileIconSize + 10);

            if (keyCode == GLFW.GLFW_KEY_I) displayNameY -= 5;
            if (keyCode == GLFW.GLFW_KEY_K) displayNameY += 5;
            if (keyCode == GLFW.GLFW_KEY_J) displayNameX -= 5;
            if (keyCode == GLFW.GLFW_KEY_L) displayNameX += 5;
            if (keyCode == GLFW.GLFW_KEY_U) displayNameScale = Math.max(0.5f, displayNameScale - 0.1f);
            if (keyCode == GLFW.GLFW_KEY_O) displayNameScale = Math.min(3.0f, displayNameScale + 0.1f);

            if (keyCode == GLFW.GLFW_KEY_R) {
                pinFieldX = -1; pinFieldY = -1; pinFieldWidth = 340; pinFieldHeight = 48;
                profileIconX = -1; profileIconY = -1; profileIconSize = 160;
                displayNameX = -1; displayNameY = -1; displayNameScale = 1.6f;
            }

            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float uiScale = ConfigHandler.uiScaleFactor();
        double sx = mouseX / uiScale;
        double sy = mouseY / uiScale;
        int lw = Math.round(this.width / uiScale);
        int lh = Math.round(this.height / uiScale);

        int mx = Math.round((float) sx);
        int my = Math.round((float) sy);

        int tileW = 220; int tileH = 64;
        int tileX = 10; int tileY = lh - tileH - 20;
        boolean tileVisible = showTile || showSignIn;

        if (tileVisible && mx >= tileX && mx < tileX + tileW && my >= tileY && my < tileY + tileH) {
            long now = System.currentTimeMillis();
            if (now - lastTileClickTime <= DOUBLE_CLICK_MS) {
                showSignIn = true;
                showTile = true;
                lastTileClickTime = 0L;
            } else {
                lastTileClickTime = now;
            }
            return true;
        }

        if (transition > 0.2f) {
            int panelW = Math.min(560, lw - 120);
            int panelH = Math.min(320, lh - 160);
            int bx = (lw - panelW) / 2;
            int by = (lh - panelH) / 2;
            if (!(mx >= bx && mx < bx + panelW && my >= by && my < by + panelH)) {
                showSignIn = false;
                showTile = false;
                return true;
            }

            int centerX = lw / 2;

            int avatarY = (profileIconY == -1) ? (int) (lh * 0.42) - profileIconSize / 2 : profileIconY;
            int inputX = (pinFieldX == -1) ? centerX - pinFieldWidth / 2 : pinFieldX;
            int inputY = (pinFieldY == -1) ? avatarY + profileIconSize + 40 : pinFieldY;

            int arrowButtonSize = pinFieldHeight;
            int arrowButtonX = inputX + pinFieldWidth - arrowButtonSize;

            if (mx >= arrowButtonX && mx < arrowButtonX + arrowButtonSize &&
                    my >= inputY && my < inputY + pinFieldHeight) {
                onLogin();
                return true;
            }
        }

        if (!showSignIn) {
            long now = System.currentTimeMillis();
            if (now - lastBgClickTime <= DOUBLE_CLICK_MS) {
                 showSignIn = true;
                 showTile = true;
                 lastBgClickTime = 0L;
             } else {
                 lastBgClickTime = now;
             }
             return true;
         }

         return super.mouseClicked(mouseX, mouseY, button);
     }

    private void onLogin() {
        if (!closingTransition) {
            closingTransition = true;
            closingProgress = 0f;
        }
    }

    @Override
    @SuppressWarnings("null")
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        float uiScale = ConfigHandler.uiScaleFactor();
        int sMouseX = Math.round(mouseX / uiScale);
        int sMouseY = Math.round(mouseY / uiScale);
        int lw = Math.round(this.width / uiScale);
        int lh = Math.round(this.height / uiScale);

        gui.pose().pushPose();
        gui.pose().scale(uiScale, uiScale, 1f);

        if (skinRes == null) {
            try { if (Minecraft.getInstance().player != null) skinRes = Minecraft.getInstance().player.getSkinTextureLocation(); } catch (Exception ignored) { skinRes = null; }
        }

        try {
            ResourceLocation currentSkin = null;
            try { if (Minecraft.getInstance().player != null) currentSkin = Minecraft.getInstance().player.getSkinTextureLocation(); } catch (Exception ignored) { currentSkin = null; }
            if (currentSkin != null && (lastSkinRes == null || !currentSkin.equals(lastSkinRes))) {
                clearAvatarCacheFor(lastSkinRes);
                clearAvatarCacheFor(currentSkin);
                lastSkinRes = currentSkin;
            }
        } catch (Exception ignored) {}

        boolean wallpaperOK;
        try {
            gui.blit(WALLPAPER_PNG, 0, 0, 0, 0, lw, lh, lw, lh);
            wallpaperOK = true;
        } catch (Exception ignored) {
            wallpaperOK = false;
            gui.fill(0, 0, lw, lh, 0xFF071018);
        }

        if (wallpaperOK && transition > 0.01f) {
            try {
                gui.blit(WALLPAPER_BLUR, 0, 0, 0, 0, lw, lh, lw, lh);
            } catch (Exception ignored) {}
        }

        if (wallpaperOK && transition > 0.01f) {
            int darkAlpha = Math.round(24f * transition);
            int darkCol = (darkAlpha << 24);
            gui.fill(0, 0, lw, lh, darkCol);
        }

        int frostAlpha = Math.round(120f * transition);
        if (frostAlpha > 0) {
            int frostCol = (frostAlpha << 24);
            gui.fill(0, 0, lw, lh, frostCol);
        }

        LocalDateTime now = LocalDateTime.now();
        String timeStr = now.format(timeFmt);
        String dateStr = now.format(dateFmt);
        float timeScale = 2.4f;
        float dateScale = 1.0f;
        int timeX = 12;
        int timeY = lh - 88;

        if (transition < 0.2f) {
            gui.pose().pushPose();
            gui.pose().scale(timeScale, timeScale, 1f);
            gui.drawString(font, Component.literal(timeStr), (int) (timeX / timeScale), (int) (timeY / timeScale), 0xFFFFFFFF, false);
            gui.pose().popPose();

            gui.pose().pushPose();
            gui.pose().scale(dateScale, dateScale, 1f);
            gui.drawString(font, Component.literal(dateStr), (int) (timeX / dateScale), (int) ((timeY + (int)(font.lineHeight * timeScale) + 4) / dateScale), 0xCCFFFFFF, false);
            gui.pose().popPose();
        }

        int tileW = 220; int tileH = 64;
        int tileX = 10; int tileY = lh - tileH - 20;
        int tileRadius = 10;
        int tileBg = 0x88000000;
        int tileInner = 0x22000000;

        if (showTile || showSignIn) {
            gui.fill(tileX, tileY, tileX + tileW, tileY + tileH, tileBg);
            gui.fill(tileX + 1, tileY + 1, tileX + tileW - 1, tileY + tileH - 1, tileInner);

            int avSize = 48;
            int avX = tileX + 12;
            int avY = tileY + (tileH - avSize) / 2;

            try {
                if (skinRes != null) {
                    String largeKey = (skinRes == null ? "default" : skinRes.toString()) + "#" + profileIconSize + "#" + avatarResolution + "#1";
                    ResourceLocation largeTex = avatarTextureCache.get(largeKey);
                    if (largeTex != null) {
                        try {
                            gui.blit(largeTex, avX, avY, avSize, avSize, 0, 0, profileIconSize, profileIconSize, profileIconSize, profileIconSize);
                        } catch (Exception ignored) {
                            drawCircularAvatar(gui, skinRes, avX, avY, avSize);
                        }
                    } else {
                        drawCircularAvatar(gui, skinRes, avX, avY, avSize);
                    }
                } else {
                    filledCircleUI(gui, avX + avSize / 2, avY + avSize / 2, avSize / 2, 0xFFCCCCCC);
                }

            } catch (Exception ignored) {}

            int textX = avX + avSize + 12;
            int rightPadding = 12;
            int textMaxWidth = tileX + tileW - rightPadding - textX;
            String nameToShow = displayName != null ? displayName : "Player";
            String emailToShow = email != null ? email : "";
            String nameClipped = clipText(font, nameToShow, Math.max(0, textMaxWidth));
            String emailClipped = clipText(font, emailToShow, Math.max(0, textMaxWidth));
            int nameY = avY + (avSize - font.lineHeight * 2) / 2;
            int emailY = nameY + font.lineHeight;
            drawString(gui, font, nameClipped, textX, nameY, 0xFFFFFFFF);
            drawString(gui, font, emailClipped, textX, emailY, 0x99FFFFFF);
        }

        if (transition > 0.01f || closingTransition) {
            int centerX = lw / 2;

            int avatarX = (profileIconX == -1) ? centerX - profileIconSize / 2 : profileIconX;
            int avatarY = (profileIconY == -1) ? (int) (lh * 0.42) - profileIconSize / 2 : profileIconY;
            int inputX = (pinFieldX == -1) ? centerX - pinFieldWidth / 2 : pinFieldX;
            int inputY = (pinFieldY == -1) ? avatarY + profileIconSize + 40 : pinFieldY;
            int nameX = (displayNameX == -1) ? centerX : displayNameX;
            int nameY = (displayNameY == -1) ? avatarY + profileIconSize + 12 : displayNameY;

            float animAvatarX = avatarX;
            float animAvatarY = avatarY;
            float animSize = profileIconSize;
            if (closingTransition) {
                int targetSize = 48;
                int targetX = tileX + 12;
                int targetY = tileY + (tileH - targetSize) / 2;
                float t = closingProgress;
                float tt = t * t * (3f - 2f * t);
                animAvatarX = avatarX + (targetX - avatarX) * tt;
                animAvatarY = avatarY + (targetY - avatarY) * tt;
                animSize = profileIconSize + (targetSize - profileIconSize) * tt;
            }

            try {
                if (skinRes != null) {
                    drawCircularAvatar(gui, skinRes, Math.round(animAvatarX), Math.round(animAvatarY), Math.max(1, Math.round(animSize)));
                } else {
                    filledCircleUI(gui, avatarX + profileIconSize / 2, avatarY + profileIconSize / 2, profileIconSize / 2, 0xFFCCCCCC);
                }
            } catch (Exception ignored) {
                filledCircleUI(gui, avatarX + profileIconSize / 2, avatarY + profileIconSize / 2, profileIconSize / 2, 0xFFCCCCCC);
            }

            if (closingTransition) {
                int alpha = Math.round(255f * closingProgress);
                int fadeCol = (alpha << 24);
                gui.fill(0, 0, lw, lh, fadeCol);
                return;
            }

            int fieldBg = 0xCC1A1A1A;
            gui.fill(inputX, inputY, inputX + pinFieldWidth, inputY + pinFieldHeight, fieldBg);

            int fieldBorder = 0xFF404040;
            gui.fill(inputX, inputY, inputX + pinFieldWidth, inputY + 1, fieldBorder);
            gui.fill(inputX, inputY + pinFieldHeight - 1, inputX + pinFieldWidth, inputY + pinFieldHeight, fieldBorder);
            gui.fill(inputX, inputY, inputX + 1, inputY + pinFieldHeight, fieldBorder);
            gui.fill(inputX + pinFieldWidth - 1, inputY, inputX + pinFieldWidth, inputY + pinFieldHeight, fieldBorder);

            int arrowButtonSize = pinFieldHeight;
            int arrowButtonX = inputX + pinFieldWidth - arrowButtonSize;

            boolean hoverArrow = (sMouseX >= arrowButtonX && sMouseX < arrowButtonX + arrowButtonSize &&
                    sMouseY >= inputY && sMouseY < inputY + pinFieldHeight);

            int arrowBg = hoverArrow ? 0x66FFFFFF : 0x33FFFFFF;
            gui.fill(arrowButtonX, inputX == inputX ? inputY : inputY, arrowButtonX + arrowButtonSize, inputY + pinFieldHeight, arrowBg);

            gui.fill(arrowButtonX, inputY, arrowButtonX + arrowButtonSize, inputY + 1, fieldBorder);
            gui.fill(arrowButtonX, inputY + pinFieldHeight - 1, arrowButtonX + arrowButtonSize, inputY + pinFieldHeight, fieldBorder);
            gui.fill(arrowButtonX, inputY, arrowButtonX + 1, inputY + pinFieldHeight, fieldBorder);

            int arrowColor = 0xFF000000;
            int arrowSize = 16;
            int arrowCenterX = arrowButtonX + arrowButtonSize / 2;
            int arrowCenterY = inputY + pinFieldHeight / 2;

            for (int i = 0; i < arrowSize / 2; i++) {
                gui.fill(arrowCenterX - arrowSize/4 + i, arrowCenterY - arrowSize/4 + i/2,
                        arrowCenterX - arrowSize/4 + i + 1, arrowCenterY - arrowSize/4 + i/2 + 1, arrowColor);
                gui.fill(arrowCenterX - arrowSize/4 + i, arrowCenterY + arrowSize/4 - i/2,
                        arrowCenterX - arrowSize/4 + i + 1, arrowCenterY + arrowSize/4 - i/2 + 1, arrowColor);
            }

            String placeholder = "Enter PIN";
            int textYPos = inputY + (pinFieldHeight - font.lineHeight) / 2;
            drawString(gui, font, placeholder, inputX + 12, textYPos, 0xFF888888);

            int linkY = inputY + pinFieldHeight + 16;
            drawCenteredString(gui, font, "Forgot PIN?", centerX, linkY, 0xFF0066CC);
            drawCenteredString(gui, font, "Sign-in options", centerX, linkY + font.lineHeight + 8, 0xFF0066CC);

            String dn = displayName != null ? displayName : "Player";
            int rawNameW = font.width(dn);
            int scaledNameX = (int) ((nameX - (rawNameW * displayNameScale) / 2f) / displayNameScale);
            int scaledNameY = (int) (nameY / displayNameScale);
            gui.pose().pushPose();
            gui.pose().scale(displayNameScale, displayNameScale, 1f);
            gui.drawString(font, Component.literal(dn), scaledNameX, scaledNameY, 0xFFFFFFFF, false);
            gui.pose().popPose();

            String[] iconGlyphs = new String[] {"⚙", "⏻", "☰"};
            int iconsCount = iconGlyphs.length;
            int radiusBtn = 16;
            int spacing = 8;
            int totalWidth = iconsCount * (radiusBtn * 2) + (iconsCount - 1) * spacing;
            int startX = width - totalWidth - 12;
            int centerYIcons = height - 28;

            if (transition < 0.2f) {
                for (int i = 0; i < iconsCount; i++) {
                    int cxBtn = startX + radiusBtn + i * (2 * radiusBtn + spacing);
                    int cyBtn = centerYIcons;

                    boolean hover = (sMouseX - cxBtn) * (sMouseX - cxBtn) + (sMouseY - cyBtn) * (sMouseY - cyBtn) <= radiusBtn * radiusBtn;

                    int bgCol = hover ? 0x66FFFFFF : 0x33FFFFFF;
                    filledCircleUI(gui, cxBtn, cyBtn, radiusBtn, bgCol);

                    String glyph = iconGlyphs[i];
                    int glyphW = font.width(glyph);
                    int glyphX = cxBtn - glyphW / 2;
                    int glyphY = cyBtn - font.lineHeight / 2;
                    int glyphCol = hover ? 0xFF000000 : 0xFFFFFFFF;
                    gui.drawString(font, Component.literal(glyph), glyphX, glyphY, glyphCol, false);
                }
            }

            if (Minecraft.getInstance().options.renderDebug) {
                drawString(gui, font, "Avatar Res: " + avatarResolution + " (Press +/- to change)", 10, 10, 0xFFFFFFFF);
                drawString(gui, font, "PIN Field: " + inputX + "," + inputY + " " + pinFieldWidth + "x" + pinFieldHeight + " (Arrow keys + Page Up/Down)", 10, 25, 0xFFFFFFFF);
                drawString(gui, font, "Profile: " + avatarX + "," + avatarY + " Size: " + profileIconSize + " (WASD + Q/E)", 10, 40, 0xFFFFFFFF);
                drawString(gui, font, "Name: " + nameX + "," + nameY + " Scale: " + displayNameScale + " (IJKL + U/O)", 10, 55, 0xFFFFFFFF);
                drawString(gui, font, "Press R to reset all positions", 10, 70, 0xFFFFFFFF);
            }
        }

        gui.pose().popPose();
    }

    private void drawCenteredString(GuiGraphics gui, Font font, String text, int cx, int y, int color) {
        if (text == null) return;
        int w = font.width(text);
        gui.drawString(font, Component.literal(text), cx - w / 2, y, color, false);
    }

    private void drawString(GuiGraphics gui, Font font, String text, int x, int y, int color) {
        if (text == null) return;
        gui.drawString(font, Component.literal(text), x, y, color, false);
    }

    private void filledCircleUI(GuiGraphics gui, int cx, int cy, int r, int color) {
        if (r <= 0) return;
        for (int dy = -r; dy <= r; dy++) {
            int span = (int) Math.sqrt(r * r - dy * dy);
            gui.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, color);
        }
    }

    private void roundedRectUI(GuiGraphics gui, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        int rx = Math.max(0, r);
        gui.fill(x + rx, y, x + w - rx, y + h, color);
        gui.fill(x, y + rx, x + rx, y + h - rx, color);
        gui.fill(x + w - rx, y + rx, x + w, y + h - rx, color);
        filledCircleUI(gui, x + rx, y + rx, rx, color);
        filledCircleUI(gui, x + w - rx - 1, y + rx, rx, color);
        filledCircleUI(gui, x + rx, y + h - rx - 1, rx, color);
        filledCircleUI(gui, x + w - rx - 1, y + h - rx - 1, rx, color);
    }

    private void drawCircularAvatar(GuiGraphics gui, ResourceLocation skin, int x, int y, int size) {
        if (size <= 0) return;

        String key = (skin == null ? "default" : skin.toString()) + "#" + size + "#" + avatarResolution + "#1";
        ResourceLocation tex = avatarTextureCache.get(key);
        if (tex == null) {
            tex = getOrCreateAvatarTexture(skin, size, true);
        }
        if (tex != null) {
            try {
                gui.blit(tex, x, y, size, size, 0, 0, size, size, size, size);
                return;
            } catch (Exception ignored) { }
        }

        List<int[]> cells = avatarCellsCache.get(key);
        if (cells == null) {
            cells = new ArrayList<>();
            int radius = size / 2;
            int centerX = radius;
            int centerY = radius;
            int step = Math.max(1, (int) Math.ceil((double) size / avatarResolution));

            for (int px = 0; px < size; px += step) {
                for (int py = 0; py < size; py += step) {
                    int cellSize = Math.min(step, Math.min(size - px, size - py));
                    float cellCenterX = px + cellSize / 2f;
                    float cellCenterY = py + cellSize / 2f;
                    float dx = cellCenterX - centerX;
                    float dy = cellCenterY - centerY;
                    float distance = (float) Math.sqrt(dx * dx + dy * dy);
                    if (distance <= radius - 0.5f) {
                        int srcU = 8 + (int) ((px / (float) size) * 8);
                        int srcV = 8 + (int) ((py / (float) size) * 8);
                        cells.add(new int[] { px, py, cellSize, srcU, srcV });
                    }
                }
            }

            avatarCellsCache.put(key, cells);
        }

        for (int[] c : cells) {
            int px = c[0];
            int py = c[1];
            int cellSize = c[2];
            int srcU = c[3];
            int srcV = c[4];

            int destX = x + px;
            int destY = y + py;

            try {
                if (skin != null) gui.blit(skin, destX, destY, cellSize, cellSize, srcU, srcV, 1, 1, 64, 64);
                else gui.fill(destX, destY, destX + cellSize, destY + cellSize, 0xFFCCCCCC);

                if (true && skin != null) {
                    gui.blit(skin, destX, destY, cellSize, cellSize, 40 + srcU - 8, srcV, 1, 1, 64, 64);
                }
            } catch (Exception ignored) {
                gui.fill(destX, destY, destX + cellSize, destY + cellSize, 0xFFCCCCCC);
            }
        }
    }

    private void clearAvatarCacheFor(ResourceLocation skin) {
        if (skin == null) {
            try {
                List<String> keys = new ArrayList<>(avatarTextureCache.keySet());
                for (String k : keys) {
                    if (k.startsWith("default#")) {
                        ResourceLocation rl = avatarTextureCache.remove(k);
                        if (rl != null) {
                            try { Minecraft.getInstance().getTextureManager().release(rl); } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}

            avatarCellsCache.keySet().removeIf(k -> k.startsWith("default#"));
        } else {
            String prefix = skin.toString() + "#";
            try {
                List<String> keys = new ArrayList<>(avatarTextureCache.keySet());
                for (String k : keys) {
                    if (k.startsWith(prefix)) {
                        ResourceLocation rl = avatarTextureCache.remove(k);
                        if (rl != null) {
                            try { Minecraft.getInstance().getTextureManager().release(rl); } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}

            avatarCellsCache.keySet().removeIf(k -> k.startsWith(prefix));
        }
    }

    private ResourceLocation getOrCreateAvatarTexture(ResourceLocation skin, int size, boolean drawOverlay) {
        String key = (skin == null ? "default" : skin.toString()) + "#" + size + "#" + avatarResolution + "#" + (drawOverlay ? 1 : 0);
        if (avatarTextureCache.containsKey(key)) return avatarTextureCache.get(key);

        NativeImage srcImage = null;
        NativeImage dest = null;
        try {
            if (skin != null) {
                try {
                    java.util.Optional<Resource> opt = Minecraft.getInstance().getResourceManager().getResource(skin);
                    if (opt.isPresent()) {
                        Resource res = opt.get();
                        java.io.InputStream in = null;
                        try {
                            in = res.open();
                            srcImage = NativeImage.read(in);
                        } finally {
                            if (in != null) try { in.close(); } catch (Exception ignored) {}
                        }
                    }
                } catch (IOException ignored) {
                    srcImage = null;
                }
            }

            dest = new NativeImage(size, size, true);
            int radius = size / 2;
            float center = radius - 0.5f;

            for (int yy = 0; yy < size; yy++) for (int xx = 0; xx < size; xx++) dest.setPixelRGBA(xx, yy, 0);

            if (srcImage == null) {
                int fillCol = 0xFFCCCCCC;
                for (int py = 0; py < size; py++) for (int px = 0; px < size; px++) {
                    float dx = px - center; float dy = py - center;
                    if (dx * dx + dy * dy <= radius * radius) dest.setPixelRGBA(px, py, fillCol);
                }
            } else {
                int srcW = srcImage.getWidth();
                int srcH = srcImage.getHeight();
                float sx = srcW / 64f; float sy = srcH / 64f;

                for (int py = 0; py < size; py++) {
                    for (int px = 0; px < size; px++) {
                        float dx = px - center; float dy = py - center;
                        if (dx * dx + dy * dy <= radius * radius) {
                            float u = 8f + (px / (float) size) * 8f;
                            float v = 8f + (py / (float) size) * 8f;
                            int ix = Math.min(srcW - 1, Math.max(0, (int) (u * sx)));
                            int iy = Math.min(srcH - 1, Math.max(0, (int) (v * sy)));
                            int base = srcImage.getPixelRGBA(ix, iy);
                            int outCol = (0xFF << 24) | (base & 0x00FFFFFF);

                            if (drawOverlay) {
                                float ou = 40f + (px / (float) size) * 8f;
                                float ov = 8f + (py / (float) size) * 8f;
                                int oix = Math.min(srcW - 1, Math.max(0, (int) (ou * sx)));
                                int oiy = Math.min(srcH - 1, Math.max(0, (int) (ov * sy)));
                                int overlay = srcImage.getPixelRGBA(oix, oiy);
                                int oa = (overlay >> 24) & 0xFF;
                                if (oa > 0) {
                                    int br = (base >> 16) & 0xFF; int bg = (base >> 8) & 0xFF; int bb = base & 0xFF;
                                    int or = (overlay >> 16) & 0xFF; int og = (overlay >> 8) & 0xFF; int ob = overlay & 0xFF;
                                    float af = oa / 255f;
                                    int rr = (int) (or * af + br * (1f - af));
                                    int gg = (int) (og * af + bg * (1f - af));
                                    int bb2 = (int) (ob * af + bb * (1f - af));
                                    outCol = (0xFF << 24) | (rr << 16) | (gg << 8) | (bb2);
                                }
                            }

                            dest.setPixelRGBA(px, py, outCol);
                        } else {
                            dest.setPixelRGBA(px, py, 0);
                        }
                    }
                }
            }

            DynamicTexture dyn = new DynamicTexture(dest);
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "avatar/" + Math.abs(key.hashCode()));
            Minecraft.getInstance().getTextureManager().register(rl, dyn);
            avatarTextureCache.put(key, rl);
            dest = null;
            return rl;
        } catch (Exception ignored) {
            if (dest != null) try { dest.close(); } catch (Exception e) {}
            return null;
        } finally {
            if (srcImage != null) try { srcImage.close(); } catch (Exception ignored) {}
        }
    }

    private void ensureAvatarTextureAsync(final ResourceLocation skin, final int size) {
        final String key = (skin == null ? "default" : skin.toString()) + "#" + size + "#" + avatarResolution + "#1";
        synchronized (bakingTextures) {
            if (avatarTextureCache.containsKey(key) || bakingTextures.contains(key)) return;
            bakingTextures.add(key);
        }

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            NativeImage srcImage = null;
            NativeImage dest = null;
            try {
                if (skin != null) {
                    try {
                        java.util.Optional<Resource> opt = Minecraft.getInstance().getResourceManager().getResource(skin);
                        if (opt.isPresent()) {
                            try (java.io.InputStream in = opt.get().open()) {
                                srcImage = NativeImage.read(in);
                            }
                        }
                    } catch (IOException ignored) { srcImage = null; }
                }

                dest = new NativeImage(size, size, true);
                int radius = size / 2;
                float center = radius - 0.5f;
                for (int yy = 0; yy < size; yy++) for (int xx = 0; xx < size; xx++) dest.setPixelRGBA(xx, yy, 0);

                if (srcImage == null) {
                    int fillCol = 0xFFCCCCCC;
                    for (int py = 0; py < size; py++) for (int px = 0; px < size; px++) {
                        float dx = px - center; float dy = py - center;
                        if (dx * dx + dy * dy <= radius * radius) dest.setPixelRGBA(px, py, fillCol);
                    }
                } else {
                    int srcW = srcImage.getWidth();
                    int srcH = srcImage.getHeight();
                    float sx = srcW / 64f; float sy = srcH / 64f;
                    for (int py = 0; py < size; py++) {
                        for (int px = 0; px < size; px++) {
                            float dx = px - center; float dy = py - center;
                            if (dx * dx + dy * dy <= radius * radius) {
                                float u = 8f + (px / (float) size) * 8f;
                                float v = 8f + (py / (float) size) * 8f;
                                int ix = Math.min(srcW - 1, Math.max(0, (int) (u * sx)));
                                int iy = Math.min(srcH - 1, Math.max(0, (int) (v * sy)));
                                int base = srcImage.getPixelRGBA(ix, iy);
                                int outCol = (0xFF << 24) | (base & 0x00FFFFFF);

                                float ou = 40f + (px / (float) size) * 8f;
                                float ov = 8f + (py / (float) size) * 8f;
                                int oix = Math.min(srcW - 1, Math.max(0, (int) (ou * sx)));
                                int oiy = Math.min(srcH - 1, Math.max(0, (int) (ov * sy)));
                                int overlay = srcImage.getPixelRGBA(oix, oiy);
                                int oa = (overlay >> 24) & 0xFF;
                                if (oa > 0) {
                                    int br = (base >> 16) & 0xFF; int bg = (base >> 8) & 0xFF; int bb = base & 0xFF;
                                    int or = (overlay >> 16) & 0xFF; int og = (overlay >> 8) & 0xFF; int ob = overlay & 0xFF;
                                    float af = oa / 255f;
                                    int rr = (int) (or * af + br * (1f - af));
                                    int gg = (int) (og * af + bg * (1f - af));
                                    int bb2 = (int) (ob * af + bb * (1f - af));
                                    outCol = (0xFF << 24) | (rr << 16) | (gg << 8) | (bb2);
                                }

                                dest.setPixelRGBA(px, py, outCol);
                            } else {
                                dest.setPixelRGBA(px, py, 0);
                            }
                        }
                    }
                }

                final NativeImage toRegister = dest;
                dest = null;
                Minecraft.getInstance().execute(() -> {
                    try {
                        DynamicTexture dyn = new DynamicTexture(toRegister);
                        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "avatar/" + Math.abs(key.hashCode()));
                        Minecraft.getInstance().getTextureManager().register(rl, dyn);
                        avatarTextureCache.put(key, rl);
                    } catch (Exception ignored) {
                        if (toRegister != null) try { toRegister.close(); } catch (Exception e) {}
                    } finally {
                        synchronized (bakingTextures) { bakingTextures.remove(key); }
                    }
                });
            } catch (Exception e) {
                synchronized (bakingTextures) { bakingTextures.remove(key); }
                if (dest != null) try { dest.close(); } catch (Exception ignored) {}
            } finally {
                if (srcImage != null) try { srcImage.close(); } catch (Exception ignored) {}
            }
        });
    }

    @Override
    public void removed() {
        super.removed();
        try {
            for (ResourceLocation rl : avatarTextureCache.values()) {
                if (rl != null) {
                    try { Minecraft.getInstance().getTextureManager().release(rl); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        avatarTextureCache.clear();
        avatarCellsCache.clear();
    }

    public static ResourceLocation getAvatarTexture(UUID playerId, int size) {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation skin = null;

        try {
            Player player = mc.level != null ? mc.level.getPlayerByUUID(playerId) : null;
            if (player != null) {
                if (player instanceof AbstractClientPlayer) {
                    skin = ((AbstractClientPlayer) player).getSkinTextureLocation();
                }
            } else if (mc.player != null && mc.player.getUUID().equals(playerId)) {
                if (mc.player instanceof AbstractClientPlayer) {
                    skin = ((AbstractClientPlayer) mc.player).getSkinTextureLocation();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (skin != null) {
            RiftLoginScreen tempScreen = new RiftLoginScreen(null);
            return tempScreen.getOrCreateAvatarTexture(skin, size, true);
        }

        return getDefaultAvatar(size);
    }

    public static ResourceLocation getDefaultAvatar(int size) {
        RiftLoginScreen tempScreen = new RiftLoginScreen(null);
        return tempScreen.getOrCreateAvatarTexture(null, size, false);
    }

    private String clipText(Font font, String text, int maxWidth) {
        if (text == null) return "";
        if (maxWidth <= 0) return "";
        int w = font.width(text);
        if (w <= maxWidth) return text;
        String ell = "…";
        int ellW = font.width(ell);
        if (ellW > maxWidth) return "";
        int len = text.length();
        int lo = 0, hi = len;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            String sub = text.substring(0, mid);
            if (font.width(sub) + ellW <= maxWidth) lo = mid; else hi = mid - 1;
        }
        if (lo <= 0) return ell;
        return text.substring(0, lo) + ell;
    }

}
