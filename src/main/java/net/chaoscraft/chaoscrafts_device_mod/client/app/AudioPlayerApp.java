package net.chaoscraft.chaoscrafts_device_mod.client.app;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;
import net.chaoscraft.chaoscrafts_device_mod.client.fs.FilesManager;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DraggableWindow;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

// for future use
public class AudioPlayerApp implements IApp {
    private DraggableWindow window;
    private final AsyncTaskManager asyncManager = AsyncTaskManager.getInstance();

    private final AtomicReference<String> currentAudio = new AtomicReference<>(null);
    private final AtomicBoolean isPlaying = new AtomicBoolean(false);
    private final AtomicInteger currentTime = new AtomicInteger(0);
    private final AtomicInteger totalTime = new AtomicInteger(0);
    private final AtomicInteger volume = new AtomicInteger(100);

    private final List<String> playlist = new ArrayList<>();
    private int currentTrackIndex = -1;
    private boolean shuffleEnabled = false;
    private boolean repeatEnabled = false;

    private EditBox filePathInput;
    private boolean inputFocused = false;
    private List<String> audioLibrary = new ArrayList<>();
    private boolean libraryLoaded = false;

    private final float[] visualizerBars = new float[32];
    private long lastVisualizerUpdate = 0;

    @Override
    public void onOpen(DraggableWindow window) {
        this.window = window;
        this.filePathInput = new EditBox(Minecraft.getInstance().font, 0, 0, 100, 16, Component.literal("File path"));
        this.filePathInput.setMaxLength(500);
        this.filePathInput.setFocused(false);

        for (int i = 0; i < visualizerBars.length; i++) {
            visualizerBars[i] = 5 + (float) Math.random() * 10;
        }

        loadAudioLibrary();
    }

