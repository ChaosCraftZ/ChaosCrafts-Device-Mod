package net.chaoscraft.chaoscrafts_device_mod.Client.Apps.BaseApps;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager.IApp;
import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DraggableWindow;
import net.chaoscraft.chaoscrafts_device_mod.Core.AsyncorAPI.AsyncRuntime;

public class CalculatorApp implements IApp {
    private String display = "0";
    private double acc = 0;
    private String op = "";
    private boolean resetNext = false;
    private final AsyncRuntime asyncRuntime = AsyncRuntime.get();

    @Override
    public void onOpen(DraggableWindow window) {
    }

    @Override
    public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16;
        guiGraphics.fill(cx, cy, cx + cw, cy + 60, 0xFF1E1E1E);
        guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(display), cx + 8, cy + 18, 0xFFFFFFFF, false);
        String[][] grid = {{"7","8","9","/"},{"4","5","6","*"},{"1","2","3","-"},{"0",".","=","+"}};
        int bw = Math.min(60, (cw - 24)/4);
        int by = cy + 70;
        for (int rrow=0;rrow<grid.length;rrow++){
            for (int ccol=0;ccol<4;ccol++){
                String lab = grid[rrow][ccol];
                int bx = cx + ccol*(bw+6);
                int byy = by + rrow*(32);
                guiGraphics.fill(bx, byy, bx + bw, byy + 28, 0xFF444444);
                guiGraphics.drawString(Minecraft.getInstance().font, Component.literal(lab), bx + 10, byy + 6, 0xFFFFFFFF, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button) {
        int[] r = window.getRenderRect(26);
        int cx = r[0] + 8, cy = r[1] + 28, cw = r[2] - 16;
        int bw = Math.min(60, (cw - 24)/4);
        int by = cy + 70;
        String[][] grid = {{"7","8","9","/"},{"4","5","6","*"},{"1","2","3","-"},{"0",".","=","+"}};
        for (int rrow=0;rrow<grid.length;rrow++){
            for (int ccol=0;ccol<4;ccol++){
                int bx = cx + ccol*(bw+6);
                int byy = by + rrow*(32);
                if (mouseRelX >= bx && mouseRelX <= bx + bw && mouseRelY >= byy && mouseRelY <= byy + 28) {
                    String lab = grid[rrow][ccol];
                    onButton(lab);
                    return true;
                }
            }
        }
        return false;
    }

    private void onButton(String lab) {
    asyncRuntime.submitCompute(() -> {
            try {
                String newDisplay = display;
                double newAcc = acc;
                String newOp = op;
                boolean newResetNext = resetNext;

                switch (lab) {
                    case "C":
                        newDisplay = "0";
                        newAcc = 0;
                        newOp = "";
                        newResetNext = false;
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
                        break;
                    case "=":
                        double cur2 = Double.parseDouble(newDisplay);
                        if (!newOp.isEmpty()) {
                            newAcc = apply(newAcc, cur2, newOp);
                            newDisplay = strip(newAcc);
                            newOp="";
                            newResetNext=true;
                        }
                        break;
                    case ".":
                        if (!newDisplay.contains(".")) newDisplay += ".";
                        break;
                    default:
                        if (newResetNext) {
                            newDisplay = lab;
                            newResetNext=false;
                        }
                        else newDisplay = ("0".equals(newDisplay) ? lab : newDisplay + lab);
                }

                final String finalDisplay = newDisplay;
                final double finalAcc = newAcc;
                final String finalOp = newOp;
                final boolean finalResetNext = newResetNext;

                asyncRuntime.runOnClientThread(() -> {
                    display = finalDisplay;
                    acc = finalAcc;
                    op = finalOp;
                    resetNext = finalResetNext;
                });
            } catch (Exception e) {
                asyncRuntime.runOnClientThread(() -> display = "ERR");
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
    @Override public boolean charTyped(DraggableWindow window, char codePoint, int modifiers) { return false; }
    @Override public boolean keyPressed(DraggableWindow window, int keyCode, int scanCode, int modifiers) { return false; }
    @Override public boolean onClose(DraggableWindow window) { return true; }
}