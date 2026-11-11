package com.sur.player.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * WEEK 4 TOPIC: the Java Concurrency package, executors and thread pools.
 *
 * Android has one special thread, the "main" or "UI" thread. Only it is
 * allowed to touch Views, and if you block it for more than a few hundred
 * milliseconds the app freezes (and Android may kill it with an ANR).
 *
 * So: database reads, network calls and the MediaStore scan all run on one
 * of the pools below, and the result is posted back with MAIN.post(...).
 *
 * Why a POOL instead of  new Thread(...)  every time: threads are expensive
 * to create, and an unbounded number of them will eventually exhaust memory.
 * A pool creates a few threads once and reuses them.
 */
public final class AppExecutors {

    /** Small factory so every thread we make has a readable name in the profiler. */
    private static ThreadFactory named(final String name) {
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread t = new Thread(runnable, name);
                // Daemon: the JVM will not wait for this thread to finish
                // before the process exits.
                t.setDaemon(true);
                return t;
            }
        };
    }

    /** Database and file work. Two threads is plenty for SQLite. */
    private static final ExecutorService DISK =
            Executors.newFixedThreadPool(2, named("sur-disk"));

    /** Network calls. Three at a time so a slow feed does not block a search. */
    private static final ExecutorService NETWORK =
            Executors.newFixedThreadPool(3, named("sur-network"));

    /** Used by the sleep timer countdown. */
    private static final ScheduledExecutorService SCHEDULED =
            Executors.newSingleThreadScheduledExecutor(named("sur-scheduled"));

    /** A Handler bound to the main thread: how a background thread talks to the UI. */
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private AppExecutors() {
        // Utility class: never instantiated.
    }

    public static void disk(Runnable task) {
        DISK.execute(task);
    }

    public static void network(Runnable task) {
        NETWORK.execute(task);
    }

    public static void main(Runnable task) {
        MAIN.post(task);
    }

    public static ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        return SCHEDULED.schedule(task, delay, unit);
    }

    /**
     * The usual pattern in one call: do the slow part on a pool thread, then
     * hand the answer back to the UI thread.
     *
     * Example:
     *   AppExecutors.diskThenMain(() -> dao.getAll(), songs -> adapter.submit(songs));
     */
    public static <T> void diskThenMain(final Supplier<T> work, final Consumer<T> onResult) {
        DISK.execute(new Runnable() {
            @Override
            public void run() {
                final T result = work.get();
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        onResult.accept(result);
                    }
                });
            }
        });
    }

    public static <T> void networkThenMain(final Supplier<T> work, final Consumer<T> onResult) {
        NETWORK.execute(new Runnable() {
            @Override
            public void run() {
                final T result = work.get();
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        onResult.accept(result);
                    }
                });
            }
        });
    }

    /** Tiny stand-ins so we do not need Java 8 desugaring of java.util.function. */
    public interface Supplier<T> {
        T get();
    }

    public interface Consumer<T> {
        void accept(T value);
    }
}
