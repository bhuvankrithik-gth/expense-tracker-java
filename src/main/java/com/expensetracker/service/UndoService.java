package com.expensetracker.service;

import com.expensetracker.dao.TransactionDao;
import com.expensetracker.model.Expense;
import com.expensetracker.model.Income;
import com.expensetracker.model.Transaction;
import com.expensetracker.util.Result;
import com.expensetracker.util.UndoList;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Undo last delete": every delete pushes the removed transaction onto a
 * per-user {@link UndoList} (Week 9 hand-written doubly linked list kept in
 * a HashMap keyed by user id — Week 7). Undo pops it and re-inserts it.
 */
public class UndoService {

    private final Map<Long, UndoList<Transaction>> histories = new ConcurrentHashMap<>();
    private final TransactionDao txnDao = new TransactionDao();

    /** Delete a transaction and remember it for undo. */
    public Result<Transaction> delete(long userId, long txnId) {
        try {
            Optional<Transaction> deleted = txnDao.delete(userId, txnId);
            if (deleted.isEmpty()) {
                return Result.fail("Transaction not found.");
            }
            histories.computeIfAbsent(userId, k -> new UndoList<>()).insert(deleted.get());
            ActivityLogService.get().log(userId, "TXN_DELETE",
                    "Deleted " + deleted.get().describe());
            return Result.ok(deleted.get());
        } catch (SQLException e) {
            return Result.fail("Database error while deleting.");
        }
    }

    /** True when there is something to undo (drives the button state). */
    public boolean canUndo(long userId) {
        UndoList<Transaction> history = histories.get(userId);
        return history != null && !history.isEmpty();
    }

    /** Peek at what undo would restore (for the button label). */
    public Optional<Transaction> peek(long userId) {
        UndoList<Transaction> history = histories.get(userId);
        return Optional.ofNullable(history == null ? null : history.peek());
    }

    /** Pop the last deleted transaction and re-insert it (gets a new id). */
    public Result<Transaction> undo(long userId) {
        UndoList<Transaction> history = histories.get(userId);
        if (history == null || history.isEmpty()) {
            return Result.fail("Nothing to undo.");
        }
        Transaction t = history.delete(); // pop from the doubly linked list
        try {
            Transaction fresh = "income".equals(t.getType())
                    ? new Income(userId, t.getCategoryId(), t.getAmount(),
                            t.getCurrency(), t.getDate(), t.getNote(), t.getPaymentMethod())
                    : new Expense(userId, t.getCategoryId(), t.getAmount(),
                            t.getCurrency(), t.getDate(), t.getNote(), t.getPaymentMethod());
            txnDao.create(fresh);
            ActivityLogService.get().log(userId, "TXN_UNDO",
                    "Restored " + fresh.describe());
            return Result.ok(fresh);
        } catch (SQLException e) {
            // Put it back on the history so the undo isn't lost on DB failure.
            history.insert(t);
            return Result.fail("Database error while restoring.");
        }
    }
}
