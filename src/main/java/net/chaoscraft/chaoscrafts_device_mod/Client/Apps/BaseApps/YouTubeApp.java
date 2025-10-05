package net.chaoscraft.chaoscrafts_device_mod.Client.Apps.BaseApps;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager.IApp;
import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DraggableWindow;
import net.chaoscraft.chaoscrafts_device_mod.Core.AsyncorAPI.AsyncRuntime;

import java.net.URI;
import net.minecraft.client.gui.Font;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicReference;

/**
 * YouTube app with basic video playback capability
 */
public class YouTubeApp implements IApp {
    private Font font;
    private EditBox urlInput;
    private final AtomicReference<String> statusMessage = new AtomicReference<>("Enter a YouTube URL or video ID");
    private boolean inputFocused = false;
    private final AsyncRuntime asyncRuntime = AsyncRuntime.get();

    @Override
    public void onOpen(DraggableWindow window) {
        this.font = Minecraft.getInstance().font;
        this.urlInput = new EditBox(this.font, 0, 0, 100, 16, Component.literal("URL"));
        this.urlInput.setMaxLength(500);
        this.urlInput.setFocused(false);
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16;

        // Header
        guiGraphics.fill(cx, cy, cx + cw, cy + 30, 0xFF2B2B2B);
    guiGraphics.drawString(this.font != null ? this.font : Minecraft.getInstance().font, Component.literal("YouTube Player"), cx + 10, cy + 8, 0xFFFFFFFF, false);

        // URL input box
        guiGraphics.fill(cx, cy + 40, cx + cw, cy + 70, 0xFF1E1E1E);
    guiGraphics.drawString(this.font != null ? this.font : Minecraft.getInstance().font, Component.literal("Video URL:"), cx + 6, cy + 48, 0xFFFFFFFF, false);

        // Position and render the input box
        urlInput.setX(cx + 80);
        urlInput.setY(cy + 46);
        urlInput.setWidth(cw - 170);
        urlInput.render(guiGraphics, mouseRelX, mouseRelY, partialTick);

        // Load button
        guiGraphics.fill(cx + cw - 80, cy + 46, cx + cw - 10, cy + 62, 0xFFCC0000);
    guiGraphics.drawString(this.font != null ? this.font : Minecraft.getInstance().font, Component.literal("Load"), cx + cw - 70, cy + 48, 0xFFFFFFFF, false);

        // Status message
    guiGraphics.drawString(this.font != null ? this.font : Minecraft.getInstance().font, Component.literal(statusMessage.get()), cx + 10, cy + 72, 0xFFCCCCCC, false);

        // Info
        guiGraphics.drawString(this.font != null ? this.font : Minecraft.getInstance().font, Component.literal("YouTube Video Player"), cx + 10, cy + 90, 0xFFFFFFFF, false);
        guiGraphics.drawString(this.font != null ? this.font : Minecraft.getInstance().font, Component.literal("Videos will open in your default web browser"), cx + 10, cy + 110, 0xFFCCCCCC, false);
        guiGraphics.drawString(this.font != null ? this.font : Minecraft.getInstance().font, Component.literal("(Minecraft cannot play videos internally)"), cx + 10, cy + 120, 0xFF888888, false);

        // Supported URL formats
        guiGraphics.drawString(this.font != null ? this.font : Minecraft.getInstance().font, Component.literal("Supported formats:"), cx + 10, cy + 130, 0xFFFFFFFF, false);
        guiGraphics.drawString(this.font != null ? this.font : Minecraft.getInstance().font, Component.literal("- https://www.youtube.com/watch?v=VIDEO_ID"), cx + 20, cy + 150, 0xFFCCCCCC, false);
        guiGraphics.drawString(this.font != null ? this.font : Minecraft.getInstance().font, Component.literal("- https://youtu.be/VIDEO_ID"), cx + 20, cy + 170, 0xFFCCCCCC, false);
        guiGraphics.drawString(this.font != null ? this.font : Minecraft.getInstance().font, Component.literal("- Just the VIDEO_ID"), cx + 20, cy + 190, 0xFFCCCCCC, false);
    }

    @Override
    public boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16;

        // Load button
        if (mouseRelX >= cx + cw - 80 && mouseRelX <= cx + cw - 10 &&
                mouseRelY >= cy + 46 && mouseRelY <= cy + 62) {
            loadVideo(urlInput.getValue());
            return true;
        }

        // Check if clicking on the input box
        if (mouseRelX >= cx + 80 && mouseRelX <= cx + cw - 170 &&
                mouseRelY >= cy + 46 && mouseRelY <= cy + 62) {
            urlInput.setFocused(true);
            inputFocused = true;
            return true;
        }

        // If clicking elsewhere, remove focus from input
        urlInput.setFocused(false);
        inputFocused = false;

        return false;
    }

    private void loadVideo(String input) {
        if (input == null || input.trim().isEmpty()) {
            statusMessage.set("Please enter a YouTube URL or video ID");
            return;
        }

        // Extract video ID from various URL formats
        String videoId = extractVideoId(input);

        if (videoId != null) {
            String url = "https://www.youtube.com/watch?v=" + videoId;
            openInBrowser(url);
            statusMessage.set("Opening video in browser: " + videoId);
        } else {
            statusMessage.set("Invalid YouTube URL or video ID");
        }
    }

    private String extractVideoId(String input) {
        // Remove any extra spaces
        input = input.trim();

        // Pattern for standard YouTube URL
        Pattern pattern = Pattern.compile("^(https?://)?(www\\.)?(youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/)([a-zA-Z0-9_-]{11})");
        Matcher matcher = pattern.matcher(input);

        if (matcher.find()) {
            return matcher.group(4);
        }

        // Pattern for just the video ID (11 characters)
        if (input.matches("[a-zA-Z0-9_-]{11}")) {
            return input;
        }

        // Pattern for YouTube share URL
        pattern = Pattern.compile("youtube\\.com/watch\\?v=([a-zA-Z0-9_-]{11})");
        matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Pattern for youtu.be URL
        pattern = Pattern.compile("youtu\\.be/([a-zA-Z0-9_-]{11})");
        matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Pattern for embed URL
        pattern = Pattern.compile("youtube\\.com/embed/([a-zA-Z0-9_-]{11})");
        matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    private void openInBrowser(String url) {
        asyncRuntime.submitCompute(() -> {
            try {
                if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                    java.awt.Desktop.getDesktop().browse(new URI(url));
                    asyncRuntime.runOnClientThread(() -> {
                        statusMessage.set("Opening in browser: " + url);
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                    });
                } else {
                    asyncRuntime.runOnClientThread(() -> statusMessage.set("Could not open browser (desktop not supported)"));
                }
            } catch (Exception e) {
                asyncRuntime.runOnClientThread(() -> statusMessage.set("Could not open browser: " + e.getMessage()));
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
            if (keyCode == 257) { // Enter key
                loadVideo(urlInput.getValue());
                return true;
            }
            return urlInput.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    @Override
    public boolean onClose(DraggableWindow window) {
        // Clean up resources
        return true;
    }
}