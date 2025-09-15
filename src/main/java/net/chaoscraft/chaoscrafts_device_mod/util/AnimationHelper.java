package net.chaoscraft.chaoscrafts_device_mod.util;

public class AnimationHelper {
    public static final double OPEN_ANIMATION_DURATION = 0.75; // seconds
    public static final double CLOSE_ANIMATION_DURATION = 1.25; // seconds

    public static long getAnimationDuration(boolean isOpening) {
        return (long) ((isOpening ? OPEN_ANIMATION_DURATION : CLOSE_ANIMATION_DURATION) * 1000);
    }
}