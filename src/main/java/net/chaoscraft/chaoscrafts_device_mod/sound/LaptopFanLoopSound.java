package net.chaoscraft.chaoscrafts_device_mod.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundEvent;

public class LaptopFanLoopSound extends AbstractTickableSoundInstance {
    private static final float TARGET_VOLUME = 0.35f;
    private static final float BASE_START_VOLUME = 0.05f; // new baseline audible volume
    private static final int FADE_IN_TICKS = 40;
    private static final int FADE_OUT_TICKS = 60;

    private final int preDelayTicks;
    private boolean fadingOut = false;
    private boolean stopRequested = false;
    private boolean finished = false;
    private int ageTicks = 1;
    private int fadeOutStartTick = -1;
    private float fadeOutStartVolume = 0f;

    public LaptopFanLoopSound(SoundEvent event, double x, double y, double z, int preDelayTicks) {
        super(event, SoundSource.BLOCKS, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.volume = BASE_START_VOLUME; // start audible
        this.pitch = 1.0f;
        this.relative = false;
        this.x = (float) x; this.y = (float) y; this.z = (float) z;
        this.preDelayTicks = Math.max(0, preDelayTicks);
    }

    public void requestFadeOut() {
        if (finished || fadingOut) return;
        fadingOut = true;
        fadeOutStartTick = ageTicks;
        fadeOutStartVolume = (this.volume > 0f ? this.volume : TARGET_VOLUME * 0.25f);
    }

    @Override
    public void tick() {
        if (finished) return;
        if (stopRequested) { terminateImmediately(); return; }
        ageTicks++;
        if (!fadingOut) {
            if (ageTicks <= preDelayTicks) { this.volume = BASE_START_VOLUME; return; }
            int fadeAge = ageTicks - preDelayTicks;
            if (fadeAge <= FADE_IN_TICKS) {
                float prog = fadeAge / (float) FADE_IN_TICKS; // 0..1
                this.volume = BASE_START_VOLUME + (TARGET_VOLUME - BASE_START_VOLUME) * (prog * prog);
            } else {
                this.volume = TARGET_VOLUME;
            }
        } else {
            if (fadeOutStartTick < 1) { fadeOutStartTick = ageTicks; if (fadeOutStartVolume <= 0f) fadeOutStartVolume = Math.max(BASE_START_VOLUME, TARGET_VOLUME * 0.25f); }
            int dt = ageTicks - fadeOutStartTick; float prog = dt / (float) FADE_OUT_TICKS;
            if (prog >= 1f) { terminateImmediately(); }
            else { float ease = 1f - prog; this.volume = Math.max(0f, fadeOutStartVolume * ease * ease); }
        }
    }

    private void terminateImmediately() {
        this.volume = 0f;
        this.looping = false;
        this.finished = true;
        this.stop();
    }

    public boolean isFinished() { return finished; }
    public boolean isFadingOut() { return fadingOut; }
}