    private void loadAudioLibrary() {
        asyncManager.submitIOTask(() -> {
            File audioDir = new File(FilesManager.getPlayerDataDir(), "music");
            audioDir.mkdirs();

            File[] audioFiles = audioDir.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".mp3") ||
                            name.toLowerCase().endsWith(".wav") ||
                            name.toLowerCase().endsWith(".ogg"));

            if (audioFiles != null) {
                for (File file : audioFiles) {
                    audioLibrary.add(file.getName());
                    playlist.add(file.getName());
                }
            }

            libraryLoaded = true;
        });
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16, ch = r[3] - 40;

        guiGraphics.fill(cx, cy, cx + cw, cy + 30, 0xFF2B2B2B);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Audio Player"), cx + 10, cy + 8, 0xFFFFFFFF, false);

        guiGraphics.fill(cx, cy + 40, cx + cw, cy + 70, 0xFF1E1E1E);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Audio File:"), cx + 6, cy + 48, 0xFFFFFFFF, false);

        filePathInput.setX(cx + 80);
        filePathInput.setY(cy + 46);
        filePathInput.setWidth(cw - 170);
        filePathInput.render(guiGraphics, mouseRelX, mouseRelY, partialTick);

        guiGraphics.fill(cx + cw - 80, cy + 46, cx + cw - 10, cy + 62, 0xFF4C7BD1);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Load"), cx + cw - 70, cy + 48, 0xFFFFFFFF, false);

        int playerY = cy + 90;
        int playerHeight = Math.min(180, (r[3] - 40 - 90) / 2);
        int playerWidth = cw;

        if (currentAudio.get() != null) {
            guiGraphics.fill(cx, playerY, cx + playerWidth, playerY + playerHeight, 0xFF1E1E1E);

            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Now Playing: " + currentAudio.get()), cx + 10, playerY + 10, 0xFFFFFFFF, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Time: " + formatTime(currentTime.get()) + " / " + formatTime(totalTime.get())), cx + 10, playerY + 25, 0xFFFFFFFF, false);

            updateVisualizer();
            int visualizerY = playerY + 45;
            int barWidth = (playerWidth - 20) / visualizerBars.length;
            for (int i = 0; i < visualizerBars.length; i++) {
                int barHeight = (int) visualizerBars[i];
                int barX = cx + 10 + i * barWidth;
                guiGraphics.fill(barX, visualizerY - barHeight, barX + barWidth - 2, visualizerY, 0xFF4C7BD1);
            }

            int controlY = playerY + playerHeight - 30;

            guiGraphics.fill(cx + 10, controlY, cx + 50, controlY + 20, 0xFF555555);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("◀◀"), cx + 20, controlY + 6, 0xFFFFFFFF, false);

            if (isPlaying.get()) {
                guiGraphics.fill(cx + 60, controlY, cx + 100, controlY + 20, 0xFF555555);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("❚❚"), cx + 75, controlY + 6, 0xFFFFFFFF, false);
            } else {
                guiGraphics.fill(cx + 60, controlY, cx + 100, controlY + 20, 0xFF555555);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("▶"), cx + 80, controlY + 6, 0xFFFFFFFF, false);
            }

            guiGraphics.fill(cx + 110, controlY, cx + 150, controlY + 20, 0xFF555555);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("■"), cx + 130, controlY + 6, 0xFFFFFFFF, false);

            guiGraphics.fill(cx + 160, controlY, cx + 200, controlY + 20, 0xFF555555);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("▶▶"), cx + 170, controlY + 6, 0xFFFFFFFF, false);

            int progressWidth = (int)((playerWidth - 20) * (currentTime.get() / (float) totalTime.get()));
            guiGraphics.fill(cx + 10, controlY - 15, cx + 10 + progressWidth, controlY - 5, 0xFF4C7BD1);
            guiGraphics.fill(cx + 10, controlY - 15, cx + playerWidth - 10, controlY - 5, 0xFF555555);

            guiGraphics.fill(cx + playerWidth - 100, controlY, cx + playerWidth - 10, controlY + 20, 0xFF555555);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Vol: " + volume.get()), cx + playerWidth - 95, controlY + 6, 0xFFFFFFFF, false);

            int shuffleColor = shuffleEnabled ? 0xFF4C7BD1 : 0xFF555555;
            guiGraphics.fill(cx + playerWidth - 220, controlY, cx + playerWidth - 190, controlY + 20, shuffleColor);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("⇄"), cx + playerWidth - 215, controlY + 6, 0xFFFFFFFF, false);

            int repeatColor = repeatEnabled ? 0xFF4C7BD1 : 0xFF555555;
            guiGraphics.fill(cx + playerWidth - 180, controlY, cx + playerWidth - 150, controlY + 20, repeatColor);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("↻"), cx + playerWidth - 175, controlY + 6, 0xFFFFFFFF, false);
        } else {
            guiGraphics.fill(cx, playerY, cx + cw, playerY + playerHeight, 0xFF1E1E1E);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Audio Player"), cx + 10, playerY + 20, 0xFFFFFFFF, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Enter an audio file path or browse your library"), cx + 10, playerY + 40, 0xFFCCCCCC, false);

            if (libraryLoaded) {
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Your Music:"), cx + 10, playerY + 70, 0xFFFFFFFF, false);
                int libY = playerY + 90;
                for (int i = 0; i < Math.min(audioLibrary.size(), 8); i++) {
                    String audio = audioLibrary.get(i);
                    boolean isCurrent = currentTrackIndex == i;

                    if (isCurrent) {
                        guiGraphics.fill(cx + 10, libY - 2, cx + cw - 10, libY + 10, 0x553333FF);
                    }

                    guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(audio), cx + 20, libY, isCurrent ? 0xFF88FF88 : 0xFFCCCCCC, false);
                    libY += 12;
                }
            } else {
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Loading audio library..."), cx + 10, playerY + 70, 0xFFCCCCCC, false);
            }
        }
    }

    private void updateVisualizer() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastVisualizerUpdate > 100) {
            lastVisualizerUpdate = currentTime;

            for (int i = 0; i < visualizerBars.length; i++) {
                if (isPlaying.get()) {
                    float baseHeight = 5 + (float) Math.sin(currentTime / 200.0 + i * 0.2) * 10;
                    float variation = (float) Math.random() * 5;
                    visualizerBars[i] = baseHeight + variation;
                } else {
                    visualizerBars[i] = Math.max(5, visualizerBars[i] * 0.9f);
                }
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

        if (mouseRelX >= cx + cw - 80 && mouseRelX <= cx + cw - 10 &&
                mouseRelY >= cy + 46 && mouseRelY <= cy + 62) {
            loadAudio(filePathInput.getValue());
            return true;
        }

        if (mouseRelX >= cx + 80 && mouseRelX <= cx + cw - 170 &&
                mouseRelY >= cy + 46 && mouseRelY <= cy + 62) {
            filePathInput.setFocused(true);
            inputFocused = true;
            return true;
        }

        if (currentAudio.get() != null) {
            int playerY = cy + 90;
            int playerHeight = Math.min(180, (r[3] - 40 - 90) / 2);
            int controlY = playerY + playerHeight - 30;

            if (mouseRelX >= cx + 10 && mouseRelX <= cx + 50 &&
                    mouseRelY >= controlY && mouseRelY <= controlY + 20) {
                playPreviousTrack();
                return true;
            }

            if (mouseRelX >= cx + 60 && mouseRelX <= cx + 100 &&
                    mouseRelY >= controlY && mouseRelY <= controlY + 20) {
                togglePlayPause();
                return true;
            }

            if (mouseRelX >= cx + 110 && mouseRelX <= cx + 150 &&
                    mouseRelY >= controlY && mouseRelY <= controlY + 20) {
                stopPlayback();
                return true;
            }

            if (mouseRelX >= cx + 160 && mouseRelX <= cx + 200 &&
                    mouseRelY >= controlY && mouseRelY <= controlY + 20) {
                playNextTrack();
                return true;
            }

            if (mouseRelY >= controlY - 15 && mouseRelY <= controlY - 5 &&
                    mouseRelX >= cx + 10 && mouseRelX <= cx + cw - 10) {
                float progress = (float)(mouseRelX - cx - 10) / (cw - 20);
                seekAudio(progress);
                return true;
            }

            if (mouseRelX >= cx + cw - 100 && mouseRelX <= cx + cw - 10 &&
                    mouseRelY >= controlY && mouseRelY <= controlY + 20) {
                int newVolume = (volume.get() + 25) % 125;
                if (newVolume > 100) newVolume = 0;
                volume.set(newVolume);
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, volume.get() / 100f));
                return true;
            }

            if (mouseRelX >= cx + cw - 220 && mouseRelX <= cx + cw - 190 &&
                    mouseRelY >= controlY && mouseRelY <= controlY + 20) {
                shuffleEnabled = !shuffleEnabled;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                return true;
            }

            if (mouseRelX >= cx + cw - 180 && mouseRelX <= cx + cw - 150 &&
                    mouseRelY >= controlY && mouseRelY <= controlY + 20) {
                repeatEnabled = !repeatEnabled;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                return true;
            }
        } else if (libraryLoaded) {
            int playerY = cy + 90;
            int libY = playerY + 90;
            for (int i = 0; i < Math.min(audioLibrary.size(), 8); i++) {
                if (mouseRelY >= libY && mouseRelY <= libY + 12 &&
                        mouseRelX >= cx + 20 && mouseRelX <= cx + cw - 10) {
                    loadAudio(audioLibrary.get(i));
                    currentTrackIndex = i;
                    return true;
                }
                libY += 12;
            }
        }

        filePathInput.setFocused(false);
        inputFocused = false;

        return false;
    }

    private void loadAudio(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return;
        }

        File audioDir = new File(FilesManager.getPlayerDataDir(), "music");
        File audioFile = new File(audioDir, filePath);

        if (!audioFile.exists()) {
            audioFile = new File(filePath);
            if (!audioFile.exists()) {
                return;
            }
        }

        currentAudio.set(audioFile.getName());
        isPlaying.set(true);
        currentTime.set(0);
        totalTime.set(180);

        currentTrackIndex = playlist.indexOf(audioFile.getName());
        if (currentTrackIndex == -1) {
            playlist.add(audioFile.getName());
            currentTrackIndex = playlist.size() - 1;
        }

        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    private void togglePlayPause() {
        isPlaying.set(!isPlaying.get());
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    private void stopPlayback() {
        isPlaying.set(false);
        currentTime.set(0);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    private void playNextTrack() {
        if (playlist.isEmpty()) return;

        if (shuffleEnabled) {
            currentTrackIndex = (int) (Math.random() * playlist.size());
        } else {
            currentTrackIndex = (currentTrackIndex + 1) % playlist.size();
        }

        loadAudio(playlist.get(currentTrackIndex));
    }

    private void playPreviousTrack() {
        if (playlist.isEmpty()) return;

        if (shuffleEnabled) {
            currentTrackIndex = (int) (Math.random() * playlist.size());
        } else {
            currentTrackIndex = (currentTrackIndex - 1 + playlist.size()) % playlist.size();
        }

        loadAudio(playlist.get(currentTrackIndex));
    }

    private void seekAudio(float progress) {
        currentTime.set((int)(totalTime.get() * progress));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    @Override
    public void tick() {
        if (isPlaying.get() && currentAudio.get() != null) {
            if (currentTime.get() < totalTime.get()) {
                currentTime.set(currentTime.get() + 1);
            } else {
                if (repeatEnabled) {
                    currentTime.set(0);
                } else {
                    playNextTrack();
                }
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
            if (keyCode == 257) {
                loadAudio(filePathInput.getValue());
                return true;
            }
            return filePathInput.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == 32 && currentAudio.get() != null) {
            togglePlayPause();
            return true;
        }

        return false;
    }

    @Override
    public boolean onClose(DraggableWindow window) {
        isPlaying.set(false);
        return true;
    }
}