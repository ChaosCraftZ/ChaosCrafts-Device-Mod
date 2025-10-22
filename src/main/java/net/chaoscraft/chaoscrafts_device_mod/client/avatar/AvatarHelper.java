package net.chaoscraft.chaoscrafts_device_mod.client.avatar;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.RiftLoginScreen;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AvatarHelper {

    // Simple cache so UI doesn't ask for avatar every single frame and get the instant fallback.
    // Keeps things chill: try cached version first, and only hit the fetcher on a controlled schedule.
    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_REQUEST = new ConcurrentHashMap<>();
    private static final Map<String, Integer> ATTEMPTS = new ConcurrentHashMap<>();
    private static final int MAX_ATTEMPTS = 6;

    private static String key(UUID playerId, int size) {
        return playerId.toString() + ":" + size;
    }

    public static ResourceLocation getAvatarTexture(UUID playerId, int size) {
        if (playerId == null) return getDefaultAvatar(size);

        String k = key(playerId, size);

        // Return cached texture if we already fetched a real one.
        ResourceLocation cached = CACHE.get(k);
        if (cached != null) return cached;

        // Try a fast synchronous fetch — this may return the fallback if the fetcher isn't ready yet.
        try {
            ResourceLocation tex = RiftLoginScreen.getAvatarTexture(playerId, size);
            ResourceLocation def = getDefaultAvatar(size);
            if (tex != null && !tex.equals(def)) {
                CACHE.put(k, tex);
                ATTEMPTS.remove(k);
                LAST_REQUEST.remove(k);
                return tex;
            }
        } catch (Exception e) {
            // swallow, we'll try async below — keep logs minimal
            try {
                if (Minecraft.getInstance().options.renderDebug) System.out.println("AvatarHelper: sync fetch failed -> " + e);
            } catch (Exception ignored) {}
        }

        // If we get here, the fetcher likely returned the default or threw — schedule a controlled retry
        scheduleFetchIfNeeded(playerId, size, k);

        // Always return a sensible default immediately so UI stays stable.
        return getDefaultAvatar(size);
    }

    private static void scheduleFetchIfNeeded(UUID playerId, int size, String k) {
        long now = System.currentTimeMillis();
        int at = ATTEMPTS.getOrDefault(k, 0);
        long last = LAST_REQUEST.getOrDefault(k, 0L);

        // Backoff: 500ms * 2^attempt, cap growth after a few tries
        long delay = 500L * (1L << Math.min(at, 5));
        if (at >= MAX_ATTEMPTS) return; // give up after some attempts
        if (now - last < delay) return; // too soon

        LAST_REQUEST.put(k, now);
        ATTEMPTS.put(k, at + 1);

        // Schedule on client thread to avoid weird context issues with RiftLoginScreen
        try {
            Minecraft.getInstance().execute(() -> {
                try {
                    ResourceLocation tex = RiftLoginScreen.getAvatarTexture(playerId, size);
                    ResourceLocation def = getDefaultAvatar(size);
                    if (tex != null && !tex.equals(def)) {
                        CACHE.put(k, tex);
                        ATTEMPTS.remove(k);
                        LAST_REQUEST.remove(k);
                    }
                } catch (Throwable ignored) {
                    // keep retrying until attempts exhausted
                }
            });
        } catch (Throwable ignored) {
            // can't schedule; will try again later when callers invoke getAvatarTexture
        }
    }

    public static ResourceLocation getDefaultAvatar(int size) {
        try {
            return RiftLoginScreen.getDefaultAvatar(size);
        } catch (Exception e) {
            // worst-case fallback if RiftLoginScreen is broken for some reason
            try {
                if (Minecraft.getInstance().options.renderDebug) System.out.println("AvatarHelper: getDefaultAvatar failed -> " + e);
            } catch (Exception ignored) {}
            return new ResourceLocation("chaoscrafts_device_mod:textures/gui/default_avatar_" + size + ".png");
        }
    }

    public static void clearCache() {
        CACHE.clear();
        ATTEMPTS.clear();
        LAST_REQUEST.clear();
        try {
            RiftLoginScreen.clearCaches();
        } catch (Exception ignored) {}
    }
}