package net.chaoscraft.chaoscrafts_device_mod.Core.AsyncorAPI;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Priority aware scheduler backed by a bounded priority queue. Designed to be used for both
 * client and server side workloads. This is inspired by the multi-threaded infrastructure from
 * the original ChaosCrafts threading prototypes but trimmed down to keep dependencies local.
 */
final class AsyncScheduler {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final String name;
    private final PriorityBlockingQueue<AsyncTaskHolder<?>> queue = new PriorityBlockingQueue<>();
    private final ExecutorService executor;
    private final AtomicInteger workerCount = new AtomicInteger();
    private final int minThreads;
    private final int maxThreads;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final LongAdder tasksSubmitted = new LongAdder();
    private final LongAdder tasksCompleted = new LongAdder();
    private final LongAdder tasksRejected = new LongAdder();
    private final Semaphore queuePermits;

    AsyncScheduler(String name, int minThreads, int maxThreads, int maxQueueSize) {
        if (minThreads <= 0 || maxThreads <= 0 || maxThreads < minThreads) {
            throw new IllegalArgumentException("Invalid scheduler thread bounds");
        }
        this.name = name;
        this.minThreads = minThreads;
        this.maxThreads = maxThreads;
        this.queuePermits = new Semaphore(Math.max(maxQueueSize, maxThreads * 128));
        this.executor = new ThreadPoolExecutor(minThreads, maxThreads, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new SchedulerThreadFactory(name));
        ((ThreadPoolExecutor) this.executor).allowCoreThreadTimeOut(true);
        for (int i = 0; i < minThreads; i++) {
            spawnWorker();
        }
        LOGGER.debug("AsyncScheduler '{}' started with {}-{} threads (dynamic)", name, minThreads, maxThreads);
    }

    <T> JobHandle<T> submit(AsyncTask<T> task, long enqueueTimeoutMillis) {
        if (!running.get()) {
            throw new RejectedExecutionException("Scheduler is shutdown");
        }
        tasksSubmitted.increment();
        boolean acquired;
        try {
            if (enqueueTimeoutMillis <= 0) {
                acquired = queuePermits.tryAcquire();
            } else {
                acquired = queuePermits.tryAcquire(enqueueTimeoutMillis, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            acquired = false;
        }

        if (!acquired) {
            tasksRejected.increment();
            CompletableFuture<T> failure = new CompletableFuture<>();
            failure.completeExceptionally(new RejectedExecutionException("Queue full for scheduler " + name));
            return new JobHandle<>(this, null, failure, true);
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        AsyncTaskHolder<T> holder = new AsyncTaskHolder<>(task, future);
        queue.add(holder);
        maybeSpawnExtraWorker();
        return new JobHandle<>(this, holder, future, false);
    }

    void shutdown() {
        if (running.compareAndSet(true, false)) {
            executor.shutdownNow();
            queue.clear();
        }
    }

    int getQueueSize() {
        return queue.size();
    }

    int getWorkerCount() {
        return workerCount.get();
    }

    long getTasksSubmitted() {
        return tasksSubmitted.sum();
    }

    long getTasksCompleted() {
        return tasksCompleted.sum();
    }

    long getTasksRejected() {
        return tasksRejected.sum();
    }

    private void maybeSpawnExtraWorker() {
        int qSize = queue.size();
        while (true) {
            int current = workerCount.get();
            if (current >= maxThreads) {
                return;
            }
            if (current >= qSize) {
                return;
            }
            if (workerCount.compareAndSet(current, current + 1)) {
                spawnWorker();
                return;
            }
        }
    }
    // *Yawn* I guess we need another worker Chaos, as the other ones are chocking
    private void spawnWorker() {
        executor.execute(this::workerLoop);
    }

    private void workerLoop() {
        try {
            while (running.get()) {
                AsyncTaskHolder<?> holder = queue.poll(2, TimeUnit.SECONDS);
                if (holder == null) {
                    int current = workerCount.get();
                    if (current > minThreads && workerCount.compareAndSet(current, current - 1)) {
                        return;
                    }
                    continue;
                }
                runHolder(holder);
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            workerCount.updateAndGet(v -> Math.max(0, v - 1));
        }
    }

    private <T> void runHolder(AsyncTaskHolder<T> holder) {
        try {
            holder.markStarted();
            T result = holder.task.getCallable().call();
            holder.future.complete(result);
        } catch (Throwable t) {
            holder.future.completeExceptionally(t);
            LOGGER.debug("Async task in '{}' failed: {}", name, t.toString());
        } finally {
            tasksCompleted.increment();
            queuePermits.release();
        }
    }

    private <T> boolean cancel(AsyncTaskHolder<T> holder, boolean mayInterrupt) {
        if (holder.isStarted()) {
            return false;
        }
        boolean removed = queue.remove(holder);
        if (removed) {
            queuePermits.release();
            holder.future.cancel(mayInterrupt);
            tasksRejected.increment();
            return true;
        }
        return holder.future.isCancelled();
    }

    static final class JobHandle<T> {
        private final AsyncScheduler scheduler;
        private final AsyncTaskHolder<T> holder;
        private final CompletableFuture<T> future;
        private final boolean rejected;

        JobHandle(AsyncScheduler scheduler, AsyncTaskHolder<T> holder, CompletableFuture<T> future, boolean rejected) {
            this.scheduler = scheduler;
            this.holder = holder;
            this.future = future;
            this.rejected = rejected;
        }

        public CompletableFuture<T> future() {
            return future;
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            if (rejected || holder == null) {
                return false;
            }
            return scheduler.cancel(holder, mayInterruptIfRunning);
        }

        public boolean isRejected() {
            return rejected;
        }

        public boolean isStarted() {
            return holder != null && holder.isStarted();
        }
    }
    // Holder for tasks in the priority queue, tracks if started to bcs why not
    private static final class AsyncTaskHolder<T> implements Comparable<AsyncTaskHolder<?>> {
        private final AsyncTask<T> task;
        private final CompletableFuture<T> future;
        private final AtomicBoolean started = new AtomicBoolean(false);

        private AsyncTaskHolder(AsyncTask<T> task, CompletableFuture<T> future) {
            this.task = task;
            this.future = future;
        }

        private void markStarted() {
            started.set(true);
        }

        private boolean isStarted() {
            return started.get();
        }

        @Override
        public int compareTo(AsyncTaskHolder<?> o) {
            return task.compareTo(o.task);
        }
    }

    private static final class SchedulerThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger();
        private final String baseName;

        private SchedulerThreadFactory(String baseName) {
            this.baseName = baseName;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "CDM-Async-" + baseName + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
