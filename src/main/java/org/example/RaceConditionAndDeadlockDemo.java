package org.example;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;

public class RaceConditionAndDeadlockDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("############ PART 1: RACE CONDITION ############\n");
        raceConditionBroken();
        raceConditionFixedWithSynchronized();
        raceConditionFixedWithAtomic();

        System.out.println("\n############ PART 2: DEADLOCK ############\n");
        // WARNING: deadlockDemo() will hang forever by design — commented out by default.
        // Uncomment to actually witness a real deadlock (you'll need to kill the program manually).
        // deadlockDemo();

        System.out.println("(deadlockDemo() call is commented out above — it hangs forever by design. " +
                "Uncomment it locally to observe a real deadlock.)");

        deadlockFixedWithLockOrdering();
        deadlockFixedWithTryLock();
    }

    // ==========================================================
    // PART 1: RACE CONDITION
    // ==========================================================

    /**
     * BROKEN: count++ is NOT atomic (read -> modify -> write).
     * Two threads incrementing 100,000 times each SHOULD give 200,000,
     * but due to interleaving, updates get lost.
     */
    static void raceConditionBroken() throws InterruptedException {
        class UnsafeCounter {
            int count = 0;
            void increment() { count++; } // NOT thread-safe
        }

        UnsafeCounter counter = new UnsafeCounter();
        Runnable task = () -> {
            for (int i = 0; i < 100_000; i++) {
                counter.increment();
            }
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("[Broken] Expected: 200000, Actual: " + counter.count +
                (counter.count != 200_000 ? "  <-- RACE CONDITION: lost updates!" : "  (got lucky this run)"));
    }

    /**
     * FIX #1: synchronized makes the read-modify-write sequence atomic
     * by ensuring only one thread can execute increment() at a time.
     */
    static void raceConditionFixedWithSynchronized() throws InterruptedException {
        class SafeCounter {
            private int count = 0;
            synchronized void increment() { count++; } // atomic now
            synchronized int get() { return count; }
        }

        SafeCounter counter = new SafeCounter();
        Runnable task = () -> {
            for (int i = 0; i < 100_000; i++) {
                counter.increment();
            }
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("[Fixed - synchronized] Expected: 200000, Actual: " + counter.get());
    }

    /**
     * FIX #2: AtomicInteger uses CAS (compare-and-swap) at the hardware level
     * to make increment atomic WITHOUT using locks at all.
     */
    static void raceConditionFixedWithAtomic() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        Runnable task = () -> {
            for (int i = 0; i < 100_000; i++) {
                counter.incrementAndGet();
            }
        };

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        System.out.println("[Fixed - AtomicInteger] Expected: 200000, Actual: " + counter.get());
    }

    // ==========================================================
    // PART 2: DEADLOCK
    // ==========================================================

    /**
     * BROKEN: classic deadlock.
     * Thread 1 locks lockA then tries to lock lockB.
     * Thread 2 locks lockB then tries to lock lockA.
     * If both grab their first lock at the same time, each waits forever for the other -> DEADLOCK.
     */
    static void deadlockDemo() throws InterruptedException {
        final Object lockA = new Object();
        final Object lockB = new Object();

        Thread thread1 = new Thread(() -> {
            synchronized (lockA) {
                System.out.println("Thread-1: locked lockA, trying to lock lockB...");
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                synchronized (lockB) {
                    System.out.println("Thread-1: locked lockB too (never reached in deadlock)");
                }
            }
        }, "Thread-1");

        Thread thread2 = new Thread(() -> {
            synchronized (lockB) {
                System.out.println("Thread-2: locked lockB, trying to lock lockA...");
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                synchronized (lockA) {
                    System.out.println("Thread-2: locked lockA too (never reached in deadlock)");
                }
            }
        }, "Thread-2");

        thread1.start();
        thread2.start();
        thread1.join(); // will hang forever
        thread2.join();
    }

    /**
     * FIX #1: Lock ordering.
     * Both threads always acquire lockA BEFORE lockB, regardless of which
     * "resource" they logically want first. This breaks the "circular wait"
     * condition required for deadlock, since a cycle can no longer form.
     */
    static void deadlockFixedWithLockOrdering() throws InterruptedException {
        final Object lockA = new Object();
        final Object lockB = new Object();

        Runnable task1 = () -> {
            synchronized (lockA) { // always lockA first
                System.out.println("Thread-1: locked lockA");
                synchronized (lockB) { // then lockB
                    System.out.println("Thread-1: locked lockB. Work done, no deadlock.");
                }
            }
        };

        Runnable task2 = () -> {
            synchronized (lockA) { // ALSO locks lockA first, even though logically it wants B "first"
                System.out.println("Thread-2: locked lockA");
                synchronized (lockB) {
                    System.out.println("Thread-2: locked lockB. Work done, no deadlock.");
                }
            }
        };

        Thread t1 = new Thread(task1, "Thread-1");
        Thread t2 = new Thread(task2, "Thread-2");
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        System.out.println("[Fixed - lock ordering] Completed without deadlock.\n");
    }

    /**
     * FIX #2: tryLock() with a timeout.
     * If a thread can't acquire the second lock within the timeout, it backs off,
     * releases what it's holding, and can retry later -- avoiding indefinite waiting.
     */
    static void deadlockFixedWithTryLock() throws InterruptedException {
        Lock lockA = new ReentrantLock();
        Lock lockB = new ReentrantLock();

        Runnable task1 = () -> attemptWithTryLock("Thread-1", lockA, lockB);
        Runnable task2 = () -> attemptWithTryLock("Thread-2", lockB, lockA); // intentionally reversed order

        Thread t1 = new Thread(task1, "Thread-1");
        Thread t2 = new Thread(task2, "Thread-2");
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        System.out.println("[Fixed - tryLock with timeout] Completed without permanent deadlock.");
    }

    private static void attemptWithTryLock(String name, Lock firstLock, Lock secondLock) {
        int attempts = 0;
        while (attempts < 5) {
            attempts++;
            boolean gotFirst = false;
            boolean gotSecond = false;
            try {
                gotFirst = firstLock.tryLock(50, TimeUnit.MILLISECONDS);
                if (gotFirst) {
                    // small pause to increase chance of contention for demo purposes
                    Thread.sleep(20);
                    gotSecond = secondLock.tryLock(50, TimeUnit.MILLISECONDS);
                    if (gotSecond) {
                        System.out.println(name + ": acquired both locks on attempt " + attempts + ", doing work.");
                        return; // success, done
                    } else {
                        System.out.println(name + ": couldn't get second lock, backing off (attempt " + attempts + ")");
                    }
                } else {
                    System.out.println(name + ": couldn't get first lock, backing off (attempt " + attempts + ")");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                if (gotSecond) secondLock.unlock();
                if (gotFirst) firstLock.unlock();
            }
            // brief random backoff before retrying, to reduce chance of repeated collision
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        }
        System.out.println(name + ": gave up after " + attempts + " attempts.");
    }
}
