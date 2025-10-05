
package net.chaoscraft.chaoscrafts_device_mod.Core.Util.FileSystem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;

import net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager.AppRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * FilesManager: manages a simple virtual filesystem and wallpaper assets for the PC UI.
 * This cleaned version centralizes logging and avoids System.out.printStackTrace calls.
 * Hmmmm files *saliva dripping noise*
 */
public class FilesManager {
    private static final Logger LOGGER = LogManager.getLogger(FilesManager.class);
    private static FilesManager INST;

    private final File wallpapersDir;

    private final List<String> wallpaperFiles = new ArrayList<>();
    private final Map<String, ResourceLocation> wallpaperPreviews = new HashMap<>();

    private final Map<String, FSNode> fileSystem = new HashMap<>();
    private final List<DesktopIconState> desktopIcons = new ArrayList<>();

    // Wallpaper state
    private DynamicTexture currentWallpaperTexture = null;
    private ResourceLocation currentWallpaperRL = null;
    private String currentWallpaperName = null;
    private boolean currentWallpaperIsColor = false;
    private int currentWallpaperColor = 0xFF000000;
    private boolean wallpaperPrefersDarkText = false;
    private int wallpaperTextColor = 0xFFFFFFFF;

    // GIF support
    private boolean currentWallpaperIsGif = false;
    private List<BufferedImage> currentGifFrames = null;
    private List<Integer> currentGifDelays = null;
    private int currentGifFrameIndex = 0;
    private Timer gifTimer = null;

    private static final int MAX_GIF_FRAMES = 60;

    private FilesManager(File base) {
        this.wallpapersDir = new File(base, "wallpapers");
        this.wallpapersDir.mkdirs();
        loadState();
        recalculateWallpaperContrast();
        scanWallpapers();
        loadCurrentWallpaper();
    }

    public static void init(File base) {
        if (INST == null) INST = new FilesManager(base);
    }

    public static FilesManager getInstance() {
        return INST;
    }

    public static File getPlayerDataDir() {
        File playerDir = new File(Minecraft.getInstance().gameDirectory, "config/chaoscrafts_device_mod/players");
        String playerName = Minecraft.getInstance().player != null ?
                Minecraft.getInstance().player.getGameProfile().getName() : "default";
        File specificPlayerDir = new File(playerDir, playerName);
        specificPlayerDir.mkdirs();
        return specificPlayerDir;
    }

    public static File getWorldDataDir() {
        File worldDir = new File(Minecraft.getInstance().gameDirectory, "saves");
        String worldName = "default";
        try {
            if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getServer() != null) {
                worldName = Minecraft.getInstance().level.getServer().getWorldData().getLevelName();
            } else if (Minecraft.getInstance().getSingleplayerServer() != null) {
                worldName = Minecraft.getInstance().getSingleplayerServer().getWorldData().getLevelName();
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to determine world name", e);
        }
        File specificWorldDir = new File(worldDir, worldName + "/chaoscrafts_device_mod");
        specificWorldDir.mkdirs();
        return specificWorldDir;
    }

    private void loadState() {
        File worldDataFile = new File(getWorldDataDir(), "pc_ui_files.dat");
        if (worldDataFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(worldDataFile))) {
                Object o = ois.readObject();
                if (o instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, FSNode> map = (Map<String, FSNode>) o;
                    fileSystem.putAll(map);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load world file system data", e);
            }
        } else {
            createDefaultFileSystem();
        }

        File playerDesktopFile = new File(getPlayerDataDir(), "desktop_state.json");
        if (playerDesktopFile.exists()) {
            try (Reader reader = new FileReader(playerDesktopFile)) {
                Gson gson = new Gson();
                DesktopState state = gson.fromJson(reader, DesktopState.class);
                if (state != null) {
                    desktopIcons.clear();
                    if (state.icons != null) desktopIcons.addAll(state.icons);
                    if (state.wallpaperName != null) this.currentWallpaperName = state.wallpaperName;
                    this.currentWallpaperIsColor = state.wallpaperIsColor;
                    this.currentWallpaperColor = state.wallpaperColor != 0 ? state.wallpaperColor : this.currentWallpaperColor;
                    if (this.currentWallpaperIsColor) clearCurrentWallpaperTexture();
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load desktop state", e);
            }
        } else {
            createDefaultDesktop();
        }
    }

