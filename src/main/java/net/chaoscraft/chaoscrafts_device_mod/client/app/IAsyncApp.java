package net.chaoscraft.chaoscrafts_device_mod.client.app;

// currently useless will be removed/refactored in future
public interface IAsyncApp extends IApp {
    default void mouseScrolledAsync(double mouseX, double mouseY, double delta) {
    }

    default void tickAsync() {
    }
}