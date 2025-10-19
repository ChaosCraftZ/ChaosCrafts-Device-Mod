package net.chaoscraft.chaoscrafts_device_mod.client.app;

import com.mojang.blaze3d.vertex.PoseStack;
import net.chaoscraft.chaoscrafts_device_mod.ConfigHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DesktopScreen;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DraggableWindow;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MarketplaceApp implements IApp {
    private DraggableWindow window;
    private final CopyOnWriteArrayList<AppCategory> categories = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, DownloadTask> downloads = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<AppInfo> installedApps = new CopyOnWriteArrayList<>();
    private int scrollOffset = 0;
    private String currentSection = "Store";
    private int visibleHeight = 0;
    private boolean isScrolling = false;
    private int scrollbarHeight = 0;
    private int scrollbarY = 0;
    private int dragStartY = 0;
    private int dragStartOffset = 0;
    private final AsyncTaskManager asyncManager = AsyncTaskManager.getInstance();

    @Override
    public void onOpen(DraggableWindow window) {
        this.window = window;
        asyncManager.submitIOTask(() -> {
            initializeCategories();
            refreshInstalledApps();
        });
    }

    private void initializeCategories() {
        categories.clear();

        AppRegistry registry = AppRegistry.getInstance();
        List<AppRegistry.AppInfo> allApps = registry.getAllApps();

        AppCategory gamesCategory = new AppCategory("Games", 0xFFE74C3C);
        AppCategory utilitiesCategory = new AppCategory("Utilities", 0xFF3498DB);
        AppCategory mediaCategory = new AppCategory("Media", 0xFF9B59B6);

        for (AppRegistry.AppInfo appInfo : allApps) {
            if (registry.isDefaultApp(appInfo.internalName)) continue;

            AppInfo marketplaceApp = new AppInfo(
                    appInfo.internalName,
                    appInfo.displayName,
                    appInfo.description,
                    appInfo.version,
                    5, // 5 second download time
                    false
            );

            switch (appInfo.internalName.toLowerCase()) {
                case "geometry dash":
                    gamesCategory.addApp(marketplaceApp);
                    break;
                case "audio player":
                case "video player":
                    mediaCategory.addApp(marketplaceApp);
                    break;
                case "home security":
                case "notes":
                case "calendar":
                case "weather":
                    utilitiesCategory.addApp(marketplaceApp);
                    break;
                default:
                    utilitiesCategory.addApp(marketplaceApp);
                    break;
            }
        }

        categories.add(gamesCategory);
        categories.add(utilitiesCategory);
        categories.add(mediaCategory);
    }

    private void refreshInstalledApps() {
        installedApps.clear();
        AppRegistry registry = AppRegistry.getInstance();

        for (AppRegistry.AppInfo appInfo : registry.getAllApps()) {
            if (!registry.isDefaultApp(appInfo.internalName) && registry.isInstalled(appInfo.internalName)) {
                installedApps.add(new AppInfo(
                        appInfo.internalName,
                        appInfo.displayName,
                        appInfo.description,
                        appInfo.version,
                        5,
                        false
                ));
            }
        }
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        int[] r = window.getAnimatedRenderRect(26);
        int cx = Math.round(window.getDisplayX()) + 8;
        int cy = Math.round(window.getDisplayY()) + 32;
        int cw = r[2] - 16;
        int ch = r[3] - 40;
        this.visibleHeight = ch - 40;

        guiGraphics.fill(cx, cy, cx + cw, cy + 30, 0xFF2B2B2B);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Marketplace"), cx + 10, cy + 10, 0xFFFFFFFF, false);

        int tabWidth = 80;
        int storeTabX = cx + cw - 160;
        int libraryTabX = cx + cw - 80;

        boolean storeSelected = currentSection.equals("Store");
        guiGraphics.fill(storeTabX, cy, storeTabX + tabWidth, cy + 30, storeSelected ? 0xFF4C7BD1 : 0xFF444444);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Store"), storeTabX + 20, cy + 10, 0xFFFFFFFF, false);

        boolean librarySelected = currentSection.equals("Library");
        guiGraphics.fill(libraryTabX, cy, libraryTabX + tabWidth, cy + 30, librarySelected ? 0xFF4C7BD1 : 0xFF444444);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Library"), libraryTabX + 20, cy + 10, 0xFFFFFFFF, false);

        int contentY = cy + 40;
        int contentHeight = ch - 40;
        float uiScale = ConfigHandler.uiScaleFactor();
        int physCX = Math.round(cx * uiScale);
        int physContentY = Math.round(contentY * uiScale);
        int physRight = Math.round((cx + cw) * uiScale);
        int physBottom = Math.round((contentY + contentHeight) * uiScale);

        guiGraphics.enableScissor(physCX, physContentY, physRight, physBottom);

        if (currentSection.equals("Store")) {
            renderStore(guiGraphics, cx, contentY - scrollOffset, cw, contentHeight);
        } else {
            renderLibrary(guiGraphics, cx, contentY - scrollOffset, cw, contentHeight);
        }

        guiGraphics.disableScissor();

        int totalHeight = getTotalContentHeight();
        if (totalHeight > contentHeight) {
            float contentRatio = (float) contentHeight / totalHeight;
            scrollbarHeight = Math.max(20, (int) (contentHeight * contentRatio));
            int scrollTrackHeight = contentHeight - scrollbarHeight;
            float scrollRatio = (float) scrollOffset / (float) Math.max(1, (totalHeight - contentHeight));
            scrollbarY = contentY + (int) (scrollRatio * scrollTrackHeight);

            guiGraphics.fill(cx + cw - 8, contentY, cx + cw - 2, contentY + contentHeight, 0xFF555555);

            int scrollbarColor = isScrolling ? 0xFF888888 : 0xFF666666;
            guiGraphics.fill(cx + cw - 8, scrollbarY, cx + cw - 2, scrollbarY + scrollbarHeight, scrollbarColor);
        }
    }

    private void renderStore(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch) {
        int categoryX = cx + 10;
        int categoryY = cy;

        for (AppCategory category : categories) {
            if (category.apps.isEmpty()) continue;

            guiGraphics.fill(categoryX, categoryY, categoryX + cw - 20, categoryY + 24, category.color);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(category.name), categoryX + 10, categoryY + 8, 0xFFFFFFFF, false);

            int appY = categoryY + 30;
            for (AppInfo app : category.apps) {
                if (appY + 60 < cy || appY > cy + ch) {
                    appY += 70;
                    continue;
                }

                guiGraphics.fill(categoryX, appY, categoryX + cw - 20, appY + 60, 0xFF2B2B2B);
                guiGraphics.fill(categoryX, appY, categoryX + 4, appY + 60, category.color);

                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(app.displayName), categoryX + 15, appY + 8, 0xFFFFFFFF, false);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(app.description), categoryX + 15, appY + 22, 0xFFCCCCCC, false);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Version: " + app.version), categoryX + 15, appY + 34, 0xFF999999, false);

                AppRegistry registry = AppRegistry.getInstance();
                boolean isInstalled = registry.isInstalled(app.internalName);
                boolean isOnDesktop = registry.isOnDesktop(app.internalName);

                DownloadTask download = downloads.get(app.internalName);
                if (download != null) {
                    if (download.completed) {
                        guiGraphics.fill(categoryX + cw - 90, appY + 10, categoryX + cw - 30, appY + 40, 0xFF57C07D);
                        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Installed"), categoryX + cw - 85, appY + 20, 0xFFFFFFFF, false);
                    } else {
                        int progressWidth = (int) (60 * download.progress);
                        guiGraphics.fill(categoryX + cw - 90, appY + 10, categoryX + cw - 30, appY + 40, 0xFF555555);
                        guiGraphics.fill(categoryX + cw - 90, appY + 10, categoryX + cw - 90 + progressWidth, appY + 40, 0xFF4C7BD1);

                        int percent = (int) (download.progress * 100);
                        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(percent + "%"), categoryX + cw - 80, appY + 20, 0xFFFFFFFF, false);
                    }
                } else if (isInstalled) {
                    if (isOnDesktop) {
                        guiGraphics.fill(categoryX + cw - 90, appY + 10, categoryX + cw - 30, appY + 40, 0xFF57C07D);
                        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Installed"), categoryX + cw - 85, appY + 20, 0xFFFFFFFF, false);
                    } else {
                        guiGraphics.fill(categoryX + cw - 90, appY + 10, categoryX + cw - 30, appY + 40, 0xFFFFA500);
                        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Add to Desktop"), categoryX + cw - 85, appY + 20, 0xFFFFFFFF, false);
                    }
                } else {
                    guiGraphics.fill(categoryX + cw - 90, appY + 10, categoryX + cw - 30, appY + 40, 0xFF4C7BD1);
                    guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Install"), categoryX + cw - 85, appY + 20, 0xFFFFFFFF, false);
                }

                appY += 70;
            }

            categoryY = appY + 20;
        }
    }

    private void renderLibrary(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch) {
        int appY = cy;

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Installed Apps"), cx + 10, appY, 0xFFFFFFFF, false);
        appY += 20;

        for (AppInfo app : installedApps) {
            if (appY + 60 < cy || appY > cy + ch) {
                appY += 70;
                continue;
            }

            guiGraphics.fill(cx + 10, appY, cx + cw - 20, appY + 60, 0xFF2B2B2B);
            guiGraphics.fill(cx + 10, appY, cx + 14, appY + 60, 0xFF4C7BD1);

            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(app.displayName), cx + 25, appY + 8, 0xFFFFFFFF, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(app.description), cx + 25, appY + 22, 0xFFCCCCCC, false);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Version: " + app.version), cx + 25, appY + 34, 0xFF999999, false);

            AppRegistry registry = AppRegistry.getInstance();
            boolean isOnDesktop = registry.isOnDesktop(app.internalName);

            if (!isOnDesktop) {
                guiGraphics.fill(cx + cw - 180, appY + 10, cx + cw - 100, appY + 40, 0xFF4C7BD1);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Add to Desktop"), cx + cw - 175, appY + 20, 0xFFFFFFFF, false);
            }

            guiGraphics.fill(cx + cw - 90, appY + 10, cx + cw - 30, appY + 40, 0xFFF94144);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Uninstall"), cx + cw - 85, appY + 20, 0xFFFFFFFF, false);

            appY += 70;
        }
    }

    private int getTotalContentHeight() {
        if (currentSection.equals("Store")) {
            int height = 0;
            for (AppCategory category : categories) {
                if (category.apps.isEmpty()) continue;
                height += 24 + 30 + (category.apps.size() * 70) + 20;
            }
            return height;
        } else {
            return 20 + (installedApps.size() * 70);
        }
    }

    @Override
    public boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        int[] r = window.getAnimatedRenderRect(26);
        int cx = Math.round(window.getDisplayX()) + 8;
        int cy = Math.round(window.getDisplayY()) + 32;
        int cw = r[2] - 16;
        int contentY = cy + 40;
        int contentHeight = visibleHeight;

        int totalHeight = getTotalContentHeight();
        if (totalHeight > contentHeight) {
            if (mouseRelX >= cx + cw - 8 && mouseRelX <= cx + cw - 2 &&
                    mouseRelY >= scrollbarY && mouseRelY <= scrollbarY + scrollbarHeight) {
                isScrolling = true;
                dragStartY = (int) mouseRelY;
                dragStartOffset = scrollOffset;
                return true;
            }
        }

        int storeTabX = cx + cw - 160;
        int libraryTabX = cx + cw - 80;

        if (mouseRelX >= storeTabX && mouseRelX <= storeTabX + 80 && mouseRelY >= cy && mouseRelY <= cy + 30) {
            currentSection = "Store";
            scrollOffset = 0;
            return true;
        }

        if (mouseRelX >= libraryTabX && mouseRelX <= libraryTabX + 80 && mouseRelY >= cy && mouseRelY <= cy + 30) {
            currentSection = "Library";
            scrollOffset = 0;
            return true;
        }

        double adjustedMouseY = mouseRelY + scrollOffset;

        if (currentSection.equals("Store")) {
            return handleStoreClick(mouseRelX, adjustedMouseY, cx, contentY, cw);
        } else {
            return handleLibraryClick(mouseRelX, adjustedMouseY, cx, contentY, cw);
        }
    }

    private boolean handleStoreClick(double mouseRelX, double mouseRelY, int cx, int contentY, int cw) {
        int categoryX = cx + 10;
        int categoryY = contentY;

        for (AppCategory category : categories) {
            if (category.apps.isEmpty()) continue;

            int headerY = categoryY;
            int appY = headerY + 30;
            for (AppInfo app : category.apps) {
                if (mouseRelX >= categoryX + cw - 90 && mouseRelX <= categoryX + cw - 30 &&
                        mouseRelY >= appY + 10 && mouseRelY <= appY + 40) {

                    AppRegistry registry = AppRegistry.getInstance();
                    boolean isInstalled = registry.isInstalled(app.internalName);
                    boolean isOnDesktop = registry.isOnDesktop(app.internalName);

                    if (!isInstalled && !downloads.containsKey(app.internalName)) {
                        DownloadTask download = new DownloadTask(app.internalName, app.downloadTime);
                        downloads.put(app.internalName, download);

                        asyncManager.submitIOTask(() -> {
                            long startTime = System.currentTimeMillis();
                            while (System.currentTimeMillis() - startTime < app.downloadTime * 1000) {
                                download.progress = (float) (System.currentTimeMillis() - startTime) / (app.downloadTime * 1000);
                                try { Thread.sleep(50); } catch (InterruptedException e) {}
                            }
                            download.progress = 1.0f;
                            download.completed = true;

                            AppRegistry.AppInfo appInfo = registry.getAppInfo(app.internalName);
                            if (appInfo != null) {
                                registry.installApp(app.internalName, appInfo.displayName, appInfo.description, appInfo.version)
                                        .thenRun(() -> {
                                            asyncManager.executeOnMainThread(this::refreshInstalledApps);
                                            asyncManager.executeOnMainThread(() -> {
                                                if (Minecraft.getInstance().screen instanceof DesktopScreen) {
                                                    ((DesktopScreen) Minecraft.getInstance().screen).refreshDesktopIcons();
                                                }
                                            });
                                        });
                            } else {
                                registry.installApp(app.internalName, app.displayName, app.description, app.version)
                                        .thenRun(() -> {
                                            asyncManager.executeOnMainThread(this::refreshInstalledApps);
                                            asyncManager.executeOnMainThread(() -> {
                                                if (Minecraft.getInstance().screen instanceof DesktopScreen) {
                                                    ((DesktopScreen) Minecraft.getInstance().screen).refreshDesktopIcons();
                                                }
                                            });
                                        });
                            }
                        });

                    } else if (isInstalled && !isOnDesktop) {
                        registry.addToDesktop(app.internalName);

                        if (Minecraft.getInstance().screen instanceof DesktopScreen) {
                            asyncManager.executeOnMainThread(() -> {
                                ((DesktopScreen) Minecraft.getInstance().screen).refreshDesktopIcons();
                            });
                        }
                    }

                    return true;
                }
                appY += 70;
            }

            categoryY = appY + 20;
        }

        return false;
    }

    private boolean handleLibraryClick(double mouseRelX, double mouseRelY, int cx, int contentY, int cw) {
        int appY = contentY + 20;

        for (AppInfo app : installedApps) {
            if (mouseRelX >= cx + cw - 180 && mouseRelX <= cx + cw - 100 &&
                    mouseRelY >= appY + 10 && mouseRelY <= appY + 40) {

                AppRegistry registry = AppRegistry.getInstance();
                registry.addToDesktop(app.internalName);

                if (Minecraft.getInstance().screen instanceof DesktopScreen) {
                    asyncManager.executeOnMainThread(() -> {
                        ((DesktopScreen) Minecraft.getInstance().screen).refreshDesktopIcons();
                    });
                }
                return true;
            }

            if (mouseRelX >= cx + cw - 90 && mouseRelX <= cx + cw - 30 &&
                    mouseRelY >= appY + 10 && mouseRelY <= appY + 40) {

                AppRegistry registry = AppRegistry.getInstance();
                registry.uninstallApp(app.internalName);

                refreshInstalledApps();

                if (Minecraft.getInstance().screen instanceof DesktopScreen) {
                    asyncManager.executeOnMainThread(() -> {
                        ((DesktopScreen) Minecraft.getInstance().screen).refreshDesktopIcons();
                    });
                }
                return true;
            }
            appY += 70;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(DraggableWindow window, double mouseRelX, double mouseRelY, double dx, double dy) {
        if (isScrolling) {
            int[] r = window.getRenderRect(26);
            int cy = r[1] + 32;
            int contentY = cy + 40;
            int contentHeight = visibleHeight;
            int totalHeight = getTotalContentHeight();

            if (totalHeight > contentHeight) {
                int scrollTrackHeight = contentHeight - scrollbarHeight;
                int deltaY = (int) mouseRelY - dragStartY;
                float ratio = (float) deltaY / (float) Math.max(1, scrollTrackHeight);
                int newOffset = dragStartOffset + (int) (ratio * (totalHeight - contentHeight));
                scrollOffset = Math.max(0, Math.min(Math.max(0, totalHeight - contentHeight), newOffset));
                return true;
            }
        }
        return false;
    }

    @Override
    public void mouseReleased(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        this.isScrolling = false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.window == null) return false;
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 32, cw = r[2] - 16, ch = r[3] - 40;
        int contentY = cy + 40;
        int contentHeight = ch - 40;

        if (mouseX < cx || mouseX > cx + cw || mouseY < cy || mouseY > cy + ch) return false;

        int totalHeight = getTotalContentHeight();
        if (totalHeight <= contentHeight) return false;

        int scrollStep = (int) Math.round(delta * 24.0);
        scrollOffset = Math.max(0, Math.min(totalHeight - contentHeight, scrollOffset - scrollStep));
        return true;
    }

    @Override
    public boolean keyPressed(DraggableWindow window, int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public boolean charTyped(DraggableWindow window, char codePoint, int modifiers) {
        return false;
    }

    @Override
    public boolean onClose(DraggableWindow window) {
        this.window = null;
        return true;
    }

    @Override
    public void tick() {}

    private static class AppCategory {
        String name;
        int color;
        List<AppInfo> apps = new ArrayList<>();

        AppCategory(String name, int color) {
            this.name = name;
            this.color = color;
        }

        void addApp(AppInfo app) {
            apps.add(app);
        }
    }

    private static class AppInfo {
        String internalName;
        String displayName;
        String description;
        String version;
        int downloadTime;
        boolean essential;

        AppInfo(String internalName, String displayName, String description, String version, int downloadTime, boolean essential) {
            this.internalName = internalName;
            this.displayName = displayName;
            this.description = description;
            this.version = version;
            this.downloadTime = downloadTime;
            this.essential = essential;
        }
    }

    private static class DownloadTask {
        String appName;
        float progress = 0.0f;
        boolean completed = false;
        int downloadTime;

        DownloadTask(String appName, int downloadTime) {
            this.appName = appName;
            this.downloadTime = downloadTime;
        }
    }
}

