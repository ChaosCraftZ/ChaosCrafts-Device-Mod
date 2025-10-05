package net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import net.chaoscraft.chaoscrafts_device_mod.Core.AsyncorAPI.AsyncRuntime;
import net.chaoscraft.chaoscrafts_device_mod.Core.Util.FileSystem.FilesManager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cleaned up AppRegistry:
 * - Centralized Gson instance and TypeToken constants.
 * - Use ConcurrentHashMap and concurrent key sets.
 * - Normalize internal names to lowercase (consistent and avoids duplicates).
 * - Consolidated file IO save methods and removed duplicated logic.
 * - Reduced noisy try/catch blocks (kept logging on failures).
 * - Minor API-preserving behavior changes (keeps same public signatures).
 */
public class AppRegistry {
    private static final Logger LOGGER = LogManager.getLogger();
    private static volatile AppRegistry INSTANCE;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type APP_MAP_TYPE = new TypeToken<Map<String, AppInfo>>() {}.getType();
    private static final Type APP_STATUS_TYPE = new TypeToken<AppStatus>() {}.getType();

    private final File registryFile;
    private final File statusFile;

    // thread-safe collections
    private final Map<String, AppInfo> allApps = new ConcurrentHashMap<>();
    private final Set<String> installedApps = ConcurrentHashMap.newKeySet();
    private final Set<String> desktopApps = ConcurrentHashMap.newKeySet();

