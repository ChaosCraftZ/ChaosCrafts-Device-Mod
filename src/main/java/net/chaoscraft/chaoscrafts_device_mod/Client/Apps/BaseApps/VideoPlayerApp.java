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
import net.chaoscraft.chaoscrafts_device_mod.Core.Util.FileSystem.FilesManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class VideoPlayerApp implements IApp {
    private final AsyncRuntime asyncRuntime = AsyncRuntime.get();

    // Player state
    private final AtomicReference<String> currentVideo = new AtomicReference<>(null);
    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private final AtomicInteger currentTime = new AtomicInteger(0);
    private final AtomicInteger totalTime = new AtomicInteger(0);
    private final AtomicInteger volume = new AtomicInteger(100);

    // UI elements
    private EditBox filePathInput;
    private boolean inputFocused = false;
    private List<String> videoLibrary = new ArrayList<>();
    private boolean libraryLoaded = false;

    @Override
    public void onOpen(DraggableWindow window) {
        this.filePathInput = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 16, Component.literal("File path"));
        this.filePathInput.setMaxLength(500);
        this.filePathInput.setFocused(false);

        // Load video library asynchronously
        loadVideoLibrary();
    }

    private void loadVideoLibrary() {
        asyncRuntime.submitIo(() -> {
            List<String> collected = new ArrayList<>();
            File videoDir = new File(FilesManager.getPlayerDataDir(), "videos");
            videoDir.mkdirs();

            File[] videoFiles = videoDir.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".mp4") ||
                            name.toLowerCase().endsWith(".avi") ||
                            name.toLowerCase().endsWith(".mov"));

            if (videoFiles != null) {
                for (File file : videoFiles) {
                    collected.add(file.getName());
                }
            }

            asyncRuntime.runOnClientThread(() -> {
                videoLibrary.clear();
                videoLibrary.addAll(collected);
                libraryLoaded = true;
            });
        });
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16;

        // Header
        guiGraphics.fill(cx, cy, cx + cw, cy + 30, 0xFF2B2B2B);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Video Player"), cx + 10, cy + 8, 0xFFFFFFFF, false);

        // File path input
        guiGraphics.fill(cx, cy + 40, cx + cw, cy + 70, 0xFF1E1E1E);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Video File:"), cx + 6, cy + 48, 0xFFFFFFFF, false);

        filePathInput.setX(cx + 80);
        filePathInput.setY(cy + 46);
        filePathInput.setWidth(cw - 170);
        filePathInput.render(guiGraphics, mouseRelX, mouseRelY, partialTick);

        // Load button
        guiGraphics.fill(cx + cw - 80, cy + 46, cx + cw - 10, cy + 62, 0xFF4C7BD1);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Load"), cx + cw - 70, cy + 48, 0xFFFFFFFF, false);

        // Video player area
        int playerY = cy + 90;
        int playerHeight = Math.min(240, (r[3] - 40 - 90) / 2);
        int playerWidth = cw;

        if (currentVideo.get() != null) {
            // Draw video player background
            guiGraphics.fill(cx, playerY, cx + playerWidth, playerY + playerHeight, 0xFF000000);

            // Draw video placeholder
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Video Playing: " + currentVideo.get()), cx + 10, playerY + 10, 0xFFFFFFFF, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Time: " + formatTime(currentTime.get()) + " / " + formatTime(totalTime.get())), cx + 10, playerY + 25, 0xFFFFFFFF, false);

            // Simulate video playback with moving bars
            if (isPlaying.get()) {
                for (int i = 0; i < 10; i++) {
                    int barWidth = 20;
                    int barHeight = 5;
                    int barX = cx + (int)((System.currentTimeMillis() / 50 + i * 30) % (playerWidth - barWidth));
                    int barY = playerY + playerHeight / 2 - barHeight / 2 + i * 10 - 50;
                    guiGraphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFFFF0000);
                }
            }

            // Playback controls
            int controlY = playerY + playerHeight - 30;

            // Play/Pause button
            if (isPlaying.get()) {
                guiGraphics.fill(cx + 10, controlY, cx + 50, controlY + 20, 0xFF555555);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Pause"), cx + 15, controlY + 6, 0xFFFFFFFF, false);
            } else {
                guiGraphics.fill(cx + 10, controlY, cx + 50, controlY + 20, 0xFF555555);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Play"), cx + 20, controlY + 6, 0xFFFFFFFF, false);
            }

            // Stop button
            guiGraphics.fill(cx + 60, controlY, cx + 100, controlY + 20, 0xFF555555);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Stop"), cx + 70, controlY + 6, 0xFFFFFFFF, false);

            // Progress bar
            int progressWidth = (int)((playerWidth - 20) * (currentTime.get() / (float) totalTime.get()));
            guiGraphics.fill(cx + 10, controlY - 15, cx + 10 + progressWidth, controlY - 5, 0xFF4C7BD1);
            guiGraphics.fill(cx + 10, controlY - 15, cx + playerWidth - 10, controlY - 5, 0xFF555555);

            // Volume control
            guiGraphics.fill(cx + playerWidth - 100, controlY, cx + playerWidth - 10, controlY + 20, 0xFF555555);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Vol: " + volume.get()), cx + playerWidth - 95, controlY + 6, 0xFFFFFFFF, false);
        } else {
            // Welcome message
            guiGraphics.fill(cx, playerY, cx + cw, playerY + playerHeight, 0xFF1E1E1E);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Video Player"), cx + 10, playerY + 20, 0xFFFFFFFF, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Enter a video file path or browse your library"), cx + 10, playerY + 40, 0xFFCCCCCC, false);

            // Video library
            if (libraryLoaded) {
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Your Videos:"), cx + 10, playerY + 70, 0xFFFFFFFF, false);
                int libY = playerY + 90;
                for (int i = 0; i < Math.min(videoLibrary.size(), 5); i++) {
                    String video = videoLibrary.get(i);
                    guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(video), cx + 20, libY, 0xFFCCCCCC, false);
                    libY += 12;
                }
            } else {
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Loading video library..."), cx + 10, playerY + 70, 0xFFCCCCCC, false);
            }
        }
    }

    private String formatTime(int seconds) {
        int mins = seconds / 60;
        int secs = seconds % 60;
        return String.format("%d:%02d", mins, secs);
    }

    @Override
    public boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16;

        // Load button
        if (mouseRelX >= cx + cw - 80 && mouseRelX <= cx + cw - 10 &&
                mouseRelY >= cy + 46 && mouseRelY <= cy + 62) {
            loadVideo(filePathInput.getValue());
            return true;
        }

        // Check if clicking on the input box
        if (mouseRelX >= cx + 80 && mouseRelX <= cx + cw - 170 &&
                mouseRelY >= cy + 46 && mouseRelY <= cy + 62) {
            filePathInput.setFocused(true);
            inputFocused = true;
            return true;
        }

        if (currentVideo.get() != null) {
            int playerY = cy + 90;
            int playerHeight = Math.min(240, (r[3] - 40 - 90) / 2);
            int controlY = playerY + playerHeight - 30;

            // Play/Pause button
            if (mouseRelX >= cx + 10 && mouseRelX <= cx + 50 &&
                    mouseRelY >= controlY && mouseRelY <= controlY + 20) {
                isPlaying.set(!isPlaying.get());
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                return true;
            }

            // Stop button
            if (mouseRelX >= cx + 60 && mouseRelX <= cx + 100 &&
                    mouseRelY >= controlY && mouseRelY <= controlY + 20) {
                isPlaying.set(false);
                currentTime.set(0);
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                return true;
            }

            // Progress bar seek
            if (mouseRelY >= controlY - 15 && mouseRelY <= controlY - 5 &&
                    mouseRelX >= cx + 10 && mouseRelX <= cx + cw - 10) {
                float progress = (float)(mouseRelX - cx - 10) / (cw - 20);
                currentTime.set((int)(totalTime.get() * progress));
                return true;
            }

            // Volume control
            if (mouseRelX >= cx + cw - 100 && mouseRelX <= cx + cw - 10 &&
                    mouseRelY >= controlY && mouseRelY <= controlY + 20) {
                // Toggle volume between 0, 50, 100
                int newVolume = volume.get() == 100 ? 50 : volume.get() == 50 ? 0 : 100;
                volume.set(newVolume);
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                return true;
            }
        }

        // If clicking elsewhere, remove focus from input
        filePathInput.setFocused(false);
        inputFocused = false;

        return false;
    }

    private void loadVideo(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return;
        }

        // Check if file exists in video library
        File videoDir = new File(FilesManager.getPlayerDataDir(), "videos");
        File videoFile = new File(videoDir, filePath);

        if (!videoFile.exists()) {
            // Check if it's a full path
            videoFile = new File(filePath);
            if (!videoFile.exists()) {
                return;
            }
        }

        currentVideo.set(videoFile.getName());
        isPlaying.set(true);
        currentTime.set(0);
        totalTime.set(300); // 5 minutes for demo purposes

        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    @Override
    public void tick() {
        if (isPlaying.get() && currentVideo.get() != null) {
            // Update playback time
            if (currentTime.get() < totalTime.get()) {
                currentTime.set(currentTime.get() + 1);
            } else {
                isPlaying.set(false);
            }
        }
    }

    @Override
    public void mouseReleased(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        if (filePathInput != null) filePathInput.mouseReleased(mouseRelX, mouseRelY, button);
    }

    @Override
    public boolean mouseDragged(DraggableWindow window, double mouseRelX, double mouseRelY, double dx, double dy) {
        return false;
    }

    @Override
    public boolean charTyped(DraggableWindow window, char codePoint, int modifiers) {
        if (filePathInput != null && inputFocused) {
            return filePathInput.charTyped(codePoint, modifiers);
        }
        return false;
    }

    @Override
    public boolean keyPressed(DraggableWindow window, int keyCode, int scanCode, int modifiers) {
        if (filePathInput != null && inputFocused) {
            if (keyCode == 257) { // Enter key
                loadVideo(filePathInput.getValue());
                return true;
            }
            return filePathInput.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    @Override
    public boolean onClose(DraggableWindow window) {
        isPlaying.set(false);
        return false;
    }
}