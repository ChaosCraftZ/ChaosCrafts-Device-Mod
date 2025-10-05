package net.chaoscraft.chaoscrafts_device_mod.Core.AsyncorAPI;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Central asynchronous runtime shared between client and server. Provides prioritised workers for
 * I/O bound and compute bound tasks, scheduled execution, thread dispatch helpers, and optional
 * handles so callers can cancel or introspect queued work. Chaos Smell's XD
 */
public final class AsyncRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AsyncRuntime INSTANCE = new AsyncRuntime();
    private static final long DEFAULT_ENQUEUE_TIMEOUT_MS = Long.getLong("cdm.async.enqueueTimeout", 200L);
    private static final String CATEGORY_IO = "io";
    private static final String CATEGORY_CPU = "cpu";

    private final AsyncScheduler ioScheduler;
    private final AsyncScheduler computeScheduler;
    private final ScheduledExecutorService timerExecutor;
    private final ExecutorService fireAndForgetExecutor;

    private AsyncRuntime() {
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
        // Detect SMT/hyperthreading: assume if cores > logical processors / 2, it's SMT
        int logical = cores;
        int physical = Math.max(1, logical / 2); // Rough estimate
        boolean hasSMT = logical > physical;
        int ioThreads = hasSMT ? Math.max(4, Math.min(16, logical / 2)) : Math.max(2, Math.min(8, cores / 2));
        int computeThreads = hasSMT ? Math.max(4, Math.min(logical, logical - 2)) : Math.max(2, Math.min(cores, cores - 1));
        this.ioScheduler = new AsyncScheduler("IO", 1, ioThreads, ioThreads * 512);
        this.computeScheduler = new AsyncScheduler("CPU", 1, computeThreads, computeThreads * 512);
        this.timerExecutor = Executors.newScheduledThreadPool(2, new NamedThreadFactory("CDM-Timer"));
        this.fireAndForgetExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("CDM-Aux"));
    }

    public static AsyncRuntime get() {
        return INSTANCE;
    }

    /* -------------------- Submission helpers (CompletableFuture) -------------------- */

    public CompletableFuture<Void> submitIo(Runnable runnable) {
        return submit(ioScheduler, AsyncTaskPriority.NORMAL, asCallable(runnable), deriveCategory(CATEGORY_IO, runnable));
    }

    public <T> CompletableFuture<T> submitIo(Callable<T> callable) {
        return submit(ioScheduler, AsyncTaskPriority.NORMAL, callable, deriveCategory(CATEGORY_IO, callable));
    }

    public CompletableFuture<Void> submitCompute(Runnable runnable) {
        return submit(computeScheduler, AsyncTaskPriority.NORMAL, asCallable(runnable), deriveCategory(CATEGORY_CPU, runnable));
    }

    public <T> CompletableFuture<T> submitCompute(Callable<T> callable) {
        return submit(computeScheduler, AsyncTaskPriority.NORMAL, callable, deriveCategory(CATEGORY_CPU, callable));
    }

    public CompletableFuture<Void> submitIo(AsyncTaskPriority priority, Runnable runnable) {
        return submit(ioScheduler, priority, asCallable(runnable), deriveCategory(CATEGORY_IO, runnable));
    }

    public <T> CompletableFuture<T> submitIo(AsyncTaskPriority priority, Callable<T> callable) {
        return submit(ioScheduler, priority, callable, deriveCategory(CATEGORY_IO, callable));
    }

    public CompletableFuture<Void> submitIo(String category, AsyncTaskPriority priority, Runnable runnable) {
        String resolved = category != null ? category : CATEGORY_IO;
        return submit(ioScheduler, priority, asCallable(runnable), resolved);
    }

    public <T> CompletableFuture<T> submitIo(String category, AsyncTaskPriority priority, Callable<T> callable) {
        String resolved = category != null ? category : CATEGORY_IO;
        return submit(ioScheduler, priority, callable, resolved);
    }

    public CompletableFuture<Void> submitCompute(AsyncTaskPriority priority, Runnable runnable) {
        return submit(computeScheduler, priority, asCallable(runnable), deriveCategory(CATEGORY_CPU, runnable));
    }

    public <T> CompletableFuture<T> submitCompute(AsyncTaskPriority priority, Callable<T> callable) {
        return submit(computeScheduler, priority, callable, deriveCategory(CATEGORY_CPU, callable));
    }

    public CompletableFuture<Void> submitCompute(String category, AsyncTaskPriority priority, Runnable runnable) {
        String resolved = category != null ? category : CATEGORY_CPU;
        return submit(computeScheduler, priority, asCallable(runnable), resolved);
    }

    public <T> CompletableFuture<T> submitCompute(String category, AsyncTaskPriority priority, Callable<T> callable) {
        String resolved = category != null ? category : CATEGORY_CPU;
        return submit(computeScheduler, priority, callable, resolved);
    }

    /* -------------------- Submission helpers (Job handles) -------------------- */

    public AsyncJob<Void> submitIoJob(Runnable runnable) {
        return submitJob(ioScheduler, AsyncTaskPriority.NORMAL, asCallable(runnable), deriveCategory(CATEGORY_IO, runnable));
    }

    public <T> AsyncJob<T> submitIoJob(Callable<T> callable) {
        return submitJob(ioScheduler, AsyncTaskPriority.NORMAL, callable, deriveCategory(CATEGORY_IO, callable));
    }

    public AsyncJob<Void> submitComputeJob(Runnable runnable) {
        return submitJob(computeScheduler, AsyncTaskPriority.NORMAL, asCallable(runnable), deriveCategory(CATEGORY_CPU, runnable));
    }

    public <T> AsyncJob<T> submitComputeJob(Callable<T> callable) {
        return submitJob(computeScheduler, AsyncTaskPriority.NORMAL, callable, deriveCategory(CATEGORY_CPU, callable));
    }

    /* -------------------- Scheduling & dispatch -------------------- */

    public ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit unit) {
        Objects.requireNonNull(runnable, "runnable");
        Objects.requireNonNull(unit, "unit");
        return timerExecutor.schedule(() -> safeRun(runnable), delay, unit);
    }

    public ScheduledFuture<?> scheduleAtFixedRate(Runnable runnable, long initialDelay, long period, TimeUnit unit) {
        Objects.requireNonNull(runnable, "runnable");
        Objects.requireNonNull(unit, "unit");
        return timerExecutor.scheduleAtFixedRate(() -> safeRun(runnable), initialDelay, period, unit);
    }

    public CompletableFuture<Void> runOnClientThreadAsync(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        CompletableFuture<Void> future = new CompletableFuture<>();
        runOnClientThread(() -> {
            try {
                runnable.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
                LOGGER.error("Client-thread async task failed", t);
            }
        });
        return future;
    }

    public CompletableFuture<Void> runLaterOnClientThread(Runnable runnable, long delay, TimeUnit unit) {
        Objects.requireNonNull(runnable, "runnable");
        Objects.requireNonNull(unit, "unit");
        CompletableFuture<Void> future = new CompletableFuture<>();
        schedule(() -> runOnClientThread(() -> {
            try {
                runnable.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
                LOGGER.error("Delayed client-thread task failed", t);
            }
        }), delay, unit);
        return future;
    }

    public CompletableFuture<Void> runOnServerThreadAsync(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        CompletableFuture<Void> future = new CompletableFuture<>();
        runOnServerThread(() -> {
            try {
                runnable.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
                LOGGER.error("Server-thread async task failed", t);
            }
        });
        return future;
    }

    public CompletableFuture<Void> runOnMainThreadAsync(Supplier<Boolean> serverCheck, Runnable runnable) {
        Objects.requireNonNull(serverCheck, "serverCheck");
        Objects.requireNonNull(runnable, "runnable");
        CompletableFuture<Void> future = new CompletableFuture<>();
        runOnMainThread(serverCheck, () -> {
            try {
                runnable.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
                LOGGER.error("Main-thread async task failed", t);
            }
        });
        return future;
    }

    public <T> CompletableFuture<T> submitIoThenClient(Callable<T> callable, Consumer<T> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        CompletableFuture<T> future = submitIo(callable);
        attachMainThreadContinuation(future, consumer, false);
        return future;
    }

    public <T> CompletableFuture<T> submitIoThenServer(Callable<T> callable, Consumer<T> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        CompletableFuture<T> future = submitIo(callable);
        attachMainThreadContinuation(future, consumer, true);
        return future;
    }

    public <T> CompletableFuture<T> submitComputeThenClient(Callable<T> callable, Consumer<T> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        CompletableFuture<T> future = submitCompute(callable);
        attachMainThreadContinuation(future, consumer, false);
        return future;
    }

    public <T> CompletableFuture<T> submitComputeThenServer(Callable<T> callable, Consumer<T> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        CompletableFuture<T> future = submitCompute(callable);
        attachMainThreadContinuation(future, consumer, true);
        return future;
    }

    /* -------------------- Metrics -------------------- */

    public AsyncStats snapshotStats() {
        return new AsyncStats(
                ioScheduler.getWorkerCount(),
                computeScheduler.getWorkerCount(),
                getQueuedTaskCount(),
                getTotalTasksSubmitted(),
                getTotalTasksCompleted(),
                getTotalTasksRejected());
    }

    public int getActiveWorkerCount() {
        return ioScheduler.getWorkerCount() + computeScheduler.getWorkerCount();
    }

    public int getQueuedTaskCount() {
        return ioScheduler.getQueueSize() + computeScheduler.getQueueSize();
    }

    public long getTotalTasksSubmitted() {
        return ioScheduler.getTasksSubmitted() + computeScheduler.getTasksSubmitted();
    }

    public long getTotalTasksCompleted() {
        return ioScheduler.getTasksCompleted() + computeScheduler.getTasksCompleted();
    }

    public long getTotalTasksRejected() {
        return ioScheduler.getTasksRejected() + computeScheduler.getTasksRejected();
    }

    /* -------------------- Thread dispatch -------------------- */

    public void runOnClientThread(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        fireAndForgetExecutor.execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                LOGGER.debug("Client thread not available; running task immediately");
                safeRun(runnable);
            } else {
                mc.execute(() -> safeRun(runnable));
            }
        });
    }

    public void runOnServerThread(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        fireAndForgetExecutor.execute(() -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                server.execute(() -> safeRun(runnable));
            } else {
                LOGGER.debug("Server thread not available; running task immediately");
                safeRun(runnable);
            }
        });
    }

    public void runOnMainThread(Supplier<Boolean> serverCheck, Runnable runnable) {
        Objects.requireNonNull(serverCheck, "serverCheck");
        Objects.requireNonNull(runnable, "runnable");
        if (Boolean.TRUE.equals(serverCheck.get())) {
            runOnServerThread(runnable);
        } else {
            runOnClientThread(runnable);
        }
    }

    public void shutdown() {
        ioScheduler.shutdown();
        computeScheduler.shutdown();
        timerExecutor.shutdownNow();
        fireAndForgetExecutor.shutdownNow();
    }

    /* -------------------- Internal helpers -------------------- */

    private <T> CompletableFuture<T> submit(AsyncScheduler scheduler, AsyncTaskPriority priority, Callable<T> callable, String category) {
        return submitJob(scheduler, priority, callable, category).future();
    }

    private <T> AsyncJob<T> submitJob(AsyncScheduler scheduler, AsyncTaskPriority priority, Callable<T> callable, String category) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(callable, "callable");
        Objects.requireNonNull(category, "category");
        AsyncTask<T> task = new AsyncTask<>(callable, priority, category);
        AsyncScheduler.JobHandle<T> handle = scheduler.submit(task, DEFAULT_ENQUEUE_TIMEOUT_MS);
        if (handle.isRejected()) {
            LOGGER.warn("Async task rejected for category '{}' on {}", category, schedulerName(scheduler));
        }
        return new AsyncJob<>(handle, category);
    }

    private <T> void attachMainThreadContinuation(CompletableFuture<T> future, Consumer<T> consumer, boolean serverThread) {
        future.whenComplete((result, error) -> {
            Runnable dispatcher = () -> {
                if (error == null) {
                    safeAccept(consumer, result);
                } else {
                    LOGGER.error("Async continuation failed", error);
                }
            };
            if (serverThread) {
                runOnServerThread(dispatcher);
            } else {
                runOnClientThread(dispatcher);
            }
        });
    }

    private static <T> void safeAccept(Consumer<T> consumer, T value) {
        try {
            consumer.accept(value);
        } catch (Throwable t) {
            LOGGER.error("Consumer threw while handling async result", t);
        }
    }

    private static Callable<Void> asCallable(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        return () -> {
            runnable.run();
            return null;
        };
    }

    private static String deriveCategory(String base, Object source) {
        if (source == null) {
            return base;
        }
        String simple = source.getClass().getSimpleName();
        if (simple == null || simple.isBlank()) {
            simple = source.getClass().getName();
        }
        if (simple.length() > 48) {
            simple = simple.substring(0, 48);
        }
        return base + ":" + simple;
    }

    private String schedulerName(AsyncScheduler scheduler) {
        if (scheduler == ioScheduler) {
            return "IO scheduler";
        }
        if (scheduler == computeScheduler) {
            return "CPU scheduler";
        }
        return scheduler.toString();
    }

    private static void safeRun(Runnable runnable) {
        try {
            runnable.run();
        } catch (Throwable t) {
            LOGGER.error("Async main-thread task failed", t);
        }
    }

    /* -------------------- Value types -------------------- */

    public static final class AsyncJob<T> {
        private final AsyncScheduler.JobHandle<T> handle;
        private final String category;

        private AsyncJob(AsyncScheduler.JobHandle<T> handle, String category) {
            this.handle = handle;
            this.category = category;
        }

        public CompletableFuture<T> future() {
            return handle.future();
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            return handle.cancel(mayInterruptIfRunning);
        }

        public boolean isStarted() {
            return handle.isStarted();
        }

        public boolean isRejected() {
            return handle.isRejected();
        }

        public String category() {
            return category;
        }
    }

    public record AsyncStats(
            int ioWorkers,
            int computeWorkers,
            int queuedTasks,
            long totalSubmitted,
            long totalCompleted,
            long totalRejected
    ) {
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();
        private final String baseName;

        private NamedThreadFactory(String baseName) {
            this.baseName = baseName;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, baseName + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
