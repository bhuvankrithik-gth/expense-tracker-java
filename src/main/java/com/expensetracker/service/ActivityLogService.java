package com.expensetracker.service;

import com.expensetracker.dao.ActivityLogDao;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Week 5 — PRODUCER-CONSUMER with a BlockingQueue.
 * Request threads (producers) just enqueue tiny log entries and return
 * immediately; ONE background consumer thread drains the queue every few
 * seconds and batch-writes them to the activity_log table. Logging can
 * never slow down a web request, and the batch insert is efficient.
 */
public class ActivityLogService {

    private static final ActivityLogService INSTANCE = new ActivityLogService();

    public static ActivityLogService get() {
        return INSTANCE;
    }

    private final BlockingQueue<ActivityLogDao.Entry> queue = new LinkedBlockingQueue<>(10_000);
    private final ActivityLogDao dao = new ActivityLogDao();
    private volatile boolean started = false;

    private ActivityLogService() {
    }

    /** Producer: called from request handlers. Never blocks the request. */
    public void log(Long userId, String action, String detail) {
        // offer() drops the entry instead of blocking if the queue is full
        queue.offer(new ActivityLogDao.Entry(userId, action, detail));
    }

    /** Start the single consumer thread (called once at app boot). */
    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        Thread consumer = new Thread(this::consumeLoop, "activity-log-consumer");
        consumer.setDaemon(true);
        consumer.start();
    }

    /** Consumer: drain the queue in batches and write them to MySQL. */
    private void consumeLoop() {
        List<ActivityLogDao.Entry> batch = new ArrayList<>(200);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Wait up to 5s for the first entry, then grab whatever else is ready.
                ActivityLogDao.Entry first = queue.poll(5, TimeUnit.SECONDS);
                if (first != null) {
                    batch.add(first);
                    queue.drainTo(batch, 199);
                }
                if (!batch.isEmpty()) {
                    try {
                        dao.insertBatch(batch); // one round-trip per batch
                    } catch (Exception e) {
                        System.out.println("[activity-log] batch write failed: " + e.getMessage());
                    } finally {
                        batch.clear();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** For tests: how many entries are still waiting. */
    int pendingCount() {
        return queue.size();
    }
}
