package net.chaoscraft.chaoscrafts_device_mod.client.app;

import com.mojang.blaze3d.vertex.PoseStack;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DraggableWindow;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

// example of a custom app add-on
public class ExampleAddOn {
    public static void register() {
        AppFactory.registerApp("example", ExampleApp::new);
        IconManager.registerCustomIcon("example", ResourceLocation.tryParse("chaoscrafts_device_mod:textures/gui/icons/default_icon.png"));
        try {
            AppRegistry.getInstance().installApp("example", "Example App", "A sample add-on app", "1.0");
        } catch (Exception ignored) {}
    }

    public static class ExampleApp implements IApp {
        private DraggableWindow window;

        @Override
        public void onOpen(DraggableWindow window) {
            this.window = window;
        }

        @Override
        public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
            int w = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int h = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
            String txt = "Hello from Example App";
            int tw = net.minecraft.client.Minecraft.getInstance().font.width(txt);
            guiGraphics.drawString(net.minecraft.client.Minecraft.getInstance().font, Component.literal(txt), Math.max(10, (w - tw) / 2), Math.max(40, (h / 2) - 8), 0xFFFFFFFF, false);
        }

        @Override
        public boolean mouseClicked(DraggableWindow window, double mouseRelX, double mouseRelY, int button) { return false; }

        @Override
        public void mouseReleased(DraggableWindow window, double mouseRelX, double mouseRelY, int button) { }

        @Override
        public boolean mouseDragged(DraggableWindow window, double mouseRelX, double mouseRelY, double dx, double dy) { return false; }

        @Override
        public boolean charTyped(DraggableWindow window, char codePoint, int modifiers) { return false; }

        @Override
        public boolean keyPressed(DraggableWindow window, int keyCode, int scanCode, int modifiers) { return false; }

        @Override
        public boolean onClose(DraggableWindow window) { return true; }
    }
}