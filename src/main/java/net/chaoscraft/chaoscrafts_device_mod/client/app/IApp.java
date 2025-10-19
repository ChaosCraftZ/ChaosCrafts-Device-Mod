package net.chaoscraft.chaoscrafts_device_mod.client.app;

import com.mojang.blaze3d.vertex.PoseStack;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DraggableWindow;
import net.minecraft.client.gui.GuiGraphics;

public interface IApp {
    void onOpen(DraggableWindow window);
    void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick);
    boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button);
    void mouseReleased(DraggableWindow window, double mouseRelX, double mouseRelY, int button);
    boolean mouseDragged(DraggableWindow window, double mouseRelX, double mouseRelY, double dx, double dy);
    boolean charTyped(DraggableWindow window, char codePoint, int modifiers);
    boolean keyPressed(DraggableWindow window, int keyCode, int scanCode, int modifiers);
    default boolean keyReleased(DraggableWindow window, int keyCode, int scanCode, int modifiers) { return false; }
    default boolean onClose(DraggableWindow window) { return true; }

    default void tick() {}
    default boolean mouseScrolled(double mouseX, double mouseY, double delta) { return false; }
    default void debugRender(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {}
}