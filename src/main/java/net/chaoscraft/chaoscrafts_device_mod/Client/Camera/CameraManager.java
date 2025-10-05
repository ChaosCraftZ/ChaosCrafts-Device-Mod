package net.chaoscraft.chaoscrafts_device_mod.Client.Camera;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.*;
import java.util.*;

import net.minecraft.server.level.ServerPlayer;

public class CameraManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static CameraManager INSTANCE;
    private final Map<String, CameraInfo> cameras = new HashMap<>();
    private final Map<String, List<ServerPlayer>> subscribers = new HashMap<>();
    private final File storageFile;

    private CameraManager() {
        // Use a proper storage directory
        File baseDir = new File("chaoscrafts_device_mod");
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            LOGGER.warn("Failed to create camera storage directory: {}", baseDir.getAbsolutePath());
        }
        storageFile = new File(baseDir, "cameras.dat");
        load();
    }

    public static synchronized CameraManager getInstance() {
        if (INSTANCE == null) INSTANCE = new CameraManager();
        return INSTANCE;
    }

    public synchronized List<CameraInfo> listCameras() {
        return new ArrayList<>(cameras.values());
    }

    public synchronized CameraInfo getCamera(String id) {
        return cameras.get(id);
    }

    public synchronized void registerCamera(CameraInfo info) {
        cameras.put(info.id, info);
        save();
    }

    public synchronized void unregisterCamera(String id) {
        cameras.remove(id);
        subscribers.remove(id);
        save();
    }

    public synchronized void subscribe(String cameraId, ServerPlayer player) {
        subscribers.computeIfAbsent(cameraId, k -> new ArrayList<>());
        List<ServerPlayer> list = subscribers.get(cameraId);
        if (!list.contains(player)) list.add(player);
    }

    public synchronized void unsubscribe(String cameraId, ServerPlayer player) {
        List<ServerPlayer> list = subscribers.get(cameraId);
        if (list != null) list.remove(player);
    }

    public synchronized List<ServerPlayer> getSubscribers(String cameraId) {
        List<ServerPlayer> list = subscribers.get(cameraId);
        return list == null ? Collections.emptyList() : new ArrayList<>(list);
    }

    private synchronized void save() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(storageFile))) {
            oos.writeObject(new ArrayList<>(cameras.values()));
        } catch (IOException e) {
            LOGGER.error("Failed to save camera data", e);
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized void load() {
        if (!storageFile.exists()) return;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(storageFile))) {
            Object o = ois.readObject();
            if (o instanceof List) {
                List<CameraInfo> list = (List<CameraInfo>) o;
                cameras.clear();
                for (CameraInfo ci : list) cameras.put(ci.id, ci);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load camera data", e);
        }
    }
}

