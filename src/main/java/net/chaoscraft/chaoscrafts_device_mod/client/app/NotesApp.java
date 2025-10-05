package net.chaoscraft.chaoscrafts_device_mod.client.app;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;
import net.chaoscraft.chaoscrafts_device_mod.client.fs.FilesManager;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DraggableWindow;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class NotesApp implements IApp {
    private DraggableWindow window;
    private final AsyncTaskManager asyncManager = AsyncTaskManager.getInstance();
    // use thread-safe list for cross-thread updates
    private final List<Note> notes = new CopyOnWriteArrayList<>();
    private Note selectedNote = null;
    private EditBox noteTitleInput;
    private MultiLineEditor noteContentInput;
    private boolean titleFocused = false;
    private boolean contentFocused = false;

    @Override
    public void onOpen(DraggableWindow window) {
        this.window = window;
        this.noteTitleInput = new EditBox(Minecraft.getInstance().font, 0, 0, 200, 16, Component.literal("Title"));
        this.noteContentInput = new MultiLineEditor(Minecraft.getInstance().font);
        this.noteContentInput.setResponder((s) -> { if (selectedNote != null) selectedNote.content = s; });

        // set responders so edits update the selected note in-memory
        this.noteTitleInput.setResponder((s) -> { if (selectedNote != null) selectedNote.title = s; });

        loadNotes();
    }

    private File getNotesDir() {
        File notesDir = new File(FilesManager.getPlayerDataDir(), "notes");
        if (notesDir.getParentFile() != null) notesDir.getParentFile().mkdirs();
        notesDir.mkdirs();
        return notesDir;
    }

    private void loadNotes() {
        asyncManager.submitIOTask(() -> {
            List<Note> tmp = new ArrayList<>();
            File notesDir = getNotesDir();

            File[] noteFiles = notesDir.listFiles((dir, name) -> name.endsWith(".txt"));
            if (noteFiles != null) {
                for (File file : noteFiles) {
                    try {
                        String content = new String(Files.readAllBytes(file.toPath()));
                        String title = file.getName().replaceFirst("\\.txt$", "");
                        Note note = new Note(title, content);
                        tmp.add(note);
                    } catch (IOException ignored) {}
                }
            }

            // If none exist, create default note
            if (tmp.isEmpty()) {
                Note defaultNote = new Note("Welcome", "Welcome to Notes!\n\nCreate new notes by clicking the '+' button.\nYour notes are automatically saved.");
                tmp.add(defaultNote);
                // Save default note to disk
                try {
                    File n = new File(notesDir, defaultNote.title + ".txt");
                    if (!n.exists()) try (FileWriter w = new FileWriter(n)) { w.write(defaultNote.content); }
                } catch (IOException ignored) {}
            }

            // Update UI on main thread
            asyncManager.executeOnMainThread(() -> {
                notes.clear(); notes.addAll(tmp);
                selectedNote = notes.isEmpty() ? null : notes.get(0);
                if (selectedNote != null) {
                    noteTitleInput.setValue(selectedNote.title);
                    noteContentInput.setValue(selectedNote.content);
                }
            });
        });
    }

    private void saveNote(Note note) {
        asyncManager.submitIOTask(() -> {
            File notesDir = getNotesDir();
            File noteFile = new File(notesDir, note.title + ".txt");
            try (FileWriter writer = new FileWriter(noteFile)) {
                writer.write(note.content == null ? "" : note.content);
            } catch (IOException ignored) {}
            // refresh list on main thread
            asyncManager.executeOnMainThread(this::loadNotes);
        });
    }

    private void deleteNote(Note note) {
        asyncManager.submitIOTask(() -> {
            File notesDir = getNotesDir();
            File noteFile = new File(notesDir, note.title + ".txt");
            if (noteFile.exists()) noteFile.delete();
            asyncManager.executeOnMainThread(() -> {
                notes.remove(note);
                if (selectedNote == note) selectedNote = notes.isEmpty() ? null : notes.get(0);
                if (selectedNote != null) {
                    noteTitleInput.setValue(selectedNote.title);
                    noteContentInput.setValue(selectedNote.content);
                }
            });
        });
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16, ch = r[3] - 40;

        // Header
        guiGraphics.fill(cx, cy, cx + cw, cy + 30, DraggableWindow.darkTheme ? 0xFF2B2B2B : 0xFFF0F0F0);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Notes"), cx + 10, cy + 8, DraggableWindow.textPrimaryColor(), false);

        // New note button
        guiGraphics.fill(cx + cw - 80, cy + 5, cx + cw - 10, cy + 25, DraggableWindow.accentColorARGB);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("+ New"), cx + cw - 75, cy + 10, DraggableWindow.contrastingColorFor(DraggableWindow.accentColorARGB), false);

        // Notes list (left panel)
        int listWidth = 200;
        guiGraphics.fill(cx, cy + 40, cx + listWidth, cy + ch, DraggableWindow.darkTheme ? 0xFF1E1E1E : 0xFFF8F8F8);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Your Notes"), cx + 10, cy + 45, DraggableWindow.textPrimaryColor(), false);

        // List notes
        int noteY = cy + 65;
        for (Note note : notes) {
            boolean isSelected = selectedNote != null && selectedNote.title.equals(note.title);

            if (isSelected) {
                guiGraphics.fill(cx, noteY, cx + listWidth, noteY + 20, DraggableWindow.selectionOverlayColor());
            }

            String displayName = note.title;
            if (displayName.length() > 20) displayName = displayName.substring(0, 17) + "...";

            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(displayName), cx + 10, noteY + 5, DraggableWindow.textPrimaryColor(), false);
            noteY += 25;
        }

        // Note editor (right panel)
        if (selectedNote != null) {
            int editorX = cx + listWidth + 10;
            int editorWidth = cw - listWidth - 10;

            // Title input
            guiGraphics.fill(editorX, cy + 40, editorX + editorWidth, cy + 70, DraggableWindow.darkTheme ? 0xFF1E1E1E : 0xFFF8F8F8);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Title:"), editorX + 5, cy + 45, DraggableWindow.textPrimaryColor(), false);

            noteTitleInput.setX(editorX + 50);
            noteTitleInput.setY(cy + 45);
            noteTitleInput.setWidth(editorWidth - 60);
            noteTitleInput.setValue(selectedNote.title);
            noteTitleInput.render(guiGraphics, mouseRelX, mouseRelY, partialTick);

            // Content input
            guiGraphics.fill(editorX, cy + 75, editorX + editorWidth, cy + ch, DraggableWindow.darkTheme ? 0xFF1E1E1E : 0xFFFAFAFA);

            noteContentInput.setX(editorX + 5);
            noteContentInput.setY(cy + 80);
            noteContentInput.setWidth(editorWidth - 10);
            noteContentInput.setHeight(ch - 90);
            noteContentInput.setTextColor(DraggableWindow.textPrimaryColor());
            noteContentInput.setValue(selectedNote.content);
            noteContentInput.render(guiGraphics, mouseRelX, mouseRelY, partialTick);

            // Save button
            guiGraphics.fill(editorX + editorWidth - 100, cy + 45, editorX + editorWidth - 10, cy + 65, DraggableWindow.accentColorARGB);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Save"), editorX + editorWidth - 85, cy + 50, DraggableWindow.contrastingColorFor(DraggableWindow.accentColorARGB), false);

            // Delete button
            guiGraphics.fill(editorX + editorWidth - 200, cy + 45, editorX + editorWidth - 110, cy + 65, 0xFFF94144);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Delete"), editorX + editorWidth - 195, cy + 50, DraggableWindow.textPrimaryColor(), false);
        } else {
            int editorX = cx + listWidth + 10;
            int editorWidth = cw - listWidth - 10;
            guiGraphics.fill(editorX, cy + 40, editorX + editorWidth, cy + ch, DraggableWindow.darkTheme ? 0xFF1E1E1E : 0xFFFAFAFA);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Select a note or create a new one"), editorX + 10, cy + 60, DraggableWindow.textPrimaryColor(), false);
        }
    }

    @Override
    public boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16, ch = r[3] - 40;
        int listWidth = 200;

        // New note button
        if (mouseRelX >= cx + cw - 80 && mouseRelX <= cx + cw - 10 &&
                mouseRelY >= cy + 5 && mouseRelY <= cy + 25) {
            createNewNote();
            return true;
        }

        // Notes list
        int noteY = cy + 65;
        for (Note note : notes) {
            if (mouseRelX >= cx && mouseRelX <= cx + listWidth &&
                    mouseRelY >= noteY && mouseRelY <= noteY + 20) {
                selectNote(note);
                return true;
            }
            noteY += 25;
        }

        if (selectedNote != null) {
            int editorX = cx + listWidth + 10;
            int editorWidth = cw - listWidth - 10;

            // Title input
            if (mouseRelX >= editorX + 50 && mouseRelX <= editorX + editorWidth - 60 &&
                    mouseRelY >= cy + 45 && mouseRelY <= cy + 65) {
                noteTitleInput.setFocused(true);
                titleFocused = true;
                contentFocused = false;
                return true;
            }

            // Content input
            if (mouseRelX >= editorX + 5 && mouseRelX <= editorX + editorWidth - 10 &&
                    mouseRelY >= cy + 80 && mouseRelY <= cy + ch) {
                // map click into content editor (use absolute coords as mouseRelX/Y are absolute)
                noteContentInput.mouseClicked(mouseRelX, mouseRelY, button);
                noteContentInput.setFocused(true);
                contentFocused = true;
                titleFocused = false;
                return true;
            }

            // Save button
            if (mouseRelX >= editorX + editorWidth - 100 && mouseRelX <= editorX + editorWidth - 10 &&
                    mouseRelY >= cy + 45 && mouseRelY <= cy + 65) {
                saveCurrentNote();
                return true;
            }

            // Delete button
            if (mouseRelX >= editorX + editorWidth - 200 && mouseRelX <= editorX + editorWidth - 110 &&
                    mouseRelY >= cy + 45 && mouseRelY <= cy + 65) {
                deleteCurrentNote();
                return true;
            }
        }

        noteTitleInput.setFocused(false);
        noteContentInput.setFocused(false);
        titleFocused = false;
        contentFocused = false;

        return false;
    }

    private void createNewNote() {
        String baseName = "New Note";
        int counter = 1;
        String name = baseName;

        boolean nameExists;
        do {
            nameExists = false;
            for (Note note : notes) {
                if (note.title.equals(name)) {
                    nameExists = true;
                    name = baseName + " (" + counter + ")";
                    counter++;
                    break;
                }
            }
        } while (nameExists);

        Note newNote = new Note(name, "");
        notes.add(newNote);
        selectNote(newNote);
        saveNote(newNote);
    }

    private void selectNote(Note note) {
        selectedNote = note;
        noteTitleInput.setValue(note.title);
        noteContentInput.setValue(note.content);
    }

    private void saveCurrentNote() {
        if (selectedNote != null) {
            selectedNote.title = noteTitleInput.getValue();
            selectedNote.content = noteContentInput.getValue();
            saveNote(selectedNote);
        }
    }

    private void deleteCurrentNote() {
        if (selectedNote != null) {
            Note toDelete = selectedNote;
            notes.remove(toDelete);
            deleteNote(toDelete);
            selectedNote = notes.isEmpty() ? null : notes.get(0);
        }
    }

    @Override
    public boolean charTyped(DraggableWindow window, char codePoint, int modifiers) {
        if (titleFocused) {
            return noteTitleInput.charTyped(codePoint, modifiers);
        } else if (contentFocused) {
            return noteContentInput.charTyped(codePoint, modifiers);
        }
        return false;
    }

    @Override
    public boolean keyPressed(DraggableWindow window, int keyCode, int scanCode, int modifiers) {
        if (titleFocused) {
            return noteTitleInput.keyPressed(keyCode, scanCode, modifiers);
        } else if (contentFocused) {
            return noteContentInput.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    @Override
    public void mouseReleased(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        if (noteTitleInput != null) noteTitleInput.mouseReleased(mouseRelX, mouseRelY, button);
        if (noteContentInput != null) noteContentInput.mouseReleased(mouseRelX, mouseRelY, button);
    }

    @Override
    public boolean mouseDragged(DraggableWindow window, double mouseRelX, double mouseRelY, double dx, double dy) {
        return false;
    }

    @Override
    public boolean onClose(DraggableWindow window) {
        // Save current note when closing
        if (selectedNote != null) {
            saveCurrentNote();
        }
        return true;
    }

    @Override
    public void tick() {}

    // Simple MultiLineEditor (minimal) for NotesApp
    private static class MultiLineEditor {
        private final net.minecraft.client.gui.Font font;
        private int x, y, width, height;
        private StringBuilder text = new StringBuilder();
        private int cursor = 0;
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

        private java.util.List<String> wrapLines(String input) {
            java.util.List<String> out = new java.util.ArrayList<>();
            if (input == null) return out;
            String[] orig = input.split("\n", -1);
            for (String line : orig) {
                if (line.isEmpty()) { out.add(""); continue; }
                String rem = line;
                while (!rem.isEmpty()) {
                    String part = font.plainSubstrByWidth(rem, width - 4);
                    if (part.isEmpty()) break;
                    out.add(part);
                    if (part.length() >= rem.length()) break;
                    rem = rem.substring(part.length());
                }
            }
            return out;
        }

        void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            java.util.List<String> lines = wrapLines(text.toString());
            int drawY = y;
            for (String line : lines) {
                g.drawString(font, Component.literal(line), x, drawY, textColor, false);
                drawY += font.lineHeight;
                if (drawY > y + height) break;
            }
            if (System.currentTimeMillis() - lastBlink > 500) { showCaret = !showCaret; lastBlink = System.currentTimeMillis(); }
            if (showCaret) {
                String before = text.substring(0, Math.max(0, Math.min(cursor, text.length())));
                java.util.List<String> lns = wrapLines(before);
                String last = lns.isEmpty() ? "" : lns.get(lns.size()-1);
                int cx = x + font.width(last);
                int cy = y + (lns.size()-1) * font.lineHeight;
                g.fill(cx, cy, cx+1, cy+font.lineHeight, 0xFF000000);
            }
        }

        boolean charTyped(char c, int modifiers) { if (c == 13 || c == '\r') return false; text.insert(cursor, c); cursor++; responder.accept(text.toString()); return true; }
        boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            switch (keyCode) {
                case 263: if (cursor>0) cursor--; return true;
                case 262: if (cursor<text.length()) cursor++; return true;
                case 259: if (cursor>0) { text.deleteCharAt(cursor-1); cursor--; responder.accept(text.toString()); } return true;
                case 261: if (cursor<text.length()) { text.deleteCharAt(cursor); responder.accept(text.toString()); } return true;
                case 257: case 335: text.insert(cursor, '\n'); cursor++; responder.accept(text.toString()); return true;
            }
            return false;
        }

        boolean mouseClicked(double mx, double my, int button) {
            if (mx < x || mx > x+width || my < y || my > y+height) return false;
            int relY=(int)my - y; int line=Math.max(0, relY / font.lineHeight);
            java.util.List<String> lines = wrapLines(text.toString()); if (line>=lines.size()) { cursor=text.length(); return true; }
            String lineStr = lines.get(line); int relX=(int)mx - x; int posInLine=0; for (int i=0;i<lineStr.length();i++){ if (font.width(lineStr.substring(0,i+1))>relX){ posInLine=i; break;} posInLine=i+1; }
            int abs=0; for (int i=0;i<line;i++) abs+=lines.get(i).length(); cursor=Math.min(text.length(), abs+posInLine); return true;
        }

        private boolean focused = false;
        void setFocused(boolean f) { this.focused = f; }
        boolean isFocused() { return this.focused; }

         void mouseReleased(double mx, double my, int button) {}
         void tick() {}
     }

    private static class Note {
        String title;
        String content;

        Note(String title, String content) {
            this.title = title;
            this.content = content;
        }
    }
}
