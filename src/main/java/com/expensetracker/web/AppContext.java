package com.expensetracker.web;

import com.expensetracker.dao.AlertDao;
import com.expensetracker.dao.BudgetDao;
import com.expensetracker.dao.CategoryDao;
import com.expensetracker.dao.TransactionDao;
import com.expensetracker.dao.UserDao;
import com.expensetracker.service.AlertWatcher;
import com.expensetracker.service.AuthService;
import com.expensetracker.service.BackupService;
import com.expensetracker.service.BudgetService;
import com.expensetracker.service.CsvService;
import com.expensetracker.service.SessionStore;
import com.expensetracker.service.UndoService;

import java.io.File;

/**
 * Week 2 — one object holding every service/DAO the handlers need.
 * Created once in {@link App} and handed to each handler (simple
 * dependency injection without any framework).
 */
public class AppContext {

    public final SessionStore sessions = new SessionStore();
    public final AuthService auth = new AuthService();
    public final BudgetService budgetService = new BudgetService();
    public final CsvService csv = new CsvService();
    public final UndoService undo = new UndoService();
    public final AlertWatcher watcher = new AlertWatcher();

    public final UserDao users = new UserDao();
    public final CategoryDao categories = new CategoryDao();
    public final TransactionDao txns = new TransactionDao();
    public final BudgetDao budgets = new BudgetDao();
    public final AlertDao alerts = new AlertDao();
    public final BackupService backups;

    public AppContext(File backupDir) {
        this.backups = new BackupService(backupDir);
    }
}
