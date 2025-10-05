package net.chaoscraft.chaoscrafts_device_mod.Client.Sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Central manager for laptop key related sounds so future per-key mapping is easy.
 * - Randomized key presses (3 variants)
 * - Distinct spacebar sound
 * - Trackpad click
 * - Separate logical mouse click (currently may point to same .ogg as trackpad)
 */
public class LaptopKeySoundManager {
    private static final RandomSource RAND = RandomSource.create();
    private static BlockPos devicePos = null; // tracked device position

    public static void setDevicePos(BlockPos pos) { devicePos = pos; }
    public static void clearDevicePos() { devicePos = null; }

    /** Play a key press sound for the given character (client side only). */
    public static void playKey(char c) {
        if (c == ' ') {
            playAtDevice(ModSounds.LAPTOP_SPACEBAR.get(), 0.3f, 1.0f);
        } else if (Character.isLetterOrDigit(c) || isCommonSymbol(c)) {
            playRandomKeyClick();
        } else {
            playRandomKeyClick();
        }
    }

    /** Trackpad click for UI selection. */
    public static void playTrackpadClick() { playAtDevice(ModSounds.LAPTOP_TRACKPAD.get(), 0.6f, 1.0f); }

    /** Separate mouse click (registered separately even if sharing the same asset for now). */
    public static void playMouseClick() { playAtDevice(ModSounds.LAPTOP_MOUSE_CLICK.get(), 0.65f, 1.0f); }

    /** Random among the three keyboard click variants. */
    public static void playRandomKeyClick() {
        int r = RAND.nextInt(3);
        SoundEvent evt = r == 0 ? ModSounds.LAPTOP_KEYBOARD_1.get() : r == 1 ? ModSounds.LAPTOP_KEYBOARD_2.get() : ModSounds.LAPTOP_KEYBOARD_3.get();
        float pitch = 0.95f + RAND.nextFloat() * 0.1f;
        playAtDevice(evt, 0.05f, pitch);
    }

    public static void playLaptopOpen() { playAtDevice(ModSounds.LAPTOP_OPEN.get(), 0.0f, 1.0f); }
    public static void playLaptopClose() { playAtDevice(ModSounds.LAPTOP_CLOSE.get(), 0.0f, 1.0f); }

    private static boolean isCommonSymbol(char c) { return "`~!@#$%^&*()-_=+[{]}\\|;:'\"<,>.?/".indexOf(c) >= 0; }

    private static void playAtDevice(SoundEvent event, float volume, float pitch) {
        if (event == null) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockPos pos = devicePos;
        if (pos != null) {
            mc.level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, event, SoundSource.BLOCKS, volume, pitch, false);
        } else {
            try {
                SimpleSoundInstance inst = new SimpleSoundInstance(event.getLocation(), SoundSource.PLAYERS, volume, pitch, RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true);
                mc.getSoundManager().play(inst);
            } catch (Exception ignored) {}
        }
    }
}
