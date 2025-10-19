package net.chaoscraft.chaoscrafts_device_mod.client.app;

import com.mojang.blaze3d.vertex.PoseStack;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DesktopScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;
import net.chaoscraft.chaoscrafts_device_mod.client.fs.FilesManager;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DraggableWindow;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FilesApp implements IApp {
    private DraggableWindow window;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private final AsyncTaskManager asyncManager = AsyncTaskManager.getInstance();

    private File currentDirectory;
    private File selectedFile = null;
    private boolean renaming = false;
    private EditBox renameBox;

    private boolean editingFile = false;
    private EditBox fileEditor;
    private File editingFileRef = null;

    private double mouseRelX, mouseRelY;
    private boolean showHiddenFiles = false;
    private String viewMode = "details";
    private String searchQuery = "";
    private List<File> searchResults = new ArrayList<>();
    private boolean inSearchMode = false;

    private final LinkedList<File> navigationHistory = new LinkedList<>();
    private int historyPointer = -1;

    private File clipboardFile = null;
    private boolean isCutOperation = false;
    private long lastClickTime = 0;
    private File lastClickedFile = null;

    @Override
    public void onOpen(DraggableWindow window) {
        this.window = window;
        this.currentDirectory = new File(FilesManager.getPlayerDataDir(), "Documents");
        this.currentDirectory.mkdirs();

        asyncManager.submitIOTask(this::initializeDefaultFiles);
    }

    private void initializeDefaultFiles() {
        File readme = new File(currentDirectory, "README.txt");
        if (!readme.exists()) {
            try (FileWriter writer = new FileWriter(readme)) {
                writer.write("Welcome to your virtual PC!\nThis is a sample text file.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        File notes = new File(currentDirectory, "Notes.txt");
        if (!notes.exists()) {
            try (FileWriter writer = new FileWriter(notes)) {
                writer.write("Important notes:\n- Taskbar at the bottom\n- Search functionality\n- File browser with columns");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private File[] getFileArray() {
        File[] files;
        if (inSearchMode) {
            files = searchResults.toArray(new File[0]);
        } else {
            File[] raw = currentDirectory.listFiles();
            files = raw != null ? raw : new File[0];
        }

        Arrays.sort(files, (f1, f2) -> {
            if (f1.isDirectory() && !f2.isDirectory()) return -1;
            if (!f1.isDirectory() && f2.isDirectory()) return 1;
            return f1.getName().compareToIgnoreCase(f2.getName());
        });

        return files;
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        this.mouseRelX = mouseRelX;
        this.mouseRelY = mouseRelY;

        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 32, cw = r[2] - 16, ch = r[3] - 40;

        if (editingFile && editingFileRef != null) {
            renderTextEditor(guiGraphics, cx, cy, cw, ch);
            return;
        }

        guiGraphics.fill(cx, cy, cx + cw, cy + 30, DraggableWindow.darkTheme ? 0xFF2B2B2B : 0xFFBFBFBF);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Files"), cx + 10, cy + 10, DraggableWindow.textPrimaryColor(), false);

        guiGraphics.fill(cx + cw - 120, cy + 5, cx + cw - 90, cy + 25, DraggableWindow.darkTheme ? 0xFF555555 : 0xFF999999);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("◀"), cx + cw - 115, cy + 10, DraggableWindow.textPrimaryColor(), false);

        guiGraphics.fill(cx + cw - 80, cy + 5, cx + cw - 50, cy + 25, DraggableWindow.darkTheme ? 0xFF555555 : 0xFF999999);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("▶"), cx + cw - 75, cy + 10, DraggableWindow.textPrimaryColor(), false);

        guiGraphics.fill(cx + cw - 40, cy + 5, cx + cw - 10, cy + 25, DraggableWindow.darkTheme ? 0xFF555555 : 0xFF999999);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("⬆"), cx + cw - 35, cy + 10, DraggableWindow.textPrimaryColor(), false);

        String path = getCurrentPath();

        int pathX = cx + 100;
        int buttonsStartX = cx + cw - 120;
        int padding = 8;
        int maxWidthPx = buttonsStartX - pathX - padding;

        String displayPath = path;
        if (maxWidthPx <= 0) {
            displayPath = "...";
        } else {
            com.mojang.blaze3d.vertex.PoseStack ps = null;
            Font font = Minecraft.getInstance().font;
            if (font.width(path) > maxWidthPx) {
                String ell = "...";
                int ellW = font.width(ell);
                int maxRemain = Math.max(0, maxWidthPx - ellW);
                int suffixLen = path.length();

                while (suffixLen > 0 && font.width(path.substring(path.length() - suffixLen)) > maxRemain) {
                    suffixLen--;
                }
                String suffix = suffixLen > 0 ? path.substring(path.length() - suffixLen) : "";
                displayPath = ell + suffix;
            }
        }

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(displayPath), pathX, cy + 10, DraggableWindow.textPrimaryColor(), false);

        guiGraphics.fill(cx, cy + 35, cx + 200, cy + 55, DraggableWindow.darkTheme ? 0xFF2B2B2B : 0xFFBFBFBF);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Search:"), cx + 5, cy + 40, DraggableWindow.textPrimaryColor(), false);

        int btnW = 92, btnH = 18;
        int bx = cx;
        int by = cy + 60;

        guiGraphics.fill(bx, by, bx + btnW, by + btnH, DraggableWindow.darkTheme ? 0xFF555555 : 0xFF999999);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("New Folder"), bx + 8, by + 4, DraggableWindow.textPrimaryColor(), false);

        int fx = bx + btnW + 8;
        guiGraphics.fill(fx, by, fx + btnW, by + btnH, DraggableWindow.darkTheme ? 0xFF555555 : 0xFF999999);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("New Text File"), fx + 8, by + 4, DraggableWindow.textPrimaryColor(), false);

        int dxBtn = fx + btnW + 8;
        if (selectedFile != null) {
            guiGraphics.fill(dxBtn, by, dxBtn + btnW, by + btnH, DraggableWindow.darkTheme ? 0xFF555555 : 0xFF999999);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Delete"), dxBtn + 8, by + 4, DraggableWindow.textPrimaryColor(), false);
        }

        int rxBtn = dxBtn + (selectedFile != null ? btnW + 8 : 0);
        if (selectedFile != null) {
            guiGraphics.fill(rxBtn, by, rxBtn + btnW, by + btnH, DraggableWindow.darkTheme ? 0xFF555555 : 0xFF999999);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Rename"), rxBtn + 8, by + 4, DraggableWindow.textPrimaryColor(), false);
        }

        int oxBtn = rxBtn + (selectedFile != null ? btnW + 8 : 0);
        if (selectedFile != null) {
            guiGraphics.fill(oxBtn, by, oxBtn + btnW, by + btnH, DraggableWindow.darkTheme ? 0xFF555555 : 0xFF999999);
            guiGraphics.drawString(Minecraft.getInstance().font,
                    Component.literal(selectedFile.isDirectory() ? "Open" : "Edit"),
                    oxBtn + 8, by + 4, DraggableWindow.textPrimaryColor(), false);
        }

        int copyX = oxBtn + (selectedFile != null ? btnW + 8 : 0);
        if (selectedFile != null) {
            guiGraphics.fill(copyX, by, copyX + btnW, by + btnH, DraggableWindow.darkTheme ? 0xFF555555 : 0xFF999999);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Copy"), copyX + 8, by + 4, DraggableWindow.textPrimaryColor(), false);
        }

        int cutX = copyX + (selectedFile != null ? btnW + 8 : 0);
        if (selectedFile != null) {
            guiGraphics.fill(cutX, by, cutX + btnW, by + btnH, DraggableWindow.darkTheme ? 0xFF555555 : 0xFF999999);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Cut"), cutX + 8, by + 4, DraggableWindow.textPrimaryColor(), false);
        }

        int pasteX = cutX + (clipboardFile != null ? btnW + 8 : 0);
        if (clipboardFile != null) {
            guiGraphics.fill(pasteX, by, pasteX + btnW, by + btnH, DraggableWindow.darkTheme ? 0xFF555555 : 0xFF999999);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Paste"), pasteX + 8, by + 4, DraggableWindow.textPrimaryColor(), false);
        }

        int listY = by + btnH + 8;
        guiGraphics.fill(cx, listY, cx + cw, listY + 20, DraggableWindow.darkTheme ? 0xFF444444 : 0xFFBFBFBF);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Name"), cx + 6, listY + 6, DraggableWindow.textPrimaryColor(), false);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Date modified"), cx + cw/2 - 60, listY + 6, DraggableWindow.textPrimaryColor(), false);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Type"), cx + cw - 180, listY + 6, DraggableWindow.textPrimaryColor(), false);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Size"), cx + cw - 80, listY + 6, DraggableWindow.textPrimaryColor(), false);

        listY += 20;
        File[] files = getFileArray();

        if (files != null) {
            for (File file : files) {
                if (!showHiddenFiles && file.isHidden()) continue;

                boolean selected = selectedFile != null && selectedFile.equals(file);
                if (selected) guiGraphics.fill(cx, listY, cx + cw, listY + 18, DraggableWindow.selectionOverlayColor());

                int iconX = cx + 4;
                int iconY = listY + 1;
                if (file.isDirectory()) {
                    guiGraphics.fill(iconX, iconY, iconX + 16, iconY + 16, 0xFFFFFF00);
                } else {
                    guiGraphics.fill(iconX, iconY, iconX + 16, iconY + 16, 0xFF8888FF);
                }

                int rowH = 18;
                if (renaming && selectedFile != null && selectedFile.equals(file)) {
                    if (renameBox == null) {
                        int renameX = cx + 22;
                        int renameW = Math.max(80, Math.min(300, cw - 44));
                        int renameY = listY + (rowH - 16) / 2;
                        renameBox = new EditBox(Minecraft.getInstance().font, renameX, renameY, renameW, 16, Component.literal(file.getName()));
                        renameBox.setValue(file.getName());
                        renameBox.setFocused(true);
                    }
                     int renameX = cx + 22;
                     int renameY = listY + (rowH - 16) / 2;
                     int renameW = renameBox.getWidth();

                     int outlineColor = DraggableWindow.darkTheme ? 0xFF777777 : 0xFFDDDDDD;
                     guiGraphics.fill(renameX, renameY, renameX + renameW, renameY + 1, outlineColor);
                     guiGraphics.fill(renameX, renameY + 15, renameX + renameW, renameY + 16, outlineColor);
                     guiGraphics.fill(renameX, renameY, renameX + 1, renameY + 16, outlineColor);
                     guiGraphics.fill(renameX + renameW - 1, renameY, renameX + renameW, renameY + 16, outlineColor);

                     String val = renameBox.getValue();
                     Font font = Minecraft.getInstance().font;
                     guiGraphics.drawString(font, Component.literal(val), renameX + 2, renameY + 2, DraggableWindow.textPrimaryColor(), false);

                     int caretPos = renameBox.getCursorPosition();
                     String prefix = val.substring(0, Math.max(0, Math.min(caretPos, val.length())));
                     int caretX = renameX + 2 + font.width(prefix);
                     boolean caretVisible = (System.currentTimeMillis() / 500) % 2 == 0;
                     if (caretVisible) {
                         guiGraphics.fill(caretX, renameY + 2, caretX + 1, renameY + 14, DraggableWindow.textPrimaryColor());
                     }
                 } else {
                    guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(file.getName()), cx + 22, listY + 3, DraggableWindow.textPrimaryColor(), false);
                }

                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(getFormattedDate(file)), cx + cw/2 - 60, listY + 3, DraggableWindow.textSecondaryColor(), false);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(getFileType(file)), cx + cw - 180, listY + 3, DraggableWindow.textSecondaryColor(), false);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(getFormattedSize(file)), cx + cw - 80, listY + 3, DraggableWindow.textSecondaryColor(), false);

                listY += rowH + 2;
            }
        }
    }

    private String getFormattedDate(File file) {
        try {
            return DATE_FMT.format(Instant.ofEpochMilli(file.lastModified()));
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private String getFileType(File file) {
        if (file.isDirectory()) return "Folder";

        String name = file.getName();
        if (name.contains(".")) {
            String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
            switch (ext) {
                case "txt": return "Text Document";
                case "jpg": case "png": case "gif": return "Image File";
                case "mp3": case "wav": return "Audio File";
                case "mp4": case "avi": case "mov": return "Video File";
                default: return ext.toUpperCase() + " File";
            }
        }
        return "File";
    }

    private String getFormattedSize(File file) {
        if (file.isDirectory()) {
            File[] contents = file.listFiles();
            return contents != null ? contents.length + " items" : "Empty";
        }

        long size = file.length();
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return (size / 1024) + " KB";
        return (size / (1024 * 1024)) + " MB";
    }

    private String getCurrentPath() {
        try {
            return currentDirectory.getCanonicalPath();
        } catch (IOException e) {
            return currentDirectory.getAbsolutePath();
        }
    }

    private void renderTextEditor(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch) {
        guiGraphics.fill(cx, cy, cx + cw, cy + 30, DraggableWindow.darkTheme ? 0xFF2B2B2B : 0xFFF0F0F0);
        guiGraphics.drawString(Minecraft.getInstance().font,
                Component.literal("Editing: " + editingFileRef.getName()),
                cx + 10, cy + 10, DraggableWindow.textPrimaryColor(), false);

        guiGraphics.fill(cx + cw - 100, cy + 5, cx + cw - 10, cy + 25, DraggableWindow.accentColorARGB);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Save"), cx + cw - 85, cy + 10, DraggableWindow.contrastingColorFor(DraggableWindow.accentColorARGB), false);

        guiGraphics.fill(cx + cw - 200, cy + 5, cx + cw - 110, cy + 25, DraggableWindow.darkTheme ? 0xFF555555 : 0xFF999999);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Cancel"), cx + cw - 185, cy + 10, DraggableWindow.textPrimaryColor(), false);

        int editorY = cy + 40;
        int editorH = ch - 40;

        guiGraphics.fill(cx, editorY, cx + cw, editorY + editorH, DraggableWindow.darkTheme ? 0xFF1E1E1E : 0xFFCCCCCC);

        if (fileEditor == null) {
            fileEditor = new EditBox(Minecraft.getInstance().font, cx + 10, editorY + 10, cw - 20, editorH - 20, Component.literal(""));
            fileEditor.setMaxLength(Integer.MAX_VALUE);

            asyncManager.submitIOTask(() -> {
                try {
                    String content = new String(Files.readAllBytes(editingFileRef.toPath()));
                    asyncManager.executeOnMainThread(() -> {
                        fileEditor.setValue(content);
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            fileEditor.setFocused(true);
        }

        fileEditor.setWidth(cw - 20);
        fileEditor.setHeight(editorH - 20);
        fileEditor.render(guiGraphics, (int)mouseRelX, (int)mouseRelY, 0);
    }

    @Override
    public boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        this.mouseRelX = mouseRelX;
        this.mouseRelY = mouseRelY;

        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 32, cw = r[2] - 16, ch = r[3] - 40;

        if (mouseRelX >= cx + cw - 120 && mouseRelX <= cx + cw - 90 &&
                mouseRelY >= cy + 5 && mouseRelY <= cy + 25) {
            navigateBack();
            return true;
        }

        if (mouseRelX >= cx + cw - 80 && mouseRelX <= cx + cw - 50 &&
                mouseRelY >= cy + 5 && mouseRelY <= cy + 25) {
            navigateForward();
            return true;
        }

        if (mouseRelX >= cx + cw - 40 && mouseRelX <= cx + cw - 10 &&
                mouseRelY >= cy + 5 && mouseRelY <= cy + 25) {
            navigateUp();
            return true;
        }

        int btnW = 92, btnH = 18;
        int bx = cx;
        int by = cy + 60;

        if (mouseRelX >= bx && mouseRelX <= bx + btnW && mouseRelY >= by && mouseRelY <= by + btnH) {
            createNewFolder();
            return true;
        }

        int fx = bx + btnW + 8;
        if (mouseRelX >= fx && mouseRelX <= fx + btnW && mouseRelY >= by && mouseRelY <= by + btnH) {
            createNewTextFile();
            return true;
        }

        int dxBtn = fx + btnW + 8;
        if (selectedFile != null && mouseRelX >= dxBtn && mouseRelX <= dxBtn + btnW && mouseRelY >= by && mouseRelY <= by + btnH) {
            deleteSelected();
            return true;
        }

        int rxBtn = dxBtn + (selectedFile != null ? btnW + 8 : 0);
        if (selectedFile != null && mouseRelX >= rxBtn && mouseRelX <= rxBtn + btnW && mouseRelY >= by && mouseRelY <= by + btnH) {
            startRenaming();
            return true;
        }

        int oxBtn = rxBtn + (selectedFile != null ? btnW + 8 : 0);
        if (selectedFile != null && mouseRelX >= oxBtn && mouseRelX <= oxBtn + btnW && mouseRelY >= by && mouseRelY <= by + btnH) {
            if (selectedFile.isDirectory()) {
                navigateTo(selectedFile);
            } else {
                openFileForEditing(selectedFile);
            }
            return true;
        }

        int copyX = oxBtn + (selectedFile != null ? btnW + 8 : 0);
        if (selectedFile != null && mouseRelX >= copyX && mouseRelX <= copyX + btnW && mouseRelY >= by && mouseRelY <= by + btnH) {
            clipboardFile = selectedFile;
            isCutOperation = false;
            return true;
        }

        int cutX = copyX + (selectedFile != null ? btnW + 8 : 0);
        if (selectedFile != null && mouseRelX >= cutX && mouseRelX <= cutX + btnW && mouseRelY >= by && mouseRelY <= by + btnH) {
            clipboardFile = selectedFile;
            isCutOperation = true;
            return true;
        }

        int pasteX = cutX + (clipboardFile != null ? btnW + 8 : 0);
        if (clipboardFile != null && mouseRelX >= pasteX && mouseRelX <= pasteX + btnW && mouseRelY >= by && mouseRelY <= by + btnH) {
            pasteFile();
            return true;
        }

        int listY = by + btnH + 8 + 20;
        File[] files = getFileArray();

        if (files != null) {
            for (File file : files) {
                if (!showHiddenFiles && file.isHidden()) continue;

                int rowH = 18;
                if (mouseRelX >= cx && mouseRelX <= cx + cw &&
                        mouseRelY >= listY && mouseRelY <= listY + rowH) {

                    if (renaming && selectedFile != null && selectedFile.equals(file)) {
                        if (renameBox != null) {
                            int renameX = cx + 22;
                            int renameW = Math.max(80, Math.min(300, cw - 44));
                            int renameY = listY + (rowH - 16) / 2;
                            int renameH = 16;
                            if (mouseRelX >= renameX && mouseRelX <= renameX + renameW && mouseRelY >= renameY && mouseRelY <= renameY + renameH) {
                                renameBox.mouseClicked(mouseRelX, mouseRelY, button);
                                return true;
                            }
                        }
                        finishRenaming();
                    } else {
                         selectedFile = file;
                         renaming = false;

                         long now = System.currentTimeMillis();
                         if (button == 0 && lastClickedFile != null && lastClickedFile.equals(file) && now - lastClickTime < 500) {
                             if (file.isDirectory()) {
                                 navigateTo(file);
                             } else {
                                 openFileForEditing(file);
                             }

                             lastClickedFile = null;
                             lastClickTime = 0;

                         } else {
                             lastClickedFile = file;
                             lastClickTime = now;
                         }
                     }
                     return true;
                 }
                 listY += rowH + 2;
              }
          }

         return false;
     }

    private void navigateTo(File directory) {
        if (directory.isDirectory()) {
            if (!isWithinPlayerDir(directory)) return;

            if (historyPointer < navigationHistory.size() - 1) {
                navigationHistory.subList(historyPointer + 1, navigationHistory.size()).clear();
            }
            navigationHistory.add(currentDirectory);
            historyPointer = navigationHistory.size() - 1;

            currentDirectory = directory;
            selectedFile = null;
            renaming = false;
            inSearchMode = false;
        }
    }

    private void navigateBack() {
        int target = historyPointer - 1;
        while (target >= 0) {
            File cand = navigationHistory.get(target);
            if (isWithinPlayerDir(cand)) {
                historyPointer = target;
                currentDirectory = cand;
                selectedFile = null;
                renaming = false;
                return;
            }
            target--;
        }
    }

    private void navigateForward() {
        int target = historyPointer + 1;
        while (target < navigationHistory.size()) {
            File cand = navigationHistory.get(target);
            if (isWithinPlayerDir(cand)) {
                historyPointer = target;
                currentDirectory = cand;
                selectedFile = null;
                renaming = false;
                return;
            }
            target++;
        }
    }

    private void navigateUp() {
        File parent = currentDirectory.getParentFile();
        if (parent != null && isWithinPlayerDir(parent)) {
            navigateTo(parent);
        }
    }

    private File getPlayerBaseDir() {
        File base = FilesManager.getPlayerDataDir();
        try {
            return base.getCanonicalFile();
        } catch (IOException e) {
            return base.getAbsoluteFile();
        }
    }

    private boolean isWithinPlayerDir(File dir) {
        if (dir == null) return false;
        try {
            File cand = dir.getCanonicalFile();
            File base = getPlayerBaseDir();
            while (cand != null) {
                if (cand.equals(base)) return true;
                cand = cand.getParentFile();
            }
        } catch (IOException e) {}
        return false;
    }

    private void createNewFolder() {
        String base = "New Folder";
        String name = base;
        int idx = 1;
        while (new File(currentDirectory, name).exists()) {
            name = base + " (" + idx + ")";
            idx++;
        }

        File newFolder = new File(currentDirectory, name);
        if (newFolder.mkdir()) {
            selectedFile = newFolder;
            startRenaming();
        }
    }

    private void createNewTextFile() {
        String base = "New Text File.txt";
        String name = base;
        int idx = 1;
        while (new File(currentDirectory, name).exists()) {
            name = "New Text File (" + idx + ").txt";
            idx++;
        }

        File newFile = new File(currentDirectory, name);
        try {
            if (newFile.createNewFile()) {
                selectedFile = newFile;
                startRenaming();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startRenaming() {
        if (selectedFile == null) return;
        renaming = true;
        renameBox = null;
    }

    private void finishRenaming() {
        if (selectedFile == null || renameBox == null || renameBox.getValue().isEmpty()) return;

        String newName = renameBox.getValue();
        File newFile = new File(selectedFile.getParentFile(), newName);

        if (!newFile.exists()) {
            if (selectedFile.renameTo(newFile)) {
                selectedFile = newFile;
            }
        }

        renaming = false;
        renameBox = null;
    }

    private void deleteSelected() {
        if (selectedFile == null) return;

        if (selectedFile.isDirectory()) {
            deleteDirectory(selectedFile);
        } else {
            selectedFile.delete();
        }

        selectedFile = null;
    }

    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }

    private void openFileForEditing(File file) {
        if (file.isDirectory() || !file.canRead()) return;

        String name = file.getName().toLowerCase();
        if (name.endsWith(".txt") || name.endsWith(".json") || name.endsWith(".xml") ||
                name.endsWith(".html") || name.endsWith(".css") || name.endsWith(".js")) {
            NotepadApp.setFileToOpen(file);
            DesktopScreen desktop = (DesktopScreen) Minecraft.getInstance().screen;
            if (desktop != null) {
                desktop.openAppSingle("notepad", 800, 600);
            }
        } else {
            editingFile = true;
            editingFileRef = file;
            fileEditor = null;
        }
    }

    private void pasteFile() {
        if (clipboardFile == null || !clipboardFile.exists()) return;

        try {
            File destination = new File(currentDirectory, clipboardFile.getName());

            int counter = 1;
            String baseName = clipboardFile.getName();
            if (baseName.contains(".")) {
                int dotIndex = baseName.lastIndexOf('.');
                String namePart = baseName.substring(0, dotIndex);
                String extPart = baseName.substring(dotIndex);

                while (destination.exists()) {
                    destination = new File(currentDirectory, namePart + " (" + counter + ")" + extPart);
                    counter++;
                }
            } else {
                while (destination.exists()) {
                    destination = new File(currentDirectory, baseName + " (" + counter + ")");
                    counter++;
                }
            }

            if (clipboardFile.isDirectory()) {
                copyDirectory(clipboardFile, destination);
            } else {
                Files.copy(clipboardFile.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            if (isCutOperation) {
                if (clipboardFile.isDirectory()) {
                    deleteDirectory(clipboardFile);
                } else {
                    clipboardFile.delete();
                }
                clipboardFile = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void copyDirectory(File source, File destination) throws IOException {
        if (source.isDirectory()) {
            if (!destination.exists()) {
                destination.mkdir();
            }

            String[] files = source.list();
            if (files != null) {
                for (String file : files) {
                    File srcFile = new File(source, file);
                    File destFile = new File(destination, file);
                    copyDirectory(srcFile, destFile);
                }
            }
        } else {
            Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public boolean keyPressed(DraggableWindow window, int keyCode, int scanCode, int modifiers) {
        if (editingFile && fileEditor != null) {
            if (keyCode == 257) {
                asyncManager.submitIOTask(() -> {
                    try {
                        Files.write(editingFileRef.toPath(), fileEditor.getValue().getBytes());
                        asyncManager.executeOnMainThread(() -> {
                            editingFile = false;
                            editingFileRef = null;
                            fileEditor = null;
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                return true;
            } else if (keyCode == 256) {
                editingFile = false;
                editingFileRef = null;
                fileEditor = null;
                return true;
            }
            return fileEditor.keyPressed(keyCode, scanCode, modifiers);
        }

        if (renaming && renameBox != null) {
            if (keyCode == 257) {
                finishRenaming();
                return true;
            } else if (keyCode == 256) {
                renaming = false;
                renameBox = null;
                return true;
            }
            return renameBox.keyPressed(keyCode, scanCode, modifiers);
        }

        return false;
    }

    @Override
    public boolean charTyped(DraggableWindow window, char codePoint, int modifiers) {
        if (editingFile && fileEditor != null) {
            return fileEditor.charTyped(codePoint, modifiers);
        }
        if (renaming && renameBox != null) {
            return renameBox.charTyped(codePoint, modifiers);
        }
        return false;
    }

    @Override
    public void mouseReleased(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        if (fileEditor != null) fileEditor.mouseReleased(mouseRelX, mouseRelY, button);
        if (renameBox != null) renameBox.mouseReleased(mouseRelX, mouseRelY, button);
    }

    @Override
    public boolean mouseDragged(DraggableWindow window, double mouseRelX, double mouseRelY, double dx, double dy) {
        return false;
    }

    @Override
    public boolean onClose(DraggableWindow window) {
        return true;
    }

    @Override
    public void tick() {
    }
}