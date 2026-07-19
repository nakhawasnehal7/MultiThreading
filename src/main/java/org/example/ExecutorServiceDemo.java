package org.example;

import java.util.*;
import java.util.concurrent.*;

public class ExecutorServiceDemo {

    public static void main(String[] args) throws Exception {

        // ---------------------------------------------------------
        // 1. Creating different types of thread pools via Executors
        // ---------------------------------------------------------
        ExecutorService fixedPool = Executors.newFixedThreadPool(4);
        ExecutorService cachedPool = Executors.newCachedThreadPool();
        ExecutorService singlePool = Executors.newSingleThreadExecutor();
        ScheduledExecutorService scheduledPool = Executors.newScheduledThreadPool(2);
        // Java 8+ : ForkJoin-based work-stealing pool
        ExecutorService workStealingPool = Executors.newWorkStealingPool();

        System.out.println("=== execute() : fire-and-forget, no result returned ===");
        fixedPool.execute(() -> System.out.println("Task running on: " + Thread.currentThread().getName()));

        System.out.println("\n=== submit(Runnable) : returns Future<?> ===");
        Future<?> f1 = fixedPool.submit(() -> System.out.println("Runnable submitted, no return value"));
        f1.get(); // wait for completion; result is null since Runnable has no return value

        System.out.println("\n=== submit(Callable<T>) : returns Future<T> with a result ===");
        Future<Integer> f2 = fixedPool.submit(() -> {
            Thread.sleep(200);
            return 42;
        });
        System.out.println("Result from Callable: " + f2.get()); // blocks until result is ready

        System.out.println("\n=== submit(Runnable, T result) : returns a fixed result ===");
        Future<String> f3 = fixedPool.submit(() -> System.out.println("Runnable with predefined result"), "DONE");
        System.out.println("Predefined result: " + f3.get());

        System.out.println("\n=== isDone() / isCancelled() / cancel() ===");
        Future<Integer> f4 = fixedPool.submit(() -> {
            Thread.sleep(2000);
            return 100;
        });
        System.out.println("isDone right after submit: " + f4.isDone());
        boolean cancelled = f4.cancel(true); // true = interrupt if running
        System.out.println("cancel() returned: " + cancelled + ", isCancelled(): " + f4.isCancelled());

        System.out.println("\n=== invokeAll() : run multiple Callables, block until ALL complete ===");
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            int taskId = i;
            tasks.add(() -> {
                Thread.sleep(100);
                return taskId * taskId;
            });
        }
        List<Future<Integer>> results = fixedPool.invokeAll(tasks);
        for (Future<Integer> f : results) {
            System.out.println("invokeAll result: " + f.get());
        }

        System.out.println("\n=== invokeAny() : run multiple Callables, return FIRST completed result ===");
        List<Callable<String>> anyTasks = List.of(
                () -> { Thread.sleep(300); return "Slow task"; },
                () -> { Thread.sleep(50);  return "Fast task"; }
        );
        String firstResult = fixedPool.invokeAny(anyTasks);
        System.out.println("invokeAny winner: " + firstResult);

        System.out.println("\n=== ScheduledExecutorService: schedule(), scheduleAtFixedRate(), scheduleWithFixedDelay() ===");
        ScheduledFuture<?> delayed = scheduledPool.schedule(
                () -> System.out.println("Ran after 500ms delay"), 500, TimeUnit.MILLISECONDS);
        delayed.get(); // wait for it

        ScheduledFuture<?> periodic = scheduledPool.scheduleAtFixedRate(
                () -> System.out.println("Periodic tick at " + System.currentTimeMillis()),
                0, 300, TimeUnit.MILLISECONDS);
        Thread.sleep(1000);
        periodic.cancel(true); // stop the periodic task

        System.out.println("\n=== ThreadPoolExecutor (manual construction, more control) ===");
        ThreadPoolExecutor customExecutor = new ThreadPoolExecutor(
                2,                          // core pool size
                4,                          // max pool size
                60L, TimeUnit.SECONDS,      // idle thread timeout
                new LinkedBlockingQueue<>(10), // work queue with bounded capacity
                new ThreadPoolExecutor.CallerRunsPolicy() // rejection policy
        );
        customExecutor.execute(() -> System.out.println("Task on custom ThreadPoolExecutor"));
        System.out.println("Active count: " + customExecutor.getActiveCount());
        System.out.println("Pool size: " + customExecutor.getPoolSize());
        System.out.println("Task count: " + customExecutor.getTaskCount());
        System.out.println("Completed task count (may lag slightly): " + customExecutor.getCompletedTaskCount());

        // ---------------------------------------------------------
        // Shutdown APIs — IMPORTANT: pools must be shut down or the JVM won't exit
        // ---------------------------------------------------------
        System.out.println("\n=== Shutdown lifecycle: shutdown(), shutdownNow(), awaitTermination(), isShutdown(), isTerminated() ===");

        fixedPool.shutdown(); // graceful: no new tasks accepted, existing tasks finish
        boolean terminatedInTime = fixedPool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("fixedPool terminated within timeout: " + terminatedInTime);
        System.out.println("isShutdown: " + fixedPool.isShutdown() + ", isTerminated: " + fixedPool.isTerminated());

        List<Runnable> neverStarted = customExecutor.shutdownNow(); // forceful: attempts to stop running tasks, returns queued-but-unstarted tasks
        System.out.println("Tasks that never started (from shutdownNow): " + neverStarted.size());

        cachedPool.shutdown();
        singlePool.shutdown();
        scheduledPool.shutdown();
        workStealingPool.shutdown();

        System.out.println("\nDone.");
    }
}
