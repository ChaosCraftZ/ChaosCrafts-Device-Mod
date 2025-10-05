package net.chaoscraft.chaoscrafts_device_mod.Client.Apps.BaseApps;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager.IApp;
import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DesktopScreen;
import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DraggableWindow;
import net.chaoscraft.chaoscrafts_device_mod.Core.AsyncorAPI.AsyncRuntime;
import net.chaoscraft.chaoscrafts_device_mod.Core.Util.FileSystem.FilesManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FilesApp implements IApp {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private final AsyncRuntime asyncRuntime = AsyncRuntime.get();
    private static final Logger LOGGER = LogManager.getLogger();

    // Current directory state
    private File currentDirectory;
    private File selectedFile = null;
    private boolean renaming = false;
    private EditBox renameBox;

    // Text editor state
    private boolean editingFile = false;
    private EditBox fileEditor;
    private File editingFileRef = null;

    // UI state
    private double mouseRelX, mouseRelY;
    private boolean showHiddenFiles = false;
    private List<File> searchResults = new ArrayList<>();
    private boolean inSearchMode = false;

    // Navigation history
    private final LinkedList<File> navigationHistory = new LinkedList<>();
    private int historyPointer = -1;

    // File operations state
    private File clipboardFile = null;
    private boolean isCutOperation = false;

    @Override
    public void onOpen(DraggableWindow window) {
        this.currentDirectory = new File(FilesManager.getPlayerDataDir(), "Documents");
        this.currentDirectory.mkdirs();

        // Initialize with some default files if empty
    asyncRuntime.submitIo(this::initializeDefaultFiles);
    }

    private void initializeDefaultFiles() {
        File readme = new File(currentDirectory, "README.txt");
        if (!readme.exists()) {
            try (FileWriter writer = new FileWriter(readme)) {
                writer.write("Welcome to your virtual PC!\nThis is a sample text file.");
            } catch (IOException e) {
                LOGGER.warn("Failed to create default README: {}", e.getMessage());
            }
        }

        File notes = new File(currentDirectory, "Notes.txt");
        if (!notes.exists()) {
            try (FileWriter writer = new FileWriter(notes)) {
                writer.write("Important notes:\n- Taskbar at the bottom\n- Search functionality\n- File browser with columns");
            } catch (IOException e) {
                LOGGER.warn("Failed to create default Notes: {}", e.getMessage());
            }
        }
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        this.mouseRelX = mouseRelX;
        this.mouseRelY = mouseRelY;

    int[] r = window.getRenderRect(26);
    int cx = r[0] + 8, cy = r[1] + 32, cw = r[2] - 16, ch = r[3] - 40;

        // If we're editing a file, show the text editor
        if (editingFile && editingFileRef != null) {
            renderTextEditor(guiGraphics, cx, cy, cw, ch);
            return;
        }

        // Header with navigation
        guiGraphics.fill(cx, cy, cx + cw, cy + 30, 0xFF2B2B2B);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Files"), cx + 10, cy + 10, 0xFFFFFFFF, false);

        // Navigation buttons
        guiGraphics.fill(cx + cw - 120, cy + 5, cx + cw - 90, cy + 25, 0xFF555555);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("◀"), cx + cw - 115, cy + 10, 0xFFFFFFFF, false);

        guiGraphics.fill(cx + cw - 80, cy + 5, cx + cw - 50, cy + 25, 0xFF555555);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("▶"), cx + cw - 75, cy + 10, 0xFFFFFFFF, false);

        guiGraphics.fill(cx + cw - 40, cy + 5, cx + cw - 10, cy + 25, 0xFF555555);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("⬆"), cx + cw - 35, cy + 10, 0xFFFFFFFF, false);

        // Current path
        String path = getCurrentPath();
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(path), cx + 100, cy + 10, 0xFFFFFFFF, false);

        // Search box
        guiGraphics.fill(cx, cy + 35, cx + 200, cy + 55, 0xFF2B2B2B);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Search:"), cx + 5, cy + 40, 0xFFFFFFFF, false);

        // File operation buttons
        int btnW = 92, btnH = 18;
        int bx = cx;
        int by = cy + 60;

        // New Folder button
        guiGraphics.fill(bx, by, bx + btnW, by + btnH, 0xFF555555);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("New Folder"), bx + 8, by + 4, 0xFFFFFFFF, false);

        // New Text File button
        int fx = bx + btnW + 8;
        guiGraphics.fill(fx, by, fx + btnW, by + btnH, 0xFF555555);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("New Text File"), fx + 8, by + 4, 0xFFFFFFFF, false);

        // Delete button (only if something is selected)
        int dxBtn = fx + btnW + 8;
        if (selectedFile != null) {
            guiGraphics.fill(dxBtn, by, dxBtn + btnW, by + btnH, 0xFF555555);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Delete"), dxBtn + 8, by + 4, 0xFFFFFFFF, false);
        }

        // Rename button (only if something is selected)
        int rxBtn = dxBtn + (selectedFile != null ? btnW + 8 : 0);
        if (selectedFile != null) {
            guiGraphics.fill(rxBtn, by, rxBtn + btnW, by + btnH, 0xFF555555);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Rename"), rxBtn + 8, by + 4, 0xFFFFFFFF, false);
        }

        // Open/Edit button (only if something is selected)
        int oxBtn = rxBtn + (selectedFile != null ? btnW + 8 : 0);
        if (selectedFile != null) {
            guiGraphics.fill(oxBtn, by, oxBtn + btnW, by + btnH, 0xFF555555);
            guiGraphics.drawString(Minecraft.getInstance().font,
                    Component.literal(selectedFile.isDirectory() ? "Open" : "Edit"),
                    oxBtn + 8, by + 4, 0xFFFFFFFF, false);
        }

        // Copy/Cut/Paste buttons
        int copyX = oxBtn + (selectedFile != null ? btnW + 8 : 0);
        if (selectedFile != null) {
            guiGraphics.fill(copyX, by, copyX + btnW, by + btnH, 0xFF555555);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Copy"), copyX + 8, by + 4, 0xFFFFFFFF, false);
        }

        int cutX = copyX + (selectedFile != null ? btnW + 8 : 0);
        if (selectedFile != null) {
            guiGraphics.fill(cutX, by, cutX + btnW, by + btnH, 0xFF555555);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Cut"), cutX + 8, by + 4, 0xFFFFFFFF, false);
        }

        int pasteX = cutX + (clipboardFile != null ? btnW + 8 : 0);
        if (clipboardFile != null) {
            guiGraphics.fill(pasteX, by, pasteX + btnW, by + btnH, 0xFF555555);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Paste"), pasteX + 8, by + 4, 0xFFFFFFFF, false);
        }

        // Column headers
        int listY = by + btnH + 8;
        guiGraphics.fill(cx, listY, cx + cw, listY + 20, 0xFF444444);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Name"), cx + 6, listY + 6, 0xFFFFFFFF, false);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Date modified"), cx + cw/2 - 60, listY + 6, 0xFFFFFFFF, false);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Type"), cx + cw - 180, listY + 6, 0xFFFFFFFF, false);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Size"), cx + cw - 80, listY + 6, 0xFFFFFFFF, false);

        // File list
        listY += 20;
        File[] files = inSearchMode ?
                searchResults.toArray(new File[0]) :
                currentDirectory.listFiles();

        if (files != null) {
            // Sort files: directories first, then by name
            Arrays.sort(files, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            });

            for (File file : files) {
                if (!showHiddenFiles && file.isHidden()) continue;

                boolean selected = selectedFile != null && selectedFile.equals(file);
                if (selected) guiGraphics.fill(cx, listY, cx + cw, listY + 18, 0x553333FF);

                // Icon based on file type
                int iconX = cx + 4;
                int iconY = listY + 1;
                if (file.isDirectory()) {
                    guiGraphics.fill(iconX, iconY, iconX + 16, iconY + 16, 0xFFFFFF00);
                } else {
                    guiGraphics.fill(iconX, iconY, iconX + 16, iconY + 16, 0xFF8888FF);
                }

                // Name (with editing if renaming)
                if (renaming && selectedFile != null && selectedFile.equals(file)) {
                    if (renameBox == null) {
                        renameBox = new EditBox(Minecraft.getInstance().font, cx + 22, listY, 200, 16, Component.literal(file.getName()));
                        renameBox.setValue(file.getName());
                        renameBox.setFocused(true);
                    }
                    renameBox.render(guiGraphics, (int)mouseRelX, (int)mouseRelY, partialTick);
                } else {
                    guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(file.getName()), cx + 22, listY + 3, 0xFFFFFFFF, false);
                }

                // Date, type, and size
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(getFormattedDate(file)), cx + cw/2 - 60, listY + 3, 0xFFFFFFFF, false);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(getFileType(file)), cx + cw - 180, listY + 3, 0xFFFFFFFF, false);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(getFormattedSize(file)), cx + cw - 80, listY + 3, 0xFFFFFFFF, false);

                listY += 20;
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
        // Editor header
        guiGraphics.fill(cx, cy, cx + cw, cy + 30, 0xFF2B2B2B);
        guiGraphics.drawString(Minecraft.getInstance().font,
                Component.literal("Editing: " + editingFileRef.getName()),
                cx + 10, cy + 10, 0xFFFFFFFF, false);

        // Save button
        guiGraphics.fill(cx + cw - 100, cy + 5, cx + cw - 10, cy + 25, 0xFF4C7BD1);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Save"), cx + cw - 85, cy + 10, 0xFFFFFFFF, false);

        // Cancel button
        guiGraphics.fill(cx + cw - 200, cy + 5, cx + cw - 110, cy + 25, 0xFF555555);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Cancel"), cx + cw - 185, cy + 10, 0xFFFFFFFF, false);

        // Text editor area
        int editorY = cy + 40;
        int editorH = ch - 40;

        guiGraphics.fill(cx, editorY, cx + cw, editorY + editorH, 0xFF1E1E1E);

        if (fileEditor == null) {
            fileEditor = new EditBox(Minecraft.getInstance().font, cx + 10, editorY + 10, cw - 20, editorH - 20, Component.literal(""));
            fileEditor.setMaxLength(Integer.MAX_VALUE);

            // Load file content asynchronously
            asyncRuntime.submitIoThenClient(() -> Files.readString(editingFileRef.toPath()), content -> {
                if (fileEditor != null) {
                    fileEditor.setValue(content);
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
    int cx = r[0] + 8;
    int cy = r[1] + 32;
    int cw = r[2] - 16;

        // Handle navigation buttons
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

        // Handle file operation buttons
        int btnW = 92, btnH = 18;
        int bx = cx;
        int by = cy + 60;

        // New Folder button
        if (mouseRelX >= bx && mouseRelX <= bx + btnW && mouseRelY >= by && mouseRelY <= by + btnH) {
            createNewFolder();
            return true;
        }

        // New Text File button
        int fx = bx + btnW + 8;
        if (mouseRelX >= fx && mouseRelX <= fx + btnW && mouseRelY >= by && mouseRelY <= by + btnH) {
            createNewTextFile();
            return true;
        }

        // Delete button
        int dxBtn = fx + btnW + 8;
        if (selectedFile != null && mouseRelX >= dxBtn && mouseRelX <= dxBtn + btnW && mouseRelY >= by && mouseRelY <= by + btnH) {
            deleteSelected();
            return true;
        }

        // Rename button
        int rxBtn = dxBtn + (selectedFile != null ? btnW + 8 : 0);
        if (selectedFile != null && mouseRelX >= rxBtn && mouseRelX <= rxBtn + btnW && mouseRelY >= by && mouseRelY <= by + btnH) {
            startRenaming();
            return true;
        }

        // Open/Edit button
        int oxBtn = rxBtn + (selectedFile != null ? btnW + 8 : 0);
        if (selectedFile != null && mouseRelX >= oxBtn && mouseRelX <= oxBtn + btnW && mouseRelY >= by && mouseRelY <= by + btnH) {
            if (selectedFile.isDirectory()) {
                navigateTo(selectedFile);
            } else {
                openFileForEditing(selectedFile);
            }
            return true;
        }

        // Copy button
        int copyX = oxBtn + (selectedFile != null ? btnW + 8 : 0);
        if (selectedFile != null && mouseRelX >= copyX && mouseRelX <= copyX + btnW && mouseRelY >= by && mouseRelY <= by + btnH) {
            clipboardFile = selectedFile;
            isCutOperation = false;
            return true;
        }

        // Cut button
        int cutX = copyX + (selectedFile != null ? btnW + 8 : 0);
        if (selectedFile != null && mouseRelX >= cutX && mouseRelX <= cutX + btnW && mouseRelY >= by && mouseRelY <= by + btnH) {
            clipboardFile = selectedFile;
            isCutOperation = true;
            return true;
        }

        // Paste button
        int pasteX = cutX + (clipboardFile != null ? btnW + 8 : 0);
        if (clipboardFile != null && mouseRelX >= pasteX && mouseRelX <= pasteX + btnW && mouseRelY >= by && mouseRelY <= by + btnH) {
            pasteFile();
            return true;
        }

        // File list clicks
        int listY = by + btnH + 8 + 20;
        File[] files = inSearchMode ?
                searchResults.toArray(new File[0]) :
                currentDirectory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (!showHiddenFiles && file.isHidden()) continue;

                if (mouseRelX >= cx && mouseRelX <= cx + cw &&
                        mouseRelY >= listY && mouseRelY <= listY + 18) {

                    if (renaming && selectedFile != null && selectedFile.equals(file)) {
                        finishRenaming();
                    } else {
                        selectedFile = file;
                        renaming = false;

                        // Double click to open
                        if (button == 0 && System.currentTimeMillis() - lastClickTime < 500) {
                            if (file.isDirectory()) {
                                navigateTo(file);
                            } else {
                                openFileForEditing(file);
                            }
                        }
                        lastClickTime = System.currentTimeMillis();
                    }
                    return true;
                }
                listY += 20;
            }
        }

        return false;
    }

    private void navigateTo(File directory) {
        if (directory.isDirectory()) {
            // Add to history
            if (historyPointer < navigationHistory.size() - 1) {
                // We're in the middle of history, remove everything after current position
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
        if (historyPointer > 0) {
            historyPointer--;
            currentDirectory = navigationHistory.get(historyPointer);
            selectedFile = null;
            renaming = false;
        }
    }

    private void navigateForward() {
        if (historyPointer < navigationHistory.size() - 1) {
            historyPointer++;
            currentDirectory = navigationHistory.get(historyPointer);
            selectedFile = null;
            renaming = false;
        }
    }

    private void navigateUp() {
        File parent = currentDirectory.getParentFile();
        if (parent != null) {
            navigateTo(parent);
        }
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
            LOGGER.warn("Failed to create new text file: {}", e.getMessage());
        }
    }

    private void startRenaming() {
        if (selectedFile == null) return;
        renaming = true;
        renameBox = new EditBox(Minecraft.getInstance().font, 0, 0, 200, 16, Component.literal(selectedFile.getName()));
        renameBox.setValue(selectedFile.getName());
        renameBox.setFocused(true);
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

        // Check if it's a text file
        String name = file.getName().toLowerCase();
        if (name.endsWith(".txt") || name.endsWith(".json") || name.endsWith(".xml") ||
                name.endsWith(".html") || name.endsWith(".css") || name.endsWith(".js")) {
            // Open with Notepad
            NotepadApp.setFileToOpen(file);
            DesktopScreen desktop = (DesktopScreen) Minecraft.getInstance().screen;
            if (desktop != null) {
                desktop.openAppSingle("notepad", 800, 600);
            }
        } else {
            // Use built-in editor for other files
            editingFile = true;
            editingFileRef = file;
            fileEditor = null;
        }
    }

    private void pasteFile() {
        if (clipboardFile == null || !clipboardFile.exists()) return;

        try {
            File destination = new File(currentDirectory, clipboardFile.getName());

            // Handle name conflicts
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

            // If it was a cut operation, delete the original
            if (isCutOperation) {
                if (clipboardFile.isDirectory()) {
                    deleteDirectory(clipboardFile);
                } else {
                    clipboardFile.delete();
                }
                clipboardFile = null;
            }
        } catch (IOException e) {
            LOGGER.warn("Error while pasting file: {}", e.getMessage());
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
            if (keyCode == 257) { // Enter key - Save
                asyncRuntime.submitIoThenClient(() -> {
                    Files.write(editingFileRef.toPath(), fileEditor.getValue().getBytes());
                    return null;
                }, ignored -> {
                    editingFile = false;
                    editingFileRef = null;
                    fileEditor = null;
                });
                return true;
            } else if (keyCode == 256) { // Escape key - Cancel
                editingFile = false;
                editingFileRef = null;
                fileEditor = null;
                return true;
            }
            return fileEditor.keyPressed(keyCode, scanCode, modifiers);
        }

        if (renaming && renameBox != null) {
            if (keyCode == 257) { // Enter key
                finishRenaming();
                return true;
            } else if (keyCode == 256) { // Escape key
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
        // Allow the window to close normally (no special veto)
        return true;
    }

    @Override
    public void tick() {
        // Handle any periodic updates if needed
    }

    private long lastClickTime = 0;
}