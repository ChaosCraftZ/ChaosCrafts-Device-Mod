package net.chaoscraft.chaoscrafts_device_mod.client.app;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class AppFactory {
    // Dynamic registry for add-on apps. Other mods/add-ons can call registerApp to provide IApp instances.
    private static final Map<String, Supplier<IApp>> DYNAMIC_REGISTRY = new ConcurrentHashMap<>();

    public static void registerApp(String internalName, Supplier<IApp> creator) {
        if (internalName == null || creator == null) return;
        DYNAMIC_REGISTRY.put(internalName.toLowerCase(), creator);
    }

    public static IApp create(String appName) {
        if (!AppRegistry.getInstance().isInstalled(appName)) {
            return null;
        }

        // Check dynamic registry first (allows add-ons to register apps without modifying this switch)
        Supplier<IApp> sup = DYNAMIC_REGISTRY.get(appName.toLowerCase());
        if (sup != null) return sup.get();

        return switch (appName.toLowerCase()) {
            case "browser" -> new BrowserApp();
            case "calculator" -> new CalculatorApp();
            case "paint" -> new PaintApp();
            case "files" -> new FilesApp();
            case "settings" -> new SettingsApp();
            case "youtube" -> new YouTubeApp();
            case "marketplace" -> new MarketplaceApp();
            case "geometry dash" -> new GeometryDashApp();
            case "home security" -> new HomeSecurityApp();
            case "audio player" -> new AudioPlayerApp();
            case "video player" -> new VideoPlayerApp();
            case "notepad" -> new NotepadApp();
            case "notes" -> new NotesApp();
            case "calendar" -> new CalendarApp();
            case "weather" -> new WeatherApp();
            default -> null;
        };
    }

    public static List<String> getAvailableAppNames() {
        return AppRegistry.getInstance().getInstalledAppNames();
    }

    public static List<String> getDefaultApps() {
        return List.of(
                "browser", "calculator", "paint", "files", "settings",
                "youtube", "geometry dash", "home security", "marketplace",
                "notepad", "video player", "audio player"
        );
    }
}
