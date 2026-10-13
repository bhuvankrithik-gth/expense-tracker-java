package com.expensetracker.web.pages;

import com.expensetracker.model.Budget;
import com.expensetracker.model.Category;
import com.expensetracker.model.User;
import com.expensetracker.service.BudgetService;
import com.expensetracker.service.ConverterFactory;
import com.expensetracker.util.Html;
import com.expensetracker.util.Result;
import com.expensetracker.util.Svg;
import com.expensetracker.web.AppContext;
import com.expensetracker.web.BaseHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Monthly budgets per category.
 *   GET  /budgets          budget board for ?month=YYYY-MM (default: current)
 *   POST /budgets          set/replace a budget
 *   POST /budgets/delete   remove a budget
 */
public final class BudgetPages {

    private BudgetPages() {
    }

    public static class BudgetHandler extends BaseHandler {
        public BudgetHandler(AppContext ctx) {
            super(ctx);
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                Optional<User> userOpt = currentUser(ex);
                if (userOpt.isEmpty()) {
                    redirect(ex, "/login");
                    return;
                }
                User user = userOpt.get();
                String sub = ex.getRequestURI().getPath().substring("/budgets".length());

                if ("/delete".equals(sub) && "POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    Map<String, String> form = formParams(ex);
                    long id = Long.parseLong(param(form, "id"));
                    ctx.budgetService.deleteBudget(user.getId(), id);
                    redirectWithNotice(ex, "/budgets?month=" + param(form, "month"),
                            "success", "Budget removed.");
                    return;
                }
                if ((sub.isEmpty() || "/".equals(sub)) && "POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    Map<String, String> form = formParams(ex);
                    Result<Void> r = ctx.budgetService.setBudget(user.getId(),
                            Long.parseLong(param(form, "category_id")),
                            param(form, "month"), param(form, "amount"),
                            user.getCurrency());
                    redirectWithNotice(ex, "/budgets?month=" + param(form, "month"),
                            r.isOk() ? "success" : "error",
                            r.isOk() ? "Budget saved." : r.getError());
                    return;
                }
                board(ex, user);
            } catch (Exception e) {
                serverError(ex, e);
            }
        }

