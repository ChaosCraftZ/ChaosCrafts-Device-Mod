package net.chaoscraft.chaoscrafts_device_mod.Client.Screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager.AppRegistry;
import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.session.ClientDeviceSession;
import net.chaoscraft.chaoscrafts_device_mod.Client.Sound.LaptopFanLoopSound;
import net.chaoscraft.chaoscrafts_device_mod.Client.Sound.LaptopKeySoundManager;
import net.chaoscraft.chaoscrafts_device_mod.Client.Sound.ModSounds;
import net.chaoscraft.chaoscrafts_device_mod.Core.AsyncorAPI.AsyncRuntime;
import net.chaoscraft.chaoscrafts_device_mod.Core.Config.ConfigHandler;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Components.*;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Device.DeviceState;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Input.DesktopInputHandler;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Menu.DesktopContextMenu;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Model.DesktopIcon;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Renderer.DesktopRenderer;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Utils.DesktopConstants;
import net.chaoscraft.chaoscrafts_device_mod.Core.Desktop.Utils.DesktopState;
import net.chaoscraft.chaoscrafts_device_mod.Core.Network.packet.LaptopDevicePackets;
import net.chaoscraft.chaoscrafts_device_mod.Core.Util.FileSystem.FilesManager;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DesktopScreen extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static DesktopScreen currentInstance;

    private final AsyncRuntime asyncRuntime = AsyncRuntime.get();
    private final BlockPos devicePos;
    private final ClientDeviceSession deviceSession;

    // Managers
    private final DesktopState state = new DesktopState();
    private final DesktopIconManager iconManager = new DesktopIconManager(state, this);
    private final WindowManager windowManager = new WindowManager(state);
    private final LoadingScreenRenderer loadingRenderer = new LoadingScreenRenderer(state);
    private final TaskbarRenderer taskbarRenderer = new TaskbarRenderer(state, windowManager);
    private final DesktopInputHandler inputHandler = new DesktopInputHandler(state, windowManager, this);
    private final DesktopRenderer renderer = new DesktopRenderer(state, loadingRenderer, taskbarRenderer);

    public DesktopScreen() { this(null); }
    public DesktopScreen(BlockPos devicePos) {
        super(Component.literal("Desktop"));
        this.devicePos = devicePos;
        this.deviceSession = new ClientDeviceSession(devicePos);
        if (devicePos != null) LaptopKeySoundManager.setDevicePos(devicePos);

        asyncRuntime.runOnClientThread(() -> {
            try { AppRegistry.getInstance(); } catch (Exception ignored) {}
            try { net.chaoscraft.chaoscrafts_device_mod.Client.Apps.BaseApps.SettingsApp.loadSettings(); } catch (Exception ignored) {}
        });
        asyncRuntime.schedule(() -> asyncRuntime.runOnClientThread(() -> state.showLoadingOverlay = false), DesktopConstants.MIN_LOADING_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Override
    protected void init() {
        if (minecraft == null) {
            return;
        }

        currentInstance = this;

        if (!state.guiScaleForced) {
            state.originalGuiScale = minecraft.getWindow().getGuiScale();
            state.guiScaleForced = true;
        }

        minecraft.getWindow().setGuiScale(2.0);

        this.width = minecraft.getWindow().getGuiScaledWidth();
        this.height = minecraft.getWindow().getGuiScaledHeight();

        this.clearWidgets();
        super.init();

        // Ensure fan sound gets created only if experimental settings enabled
        if (!state.fanStarted && ConfigHandler.experimentalEnabled()) {
            try {
                double fx, fy, fz;
                if (devicePos != null) {
                    fx = devicePos.getX() + 0.5; fy = devicePos.getY() + 0.5; fz = devicePos.getZ() + 0.5;
                } else {
                    var player = Minecraft.getInstance().player;
                    if (player != null) { fx = player.getX(); fy = player.getY(); fz = player.getZ(); } else { fx = 0; fy = 0; fz = 0; }
                }
                state.fanLoopSound = new LaptopFanLoopSound(ModSounds.LAPTOP_FAN_NOISE.get(), fx, fy, fz, DesktopConstants.FAN_PREDELAY_TICKS);
                Minecraft.getInstance().getSoundManager().play(state.fanLoopSound);
                state.fanStarted = true;
            } catch (Exception e) {
                LOGGER.warn("[DesktopScreen] Failed to start fan loop: {}", e.getMessage());
            }
        }

        state.searchBox = new EditBox(this.font, DesktopConstants.SEARCH_BOX_X, height - DesktopConstants.TASKBAR_HEIGHT + 4, DesktopConstants.SEARCH_BOX_WIDTH, 20, Component.literal("Search"));
        state.searchBox.setMaxLength(100);
        this.addRenderableWidget(state.searchBox);
        updateSearchWidgetVisibility();

        if (state.renamingIcon != null) {
            int rx = Math.round(state.renamingIcon.displayX);
            int ry = Math.round(state.renamingIcon.displayY + state.renamingIcon.iconSize + 4);
            int rw = Math.max(80, state.renamingIcon.iconSize);
            state.renameBox = new EditBox(this.font, rx, ry, rw, 16, Component.literal("Rename"));
            this.addRenderableWidget(state.renameBox);
        }

        try {
            AppRegistry reg = AppRegistry.getInstance();
            reg.getLoadedFuture().whenComplete((v, ex) -> asyncRuntime.runOnClientThread(this::refreshDesktopIcons));
        } catch (Exception e) {
            asyncRuntime.runOnClientThread(this::refreshDesktopIcons);
        }

        handleDimensionChange(this.width, this.height);

        deviceSession.requestInitialState();
    }

    @Override
    public void onClose() {
        restoreGuiScaleIfNeeded();
        if (state.fanLoopSound != null && state.fanStarted && !state.fanLoopSound.isFinished()) state.fanLoopSound.requestFadeOut();
        LaptopKeySoundManager.clearDevicePos();
        deviceSession.close();
        if (currentInstance == this) {
            currentInstance = null;
        }
        super.onClose();
    }

    @Override
    public void removed() {
        restoreGuiScaleIfNeeded();
        if (state.fanLoopSound != null && state.fanStarted && !state.fanLoopSound.isFinished() && !state.fanLoopSound.isFadingOut()) {
            state.fanLoopSound.requestFadeOut();
        }
        if (currentInstance == this) {
            currentInstance = null;
        }
        super.removed();
    }

    private void restoreGuiScaleIfNeeded() {
        if (state.guiScaleForced) {
            var mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
            if (mc != null) {
                var window = mc.getWindow();
                if (window != null) {
                    window.setGuiScale(state.originalGuiScale);
                }
            }
            state.guiScaleForced = false;
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        if (minecraft != null) {
            minecraft.getWindow().setGuiScale(2.0);
            int scaledWidth = minecraft.getWindow().getGuiScaledWidth();
            int scaledHeight = minecraft.getWindow().getGuiScaledHeight();
            super.resize(minecraft, scaledWidth, scaledHeight);
        } else {
            super.resize(minecraft, width, height);
            handleDimensionChange(width, height);
        }
    }

    private void handleDimensionChange(int newWidth, int newHeight) {
        if (newWidth <= 0 || newHeight <= 0) {
            return;
        }

        if (state.lastScaledWidth > 0 && state.lastScaledHeight > 0 &&
                (newWidth != state.lastScaledWidth || newHeight != state.lastScaledHeight)) {
            double scaleX = newWidth / (double) state.lastScaledWidth;
            double scaleY = newHeight / (double) state.lastScaledHeight;
            scaleDesktopIcons(scaleX, scaleY);
            scaleOpenWindows(scaleX, scaleY);
        }

        applyResponsiveLayout();
        state.lastScaledWidth = newWidth;
        state.lastScaledHeight = newHeight;
    }

    private void applyResponsiveLayout() {
        if (width <= 0 || height <= 0) {
            return;
        }
        clampDesktopIconsToBounds(width, height);
        clampOpenWindowsToBounds(width, height);
    }

    private void clampDesktopIconsToBounds(int canvasWidth, int canvasHeight) {
        if (state.desktopIcons.isEmpty()) {
            return;
        }

        int minX = 12;
        int minY = 24;
        int maxX = Math.max(minX, canvasWidth - state.iconSize - 12);
        int maxY = Math.max(minY, canvasHeight - DesktopConstants.TASKBAR_HEIGHT - state.iconSize - 16);

        FilesManager manager;
        try {
            manager = FilesManager.getInstance();
        } catch (Exception ignored) {
            manager = null;
        }
        for (DesktopIcon icon : state.desktopIcons) {
            if (icon == null) {
                continue;
            }
            int adjustedX = Math.min(Math.max(minX, icon.targetX), maxX);
            int adjustedY = Math.min(Math.max(minY, icon.targetY), maxY);
            if (adjustedX != icon.targetX || adjustedY != icon.targetY) {
                icon.targetX = adjustedX;
                icon.targetY = adjustedY;
                icon.displayX = adjustedX;
                icon.displayY = adjustedY;
                if (manager != null) {
                    manager.updateDesktopIconPosition(icon.name, adjustedX, adjustedY);
                }
            }
        }
    }

    private void clampOpenWindowsToBounds(int canvasWidth, int canvasHeight) {
        if (state.openApps.isEmpty()) {
            return;
        }

    int effectiveHeight = Math.max(0, canvasHeight - DesktopConstants.TASKBAR_HEIGHT);
    int widestWithinCanvas = Math.max(120, canvasWidth - 20);
    int tallestWithinCanvas = Math.max(120, effectiveHeight - 20);

    int maxWindowWidth = Math.min(canvasWidth, widestWithinCanvas);
    int maxWindowHeight = effectiveHeight > 0 ? Math.min(effectiveHeight, tallestWithinCanvas) : 0;

        for (DraggableWindow window : state.openApps) {
            if (window == null || window.maximized || window.exclusiveFullscreen) {
                continue;
            }

            if (maxWindowWidth > 0 && window.width > maxWindowWidth) {
                window.width = maxWindowWidth;
            }
            if (maxWindowHeight > 0 && window.height > maxWindowHeight) {
                window.height = maxWindowHeight;
            }

            int maxX = Math.max(0, canvasWidth - window.width);
            int maxY = Math.max(0, effectiveHeight - window.height);

            if (window.x < 0) {
                window.x = 0;
            } else if (window.x > maxX) {
                window.x = maxX;
            }

            if (window.y < 0) {
                window.y = 0;
            } else if (window.y > maxY) {
                window.y = maxY;
            }
        }
    }

    private void scaleDesktopIcons(double scaleX, double scaleY) {
        if (state.desktopIcons.isEmpty()) {
            return;
        }

        FilesManager manager;
        try {
            manager = FilesManager.getInstance();
        } catch (Exception ignored) {
            manager = null;
        }

        for (DesktopIcon icon : state.desktopIcons) {
            if (icon == null) {
                continue;
            }
            int scaledX = (int) Math.round(icon.targetX * scaleX);
            int scaledY = (int) Math.round(icon.targetY * scaleY);
            icon.targetX = scaledX;
            icon.targetY = scaledY;
            icon.displayX = scaledX;
            icon.displayY = scaledY;
            if (manager != null) {
                manager.updateDesktopIconPosition(icon.name, scaledX, scaledY);
            }
        }
    }

    private void scaleOpenWindows(double scaleX, double scaleY) {
        if (state.openApps.isEmpty()) {
            return;
        }

        for (DraggableWindow window : state.openApps) {
            if (window == null || window.maximized || window.exclusiveFullscreen) {
                continue;
            }

            window.x = (int) Math.round(window.x * scaleX);
            window.y = (int) Math.round(window.y * scaleY);

            int scaledWidth = (int) Math.round(window.width * scaleX);
            int scaledHeight = (int) Math.round(window.height * scaleY);

            if (scaledWidth > 0) {
                window.width = Math.max(120, scaledWidth);
            }
            if (scaledHeight > 0) {
                window.height = Math.max(100, scaledHeight);
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderer.render(guiGraphics, mouseX, mouseY, partialTick, width, height);
        windowManager.cleanupClosedWindows();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        super.tick();
        updateSearchWidgetVisibility();

        if (!state.handlingDynamicResize && minecraft != null && minecraft.getWindow() != null) {
            int scaledWidth = minecraft.getWindow().getGuiScaledWidth();
            int scaledHeight = minecraft.getWindow().getGuiScaledHeight();
            if (scaledWidth != state.lastScaledWidth || scaledHeight != state.lastScaledHeight) {
                state.handlingDynamicResize = true;
                this.resize(minecraft, scaledWidth, scaledHeight);
                state.handlingDynamicResize = false;
            }
        }
        // If experimental setting was turned off while open, immediately stop fan
        if (state.fanStarted && state.fanLoopSound != null && !ConfigHandler.experimentalEnabled() && !state.fanLoopSound.isFinished()) {
            state.fanLoopSound.requestFadeOut();
            state.fanStarted = false;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return inputHandler.handleMouseClicked(mouseX, mouseY, button, width, height);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return inputHandler.handleMouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        return inputHandler.handleMouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return inputHandler.handleMouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (state.contextMenu != null) {
                state.contextMenu = null;
                return true;
            }
            if (state.renameBox != null) {
                this.removeWidget(state.renameBox);
                state.renameBox = null;
                state.renamingIcon = null;
                return true;
            }
        }

        boolean handled = inputHandler.handleKeyPressed(keyCode, scanCode, modifiers);
        if (!handled) {
            handled = super.keyPressed(keyCode, scanCode, modifiers);
        }
        return handled;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return inputHandler.handleKeyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char typedChar, int keyCode) {
        return inputHandler.handleCharTyped(typedChar, keyCode);
    }

    // Public methods for external access
    public void openAppSingle(String name, int w, int h) {
        windowManager.openAppSingle(name, w, h, this.width, this.height);
    }

    public BlockPos getDevicePos() {
        return devicePos;
    }

    public void showContextMenu(int x, int y) {
        state.contextMenu = new DesktopContextMenu(this, x, y);
    }

    public void refreshDesktopIcons() {
        if (deviceSession != null && deviceSession.getDevicePos() != null) {
            if (deviceSession.isInitialized()) {
                rebuildDesktopIconsFromSession();
            } else {
                state.desktopIcons.clear();
                state.selectedIcons.clear();
            }
        } else {
            iconManager.refreshDesktopIcons();
        }
        updateSearchWidgetVisibility();
    }

    private void rebuildDesktopIconsFromSession() {
        if (deviceSession == null) {
            return;
        }
        state.desktopIcons.clear();
        state.selectedIcons.clear();

        List<DeviceState.DesktopIconData> icons = deviceSession.getDesktopIcons();
        AppRegistry registry;
        try {
            registry = AppRegistry.getInstance();
        } catch (Exception ignored) {
            registry = null;
        }

        for (DeviceState.DesktopIconData iconData : icons) {
            if (iconData == null || iconData.name == null || iconData.name.isBlank()) {
                continue;
            }
            String iconName = iconData.name;
            int iconX = iconData.x;
            int iconY = iconData.y;
            boolean isApp = registry != null && registry.isInstalled(iconName);
            boolean isTextFile = iconName.toLowerCase(Locale.ROOT).endsWith(".txt");
            DesktopIcon icon = new DesktopIcon(iconName, iconX, iconY, () -> {
                if (isApp) {
                    openAppSingle(iconName, 900, 600);
                } else if (isTextFile) {
                    openAppSingle("notepad", 800, 600);
                } else {
                    openAppSingle("files", 780, 520);
                }
            });
            icon.iconSize = state.iconSize;
            state.desktopIcons.add(icon);
        }

        state.iconStartPositions.clear();
        applyResponsiveLayout();
    }

    public void setIconSize(int size) {
        iconManager.setIconSize(size);
    }

    public void refresh() {
        FilesManager.getInstance().saveState();
    }

    public void openSettingsApp() {
        openAppSingle("Settings", 520, 480);
    }

    public void sortIconsByName() {
        iconManager.sortIconsByName();
    }

    public void sortIconsByDate() {
        iconManager.sortIconsByDate();
    }

    public void sortIconsBySize() {
        iconManager.sortIconsBySize();
    }

    public void createNewFolderOnDesktop() {
        iconManager.createNewFolderOnDesktop();
    }

    public void createNewTextFileOnDesktop() {
        iconManager.createNewTextFileOnDesktop();
    }

    public boolean isPositionOccupied(int x, int y, DesktopIcon exclude) {
        return iconManager.isPositionOccupied(x, y, exclude);
    }

    public void updateIconPosition(String name, int x, int y) {
        try {
            FilesManager.getInstance().updateDesktopIconPosition(name, x, y);
        } catch (Exception ignored) {}
    }

    private void updateSearchWidgetVisibility() {
        if (state.searchBox == null) {
            return;
        }

        boolean shouldBeVisible = !state.showLoadingOverlay;
        state.searchBox.setVisible(shouldBeVisible);
        state.searchBox.setEditable(shouldBeVisible);

        if (!shouldBeVisible) {
            state.searchBox.setValue("");
            state.searchResults.clear();
        }
    }

    public static void applyRemoteState(BlockPos pos, CompoundTag stateTag) {
        DesktopScreen screen = currentInstance;
        if (screen == null || screen.deviceSession == null) {
            return;
        }
        if (!Objects.equals(screen.deviceSession.getDevicePos(), pos)) {
            return;
        }

        screen.deviceSession.applyState(stateTag);
        // Ensure loading overlay shows for minimum time
        long elapsed = System.currentTimeMillis() - screen.state.loadingStartMillis;
        if (elapsed >= DesktopConstants.MIN_LOADING_MS) {
            screen.state.showLoadingOverlay = false;
        }
        // else let the constructor schedule handle it
        screen.refreshDesktopIcons();
    }

    public static void handleActionResponse(LaptopDevicePackets.ActionResponse response) {
        DesktopScreen screen = currentInstance;
        if (screen == null) {
            return;
        }
        screen.deviceSession.applyActionResponse(response);
        if (response == null || response.message == null || response.message.isBlank()) {
            return;
        }

        Minecraft mc = screen.minecraft != null ? screen.minecraft : Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.player.displayClientMessage(Component.literal(response.message), false);
        }
    }
}
