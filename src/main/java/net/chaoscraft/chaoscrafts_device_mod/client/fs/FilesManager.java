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
    private final Map<String, ResourceLocation> wallpaperPreviews = new HashMap<>();

    private final Map<String, FSNode> fileSystem = new HashMap<>();
    private final List<DesktopIconState> desktopIcons = new ArrayList<>();

    private DynamicTexture currentWallpaperTexture = null;
    private ResourceLocation currentWallpaperRL = null;
    private String currentWallpaperName = null;

    private boolean currentWallpaperIsColor = false;
    private int currentWallpaperColor = 0xFF000000;

    private boolean currentWallpaperIsGif = false;
    private List<BufferedImage> currentGifFrames = null;
    private List<Integer> currentGifDelays = null;
    private int currentGifFrameIndex = 0;
    private Timer gifTimer = null;

    private static final int MAX_GIF_FRAMES = 60;

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

        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.getServer() != null) {
            worldName = Minecraft.getInstance().level.getServer().getWorldData().getLevelName();
        } else if (Minecraft.getInstance().getSingleplayerServer() != null) {
            worldName = Minecraft.getInstance().getSingleplayerServer().getWorldData().getLevelName();
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
                e.printStackTrace();
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

                    if (state.wallpaperName != null) {
                        this.currentWallpaperName = state.wallpaperName;
                    }
                    this.currentWallpaperIsColor = state.wallpaperIsColor;
                    this.currentWallpaperColor = state.wallpaperColor != 0 ? state.wallpaperColor : this.currentWallpaperColor;
                    if (this.currentWallpaperIsColor) {
                        clearCurrentWallpaperTexture();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            createDefaultDesktop();
        }
    }

    public void saveState() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(getWorldDataDir(), "pc_ui_files.dat")))) {
            oos.writeObject(fileSystem);
        } catch (Exception e) {
            e.printStackTrace();
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
            e.printStackTrace();
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
        List<String> defaultApps = AppFactory.getDefaultApps();

        int x = 150;
        int y = 60;

        for (String appName : defaultApps) {
            desktopIcons.add(new DesktopIconState(appName, x, y));
            x += 64;

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

    public FSNode getNode(String path) {
        return fileSystem.get(path);
    }

    public boolean deleteNode(String path) {
        FSNode node = getNode(path);
        if (node == null) return false;

        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == -1) return false;

        String parentPath = path.substring(0, lastSlash);
        String name = path.substring(lastSlash + 1);

        FSNode parent = getNode(parentPath);
        if (parent == null) return false;

        parent.removeChild(name);
        fileSystem.remove(path);

        if (parentPath.equals("/Desktop")) {
            removeDesktopIcon(name);
        }

        saveState();
        return true;
    }

    public boolean renameNode(String path, String newName) {
        FSNode node = getNode(path);
        if (node == null) return false;

        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == -1) return false;

        String parentPath = path.substring(0, lastSlash);
        String oldName = path.substring(lastSlash + 1);

        FSNode parent = getNode(parentPath);
        if (parent == null) return false;

        parent.removeChild(oldName);
        node.name = newName;
        parent.addChild(node);

        fileSystem.remove(path);
        fileSystem.put(parentPath + "/" + newName, node);

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

    public void setCurrentWallpaperName(String name) {
        currentWallpaperIsColor = false;

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
                String low = name.toLowerCase(Locale.ROOT);
                if (low.endsWith(".gif")) {
                    currentWallpaperIsGif = true;
                    loadGifAndStart(f);
                } else {
                    currentWallpaperIsGif = false;
                    loadCurrentWallpaper();
                }
            } else {
                System.out.println("[PC-UI] Requested wallpaper file does not exist: " + name + " (path=" + f.getAbsolutePath() + ")");
            }
        }
        saveState();
    }

    public void setCurrentWallpaperColor(int argb) {
        System.out.println("[PC-UI] Setting solid color wallpaper: " + String.format("#%06X", argb & 0xFFFFFF));
        currentWallpaperIsColor = true;
        currentWallpaperColor = argb;
        currentWallpaperName = null;
        currentWallpaperIsGif = false;
        clearCurrentWallpaperTexture();

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

    private byte[] bufferedImageToPngBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    private void registerImageBytesSafely(final byte[] pngBytes, final String id) {
        if (pngBytes == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                try {
                    NativeImage n = null;
                    try (ByteArrayInputStream bais = new ByteArrayInputStream(pngBytes)) {
                        n = NativeImage.read(bais);
                    }
                    if (n == null) return;
                    int ww = n.getWidth();

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
                    } catch (Exception ignored) {}
                    currentWallpaperTexture = null;
                    currentWallpaperRL = null;
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
                e.printStackTrace();
                currentWallpaperTexture = null;
                currentWallpaperRL = null;
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
                    if (currentWallpaperRL != null) {
                        try { mc.getTextureManager().release(currentWallpaperRL); } catch (Exception ignored) {}
                        currentWallpaperRL = null;
                    }
                    if (currentWallpaperTexture != null) {
                        try { currentWallpaperTexture.close(); } catch (Exception ignored) {}
                        currentWallpaperTexture = null;
                    }

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

    public ResourceLocation getCurrentWallpaperResource() {
        return currentWallpaperIsColor ? null : currentWallpaperRL;
    }

    public ResourceLocation getWallpaperPreviewResource(String name) {
        if (name == null) return null;
        synchronized (wallpaperPreviews) {
            if (wallpaperPreviews.containsKey(name)) return wallpaperPreviews.get(name);
        }
        File f = new File(wallpapersDir, name);
        if (!f.exists()) return null;
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
        synchronized (wallpaperPreviews) {
            return wallpaperPreviews.get(name);
        }
    }

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
                int delay = 100;
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
            BufferedImage first = frames.get(0);
            byte[] bytes = bufferedImageToPngBytes(first);
            String safeName = gifFile.getName().replaceAll("[^a-zA-Z0-9_.-]", "_").toLowerCase(Locale.ROOT);
            String id = "pc_ui_wallpaper_current_" + safeName + "_" + gifFile.lastModified();
            registerImageBytesSafely(bytes, id + "_0");

            scheduleNextGifFrame();
        } catch (Exception e) {
            e.printStackTrace();
            currentGifFrames = null;
            currentGifDelays = null;
            currentWallpaperIsGif = false;
        }
    }

    private int parseGifDelay(IIOMetadata meta) {
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
                            return Math.max(10, delayCentis * 10);
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
            registerImageBytesSafely(bytes, id);

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

    public static class DesktopState {
        public List<DesktopIconState> icons;
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

        AppRegistry registry = AppRegistry.getInstance();
        for (String appName : registry.getDesktopApps()) {
            if (registry.isInstalled(appName)) {
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