    // default apps are constant and case-insensitive (internal names stored lowercase)
    private static final Set<String> DEFAULT_APPS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "browser", "calculator", "paint", "files", "settings", "youtube", "notepad", "marketplace"
    )));

    private final AsyncRuntime asyncRuntime = AsyncRuntime.get();
    private final CompletableFuture<Void> loadFuture;

    private AppRegistry() {
        File playerDir = FilesManager.getPlayerDataDir();
        this.registryFile = new File(playerDir, "app_registry.json");
        this.statusFile = new File(playerDir, "app_status.json");

        // Kick off background load and expose the future so others can wait
        this.loadFuture = asyncRuntime.submitIo(this::loadRegistry);
    }

    /**
     * Returns a future that completes once the registry has finished loading.
     */
    public CompletableFuture<Void> getLoadedFuture() {
        return loadFuture;
    }

    public static AppRegistry getInstance() {
        if (INSTANCE == null) {
            synchronized (AppRegistry.class) {
                if (INSTANCE == null) INSTANCE = new AppRegistry();
            }
        }
        return INSTANCE;
    }

    /**
     * Install with metadata. Returns a future that completes when the install finishes.
     */
    public CompletableFuture<Void> installApp(String appName, String displayName, String description, String version) {
        Objects.requireNonNull(appName, "appName");
        final String key = normalize(appName);
        return asyncRuntime.submitIo(() -> {
            allApps.put(key, new AppInfo(key, displayName, description, version));
            boolean added = installedApps.add(key);
            if (added) {
                desktopApps.add(key);
                ensureFilesManagerIcon(key);
                saveStatusAsync();
                refreshDesktopIfOpen();
                LOGGER.info("Installed app: {}", key);
            } else {
                // still persist metadata changes and registry
                saveRegistryAsync();
            }
        });
    }

    /**
     * Basic install when only internal name is known.
     */
    public CompletableFuture<Void> installApp(String appName) {
        Objects.requireNonNull(appName, "appName");
        final String key = normalize(appName);
        return asyncRuntime.submitIo(() -> {
            boolean added = installedApps.add(key);
            if (added) {
                desktopApps.add(key);
                ensureFilesManagerIcon(key);
                saveStatusAsync();
                refreshDesktopIfOpen();
                LOGGER.info("Installed app (basic): {}", key);
            }
        });
    }

    public AppInfo getAppInfo(String appName) {
        if (appName == null) return null;
        return allApps.get(normalize(appName));
    }

    public List<String> getInstalledAppNames() {
        return new ArrayList<>(installedApps);
    }

    public List<AppInfo> getAllApps() {
        return new ArrayList<>(allApps.values());
    }

    public boolean isDefaultApp(String appName) {
        if (appName == null) return false;
        return DEFAULT_APPS.contains(normalize(appName));
    }

    public boolean isInstalled(String appName) {
        if (appName == null) return false;
        return installedApps.contains(normalize(appName));
    }

    public boolean isOnDesktop(String appName) {
        if (appName == null) return false;
        return desktopApps.contains(normalize(appName));
    }

    public void addToDesktop(String appName) {
        Objects.requireNonNull(appName, "appName");
        final String key = normalize(appName);
        asyncRuntime.submitIo(() -> {
            boolean added = desktopApps.add(key);
            if (!added) {
                LOGGER.debug("addToDesktop: already present {}", key);
                return;
            }
            ensureFilesManagerIcon(key);
            saveStatusAsync();
            refreshDesktopIfOpen();
            LOGGER.info("Added to desktop: {}", key);
        });
    }

    public void removeFromDesktop(String appName) {
        Objects.requireNonNull(appName, "appName");
        final String key = normalize(appName);
        asyncRuntime.submitIo(() -> {
            desktopApps.remove(key);
            removeDesktopIconIfExists(key);
            saveStatusAsync();
            refreshDesktopIfOpen();
            LOGGER.info("Removed from desktop: {}", key);
        });
    }

    public void uninstallApp(String appName) {
        Objects.requireNonNull(appName, "appName");
        final String key = normalize(appName);
        asyncRuntime.submitIo(() -> {
            installedApps.remove(key);
            desktopApps.remove(key);
            removeDesktopIconIfExists(key);
            saveStatusAsync();
            refreshDesktopIfOpen();
            LOGGER.info("Uninstalled app: {}", key);
        });
    }

    public Set<String> getDesktopApps() {
        return new HashSet<>(desktopApps);
    }

    /* -------------------- Internal helpers -------------------- */

    private void loadRegistry() {
        try {
            // Load registry file
            if (registryFile.exists()) {
                try (FileReader reader = new FileReader(registryFile)) {
                    Map<String, AppInfo> loaded = GSON.fromJson(reader, APP_MAP_TYPE);
                    if (loaded != null) {
                        // normalize keys to lowercase to avoid duplicates
                        for (Map.Entry<String, AppInfo> e : loaded.entrySet()) {
                            String key = normalize(e.getKey());
                            AppInfo info = e.getValue();
                            if (info != null) {
                                // ensure internalName stored normalized
                                info.internalName = key;
                                allApps.put(key, info);
                            }
                        }
                    }
                }
            }

            // Load installation status (installed / desktop)
            loadInstallationStatus();

            // Ensure default apps exist and are marked installed + on desktop
            for (String d : DEFAULT_APPS) {
                allApps.computeIfAbsent(d, k -> {
                    String display = capitalizeEachWord(k);
                    return new AppInfo(k, display, "Default application", "1.0");
                });
                installedApps.add(d);
                desktopApps.add(d);
            }

            // Marketplace / extra entries
            Map<String, String> marketplace = new LinkedHashMap<>();
            marketplace.put("geometry dash", "A fun rhythm-based platformer");
            marketplace.put("home security", "Monitor your home security system");
            marketplace.put("audio player", "Play your favorite music files");
            marketplace.put("video player", "Watch video files");
            marketplace.put("notes", "A simple note taking application");
            marketplace.put("calendar", "Manage your events and schedule");
            marketplace.put("weather", "Check the current weather and forecast");

            for (Map.Entry<String, String> e : marketplace.entrySet()) {
                String internal = normalize(e.getKey());
                allApps.computeIfAbsent(internal, k -> {
                    return new AppInfo(k, capitalizeEachWord(k), e.getValue(), "1.0");
                });
            }

            // Persist any changes discovered during load
            saveRegistryAsync();
            saveStatusAsync();

            // Try to synchronize desktop icons with FilesManager
            try {
                FilesManager.getInstance().updateDesktopIconsFromRegistry();
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load app registry", e);
        }
    }

    private void loadInstallationStatus() {
        if (!statusFile.exists()) return;
        try (FileReader reader = new FileReader(statusFile)) {
            AppStatus status = GSON.fromJson(reader, APP_STATUS_TYPE);
            if (status != null) {
                installedApps.clear();
                if (status.installedApps != null) {
                    for (String s : status.installedApps) installedApps.add(normalize(s));
                }
                desktopApps.clear();
                if (status.desktopApps != null) {
                    for (String s : status.desktopApps) desktopApps.add(normalize(s));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load installation status", e);
        }
    }

    private void saveStatusAsync() {
        asyncRuntime.submitIo(() -> {
            try (FileWriter writer = new FileWriter(statusFile)) {
                AppStatus status = new AppStatus(new HashSet<>(installedApps), new HashSet<>(desktopApps));
                GSON.toJson(status, writer);
            } catch (Exception e) {
                LOGGER.error("Failed to save installation status", e);
            }
        });
    }

    private void saveRegistryAsync() {
        asyncRuntime.submitIo(() -> {
            try (FileWriter writer = new FileWriter(registryFile)) {
                GSON.toJson(allApps, writer);
            } catch (Exception e) {
                LOGGER.error("Failed to save app registry", e);
            }
        });
    }

    private void ensureFilesManagerIcon(String appName) {
        try {
            FilesManager fm = FilesManager.getInstance();
            if (fm == null) return;
            if (!fm.hasDesktopIcon(appName)) {
                int x = 150 + ThreadLocalRandom.current().nextInt(0, 301); // [150,450)
                int y = 60 + ThreadLocalRandom.current().nextInt(0, 201);  // [60,260)
                fm.addDesktopIcon(appName, x, y);
            }
        } catch (Exception e) {
            LOGGER.debug("ensureFilesManagerIcon failed for {}", appName, e);
        }
    }

    private void removeDesktopIconIfExists(String appName) {
        try {
            FilesManager fm = FilesManager.getInstance();
            if (fm != null && fm.hasDesktopIcon(appName)) {
                fm.removeDesktopIcon(appName);
            }
        } catch (Exception e) {
            LOGGER.debug("FilesManager removeDesktopIcon failed for {}", appName, e);
        }
    }

    private void refreshDesktopIfOpen() {
        try {
            asyncRuntime.runOnClientThread(() -> {
                try {
                    if (net.minecraft.client.Minecraft.getInstance().screen instanceof net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DesktopScreen) {
                        ((net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DesktopScreen) net.minecraft.client.Minecraft.getInstance().screen).refreshDesktopIcons();
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static String capitalizeEachWord(String s) {
        String[] parts = s.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            if (i < parts.length - 1) sb.append(' ');
        }
        return sb.toString();
    }

    private static String normalize(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT).trim();
    }

    public static class AppInfo {
        public String internalName;
        public String displayName;
        public String description;
        public String version;

        // Default constructor for Gson
        public AppInfo() {}

        public AppInfo(String internalName, String displayName, String description, String version) {
            this.internalName = internalName;
            this.displayName = displayName;
            this.description = description;
            this.version = version;
        }
    }

    private static class AppStatus {
        Set<String> installedApps;
        Set<String> desktopApps;

        // Default constructor for Gson
        @SuppressWarnings("unused")
        public AppStatus() {}

        public AppStatus(Set<String> installedApps, Set<String> desktopApps) {
            this.installedApps = installedApps;
            this.desktopApps = desktopApps;
        }
    }
}
