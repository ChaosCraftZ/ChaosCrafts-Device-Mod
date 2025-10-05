package net.chaoscraft.chaoscrafts_device_mod.Client.Camera;

import java.io.Serializable;
import java.util.UUID;

public class CameraInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    public String id;
    public UUID owner;
    public int x, y, z;
    public String dimension;
    public String name;
    public boolean isPublic;
    public int width, height;
    public int fps;

    public CameraInfo() {}

    public CameraInfo(String id, UUID owner, int x, int y, int z, String dimension, String name) {
        this.id = id;
        this.owner = owner;
        this.x = x; this.y = y; this.z = z;
        this.dimension = dimension;
        this.name = name;
        this.isPublic = false;
        this.width = 320; this.height = 180; this.fps = 30;
    }
}

