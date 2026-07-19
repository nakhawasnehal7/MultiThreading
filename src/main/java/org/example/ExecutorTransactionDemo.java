package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Demonstrates running DATABASE TRANSACTIONS concurrently using ExecutorService.
 *
 * KEY RULE: java.sql.Connection is NOT thread-safe.
 * Each task/thread must obtain its OWN Connection (ideally from a connection pool
 * like HikariCP), run its own transaction on it, and close it when done.
 * NEVER share a single Connection object across multiple threads.
 */
public class ExecutorTransactionDemo {

    // In real projects, replace this with a pooled DataSource (e.g. HikariCP),
    // NOT DriverManager directly -- DriverManager creates a brand-new physical
    // connection every time, which is slow and doesn't scale under concurrency.
    private static final String DB_URL = "jdbc:mysql://localhost:3306/mydb";
    private static final String DB_USER = "your_user";
    private static final String DB_PASSWORD = "your_password";

    public static void main(String[] args) throws InterruptedException {

        ExecutorService executor = Executors.newFixedThreadPool(4);

        // Simulate 5 independent "transfer money" transactions running concurrently
        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            int accountId = i;
            double amount = 100.0 * i;

            Future<?> future = executor.submit(() -> {
                try {
                    performTransaction(accountId, amount);
                } catch (SQLException e) {
                    System.err.println("Transaction failed for account " + accountId + ": " + e.getMessage());
                }
            });
            futures.add(future);
        }

        // Wait for all submitted transactions to finish before shutting down
        for (Future<?> f : futures) {
            try {
                f.get(); // blocks until this task completes; rethrows any exception it threw
            } catch (Exception e) {
                System.err.println("Task failed: " + e.getMessage());
            }
        }

        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        System.out.println("All transactions submitted and completed. Executor shut down.");
    }

    /**
     * Runs a single database transaction on a DEDICATED connection for this thread.
     * Each call to this method (from a different pooled thread) gets its own
     * Connection instance -- that's what makes this safe to run concurrently.
     */
    private static void performTransaction(int accountId, double amount) throws SQLException {
        // Each thread opens its OWN connection here -- never reuse one across threads.
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {

            conn.setAutoCommit(false); // start a transaction explicitly

            try {
                // Example: deduct from one account, credit to another, as ONE atomic unit
                try (PreparedStatement debit = conn.prepareStatement(
                        "UPDATE accounts SET balance = balance - ? WHERE id = ?")) {
                    debit.setDouble(1, amount);
                    debit.setInt(2, accountId);
                    debit.executeUpdate();
                }

                try (PreparedStatement credit = conn.prepareStatement(
                        "UPDATE accounts SET balance = balance + ? WHERE id = ?")) {
                    credit.setDouble(1, amount);
                    credit.setInt(2, accountId + 1000); // arbitrary "destination" account for demo
                    credit.executeUpdate();
                }

                conn.commit(); // both updates succeed together, atomically
                System.out.println(Thread.currentThread().getName() +
                        ": committed transaction for account " + accountId + " (amount=" + amount + ")");

            } catch (SQLException e) {
                conn.rollback(); // if ANYTHING failed above, undo both updates
                System.out.println(Thread.currentThread().getName() +
                        ": rolled back transaction for account " + accountId + " due to: " + e.getMessage());
                throw e; // propagate so the caller (Future.get()) knows it failed
            } finally {
                conn.setAutoCommit(true); // restore default state before connection is returned/closed
            }
        }
        // try-with-resources automatically closes the connection here,
        // returning it to the pool if using a pooled DataSource.
    }
}
