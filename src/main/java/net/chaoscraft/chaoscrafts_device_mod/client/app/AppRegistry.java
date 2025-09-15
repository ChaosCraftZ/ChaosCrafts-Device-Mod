package net.chaoscraft.chaoscrafts_device_mod.client.app;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;
import net.chaoscraft.chaoscrafts_device_mod.client.fs.FilesManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class AppRegistry {
    private static final Logger LOGGER = LogManager.getLogger();
    private static AppRegistry INSTANCE;

    private final File registryFile;
    private final File statusFile;

    private final Map<String, AppInfo> allApps = new ConcurrentHashMap<>();
    private final Set<String> installedApps = new CopyOnWriteArraySet<>();
    private final Set<String> desktopApps = new CopyOnWriteArraySet<>();
    private final Set<String> defaultApps = new CopyOnWriteArraySet<>();

    private final AsyncTaskManager asyncManager = AsyncTaskManager.getInstance();
    // future that completes when the registry has finished loading
    private final java.util.concurrent.CompletableFuture<Void> loadFuture;

    private AppRegistry() {
        this.registryFile = new File(FilesManager.getPlayerDataDir(), "app_registry.json");
        this.statusFile = new File(FilesManager.getPlayerDataDir(), "app_status.json");

        // Default apps
        Collections.addAll(defaultApps, "browser", "calculator", "paint", "files", "settings", "youtube", "notepad", "marketplace");

        // Load on background thread and keep the future so callers can wait for completion
        this.loadFuture = asyncManager.submitIOTask(this::loadRegistry);
    }

    // Allows callers to run logic when the registry has finished loading (may already be completed)
    public java.util.concurrent.CompletableFuture<Void> getLoadedFuture() {
        return loadFuture;
    }

    public static synchronized AppRegistry getInstance() {
        if (INSTANCE == null) INSTANCE = new AppRegistry();
        return INSTANCE;
    }

    // Install with metadata; returns a CompletableFuture that completes when install finished
    public CompletableFuture<Void> installApp(String appName, String displayName, String description, String version) {
        return asyncManager.submitIOTask(() -> {
            allApps.put(appName, new AppInfo(appName, displayName, description, version));
            boolean added = installedApps.add(appName);
            if (!added) {
                saveRegistry();
                return;
            }
            desktopApps.add(appName);
            ensureFilesManagerIcon(appName);
            saveRegistry();
            saveInstallationStatus();
            refreshDesktopIfOpen();
            LOGGER.info("Installed app: {}", appName);
        });
    }

    // Basic install used when only internal name is known
    public CompletableFuture<Void> installApp(String appName) {
        return asyncManager.submitIOTask(() -> {
            boolean added = installedApps.add(appName);
            if (!added) return;
            desktopApps.add(appName);
            ensureFilesManagerIcon(appName);
            saveInstallationStatus();
            refreshDesktopIfOpen();
            LOGGER.info("Installed app (basic): {}", appName);
        });
    }

    public AppInfo getAppInfo(String appName) {
        return allApps.get(appName);
    }

    public List<String> getInstalledAppNames() {
        return new ArrayList<>(installedApps);
    }

    public List<AppInfo> getAllApps() {
        return new ArrayList<>(allApps.values());
    }

    public boolean isDefaultApp(String appName) {
        return defaultApps.contains(appName);
    }

    public boolean isInstalled(String appName) {
        return installedApps.contains(appName);
    }

    public boolean isOnDesktop(String appName) {
        return desktopApps.contains(appName);
    }

    public void addToDesktop(String appName) {
        asyncManager.submitIOTask(() -> {
            boolean added = desktopApps.add(appName);
            if (!added) {
                LOGGER.info("addToDesktop: already present {}", appName);
                return;
            }
            ensureFilesManagerIcon(appName);
            saveInstallationStatus();
            refreshDesktopIfOpen();
            LOGGER.info("Added to desktop: {}", appName);
        });
    }

    public void removeFromDesktop(String appName) {
        asyncManager.submitIOTask(() -> {
            desktopApps.remove(appName);
            try {
                FilesManager fm = FilesManager.getInstance();
                if (fm != null && fm.hasDesktopIcon(appName)) fm.removeDesktopIcon(appName);
            } catch (Exception e) {
                LOGGER.debug("FilesManager removeDesktopIcon failed", e);
            }
            saveInstallationStatus();
            refreshDesktopIfOpen();
            LOGGER.info("Removed from desktop: {}", appName);
        });
    }

    public void uninstallApp(String appName) {
        asyncManager.submitIOTask(() -> {
            installedApps.remove(appName);
            desktopApps.remove(appName);
            try {
                FilesManager fm = FilesManager.getInstance();
                if (fm != null && fm.hasDesktopIcon(appName)) fm.removeDesktopIcon(appName);
            } catch (Exception e) {
                LOGGER.debug("FilesManager removeDesktopIcon failed", e);
            }
            saveInstallationStatus();
            refreshDesktopIfOpen();
            LOGGER.info("Uninstalled app: {}", appName);
        });
    }

    public Set<String> getDesktopApps() {
        return new HashSet<>(desktopApps);
    }

    private void loadRegistry() {
        try {
            if (registryFile.exists()) {
                try (FileReader reader = new FileReader(registryFile)) {
                    Gson gson = new Gson();
                    Type type = new TypeToken<Map<String, AppInfo>>(){}.getType();
                    Map<String, AppInfo> loaded = gson.fromJson(reader, type);
                    if (loaded != null) allApps.putAll(loaded);
                }
            }

            loadInstallationStatus();

            // Remove legacy apps
            // NOTE: Removed removal of notes/calendar/weather so they can appear in the marketplace
//            List<String> legacy = Arrays.asList("notes", "calendar", "weather");
//            for (String l : legacy) {
//                allApps.remove(l);
//                installedApps.remove(l);
//                desktopApps.remove(l);
//                try { FilesManager fm = FilesManager.getInstance(); if (fm != null && fm.hasDesktopIcon(l)) fm.removeDesktopIcon(l); } catch (Exception ignored) {}
//            }

            // Ensure defaults
            for (String d : defaultApps) {
                if (!allApps.containsKey(d)) {
                    String display = d.substring(0,1).toUpperCase() + d.substring(1);
                    allApps.put(d, new AppInfo(d, display, "Default application", "1.0"));
                }
                installedApps.add(d);
                desktopApps.add(d);
            }

            // Marketplace entries (space-separated internal names)
            Map<String,String> nonDefault = new LinkedHashMap<>();
            nonDefault.put("geometry dash","A fun rhythm-based platformer");
            nonDefault.put("home security","Monitor your home security system");
            nonDefault.put("audio player","Play your favorite music files");
            nonDefault.put("video player","Watch video files");
            // Add Notes, Calendar and Weather to marketplace entries
            nonDefault.put("notes", "A simple note taking application");
            nonDefault.put("calendar", "Manage your events and schedule");
            nonDefault.put("weather", "Check the current weather and forecast");

            for (Map.Entry<String,String> e : nonDefault.entrySet()) {
                String internal = e.getKey();
                if (!allApps.containsKey(internal)) {
                    String display = Arrays.stream(internal.split(" "))
                            .map(s -> s.substring(0,1).toUpperCase() + s.substring(1))
                            .reduce((a,b)->a+" "+b).orElse(internal);
                    allApps.put(internal, new AppInfo(internal, display, e.getValue(), "1.0"));
                }
            }

            saveRegistry();
            saveInstallationStatus();

            try { FilesManager.getInstance().updateDesktopIconsFromRegistry(); } catch (Exception ignored) {}
        } catch (Exception e) {
            LOGGER.error("Failed to load app registry", e);
        }
    }

    private void loadInstallationStatus() {
        if (!statusFile.exists()) return;
        try (FileReader reader = new FileReader(statusFile)) {
            Gson gson = new Gson();
            Type type = new TypeToken<AppStatus>(){}.getType();
            AppStatus status = gson.fromJson(reader, type);
            if (status != null) {
                installedApps.clear();
                if (status.installedApps != null) installedApps.addAll(status.installedApps);
                desktopApps.clear();
                if (status.desktopApps != null) desktopApps.addAll(status.desktopApps);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load installation status", e);
        }
    }

    private void saveInstallationStatus() {
        asyncManager.submitIOTask(() -> {
            try (FileWriter writer = new FileWriter(statusFile)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                AppStatus status = new AppStatus(installedApps, desktopApps);
                gson.toJson(status, writer);
            } catch (Exception e) {
                LOGGER.error("Failed to save installation status", e);
            }
        });
    }

    private void saveRegistry() {
        asyncManager.submitIOTask(() -> {
            try (FileWriter writer = new FileWriter(registryFile)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(allApps, writer);
            } catch (Exception e) {
                LOGGER.error("Failed to save app registry", e);
            }
        });
    }

    private void ensureFilesManagerIcon(String appName) {
        try {
            FilesManager fm = FilesManager.getInstance();
            if (fm != null && !fm.hasDesktopIcon(appName)) {
                int x = 150 + (int) (Math.random() * 300);
                int y = 60 + (int) (Math.random() * 200);
                fm.addDesktopIcon(appName, x, y);
            }
        } catch (Exception e) {
            LOGGER.debug("ensureFilesManagerIcon failed for {}", appName, e);
        }
    }

    private void refreshDesktopIfOpen() {
        try {
            asyncManager.executeOnMainThread(() -> {
                try {
                    if (net.minecraft.client.Minecraft.getInstance().screen instanceof net.chaoscraft.chaoscrafts_device_mod.client.screen.DesktopScreen) {
                        ((net.chaoscraft.chaoscrafts_device_mod.client.screen.DesktopScreen) net.minecraft.client.Minecraft.getInstance().screen).refreshDesktopIcons();
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    public static class AppInfo {
        public String internalName;
        public String displayName;
        public String description;
        public String version;

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

        public AppStatus(Set<String> installedApps, Set<String> desktopApps) {
            this.installedApps = installedApps;
            this.desktopApps = desktopApps;
        }
    }
}
