package net.chaoscraft.chaoscrafts_device_mod.client.app;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;
import net.chaoscraft.chaoscrafts_device_mod.client.fs.FilesManager;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DraggableWindow;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PaintApp implements IApp {
    private DraggableWindow window;
    private final List<Stroke> strokes = new ArrayList<>();
    private Stroke currentStroke = null;
    private int brushSize = 4;
    private int currentColor = 0xFF000000;
    private final AsyncTaskManager asyncManager = AsyncTaskManager.getInstance();

    private EditBox hexColorInput;
    private boolean hexInputFocused = false;

    private boolean colorPickerOpen = false;
    private float hue = 0f;
    private float saturation = 1f;
    private float value = 1f;
    private boolean draggingHue = false;
    private boolean draggingSV = false;

    private final int[] palette = new int[]{
            0xFF000000, 0xFFFFFFFF, 0xFFF94144, 0xFFFFD166, 0xFF5AE999, 0xFF7AD7FF,
            0xFF9B59B6, 0xFF2ECC71, 0xFF3498DB, 0xFFF39C12
    };

    @Override
    public void onOpen(DraggableWindow window) {
        this.window = window;
        this.hexColorInput = new EditBox(Minecraft.getInstance().font, 0, 0, 80, 16, Component.literal("HEX"));
        this.hexColorInput.setMaxLength(7);
        this.hexColorInput.setValue("#000000");
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 32;
        int cw = r[2] - 16, ch = r[3] - 40;

        guiGraphics.fill(cx, cy, cx + cw, cy + 36, DraggableWindow.darkTheme ? 0xFF2B2B2B : 0xFFBFBFBF);

        guiGraphics.fill(cx + 6, cy + 6, cx + 56, cy + 26, DraggableWindow.darkTheme ? 0xFF555555 : 0xFF999999);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Clear"), cx + 12, cy + 10, DraggableWindow.textPrimaryColor(), false);

        guiGraphics.fill(cx + 62, cy + 6, cx + 122, cy + 26, DraggableWindow.darkTheme ? 0xFF555555 : 0xFF999999);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Save PNG"), cx + 68, cy + 10, DraggableWindow.textPrimaryColor(), false);

        guiGraphics.fill(cx + 128, cy + 6, cx + 148, cy + 26, DraggableWindow.darkTheme ? 0xFF555555 : 0xFF999999);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("" + brushSize), cx + 134, cy + 10, DraggableWindow.textPrimaryColor(), false);

        guiGraphics.fill(cx + 154, cy + 6, cx + 174, cy + 20, DraggableWindow.darkTheme ? 0xFF666666 : 0xFFBBBBBB);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("+"), cx + 160, cy + 8, DraggableWindow.textPrimaryColor(), false);

        guiGraphics.fill(cx + 154, cy + 12, cx + 174, cy + 26, DraggableWindow.darkTheme ? 0xFF666666 : 0xFFBBBBBB);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("-"), cx + 160, cy + 14, DraggableWindow.textPrimaryColor(), false);

         int paletteX = cx + 180;
         for (int i = 0; i < palette.length; i++) {
             int px = paletteX + i * 20;
            guiGraphics.fill(px, cy + 6, px + 16, cy + 22, palette[i]);
            if ((palette[i] & 0x00FFFFFF) == (currentColor & 0x00FFFFFF)) {
                guiGraphics.fill(px - 1, cy + 5, px + 17, cy + 23, DraggableWindow.selectionOverlayColor());
            }
         }

         int colorPickerBtnX = paletteX + palette.length * 20 + 10;
        guiGraphics.fill(colorPickerBtnX, cy + 6, colorPickerBtnX + 80, cy + 26, DraggableWindow.darkTheme ? 0xFF555555 : 0xFF999999);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("Colors"), colorPickerBtnX + 8, cy + 10, DraggableWindow.textPrimaryColor(), false);

         int hexInputX = colorPickerBtnX + 86;
         hexColorInput.setX(hexInputX);
         hexColorInput.setY(cy + 6);
         hexColorInput.render(guiGraphics, mouseRelX, mouseRelY, partialTick);

        guiGraphics.fill(hexInputX + 86, cy + 6, hexInputX + 106, cy + 26, currentColor);
        guiGraphics.fill(hexInputX + 85, cy + 5, hexInputX + 107, cy + 27, DraggableWindow.darkTheme ? 0xFF666666 : 0xFFBFBFBF);

         if (colorPickerOpen) {
             renderColorPicker(guiGraphics, cx, cy, cw, ch);
         }

         int canvasX = cx, canvasY = cy + 36;
         int canvasW = cw, canvasH = ch - 36;
        guiGraphics.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, DraggableWindow.darkTheme ? 0xFF0F0F0F : 0xFFCCCCCC);
        int gridColor = DraggableWindow.darkTheme ? 0xFF2B2B2B : 0xFFEEEEEE;
        for (int x = canvasX; x <= canvasX + canvasW; x += 10) {
            guiGraphics.fill(x, canvasY, x + 1, canvasY + canvasH, gridColor);
        }
        for (int y = canvasY; y <= canvasY + canvasH; y += 10) {
            guiGraphics.fill(canvasX, y, canvasX + canvasW, y + 1, gridColor);
        }

        for (Stroke stroke : strokes) {
            drawStroke(guiGraphics, stroke, canvasX, canvasY);
        }

        if (currentStroke != null) {
            drawStroke(guiGraphics, currentStroke, canvasX, canvasY);
        }
    }

    private void renderColorPicker(GuiGraphics guiGraphics, int cx, int cy, int cw, int ch) {
        int pickerX = cx + 50;
        int pickerY = cy + 50;
        int pickerW = 250;
        int pickerH = 200;

        guiGraphics.fill(pickerX, pickerY, pickerX + pickerW, pickerY + pickerH, DraggableWindow.darkTheme ? 0xFF333333 : 0xFFBFBFBF);
        guiGraphics.fill(pickerX - 1, pickerY - 1, pickerX + pickerW + 1, pickerY + pickerH + 1, DraggableWindow.darkTheme ? 0xFF666666 : 0xFFBFBFBF);

        int hueX = pickerX + pickerW - 20;
        int hueY = pickerY + 10;
        int hueH = pickerH - 40;

        for (int y = 0; y < hueH; y++) {
            float hueValue = (float) y / hueH * 360f;
            int hueColor = hsvToRgb(hueValue, 1f, 1f);
            guiGraphics.fill(hueX, hueY + y, hueX + 15, hueY + y + 1, hueColor);
        }

        int hueSelectorY = hueY + (int) (hue / 360f * hueH);
        guiGraphics.fill(hueX - 2, hueSelectorY - 2, hueX + 17, hueSelectorY + 2, DraggableWindow.selectionOverlayColor());

        int svX = pickerX + 10;
        int svY = pickerY + 10;
        int svSize = pickerH - 40;

        for (int x = 0; x < svSize; x++) {
            for (int y = 0; y < svSize; y++) {
                float s = (float) x / svSize;
                float v = 1f - (float) y / svSize;
                int color = hsvToRgb(hue, s, v);
                guiGraphics.fill(svX + x, svY + y, svX + x + 1, svY + y + 1, color);
            }
        }

        int svSelectorX = svX + (int) (saturation * svSize);
        int svSelectorY = svY + (int) ((1f - value) * svSize);
        guiGraphics.fill(svSelectorX - 2, svSelectorY - 2, svSelectorX + 2, svSelectorY + 2, DraggableWindow.selectionOverlayColor());

        guiGraphics.fill(pickerX + 10, pickerY + pickerH - 25, pickerX + 40, pickerY + pickerH - 5, currentColor);

        guiGraphics.fill(pickerX + pickerW - 50, pickerY + pickerH - 25, pickerX + pickerW - 10, pickerY + pickerH - 5, DraggableWindow.darkTheme ? 0xFF555555 : 0xFF999999);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal("OK"), pickerX + pickerW - 40, pickerY + pickerH - 20, DraggableWindow.textPrimaryColor(), false);
    }

    private int hsvToRgb(float h, float s, float v) {
        int r = 0, g = 0, b = 0;

        if (s == 0) {
            r = g = b = (int) (v * 255);
        } else {
            h = h / 60;
            int i = (int) Math.floor(h);
            float f = h - i;
            float p = v * (1 - s);
            float q = v * (1 - s * f);
            float t = v * (1 - s * (1 - f));

            switch (i) {
                case 0: r = (int) (v * 255); g = (int) (t * 255); b = (int) (p * 255); break;
                case 1: r = (int) (q * 255); g = (int) (v * 255); b = (int) (p * 255); break;
                case 2: r = (int) (p * 255); g = (int) (v * 255); b = (int) (t * 255); break;
                case 3: r = (int) (p * 255); g = (int) (q * 255); b = (int) (v * 255); break;
                case 4: r = (int) (t * 255); g = (int) (p * 255); b = (int) (v * 255); break;
                default: r = (int) (v * 255); g = (int) (p * 255); b = (int) (q * 255); break;
            }
        }

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void rgbToHsv(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        float rf = r / 255f;
        float gf = g / 255f;
        float bf = b / 255f;

        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float delta = max - min;

        if (delta == 0) {
            hue = 0;
        } else if (max == rf) {
            hue = 60 * (((gf - bf) / delta) % 6);
        } else if (max == gf) {
            hue = 60 * (((bf - rf) / delta) + 2);
        } else {
            hue = 60 * (((rf - gf) / delta) + 4);
        }

        if (hue < 0) hue += 360;

        saturation = max == 0 ? 0 : delta / max;

        value = max;
    }

    private void drawStroke(GuiGraphics guiGraphics, Stroke stroke, int canvasX, int canvasY) {
        if (stroke.points.size() < 2) return;

        Point prev = stroke.points.get(0);
        for (int i = 1; i < stroke.points.size(); i++) {
            Point current = stroke.points.get(i);

            int x1 = canvasX + prev.x;
            int y1 = canvasY + prev.y;
            int x2 = canvasX + current.x;
            int y2 = canvasY + current.y;

            drawLine(guiGraphics, x1, y1, x2, y2, stroke.color, stroke.size);

            prev = current;
        }
    }

    private void drawLine(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color, int size) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = (x1 < x2) ? 1 : -1;
        int sy = (y1 < y2) ? 1 : -1;
        int err = dx - dy;

        while (true) {
            guiGraphics.fill(x1 - size/2, y1 - size/2, x1 + size/2, y1 + size/2, color);

            if (x1 == x2 && y1 == y2) break;

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y1 += sy;
            }
        }
    }

    @Override
    public boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 32, cw = r[2] - 16;

        if (hexColorInput.isMouseOver(mouseRelX, mouseRelY)) {
            hexInputFocused = true;
            hexColorInput.mouseClicked(mouseRelX, mouseRelY, button);
            return true;
        }
        hexInputFocused = false;

        int colorPickerBtnX = cx + 180 + palette.length * 20 + 10;
        if (mouseRelX >= colorPickerBtnX && mouseRelX <= colorPickerBtnX + 80 &&
                mouseRelY >= cy + 6 && mouseRelY <= cy + 26) {
            colorPickerOpen = !colorPickerOpen;
            if (colorPickerOpen) {
                rgbToHsv(currentColor);
            }
            return true;
        }

        if (colorPickerOpen) {
            int pickerX = cx + 50;
            int pickerY = cy + 50;
            int pickerW = 250;
            int pickerH = 200;

            if (mouseRelX >= pickerX && mouseRelX <= pickerX + pickerW &&
                    mouseRelY >= pickerY && mouseRelY <= pickerY + pickerH) {

                int hueX = pickerX + pickerW - 20;
                int hueY = pickerY + 10;
                int hueH = pickerH - 40;

                if (mouseRelX >= hueX && mouseRelX <= hueX + 15 &&
                        mouseRelY >= hueY && mouseRelY <= hueY + hueH) {
                    draggingHue = true;
                    hue = (float) ((mouseRelY - hueY) / (float) hueH * 360f);
                    updateColorFromHSV();
                    return true;
                }

                int svX = pickerX + 10;
                int svY = pickerY + 10;
                int svSize = pickerH - 40;

                if (mouseRelX >= svX && mouseRelX <= svX + svSize &&
                        mouseRelY >= svY && mouseRelY <= svY + svSize) {
                    draggingSV = true;
                    saturation = (float) ((mouseRelX - svX) / (float) svSize);
                    value = 1f - (float) ((mouseRelY - svY) / (float) svSize);
                    updateColorFromHSV();
                    return true;
                }

                if (mouseRelX >= pickerX + pickerW - 50 && mouseRelX <= pickerX + pickerW - 10 &&
                        mouseRelY >= pickerY + pickerH - 25 && mouseRelY <= pickerY + pickerH - 5) {
                    colorPickerOpen = false;
                    return true;
                }

                return true;
            }
        }

        if (mouseRelX >= cx + 6 && mouseRelX <= cx + 56 && mouseRelY >= cy + 6 && mouseRelY <= cy + 26) {
            strokes.clear();
            return true;
        }

        if (mouseRelX >= cx + 62 && mouseRelX <= cx + 122 && mouseRelY >= cy + 6 && mouseRelY <= cy + 26) {
            exportPNG(window);
            return true;
        }

        if (mouseRelX >= cx + 154 && mouseRelX <= cx + 174 && mouseRelY >= cy + 6 && mouseRelY <= cy + 20) {
            brushSize = Math.min(20, brushSize + 1);
            return true;
        }

        if (mouseRelX >= cx + 154 && mouseRelX <= cx + 174 && mouseRelY >= cy + 12 && mouseRelY <= cy + 26) {
            brushSize = Math.max(1, brushSize - 1);
            return true;
        }

        int paletteX = cx + 180;
        for (int i = 0; i < palette.length; i++) {
            int px = paletteX + i * 20;
            if (mouseRelX >= px && mouseRelX <= px + 16 && mouseRelY >= cy + 6 && mouseRelY <= cy + 22) {
                currentColor = palette[i];
                hexColorInput.setValue(String.format("#%06X", currentColor & 0xFFFFFF));
                return true;
            }
        }

        int canvasX = cx, canvasY = cy + 36, canvasW = cw, canvasH = r[3] - cy - 36;
        if (mouseRelX >= canvasX && mouseRelY >= canvasY && mouseRelX <= canvasX + canvasW && mouseRelY <= canvasY + canvasH) {
            currentStroke = new Stroke(currentColor, brushSize);
            int rx = (int) (mouseRelX - canvasX), ry = (int) (mouseRelY - canvasY);
            currentStroke.points.add(new Point(rx, ry));
            return true;
        }

        return false;
    }

    private void updateColorFromHSV() {
        currentColor = hsvToRgb(hue, saturation, value);
        hexColorInput.setValue(String.format("#%06X", currentColor & 0xFFFFFF));
    }

    @Override
    public void mouseReleased(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        if (currentStroke != null) {
            strokes.add(currentStroke);
            currentStroke = null;
        }

        draggingHue = false;
        draggingSV = false;
    }

    @Override
    public boolean mouseDragged(DraggableWindow window, double mouseRelX, double mouseRelY, double dx, double dy) {
        if (draggingHue || draggingSV) {
            int[] r = window.getRenderRect(26);
            int cx = r[0] + 8, cy = r[1] + 32;

            int pickerX = cx + 50;
            int pickerY = cy + 50;
            int pickerW = 250;
            int pickerH = 200;

            if (draggingHue) {
                int hueX = pickerX + pickerW - 20;
                int hueY = pickerY + 10;
                int hueH = pickerH - 40;

                hue = (float) ((mouseRelY - hueY) / (float) hueH * 360f);
                hue = Math.max(0, Math.min(360, hue));
                updateColorFromHSV();
                return true;
            }

            if (draggingSV) {
                int svX = pickerX + 10;
                int svY = pickerY + 10;
                int svSize = pickerH - 40;

                saturation = (float) ((mouseRelX - svX) / (float) svSize);
                value = 1f - (float) ((mouseRelY - svY) / (float) svSize);
                saturation = Math.max(0, Math.min(1, saturation));
                value = Math.max(0, Math.min(1, value));
                updateColorFromHSV();
                return true;
            }
        }

        if (currentStroke == null) return false;

        int[] r = window.getRenderRect(26);
        int canvasX = r[0] + 8, canvasY = r[1] + 32 + 36;

        int cx = (int) (mouseRelX - canvasX);
        int cy = (int) (mouseRelY - canvasY);

        if (cx >= 0 && cy >= 0 && cx < (r[2] - 16) && cy < (r[3] - 40 - 36)) {
            currentStroke.points.add(new Point(cx, cy));
            return true;
        }

        return false;
    }

    @Override
    public boolean charTyped(DraggableWindow window, char codePoint, int modifiers) {
        if (hexInputFocused) {
            boolean result = hexColorInput.charTyped(codePoint, modifiers);
            if (result) {
                try {
                    String hex = hexColorInput.getValue().replace("#", "");
                    if (hex.length() == 6) {
                        currentColor = 0xFF000000 | Integer.parseInt(hex, 16);
                    }
                } catch (NumberFormatException e) {
                }
            }
            return result;
        }
        return false;
    }

    @Override
    public boolean keyPressed(DraggableWindow window, int keyCode, int scanCode, int modifiers) {
        if (hexInputFocused) {
            return hexColorInput.keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    private void exportPNG(DraggableWindow window) {
        asyncManager.submitIOTask(() -> {
            try {
                int[] r = window.getRenderRect(26);
                int canvasW = r[2] - 16;
                int canvasH = r[3] - 40 - 36;

                BufferedImage img = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();

                g.setColor(Color.WHITE);
                g.fillRect(0, 0, canvasW, canvasH);

                for (Stroke stroke : strokes) {
                    g.setColor(new Color(stroke.color, true));
                    g.setStroke(new BasicStroke(stroke.size, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                    if (stroke.points.size() > 1) {
                        for (int i = 0; i < stroke.points.size() - 1; i++) {
                            Point a = stroke.points.get(i);
                            Point b = stroke.points.get(i + 1);
                            g.drawLine(a.x, a.y, b.x, b.y);
                        }
                    }
                }

                g.dispose();

                File out = FilesManager.getInstance().saveImageAsWallpaper(img, "paint_export");

                asyncManager.executeOnMainThread(() -> {
                    Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f
                            )
                    );
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public boolean onClose(DraggableWindow window) { return true; }

    private static class Stroke {
        final int color;
        final int size;
        final List<Point> points = new ArrayList<>();

        Stroke(int color, int size) {
            this.color = color;
            this.size = size;
        }
    }

    private static class Point {
        final int x, y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
