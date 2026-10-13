package com.expensetracker.web.pages;

import com.expensetracker.dao.TransactionDao;
import com.expensetracker.model.Transaction;
import com.expensetracker.model.User;
import com.expensetracker.service.ConverterFactory;
import com.expensetracker.util.Html;
import com.expensetracker.util.Svg;
import com.expensetracker.web.AppContext;
import com.expensetracker.web.BaseHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * GET /reports — deeper analytics for a chosen date range:
 * category donut, income-vs-expense bars, top categories table,
 * and averages per day (with ArithmeticException guarded).
 */
public final class ReportPages {

    private ReportPages() {
    }

    public static class ReportHandler extends BaseHandler {
        public ReportHandler(AppContext ctx) {
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
                Map<String, String> q = queryParams(ex);

                LocalDate to = parseOr(q.get("to"), LocalDate.now());
                LocalDate from = parseOr(q.get("from"), to.withDayOfMonth(1));
                if (from.isAfter(to)) {
                    LocalDate swap = from;
                    from = to;
                    to = swap;
                }
                String code = user.getCurrency();

                List<Transaction> txns = ctx.txns.list(user.getId(),
                        new TransactionDao.Filter(null, null, from, to, null, "date_desc"), 0);

                BigDecimal income = BigDecimal.ZERO;
                BigDecimal expense = BigDecimal.ZERO;
                // Week 8: TreeMap (sorted) + HashMap-style merge for grouping.
                Map<String, BigDecimal> spendByCat = new TreeMap<>();
                Map<String, BigDecimal> incomeByCat = new TreeMap<>();
                for (Transaction t : txns) {
                    BigDecimal d = ConverterFactory.convert(t.getAmount(), t.getCurrency(), code);
                    String cat = t.getCategoryName() == null ? "Uncategorised" : t.getCategoryName();
                    if ("income".equals(t.getType())) {
                        income = income.add(d);
                        incomeByCat.merge(cat, d, BigDecimal::add);
                    } else {
                        expense = expense.add(d);
                        spendByCat.merge(cat, d, BigDecimal::add);
                    }
                }

                // Week 4: average per day — guard the division.
                long days = ChronoUnit.DAYS.between(from, to) + 1;
                String avgPerDay;
                try {
                    if (days <= 0) {
                        throw new ArithmeticException("empty range");
                    }
                    avgPerDay = ConverterFactory.format(
                            expense.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP), code);
                } catch (ArithmeticException e) {
                    avgPerDay = "—"; // Week 4: friendly fallback instead of a crash
                }

                // Top categories table (spend).
                List<Map.Entry<String, BigDecimal>> top =
                        new ArrayList<>(spendByCat.entrySet());
                top.sort((a, b) -> b.getValue().compareTo(a.getValue())); // descending
                StringBuilder topRows = new StringBuilder();
                int rank = 1;
                for (Map.Entry<String, BigDecimal> e : top) {
                    if (rank > 8) break;
                    double share = expense.compareTo(BigDecimal.ZERO) == 0 ? 0
                            : e.getValue().multiply(BigDecimal.valueOf(100))
                                    .divide(expense, 1, RoundingMode.HALF_UP).doubleValue();
                    topRows.append("<tr><td>").append(rank++).append("</td><td>")
                           .append(Html.esc(e.getKey())).append("</td>")
                           .append("<td class=\"num\">")
                           .append(Html.esc(ConverterFactory.format(e.getValue(), code)))
                           .append("</td><td class=\"num\">")
                           .append(String.format("%.1f%%", share)).append("</td></tr>");
                }

                List<Svg.Slice> slices = spendByCat.entrySet().stream()
                        .map(e -> new Svg.Slice(e.getKey(), e.getValue().doubleValue()))
                        .toList();

                // Monthly bars inside the range (up to 12 months back from `to`).
                List<String> labels = new ArrayList<>();
                List<Double> inSeries = new ArrayList<>();
                List<Double> exSeries = new ArrayList<>();
                YearMonth startYm = YearMonth.from(from);
                YearMonth endYm = YearMonth.from(to);
                for (YearMonth m = startYm; !m.isAfter(endYm); m = m.plusMonths(1)) {
                    List<Transaction> mtx = ctx.txns.list(user.getId(),
                            new TransactionDao.Filter(null, null, m.atDay(1), m.atEndOfMonth(),
                                    null, "date_desc"), 0);
                    BigDecimal in = BigDecimal.ZERO, ex2 = BigDecimal.ZERO;
                    for (Transaction t : mtx) {
                        BigDecimal d = ConverterFactory.convert(t.getAmount(), t.getCurrency(), code);
                        if ("income".equals(t.getType())) in = in.add(d);
                        else ex2 = ex2.add(d);
                    }
                    labels.add(m.getMonth().name().substring(0, 3) + " '" + (m.getYear() % 100));
                    inSeries.add(in.doubleValue());
                    exSeries.add(ex2.doubleValue());
                }

                String body = """
                        <section class="page-head">
                          <div><p class="kicker">Deeper insights</p><h1>Reports</h1></div>
                        </section>
                        <section class="card">
                          <form method="get" action="/reports" class="form form-row">
                            <label>From <input type="date" name="from" value="%s"></label>
                            <label>To <input type="date" name="to" value="%s"></label>
                            <button class="btn btn-primary" type="submit">Run report</button>
                          </form>
                        </section>
                        <section class="stats">
                          <div class="stat"><p class="stat-label">Income in range</p>
                            <p class="stat-value positive">%s</p></div>
                          <div class="stat"><p class="stat-label">Expenses in range</p>
                            <p class="stat-value negative">%s</p></div>
                          <div class="stat"><p class="stat-label">Net</p>
                            <p class="stat-value">%s</p></div>
                          <div class="stat"><p class="stat-label">Avg spend / day</p>
                            <p class="stat-value">%s</p><p class="muted">%d days</p></div>
                        </section>
                        <section class="grid-2">
                          <div class="card"><h2>Expense breakdown</h2>
                            %s</div>
                          <div class="card"><h2>Monthly trend</h2>
                            %s</div>
                        </section>
                        <section class="card">
                          <h2>Top spending categories</h2>
                          %s
                        </section>
                        """.formatted(from, to,
                        Html.esc(ConverterFactory.format(income, code)),
                        Html.esc(ConverterFactory.format(expense, code)),
                        Html.esc(ConverterFactory.format(income.subtract(expense), code)),
                        Html.esc(avgPerDay), days,
                        Svg.donut(slices, 190),
                        Svg.bars(labels, inSeries, exSeries, 560, 300),
                        topRows.isEmpty()
                                ? "<p class=\"muted\">No expenses in this range.</p>"
                                : "<table class=\"table\"><thead><tr><th>#</th><th>Category</th>"
                                  + "<th class=\"num\">Spent</th><th class=\"num\">Share</th>"
                                  + "</tr></thead><tbody>" + topRows + "</tbody></table>");

                html(ex, 200, Html.page("Reports", themeFor(ex, user), "reports",
                        user.getName(), body));
            } catch (Exception e) {
                serverError(ex, e);
            }
        }

        private LocalDate parseOr(String raw, LocalDate fallback) {
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            try {
                return LocalDate.parse(raw.trim());
            } catch (Exception e) {
                return fallback;
            }
        }
    }
}
