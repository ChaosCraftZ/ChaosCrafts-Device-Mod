package net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Device;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents all persistent state for a single in-game PC (laptop) instance.
 * Stored server-side inside {@link net.chaoscraft.chaoscrafts_device_mod.Core.PC.block.entity.LaptopEntity}
 * and synchronized to clients as needed.
 */
public class DeviceState {
    private static final long EPOCH_MILLIS = System.currentTimeMillis();
    private static final List<String> DEFAULT_DESKTOP_APPS = List.of(
            "browser",
            "calculator",
            "files",
            "settings",
            "weather",
            "notepad",
            "youtube"
    );

    private final List<DesktopIconData> desktopIcons = new ArrayList<>();
    private final VirtualFileNode root = new VirtualFileNode("", true);

    private String wallpaperName = null;
    private boolean wallpaperIsColor = false;
    private int wallpaperColor = 0xFF000000;

    public DeviceState() {
        initializeDefaults();
    }

    public synchronized void initializeDefaults() {
        if (root.children.isEmpty()) {
            VirtualFileNode desktop = createDirectory(root, "Desktop");
            VirtualFileNode user = createDirectory(root, "User");
            VirtualFileNode documents = createDirectory(user, "Documents");
            VirtualFileNode pictures = createDirectory(user, "Pictures");
            VirtualFileNode downloads = createDirectory(user, "Downloads");

            documents.addChild(new VirtualFileNode("README.txt", false).withContent("Welcome to your virtual PC!\nThis is a shared document."));
            documents.addChild(new VirtualFileNode("Notes.txt", false).withContent("Shared notes live here."));
            pictures.touch();
            downloads.touch();
            desktop.touch();
        }

        if (desktopIcons.isEmpty()) {
            int baseX = 150;
            int baseY = 60;
            int step = 80;
            for (int i = 0; i < DEFAULT_DESKTOP_APPS.size(); i++) {
                String app = DEFAULT_DESKTOP_APPS.get(i);
                int x = baseX + (i % 4) * step;
                int y = baseY + (i / 4) * step;
                desktopIcons.add(new DesktopIconData(capitalize(app), x, y));
            }
        }
    }

    /* =========================== Desktop icon API =========================== */

    public synchronized List<DesktopIconData> copyDesktopIcons() {
        return desktopIcons.stream().map(DesktopIconData::copy).collect(Collectors.toList());
    }

    public synchronized boolean hasDesktopIcon(String name) {
        return desktopIcons.stream().anyMatch(icon -> icon.name.equalsIgnoreCase(name));
    }

    public synchronized boolean addDesktopIcon(String name, int x, int y) {
        if (name == null || name.isBlank() || hasDesktopIcon(name)) {
            return false;
        }
        desktopIcons.add(new DesktopIconData(name, x, y));
        return true;
    }

    public synchronized boolean removeDesktopIcon(String name) {
        return desktopIcons.removeIf(icon -> icon.name.equalsIgnoreCase(name));
    }

    public synchronized boolean updateDesktopIconPosition(String name, int x, int y) {
        for (DesktopIconData icon : desktopIcons) {
            if (icon.name.equalsIgnoreCase(name)) {
                icon.x = x;
                icon.y = y;
                return true;
            }
        }
        return false;
    }

    public synchronized boolean renameDesktopIcon(String oldName, String newName) {
        if (hasDesktopIcon(newName)) {
            return false;
        }
        for (DesktopIconData icon : desktopIcons) {
            if (icon.name.equalsIgnoreCase(oldName)) {
                icon.name = newName;
                return true;
            }
        }
        return false;
    }

    /* ========================== Virtual file system ======================== */

    public synchronized boolean createPath(String parentPath, String name, boolean directory, String initialContent) {
        VirtualFileNode parent = resolve(parentPath);
        if (parent == null || !parent.directory || parent.children.containsKey(name)) {
            return false;
        }
        VirtualFileNode node = new VirtualFileNode(name, directory);
        if (!directory && initialContent != null) {
            node.withContent(initialContent);
        }
        parent.addChild(node);
        parent.touch();
        return true;
    }

