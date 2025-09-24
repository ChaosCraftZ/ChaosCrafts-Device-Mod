package net.chaoscraft.chaoscrafts_device_mod.client.app;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Efficient app factory that maps app names to integer IDs for faster lookups
 * and keeps a registry of suppliers keyed by integer IDs. Supports registering
 * dynamic apps (from other mods/add-ons) and built-in apps.
 */
public final class AppFactory {
    // Built-in app name -> id assignments (lowercase keys)
    private static final Map<String, Integer> BUILT_IN_NAME_TO_ID = Map.ofEntries(
            Map.entry("browser", 1),
            Map.entry("calculator", 2),
            Map.entry("paint", 3),
            Map.entry("files", 4),
            Map.entry("settings", 5),
            Map.entry("youtube", 6),
            Map.entry("marketplace", 7),
            Map.entry("geometry dash", 8),
            Map.entry("home security", 9),
            Map.entry("audio player", 10),
            Map.entry("video player", 11),
            Map.entry("notepad", 12),
            Map.entry("notes", 13),
            Map.entry("calendar", 14),
            Map.entry("weather", 15)
    );

    // Map of name (lowercase) to id that includes both built-in and dynamic apps
    private static final ConcurrentHashMap<String, Integer> NAME_TO_ID = new ConcurrentHashMap<>(BUILT_IN_NAME_TO_ID);

    // Registry of id -> supplier (built-in and dynamic)
    private static final ConcurrentHashMap<Integer, Supplier<IApp>> ID_REGISTRY = new ConcurrentHashMap<>();

    // Next dynamic id (starts after built-ins)
    private static final AtomicInteger NEXT_DYNAMIC_ID = new AtomicInteger(16);

    static {
        // Register built-in suppliers by id. Suppliers create new instances each call.
        ID_REGISTRY.put(1, () -> new BrowserApp());
        ID_REGISTRY.put(2, () -> new CalculatorApp());
        ID_REGISTRY.put(3, () -> new PaintApp());
        ID_REGISTRY.put(4, () -> new FilesApp());
        ID_REGISTRY.put(5, () -> new SettingsApp());
        ID_REGISTRY.put(6, () -> new YouTubeApp());
        ID_REGISTRY.put(7, () -> new MarketplaceApp());
        ID_REGISTRY.put(8, () -> new GeometryDashApp());
        ID_REGISTRY.put(9, () -> new HomeSecurityApp());
        ID_REGISTRY.put(10, () -> new AudioPlayerApp());
        ID_REGISTRY.put(11, () -> new VideoPlayerApp());
        ID_REGISTRY.put(12, () -> new NotepadApp());
        ID_REGISTRY.put(13, () -> new NotesApp());
        ID_REGISTRY.put(14, () -> new CalendarApp());
        ID_REGISTRY.put(15, () -> new WeatherApp());
    }

    private AppFactory() { /* no instances */ }

    /**
     * Register a dynamic app by name. If the name already exists it will overwrite
     * the supplier for that id. Name matching is case-insensitive.
     *
     * Returns the integer id assigned to the registered app (useful for advanced uses).
     */
    public static int registerApp(String internalName, Supplier<IApp> creator) {
        if (internalName == null || creator == null) throw new IllegalArgumentException("name and creator cannot be null");
        String key = internalName.toLowerCase();
        // assign or reuse an id for this name
        int id = NAME_TO_ID.computeIfAbsent(key, k -> NEXT_DYNAMIC_ID.getAndIncrement());
        ID_REGISTRY.put(id, creator);
        return id;
    }

    /**
     * Create an app instance by name. Returns null if the app is not installed
     * (according to AppRegistry) or not registered in the factory.
     */
    public static IApp create(String appName) {
        if (appName == null) return null;

        // fast check for whether the app is installed
        if (!AppRegistry.getInstance().isInstalled(appName)) {
            return null;
        }

        Integer id = NAME_TO_ID.get(appName.toLowerCase());
        if (id == null) return null;

        Supplier<IApp> sup = ID_REGISTRY.get(id);
        return (sup != null) ? sup.get() : null;
    }

    /**
     * Return the names of apps available according to AppRegistry (unchanged behavior).
     */
    public static List<String> getAvailableAppNames() {
        return AppRegistry.getInstance().getInstalledAppNames();
    }

    /**
     * Default apps (kept as strings for compatibility with other code).
     */
    public static List<String> getDefaultApps() {
        return List.of(
                "browser", "calculator", "paint", "files", "settings",
                "youtube", "geometry dash", "home security", "marketplace",
                "notepad", "video player", "audio player"
        );
    }

    /**
     * Expose a snapshot of registered names -> ids (for diagnostics or other uses).
     */
    public static Map<String, Integer> getRegisteredNameToIdSnapshot() {
        return NAME_TO_ID.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
