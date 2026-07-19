package org.example;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Demonstrates how ReentrantLock.tryLock(timeout) can be used to AVOID deadlock,
 * even when two threads try to acquire two shared locks in OPPOSITE order
 * (the exact setup that would deadlock forever with plain 'synchronized').
 */
public class DeadlockFixWithReentrantLock {

    // Two shared resources, each protected by its own ReentrantLock
    private static final Lock lockA = new ReentrantLock();
    private static final Lock lockB = new ReentrantLock();

    public static void main(String[] args) throws InterruptedException {

        // Thread-1 tries to acquire lockA first, then lockB
        Thread thread1 = new Thread(() -> transferStyleTask("Thread-1", lockA, lockB), "Thread-1");

        // Thread-2 tries to acquire lockB first, then lockA (REVERSED order --
        // this is exactly the pattern that causes classic deadlock with synchronized)
        Thread thread2 = new Thread(() -> transferStyleTask("Thread-2", lockB, lockA), "Thread-2");

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        System.out.println("\nMain: both threads finished. No deadlock occurred.");
    }

    /**
     * Attempts to acquire 'firstLock' then 'secondLock' using tryLock() with a timeout.
     * If the second lock can't be obtained in time, it releases the first lock
     * and retries after a short backoff -- instead of blocking forever like
     * a plain 'synchronized' block would.
     */
    private static void transferStyleTask(String threadName, Lock firstLock, Lock secondLock) {
        int maxAttempts = 10;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean gotFirstLock = false;
            boolean gotSecondLock = false;

            try {
                // Try to acquire the first lock, waiting at most 100ms
                gotFirstLock = firstLock.tryLock(100, TimeUnit.MILLISECONDS);

                if (gotFirstLock) {
                    System.out.println(threadName + ": acquired FIRST lock (attempt " + attempt + ")");

                    // Small artificial delay to increase the chance both threads
                    // are mid-way holding their first lock at the same time --
                    // this is the exact moment a plain 'synchronized' version would deadlock.
                    Thread.sleep(50);

                    // Try to acquire the second lock, also waiting at most 100ms
                    gotSecondLock = secondLock.tryLock(100, TimeUnit.MILLISECONDS);

                    if (gotSecondLock) {
                        System.out.println(threadName + ": acquired SECOND lock (attempt " + attempt +
                                ") -- doing critical work now.");
                        Thread.sleep(50); // simulate doing actual work while holding both locks
                        return; // success -- exit the method, locks released in 'finally' below
                    } else {
                        System.out.println(threadName + ": FAILED to get second lock on attempt " + attempt +
                                " -- releasing first lock and backing off.");
                    }
                } else {
                    System.out.println(threadName + ": FAILED to get first lock on attempt " + attempt +
                            " -- backing off.");
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println(threadName + ": interrupted, aborting.");
                return;
            } finally {
                // CRITICAL: always release whatever locks we actually acquired,
                // in reverse order of acquisition, so we never hold a lock while retrying.
                if (gotSecondLock) {
                    secondLock.unlock();
                }
                if (gotFirstLock) {
                    firstLock.unlock();
                }
            }

            // Backoff before retrying -- helps avoid the two threads repeatedly
            // colliding in lockstep on every retry (a form of "livelock").
            try {
                Thread.sleep(20 + (long) (Math.random() * 30));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        System.out.println(threadName + ": gave up after " + maxAttempts + " attempts.");
    }
}
