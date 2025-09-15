package net.chaoscraft.chaoscrafts_device_mod.client.app;

import com.mojang.blaze3d.vertex.PoseStack;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DraggableWindow;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * ExampleAddOn demonstrates how an external developer can register a new app and icon.
 * This class is called from client setup as an example; add-ons should perform registration
 * during their client initialization (FMLClientSetupEvent) or another safe post-initialization step.
 */
public class ExampleAddOn {
    public static void register() {
        // Register a simple app factory under the internal name "example"
        AppFactory.registerApp("example", ExampleApp::new);

        // Register an icon for the app. If you don't call this, the IconManager will serve default_icon.png.
        IconManager.registerCustomIcon("example", ResourceLocation.tryParse("chaoscrafts_device_mod:textures/gui/icons/default_icon.png"));

        // Optionally install the app so it appears in the taskbar/desktop automatically
        try {
            AppRegistry.getInstance().installApp("example", "Example App", "A sample add-on app", "1.0");
        } catch (Exception ignored) {}
    }

    // Minimal example app implementation. Real add-ons should implement a full-featured IApp.
    public static class ExampleApp implements IApp {
        private DraggableWindow window;

        @Override
        public void onOpen(DraggableWindow window) {
            this.window = window;
        }

        @Override
        public void renderContent(GuiGraphics guiGraphics, PoseStack poseStack, DraggableWindow window, int mouseRelX, int mouseRelY, float partialTick) {
            // Draw simple centered text
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

