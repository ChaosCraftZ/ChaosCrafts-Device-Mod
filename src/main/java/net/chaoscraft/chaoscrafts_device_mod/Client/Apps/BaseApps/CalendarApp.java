package net.chaoscraft.chaoscrafts_device_mod.Client.Apps.BaseApps;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager.IApp;
import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DraggableWindow;
import net.chaoscraft.chaoscrafts_device_mod.Core.AsyncorAPI.AsyncRuntime;
import net.chaoscraft.chaoscrafts_device_mod.Core.Util.FileSystem.FilesManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CalendarApp implements IApp {
    private final AsyncRuntime asyncRuntime = AsyncRuntime.get();
    private LocalDate currentDate = LocalDate.now();
    private List<CalendarEvent> events = new ArrayList<>();

    // UI state for event dialogs
    private boolean showEventDialog = false;
    private LocalDate dialogDate = null;
    private EditBox eventTitleInput;
    private EditBox eventTimeInput;

    // hover
    private int hoverCol = -1, hoverRow = -1;

    @Override
    public void onOpen(DraggableWindow window) {
        // initialize inputs
        this.eventTitleInput = new EditBox(Minecraft.getInstance().font, 0, 0, 200, 16, Component.literal("Title"));
        this.eventTimeInput = new EditBox(Minecraft.getInstance().font, 0, 0, 120, 16, Component.literal("Time"));
        loadEvents();
    }

    private File getCalendarDir() {
        File d = new File(FilesManager.getPlayerDataDir(), "calendar");
        d.mkdirs();
        return d;
    }

    private void loadEvents() {
        asyncRuntime.submitIo(() -> {
            List<CalendarEvent> loaded = new ArrayList<>();
            File dir = getCalendarDir();
            File[] files = dir.listFiles((f, n) -> n.endsWith(".evt"));
            if (files != null) {
                for (File f : files) {
                    try {
                        List<String> lines = Files.readAllLines(f.toPath());
                        if (lines.size() >= 3) {
                            String title = lines.get(0);
                            LocalDate date = LocalDate.parse(lines.get(1));
                            String time = lines.get(2);
                            loaded.add(new CalendarEvent(title, date, time, f.getName()));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            List<CalendarEvent> result = loaded;
            asyncRuntime.runOnClientThread(() -> events = result);
        });
    }

    private void saveEvent(CalendarEvent ev) {
        asyncRuntime.submitIo(() -> {
            try {
                File dir = getCalendarDir();
                File out = new File(dir, ev.fileName != null ? ev.fileName : (UUID.randomUUID().toString() + ".evt"));
                try (FileWriter w = new FileWriter(out)) {
                    w.write(ev.title + "\n");
                    w.write(ev.date.toString() + "\n");
                    w.write(ev.time + "\n");
                }
                // refresh in-memory list
                loadEvents();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void deleteEvent(CalendarEvent ev) {
        asyncRuntime.submitIo(() -> {
            try {
                if (ev.fileName != null) {
                    File f = new File(getCalendarDir(), ev.fileName);
                    if (f.exists()) f.delete();
                }
                loadEvents();
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16, ch = r[3] - 40;

        // Header
        guiGraphics.fill(cx, cy, cx + cw, cy + 30, 0xFF2B2B2B);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Calendar"), cx + 10, cy + 8, 0xFFFFFFFF, false);

        // Navigation buttons
        guiGraphics.fill(cx + cw - 160, cy + 5, cx + cw - 130, cy + 25, 0xFF555555);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("◀"), cx + cw - 155, cy + 10, 0xFFFFFFFF, false);

        guiGraphics.fill(cx + cw - 120, cy + 5, cx + cw - 90, cy + 25, 0xFF555555);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("▶"), cx + cw - 115, cy + 10, 0xFFFFFFFF, false);

        guiGraphics.fill(cx + cw - 80, cy + 5, cx + cw - 10, cy + 25, 0xFF4C7BD1);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Today"), cx + cw - 75, cy + 10, 0xFFFFFFFF, false);

        // Month and year
        String monthYear = currentDate.format(DateTimeFormatter.ofPattern("MMMM yyyy"));
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(monthYear), cx + 100, cy + 10, 0xFFFFFFFF, false);

        // Calendar grid
        int gridY = cy + 40;
        int cellWidth = cw / 7;
        int cellHeight = (ch - 40) / 7; // match mouseClicked calculation

        // Day names
        String[] dayNames = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        for (int i = 0; i < 7; i++) {
            int dayX = cx + i * cellWidth;
            guiGraphics.fill(dayX, gridY, dayX + cellWidth, gridY + 20, 0xFF444444);
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(dayNames[i]), dayX + 5, gridY + 5, 0xFFFFFFFF, false);
        }

        // Calendar cells
        LocalDate firstOfMonth = currentDate.withDayOfMonth(1);
        int daysInMonth = currentDate.lengthOfMonth();
        int dayOfWeek = firstOfMonth.getDayOfWeek().getValue() % 7; // 0 = Sunday, 6 = Saturday

        int day = 1;
        hoverCol = -1; hoverRow = -1;
        // compute mouse-relative to calendar origin
        int mx = mouseRelX, my = mouseRelY;

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 7; col++) {
                int cellX = cx + col * cellWidth;
                int cellY = gridY + 20 + row * cellHeight;

                // Only draw cells that are part of the current month
                if ((row == 0 && col < dayOfWeek) || day > daysInMonth) {
                    guiGraphics.fill(cellX, cellY, cellX + cellWidth, cellY + cellHeight, 0xFF2B2B2B);
                    continue;
                }

                // Detect hover
                if (mx >= cellX && mx <= cellX + cellWidth && my >= cellY && my <= cellY + cellHeight) {
                    hoverCol = col; hoverRow = row;
                }

                // Highlight current day
                boolean isToday = currentDate.getMonth() == LocalDate.now().getMonth() &&
                        currentDate.getYear() == LocalDate.now().getYear() &&
                        day == LocalDate.now().getDayOfMonth();

                if (isToday) {
                    guiGraphics.fill(cellX, cellY, cellX + cellWidth, cellY + cellHeight, 0x5533AA33);
                } else {
                    guiGraphics.fill(cellX, cellY, cellX + cellWidth, cellY + cellHeight, 0xFF2B2B2B);
                }

                // Draw hover overlay
                if (hoverCol == col && hoverRow == row) {
                    guiGraphics.fill(cellX, cellY, cellX + cellWidth, cellY + cellHeight, 0x332B77FF);
                }

                // Draw day number
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(String.valueOf(day)), cellX + 5, cellY + 5, 0xFFFFFFFF, false);

                // Draw events for this day
                LocalDate cellDate = LocalDate.of(currentDate.getYear(), currentDate.getMonth(), day);
                int eventY = cellY + 18;
                for (CalendarEvent event : events) {
                    if (event.date.equals(cellDate)) {
                        guiGraphics.fill(cellX + 2, eventY, cellX + cellWidth - 2, eventY + 12, 0xFF4C7BD1);
                        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(event.time + " " + event.title), cellX + 4, eventY + 2, 0xFFFFFFFF, false);
                        eventY += 14;
                    }
                }

                day++;
            }
        }

        // Upcoming events panel
        int eventsPanelX = cx;
        int eventsPanelY = gridY + 20 + 6 * cellHeight;
        int eventsPanelWidth = cw;
        int eventsPanelHeight = ch - (gridY + 20 + 6 * cellHeight) - 40;

        guiGraphics.fill(eventsPanelX, eventsPanelY, eventsPanelX + eventsPanelWidth, eventsPanelY + eventsPanelHeight, 0xFF1E1E1E);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Upcoming Events"), eventsPanelX + 5, eventsPanelY + 5, 0xFFFFFFFF, false);

        int eventListY = eventsPanelY + 20;
        for (CalendarEvent event : events) {
            if (event.date.isAfter(LocalDate.now().minusDays(1))) {
                String eventText = event.date.format(DateTimeFormatter.ofPattern("MMM d")) + " - " + event.title + " " + event.time;
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(eventText), eventsPanelX + 10, eventListY, 0xFFFFFFFF, false);
                eventListY += 12;
            }
        }

        // Render event dialog if active
         if (showEventDialog && dialogDate != null) {
             int dw = 320, dh = 140;
             int dx = cx + (cw - dw) / 2;
             int dy = cy + (ch - dh) / 2;

             guiGraphics.fill(dx, dy, dx + dw, dy + dh, 0xFF2B2B2B);
             guiGraphics.fill(dx, dy, dx + dw, dy + 24, 0xFF4C7BD1);
             guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Events for " + dialogDate.toString()), dx + 10, dy + 8, 0xFFFFFFFF, false);

             // existing events for day
             int ey = dy + 32;
             for (CalendarEvent ev : events) {
                 if (ev.date.equals(dialogDate)) {
                     guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(ev.time + " - " + ev.title), dx + 8, ey, 0xFFFFFFFF, false);
                     // delete button
                     guiGraphics.fill(dx + dw - 70, ey - 2, dx + dw - 10, ey + 12, 0xFFF94144);
                     guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Del"), dx + dw - 60, ey, 0xFFFFFFFF, false);
                     ey += 14;
                 }
             }

             // inputs
             guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Title:"), dx + 8, dy + dh - 72, 0xFFFFFFFF, false);
             eventTitleInput.setX(dx + 50);
             eventTitleInput.setY(dy + dh - 76);
             eventTitleInput.setWidth(dw - 60);
             // pass actual mouse coords so clicks and focus work correctly
             eventTitleInput.render(guiGraphics, mouseRelX, mouseRelY, partialTick);

             guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Time:"), dx + 8, dy + dh - 48, 0xFFFFFFFF, false);
             eventTimeInput.setX(dx + 50);
             eventTimeInput.setY(dy + dh - 52);
             eventTimeInput.setWidth(120);
             // pass actual mouse coords so typing/click focuses correctly
             eventTimeInput.render(guiGraphics, mouseRelX, mouseRelY, partialTick);

             // add button
             guiGraphics.fill(dx + dw - 110, dy + dh - 52, dx + dw - 30, dy + dh - 28, 0xFF4C7BD1);
             guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Add Event"), dx + dw - 100, dy + dh - 48, 0xFFFFFFFF, false);
         }
     }

     @Override
     public boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16, ch = r[3] - 40;

        // Previous month button
        if (mouseRelX >= cx + cw - 160 && mouseRelX <= cx + cw - 130 &&
                mouseRelY >= cy + 5 && mouseRelY <= cy + 25) {
            currentDate = currentDate.minusMonths(1);
            return true;
        }

        // Next month button
        if (mouseRelX >= cx + cw - 120 && mouseRelX <= cx + cw - 90 &&
                mouseRelY >= cy + 5 && mouseRelY <= cy + 25) {
            currentDate = currentDate.plusMonths(1);
            return true;
        }

        // Today button
        if (mouseRelX >= cx + cw - 80 && mouseRelX <= cx + cw - 10 &&
                mouseRelY >= cy + 5 && mouseRelY <= cy + 25) {
            currentDate = LocalDate.now();
            return true;
        }

        int gridY = cy + 40;
        int cellWidth = cw / 7;
        int cellHeight = (ch - 40) / 7; // same as render

        // compute first day offset
        LocalDate firstOfMonth = currentDate.withDayOfMonth(1);
        int dayOfWeek = firstOfMonth.getDayOfWeek().getValue() % 7;

        int day = 1;
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 7; col++) {
                int cellX = cx + col * cellWidth;
                int cellY = gridY + 20 + row * cellHeight;

                if ((row == 0 && col < dayOfWeek) || day > currentDate.lengthOfMonth()) { day++; continue; }

                if (mouseRelX >= cellX && mouseRelX <= cellX + cellWidth &&
                        mouseRelY >= cellY && mouseRelY <= cellY + cellHeight) {
                    dialogDate = LocalDate.of(currentDate.getYear(), currentDate.getMonth(), day);
                    showEventDialog = true;
                    eventTitleInput.setValue("");
                    eventTimeInput.setValue("");
                    return true;
                }

                day++;
            }
        }

        // If event dialog open, handle its buttons (Add/Delete)
        if (showEventDialog && dialogDate != null) {
            int dw = 320, dh = 140;
            int dx = cx + (cw - dw) / 2;
            int dy = cy + (ch - dh) / 2;
            // If click lands inside the dialog, route clicks to inputs first
            if (mouseRelX >= dx && mouseRelX <= dx + dw && mouseRelY >= dy && mouseRelY <= dy + dh) {
                // Title input area
                if (mouseRelX >= dx + 50 && mouseRelX <= dx + 50 + (dw - 60) && mouseRelY >= dy + dh - 76 && mouseRelY <= dy + dh - 56) {
                    eventTitleInput.mouseClicked(mouseRelX, mouseRelY, button);
                    return true;
                }
                // Time input area
                if (mouseRelX >= dx + 50 && mouseRelX <= dx + 50 + 120 && mouseRelY >= dy + dh - 52 && mouseRelY <= dy + dh - 32) {
                    eventTimeInput.mouseClicked(mouseRelX, mouseRelY, button);
                    return true;
                }
                // clicking inside dialog but not on controls should not fall through
            }
            // Add button
            if (mouseRelX >= dx + dw - 110 && mouseRelX <= dx + dw - 30 && mouseRelY >= dy + dh - 52 && mouseRelY <= dy + dh - 28) {
                String title = eventTitleInput.getValue().trim();
                String time = eventTimeInput.getValue().trim();
                if (!title.isEmpty()) {
                    CalendarEvent ev = new CalendarEvent(title, dialogDate, time);
                    saveEvent(ev);
                    showEventDialog = false;
                    return true;
                } else {
                    // notify user via status
                    // no direct showStatus here; ignoring
                }
            }
            // delete buttons for existing events
            int ey = dy + 32;
            for (CalendarEvent ev : events) {
                if (ev.date.equals(dialogDate)) {
                    if (mouseRelX >= dx + dw - 70 && mouseRelX <= dx + dw - 10 && mouseRelY >= ey - 2 && mouseRelY <= ey + 12) {
                        deleteEvent(ev);
                        return true;
                    }
                    ey += 14;
                }
            }
            // clicking outside dialog closes it
            if (!(mouseRelX >= dx && mouseRelX <= dx + dw && mouseRelY >= dy && mouseRelY <= dy + dh)) {
                showEventDialog = false;
            }
        }

        return false;
     }

    @Override
    public void tick() {}

    @Override
    public void mouseReleased(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        // Empty implementation
    }

    @Override
    public boolean mouseDragged(DraggableWindow window, double mouseRelX, double mouseRelY, double dx, double dy) {
        return false;
    }

    @Override
    public boolean charTyped(DraggableWindow window, char codePoint, int modifiers) {
        if (showEventDialog) {
            if (eventTitleInput.isFocused()) return eventTitleInput.charTyped(codePoint, modifiers);
            if (eventTimeInput.isFocused()) return eventTimeInput.charTyped(codePoint, modifiers);
        }
        return false;
    }

    @Override
    public boolean keyPressed(DraggableWindow window, int keyCode, int scanCode, int modifiers) {
        if (showEventDialog) {
            if (eventTitleInput.isFocused()) return eventTitleInput.keyPressed(keyCode, scanCode, modifiers);
            if (eventTimeInput.isFocused()) return eventTimeInput.keyPressed(keyCode, scanCode, modifiers);

            // ESC closes dialog
            if (keyCode == 256) {
                showEventDialog = false; dialogDate = null; return true;
            }
        }
        return false;
    }

    @Override
    public boolean onClose(DraggableWindow window) { return true; }

    private static class CalendarEvent {
        String title;
        LocalDate date;
        String time;
        String fileName;

        CalendarEvent(String title, LocalDate date, String time) { this.title = title; this.date = date; this.time = time; this.fileName = null; }
        CalendarEvent(String title, LocalDate date, String time, String fileName) { this.title = title; this.date = date; this.time = time; this.fileName = fileName; }
    }
}
