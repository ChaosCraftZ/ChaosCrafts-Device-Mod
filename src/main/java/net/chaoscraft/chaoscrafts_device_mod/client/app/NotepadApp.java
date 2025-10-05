package net.chaoscraft.chaoscrafts_device_mod.client.app;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;
import net.chaoscraft.chaoscrafts_device_mod.client.fs.FilesManager;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DraggableWindow;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class NotepadApp implements IApp {
    private DraggableWindow window;
    private final AsyncTaskManager asyncManager = AsyncTaskManager.getInstance();

    private MultiLineEditor textArea;
    private final AtomicReference<File> currentFile = new AtomicReference<>(null);
    private final AtomicBoolean isModified = new AtomicBoolean(false);

    private double mouseRelX, mouseRelY;

    // Save dialog
    private boolean showSaveDialog = false;
    private EditBox saveFileNameInput;
    private EditBox filePathInput;

    // Open dialog state
    private boolean showOpenDialog = false;
    private EditBox openFileNameInput;
    private EditBox openFilePathInput;
    // Save-on-exit dialog state
    private boolean showSaveOnExitDialog = false;

    // Undo/redo
    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();

    // Recent files
    private final LinkedList<String> recentFiles = new LinkedList<>();
    private final File recentFilesFile = new File(FilesManager.getPlayerDataDir(), "notepad_recent.txt");

    private String statusMessage = "";
    private long statusMessageTime = 0L;

    // Menu bar state
    private boolean showMenuBar = true;
    private boolean fileMenuOpen = false;
    private int fileMenuWidth = 180, fileMenuItemHeight = 22;
    private int fileMenuSelected = -1;
    private final String[] fileMenuItems = new String[] {
            "New", "Open...", "Save", "Save As...", "Print...", "-", "Exit"
    };

    private static File fileToOpen = null;
    public static void setFileToOpen(File f) { fileToOpen = f; }

    @Override
    public void onOpen(DraggableWindow window) {
        this.window = window;
        initializeComponents();
        loadRecentFiles();
        if (fileToOpen != null) { openFile(fileToOpen); fileToOpen = null; }
        else newDocument();
    }

    private void initializeComponents() {
        Minecraft mc = Minecraft.getInstance();
        this.textArea = new MultiLineEditor(mc.font);
        this.textArea.setResponder(this::onTextChanged);

        this.saveFileNameInput = new EditBox(mc.font, 0, 0, 200, 16, Component.empty());
        this.saveFileNameInput.setMaxLength(255);
        this.filePathInput = new EditBox(mc.font, 0, 0, 300, 16, Component.empty());
        this.filePathInput.setMaxLength(500);

        this.openFileNameInput = new EditBox(mc.font, 0, 0, 200, 16, Component.empty());
        this.openFileNameInput.setMaxLength(255);
        this.openFilePathInput = new EditBox(mc.font, 0, 0, 300, 16, Component.empty());
        this.openFilePathInput.setMaxLength(500);

        pushUndoState();
    }

    private void onTextChanged(String newText) {
        if (!isModified.get()) {
            isModified.set(true);
            updateWindowTitle();
        }
        pushUndoState();
        redoStack.clear();
    }

    private void updateWindowTitle() {
        if (window == null) return;
        String base = currentFile.get() != null ? currentFile.get().getName() : "Untitled";
        window.appName = base + (isModified.get() ? "*" : "") + " - Notepad";
    }

    private void newDocument() {
        if (!checkSaveNeeded()) return;
        textArea.setValue("");
        currentFile.set(null);
        isModified.set(false);
        pushUndoState();
        updateWindowTitle();
        showStatus("New document");
    }

    private boolean checkSaveNeeded() {
        if (!isModified.get()) return true;
        File f = currentFile.get();
        if (f != null) { asyncSaveDocument(f); return true; }
        return true;
    }

    private void openFile(File file) {
        asyncManager.submitIOTask(() -> {
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                String content = new String(data);
                asyncManager.executeOnMainThread(() -> {
                    textArea.setValue(content);
                    currentFile.set(file);
                    isModified.set(false);
                    pushUndoState();
                    updateWindowTitle();
                    showStatus("Opened: " + file.getName());
                    addRecent(file);
                });
            } catch (IOException e) {
                asyncManager.executeOnMainThread(() -> showStatus("Error opening: " + e.getMessage()));
            }
        });
    }

    private void saveDocument() {
        File f = currentFile.get();
        if (f == null) {
            // Quick-save: if no file is open, save immediately to player Documents/Untitled*.txt instead of prompting
            File dir = new File(FilesManager.getPlayerDataDir(), "Documents");
            if (!dir.exists()) dir.mkdirs();
            File saveFile = new File(dir, "Untitled.txt");
            int idx = 1;
            while (saveFile.exists()) {
                saveFile = new File(dir, "Untitled(" + idx + ").txt");
                idx++;
            }
            asyncSaveDocument(saveFile);
        } else {
            asyncSaveDocument(f);
        }
    }

    private final java.util.concurrent.atomic.AtomicBoolean closeAfterSave = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicBoolean pendingCloseOnSave = new java.util.concurrent.atomic.AtomicBoolean(false);

    private void asyncSaveDocument(File file) {
        asyncManager.submitIOTask(() -> {
            try {
                if (file.getParentFile() != null) file.getParentFile().mkdirs();
                Files.write(file.toPath(), textArea.getValue().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                asyncManager.executeOnMainThread(() -> {
                    currentFile.set(file);
                    isModified.set(false);
                    updateWindowTitle();
                    showStatus("Saved: " + file.getName());
                    playSound(SoundEvents.UI_BUTTON_CLICK.get());
                    addRecent(file);
                    if (closeAfterSave.getAndSet(false)) {
                        // request the window to close (fade out) after save completes rather than closing the whole GUI
                        if (this.window != null) {
                            this.window.requestClose();
                        } else {
                            Minecraft.getInstance().setScreen(null);
                        }
                    }
                });
            } catch (IOException e) {
                asyncManager.executeOnMainThread(() -> showStatus("Error saving: " + e.getMessage()));
            }
        });
    }

    private void showStatus(String m) { statusMessage = m; statusMessageTime = System.currentTimeMillis(); }
    private void playSound(net.minecraft.sounds.SoundEvent sound) { Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F)); }

    // undo/redo
    private void pushUndoState() {
        if (textArea == null) return;
        String v = textArea.getValue();
        if (v == null) v = "";
        if (undoStack.isEmpty() || !undoStack.peek().equals(v)) { undoStack.push(v); while (undoStack.size() > 200) undoStack.removeLast(); }
    }

    private void undo() {
        if (undoStack.size() <= 1) return;
        String cur = undoStack.poll(); if (cur == null) return; redoStack.push(cur); String prev = undoStack.peek(); if (prev != null) asyncManager.executeOnMainThread(() -> textArea.setValue(prev));
    }

    private void redo() { if (redoStack.isEmpty()) return; String n = redoStack.pop(); pushUndoState(); asyncManager.executeOnMainThread(() -> textArea.setValue(n)); }

    // recent files
    private void addRecent(File f) { try { String p = f.getAbsolutePath(); recentFiles.remove(p); recentFiles.addFirst(p); while (recentFiles.size() > 20) recentFiles.removeLast(); saveRecentFiles(); } catch (Exception ignored) {} }
    private void loadRecentFiles() { recentFiles.clear(); try { if (recentFilesFile.exists()) { List<String> lines = Files.readAllLines(recentFilesFile.toPath()); for (String l : lines) if (!l.isBlank()) recentFiles.add(l); } } catch (IOException ignored) {} }
    private void saveRecentFiles() { try { if (recentFilesFile.getParentFile() != null) recentFilesFile.getParentFile().mkdirs(); try (Writer w = new FileWriter(recentFilesFile, false)) { for (String s : recentFiles) w.write(s + "\n"); } } catch (IOException ignored) {} }

    private int[] getCursorLineCol() {
        try {
            int cursor = textArea.getCursorPos();
            String s = textArea.getValue(); if (s == null) s = "";
            if (cursor < 0) cursor = s.length();

            int line = 1, col = 1, pos = 0;
            for (int i = 0; i < s.length() && pos < cursor; i++) { char c = s.charAt(i); pos++; if (c == '\n') { line++; col = 1; } else col++; }
            return new int[]{line, col};
        } catch (Exception e) { return new int[]{1,1}; }
    }

    private int getEditCursorPos() {
        return textArea != null ? textArea.getCursorPos() : 0;
    }

    private void setEditCursorPos(int pos) {
        if (textArea != null) textArea.setCursorPos(pos);
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        this.mouseRelX = mouseRelX; this.mouseRelY = mouseRelY;
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16, ch = r[3] - 40;
        int textY = cy + 30, textHeight = ch - 30 - 20;

        // Menu bar
        if (showMenuBar) {
            guiGraphics.fill(cx, cy, cx + cw, cy + 24, DraggableWindow.darkTheme ? 0xFF2B2B2B : 0xFFF0F0F0);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("File"), cx + 8, cy + 6, DraggableWindow.textPrimaryColor(), false);
            // Save button in the menu bar for quick access
            int saveBtnX = cx + 64;
            int saveBtnY = cy + 3;
            int saveBtnW = 52;
            int saveBtnH = 18;
            guiGraphics.fill(saveBtnX, saveBtnY, saveBtnX + saveBtnW, saveBtnY + saveBtnH, 0xFF4CAF50);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Save"), saveBtnX + 10, saveBtnY + 4, DraggableWindow.textPrimaryColor(), false);
            // File menu dropdown
            if (fileMenuOpen) {
                int mx = cx + 8, my = cy + 24;
                int menuH = fileMenuItemHeight * (fileMenuItems.length + Math.min(5, recentFiles.size()));
                guiGraphics.fill(mx, my, mx + fileMenuWidth, my + menuH, DraggableWindow.darkTheme ? 0xFF444444 : 0xFFFFFFFF);
                int idx = 0;
                for (String item : fileMenuItems) {
                    if (item.equals("-")) {
                        guiGraphics.fill(mx, my + idx * fileMenuItemHeight, mx + fileMenuWidth, my + (idx + 1) * fileMenuItemHeight, DraggableWindow.darkTheme ? 0xFF222222 : 0xFFEFEFEF);
                    } else {
                        int bg = (fileMenuSelected == idx) ? (DraggableWindow.darkTheme ? 0xFF6666AA : 0xFFDDDDEE) : 0x00000000;
                        guiGraphics.fill(mx, my + idx * fileMenuItemHeight, mx + fileMenuWidth, my + (idx + 1) * fileMenuItemHeight, bg);
                        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(item), mx + 8, my + idx * fileMenuItemHeight + 4, DraggableWindow.textPrimaryColor(), false);
                    }
                    idx++;
                }
                // Recent files
                if (!recentFiles.isEmpty()) {
                    guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Recent files:"), mx + 8, my + idx * fileMenuItemHeight + 2, DraggableWindow.textSecondaryColor(), false);
                    idx++;
                    int rIdx = 0;
                    for (String rf : recentFiles) {
                        int bg = (fileMenuSelected == idx) ? (DraggableWindow.darkTheme ? 0xFF6666AA : 0xFFDDDDEE) : 0x00000000;
                        guiGraphics.fill(mx, my + idx * fileMenuItemHeight, mx + fileMenuWidth, my + (idx + 1) * fileMenuItemHeight, bg);
                        String shortName = new File(rf).getName();
                        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(shortName), mx + 16, my + idx * fileMenuItemHeight + 4, DraggableWindow.textPrimaryColor(), false);
                        idx++;
                        rIdx++;
                        if (rIdx >= 5) break;
                    }
                }
            }
        }

        // background for text area (use theme-aware color)
        int textBg = DraggableWindow.darkTheme ? 0xFF1E1E1E : 0xFFFAFAFA;
        guiGraphics.fill(cx, textY, cx + cw, textY + textHeight, textBg);

        textArea.setX(cx + 2); textArea.setY(textY + 2); textArea.setWidth(cw - 4); textArea.setHeight(textHeight - 4);
        int textColor = DraggableWindow.textPrimaryColor();
        textArea.setTextColor(textColor);
        textArea.render(guiGraphics, (int)this.mouseRelX, (int)this.mouseRelY, partialTick);

        int statusY = textY + textHeight;
        guiGraphics.fill(cx, statusY, cx + cw, statusY + 20, DraggableWindow.darkTheme ? 0xFF2B2B2B : 0xFFEEEEEE);
        int[] lc = getCursorLineCol();
        String name = currentFile.get() != null ? currentFile.get().getName() : "Untitled";
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(name + (isModified.get() ? " *" : "") + "  |  Ln " + lc[0] + ", Col " + lc[1]), cx + 5, statusY + 5, DraggableWindow.textPrimaryColor(), false);
        if (System.currentTimeMillis() - statusMessageTime < 3000) guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(statusMessage), cx + cw - 10 - Minecraft.getInstance().font.width(statusMessage), statusY + 5, DraggableWindow.textPrimaryColor(), false);

        if (showSaveDialog) renderSaveDialog(guiGraphics, cx, cy, cw, ch);
        if (showOpenDialog) renderOpenDialog(guiGraphics, cx, cy, cw, ch);
        if (showSaveOnExitDialog) renderSaveOnExitDialog(guiGraphics, cx, cy, cw, ch);
    }

    private void renderSaveDialog(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch) {
        int w = 420, h = 140;
        int x = cx + (cw - w) / 2;
        int y = cy + (ch - h) / 2;
        guiGraphics.fill(x, y, x + w, y + h, DraggableWindow.darkTheme ? 0xFF2B2B2B : 0xFFFFFFFF);
        guiGraphics.fill(x, y, x + w, y + 20, DraggableWindow.accentColorARGB);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Save Document"), x + 8, y + 6, DraggableWindow.textPrimaryColor(), false);

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("File name:"), x + 8, y + 34, DraggableWindow.textPrimaryColor(), false);
        saveFileNameInput.setX(x + 100); saveFileNameInput.setY(y + 32); saveFileNameInput.setWidth(w - 210); saveFileNameInput.render(guiGraphics, (int)mouseRelX, (int)mouseRelY, 0);

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Save in:"), x + 8, y + 62, DraggableWindow.textPrimaryColor(), false);
        filePathInput.setX(x + 100); filePathInput.setY(y + 60); filePathInput.setWidth(w - 210); filePathInput.render(guiGraphics, (int)mouseRelX, (int)mouseRelY, 0);

        guiGraphics.fill(x + w - 170, y + h - 36, x + w - 110, y + h - 12, DraggableWindow.darkTheme ? 0xFF888888 : 0xFFCCCCCC);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Cancel"), x + w - 158, y + h - 32, DraggableWindow.textPrimaryColor(), false);
        guiGraphics.fill(x + w - 100, y + h - 36, x + w - 40, y + h - 12, 0xFF4CAF50);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Save"), x + w - 90, y + h - 32, DraggableWindow.textPrimaryColor(), false);
    }

    private void renderOpenDialog(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch) {
        int w = 420, h = 140;
        int x = cx + (cw - w) / 2;
        int y = cy + (ch - h) / 2;
        guiGraphics.fill(x, y, x + w, y + h, DraggableWindow.darkTheme ? 0xFF2B2B2B : 0xFFFFFFFF);
        guiGraphics.fill(x, y, x + w, y + 20, DraggableWindow.accentColorARGB);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Open Document"), x + 8, y + 6, DraggableWindow.textPrimaryColor(), false);

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("File name:"), x + 8, y + 34, DraggableWindow.textPrimaryColor(), false);
        openFileNameInput.setX(x + 100); openFileNameInput.setY(y + 32); openFileNameInput.setWidth(w - 210); openFileNameInput.render(guiGraphics, (int)mouseRelX, (int)mouseRelY, 0);

        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Open from:"), x + 8, y + 62, DraggableWindow.textPrimaryColor(), false);
        openFilePathInput.setX(x + 100); openFilePathInput.setY(y + 60); openFilePathInput.setWidth(w - 210); openFilePathInput.render(guiGraphics, (int)mouseRelX, (int)mouseRelY, 0);

        guiGraphics.fill(x + w - 170, y + h - 36, x + w - 110, y + h - 12, DraggableWindow.darkTheme ? 0xFF888888 : 0xFFCCCCCC);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Cancel"), x + w - 158, y + h - 32, DraggableWindow.textPrimaryColor(), false);
        guiGraphics.fill(x + w - 100, y + h - 36, x + w - 40, y + h - 12, 0xFF4CAF50);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Open"), x + w - 90, y + h - 32, DraggableWindow.textPrimaryColor(), false);
    }

    private void renderSaveOnExitDialog(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch) {
        int w = 340, h = 100;
        int x = cx + (cw - w) / 2;
        int y = cy + (ch - h) / 2;
        guiGraphics.fill(x, y, x + w, y + h, DraggableWindow.darkTheme ? 0xFF2B2B2B : 0xFFFFFFFF);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Save changes before exiting?"), x + 16, y + 18, DraggableWindow.textPrimaryColor(), false);
        guiGraphics.fill(x + 20, y + h - 36, x + 100, y + h - 12, 0xFF4CAF50);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Save"), x + 36, y + h - 32, DraggableWindow.textPrimaryColor(), false);
        guiGraphics.fill(x + 120, y + h - 36, x + 200, y + h - 12, DraggableWindow.darkTheme ? 0xFF888888 : 0xFFCCCCCC);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Don't Save"), x + 126, y + h - 32, DraggableWindow.textPrimaryColor(), false);
        guiGraphics.fill(x + 220, y + h - 36, x + 300, y + h - 12, DraggableWindow.darkTheme ? 0xFF888888 : 0xFFCCCCCC);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Cancel"), x + 236, y + h - 32, DraggableWindow.textPrimaryColor(), false);
    }

    @Override
    public boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        this.mouseRelX = mouseRelX; this.mouseRelY = mouseRelY;
        // Quick Save button in menu bar
        if (showMenuBar) {
            int[] r = window.getRenderRect(26);
            int cx = r[0] + 8, cy = r[1] + 28;
            int saveBtnX = cx + 64;
            int saveBtnY = cy + 3;
            int saveBtnW = 52;
            int saveBtnH = 18;
            if (mouseRelX >= saveBtnX && mouseRelX <= saveBtnX + saveBtnW && mouseRelY >= saveBtnY && mouseRelY <= saveBtnY + saveBtnH) {
                // Quick save
                saveDocument();
                return true;
            }
        }
        if (showSaveDialog) {
            int[] r = window.getRenderRect(26);
            int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16, ch = r[3] - 40;
            // dialog size
            int w = 420, h = 140;
            int x = cx + (cw - w) / 2;
            int y = cy + (ch - h) / 2;
            // Cancel button area
            if (mouseRelX >= x + w - 170 && mouseRelX <= x + w - 110 && mouseRelY >= y + h - 36 && mouseRelY <= y + h - 12) {
                // Cancel button
                showSaveDialog = false;
                return true;
            }
            // Save button area
            if (mouseRelX >= x + w - 100 && mouseRelX <= x + w - 40 && mouseRelY >= y + h - 36 && mouseRelY <= y + h - 12) {
                // Save button
                String fileName = saveFileNameInput.getValue().trim();
                String filePath = filePathInput.getValue().trim();
                if (fileName.isEmpty() || filePath.isEmpty()) {
                    showStatus("File name and path cannot be empty");
                } else {
                    File dir = new File(filePath);
                    if (!dir.exists() || !dir.isDirectory()) {
                        showStatus("Invalid directory: " + filePath);
                    } else {
                        File saveFile = new File(dir, fileName);
                        // if this Save dialog was opened as part of a Save-on-exit flow, close after save
                        if (pendingCloseOnSave.getAndSet(false)) closeAfterSave.set(true);
                        asyncSaveDocument(saveFile);
                        showSaveDialog = false;
                    }
                }
                return true;
            }
        }
        if (showOpenDialog) {
            int[] r = window.getRenderRect(26);
            int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16, ch = r[3] - 40;
            int w = 420, h = 140;
            int x = cx + (cw - w) / 2;
            int y = cy + (ch - h) / 2;
            // Cancel
            if (mouseRelX >= x + w - 170 && mouseRelX <= x + w - 110 && mouseRelY >= y + h - 36 && mouseRelY <= y + h - 12) {
                showOpenDialog = false;
                return true;
            }
            // Open
            if (mouseRelX >= x + w - 100 && mouseRelX <= x + w - 40 && mouseRelY >= y + h - 36 && mouseRelY <= y + h - 12) {
                String fileName = openFileNameInput.getValue().trim();
                String filePath = openFilePathInput.getValue().trim();
                if (fileName.isEmpty() || filePath.isEmpty()) {
                    showStatus("File name and path cannot be empty");
                } else {
                    File dir = new File(filePath);
                    File openFile = new File(dir, fileName);
                    if (!openFile.exists() || !openFile.isFile()) {
                        showStatus("File does not exist: " + openFile.getAbsolutePath());
                    } else {
                        openFile(openFile);
                        showOpenDialog = false;
                    }
                }
                return true;
            }
        }
        if (showSaveOnExitDialog) {
            int[] r = window.getRenderRect(26);
            int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16, ch = r[3] - 40;
            int w = 340, h = 100;
            int x = cx + (cw - w) / 2;
            int y = cy + (ch - h) / 2;
            // Save
            if (mouseRelX >= x + 20 && mouseRelX <= x + 100 && mouseRelY >= y + h - 36 && mouseRelY <= y + h - 12) {
                // Save and close when save completes
                File f = currentFile.get();
                if (f == null) {
                    // prompt Save As and remember to close after the save
                    pendingCloseOnSave.set(true);
                    showSaveDialog = true;
                } else {
                    closeAfterSave.set(true);
                    asyncSaveDocument(f);
                }
                showSaveOnExitDialog = false;
                return true;
            }
            // Don't Save
            if (mouseRelX >= x + 120 && mouseRelX <= x + 200 && mouseRelY >= y + h - 36 && mouseRelY <= y + h - 12) {
                showSaveOnExitDialog = false;
                // request the window to close (fade out) instead of closing the whole screen
                if (window != null) { window.requestClose(); }
                return true;
            }
            // Cancel
            if (mouseRelX >= x + 220 && mouseRelX <= x + 300 && mouseRelY >= y + h - 36 && mouseRelY <= y + h - 12) {
                showSaveOnExitDialog = false;
                return true;
            }
        }
        // Menu bar
        if (showMenuBar) {
            int[] r = window.getRenderRect(26);
            int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16, ch = r[3] - 40;
            int mx = cx + 8, my = cy + 24;
            // File menu
            if (mouseRelX >= cx + 8 && mouseRelX <= cx + 8 + fileMenuWidth && mouseRelY >= cy + 24 && mouseRelY <= cy + 24 + fileMenuItemHeight) {
                fileMenuOpen = !fileMenuOpen;
                fileMenuSelected = -1;
                return true;
            }
            // File menu items
            if (fileMenuOpen) {
                int menuH = fileMenuItemHeight * (fileMenuItems.length + Math.min(5, recentFiles.size()));
                if (mouseRelY >= my && mouseRelY <= my + menuH) {
                    // cast to int because mouseRelY and my are doubles
                    int idx = (int) ((mouseRelY - my) / fileMenuItemHeight);
                    if (idx < fileMenuItems.length) {
                        fileMenuSelected = idx;
                        switch (idx) {
                            case 0: newDocument(); fileMenuOpen = false; return true; // New
                            case 1: showOpenDialog(); fileMenuOpen = false; return true; // Open
                            case 2: saveDocument(); fileMenuOpen = false; return true; // Save
                            case 3: showSaveDialog = true; fileMenuOpen = false; return true; // Save As
                            case 4: showStatus("Print is not implemented"); fileMenuOpen = false; return true; // Print
                            case 6: handleExit(); fileMenuOpen = false; return true; // Exit
                        }
                    } else {
                        // Recent files
                        idx -= fileMenuItems.length;
                        if (idx < recentFiles.size()) {
                            File recentFile = new File(recentFiles.get(idx));
                            openFile(recentFile);
                            fileMenuOpen = false;
                            return true;
                        }
                    }
                }
            }
        }
        return textArea != null && textArea.mouseClicked(mouseRelX, mouseRelY, button);
    }

    // Show open dialog (stub for now)
    private void showOpenDialog() {
        openFileNameInput.setValue("");
        openFilePathInput.setValue(new File(FilesManager.getPlayerDataDir(), "Documents").getAbsolutePath());
        showOpenDialog = true;
    }

    // Prompt to save on exit if needed
    private void handleExit() {
        if (isModified.get()) {
            showSaveOnExitDialog = true;
        } else {
            // fade/close the notepad window rather than closing whole UI
            if (this.window != null) { this.window.requestClose(); }
        }
    }

    @Override
    public boolean keyPressed(DraggableWindow window, int keyCode, int scanCode, int modifiers) {
        // Handle Ctrl shortcuts first
        boolean ctrl = (modifiers & 0x2) != 0;
        boolean shift = (modifiers & 0x1) != 0;
        if (ctrl) {
            switch (keyCode) {
                case 78: // 'N'
                    newDocument(); return true;
                case 79: // 'O'
                    showOpenDialog(); return true;
                case 83: // 'S'
                    if (shift) { showSaveDialog = true; } else { saveDocument(); } return true;
                case 80: // 'P'
                    showSaveDialog = true; return true;
                case 90: // 'Z'
                    undo(); return true;
                case 89: // 'Y'
                    redo(); return true;
            }
        }

        // Intercept Enter and Up/Down for simple multi-line behavior
        if (keyCode == 257 || keyCode == 335) { // Enter
            int pos = getEditCursorPos();
            String v = textArea.getValue(); if (v == null) v = "";
            String nv = v.substring(0, pos) + "\n" + v.substring(pos);
            textArea.setValue(nv);
            setEditCursorPos(pos + 1);
            isModified.set(true);
            pushUndoState();
            return true;
        }
        if (keyCode == 265) { // Up arrow -> move cursor to line start
            int[] lc = getCursorLineCol();
            int line = lc[0], col = lc[1];
            if (line > 1) {
                // compute new pos: move to start of previous line plus min(col-1, length of prev line)
                String s = textArea.getValue(); if (s == null) s = "";
                int curPos = getEditCursorPos();
                int scan = curPos - col - 1; // position of previous line start
                if (scan < 0) scan = 0;
                setEditCursorPos(scan);
            } else {
                setEditCursorPos(0);
            }
            return true;
        }
        if (keyCode == 264) { // Down arrow -> move cursor to end of current line
            String s = textArea.getValue(); if (s == null) s = "";
            int curPos = getEditCursorPos();
            int len = s.length();
            int idx = s.indexOf('\n', curPos);
            if (idx == -1) setEditCursorPos(len);
            else setEditCursorPos(idx);
            return true;
        }

        return textArea != null && textArea.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void mouseReleased(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        if (textArea != null) textArea.mouseReleased(mouseRelX, mouseRelY, button);
    }
    @Override
    public boolean mouseDragged(DraggableWindow window, double mouseRelX, double mouseRelY, double dx, double dy) {
        return false;
    }
    @Override
    public boolean charTyped(DraggableWindow window, char codePoint, int modifiers) {
        return textArea != null && textArea.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean onClose(DraggableWindow window) {
        // Prompt to save if modified when the Notepad window is being closed
        if (isModified.get()) {
            showSaveOnExitDialog = true;
            // veto immediate close so the dialog can be shown
            return false;
        }
        return true;
    }

    @Override
    public void tick() {
        if (textArea != null) textArea.tick();
    }

    // Simple multiline editor (minimal features for Enter and arrow navigation)
    private static class MultiLineEditor {
        private final net.minecraft.client.gui.Font font;
        private int x, y, width, height;
        private StringBuilder text = new StringBuilder();
        private int cursor = 0;
        private boolean focused = false;
        private long lastBlink = System.currentTimeMillis();
        private boolean showCaret = true;
        private java.util.function.Consumer<String> responder = s -> {};
        private int textColor = 0xFF000000;

        MultiLineEditor(net.minecraft.client.gui.Font font) { this.font = font; }
        void setResponder(java.util.function.Consumer<String> r) { this.responder = r; }
        void setX(int v) { x = v; } void setY(int v) { y = v; } void setWidth(int v) { width = v; } void setHeight(int v) { height = v; }
        void setTextColor(int c) { textColor = c; }
        void setValue(String s) { text = new StringBuilder(s == null ? "" : s); if (cursor > text.length()) cursor = text.length(); }
        String getValue() { return text.toString(); }
        int getCursorPos() { return cursor; }
        void setCursorPos(int p) { cursor = Math.max(0, Math.min(p, text.length())); }

        private java.util.List<String> getLines() {
            // simple split by newline; no visual wrapping (keeps implementation simple and robust)
            String t = text.toString();
            if (t.isEmpty()) return java.util.Collections.singletonList("");
            String[] arr = t.split("\n", -1);
            java.util.List<String> out = new java.util.ArrayList<>(arr.length);
            for (String s : arr) out.add(s);
            return out;
        }

        void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            // background
            g.fill(x, y, x + width, y + height, 0xFF000000);
            java.util.List<String> lines = getLines();
            int lineH = font.lineHeight;
            int maxLines = Math.max(1, height / lineH);
            for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
                String line = lines.get(i);
                g.drawString(font, Component.literal(line), x + 4, y + 2 + i * lineH, textColor, false);
            }
            // caret (blinking)
            long now = System.currentTimeMillis();
            if (now - lastBlink > 500) { showCaret = !showCaret; lastBlink = now; }
            if (focused && showCaret) {
                // compute caret position roughly (end of current line)
                int lineIndex = 0; int pos = 0;
                for (String ln : lines) {
                    if (cursor <= pos + ln.length()) break;
                    pos += ln.length() + 1; lineIndex++;
                }
                int col = cursor - pos;
                String curLine = lineIndex < lines.size() ? lines.get(lineIndex) : "";
                int caretX = x + 4 + font.width(curLine.substring(0, Math.max(0, Math.min(col, curLine.length()))));
                int caretY = y + 2 + lineIndex * lineH;
                g.fill(caretX, caretY, caretX + 1, caretY + lineH - 2, 0xFFFFFFFF);
            }
        }

        boolean mouseClicked(double mx, double my, int button) {
            focused = (mx >= x && mx <= x + width && my >= y && my <= y + height);
            if (focused) {
                // set cursor to nearest line end based on click Y (simple behavior)
                int lineH = font.lineHeight;
                int rel = (int)my - y;
                int line = Math.max(0, rel / lineH);
                java.util.List<String> lines = getLines();
                if (line >= lines.size()) { cursor = text.length(); }
                else {
                    int pos = 0;
                    for (int i = 0; i < line; i++) pos += lines.get(i).length() + 1;
                    // set cursor at line start + approx char index based on X
                    int relX = (int)mx - x - 4;
                    int approxCol = 0;
                    for (int i = 1; i <= lines.get(line).length(); i++) {
                        if (font.width(lines.get(line).substring(0, i)) > relX) break;
                        approxCol = i;
                    }
                    cursor = Math.min(text.length(), pos + approxCol);
                }
                return true;
            }
            return false;
        }

        void mouseReleased(double mx, double my, int button) { /* no-op */ }

        boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            // Basic keyboard handling: backspace, delete, arrows, enter
            switch (keyCode) {
                case 259: // backspace
                    if (cursor > 0) { text.deleteCharAt(cursor - 1); cursor--; responder.accept(text.toString()); }
                    return true;
                case 261: // delete
                    if (cursor < text.length()) { text.deleteCharAt(cursor); responder.accept(text.toString()); }
                    return true;
                case 262: // right
                    if (cursor < text.length()) cursor++;
                    return true;
                case 263: // left
                    if (cursor > 0) cursor--;
                    return true;
                case 265: // up
                    // move to line above same col (approx)
                    java.util.List<String> lines = getLines();
                    int pos = 0; int lineIdx = 0;
                    for (; lineIdx < lines.size(); lineIdx++) {
                        int l = lines.get(lineIdx).length();
                        if (cursor <= pos + l) break;
                        pos += l + 1;
                    }
                    int col = cursor - pos;
                    if (lineIdx > 0) {
                        int prevLen = lines.get(lineIdx - 1).length();
                        int newPos = pos - (lines.get(lineIdx - 1).length() + 1) + Math.min(prevLen, col);
                        cursor = Math.max(0, Math.min(newPos, text.length()));
                    }
                    return true;
                case 264: // down
                    java.util.List<String> all = getLines();
                    pos = 0; lineIdx = 0;
                    for (; lineIdx < all.size(); lineIdx++) {
                        int l = all.get(lineIdx).length();
                        if (cursor <= pos + l) break;
                        pos += l + 1;
                    }
                    int colPos = cursor - pos;
                    if (lineIdx < all.size() - 1) {
                        int nextLen = all.get(lineIdx + 1).length();
                        int newPos = pos + all.get(lineIdx).length() + 1 + Math.min(nextLen, colPos);
                        cursor = Math.max(0, Math.min(newPos, text.length()));
                    }
                    return true;
                case 257: case 335: // Enter
                    text.insert(cursor, '\n'); cursor++; responder.accept(text.toString()); return true;
            }
            return false;
        }

        boolean charTyped(char codePoint, int modifiers) {
            if (!focused) return false;
            // ignore control chars
            if (codePoint >= 32) {
                text.insert(cursor, codePoint); cursor++; responder.accept(text.toString());
                return true;
            }
            return false;
        }

        void tick() { /* caret blink handled in render */ }
    }
}
