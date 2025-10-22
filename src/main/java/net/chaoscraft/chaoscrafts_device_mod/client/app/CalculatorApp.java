package net.chaoscraft.chaoscrafts_device_mod.client.app;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.client.async.AsyncTaskManager;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DraggableWindow;
import net.chaoscraft.chaoscrafts_device_mod.ConfigHandler;

public class CalculatorApp implements IApp {
    private String display = "0";
    private double acc = 0;
    private String op = "";
    private boolean resetNext = false;
    private final AsyncTaskManager asyncManager = AsyncTaskManager.getInstance();

    private String expression = "";
    private String lastCalculation = "";
    private boolean showLastCalculation = false;

    @Override
    public void onOpen(DraggableWindow window) {
        try {
            System.out.println("[CalculatorApp] onOpen called. Window before sizing: w=" + window.width + " h=" + window.height);
        } catch (Exception ignored) {}

        try {
            Minecraft mc = Minecraft.getInstance();
            float uiScale = ConfigHandler.uiScaleFactor();
            int logicalW = Math.round(mc.getWindow().getGuiScaledWidth() / uiScale);
            int logicalH = Math.round(mc.getWindow().getGuiScaledHeight() / uiScale);
            int desiredW = Math.max(260, logicalW / 4);
            int desiredH = Math.max(320, logicalH / 4);
            desiredW = Math.min(desiredW, Math.max(300, logicalW / 2));
            desiredH = Math.min(desiredH, Math.max(360, logicalH / 2));
            window.width = desiredW;
            window.height = desiredH;
            try { System.out.println("[CalculatorApp] resized to: w=" + window.width + " h=" + window.height); } catch (Exception ignored) {}
        } catch (Exception ignored) {}
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        int[] r = window.getRenderRect(26);
        int padding = 12;
        int cx = r[0] + padding;
        int cy = r[1] + 30;
        int cw = Math.max(160, r[2] - padding * 2);

        int dispH = 56;
        int dispX = cx;
        int dispY = cy;
        int dispW = cw;

        int bg = DraggableWindow.darkTheme ? 0xFF111214 : 0xFFF3F4F6;
        int inner = DraggableWindow.darkTheme ? 0xFF1E1E1E : 0xFFFFFFFF;
        int accent = DraggableWindow.accentColorARGB;
        int textColor = DraggableWindow.textPrimaryColor();
        int secondary = DraggableWindow.textSecondaryColor();

        guiGraphics.fill(dispX, dispY, dispX + dispW, dispY + dispH, inner);
        guiGraphics.fill(dispX + 2, dispY + 2, dispX + dispW - 2, dispY + dispH - 2, bg);

        String smallLine = showLastCalculation ? lastCalculation : "";
        if (smallLine != null && !smallLine.isEmpty()) {
            int smallColor = (secondary & 0x00FFFFFF) | 0xAA000000;
            guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(smallLine), dispX + 8, dispY + 6, smallColor, false);
        }

        String val;
        if (showLastCalculation) {
            val = display == null ? "" : display;
        } else if (expression != null && !expression.isEmpty()) {
            val = expression;
        } else {
            val = display == null ? "" : display;
        }

