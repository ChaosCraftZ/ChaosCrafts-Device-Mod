package net.chaoscraft.chaoscrafts_device_mod.client.app;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;
import net.chaoscraft.chaoscrafts_device_mod.client.fs.FilesManager;
import net.chaoscraft.chaoscrafts_device_mod.ConfigHandler;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DesktopScreen;
import net.minecraft.client.Minecraft;
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

public class AppRegistry {
    private static final Logger LOGGER = LogManager.getLogger();
    private static volatile AppRegistry INSTANCE;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type APP_MAP_TYPE = new TypeToken<Map<String, AppInfo>>() {}.getType();
    private static final Type APP_STATUS_TYPE = new TypeToken<AppStatus>() {}.getType();

    private final File registryFile;
    private final File statusFile;

    private final Map<String, AppInfo> allApps = new ConcurrentHashMap<>();
    private final Set<String> installedApps = ConcurrentHashMap.newKeySet();
    private final Set<String> desktopApps = ConcurrentHashMap.newKeySet();

    private static Set<String> getDefaultApps() {
        Set<String> base = new HashSet<>(Arrays.asList(
                "browser", "calculator", "paint", "files", "settings", "youtube", "notepad", "marketplace"
        ));
        if (!ConfigHandler.experimentalAppsEnabled()) {
            base.remove("youtube");
            base.remove("browser");
            base.remove("home security");
        }
        return Collections.unmodifiableSet(base);
    }

    private final AsyncTaskManager asyncManager = AsyncTaskManager.getInstance();
    private final CompletableFuture<Void> loadFuture;

    private AppRegistry() {
        File playerDir = FilesManager.getPlayerDataDir();
        this.registryFile = new File(playerDir, "app_registry.json");
        this.statusFile = new File(playerDir, "app_status.json");

        this.loadFuture = asyncManager.submitIOTask(this::loadRegistry);
    }

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

    public CompletableFuture<Void> installApp(String appName, String displayName, String description, String version) {
        Objects.requireNonNull(appName, "appName");
        final String key = normalize(appName);
        return asyncManager.submitIOTask(() -> {
            allApps.put(key, new AppInfo(key, displayName, description, version));
            boolean added = installedApps.add(key);
            if (added) {
                desktopApps.add(key);
                ensureFilesManagerIcon(key);
                saveStatusAsync();
                refreshDesktopIfOpen();
                LOGGER.info("Installed app: {}", key);
            } else {
                saveRegistryAsync();
            }
        });
    }

    public CompletableFuture<Void> installApp(String appName) {
        Objects.requireNonNull(appName, "appName");
        final String key = normalize(appName);
        return asyncManager.submitIOTask(() -> {
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
        List<AppInfo> apps = new ArrayList<>(allApps.values());
        if (!ConfigHandler.experimentalAppsEnabled()) {
            apps.removeIf(a -> {
                if (a == null || a.internalName == null) return false;
                String n = a.internalName.toLowerCase(Locale.ROOT).trim();
                return n.equals("calendar") || n.equals("calender") || n.equals("youtube") || n.equals("browser") || n.equals("home security");
            });
        }
        return apps;
    }

    public boolean isDefaultApp(String appName) {
        if (appName == null) return false;
        return getDefaultApps().contains(normalize(appName));
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
        asyncManager.submitIOTask(() -> {
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
        asyncManager.submitIOTask(() -> {
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
        asyncManager.submitIOTask(() -> {
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

    private void loadRegistry() {
        try {
            if (registryFile.exists()) {
                try (FileReader reader = new FileReader(registryFile)) {
                    Map<String, AppInfo> loaded = GSON.fromJson(reader, APP_MAP_TYPE);
                    if (loaded != null) {
                        for (Map.Entry<String, AppInfo> e : loaded.entrySet()) {
                            String key = normalize(e.getKey());
                            AppInfo info = e.getValue();
                            if (info != null) {
                                info.internalName = key;
                                allApps.put(key, info);
                            }
                        }
                    }
                }
            }

            loadInstallationStatus();

            for (String d : getDefaultApps()) {
                allApps.computeIfAbsent(d, k -> {
                    String display = capitalizeEachWord(k);
                    return new AppInfo(k, display, "Default application", "1.0");
                });
                installedApps.add(d);
                desktopApps.add(d);
            }

            Map<String, String> marketplace = new LinkedHashMap<>();
            marketplace.put("geometry dash", "A fun rhythm-based platformer");
            if (ConfigHandler.experimentalAppsEnabled()) {
                marketplace.put("home security", "Monitor your home security system");
                marketplace.put("calendar", "Manage your events and schedule");
            }
            marketplace.put("audio player", "Play your favorite music files");
            marketplace.put("video player", "Watch video files");
            marketplace.put("notes", "A simple note taking application");
            marketplace.put("weather", "Check the current weather and forecast");

            for (Map.Entry<String, String> e : marketplace.entrySet()) {
                String internal = normalize(e.getKey());
                allApps.computeIfAbsent(internal, k -> {
                    return new AppInfo(k, capitalizeEachWord(k), e.getValue(), "1.0");
                });
            }

            if (!ConfigHandler.experimentalAppsEnabled()) {
                allApps.remove("calendar");
                allApps.remove("calender");
                allApps.remove("youtube");
                allApps.remove("browser");
                allApps.remove("home security");
            }

            saveRegistryAsync();
            saveStatusAsync();

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

            if (!ConfigHandler.experimentalAppsEnabled()) {
                installedApps.remove("youtube");
                installedApps.remove("browser");
                installedApps.remove("calendar");
                installedApps.remove("home security");

                desktopApps.remove("youtube");
                desktopApps.remove("browser");
                desktopApps.remove("calendar");
                desktopApps.remove("home security");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load installation status", e);
        }
    }

    private void saveStatusAsync() {
        asyncManager.submitIOTask(() -> {
            try (FileWriter writer = new FileWriter(statusFile)) {
                AppStatus status = new AppStatus(new HashSet<>(installedApps), new HashSet<>(desktopApps));
                GSON.toJson(status, writer);
            } catch (Exception e) {
                LOGGER.error("Failed to save installation status", e);
            }
        });
    }

    private void saveRegistryAsync() {
        asyncManager.submitIOTask(() -> {
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
                int x = 150 + ThreadLocalRandom.current().nextInt(0, 301);
                int y = 60 + ThreadLocalRandom.current().nextInt(0, 201);
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
            asyncManager.executeOnMainThread(() -> {
                try {
                    if (Minecraft.getInstance().screen instanceof DesktopScreen) {
                        ((DesktopScreen) Minecraft.getInstance().screen).refreshDesktopIcons();
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

        public AppStatus() {}

        public AppStatus(Set<String> installedApps, Set<String> desktopApps) {
            this.installedApps = installedApps;
            this.desktopApps = desktopApps;
        }
    }
}
