package net.chaoscraft.chaoscrafts_device_mod.Core.AsyncorAPI;

import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Represents a unit of work submitted to an {@link AsyncScheduler}.
 */
final class AsyncTask<T> implements Comparable<AsyncTask<?>> {
    private final Callable<T> callable;
    private final AsyncTaskPriority priority;
    private final long createdAt;
    private final String category;

    AsyncTask(Callable<T> callable, AsyncTaskPriority priority, String category) {
        this.callable = Objects.requireNonNull(callable, "callable");
        this.priority = Objects.requireNonNull(priority, "priority");
        this.category = category == null ? "general" : category;
        this.createdAt = System.nanoTime();
    }

    Callable<T> getCallable() {
        return callable;
    }

    AsyncTaskPriority getPriority() {
        return priority;
    }

    String getCategory() {
        return category;
    }

    long getCreatedAt() {
        return createdAt;
    }

    @Override
    public int compareTo(AsyncTask<?> o) {
        int priorityCompare = Integer.compare(o.priority.getWeight(), this.priority.getWeight());
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        return Long.compare(this.createdAt, o.createdAt);
    }
}
