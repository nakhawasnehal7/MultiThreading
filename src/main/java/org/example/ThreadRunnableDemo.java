package org.example;

import java.util.concurrent.atomic.AtomicInteger;

public class ThreadRunnableDemo {

    // A shared resource used to demonstrate synchronization
    static class Counter {
        private int count = 0;
        private final Object lock = new Object(); // dedicated lock object (better than locking on 'this')

        // synchronized METHOD -> lock is on 'this' (the Counter instance)
        public synchronized void incrementMethodLevel() {
            count++;
        }

        // synchronized BLOCK -> lock is explicitly on our private lock object
        public void incrementBlockLevel() {
            synchronized (lock) {
                count++;
            }
        }

        public int getCount() {
            synchronized (lock) {
                return count;
            }
        }
    }

    // ---------------------------------------------------------
    // Way 1: Creating a thread by extending Thread
    // ---------------------------------------------------------
    static class MyThread extends Thread {
        public MyThread(String name) {
            super(name); // sets thread name via Thread's constructor
        }

        @Override
        public void run() {
            System.out.println(getName() + " running via extends Thread. Priority: " + getPriority());
        }
    }

    // ---------------------------------------------------------
    // Way 2: Creating a thread by implementing Runnable (preferred)
    // ---------------------------------------------------------
    static class MyRunnable implements Runnable {
        @Override
        public void run() {
            System.out.println(Thread.currentThread().getName() + " running via implements Runnable");
        }
    }

    public static void main(String[] args) throws InterruptedException {

        System.out.println("=== 1. Thread creation: extends Thread vs implements Runnable ===");
        Thread t1 = new MyThread("ExtendsThreadWorker");
        t1.start(); // NOTE: always call start(), never call run() directly (that just runs it on main thread, no new thread)

        Thread t2 = new Thread(new MyRunnable(), "RunnableWorker");
        t2.start();

        // Java 8+ lambda shortcut since Runnable is a functional interface
        Thread t3 = new Thread(() -> System.out.println(Thread.currentThread().getName() + " running via lambda Runnable"), "LambdaWorker");
        t3.start();

        t1.join(); // main thread waits here until t1 finishes
        t2.join();
        t3.join();

        System.out.println("\n=== 2. Thread state / info methods ===");
        Thread t4 = new Thread(() -> {
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
        }, "StateDemoThread");
        System.out.println("State before start(): " + t4.getState());   // NEW
        t4.start();
        System.out.println("State just after start(): " + t4.getState()); // RUNNABLE (or TIMED_WAITING once sleeping)
        Thread.sleep(50);
        System.out.println("State while sleeping: " + t4.getState());   // TIMED_WAITING
        t4.join();
        System.out.println("State after finishing: " + t4.getState());  // TERMINATED
        System.out.println("isAlive(): " + t4.isAlive());
/*
        System.out.println("Thread ID: " + t4.threadId());
*/
        System.out.println("Is daemon?: " + t4.isDaemon());

        System.out.println("\n=== 3. Thread priority ===");
        Thread low = new Thread(() -> System.out.println("Low priority thread"));
        low.setPriority(Thread.MIN_PRIORITY); // 1
        Thread high = new Thread(() -> System.out.println("High priority thread"));
        high.setPriority(Thread.MAX_PRIORITY); // 10
        System.out.println("Default priority is: " + Thread.NORM_PRIORITY); // 5
        // NOTE: priority is only a HINT to the OS scheduler; not guaranteed behavior across platforms
        low.start();
        high.start();
        low.join();
        high.join();

        System.out.println("\n=== 4. Daemon threads ===");
        Thread daemonThread = new Thread(() -> {
            while (true) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
            }
        });
        daemonThread.setDaemon(true); // MUST be set before start()
        daemonThread.start();
        System.out.println("Daemon thread started. JVM will NOT wait for it to exit.");
        // We won't join() this one on purpose — daemon threads die automatically when main() ends.

        System.out.println("\n=== 5. sleep() ===");
        long startTime = System.currentTimeMillis();
        Thread.sleep(200); // pauses the CURRENT thread; does not release any lock it holds
        System.out.println("Slept for approx: " + (System.currentTimeMillis() - startTime) + "ms");

