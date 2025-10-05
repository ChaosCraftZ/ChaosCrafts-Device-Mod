package net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager;

import com.mojang.blaze3d.vertex.PoseStack;

import net.chaoscraft.chaoscrafts_device_mod.Client.Screen.DraggableWindow;
import net.minecraft.client.gui.GuiGraphics;

public interface IApp {
    void onOpen(DraggableWindow window);
    void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick);
    boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button);
    void mouseReleased(DraggableWindow window, double mouseRelX, double mouseRelY, int button);
    boolean mouseDragged(DraggableWindow window, double mouseRelX, double mouseRelY, double dx, double dy);
    boolean charTyped(DraggableWindow window, char codePoint, int modifiers);
    boolean keyPressed(DraggableWindow window, int keyCode, int scanCode, int modifiers);
    // Called when a key is released. Default implementation returns false (not consumed).
    default boolean keyReleased(DraggableWindow window, int keyCode, int scanCode, int modifiers) { return false; }
    // Return true to allow the window to close immediately, false to cancel/handle (e.g. show save dialog)
    default boolean onClose(DraggableWindow window) { return true; }

    // Add these new methods
    default void tick() {} // Default empty implementation
    default boolean mouseScrolled(double mouseX, double mouseY, double delta) { return false; } // Default implementation
}