    public synchronized boolean deletePath(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return false;
        }
        VirtualFileNode node = resolve(path);
        if (node == null) {
            return false;
        }
        VirtualFileNode parent = node.parent;
        if (parent == null) {
            return false;
        }
        parent.removeChild(node.name);
        parent.touch();
        return true;
    }

    public synchronized boolean renamePath(String path, String newName) {
        if (path == null || newName == null || newName.isBlank()) {
            return false;
        }
        VirtualFileNode node = resolve(path);
        if (node == null || node.parent == null) {
            return false;
        }
        VirtualFileNode parent = node.parent;
        if (parent.children.containsKey(newName) && parent.children.get(newName) != node) {
            return false;
        }
        parent.children.remove(node.name);
        node.name = newName;
        parent.children.put(newName, node);
        node.touch();
        parent.touch();
        return true;
    }

    public synchronized boolean updateFileContent(String path, String content) {
        VirtualFileNode node = resolve(path);
        if (node == null || node.directory) {
            return false;
        }
        node.withContent(content != null ? content : "");
        node.touch();
        return true;
    }

    public synchronized List<VirtualFileSnapshot> listDirectory(String path) {
        VirtualFileNode dir = resolve(path);
        if (dir == null || !dir.directory) {
            return List.of();
        }
        List<VirtualFileSnapshot> result = new ArrayList<>();
        dir.children.values().forEach(child -> result.add(child.snapshot()));
        return result;
    }

    public synchronized Optional<VirtualFileSnapshot> getFile(String path) {
        VirtualFileNode node = resolve(path);
        return node != null ? Optional.of(node.snapshotWithContent()) : Optional.empty();
    }

    private VirtualFileNode resolve(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return root;
        }
        String normalized = normalize(path);
        String[] parts = normalized.substring(1).split("/");
        VirtualFileNode cursor = root;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            cursor = cursor.children.get(part);
            if (cursor == null) {
                return null;
            }
        }
        return cursor;
    }

    private static String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        String trimmed = path.trim();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        if (trimmed.length() > 1 && trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static VirtualFileNode createDirectory(VirtualFileNode parent, String name) {
        VirtualFileNode node = new VirtualFileNode(name, true);
        parent.addChild(node);
        return node;
    }

    /* ============================ Wallpaper state ========================== */

    public synchronized String getWallpaperName() {
        return wallpaperName;
    }

    public synchronized void setWallpaperName(String wallpaperName) {
        this.wallpaperName = wallpaperName;
    }

    public synchronized boolean isWallpaperColor() {
        return wallpaperIsColor;
    }

    public synchronized void setWallpaperColor(int color) {
        this.wallpaperIsColor = true;
        this.wallpaperColor = color;
        this.wallpaperName = null;
    }

    public synchronized int getWallpaperColor() {
        return wallpaperColor;
    }

    public synchronized void clearWallpaperImage() {
        this.wallpaperName = null;
        this.wallpaperIsColor = false;
    }

    /* ============================== Serialization ========================== */

    public synchronized CompoundTag saveToTag() {
        CompoundTag tag = new CompoundTag();

        ListTag iconList = new ListTag();
        for (DesktopIconData icon : desktopIcons) {
            iconList.add(icon.saveToTag());
        }
        tag.put("Icons", iconList);

        tag.put("Files", root.saveToTag());
        tag.putString("WallpaperName", wallpaperName == null ? "" : wallpaperName);
        tag.putBoolean("WallpaperIsColor", wallpaperIsColor);
        tag.putInt("WallpaperColor", wallpaperColor);
        return tag;
    }

    public synchronized void loadFromTag(CompoundTag tag) {
        desktopIcons.clear();
        if (tag.contains("Icons", Tag.TAG_LIST)) {
            ListTag iconList = tag.getList("Icons", Tag.TAG_COMPOUND);
            for (int i = 0; i < iconList.size(); i++) {
                CompoundTag iconTag = iconList.getCompound(i);
                desktopIcons.add(DesktopIconData.fromTag(iconTag));
            }
        }

        if (tag.contains("Files", Tag.TAG_COMPOUND)) {
            root.children.clear();
            root.loadFromTag(tag.getCompound("Files"));
        }

        wallpaperName = tag.getString("WallpaperName");
        if (wallpaperName != null && wallpaperName.isEmpty()) {
            wallpaperName = null;
        }
        wallpaperIsColor = tag.getBoolean("WallpaperIsColor");
        wallpaperColor = tag.getInt("WallpaperColor");
    }

    public synchronized DeviceState copy() {
        DeviceState copy = new DeviceState();
        copy.desktopIcons.clear();
        for (DesktopIconData icon : desktopIcons) {
            copy.desktopIcons.add(icon.copy());
        }
        copy.root.children.clear();
        copy.root.loadFromTag(this.root.saveToTag());
        copy.wallpaperName = this.wallpaperName;
        copy.wallpaperIsColor = this.wallpaperIsColor;
        copy.wallpaperColor = this.wallpaperColor;
        return copy;
    }

    /* ============================== DTOs =================================== */

    public static class DesktopIconData {
        public String name;
        public int x;
        public int y;

        public DesktopIconData(String name, int x, int y) {
            this.name = name;
            this.x = x;
            this.y = y;
        }

        CompoundTag saveToTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", name);
            tag.putInt("X", x);
            tag.putInt("Y", y);
            return tag;
        }

        static DesktopIconData fromTag(CompoundTag tag) {
            return new DesktopIconData(tag.getString("Name"), tag.getInt("X"), tag.getInt("Y"));
        }

        DesktopIconData copy() {
            return new DesktopIconData(name, x, y);
        }
    }

    public static class VirtualFileSnapshot {
        public final String path;
        public final String name;
        public final boolean directory;
        public final long created;
        public final long modified;
        public final long size;
        public final String content; // only for files when requested

        VirtualFileSnapshot(String path, String name, boolean directory, long created, long modified, long size, String content) {
            this.path = path;
            this.name = name;
            this.directory = directory;
            this.created = created;
            this.modified = modified;
            this.size = size;
            this.content = content;
        }
    }

    /* ============================== Internal Node ========================== */

    private static class VirtualFileNode {
        private String name;
        private final boolean directory;
        private final Map<String, VirtualFileNode> children = new LinkedHashMap<>();
        private String content = "";
        private long createdAt = EPOCH_MILLIS;
        private long modifiedAt = EPOCH_MILLIS;
        private transient VirtualFileNode parent;

        private VirtualFileNode(String name, boolean directory) {
            this.name = name;
            this.directory = directory;
        }

        private void addChild(VirtualFileNode node) {
            node.parent = this;
            children.put(node.name, node);
            touch();
        }

        private void removeChild(String childName) {
            if (children.remove(childName) != null) {
                touch();
            }
        }

        private void loadFromTag(CompoundTag tag) {
            children.clear();
            ListTag childList = tag.getList("Children", Tag.TAG_COMPOUND);
            for (int i = 0; i < childList.size(); i++) {
                CompoundTag childTag = childList.getCompound(i);
                VirtualFileNode child = new VirtualFileNode(childTag.getString("Name"), childTag.getBoolean("Dir"));
                child.createdAt = childTag.getLong("Created");
                child.modifiedAt = childTag.getLong("Modified");
                if (!child.directory) {
                    child.content = childTag.getString("Content");
                } else {
                    child.loadFromTag(childTag);
                }
                addChild(child);
            }
        }
        // Idk, seems fine to me
        private CompoundTag saveToTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", name);
            tag.putBoolean("Dir", directory);
            tag.putLong("Created", createdAt);
            tag.putLong("Modified", modifiedAt);
            if (!directory) {
                tag.putString("Content", content);
            }
            ListTag childList = new ListTag();
            for (VirtualFileNode child : children.values()) {
                childList.add(child.saveToTag());
            }
            tag.put("Children", childList);
            return tag;
        }

        private VirtualFileSnapshot snapshot() {
            return new VirtualFileSnapshot(buildPath(), name, directory, createdAt, modifiedAt, directory ? children.size() : content.length(), null);
        }

        private VirtualFileSnapshot snapshotWithContent() {
            return new VirtualFileSnapshot(buildPath(), name, directory, createdAt, modifiedAt, directory ? children.size() : content.length(), directory ? null : content);
        }

        private String buildPath() {
            if (parent == null || parent.name.isEmpty()) {
                return "/" + name;
            }
            return parent.buildPath() + "/" + name;
        }

        private VirtualFileNode withContent(String value) {
            this.content = value == null ? "" : value;
            touch();
            return this;
        }

        private void touch() {
            modifiedAt = Instant.now().toEpochMilli();
        }
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String[] parts = value.split("[ _-]");
        return Arrays.stream(parts)
                .filter(p -> !p.isEmpty())
                .map(p -> Character.toUpperCase(p.charAt(0)) + p.substring(1))
                .collect(Collectors.joining(" "));
    }
}