        System.out.println("\n=== 6. interrupt() / isInterrupted() / interrupted() ===");
        Thread interruptible = new Thread(() -> {
            try {
                System.out.println("Worker going to sleep for 5s...");
                Thread.sleep(5000);
                System.out.println("Worker woke up normally (should NOT print)");
            } catch (InterruptedException e) {
                System.out.println("Worker was interrupted during sleep! Exiting gracefully.");
            }
        });
        interruptible.start();
        Thread.sleep(200); // let it get into sleep() first
        interruptible.interrupt(); // sets interrupt flag; since thread is sleeping, throws InterruptedException immediately
        interruptible.join();

        System.out.println("\n=== 7. synchronized method vs synchronized block ===");
        Counter counter = new Counter();
        Runnable incrementTask = () -> {
            for (int i = 0; i < 1000; i++) {
                counter.incrementMethodLevel();
                counter.incrementBlockLevel();
            }
        };
        Thread s1 = new Thread(incrementTask);
        Thread s2 = new Thread(incrementTask);
        s1.start();
        s2.start();
        s1.join();
        s2.join();
        // Expected: 2 threads * 1000 increments each * 2 methods = 4000
        System.out.println("Final counter value (should be 4000 if thread-safe): " + counter.getCount());

        System.out.println("\n=== 8. wait() / notify() / notifyAll() -- producer-consumer style ===");
        final Object monitor = new Object();
        final boolean[] dataReady = {false};

        Thread consumer = new Thread(() -> {
            synchronized (monitor) {
                while (!dataReady[0]) { // always check condition in a loop, not just 'if' (guards against spurious wakeups)
                    try {
                        System.out.println("Consumer: waiting for data...");
                        monitor.wait(); // releases the lock on 'monitor' while waiting
                    } catch (InterruptedException ignored) {}
                }
                System.out.println("Consumer: data received, processing.");
            }
        }, "Consumer");

        Thread producer = new Thread(() -> {
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            synchronized (monitor) {
                dataReady[0] = true;
                System.out.println("Producer: data is ready, notifying consumer.");
                monitor.notify(); // wakes ONE waiting thread on 'monitor' (use notifyAll() if multiple waiters)
            }
        }, "Producer");

        consumer.start();
        producer.start();
        consumer.join();
        producer.join();

        System.out.println("\n=== 9. volatile flag to stop a thread safely ===");
        class FlagWorker implements Runnable {
            private volatile boolean running = true; // volatile ensures visibility of the flag change across threads

            public void stopRunning() { running = false; }

            @Override
            public void run() {
                int loops = 0;
                while (running) {
                    loops++;
                    if (loops % 50_000_000 == 0) {
                        // just spinning to simulate work
                    }
                }
                System.out.println("FlagWorker stopped gracefully.");
            }
        }
        FlagWorker flagWorker = new FlagWorker();
        Thread flagThread = new Thread(flagWorker);
        flagThread.start();
        Thread.sleep(100);
        flagWorker.stopRunning(); // without 'volatile', this update might never be seen by flagThread's cached copy
        flagThread.join();

        System.out.println("\n=== 10. AtomicInteger as a lock-free alternative to synchronized counters ===");
        AtomicInteger atomicCounter = new AtomicInteger(0);
        Runnable atomicTask = () -> {
            for (int i = 0; i < 1000; i++) {
                atomicCounter.incrementAndGet(); // CAS-based atomic increment, no locks needed
            }
        };
        Thread a1 = new Thread(atomicTask);
        Thread a2 = new Thread(atomicTask);
        a1.start();
        a2.start();
        a1.join();
        a2.join();
        System.out.println("AtomicInteger final value (should be 2000): " + atomicCounter.get());

        System.out.println("\n=== 11. Thread.currentThread() and setName()/getName() ===");
        Thread named = new Thread(() -> {
            Thread self = Thread.currentThread();
            System.out.println("Running inside: " + self.getName());
        });
        named.setName("CustomNamedThread");
        named.start();
        named.join();

        System.out.println("\nAll demos complete. Main thread name: " + Thread.currentThread().getName());
    }
}
