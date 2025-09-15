package net.chaoscraft.chaoscrafts_device_mod.client.async;

import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class AsyncTaskManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static AsyncTaskManager INSTANCE;

    private final ExecutorService ioBoundExecutor;
    private final ExecutorService cpuBoundExecutor;
    private final ScheduledExecutorService scheduledExecutor;

    private final AtomicInteger activeTasks = new AtomicInteger(0);
    private long totalTasksExecuted = 0;

    private AsyncTaskManager() {
        // I/O bound tasks (file operations, network requests)
        ioBoundExecutor = Executors.newFixedThreadPool(4, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "PC-UI-IO-" + counter.incrementAndGet());
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            }
        });

        // CPU bound tasks (image processing, calculations)
        cpuBoundExecutor = Executors.newWorkStealingPool();

        // Scheduled tasks
        scheduledExecutor = Executors.newScheduledThreadPool(2, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "PC-UI-Scheduled-" + counter.incrementAndGet());
            }
        });

        LOGGER.info("AsyncTaskManager initialized with thread pools");
    }

    public static AsyncTaskManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AsyncTaskManager();
        }
        return INSTANCE;
    }

    // Add this method to fix the error
    public ExecutorService getIoExecutor() {
        return ioBoundExecutor;
    }

    // ... rest of the class remains the same ...


    public CompletableFuture<Void> submitIOTask(Runnable task) {
        activeTasks.incrementAndGet();
        return CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } finally {
                activeTasks.decrementAndGet();
                totalTasksExecuted++;
            }
        }, ioBoundExecutor);
    }

    public <T> CompletableFuture<T> submitIOTask(Callable<T> task) {
        activeTasks.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            } finally {
                activeTasks.decrementAndGet();
                totalTasksExecuted++;
            }
        }, ioBoundExecutor);
    }

    public CompletableFuture<Void> submitCPUTask(Runnable task) {
        activeTasks.incrementAndGet();
        return CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } finally {
                activeTasks.decrementAndGet();
                totalTasksExecuted++;
            }
        }, cpuBoundExecutor);
    }

    public <T> CompletableFuture<T> submitCPUTask(Callable<T> task) {
        activeTasks.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            } finally {
                activeTasks.decrementAndGet();
                totalTasksExecuted++;
            }
        }, cpuBoundExecutor);
    }

    public ScheduledFuture<?> scheduleTask(Runnable task, long delay, TimeUnit unit) {
        activeTasks.incrementAndGet();
        return scheduledExecutor.schedule(() -> {
            try {
                task.run();
            } finally {
                activeTasks.decrementAndGet();
                totalTasksExecuted++;
            }
        }, delay, unit);
    }

    public void executeOnMainThread(Runnable task) {
        Minecraft.getInstance().execute(task);
    }

    // Monitoring methods
    public int getActiveTaskCount() {
        return activeTasks.get();
    }

    public long getTotalTasksExecuted() {
        return totalTasksExecuted;
    }

    public void shutdown() {
        ioBoundExecutor.shutdown();
        cpuBoundExecutor.shutdown();
        scheduledExecutor.shutdown();

        try {
            if (!ioBoundExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ioBoundExecutor.shutdownNow();
            }
            if (!cpuBoundExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cpuBoundExecutor.shutdownNow();
            }
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ioBoundExecutor.shutdownNow();
            cpuBoundExecutor.shutdownNow();
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOGGER.info("AsyncTaskManager shutdown completed");
    }
}