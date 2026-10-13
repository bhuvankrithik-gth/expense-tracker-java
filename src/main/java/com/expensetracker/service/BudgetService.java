package com.expensetracker.service;

import com.expensetracker.dao.BudgetDao;
import com.expensetracker.dao.CategoryDao;
import com.expensetracker.dao.TransactionDao;
import com.expensetracker.exception.BudgetExceededException;
import com.expensetracker.exception.InvalidAmountException;
import com.expensetracker.exception.ValidationException;
import com.expensetracker.model.Budget;
import com.expensetracker.model.Category;
import com.expensetracker.util.Result;
import com.expensetracker.util.Validation;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Budget rules. After an expense is saved, {@link #checkAfterAdd} compares
 * the month's spend against the budget and THROWS the custom
 * {@link BudgetExceededException} when the limit is crossed (Week 4).
 * Callers catch it and show a warning banner — the expense is kept.
 */
public class BudgetService {

    private final BudgetDao budgetDao = new BudgetDao();
    private final TransactionDao txnDao = new TransactionDao();
    private final CategoryDao categoryDao = new CategoryDao();

    /**
     * Set (or replace) a monthly budget; validates everything first.
     * The amount is entered in the user's display currency and converted to
     * INR before storing — the budgets table always holds INR, so spend
     * comparisons (also in INR) are apples-to-apples.
     */
    public Result<Void> setBudget(long userId, long categoryId, String month,
                                  String amountRaw, String displayCurrency) {
        try {
            String cleanMonth = Validation.requireMonth(month);
            BigDecimal amount = Validation.parseAmount(amountRaw);
            Optional<Category> cat = categoryDao.findById(userId, categoryId);
            if (cat.isEmpty()) {
                return Result.fail("Category not found.");
            }
            BigDecimal inr = ConverterFactory.convert(amount, displayCurrency, "INR");
            budgetDao.upsert(new Budget(userId, categoryId, cleanMonth, inr));
            return Result.ok(null);
        } catch (ValidationException | InvalidAmountException e) {
            return Result.fail(e.getMessage());
        } catch (SQLException e) {
            return Result.fail("Database error while saving the budget.");
        }
    }

    /**
     * Called right after an expense is stored. Throws BudgetExceededException
     * when this expense pushed the category over its monthly budget.
     * Both sides are in INR, so mixed-currency expenses compare correctly.
     */
    public void checkAfterAdd(long userId, long categoryId, LocalDate date)
            throws BudgetExceededException, SQLException {
        String month = date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        BigDecimal budget = budgetDao.amountFor(userId, categoryId, month);
        if (budget == null) {
            return; // no budget set for this category/month — nothing to check
        }
        BigDecimal spent = txnDao.spendInCategoryMonthInr(userId, categoryId, month);
        if (spent.compareTo(budget) > 0) {
            String name = categoryDao.findById(userId, categoryId)
                    .map(Category::getName).orElse("this category");
            throw new BudgetExceededException(name, budget, spent);
        }
    }

    public List<Budget> budgetsForMonth(long userId, String month) throws SQLException {
        return budgetDao.listByMonth(userId, month);
    }

    public boolean deleteBudget(long userId, long budgetId) throws SQLException {
        return budgetDao.delete(userId, budgetId);
    }

    /** Current month in "YYYY-MM" form — the default budgets view. */
    public static String currentMonth() {
        return LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
    }
}
