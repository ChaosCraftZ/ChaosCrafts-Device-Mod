package net.chaoscraft.chaoscrafts_device_mod.client.app;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DraggableWindow;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicReference;

// currently useless
public class YouTubeApp implements IApp {
    private DraggableWindow window;
    private EditBox urlInput;
    private final AtomicReference<String> statusMessage = new AtomicReference<>("Enter a YouTube URL or video ID");
    private final AtomicReference<String> currentVideoId = new AtomicReference<>(null);
    private boolean inputFocused = false;
    private BufferedImage currentFrame;
    private long lastFrameUpdate;
    private int frameCounter = 0;
    private final AtomicReference<Boolean> isPlaying = new AtomicReference<>(false);
    private final AsyncTaskManager asyncManager = AsyncTaskManager.getInstance();

    @Override
    public void onOpen(DraggableWindow window) {
        this.window = window;
        this.urlInput = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 16, Component.literal("URL"));
        this.urlInput.setMaxLength(500);
        this.urlInput.setFocused(false);
        this.currentFrame = new BufferedImage(320, 240, BufferedImage.TYPE_INT_ARGB);

        asyncManager.scheduleTask(this::frameUpdateLoop, 100, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private void frameUpdateLoop() {
        if (isPlaying.get() && currentVideoId.get() != null) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastFrameUpdate > 100) {
                lastFrameUpdate = currentTime;
                frameCounter++;

                if (frameCounter % 30 == 0 && currentVideoId.get() != null) {
                    loadYouTubeThumbnail(currentVideoId.get());
                }
            }
        }

        if (window != null) {
            asyncManager.scheduleTask(this::frameUpdateLoop, 100, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16;

        guiGraphics.fill(cx, cy, cx + cw, cy + 30, 0xFF2B2B2B);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("YouTube Player"), cx + 10, cy + 8, 0xFFFFFFFF, false);

        guiGraphics.fill(cx, cy + 40, cx + cw, cy + 70, 0xFF1E1E1E);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Video URL:"), cx + 6, cy + 48, 0xFFFFFFFF, false);

        urlInput.setX(cx + 80);
        urlInput.setY(cy + 46);
        urlInput.setWidth(cw - 170);
        urlInput.render(guiGraphics, mouseRelX, mouseRelY, partialTick);

        guiGraphics.fill(cx + cw - 80, cy + 46, cx + cw - 10, cy + 62, 0xFFCC0000);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Load"), cx + cw - 70, cy + 48, 0xFFFFFFFF, false);

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(statusMessage.get()), cx + 10, cy + 72, 0xFFCCCCCC, false);

        int playerY = cy + 90;
        int playerHeight = Math.min(240, (r[3] - 40 - 90) / 2);
        int playerWidth = cw;

