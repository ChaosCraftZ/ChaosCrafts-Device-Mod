// FilesManager.java (partial rewrite focusing on desktop operations)
package net.chaoscraft.chaoscrafts_device_mod.client.fs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.NativeImage;
import net.chaoscraft.chaoscrafts_device_mod.client.app.AppFactory;
import net.chaoscraft.chaoscrafts_device_mod.client.app.AppRegistry;
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
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;

public class FilesManager {
    private static FilesManager INST;
    private final File baseDir;
    private final File wallpapersDir;
    private final File dataFile;
    private final File desktopStateFile;
    private final List<String> wallpaperFiles = new ArrayList<>();
    // Cache preview resource locations for wallpapers
    private final Map<String, ResourceLocation> wallpaperPreviews = new HashMap<>();

    // File system state
    private final Map<String, FSNode> fileSystem = new HashMap<>();
    private final List<DesktopIconState> desktopIcons = new ArrayList<>();

    // Wallpaper state
    private DynamicTexture currentWallpaperTexture = null;
    private ResourceLocation currentWallpaperRL = null;
    private String currentWallpaperName = null;

    // New: support solid color wallpaper
    private boolean currentWallpaperIsColor = false;
    private int currentWallpaperColor = 0xFF000000; // ARGB

    // New: basic GIF support
    private boolean currentWallpaperIsGif = false;
    private List<BufferedImage> currentGifFrames = null;
    private List<Integer> currentGifDelays = null; // milliseconds
    private int currentGifFrameIndex = 0;
    private Timer gifTimer = null;

    // New constants
    private static final int MAX_GIF_FRAMES = 60; // limit frames to avoid excessive memory use

    private FilesManager(File base) {
        this.baseDir = base;
        this.wallpapersDir = new File(base, "wallpapers");
        this.wallpapersDir.mkdirs();
        this.dataFile = new File(base, "pc_ui_files.dat");
        this.desktopStateFile = new File(base, "desktop_state.json");
        loadState();
        scanWallpapers();
        loadCurrentWallpaper();
    }

    public static void init(File base) {
        if (INST == null) INST = new FilesManager(base);
    }

    public static FilesManager getInstance() {
        return INST;
    }

    // Get player-specific directory
    public static File getPlayerDataDir() {
        File playerDir = new File(Minecraft.getInstance().gameDirectory, "config/chaoscrafts_device_mod/players");
        String playerName = Minecraft.getInstance().player != null ?
                Minecraft.getInstance().player.getGameProfile().getName() : "default";
        File specificPlayerDir = new File(playerDir, playerName);
        specificPlayerDir.mkdirs();
        return specificPlayerDir;
    }

    // Get world-specific directory
    public static File getWorldDataDir() {
        File worldDir = new File(Minecraft.getInstance().gameDirectory, "saves");
        String worldName = "default";

        // Check if we're in a world and the server is available
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getServer() != null) {
            worldName = Minecraft.getInstance().level.getServer().getWorldData().getLevelName();
        } else if (Minecraft.getInstance().getSingleplayerServer() != null) {
            // Fallback to singleplayer server if available
            worldName = Minecraft.getInstance().getSingleplayerServer().getWorldData().getLevelName();
        }

