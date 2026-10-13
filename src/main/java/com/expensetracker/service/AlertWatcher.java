package com.expensetracker.service;

import com.expensetracker.dao.AlertDao;
import com.expensetracker.dao.BudgetDao;
import com.expensetracker.dao.UserDao;
import com.expensetracker.model.Alert;
import com.expensetracker.model.Budget;
import com.expensetracker.model.User;

import java.time.Duration;
import java.util.List;

/**
 * Week 5 — BACKGROUND THREAD. Every 10 minutes it scans every user's
 * current-month budgets and creates an alert row when a budget is
 * exceeded (deduped: no repeat alert while one is still unread).
 * Alerts are shown as a banner on the dashboard.
 */
public class AlertWatcher {

    private final BudgetDao budgetDao = new BudgetDao();
    private final AlertDao alertDao = new AlertDao();
    private volatile boolean started = false;

    /** Start the daemon watcher thread (called once at app boot). */
    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        Thread watcher = new Thread(this::watchLoop, "budget-watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    private void watchLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                checkAll();
                Thread.sleep(Duration.ofMinutes(10).toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.out.println("[budget-watcher] check failed: " + e.getMessage());
            }
        }
    }

    /** One full scan — also callable directly (used by tests / manual trigger). */
    public void checkAll() throws Exception {
        String month = BudgetService.currentMonth();
        List<Long> userIds = budgetDao.usersWithBudgets();
        UserDao userDao = new UserDao();
        for (long userId : userIds) {
            List<Budget> budgets = budgetDao.listByMonth(userId, month);
            // Alert text reads best in the user's own display currency.
            String code = userDao.findById(userId).map(User::getCurrency).orElse("INR");
            for (Budget b : budgets) {
                if (b.isExceeded()) {
                    String prefix = "Budget exceeded: " + b.getCategoryName();
                    if (!alertDao.hasUnreadLike(userId, prefix)) {
                        String msg = prefix + " — spent "
                                + ConverterFactory.format(
                                        ConverterFactory.convert(b.getSpent(), "INR", code), code)
                                + " of "
                                + ConverterFactory.format(
                                        ConverterFactory.convert(b.getAmount(), "INR", code), code)
                                + " for " + month;
                        alertDao.create(new Alert(userId, msg));
                        ActivityLogService.get().log(userId, "BUDGET_ALERT", msg);
                    }
                }
            }
        }
    }
}
