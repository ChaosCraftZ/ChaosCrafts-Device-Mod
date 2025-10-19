package net.chaoscraft.chaoscrafts_device_mod.client.app;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import net.chaoscraft.chaoscrafts_device_mod.client.app.messenger.MessengerApp;

public final class AppFactory {
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
            Map.entry("weather", 15),
            Map.entry("messenger", 16)
    );

    private static final ConcurrentHashMap<String, Integer> NAME_TO_ID = new ConcurrentHashMap<>(BUILT_IN_NAME_TO_ID);

    private static final ConcurrentHashMap<Integer, Supplier<Object>> ID_REGISTRY = new ConcurrentHashMap<>();

    private static final AtomicInteger NEXT_DYNAMIC_ID = new AtomicInteger(17);

    static {
        ID_REGISTRY.put(1, (Supplier<Object>) (() -> new BrowserApp()));
        ID_REGISTRY.put(2, (Supplier<Object>) (() -> new CalculatorApp()));
        ID_REGISTRY.put(3, (Supplier<Object>) (() -> new PaintApp()));
        ID_REGISTRY.put(4, (Supplier<Object>) (() -> new FilesApp()));
        ID_REGISTRY.put(5, (Supplier<Object>) (() -> new SettingsApp()));
        ID_REGISTRY.put(6, (Supplier<Object>) (() -> new YouTubeApp()));
        ID_REGISTRY.put(7, (Supplier<Object>) (() -> new MarketplaceApp()));
        ID_REGISTRY.put(8, (Supplier<Object>) (() -> new GeometryDashApp()));
        ID_REGISTRY.put(9, (Supplier<Object>) (() -> new HomeSecurityApp()));
        ID_REGISTRY.put(10, (Supplier<Object>) (() -> new AudioPlayerApp()));
        ID_REGISTRY.put(11, (Supplier<Object>) (() -> new VideoPlayerApp()));
        ID_REGISTRY.put(12, (Supplier<Object>) (() -> new NotepadApp()));
        ID_REGISTRY.put(13, (Supplier<Object>) (() -> new NotesApp()));
        ID_REGISTRY.put(14, (Supplier<Object>) (() -> new CalendarApp()));
        ID_REGISTRY.put(15, (Supplier<Object>) (() -> new WeatherApp()));
        ID_REGISTRY.put(16, (Supplier<Object>) (() -> new MessengerApp()));
    }

    private AppFactory() { }

    public static int registerApp(String internalName, Supplier<Object> creator) {
        if (internalName == null || creator == null) throw new IllegalArgumentException("name and creator cannot be null");
        String key = internalName.toLowerCase();
        int id = NAME_TO_ID.computeIfAbsent(key, k -> NEXT_DYNAMIC_ID.getAndIncrement());
        ID_REGISTRY.put(id, creator);
        return id;
    }

    public static IApp create(String appName) {
        if (appName == null) return null;

        if (!AppRegistry.getInstance().isInstalled(appName)) {
            return null;
        }

        Integer id = NAME_TO_ID.get(appName.toLowerCase());
        if (id == null) return null;

        Supplier<Object> sup = ID_REGISTRY.get(id);
        Object o = (sup != null) ? sup.get() : null;
        return (o instanceof IApp) ? (IApp) o : null;
    }

    public static List<String> getAvailableAppNames() {
        return AppRegistry.getInstance().getInstalledAppNames();
    }

    public static List<String> getDefaultApps() {
        return List.of(
                "browser", "calculator", "paint", "files", "settings",
                "youtube", "geometry dash", "home security", "marketplace",
                "notepad", "video player", "audio player", "messenger"
        );
    }

    public static Map<String, Integer> getRegisteredNameToIdSnapshot() {
        return NAME_TO_ID.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}