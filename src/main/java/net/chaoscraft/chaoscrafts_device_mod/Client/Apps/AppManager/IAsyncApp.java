package net.chaoscraft.chaoscrafts_device_mod.Client.Apps.AppManager;

/**
 * Interface for apps that support asynchronous operations
 */
public interface IAsyncApp extends IApp {
    /**
     * Asynchronous version of mouseScrolled for CPU-intensive operations
     */
    default void mouseScrolledAsync(double mouseX, double mouseY, double delta) {
        // Default implementation does nothing
    }

    /**
     * Asynchronous version of tick for background processing
     */
    default void tickAsync() {
        // Default implementation does nothing
    }
}