        File specificWorldDir = new File(worldDir, worldName + "/chaoscrafts_device_mod");
        specificWorldDir.mkdirs();
        return specificWorldDir;
    }

    // ... rest of the class ...

    private void loadState() {
        // Load file system from world data
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
                e.printStackTrace();
            }
        } else {
            // Create default file system
            createDefaultFileSystem();
        }

        // Load desktop state from player data
        File playerDesktopFile = new File(getPlayerDataDir(), "desktop_state.json");
        if (playerDesktopFile.exists()) {
            try (Reader reader = new FileReader(playerDesktopFile)) {
                Gson gson = new Gson();
                DesktopState state = gson.fromJson(reader, DesktopState.class);
                if (state != null) {
                    desktopIcons.clear();
                    if (state.icons != null) desktopIcons.addAll(state.icons);

                    // Load wallpaper state from DesktopState (new fields)
                    if (state.wallpaperName != null) {
                        this.currentWallpaperName = state.wallpaperName;
                    }
                    this.currentWallpaperIsColor = state.wallpaperIsColor;
                    this.currentWallpaperColor = state.wallpaperColor != 0 ? state.wallpaperColor : this.currentWallpaperColor;
                    // If wallpaper is a color, ensure we don't try to load a file.
                    if (this.currentWallpaperIsColor) {
                        // Clear any previously loaded texture
                        clearCurrentWallpaperTexture();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // Create default desktop icons
            createDefaultDesktop();
        }
    }

    public void saveState() {
        // Save file system to world data
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(getWorldDataDir(), "pc_ui_files.dat")))) {
            oos.writeObject(fileSystem);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Save desktop state to player data
        saveDesktopState();
    }

    private void saveDesktopState() {
        File playerDesktopFile = new File(getPlayerDataDir(), "desktop_state.json");
        try (Writer writer = new FileWriter(playerDesktopFile)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            DesktopState state = new DesktopState(desktopIcons, currentWallpaperName, currentWallpaperIsColor, currentWallpaperColor);
            gson.toJson(state, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ... rest of the class remains mostly the same ...


    private void createDefaultFileSystem() {
        // Create root directory
        FSNode root = new FSNode("", true);
        fileSystem.put("/", root);

        // Create user directory
        FSNode userDir = new FSNode("User", true);
        root.addChild(userDir);

        // Create Documents, Pictures, etc.
        FSNode documents = new FSNode("Documents", true);
        FSNode pictures = new FSNode("Pictures", true);
        FSNode downloads = new FSNode("Downloads", true);

        userDir.addChild(documents);
        userDir.addChild(pictures);
        userDir.addChild(downloads);

        // Create some sample files
        FSNode readme = new FSNode("README.txt", false);
        readme.setContent("Welcome to your virtual PC!\nThis is a sample text file.");
        documents.addChild(readme);

        FSNode notes = new FSNode("Notes.txt", false);
        notes.setContent("Important notes:\n- Taskbar at the bottom\n- Search functionality\n- File browser with columns");
        documents.addChild(notes);

        // Create Desktop directory
        FSNode desktop = new FSNode("Desktop", true);
        root.addChild(desktop);
    }

    // In FilesManager.java, update the createDefaultDesktop method:

    private void createDefaultDesktop() {
        // Get default apps from AppFactory
        List<String> defaultApps = AppFactory.getDefaultApps();

        int x = 150;
        int y = 60;

        for (String appName : defaultApps) {
            desktopIcons.add(new DesktopIconState(appName, x, y));
            x += 64; // Space icons horizontally

            // If we reach the edge of the screen, move to next row
            if (x > 700) {
                x = 150;
                y += 80;
            }
        }

        saveDesktopState();
    }

    public List<DesktopIconState> getDesktopIcons() {
        return new ArrayList<>(desktopIcons);
    }

    public boolean hasDesktopIcon(String name) {
        for (DesktopIconState icon : desktopIcons) {
            if (icon.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public void addDesktopIcon(String name, int x, int y) {
        desktopIcons.add(new DesktopIconState(name, x, y));
        saveDesktopState();
    }

    public void removeDesktopIcon(String name) {
        desktopIcons.removeIf(icon -> icon.name.equals(name));
        saveDesktopState();
    }


    public boolean createFile(String parentPath, String name, boolean isDirectory) {
        FSNode parent = getNode(parentPath);
        if (parent == null || !parent.isDirectory) return false;

        // Check if name already exists
        if (parent.children.containsKey(name)) {
            return false;
        }

        FSNode newNode = new FSNode(name, isDirectory);
        parent.addChild(newNode);
        fileSystem.put(parentPath + "/" + name, newNode);
        saveState();
        return true;
    }

    public boolean createFileWithContent(String parentPath, String name, String content) {
        FSNode parent = getNode(parentPath);
        if (parent == null || !parent.isDirectory) return false;

        // Check if name already exists
        if (parent.children.containsKey(name)) {
            return false;
        }

        FSNode newNode = new FSNode(name, false);
        newNode.setContent(content);
        parent.addChild(newNode);
        fileSystem.put(parentPath + "/" + name, newNode);
        saveState();
        return true;
    }

    public void updateDesktopIconPosition(String name, int x, int y) {
        for (DesktopIconState icon : desktopIcons) {
            if (icon.name.equals(name)) {
                icon.x = x;
                icon.y = y;
                break;
            }
        }
        saveDesktopState();
    }

    // File system operations
    public FSNode getNode(String path) {
        return fileSystem.get(path);
    }




    public boolean deleteNode(String path) {
        FSNode node = getNode(path);
        if (node == null) return false;

        // Find parent
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == -1) return false; // Can't delete root

        String parentPath = path.substring(0, lastSlash);
        String name = path.substring(lastSlash + 1);

        FSNode parent = getNode(parentPath);
        if (parent == null) return false;

        parent.removeChild(name);
        fileSystem.remove(path);

        // If deleting from desktop, remove the desktop icon
        if (parentPath.equals("/Desktop")) {
            removeDesktopIcon(name);
        }

        saveState();
        return true;
    }

    public boolean renameNode(String path, String newName) {
        FSNode node = getNode(path);
        if (node == null) return false;

        // Find parent
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == -1) return false; // Can't rename root

        String parentPath = path.substring(0, lastSlash);
        String oldName = path.substring(lastSlash + 1);

        FSNode parent = getNode(parentPath);
        if (parent == null) return false;

        // Remove from parent and add with new name
        parent.removeChild(oldName);
        node.name = newName;
        parent.addChild(node);

        // Update file system map
        fileSystem.remove(path);
        fileSystem.put(parentPath + "/" + newName, node);

        // If renaming on desktop, update the desktop icon
        if (parentPath.equals("/Desktop")) {
            for (DesktopIconState icon : desktopIcons) {
                if (icon.name.equals(oldName)) {
                    icon.name = newName;
                    break;
                }
            }
            saveDesktopState();
        }

        saveState();
        return true;
    }

    public List<FSNode> listDirectory(String path) {
        FSNode node = getNode(path);
        if (node == null || !node.isDirectory) return new ArrayList<>();
        return new ArrayList<>(node.children.values());
    }

    // Wallpaper methods
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

    public File getWallpaperFile(String name) {
        if (name == null) return null;
        File f = new File(wallpapersDir, name);
        return f.exists() ? f : null;
    }

    public File getWallpapersDir() { return wallpapersDir; }

    /**
     * Choose a wallpaper by filename. Passing null clears the wallpaper.
     */
    public void setCurrentWallpaperName(String name) {
        // Clear any color mode
        currentWallpaperIsColor = false;

        // Ensure any previous wallpaper resources/timers (especially GIFs) are cleared
        // before setting a new wallpaper so old GIF timers don't keep re-registering textures.
        clearCurrentWallpaperTexture();

        if (name == null) {
            System.out.println("[PC-UI] Clearing current wallpaper (file)");
            currentWallpaperName = null;
            currentWallpaperIsGif = false;
            clearCurrentWallpaperTexture();
        } else {
            File f = new File(wallpapersDir, name);
            if (f.exists()) {
                System.out.println("[PC-UI] Setting wallpaper to file: " + name + " (exists=true)");
                currentWallpaperName = name;
                // Detect GIF
                String low = name.toLowerCase(Locale.ROOT);
                if (low.endsWith(".gif")) {
                    currentWallpaperIsGif = true;
                    // Directly call GIF loader — it will handle client-thread registration as needed
                    loadGifAndStart(f);
                } else {
                    currentWallpaperIsGif = false;
                    // Direct call; loadCurrentWallpaper will schedule registration if needed
                    loadCurrentWallpaper();
                    // Do NOT assign the preview resource to currentWallpaperRL here. The preview resources
                    // are cached separately and may be released later; assigning them to the "current" slot
                    // leads to the TextureManager releasing preview textures and leaving the cache with
                    // stale ResourceLocations. Rendering will fall back to preview resources when no
                    // current texture is yet registered (see FilesManager.getWallpaperPreviewResource).
                }
            } else {
                System.out.println("[PC-UI] Requested wallpaper file does not exist: " + name + " (path=" + f.getAbsolutePath() + ")");
            }
        }
        saveState();
    }

    /**
     * Choose a solid color wallpaper (ARGB). This will clear any file-based wallpaper.
     */
    public void setCurrentWallpaperColor(int argb) {
        System.out.println("[PC-UI] Setting solid color wallpaper: " + String.format("#%06X", argb & 0xFFFFFF));
        currentWallpaperIsColor = true;
        currentWallpaperColor = argb;
        currentWallpaperName = null;
        currentWallpaperIsGif = false;
        clearCurrentWallpaperTexture();

        // Release and clear any cached preview textures so the UI won't try to use them
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
        } catch (Exception ignored) { synchronized (wallpaperPreviews) { wallpaperPreviews.clear(); } }

        saveState();
    }

    public boolean isCurrentWallpaperColor() { return currentWallpaperIsColor; }
    public int getCurrentWallpaperColor() { return currentWallpaperColor; }

    private void clearCurrentWallpaperTexture() {
        // Stop GIF timer if running
        if (gifTimer != null) {
            gifTimer.cancel();
            gifTimer = null;
        }
        currentGifFrames = null;
        currentGifDelays = null;
        currentGifFrameIndex = 0;

        if (currentWallpaperTexture != null) {
            try {
                Minecraft.getInstance().getTextureManager().release(currentWallpaperRL);
            } catch (Exception ignored) {}
            currentWallpaperTexture.close();
            currentWallpaperTexture = null;
            currentWallpaperRL = null;
        }
    }

    // New helper: convert BufferedImage to PNG bytes
    private byte[] bufferedImageToPngBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    // New helper: register image bytes safely on the client thread. This avoids creating NativeImage on background threads
    private void registerImageBytesSafely(final byte[] pngBytes, final String id) {
        if (pngBytes == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                try {
                    // Create NativeImage on client thread to avoid cross-thread ownership issues
                    NativeImage n = null;
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(pngBytes)) {
                        n = NativeImage.read(bais);
                    }
                    if (n == null) return;
                    // quick validation
                    int ww = n.getWidth();

                    // release previous
                    if (currentWallpaperRL != null) {
                        try { mc.getTextureManager().release(currentWallpaperRL); } catch (Exception ignored) {}
                    }
                    if (currentWallpaperTexture != null) {
                        try { currentWallpaperTexture.close(); } catch (Exception ignored) {}
                    }

                    currentWallpaperTexture = new DynamicTexture(n);
                    currentWallpaperRL = mc.getTextureManager().register(id, currentWallpaperTexture);
                    System.out.println("[PC-UI] Registered wallpaper texture with id=" + id + " resource=" + currentWallpaperRL);
                } catch (Throwable t) {
                    t.printStackTrace();
                    try {
                        // no direct n reference here if failed earlier
                    } catch (Exception ignored) {}
                    currentWallpaperTexture = null;
                    currentWallpaperRL = null;
                }
            });
        } else {
            // Headless: create NativeImage synchronously
            try (ByteArrayInputStream bais = new ByteArrayInputStream(pngBytes)) {
                NativeImage n = NativeImage.read(bais);
                if (n != null) {
                    if (currentWallpaperTexture != null) try { currentWallpaperTexture.close(); } catch (Exception ignored) {}
                    currentWallpaperTexture = new DynamicTexture(n);
                }
            } catch (Exception e) {
                e.printStackTrace();
                currentWallpaperTexture = null;
                currentWallpaperRL = null;
            }
        }
    }

    private void loadCurrentWallpaper() {
        // If solid color mode, nothing to load
        if (currentWallpaperIsColor) return;

        if (currentWallpaperName == null) return;

        File file = new File(wallpapersDir, currentWallpaperName);
        if (!file.exists()) return;

        // If GIF, handled elsewhere
        if (currentWallpaperIsGif) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                try {
                    // Release previous
                    if (currentWallpaperRL != null) {
                        try { mc.getTextureManager().release(currentWallpaperRL); } catch (Exception ignored) {}
                        currentWallpaperRL = null;
                    }
                    if (currentWallpaperTexture != null) {
                        try { currentWallpaperTexture.close(); } catch (Exception ignored) {}
                        currentWallpaperTexture = null;
                    }

                    // Read file bytes and register on client thread to create NativeImage safely
                    byte[] bytes = null;
                    try (FileInputStream fis = new FileInputStream(file)) {
                        bytes = fis.readAllBytes();
                    }
                    if (bytes != null) {
                        String safeName = file.getName().replaceAll("[^a-zA-Z0-9_.-]", "_").toLowerCase(Locale.ROOT);
                        String id = "pc_ui_wallpaper_current_" + safeName + "_" + file.lastModified();
                        registerImageBytesSafely(bytes, id);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                    try { if (currentWallpaperTexture != null) currentWallpaperTexture.close(); } catch (Exception ignored) {}
                    currentWallpaperTexture = null;
                    currentWallpaperRL = null;
                }
            });
        } else {
            // Fallback for headless/testing: do synchronous load
            try (FileInputStream fis = new FileInputStream(file)) {
                NativeImage img = NativeImage.read(fis);
                if (currentWallpaperTexture != null) try { currentWallpaperTexture.close(); } catch (Exception ignored) {}
                currentWallpaperTexture = new DynamicTexture(img);
            } catch (Exception e) {
                e.printStackTrace();
                currentWallpaperTexture = null;
                currentWallpaperRL = null;
            }
        }
    }

    /**
     * Returns the ResourceLocation for the currently registered wallpaper texture,
     * or null if none (or if a solid color wallpaper is active).
     */
    public ResourceLocation getCurrentWallpaperResource() {
        return currentWallpaperIsColor ? null : currentWallpaperRL;
    }

    // Update getWallpaperPreviewResource to use byte-based registration (creates NativeImage on client thread)
    public ResourceLocation getWallpaperPreviewResource(String name) {
        if (name == null) return null;
        synchronized (wallpaperPreviews) {
            if (wallpaperPreviews.containsKey(name)) return wallpaperPreviews.get(name);
        }
        File f = new File(wallpapersDir, name);
        if (!f.exists()) return null;
        try {
            // Prepare a unique id for the preview
            String safe = name.replaceAll("[^a-zA-Z0-9_.-]", "_").toLowerCase(Locale.ROOT);
            String id = "pc_ui_wallpaper_preview_" + safe + "_" + f.lastModified();

            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                // Read bytes off-thread then register on client thread
                byte[] fileBytes = null;
                if (name.toLowerCase(Locale.ROOT).endsWith(".gif")) {
                    // Read first frame only for preview
                    try (ImageInputStream iis = ImageIO.createImageInputStream(f)) {
                        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                        if (readers.hasNext()) {
                            ImageReader reader = readers.next();
                            reader.setInput(iis);
                            BufferedImage frame = reader.read(0);
                            fileBytes = bufferedImageToPngBytes(frame);
                        }
                    } catch (Exception ignored) {}
                } else {
                    try (FileInputStream fis = new FileInputStream(f)) {
                        fileBytes = fis.readAllBytes();
                    } catch (Exception ignored) {}
                }

                final byte[] registerBytes = fileBytes;
                mc.execute(() -> {
                    try {
                        if (registerBytes == null) return;
                        synchronized (wallpaperPreviews) { if (wallpaperPreviews.containsKey(name)) return; }
                        // Create NativeImage on client thread and register
                        NativeImage ni = null;
                        try (ByteArrayInputStream bais = new ByteArrayInputStream(registerBytes)) {
                            ni = NativeImage.read(bais);
                        }
                        if (ni == null) return;
                        DynamicTexture dt = new DynamicTexture(ni);
                        ResourceLocation rl = mc.getTextureManager().register(id, dt);
                        synchronized (wallpaperPreviews) { wallpaperPreviews.put(name, rl); }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            } else {
                // Fallback: attempt synchronous registration (headless/testing)
                try (FileInputStream fis = new FileInputStream(f)) {
                    byte[] bytes = fis.readAllBytes();
                    NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
                    DynamicTexture dt = new DynamicTexture(img);
                    ResourceLocation rl = Minecraft.getInstance().getTextureManager().register(id, dt);
                    synchronized (wallpaperPreviews) { wallpaperPreviews.put(name, rl); }
                    return rl;
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        // If async registration is scheduled, return whatever is cached now (likely null)
        synchronized (wallpaperPreviews) {
            return wallpaperPreviews.get(name);
        }
    }

    // New: load GIF frames and start animation
    private void loadGifAndStart(File gifFile) {
        clearCurrentWallpaperTexture();
        try (ImageInputStream iis = ImageIO.createImageInputStream(gifFile)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return;
            ImageReader reader = readers.next();
            reader.setInput(iis);
            int numFrames = reader.getNumImages(true);
            List<BufferedImage> frames = new ArrayList<>();
            List<Integer> delays = new ArrayList<>();
            int limit = Math.min(numFrames, MAX_GIF_FRAMES);
            for (int i = 0; i < limit; i++) {
                BufferedImage frame = reader.read(i);
                frames.add(frame);
                int delay = 100; // default 100ms
                try {
                    IIOMetadata meta = reader.getImageMetadata(i);
                    int parsed = parseGifDelay(meta);
                    if (parsed > 0) delay = parsed;
                } catch (Exception ignored) {}
                delays.add(delay);
            }
            if (frames.isEmpty()) return;
            this.currentGifFrames = frames;
            this.currentGifDelays = delays;
            this.currentGifFrameIndex = 0;
            // Immediately set first frame as texture using safe registration (use bytes)
            BufferedImage first = frames.get(0);
            byte[] bytes = bufferedImageToPngBytes(first);
            String safeName = gifFile.getName().replaceAll("[^a-zA-Z0-9_.-]", "_").toLowerCase(Locale.ROOT);
            String id = "pc_ui_wallpaper_current_" + safeName + "_" + gifFile.lastModified();
            registerImageBytesSafely(bytes, id + "_0");

            // Start scheduling frames
            scheduleNextGifFrame();
        } catch (Exception e) {
            e.printStackTrace();
            currentGifFrames = null;
            currentGifDelays = null;
            currentWallpaperIsGif = false;
        }
    }

    private int parseGifDelay(IIOMetadata meta) {
        // Attempt to parse delay from GIF metadata (javax_imageio_gif_image_1.0)
        if (meta == null) return -1;
        try {
            Node root = meta.getAsTree("javax_imageio_gif_image_1.0");
            if (root == null) return -1;
            for (int i = 0; i < root.getChildNodes().getLength(); i++) {
                Node node = root.getChildNodes().item(i);
                if (node.getNodeName().equals("GraphicControlExtension")) {
                    NamedNodeMap attrs = node.getAttributes();
                    Node delayNode = attrs.getNamedItem("delayTime");
                    if (delayNode != null) {
                        try {
                            int delayCentis = Integer.parseInt(delayNode.getNodeValue());
                            return Math.max(10, delayCentis * 10); // convert to ms, minimum 10ms
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private void scheduleNextGifFrame() {
        if (currentGifFrames == null || currentGifDelays == null || currentGifFrames.isEmpty()) return;
        if (gifTimer != null) gifTimer.cancel();
        gifTimer = new Timer(true);
        int delay = currentGifDelays.get(currentGifFrameIndex);
        gifTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                advanceGifFrame();
            }
        }, delay);
    }

    private void advanceGifFrame() {
        try {
            currentGifFrameIndex = (currentGifFrameIndex + 1) % currentGifFrames.size();
            BufferedImage bi = currentGifFrames.get(currentGifFrameIndex);
            byte[] bytes = bufferedImageToPngBytes(bi);
            final String safeName = (currentWallpaperName != null ? currentWallpaperName : "gif").replaceAll("[^a-zA-Z0-9_.-]", "_").toLowerCase(Locale.ROOT);
            final String id = "pc_ui_wallpaper_current_" + safeName + "_" + System.currentTimeMillis() + "_" + currentGifFrameIndex;
            // Register bytes on client thread
            registerImageBytesSafely(bytes, id);

            // schedule next
            if (currentGifDelays != null && !currentGifDelays.isEmpty()) {
                int nextDelay = currentGifDelays.get(currentGifFrameIndex);
                if (gifTimer != null) gifTimer.schedule(new TimerTask() {
                    @Override public void run() { advanceGifFrame(); }
                }, Math.max(10, nextDelay));
            }
        } catch (Exception e) {
            e.printStackTrace();
            clearCurrentWallpaperTexture();
        }
    }


    // Desktop state classes
    public static class DesktopState {
        public List<DesktopIconState> icons;
        // Persist wallpaper choices
        public String wallpaperName;
        public boolean wallpaperIsColor;
        public int wallpaperColor;

        public DesktopState(List<DesktopIconState> icons) {
            this.icons = icons;
        }

        public DesktopState(List<DesktopIconState> icons, String wallpaperName, boolean wallpaperIsColor, int wallpaperColor) {
            this.icons = icons;
            this.wallpaperName = wallpaperName;
            this.wallpaperIsColor = wallpaperIsColor;
            this.wallpaperColor = wallpaperColor;
        }
    }

    public static class DesktopIconState {
        public String name;
        public int x;
        public int y;

        public DesktopIconState(String name, int x, int y) {
            this.name = name;
            this.x = x;
            this.y = y;
        }
    }

    public void updateDesktopIconsFromRegistry() {
        desktopIcons.clear();

        // Get all apps that should be on desktop from registry
        AppRegistry registry = AppRegistry.getInstance();
        for (String appName : registry.getDesktopApps()) {
            if (registry.isInstalled(appName)) {
                // Add to desktop with default position
                addDesktopIcon(appName,
                        (int) (Math.random() * 300) + 100,
                        (int) (Math.random() * 300) + 60);
            }
        }

        saveDesktopState();
    }

    public String getCurrentWallpaperName() {
        return currentWallpaperName;
    }

    public List<String> listWallpapers() {
        synchronized (wallpaperFiles) {
            return new ArrayList<>(wallpaperFiles);
        }
    }
}