        private void board(HttpExchange ex, User user) throws Exception {
            Map<String, String> q = queryParams(ex);
            String month = q.getOrDefault("month", "").trim();
            if (month.isEmpty()) {
                month = BudgetService.currentMonth();
            }
            List<Budget> budgets = ctx.budgetService.budgetsForMonth(user.getId(), month);
            List<Category> categories = ctx.categories.listByUser(user.getId());
            List<String> months = ctx.budgets.monthsForUser(user.getId());
            String code = user.getCurrency();

            StringBuilder cards = new StringBuilder();
            for (Budget b : budgets) {
                // Budgets are stored in INR; convert both sides to the user's
                // display currency for the progress card.
                BigDecimal spentDisplay = spentInDisplayCurrency(user.getId(), b, code);
                BigDecimal budgetDisplay = ConverterFactory.convert(b.getAmount(), "INR", code);
                double pct = pct(spentDisplay, budgetDisplay);
                String status = spentDisplay.compareTo(budgetDisplay) > 0 ? "over"
                        : pct >= 80 ? "near" : "ok";
                cards.append("""
                        <div class="budget-card budget-%s">
                          <div class="budget-head">
                            <h3>%s</h3>
                            <form method="post" action="/budgets/delete" class="inline-form">
                              <input type="hidden" name="id" value="%d">
                              <input type="hidden" name="month" value="%s">
                              <button class="linklike" type="submit">Remove</button>
                            </form>
                          </div>
                          %s
                          <p class="budget-numbers"><strong>%s</strong> of %s
                            <span class="muted">(%s%%)</span></p>
                          %s
                        </div>
                        """.formatted(status, Html.esc(b.getCategoryName()), b.getId(),
                        Html.attr(month), Svg.progress(pct),
                        Html.esc(ConverterFactory.format(spentDisplay, code)),
                        Html.esc(ConverterFactory.format(budgetDisplay, code)),
                        String.format("%.0f", pct),
                        spentDisplay.compareTo(budgetDisplay) > 0
                                ? "<p class=\"budget-warn\">⚠ Over budget by "
                                  + Html.esc(ConverterFactory.format(
                                          spentDisplay.subtract(budgetDisplay), code)) + "</p>"
                                : ""));
            }

            String monthOptions = new StringBuilder()
                    .append("<option value=\"").append(Html.attr(BudgetService.currentMonth())).append("\"")
                    .append(BudgetService.currentMonth().equals(month) ? " selected" : "").append(">")
                    .append(Html.esc(BudgetService.currentMonth())).append(" (current)</option>")
                    .toString();
            StringBuilder mo = new StringBuilder(monthOptions);
            for (String m : months) {
                if (m.equals(BudgetService.currentMonth())) continue;
                mo.append("<option value=\"").append(Html.attr(m)).append("\"")
                  .append(m.equals(month) ? " selected" : "").append(">")
                  .append(Html.esc(m)).append("</option>");
            }

            StringBuilder catOptions = new StringBuilder();
            for (Category c : categories) {
                catOptions.append("<option value=\"").append(c.getId()).append("\">")
                          .append(Html.esc(c.getName())).append("</option>");
            }

            String body = flash(q) + """
                    <section class="page-head">
                      <div><p class="kicker">Monthly limits</p><h1>Budgets</h1></div>
                      <form method="get" action="/budgets" class="inline-form">
                        <label class="muted">Month
                          <select name="month">%s</select>
                        </label>
                        <button class="btn btn-ghost" type="submit">View</button>
                      </form>
                    </section>
                    <section class="card">
                      <h2>Set a budget</h2>
                      <form method="post" action="/budgets" class="form form-row">
                        <input type="hidden" name="month" value="%s">
                        <label>Category<select name="category_id">%s</select></label>
                        <label>Amount (%s)<input type="text" name="amount" required inputmode="decimal" placeholder="0.00"></label>
                        <button class="btn btn-primary" type="submit">Save budget</button>
                      </form>
                      <p class="muted">When spending in a category passes its budget, you'll see a
                      warning — and a background thread also raises an alert on your dashboard.</p>
                    </section>
                    <section class="budget-grid">%s</section>
                    """.formatted(mo, Html.attr(month), catOptions, Html.esc(code),
                    cards.isEmpty()
                            ? Html.emptyState("No budgets for " + month,
                                    "Set your first monthly limit above — e.g. Food ₹8,000.",
                                    null, null)
                            : cards.toString());

            html(ex, 200, Html.page("Budgets", themeFor(ex, user), "budgets",
                    user.getName(), body));
        }

        /**
         * Spend for one budget, converted transaction-by-transaction into the
         * display currency (amounts may have been entered in mixed currencies).
         */
        private BigDecimal spentInDisplayCurrency(long userId, Budget b, String code)
                throws Exception {
            java.time.YearMonth ym = java.time.YearMonth.parse(b.getMonth());
            var txns = ctx.txns.list(userId,
                    new com.expensetracker.dao.TransactionDao.Filter(
                            "expense", b.getCategoryId(),
                            ym.atDay(1), ym.atEndOfMonth(), null, "date_desc"),
                    0);
            BigDecimal total = BigDecimal.ZERO;
            for (var t : txns) {
                total = total.add(ConverterFactory.convert(t.getAmount(), t.getCurrency(), code));
            }
            return total;
        }

        private double pct(BigDecimal spent, BigDecimal budget) {
            if (budget.compareTo(BigDecimal.ZERO) == 0) {
                return 0; // Week 4: never divide by zero
            }
            return spent.multiply(BigDecimal.valueOf(100))
                    .divide(budget, 1, java.math.RoundingMode.HALF_UP).doubleValue();
        }
    }
}