    public void saveState() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(getWorldDataDir(), "pc_ui_files.dat")))) {
            oos.writeObject(fileSystem);
        } catch (Exception e) {
            LOGGER.warn("Failed to save world file system data", e);
        }
        saveDesktopState();
    }

    private void saveDesktopState() {
        File playerDesktopFile = new File(getPlayerDataDir(), "desktop_state.json");
        try (Writer writer = new FileWriter(playerDesktopFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            DesktopState state = new DesktopState(desktopIcons, currentWallpaperName, currentWallpaperIsColor, currentWallpaperColor);
            gson.toJson(state, writer);
        } catch (Exception e) {
            LOGGER.warn("Failed to save desktop state", e);
        }
    }

    private void createDefaultFileSystem() {
        FSNode root = new FSNode("", true);
        fileSystem.put("/", root);
        FSNode userDir = new FSNode("User", true);
        root.addChild(userDir);
        FSNode documents = new FSNode("Documents", true);
        FSNode pictures = new FSNode("Pictures", true);
        FSNode downloads = new FSNode("Downloads", true);
        userDir.addChild(documents);
        userDir.addChild(pictures);
        userDir.addChild(downloads);
        FSNode readme = new FSNode("README.txt", false);
        readme.setContent("Welcome to your virtual PC!\nThis is a sample text file.");
        documents.addChild(readme);
        FSNode notes = new FSNode("Notes.txt", false);
        notes.setContent("Important notes:\n- Taskbar at the bottom\n- Search functionality\n- File browser with columns");
        documents.addChild(notes);
        FSNode desktop = new FSNode("Desktop", true);
        root.addChild(desktop);
    }

    private void createDefaultDesktop() {
        Set<String> defaultApps = AppRegistry.getInstance().getDesktopApps();
        int x = 150, y = 60;
        for (String appName : defaultApps) {
            if (AppRegistry.getInstance().isInstalled(appName)) {
                desktopIcons.add(new DesktopIconState(appName, x, y));
                x += 80;
                if (x > 700) { x = 150; y += 80; }
            }
        }
        saveDesktopState();
    }

    /**
     * Synchronizes the stored desktop icon list with the desired entries reported by the {@link AppRegistry}.
     * Missing icons are appended with a reasonable grid placement; stale icons are pruned.
     */
    public void updateDesktopIconsFromRegistry() {
        try {
            AppRegistry registry = AppRegistry.getInstance();
            if (registry == null) {
                return;
            }

            Set<String> desired = new LinkedHashSet<>(registry.getDesktopApps());
            if (desired.isEmpty()) {
                if (!desktopIcons.isEmpty()) {
                    desktopIcons.clear();
                    saveDesktopState();
                }
                return;
            }

            // Remove entries no longer present in registry
            desktopIcons.removeIf(icon -> !desired.contains(icon.name));

            Set<String> currentNames = desktopIcons.stream()
                    .map(icon -> icon.name)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            // Append missing icons with simple grid-based positioning.
            int baseX = 150;
            int baseY = 60;
            int step = 80;
            for (String app : desired) {
                if (currentNames.contains(app)) {
                    continue;
                }

                int index = desktopIcons.size();
                int columns = Math.max(1, (700 - baseX) / step);
                int x = baseX + (index % columns) * step;
                int y = baseY + (index / columns) * step;
                desktopIcons.add(new DesktopIconState(app, x, y));
            }

            saveDesktopState();
        } catch (Exception e) {
            LOGGER.warn("Failed to synchronize desktop icons with registry", e);
        }
    }

    public List<DesktopIconState> getDesktopIcons() {
        return new ArrayList<>(desktopIcons);
    }

    public boolean hasDesktopIcon(String name) {
        for (DesktopIconState icon : desktopIcons) if (icon.name.equals(name)) return true;
        return false;
    }

    public void addDesktopIcon(String name, int x, int y) { desktopIcons.add(new DesktopIconState(name, x, y)); saveDesktopState(); }
    public void removeDesktopIcon(String name) { desktopIcons.removeIf(icon -> icon.name.equals(name)); saveDesktopState(); }

    public boolean createFile(String parentPath, String name, boolean isDirectory) {
        FSNode parent = getNode(parentPath); if (parent == null || !parent.isDirectory) return false;
        if (parent.children.containsKey(name)) return false;
        FSNode newNode = new FSNode(name, isDirectory);
        parent.addChild(newNode);
        fileSystem.put(parentPath + "/" + name, newNode);
        saveState();
        return true;
    }

    public boolean createFileWithContent(String parentPath, String name, String content) {
        FSNode parent = getNode(parentPath); if (parent == null || !parent.isDirectory) return false;
        if (parent.children.containsKey(name)) return false;
        FSNode newNode = new FSNode(name, false);
        newNode.setContent(content);
        parent.addChild(newNode);
        fileSystem.put(parentPath + "/" + name, newNode);
        saveState();
        return true;
    }

    public void updateDesktopIconPosition(String name, int x, int y) {
        for (DesktopIconState icon : desktopIcons) if (icon.name.equals(name)) { icon.x = x; icon.y = y; break; }
        saveDesktopState();
    }

    public FSNode getNode(String path) { return fileSystem.get(path); }

    public boolean deleteNode(String path) {
        FSNode node = getNode(path); if (node == null) return false;
        int lastSlash = path.lastIndexOf('/'); if (lastSlash == -1) return false;
        String parentPath = path.substring(0, lastSlash); String name = path.substring(lastSlash + 1);
        FSNode parent = getNode(parentPath); if (parent == null) return false;
        parent.removeChild(name); fileSystem.remove(path);
        if (parentPath.equals("/Desktop")) removeDesktopIcon(name);
        saveState(); return true;
    }

    public boolean renameNode(String path, String newName) {
        FSNode node = getNode(path); if (node == null) return false;
        int lastSlash = path.lastIndexOf('/'); if (lastSlash == -1) return false;
        String parentPath = path.substring(0, lastSlash); String oldName = path.substring(lastSlash + 1);
        FSNode parent = getNode(parentPath); if (parent == null) return false;
        parent.removeChild(oldName); node.name = newName; parent.addChild(node);
        fileSystem.remove(path); fileSystem.put(parentPath + "/" + newName, node);
        if (parentPath.equals("/Desktop")) {
            for (DesktopIconState icon : desktopIcons) if (icon.name.equals(oldName)) { icon.name = newName; break; }
            saveDesktopState();
        }
        saveState(); return true;
    }

    public List<FSNode> listDirectory(String path) { FSNode node = getNode(path); if (node == null || !node.isDirectory) return new ArrayList<>(); return new ArrayList<>(node.children.values()); }

    private void scanWallpapers() {
        wallpaperFiles.clear();
        File[] files = wallpapersDir.listFiles((d, n) -> {
            String low = n.toLowerCase();
            return low.endsWith(".png") || low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".gif");
        });
        if (files == null) return;
        for (File f : files) wallpaperFiles.add(f.getName());
    }

    public File saveImageAsWallpaper(BufferedImage img, String baseName) throws IOException {
        String name = baseName + "_" + System.currentTimeMillis() + ".png";
        File out = new File(wallpapersDir, name);
        ImageIO.write(img, "PNG", out);
        scanWallpapers();
        return out;
    }

    public File getWallpaperFile(String name) { if (name == null) return null; File f = new File(wallpapersDir, name); return f.exists() ? f : null; }
    public File getWallpapersDir() { return wallpapersDir; }

    public void setCurrentWallpaperName(String name) {
        currentWallpaperIsColor = false;
        clearCurrentWallpaperTexture();
        if (name == null) {
            LOGGER.info("Clearing current wallpaper (file)");
            currentWallpaperName = null; currentWallpaperIsGif = false; clearCurrentWallpaperTexture();
        } else {
            File f = new File(wallpapersDir, name);
            if (f.exists()) {
                LOGGER.info("Setting wallpaper to file: {} (exists=true)", name);
                currentWallpaperName = name;
                String low = name.toLowerCase(Locale.ROOT);
                if (low.endsWith(".gif")) {
                    currentWallpaperIsGif = true; loadGifAndStart(f);
                } else {
                    currentWallpaperIsGif = false; loadCurrentWallpaper();
                }
            } else {
                LOGGER.warn("Requested wallpaper file does not exist: {} (path={})", name, f.getAbsolutePath());
            }
        }
        recalculateWallpaperContrast();
        saveState();
    }

    public void setCurrentWallpaperColor(int argb) {
        LOGGER.info("Setting solid color wallpaper: {}", String.format("#%06X", argb & 0xFFFFFF));
        currentWallpaperIsColor = true; currentWallpaperColor = argb; currentWallpaperName = null; currentWallpaperIsGif = false; clearCurrentWallpaperTexture();
        updateWallpaperBrightnessFromColor(argb);
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    synchronized (wallpaperPreviews) {
                        for (ResourceLocation rl : wallpaperPreviews.values()) {
                            try { mc.getTextureManager().release(rl); } catch (Exception ignored) {}
                        }
                        wallpaperPreviews.clear();
                    }
                });
            } else {
                synchronized (wallpaperPreviews) { wallpaperPreviews.clear(); }
            }
        } catch (Exception e) { LOGGER.debug("Failed clearing previews", e); synchronized (wallpaperPreviews) { wallpaperPreviews.clear(); } }
        saveState();
    }

    public boolean isCurrentWallpaperColor() { return currentWallpaperIsColor; }
    public int getCurrentWallpaperColor() { return currentWallpaperColor; }
    public int getCurrentWallpaperTextColor() { return wallpaperTextColor; }
    public boolean isCurrentWallpaperBright() { return wallpaperPrefersDarkText; }

    private void recalculateWallpaperContrast() {
        if (currentWallpaperIsColor) {
            updateWallpaperBrightnessFromColor(currentWallpaperColor);
            return;
        }
        if (currentWallpaperName != null) {
            File file = new File(wallpapersDir, currentWallpaperName);
            if (file.exists()) {
                updateWallpaperBrightnessFromFile(file);
                return;
            }
        }
        applyWallpaperLuminance(0.15f);
    }

    private void updateWallpaperBrightnessFromColor(int argb) {
        applyWallpaperLuminance(calculateRelativeLuminance(argb));
    }

    private void updateWallpaperBrightnessFromFile(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image != null) {
                updateWallpaperBrightnessFromImage(image);
                return;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to sample wallpaper brightness from file {}", file, e);
        }
        applyWallpaperLuminance(0.15f);
    }

    private void updateWallpaperBrightnessFromImage(BufferedImage image) {
        if (image == null) {
            applyWallpaperLuminance(0.15f);
            return;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            applyWallpaperLuminance(0.15f);
            return;
        }
        int stepX = Math.max(1, width / 64);
        int stepY = Math.max(1, height / 64);
        double total = 0.0;
        int count = 0;
        for (int y = 0; y < height; y += stepY) {
            for (int x = 0; x < width; x += stepX) {
                int rgb = image.getRGB(x, y);
                total += calculateRelativeLuminance(rgb);
                count++;
            }
        }
        float luminance = count > 0 ? (float)(total / count) : 0.15f;
        applyWallpaperLuminance(luminance);
    }

    private void applyWallpaperLuminance(float luminance) {
        luminance = Math.max(0f, Math.min(1f, luminance));
        this.wallpaperPrefersDarkText = luminance > 0.6f;
        this.wallpaperTextColor = wallpaperPrefersDarkText ? 0xFF000000 : 0xFFFFFFFF;
    }

    private static float calculateRelativeLuminance(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (float)((0.299 * r + 0.587 * g + 0.114 * b) / 255.0);
    }

    private void clearCurrentWallpaperTexture() {
        if (gifTimer != null) { gifTimer.cancel(); gifTimer = null; }
        currentGifFrames = null; currentGifDelays = null; currentGifFrameIndex = 0;
        if (currentWallpaperTexture != null) {
            try { Minecraft.getInstance().getTextureManager().release(currentWallpaperRL); } catch (Exception ignored) {}
            try { currentWallpaperTexture.close(); } catch (Exception ignored) {}
            currentWallpaperTexture = null; currentWallpaperRL = null;
        }
    }

    private byte[] bufferedImageToPngBytes(BufferedImage img) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        }
    }

    private void registerImageBytesSafely(final byte[] pngBytes, final String id) {
        if (pngBytes == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                try {
                    NativeImage n = null;
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(pngBytes)) { n = NativeImage.read(bais); }
                    if (n == null) return;
                    if (currentWallpaperRL != null) { try { mc.getTextureManager().release(currentWallpaperRL); } catch (Exception ignored) {} }
                    if (currentWallpaperTexture != null) { try { currentWallpaperTexture.close(); } catch (Exception ignored) {} }
                    currentWallpaperTexture = new DynamicTexture(n);
                    currentWallpaperRL = mc.getTextureManager().register(id, currentWallpaperTexture);
                    LOGGER.info("Registered wallpaper texture id={} resource={}", id, currentWallpaperRL);
                } catch (Throwable t) {
                    LOGGER.warn("Failed to register wallpaper bytes safely", t);
                    currentWallpaperTexture = null; currentWallpaperRL = null;
                }
            });
        } else {
            try (ByteArrayInputStream bais = new ByteArrayInputStream(pngBytes)) {
                NativeImage n = NativeImage.read(bais);
                if (n != null) {
                    if (currentWallpaperTexture != null) try { currentWallpaperTexture.close(); } catch (Exception ignored) {}
                    currentWallpaperTexture = new DynamicTexture(n);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to register wallpaper bytes synchronously", e);
                currentWallpaperTexture = null; currentWallpaperRL = null;
            }
        }
    }

    private void loadCurrentWallpaper() {
        if (currentWallpaperIsColor) return;
        if (currentWallpaperName == null) return;
        File file = new File(wallpapersDir, currentWallpaperName);
        if (!file.exists()) return;
        if (currentWallpaperIsGif) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                try {
                    if (currentWallpaperRL != null) { try { mc.getTextureManager().release(currentWallpaperRL); } catch (Exception ignored) {} currentWallpaperRL = null; }
                    if (currentWallpaperTexture != null) { try { currentWallpaperTexture.close(); } catch (Exception ignored) {} currentWallpaperTexture = null; }
                    byte[] bytes = null;
                    try (FileInputStream fis = new FileInputStream(file)) { bytes = fis.readAllBytes(); }
                    if (bytes != null) {
                        String safeName = file.getName().replaceAll("[^a-zA-Z0-9_.-]", "_").toLowerCase(Locale.ROOT);
                        String id = "pc_ui_wallpaper_current_" + safeName + "_" + file.lastModified();
                        registerImageBytesSafely(bytes, id);
                    }
                } catch (Throwable t) {
                    LOGGER.warn("Failed to load current wallpaper", t);
                    try { if (currentWallpaperTexture != null) currentWallpaperTexture.close(); } catch (Exception ignored) {}
                    currentWallpaperTexture = null; currentWallpaperRL = null;
                }
            });
        } else {
            try (FileInputStream fis = new FileInputStream(file)) {
                NativeImage img = NativeImage.read(fis);
                if (currentWallpaperTexture != null) try { currentWallpaperTexture.close(); } catch (Exception ignored) {}
                currentWallpaperTexture = new DynamicTexture(img);
            } catch (Exception e) {
                LOGGER.warn("Failed to load current wallpaper synchronously", e);
                currentWallpaperTexture = null; currentWallpaperRL = null;
            }
        }
    }

    public ResourceLocation getCurrentWallpaperResource() { return currentWallpaperIsColor ? null : currentWallpaperRL; }

    public ResourceLocation getWallpaperPreviewResource(String name) {
        if (name == null) return null;
        synchronized (wallpaperPreviews) { if (wallpaperPreviews.containsKey(name)) return wallpaperPreviews.get(name); }
        File f = new File(wallpapersDir, name); if (!f.exists()) return null;
        try {
            String safe = name.replaceAll("[^a-zA-Z0-9_.-]", "_").toLowerCase(Locale.ROOT);
            String id = "pc_ui_wallpaper_preview_" + safe + "_" + f.lastModified();
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                byte[] fileBytes = null;
                if (name.toLowerCase(Locale.ROOT).endsWith(".gif")) {
                    try (ImageInputStream iis = ImageIO.createImageInputStream(f)) {
                        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                        if (readers.hasNext()) {
                            ImageReader reader = readers.next(); reader.setInput(iis);
                            BufferedImage frame = reader.read(0); fileBytes = bufferedImageToPngBytes(frame);
                        }
                    } catch (Exception ignored) {}
                } else {
                    try (FileInputStream fis = new FileInputStream(f)) { fileBytes = fis.readAllBytes(); } catch (Exception ignored) {}
                }
                final byte[] registerBytes = fileBytes;
                mc.execute(() -> {
                    try {
                        if (registerBytes == null) return;
                        synchronized (wallpaperPreviews) { if (wallpaperPreviews.containsKey(name)) return; }
                        NativeImage ni = null; try (ByteArrayInputStream bais = new ByteArrayInputStream(registerBytes)) { ni = NativeImage.read(bais); }
                        if (ni == null) return; DynamicTexture dt = new DynamicTexture(ni);
                        ResourceLocation rl = mc.getTextureManager().register(id, dt);
                        synchronized (wallpaperPreviews) { wallpaperPreviews.put(name, rl); }
                    } catch (Exception e) { LOGGER.warn("Failed to register wallpaper preview", e); }
                });
            } else {
                try (FileInputStream fis = new FileInputStream(f)) {
                    byte[] bytes = fis.readAllBytes(); NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes)); DynamicTexture dt = new DynamicTexture(img);
                    ResourceLocation rl = Minecraft.getInstance().getTextureManager().register(id, dt);
                    synchronized (wallpaperPreviews) { wallpaperPreviews.put(name, rl); }
                    return rl;
                } catch (Exception e) { LOGGER.warn("Failed to register wallpaper preview synchronously", e); return null; }
            }
        } catch (Exception e) { LOGGER.warn("Failed to prepare wallpaper preview", e); return null; }
        synchronized (wallpaperPreviews) { return wallpaperPreviews.get(name); }
    }

    private void loadGifAndStart(File gifFile) {
        clearCurrentWallpaperTexture();
        try (ImageInputStream iis = ImageIO.createImageInputStream(gifFile)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis); if (!readers.hasNext()) return; ImageReader reader = readers.next();
            reader.setInput(iis); int numFrames = reader.getNumImages(true);
            List<BufferedImage> frames = new ArrayList<>(); List<Integer> delays = new ArrayList<>();
            int limit = Math.min(numFrames, MAX_GIF_FRAMES);
            for (int i = 0; i < limit; i++) {
                BufferedImage frame = reader.read(i); frames.add(frame);
                int delay = 100; try { IIOMetadata meta = reader.getImageMetadata(i); int parsed = parseGifDelay(meta); if (parsed > 0) delay = parsed; } catch (Exception ignored) {}
                delays.add(delay);
            }
            if (frames.isEmpty()) return; this.currentGifFrames = frames; this.currentGifDelays = delays; this.currentGifFrameIndex = 0;
            BufferedImage first = frames.get(0); byte[] bytes = bufferedImageToPngBytes(first);
            String safeName = gifFile.getName().replaceAll("[^a-zA-Z0-9_.-]", "_").toLowerCase(Locale.ROOT);
            String id = "pc_ui_wallpaper_current_" + safeName + "_" + gifFile.lastModified(); registerImageBytesSafely(bytes, id + "_0");
            scheduleNextGifFrame();
        } catch (Exception e) { LOGGER.warn("Failed to load GIF wallpaper", e); currentGifFrames = null; currentGifDelays = null; currentWallpaperIsGif = false; }
    }

    private int parseGifDelay(IIOMetadata meta) {
        if (meta == null) return -1; try {
            Node root = meta.getAsTree("javax_imageio_gif_image_1.0"); if (root == null) return -1;
            for (int i = 0; i < root.getChildNodes().getLength(); i++) {
                Node node = root.getChildNodes().item(i); if (node.getNodeName().equals("GraphicControlExtension")) {
                    NamedNodeMap attrs = node.getAttributes(); Node delayNode = attrs.getNamedItem("delayTime"); if (delayNode != null) {
                        try { int delayCentis = Integer.parseInt(delayNode.getNodeValue()); return Math.max(10, delayCentis * 10); } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private void scheduleNextGifFrame() {
        if (currentGifFrames == null || currentGifDelays == null || currentGifFrames.isEmpty()) return;
        if (gifTimer != null) gifTimer.cancel(); gifTimer = new Timer(true);
        int delay = currentGifDelays.get(currentGifFrameIndex);
        gifTimer.schedule(new TimerTask() { @Override public void run() { advanceGifFrame(); } }, delay);
    }

    private void advanceGifFrame() {
        try {
            currentGifFrameIndex = (currentGifFrameIndex + 1) % currentGifFrames.size(); BufferedImage bi = currentGifFrames.get(currentGifFrameIndex);
            byte[] bytes = bufferedImageToPngBytes(bi); final String safeName = (currentWallpaperName != null ? currentWallpaperName : "gif").replaceAll("[^a-zA-Z0-9_.-]", "_").toLowerCase(Locale.ROOT);
            final String id = "pc_ui_wallpaper_current_" + safeName + "_" + System.currentTimeMillis() + "_" + currentGifFrameIndex; registerImageBytesSafely(bytes, id);
            if (currentGifDelays != null && !currentGifDelays.isEmpty()) {
                int nextDelay = currentGifDelays.get(currentGifFrameIndex);
                if (gifTimer != null) gifTimer.schedule(new TimerTask() { @Override public void run() { advanceGifFrame(); } }, Math.max(10, nextDelay));
            }
        } catch (Exception e) { LOGGER.warn("Failed advancing GIF frame", e); clearCurrentWallpaperTexture(); }
    }

    // Desktop state classes
    public static class DesktopState { public List<DesktopIconState> icons; public String wallpaperName; public boolean wallpaperIsColor; public int wallpaperColor;
        public DesktopState(List<DesktopIconState> icons) { this.icons = icons; }
        public DesktopState(List<DesktopIconState> icons, String wallpaperName, boolean wallpaperIsColor, int wallpaperColor) { this.icons = icons; this.wallpaperName = wallpaperName; this.wallpaperIsColor = wallpaperIsColor; this.wallpaperColor = wallpaperColor; }
    }

    public static class DesktopIconState { public String name; public int x; public int y; public DesktopIconState(String name, int x, int y) { this.name = name; this.x = x; this.y = y; } }

    // Minimal FSNode implementation used by the manager
    public static class FSNode implements Serializable {
        public String name; public boolean isDirectory; public Map<String, FSNode> children = new LinkedHashMap<>(); private String content = null;
        public FSNode(String name, boolean isDirectory) { this.name = name; this.isDirectory = isDirectory; }
        public void addChild(FSNode node) { if (isDirectory && node != null) children.put(node.name, node); }
        public void removeChild(String name) { if (isDirectory) children.remove(name); }
        public void setContent(String content) { this.content = content; }
        public String getContent() { return content; }
    }

    public String getCurrentWallpaperName() { return currentWallpaperName; }
    public List<String> listWallpapers() { synchronized (wallpaperFiles) { return new ArrayList<>(wallpaperFiles); } }
}