        int availableTextW = Math.max(20, dispW - 16);
        if (Minecraft.getInstance().font.width(val) > availableTextW) {
            val = Minecraft.getInstance().font.plainSubstrByWidth(val, availableTextW - 6) + "...";
        }
        int txtW = Minecraft.getInstance().font.width(val);
        int txtX = cx + Math.max(8, cw - txtW - 12);
        int txtY = cy + (dispH / 2) - 6;
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(val), txtX, txtY, textColor, false);

        int clearH = 22;
        int clearY = cy + dispH + 10;
        int clearW = dispW;
        guiGraphics.fill(cx, clearY, cx + clearW, clearY + clearH, 0xFFB94A4A);
        String clearLabel = "Clear";
        int clw = Minecraft.getInstance().font.width(clearLabel);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(clearLabel), cx + (clearW / 2) - (clw / 2), clearY + 4, 0xFFFFFFFF, false);

        String[][] grid = {{"7","8","9","/"},{"4","5","6","*"},{"1","2","3","-"},{"0",".","=","+"}};
        int gap = 8;
        int btnAreaX = cx;
        int btnAreaY = clearY + clearH + 10;
        int btnAreaW = cw;
        int btnCols = 4;
        int btnRows = 4;
        int bw = (btnAreaW - gap * (btnCols - 1)) / btnCols;
        int bh = Math.max(36, (r[3] - (btnAreaY - r[1])) / (btnRows + 1));
        bh = Math.min(bh, 48);

        for (int rr = 0; rr < btnRows; rr++) {
            for (int cc = 0; cc < btnCols; cc++) {
                String lab = grid[rr][cc];
                int bx = btnAreaX + cc * (bw + gap);
                int by = btnAreaY + rr * (bh + gap);
                boolean isOp = "/-*+=".contains(lab);
                int btnCol = isOp ? accent : (DraggableWindow.darkTheme ? 0xFF2B2B2B : 0xFFEFEFEF);
                int textCol = isOp ? DraggableWindow.contrastingColorFor(btnCol) : textColor;
                guiGraphics.fill(bx, by, bx + bw, by + bh, btnCol);
                int labelW = Minecraft.getInstance().font.width(lab);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(lab), bx + (bw / 2) - (labelW / 2), by + (bh / 2) - 4, textCol, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        int[] r = window.getRenderRect(26);
        int padding = 12;
        int cx = r[0] + padding;
        int cy = r[1] + 30;
        int cw = Math.max(160, r[2] - padding * 2);

        int dispH = 56;
        int dispW = cw;
        int clearH = 22;
        int clearY = cy + dispH + 10;
        int clearW = dispW;
        if (mouseRelX >= cx && mouseRelX <= cx + clearW && mouseRelY >= clearY && mouseRelY <= clearY + clearH) {
            onButton("C");
            return true;
        }

        String[][] grid = {{"7","8","9","/"},{"4","5","6","*"},{"1","2","3","-"},{"0",".","=","+"}};
        int gap = 8;
        int btnAreaX = cx;
        int btnAreaY = clearY + clearH + 10;
        int btnAreaW = cw;
        int btnCols = 4;
        int btnRows = 4;
        int bw = (btnAreaW - gap * (btnCols - 1)) / btnCols;
        int bh = Math.max(36, (r[3] - (btnAreaY - r[1])) / (btnRows + 1));
        bh = Math.min(bh, 48);
        for (int rr = 0; rr < btnRows; rr++) {
            for (int cc = 0; cc < btnCols; cc++) {
                int bx = btnAreaX + cc * (bw + gap);
                int by = btnAreaY + rr * (bh + gap);
                if (mouseRelX >= bx && mouseRelX <= bx + bw && mouseRelY >= by && mouseRelY <= by + bh) {
                    String lab = grid[rr][cc];
                    onButton(lab);
                    return true;
                }
            }
        }
        return false;
    }

    private void onButton(String lab) {
        asyncManager.submitCPUTask(() -> {
            try {
                String newDisplay = display;
                double newAcc = acc;
                String newOp = op;
                boolean newResetNext = resetNext;

                String expr = "";
                String lastCalcToShow = null;
                boolean setShowLastCalc = false;

                switch (lab) {
                    case "C":
                        newDisplay = "0";
                        newAcc = 0;
                        newOp = "";
                        newResetNext = false;
                        expr = "";
                        lastCalcToShow = "";
                        setShowLastCalc = false;
                        break;
                    case "+":
                    case "-":
                    case "*":
                    case "/":
                        double cur = Double.parseDouble(newDisplay);
                        if (!newOp.isEmpty()) newAcc = apply(newAcc, cur, newOp);
                        else newAcc = cur;
                        newOp = lab;
                        newResetNext = true;
                        newDisplay = strip(newAcc);
                        expr = strip(newAcc) + " " + newOp;
                        break;
                    case "=":
                        double cur2 = Double.parseDouble(newDisplay);
                        if (!newOp.isEmpty()) {
                            lastCalcToShow = strip(newAcc) + " " + newOp + " " + newDisplay;
                            double result = apply(newAcc, cur2, newOp);
                            newAcc = result;
                            newDisplay = strip(newAcc);
                            newOp = "";
                            newResetNext = true;
                            setShowLastCalc = true;
                            expr = "";
                        }
                        break;
                    case ".":
                        if (!newDisplay.contains(".")) newDisplay += ".";
                        break;
                    default:
                        if (newResetNext) {
                            newDisplay = lab;
                            newResetNext = false;
                        }
                        else newDisplay = ("0".equals(newDisplay) ? lab : newDisplay + lab);
                        break;
                }

                if (!setShowLastCalc) {
                    if (!newOp.isEmpty()) {
                        expr = strip(newAcc) + " " + newOp;
                        if (!newResetNext && newDisplay != null && !newDisplay.isEmpty()) expr += " " + newDisplay;
                    } else {
                        expr = "";
                    }
                }

                final String finalDisplay = newDisplay;
                final double finalAcc = newAcc;
                final String finalOp = newOp;
                final boolean finalResetNext = newResetNext;
                final String finalExpr = expr;
                final String finalLastCalc = lastCalcToShow;
                final boolean finalShowLast = setShowLastCalc;

                asyncManager.executeOnMainThread(() -> {
                    display = finalDisplay;
                    acc = finalAcc;
                    op = finalOp;
                    resetNext = finalResetNext;
                    expression = finalExpr;

                    if (finalShowLast) {
                        lastCalculation = finalLastCalc == null ? "" : finalLastCalc;
                        showLastCalculation = true;
                    } else if ("C".equals(lab)) {
                        lastCalculation = "";
                        showLastCalculation = false;
                    } else {
                        showLastCalculation = false;
                    }
                });
            } catch (Exception e) {
                asyncManager.executeOnMainThread(() -> display = "ERR");
            }
        });
    }

    private double apply(double a, double b, String op) {
        return switch (op) {
            case "+" -> a + b;
            case "-" -> a - b;
            case "*" -> a * b;
            case "/" -> b == 0 ? 0 : a / b;
            default -> b;
        };
    }

    private String strip(double v) {
        if (v == (long)v) return String.format("%d", (long)v);
        return String.valueOf(v);
    }

    @Override public void mouseReleased(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {}
    @Override public boolean mouseDragged(DraggableWindow window, double mouseRelX, double mouseRelY, double dx, double dy) { return false; }
    @Override public boolean charTyped(DraggableWindow window, char codePoint, int modifiers) {
        if (Character.isDigit(codePoint) || codePoint == '.') {
            onButton(String.valueOf(codePoint));
            return true;
        }
        if (codePoint == '+' || codePoint == '-' || codePoint == '*' || codePoint == '/') {
            onButton(String.valueOf(codePoint));
            return true;
        }
        if (codePoint == '=' || codePoint == '\n' || codePoint == '\r') {
            onButton("=");
            return true;
        }
        if (codePoint == 'c' || codePoint == 'C') {
            onButton("C");
            return true;
        }
        return false;
    }

    @Override public boolean keyPressed(DraggableWindow window, int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            onButton("=");
            return true;
        }
        if (keyCode == 259) {
            asyncManager.executeOnMainThread(() -> {
                if (display == null || display.isEmpty() || "0".equals(display)) {
                    display = "0";
                } else {
                    if (display.length() <= 1) display = "0";
                    else display = display.substring(0, display.length() - 1);
                }
                if (!op.isEmpty()) {
                    expression = strip(acc) + " " + op;
                    if (!resetNext && display != null && !display.isEmpty()) expression += " " + display;
                } else {
                    expression = "";
                }
                showLastCalculation = false;
            });
            return true;
        }
        return false;
    }
    @Override public boolean onClose(DraggableWindow window) { return true; }
}