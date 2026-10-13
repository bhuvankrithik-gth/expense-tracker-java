package com.expensetracker;

import com.expensetracker.dao.BudgetDao;
import com.expensetracker.dao.CategoryDao;
import com.expensetracker.dao.Db;
import com.expensetracker.dao.TransactionDao;
import com.expensetracker.dao.UserDao;
import com.expensetracker.model.Budget;
import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.model.Income;
import com.expensetracker.model.Transaction;
import com.expensetracker.model.User;
import com.expensetracker.util.Passwords;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Week-12 DAO tests: full JDBC CRUD against a real MySQL database.
 * Skipped automatically when no database is reachable (e.g. plain
 * `mvn -DskipTests` style environments), so unit tests stay green anywhere.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DaoTest {

    private final UserDao users = new UserDao();
    private final CategoryDao categories = new CategoryDao();
    private final TransactionDao txns = new TransactionDao();
    private final BudgetDao budgets = new BudgetDao();

    private long userId;
    private long foodId;

    @BeforeAll
    void setUp() {
        try {
            Db.initSchema();
            Db.getConnection().close();
        } catch (Exception e) {
            Assumptions.abort("No MySQL available for DAO tests: " + e.getMessage());
        }
        try {
            String email = "test-" + UUID.randomUUID() + "@example.com";
            User u = new User("Test User", email, Passwords.hash("password123"));
            userId = users.create(u);
            categories.ensureDefaults(userId);
            foodId = categories.findByName(userId, "Food").orElseThrow().getId();
        } catch (Exception e) {
            Assumptions.abort("DAO test setup failed: " + e.getMessage());
        }
    }

    @Test
    void userCrud() throws Exception {
        Optional<User> found = users.findByEmail(
                users.findById(userId).orElseThrow().getEmail());
        assertTrue(found.isPresent());
        assertEquals(userId, found.get().getId());

        users.updatePreferences(userId, "USD", "dark");
        assertEquals("USD", users.findById(userId).orElseThrow().getCurrency());
        assertEquals("dark", users.findById(userId).orElseThrow().getTheme());
        users.updatePreferences(userId, "INR", "light"); // restore
    }

    @Test
    void transactionCrudAndPolymorphism() throws Exception {
        // CREATE an expense...
        Expense expense = new Expense(userId, foodId, new BigDecimal("250.00"),
                "INR", LocalDate.now(), "Lunch", "UPI");
        long expenseId = txns.create(expense);
        assertTrue(expenseId > 0);

        // ...READ it back as the right subclass (Week 3 polymorphism via DAO mapping)
        Transaction read = txns.findById(userId, expenseId).orElseThrow();
        assertInstanceOf(Expense.class, read);
        assertEquals(new BigDecimal("250.00"), read.getAmount());
        assertEquals("Food", read.getCategoryName());
        assertEquals(new BigDecimal("-250.00"), read.getSignedAmount());

        // UPDATE
        read.setNote("Lunch with team");
        assertTrue(txns.update(read));
        assertEquals("Lunch with team",
                txns.findById(userId, expenseId).orElseThrow().getNote());

        // Income sibling
        Income income = new Income(userId, null, new BigDecimal("5000"),
                "INR", LocalDate.now(), "Stipend", "Bank transfer");
        long incomeId = txns.create(income);
        assertInstanceOf(Income.class, txns.findById(userId, incomeId).orElseThrow());

        // DELETE returns the deleted row (feeds the undo history)
        Optional<Transaction> deleted = txns.delete(userId, expenseId);
        assertTrue(deleted.isPresent());
        assertTrue(txns.findById(userId, expenseId).isEmpty());

        // Per-user isolation: another user id sees nothing
        assertTrue(txns.findById(userId + 999999, incomeId).isEmpty());
        txns.delete(userId, incomeId);
    }

    @Test
    void filtersAndSorting() throws Exception {
        txns.create(new Expense(userId, foodId, new BigDecimal("100"),
                "INR", LocalDate.now().minusDays(2), "filter-me snacks", "Cash"));
        txns.create(new Expense(userId, foodId, new BigDecimal("900"),
                "INR", LocalDate.now(), "filter-me dinner", "Card"));
        txns.create(new Income(userId, null, new BigDecimal("1000"),
                "INR", LocalDate.now(), "filter-me pay", "Bank transfer"));

        var filter = new TransactionDao.Filter("expense", foodId, null, null,
                "filter-me", "amount_desc");
        List<Transaction> found = txns.list(userId, filter, 0);
        assertEquals(2, found.size());
        // amount_desc: biggest first
        assertTrue(found.get(0).getAmount().compareTo(found.get(1).getAmount()) > 0);
        assertTrue(found.stream().allMatch(t -> "expense".equals(t.getType())));

        // cleanup
        for (Transaction t : txns.list(userId,
                new TransactionDao.Filter(null, null, null, null, "filter-me", "date_desc"), 0)) {
            txns.delete(userId, t.getId());
        }
    }

    @Test
    void budgetUpsertAndProgress() throws Exception {
        String month = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        budgets.upsert(new Budget(userId, foodId, month, new BigDecimal("1000")));
        budgets.upsert(new Budget(userId, foodId, month, new BigDecimal("800"))); // replace

        txns.create(new Expense(userId, foodId, new BigDecimal("200"),
                "INR", LocalDate.now(), "budget-test", "Cash"));
        List<Budget> list = budgets.listByMonth(userId, month);
        Budget b = list.stream().filter(x -> x.getCategoryId() == foodId)
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("800.00"), b.getAmount());
        // SQL-side currency conversion can add decimal places; compare numerically.
        assertEquals(0, new BigDecimal("200.00").compareTo(b.getSpent()));
        assertEquals(25.0, b.percentUsed(), 0.01);
        assertFalse(b.isExceeded());

        assertEquals(new BigDecimal("800.00"),
                budgets.amountFor(userId, foodId, month));
        assertEquals(0, new BigDecimal("200.00").compareTo(
                txns.spendInCategoryMonthInr(userId, foodId, month)));

        // cleanup
        for (Transaction t : txns.list(userId,
                new TransactionDao.Filter(null, null, null, null, "budget-test", "date_desc"), 0)) {
            txns.delete(userId, t.getId());
        }
        assertTrue(budgets.delete(userId, b.getId()));
    }

    @Test
    void categoryCrud() throws Exception {
        Category custom = new Category(userId, "TestCat" + UUID.randomUUID().toString().substring(0, 6),
                "expense", false);
        long id = categories.create(custom);
        assertTrue(categories.findById(userId, id).isPresent());
        assertTrue(categories.delete(userId, id));       // custom: deletable
        assertFalse(categories.delete(userId, foodId));  // default: protected
    }

    @Test
    void analyticsQueries() throws Exception {
        String month = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        txns.create(new Expense(userId, foodId, new BigDecimal("300"),
                "INR", LocalDate.now(), "analytics-test", "Cash"));

        // JOIN + GROUP BY + SUM
        Map<String, BigDecimal> byCat = txns.spendByCategory(userId, month);
        assertTrue(byCat.containsKey("Food"));

        // Aggregate totals
        TransactionDao.Totals totals = txns.totals(userId,
                LocalDate.now().withDayOfMonth(1), LocalDate.now());
        assertTrue(totals.expense().compareTo(BigDecimal.ZERO) > 0);
        assertTrue(totals.count() > 0);

        // Monthly trend buckets
        List<TransactionDao.MonthBucket> trend = txns.monthlyTrend(userId, 3);
        assertFalse(trend.isEmpty());

        for (Transaction t : txns.list(userId,
                new TransactionDao.Filter(null, null, null, null, "analytics-test", "date_desc"), 0)) {
            txns.delete(userId, t.getId());
        }
    }
}
