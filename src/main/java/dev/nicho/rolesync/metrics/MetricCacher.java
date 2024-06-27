package dev.nicho.rolesync.metrics;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.Callable;

public class MetricCacher<T> {

    private final long interval;
    private final JavaPlugin plugin;
    private final Callable<T> callable;

    private T value;
    private BukkitTask task;

    /**
     * Creates and starts a metric cacher. This will asynchronously call the callable
     * every `interval` ticks, and store the value which can be retrieved later with
     * getValue(). Value is not guaranteed to be initialized immediately, and can
     * return null before first run to cache it. It is the user's responsibility to
     * call .stop() on this to stop tick.
     *
     * @param plugin   The JavaPlugin
     * @param callable The callable which should return the value to cache. Should be thread-safe
     *                 as it will be called asynchronously.
     * @param interval The interval, in ticks, to run the callable
     */
    public MetricCacher(JavaPlugin plugin, Callable<T> callable, long interval) {
        this.interval = interval;
        this.plugin = plugin;
        this.callable = callable;

        this.start();
    }

    /**
     * Gets the cached value. Can be null if the callable was not called yet.
     *
     * @return the value that is cached by this MetricCacher
     */
    public T getValue() {
        synchronized (this) {
            return value;
        }
    }

    /**
     * Starts the timers. Already called by the constructor, so it is
     * unnecessary (but safe) to call this unless to start again after
     * calling stop. Thread-safe.
     */
    public void start() {
        synchronized (this) {
            // Already started.
            if (this.task != null) return;

            Runnable run = () -> {
                synchronized (this) {
                    try {
                        this.value = this.callable.call();
                    } catch (Exception ignored) {
                        // This is just for metrics, we don't want to bother the user if there's a problem here.
                    }
                }
            };

            this.task = this.plugin.getServer().getScheduler().runTaskTimerAsynchronously(this.plugin, run, 0L, interval);
        }
    }

    /**
     * Stops the timers, effectively disabling updates to this metric cacher.
     * Safe to call multiple times. Thread-safe.
     */
    public void stop() {
        synchronized (this) {
            if (this.task != null) {
                this.task.cancel();
                this.task = null;
            }
        }
    }
}
