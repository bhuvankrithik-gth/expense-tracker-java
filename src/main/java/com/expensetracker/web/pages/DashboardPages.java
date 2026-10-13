package com.expensetracker.web.pages;

import com.expensetracker.dao.TransactionDao;
import com.expensetracker.model.Alert;
import com.expensetracker.model.Transaction;
import com.expensetracker.model.User;
import com.expensetracker.service.BudgetService;
import com.expensetracker.service.ConverterFactory;
import com.expensetracker.util.Html;
import com.expensetracker.util.Svg;
import com.expensetracker.web.AppContext;
import com.expensetracker.web.BaseHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * GET /dashboard — totals, category donut, monthly trend bars, recent
 * transactions and unread budget alerts. Amounts are converted from each
 * transaction's own currency into the user's display currency.
 */
public final class DashboardPages {

    private DashboardPages() {
    }

    public static class DashboardHandler extends BaseHandler {
        public DashboardHandler(AppContext ctx) {
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
                String displayCode = user.getCurrency();
                Map<String, String> query = queryParams(ex);

                YearMonth ym = YearMonth.now();
                LocalDate from = ym.atDay(1);
                LocalDate to = ym.atEndOfMonth();

                // This month's transactions, converted to the display currency.
                List<Transaction> monthTxns = ctx.txns.list(user.getId(),
                        new TransactionDao.Filter(null, null, from, to, null, "date_desc"), 0);

                BigDecimal income = BigDecimal.ZERO;
                BigDecimal expense = BigDecimal.ZERO;
                // Week 8: TreeMap keeps categories sorted alphabetically for the chart.
                Map<String, BigDecimal> byCategory = new TreeMap<>();
                for (Transaction t : monthTxns) {
                    BigDecimal inDisplay =
                            ConverterFactory.convert(t.getAmount(), t.getCurrency(), displayCode);
                    if ("income".equals(t.getType())) {
                        income = income.add(inDisplay);
                    } else {
                        expense = expense.add(inDisplay);
                        String cat = t.getCategoryName() == null ? "Uncategorised" : t.getCategoryName();
                        byCategory.merge(cat, inDisplay, BigDecimal::add); // autoboxing in action
                    }
                }
                BigDecimal balance = income.subtract(expense);

                // Trend: last 6 months, converted per transaction.
                List<String> trendLabels = new ArrayList<>();
                List<Double> trendIncome = new ArrayList<>();
                List<Double> trendExpense = new ArrayList<>();
                for (int i = 5; i >= 0; i--) {
                    YearMonth m = ym.minusMonths(i);
                    List<Transaction> txns = ctx.txns.list(user.getId(),
                            new TransactionDao.Filter(null, null, m.atDay(1), m.atEndOfMonth(),
                                    null, "date_desc"), 0);
                    BigDecimal in = BigDecimal.ZERO, ex2 = BigDecimal.ZERO;
                    for (Transaction t : txns) {
                        BigDecimal d = ConverterFactory.convert(t.getAmount(), t.getCurrency(), displayCode);
                        if ("income".equals(t.getType())) {
                            in = in.add(d);
                        } else {
                            ex2 = ex2.add(d);
                        }
                    }
                    trendLabels.add(m.getMonth().name().substring(0, 3));
                    trendIncome.add(in.doubleValue());
                    trendExpense.add(ex2.doubleValue());
                }

                List<Transaction> recent = ctx.txns.list(user.getId(),
                        new TransactionDao.Filter(null, null, null, null, null, "date_desc"), 5);
                List<Alert> alerts = ctx.alerts.unreadByUser(user.getId());

                String body = flash(query)
                        + alertsBanner(alerts)
                        + headerRow(user, displayCode, ym)
                        + statCards(income, expense, balance, displayCode, monthTxns.size())
                        + chartsRow(byCategory, trendLabels, trendIncome, trendExpense, displayCode)
                        + recentCard(recent, displayCode, user);

                html(ex, 200, Html.page("Dashboard", themeFor(ex, user), "dashboard",
                        user.getName(), body));
            } catch (Exception e) {
                serverError(ex, e);
            }
        }

