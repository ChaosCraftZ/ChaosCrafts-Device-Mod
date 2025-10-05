package net.chaoscraft.chaoscrafts_device_mod.Core.Util.FileSystem;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class FSNode implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public boolean isDirectory;
    public String content;
    public Map<String, FSNode> children;
    public long created;
    public long modified;

    public FSNode(String name, boolean isDirectory) {
        this.name = name;
        this.isDirectory = isDirectory;
        this.content = "";
        this.children = isDirectory ? new HashMap<>() : null;
        this.created = System.currentTimeMillis();
        this.modified = this.created;
    }

    public void addChild(FSNode child) {
        if (!isDirectory) return;
        children.put(child.name, child);
        modified = System.currentTimeMillis();
    }

    public void removeChild(String name) {
        if (!isDirectory) return;
        children.remove(name);
        modified = System.currentTimeMillis();
    }

    public void setContent(String content) {
        if (isDirectory) return;
        this.content = content;
        modified = System.currentTimeMillis();
    }

    public String getType() {
        if (isDirectory) return "Folder";

        if (name.contains(".")) {
            String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
            switch (ext) {
                case "txt": return "Text Document";
                case "png": case "jpg": case "jpeg": case "gif": return "Image File";
                case "mp3": case "wav": case "ogg": return "Audio File";
                case "mp4": case "avi": case "mov": return "Video File";
                default: return ext.toUpperCase() + " File";
            }
        }
        return "File";
    }

    public String getFormattedSize() {
        if (isDirectory) {
            int itemCount = children != null ? children.size() : 0;
            return itemCount + " item" + (itemCount != 1 ? "s" : "");
        }

        int size = content != null ? content.length() : 0;
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return (size / 1024) + " KB";
        return (size / (1024 * 1024)) + " MB";
    }

    public String getFormattedDate() {
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .format(java.time.Instant.ofEpochMilli(modified).atZone(java.time.ZoneId.systemDefault()));
    }
}