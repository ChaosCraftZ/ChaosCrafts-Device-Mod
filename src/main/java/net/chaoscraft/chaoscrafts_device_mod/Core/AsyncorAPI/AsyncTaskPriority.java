package net.chaoscraft.chaoscrafts_device_mod.Core.AsyncorAPI;

/**
 * Priority levels used by the asynchronous schedulers. Higher ordinal means higher priority.
 */
public enum AsyncTaskPriority {
    CRITICAL(3),
    HIGH(2),
    NORMAL(1),
    LOW(0);

    private final int weight;

    AsyncTaskPriority(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }
}