        private String alertsBanner(List<Alert> alerts) {
            if (alerts.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder(
                    "<section class=\"card alert-card\"><h2>Budget alerts</h2><ul class=\"alerts\">");
            for (Alert a : alerts) {
                sb.append("<li>").append(Html.esc(a.getMessage())).append("</li>");
            }
            sb.append("</ul><form method=\"post\" action=\"/alerts/read\">"
                    + "<button class=\"btn btn-ghost\" type=\"submit\">Mark all as read</button>"
                    + "</form></section>");
            return sb.toString();
        }

        private String headerRow(User user, String displayCode, YearMonth ym) {
            String undoBtn = "";
            if (ctx.undo.canUndo(user.getId())) {
                String what = ctx.undo.peek(user.getId())
                        .map(t -> t.describe()).orElse("last delete");
                undoBtn = """
                        <form method="post" action="/transactions/undo" class="inline-form">
                          <button class="btn btn-ghost" type="submit" title="%s">↩ Undo delete</button>
                        </form>""".formatted(Html.attr(what));
            }
            return """
                    <section class="page-head">
                      <div>
                        <p class="kicker">%s · showing %s</p>
                        <h1>Good %s, %s</h1>
                      </div>
                      <div class="page-actions">
                        %s
                        <a class="btn btn-primary" href="/transactions/new">+ Add transaction</a>
                      </div>
                    </section>
                    """.formatted(Html.esc(ym.getMonth()
                            .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
                            + " " + ym.getYear()),
                    Html.esc(displayCode), daypart(), Html.esc(user.getName()), undoBtn);
        }

        private String daypart() {
            int h = java.time.LocalTime.now().getHour();
            return h < 12 ? "morning" : h < 17 ? "afternoon" : "evening";
        }

        private String statCards(BigDecimal income, BigDecimal expense, BigDecimal balance,
                                 String code, int txnCount) {
            String balClass = balance.compareTo(BigDecimal.ZERO) < 0 ? "negative" : "positive";
            return """
                    <section class="stats">
                      <div class="stat"><p class="stat-label">Income</p>
                        <p class="stat-value positive">%s</p></div>
                      <div class="stat"><p class="stat-label">Expenses</p>
                        <p class="stat-value negative">%s</p></div>
                      <div class="stat"><p class="stat-label">Balance</p>
                        <p class="stat-value %s">%s</p></div>
                      <div class="stat"><p class="stat-label">Transactions</p>
                        <p class="stat-value">%d</p><p class="muted">this month</p></div>
                    </section>
                    """.formatted(ConverterFactory.format(income, code),
                    ConverterFactory.format(expense, code), balClass,
                    ConverterFactory.format(balance, code), txnCount);
        }

        private String chartsRow(Map<String, BigDecimal> byCategory, List<String> labels,
                                 List<Double> income, List<Double> expense, String code) {
            List<Svg.Slice> slices = byCategory.entrySet().stream()
                    .map(e -> new Svg.Slice(e.getKey()
                            + " (" + ConverterFactory.format(e.getValue(), code) + ")",
                            e.getValue().doubleValue()))
                    .toList();
            return """
                    <section class="grid-2">
                      <div class="card">
                        <h2>Spending by category</h2>
                        <p class="muted">Where this month's money went.</p>
                        %s
                      </div>
                      <div class="card">
                        <h2>Monthly trend</h2>
                        <p class="muted">Income vs expenses, last 6 months.</p>
                        %s
                      </div>
                    </section>
                    """.formatted(Svg.donut(slices, 190), Svg.bars(labels, income, expense, 560, 300));
        }

        private String recentCard(List<Transaction> recent, String code, User user) {
            StringBuilder sb = new StringBuilder(
                    "<section class=\"card\"><div class=\"card-head\"><h2>Recent transactions</h2>"
                    + "<a href=\"/transactions\">View all →</a></div>");
            if (recent.isEmpty()) {
                sb.append(Html.emptyState("Nothing here yet",
                        "Add your first transaction to start seeing your money clearly.",
                        "/transactions/new", "Add transaction"));
            } else {
                sb.append("<table class=\"table\"><thead><tr><th>Date</th><th>Note</th>"
                        + "<th>Category</th><th class=\"num\">Amount</th></tr></thead><tbody>");
                for (Transaction t : recent) {
                    BigDecimal d = ConverterFactory.convert(t.getAmount(), t.getCurrency(), code);
                    String cls = "income".equals(t.getType()) ? "positive" : "negative";
                    String sign = "income".equals(t.getType()) ? "+" : "−";
                    sb.append("<tr><td>").append(t.getDate()).append("</td><td>")
                      .append(Html.esc(t.getNote() == null ? "—" : t.getNote())).append("</td><td>")
                      .append(Html.esc(t.getCategoryName() == null ? "—" : t.getCategoryName()))
                      .append("</td><td class=\"num ").append(cls).append("\">").append(sign)
                      .append(Html.esc(ConverterFactory.format(d, code))).append("</td></tr>");
                }
                sb.append("</tbody></table>");
            }
            sb.append("</section>");
            return sb.toString();
        }
    }
}