        if (currentVideoId.get() != null) {
            guiGraphics.fill(cx, playerY, cx + playerWidth, playerY + playerHeight, 0xFF000000);

            if (isPlaying.get()) {
                int blockSize = 10;
                for (int y = 0; y < playerHeight; y += blockSize) {
                    for (int x = 0; x < playerWidth; x += blockSize) {
                        int colorIndex = ((x / blockSize) + (y / blockSize) + frameCounter) % 3;
                        int color;

                        switch (colorIndex) {
                            case 0: color = 0xFFFF0000; break;
                            case 1: color = 0xFF00FF00; break;
                            case 2: color = 0xFF0000FF; break;
                            default: color = 0xFFFFFFFF; break;
                        }

                        guiGraphics.fill(cx + x, playerY + y, cx + x + blockSize, playerY + y + blockSize, color);
                    }
                }
            }

            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Now Playing: " + currentVideoId.get()), cx + 10, playerY + 5, 0xFFFFFFFF, false);

            int controlY = playerY + playerHeight - 30;

            if (isPlaying.get()) {
                guiGraphics.fill(cx + 10, controlY, cx + 50, controlY + 20, 0xFF555555);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Pause"), cx + 15, controlY + 6, 0xFFFFFFFF, false);
            } else {
                guiGraphics.fill(cx + 10, controlY, cx + 50, controlY + 20, 0xFF555555);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Play"), cx + 20, controlY + 6, 0xFFFFFFFF, false);
            }

            guiGraphics.fill(cx + 60, controlY, cx + 100, controlY + 20, 0xFF555555);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Stop"), cx + 70, controlY + 6, 0xFFFFFFFF, false);

            guiGraphics.fill(cx + cw - 110, controlY, cx + cw - 10, controlY + 20, 0xFFCC0000);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Open in Browser"), cx + cw - 105, controlY + 6, 0xFFFFFFFF, false);
        } else {
            guiGraphics.fill(cx, playerY, cx + cw, playerY + playerHeight, 0xFF1E1E1E);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("YouTube Video Player"), cx + 10, playerY + 20, 0xFFFFFFFF, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Paste any YouTube URL above to play a video"), cx + 10, playerY + 40, 0xFFCCCCCC, false);

            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Supported formats:"), cx + 10, playerY + 70, 0xFFFFFFFF, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("- https://www.youtube.com/watch?v=VIDEO_ID"), cx + 20, playerY + 90, 0xFFCCCCCC, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("- https://youtu.be/VIDEO_ID"), cx + 20, playerY + 110, 0xFFCCCCCC, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("- Just the VIDEO_ID"), cx + 20, playerY + 130, 0xFFCCCCCC, false);
        }
    }

    private void loadYouTubeThumbnail(String videoId) {
        asyncManager.submitIOTask(() -> {
            try {
                URL url = new URL("https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg");
                BufferedImage thumbnail = ImageIO.read(url);
                if (thumbnail != null) {
                    statusMessage.set("Loaded thumbnail for: " + videoId);
                }
            } catch (IOException e) {
            }
        });
    }

    @Override
    public boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16;

        if (mouseRelX >= cx + cw - 80 && mouseRelX <= cx + cw - 10 &&
                mouseRelY >= cy + 46 && mouseRelY <= cy + 62) {
            loadVideo(urlInput.getValue());
            return true;
        }

        if (mouseRelX >= cx + 80 && mouseRelX <= cx + cw - 170 &&
                mouseRelY >= cy + 46 && mouseRelY <= cy + 62) {
            urlInput.setFocused(true);
            inputFocused = true;
            return true;
        }

        if (currentVideoId.get() != null) {
            int playerY = cy + 90;
            int playerHeight = Math.min(240, (r[3] - 40 - 90) / 2);
            int controlY = playerY + playerHeight - 30;

            if (mouseRelX >= cx + 10 && mouseRelX <= cx + 50 &&
                    mouseRelY >= controlY && mouseRelY <= controlY + 20) {
                isPlaying.set(!isPlaying.get());
                statusMessage.set(isPlaying.get() ? "Playing video" : "Video paused");
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                return true;
            }

            if (mouseRelX >= cx + 60 && mouseRelX <= cx + 100 &&
                    mouseRelY >= controlY && mouseRelY <= controlY + 20) {
                isPlaying.set(false);
                currentVideoId.set(null);
                statusMessage.set("Video stopped");
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                return true;
            }

            if (mouseRelX >= cx + cw - 110 && mouseRelX <= cx + cw - 10 &&
                    mouseRelY >= controlY && mouseRelY <= controlY + 20) {
                openInBrowser("https://www.youtube.com/watch?v=" + currentVideoId.get());
                return true;
            }
        }

        urlInput.setFocused(false);
        inputFocused = false;

        return false;
    }

    private void loadVideo(String input) {
        if (input == null || input.trim().isEmpty()) {
            statusMessage.set("Please enter a YouTube URL or video ID");
            return;
        }

        String videoId = extractVideoId(input);

        if (videoId != null) {
            currentVideoId.set(videoId);
            isPlaying.set(true);
            statusMessage.set("Playing video: " + videoId);
            frameCounter = 0;

            loadYouTubeThumbnail(videoId);

            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        } else {
            statusMessage.set("Invalid YouTube URL or video ID");
        }
    }

    private String extractVideoId(String input) {
        input = input.trim();

        Pattern pattern = Pattern.compile("^(https?://)?(www\\.)?(youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/)([a-zA-Z0-9_-]{11})");
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            return matcher.group(4);
        }

        if (input.matches("[a-zA-Z0-9_-]{11}")) {
            return input;
        }

        pattern = Pattern.compile("youtube\\.com/watch\\?v=([a-zA-Z0-9_-]{11})");
        matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }

        pattern = Pattern.compile("youtu\\.be/([a-zA-Z0-9_-]{11})");
        matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }

        pattern = Pattern.compile("youtube\\.com/embed/([a-zA-Z0-9_-]{11})");
        matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private void openInBrowser(String url) {
        asyncManager.submitIOTask(() -> {
            try {
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                    java.awt.Desktop.getDesktop().browse(new URI(url));
                    statusMessage.set("Opening in browser: " + url);
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                } else {
                    statusMessage.set("Could not open browser (desktop not supported)");
                }
            } catch (Exception e) {
                statusMessage.set("Could not open browser: " + e.getMessage());
            }
        });
    }

    @Override
    public void mouseReleased(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        if (urlInput != null) urlInput.mouseReleased(mouseRelX, mouseRelY, button);
    }

    @Override
    public boolean mouseDragged(DraggableWindow window, double mouseRelX, double mouseRelY, double dx, double dy) {
        return false;
    }

    @Override
    public boolean charTyped(DraggableWindow window, char codePoint, int modifiers) {
        if (urlInput != null && inputFocused) {
            return urlInput.charTyped(codePoint, modifiers);
        }
        return false;
    }

    @Override
    public boolean keyPressed(DraggableWindow window, int keyCode, int scanCode, int modifiers) {
        if (urlInput != null && inputFocused) {
            if (keyCode == 257) {
                loadVideo(urlInput.getValue());
                return true;
            }
            return urlInput.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    @Override
    public boolean onClose(DraggableWindow window) {
        currentFrame = null;
        this.window = null;
        return true;
    }
}