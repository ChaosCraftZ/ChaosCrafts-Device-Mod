package net.chaoscraft.chaoscrafts_device_mod.client.app;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

public class IconManager {
    private static final Map<String, ResourceLocation> ICON_MAP = new ConcurrentHashMap<>();
    private static final ResourceLocation DEFAULT_ICON = ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "textures/gui/icons/default_icon.png");
    private static final Set<String> missingIconLog = ConcurrentHashMap.newKeySet();

    static {
        ICON_MAP.put("browser", ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "textures/gui/icons/browser_icon.png"));
        ICON_MAP.put("files", ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "textures/gui/icons/files_icon.png"));
        ICON_MAP.put("geometry dash", ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "textures/gui/icons/geometry_dash_icon.png"));
        ICON_MAP.put("home security", ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "textures/gui/icons/home_security_icon.png"));
        ICON_MAP.put("marketplace", ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "textures/gui/icons/marketplace_icon.png"));
        ICON_MAP.put("notepad", ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "textures/gui/icons/notepad_icon.png"));
        ICON_MAP.put("notes", ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "textures/gui/icons/notes_icon.png"));
        ICON_MAP.put("paint", ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "textures/gui/icons/paint_icon.png"));
        ICON_MAP.put("settings", ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "textures/gui/icons/settings_icon.png"));
        ICON_MAP.put("youtube", ResourceLocation.fromNamespaceAndPath("chaoscrafts_device_mod", "textures/gui/icons/youtube_icon.png"));
    }

    public static ResourceLocation getIconResource(String appName) {
        if (appName == null) return DEFAULT_ICON;
        ResourceLocation rl = ICON_MAP.get(appName.toLowerCase());
        if (rl == null) {
            if (missingIconLog.add(appName.toLowerCase())) {
                System.out.println("[IconManager] No registered icon for key: '" + appName + "' -> using default icon");
            }
            return DEFAULT_ICON;
        }
        try {
            Minecraft.getInstance().getResourceManager().getResource(rl);
            return rl;
        } catch (Exception e) {
            if (missingIconLog.add(rl.toString())) {
                System.out.println("[IconManager] Icon resource missing for key '" + appName + "' (" + rl + ") -> using default icon");
            }
            return DEFAULT_ICON;
        }
    }

    public static void registerCustomIcon(String appName, ResourceLocation icon) {
        if (appName == null || icon == null) return;
        ICON_MAP.put(appName.toLowerCase(), icon);
    }
}
