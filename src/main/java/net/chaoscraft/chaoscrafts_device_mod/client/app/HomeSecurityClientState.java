package net.chaoscraft.chaoscrafts_device_mod.client.app;

import net.chaoscraft.chaoscrafts_device_mod.backend.CameraInfo;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class HomeSecurityClientState {
    private static final CopyOnWriteArrayList<CameraInfo> cameras = new CopyOnWriteArrayList<>();

    public static void setCameras(List<CameraInfo> cams) {
        cameras.clear();
        if (cams != null) cameras.addAll(cams);
    }

    public static List<CameraInfo> getCameras() {
        return Collections.unmodifiableList(cameras);
    }

    public static void clear() { cameras.clear(); }
}

