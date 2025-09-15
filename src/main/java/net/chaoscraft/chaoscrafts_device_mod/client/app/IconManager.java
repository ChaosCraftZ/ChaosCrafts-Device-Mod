package net.chaoscraft.chaoscrafts_device_mod.client.app;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IconManager maps internal app names to ResourceLocations for icons.
 * If an app does not have a custom icon registered, default_icon.png is used.
 * Add-ons can register apps' icons at runtime via registerCustomIcon.
 */
public class IconManager {
    private static final Map<String, ResourceLocation> ICON_MAP = new ConcurrentHashMap<>();
    private static final ResourceLocation DEFAULT_ICON = new ResourceLocation("chaoscrafts_device_mod:textures/gui/icons/default_icon.png");

    static {
        ICON_MAP.put("browser", new ResourceLocation("chaoscrafts_device_mod:textures/gui/icons/browser_icon.png"));
        ICON_MAP.put("files", new ResourceLocation("chaoscrafts_device_mod:textures/gui/icons/files_icon.png"));
        ICON_MAP.put("geometry dash", new ResourceLocation("chaoscrafts_device_mod:textures/gui/icons/geometry_dash_icon.png"));
        ICON_MAP.put("home security", new ResourceLocation("chaoscrafts_device_mod:textures/gui/icons/home_security_icon.png"));
        ICON_MAP.put("marketplace", new ResourceLocation("chaoscrafts_device_mod:textures/gui/icons/marketplace_icon.png"));
        ICON_MAP.put("notepad", new ResourceLocation("chaoscrafts_device_mod:textures/gui/icons/notepad_icon.png"));
        ICON_MAP.put("notes", new ResourceLocation("chaoscrafts_device_mod:textures/gui/icons/notes_icon.png"));
        ICON_MAP.put("paint", new ResourceLocation("chaoscrafts_device_mod:textures/gui/icons/paint_icon.png"));
        ICON_MAP.put("settings", new ResourceLocation("chaoscrafts_device_mod:textures/gui/icons/settings_icon.png"));
        ICON_MAP.put("youtube", new ResourceLocation("chaoscrafts_device_mod:textures/gui/icons/youtube_icon.png"));
    }

    public static ResourceLocation getIconResource(String appName) {
        if (appName == null) return DEFAULT_ICON;
        ResourceLocation rl = ICON_MAP.get(appName.toLowerCase());
        if (rl == null) return DEFAULT_ICON;
        try {
            // ensure resource exists; if not, fall back
            Minecraft.getInstance().getResourceManager().getResource(rl);
            return rl;
        } catch (Exception e) {
            return DEFAULT_ICON;
        }
    }

    public static void registerCustomIcon(String appName, ResourceLocation icon) {
        if (appName == null || icon == null) return;
        ICON_MAP.put(appName.toLowerCase(), icon);
    }
}
