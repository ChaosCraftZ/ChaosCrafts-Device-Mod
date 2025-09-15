package net.chaoscraft.chaoscrafts_device_mod.client.async;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.chaoscraft.chaoscrafts_device_mod.client.screen.DraggableWindow;

import java.util.concurrent.ConcurrentLinkedQueue;

public class ThreadSafeRenderer {
    private final ConcurrentLinkedQueue<Runnable> renderQueue = new ConcurrentLinkedQueue<>();

    public void addToRenderQueue(Runnable renderTask) {
        renderQueue.offer(renderTask);
    }

    public void processRenderQueue(GuiGraphics guiGraphics, PoseStack poseStack,
                                   DraggableWindow window, int mouseRelX, int mouseRelY,
                                   float partialTick) {
        Runnable task;
        while ((task = renderQueue.poll()) != null) {
            task.run();
        }
    }

    public void clearQueue() {
        renderQueue.clear();
    }

    public int getQueueSize() {
        return renderQueue.size();
    }
}