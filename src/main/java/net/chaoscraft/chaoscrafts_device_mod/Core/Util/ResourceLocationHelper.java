package net.chaoscraft.chaoscrafts_device_mod.Core.Util;

import net.chaoscraft.chaoscrafts_device_mod.CDM;
import net.minecraft.resources.ResourceLocation;

/**
 * Simple compatibility helper that prefers the modern {@code ResourceLocation.fromNamespaceAndPath}
 * API when it exists (Forge 1.20.2+/MC 1.20.2+), but gracefully falls back to the legacy
 * constructor on older environments such as Forge 1.20.1. This prevents {@link NoSuchMethodError}
 * crashes on clients that are still on the older runtime while allowing newer runtimes to avoid
 * the deprecated constructor.
 */
public final class ResourceLocationHelper {
    private ResourceLocationHelper() {}

    /**
     * Builds a resource location in this mod's namespace.
     *
     * @param path the path within the mod namespace
     * @return a valid {@link ResourceLocation}
     */
    public static ResourceLocation mod(String path) {
        return namespace(CDM.MOD_ID, path);
    }

    /**
     * Builds a resource location for the provided namespace/path pair while handling both the
     * modern and legacy ResourceLocation APIs.
     */
    public static ResourceLocation namespace(String namespace, String path) {
        try {
            return ResourceLocation.fromNamespaceAndPath(namespace, path);
        } catch (NoSuchMethodError ignored) {
            return new ResourceLocation(namespace, path);
        }
    }
}
