package com.expensetracker.service;

import com.expensetracker.dao.BudgetDao;
import com.expensetracker.dao.CategoryDao;
import com.expensetracker.dao.Db;
import com.expensetracker.dao.TransactionDao;
import com.expensetracker.dao.UserDao;
import com.expensetracker.model.Budget;
import com.expensetracker.model.Category;
import com.expensetracker.model.Transaction;
import com.expensetracker.model.User;
import com.expensetracker.util.BackupFiles;
import com.expensetracker.util.Result;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Week 7 — Serialization: a user's whole dataset (profile, categories,
 * transactions, budgets) is written to a ".ser" file with ObjectOutputStream
 * and read back with ObjectInputStream. Restore REPLACES the user's current
 * data (inside one DB transaction) after verifying the backup belongs to them.
 */
public class BackupService {

    /** Everything about one user, in one serializable snapshot. */
    public static class UserBackup implements Serializable {
        private static final long serialVersionUID = 1L;

        public long userId;
        public String email;
        public LocalDateTime takenAt;
        public User user;
        public List<Category> categories;
        public List<Transaction> transactions;
        public List<Budget> budgets;
    }

    private final UserDao userDao = new UserDao();
    private final CategoryDao categoryDao = new CategoryDao();
    private final TransactionDao txnDao = new TransactionDao();
    private final BudgetDao budgetDao = new BudgetDao();

    private final File backupDir;

    public BackupService(File backupDir) {
        this.backupDir = backupDir;
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
    }

    /** Write a timestamped backup file; returns its file name. */
    public Result<String> backup(long userId) {
        try {
            Optional<User> user = userDao.findById(userId);
            if (user.isEmpty()) {
                return Result.fail("User not found.");
            }
            UserBackup snap = new UserBackup();
            snap.userId = userId;
            snap.email = user.get().getEmail();
            snap.takenAt = LocalDateTime.now();
            snap.user = user.get();
            snap.categories = categoryDao.listByUser(userId);
            snap.transactions = txnDao.listAll(userId);
            // Budgets across all months the user has (so nothing is lost).
            snap.budgets = new java.util.ArrayList<>();
            for (String month : budgetDao.monthsForUser(userId)) {
                snap.budgets.addAll(budgetDao.listByMonth(userId, month));
            }

            String fileName = "user_" + userId + "_"
                    + snap.takenAt.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".ser";
            File file = new File(backupDir, fileName);
            try (ObjectOutputStream out =
                         new ObjectOutputStream(new FileOutputStream(file))) {
                out.writeObject(snap); // Week 7: serialization
            }
            return Result.ok(fileName);
        } catch (Exception e) {
            return Result.fail("Backup failed: " + e.getMessage());
        }
    }

    /** Backups for this user, newest first (Week 6 recursive listing). */
    public List<BackupFiles.BackupInfo> list(long userId) {
        return BackupFiles.listForUser(backupDir, userId);
    }

    /**
     * Restore a backup file. The file must belong to this user, otherwise
     * it is refused (per-user isolation). Current data is replaced.
     */
    public Result<Integer> restore(long userId, String fileName) {
        try {
            // Path safety: only a bare file name inside our backup dir.
            File file = new File(backupDir, new File(fileName).getName());
            if (!file.isFile() || !file.getName().startsWith("user_" + userId + "_")) {
                return Result.fail("Backup file not found.");
            }
            final UserBackup snap;
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file))) {
                Object obj = in.readObject(); // Week 7: deserialization
                if (!(obj instanceof UserBackup)) {
                    return Result.fail("That file is not a valid backup.");
                }
                snap = (UserBackup) obj;
            }
            if (snap.userId != userId) {
                return Result.fail("That backup belongs to a different account.");
            }

            int restoredTxns = restoreIntoDb(userId, snap);
            return Result.ok(restoredTxns);
        } catch (Exception e) {
            return Result.fail("Restore failed: " + e.getMessage());
        }
    }

    /**
     * Replace the user's data with the snapshot's. Runs in ONE database
     * transaction via {@link Db#inTransaction}: either everything is restored
     * or the rollback leaves the old data untouched.
     */
    private int restoreIntoDb(long userId, UserBackup snap) throws SQLException {
        final int[] count = {0};
        Db.inTransaction(conn -> {
            // Wipe current data inside the transaction.
            for (Transaction t : txnDao.listAll(conn, userId)) {
                txnDao.delete(conn, userId, t.getId());
            }
            for (String month : budgetDao.monthsForUser(conn, userId)) {
                for (Budget b : budgetDao.listByMonth(conn, userId, month)) {
                    budgetDao.delete(conn, userId, b.getId());
                }
            }
            for (Category c : categoryDao.listByUser(conn, userId)) {
                if (!c.isDefault()) {
                    categoryDao.delete(conn, userId, c.getId());
                }
            }
            // Re-create categories, mapping old ids -> new ids.
            java.util.Map<Long, Long> catIdMap = new java.util.HashMap<>();
            for (Category c : snap.categories) {
                if (categoryDao.findByName(conn, userId, c.getName()).isEmpty()) {
                    Category fresh = new Category(userId, c.getName(), c.getKind(), false);
                    categoryDao.create(conn, fresh);
                }
                long newId = categoryDao.findByName(conn, userId, c.getName()).get().getId();
                catIdMap.put(c.getId(), newId);
            }
            // Re-insert transactions with remapped category ids.
            for (Transaction t : snap.transactions) {
                Long newCat = t.getCategoryId() == null ? null : catIdMap.get(t.getCategoryId());
                Transaction fresh = "income".equals(t.getType())
                        ? new com.expensetracker.model.Income(userId, newCat, t.getAmount(),
                                t.getCurrency(), t.getDate(), t.getNote(), t.getPaymentMethod())
                        : new com.expensetracker.model.Expense(userId, newCat, t.getAmount(),
                                t.getCurrency(), t.getDate(), t.getNote(), t.getPaymentMethod());
                txnDao.create(conn, fresh);
                count[0]++;
            }
            // Re-insert budgets (amounts are already in INR — see BudgetService).
            for (Budget b : snap.budgets) {
                Long newCat = catIdMap.get(b.getCategoryId());
                if (newCat != null) {
                    budgetDao.upsert(conn, new Budget(userId, newCat, b.getMonth(), b.getAmount()));
                }
            }
            // Any exception thrown above triggers a rollback in Db.inTransaction.
        });
        return count[0];
    }
}
