package com.expensetracker.web.pages;

import com.expensetracker.dao.TransactionDao;
import com.expensetracker.exception.BudgetExceededException;
import com.expensetracker.exception.InvalidAmountException;
import com.expensetracker.exception.ValidationException;
import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.model.Income;
import com.expensetracker.model.Transaction;
import com.expensetracker.model.User;
import com.expensetracker.service.ActivityLogService;
import com.expensetracker.service.ConverterFactory;
import com.expensetracker.util.Html;
import com.expensetracker.util.Result;
import com.expensetracker.util.Validation;
import com.expensetracker.web.AppContext;
import com.expensetracker.web.BaseHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Full CRUD for transactions plus search / filter / sort and "undo delete".
 * Routes (all under /transactions):
 *   GET  /transactions            list + filters
 *   GET  /transactions/new        add form
 *   POST /transactions            create
 *   GET  /transactions/{id}/edit  edit form
 *   POST /transactions/{id}       update
 *   POST /transactions/{id}/delete  delete (remembered for undo)
 *   POST /transactions/undo       restore last deleted
 */
public final class TransactionPages {

    private TransactionPages() {
    }

    private static final String[] METHODS = {"Cash", "Card", "UPI", "Bank transfer", "Other"};

    public static class TransactionHandler extends BaseHandler {
        public TransactionHandler(AppContext ctx) {
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
                String method = ex.getRequestMethod();
                String sub = ex.getRequestURI().getPath().substring("/transactions".length());

                if (sub.isEmpty() || "/".equals(sub)) {
                    if ("GET".equalsIgnoreCase(method)) list(ex, user);
                    else if ("POST".equalsIgnoreCase(method)) create(ex, user);
                    else redirect(ex, "/transactions");
                } else if ("/new".equals(sub)) {
                    if ("GET".equalsIgnoreCase(method)) form(ex, user, null, null);
                    else redirect(ex, "/transactions");
                } else if ("/undo".equals(sub) && "POST".equalsIgnoreCase(method)) {
                    undo(ex, user);
                } else if (sub.matches("/\\d+/edit") && "GET".equalsIgnoreCase(method)) {
                    editForm(ex, user, Long.parseLong(sub.split("/")[1]));
                } else if (sub.matches("/\\d+/delete") && "POST".equalsIgnoreCase(method)) {
                    delete(ex, user, Long.parseLong(sub.split("/")[1]));
                } else if (sub.matches("/\\d+") && "POST".equalsIgnoreCase(method)) {
                    update(ex, user, Long.parseLong(sub.substring(1)));
                } else {
                    redirect(ex, "/transactions");
                }
            } catch (Exception e) {
                serverError(ex, e);
            }
        }

        // ---- list with search / filter / sort --------------------------------

        private void list(HttpExchange ex, User user) throws Exception {
            Map<String, String> q = queryParams(ex);
            String type = emptyToNull(q.get("type"));
            if (type != null && !type.equals("expense") && !type.equals("income")) type = null;
            Long categoryId = parseLongOrNull(q.get("category"));
            LocalDate from = parseDateOrNull(q.get("from"));
            LocalDate to = parseDateOrNull(q.get("to"));
            String search = emptyToNull(q.get("q"));
            String sort = q.getOrDefault("sort", "date_desc");

            TransactionDao.Filter filter =
                    new TransactionDao.Filter(type, categoryId, from, to, search, sort);
            List<Transaction> txns = ctx.txns.list(user.getId(), filter, 200);
            List<Category> categories = ctx.categories.listByUser(user.getId());
            String code = user.getCurrency();

            StringBuilder rows = new StringBuilder();
            BigDecimal pageTotal = BigDecimal.ZERO;
            for (Transaction t : txns) {
                BigDecimal d = ConverterFactory.convert(t.getAmount(), t.getCurrency(), code);
                if ("income".equals(t.getType())) pageTotal = pageTotal.add(d);
                else pageTotal = pageTotal.subtract(d);
                String cls = "income".equals(t.getType()) ? "positive" : "negative";
                String sign = "income".equals(t.getType()) ? "+" : "−";
                rows.append("<tr><td>").append(t.getDate()).append("</td>")
                    .append("<td><span class=\"pill pill-").append(t.getType()).append("\">")
                    .append(t.getType()).append("</span></td>")
                    .append("<td>").append(Html.esc(t.getCategoryName() == null ? "—" : t.getCategoryName())).append("</td>")
                    .append("<td>").append(Html.esc(t.getNote() == null ? "—" : t.getNote())).append("</td>")
                    .append("<td>").append(Html.esc(t.getPaymentMethod() == null ? "—" : t.getPaymentMethod())).append("</td>")
                    .append("<td class=\"num ").append(cls).append("\">").append(sign)
                    .append(Html.esc(ConverterFactory.format(d, code))).append("</td>")
                    .append("<td class=\"row-actions\">")
                    .append("<a href=\"/transactions/").append(t.getId()).append("/edit\">Edit</a>")
                    .append("<form method=\"post\" action=\"/transactions/").append(t.getId())
                    .append("/delete\" class=\"inline-form\">")
                    .append("<button class=\"linklike\" type=\"submit\">Delete</button></form>")
                    .append("</td></tr>");
            }

            String undoBtn = "";
            if (ctx.undo.canUndo(user.getId())) {
                undoBtn = """
                        <form method="post" action="/transactions/undo" class="inline-form">
                          <button class="btn btn-ghost" type="submit">↩ Undo last delete</button>
                        </form>""";
            }

            String body = flash(q) + """
                    <section class="page-head">
                      <div><p class="kicker">%d transactions · net %s</p>
                      <h1>Transactions</h1></div>
                      <div class="page-actions">%s
                        <a class="btn btn-primary" href="/transactions/new">+ Add transaction</a>
                      </div>
                    </section>
                    <section class="card">
                      <form method="get" action="/transactions" class="filters">
                        <label>Type
                          <select name="type">
                            <option value="">All</option>
                            <option value="expense"%s>Expenses</option>
                            <option value="income"%s>Income</option>
                          </select>
                        </label>
                        <label>Category
                          <select name="category">
                            <option value="">All</option>
                            %s
                          </select>
                        </label>
                        <label>From <input type="date" name="from" value="%s"></label>
                        <label>To <input type="date" name="to" value="%s"></label>
                        <label>Search <input type="search" name="q" value="%s" placeholder="note or method…"></label>
                        <label>Sort
                          <select name="sort">
                            <option value="date_desc"%s>Newest first</option>
                            <option value="date_asc"%s>Oldest first</option>
                            <option value="amount_desc"%s>Amount ↓</option>
                            <option value="amount_asc"%s>Amount ↑</option>
                          </select>
                        </label>
                        <button class="btn btn-ghost" type="submit">Apply</button>
                        <a class="btn btn-ghost" href="/transactions">Clear</a>
                      </form>
                    """.formatted(txns.size(), Html.esc(ConverterFactory.format(pageTotal, code)),
                    undoBtn,
                    "expense".equals(type) ? " selected" : "",
                    "income".equals(type) ? " selected" : "",
                    categoryOptions(categories, categoryId),
                    Html.attr(q.getOrDefault("from", "")),
                    Html.attr(q.getOrDefault("to", "")),
                    Html.attr(q.getOrDefault("q", "")),
                    "date_desc".equals(sort) ? " selected" : "",
                    "date_asc".equals(sort) ? " selected" : "",
                    "amount_desc".equals(sort) ? " selected" : "",
                    "amount_asc".equals(sort) ? " selected" : "");

            if (txns.isEmpty()) {
                body += Html.emptyState("No transactions match",
                        "Try widening the filters, or add your first transaction.",
                        "/transactions/new", "Add transaction");
            } else {
                body += "<div class=\"table-scroll\"><table class=\"table\"><thead><tr>"
                        + "<th>Date</th><th>Type</th><th>Category</th><th>Note</th>"
                        + "<th>Method</th><th class=\"num\">Amount</th><th></th>"
                        + "</tr></thead><tbody>" + rows + "</tbody></table></div>";
            }
            body += "</section>";
            html(ex, 200, Html.page("Transactions", themeFor(ex, user), "transactions",
                    user.getName(), body));
        }

        // ---- create / update ---------------------------------------------------

        private void form(HttpExchange ex, User user, Transaction existing, String error)
                throws Exception {
            List<Category> categories = ctx.categories.listByUser(user.getId());
            // New forms can default the type via ?type=income (dashboard shortcuts).
            String type = existing != null ? existing.getType()
                    : "income".equals(queryParams(ex).get("type")) ? "income" : "expense";
            BigDecimal amount = existing != null ? existing.getAmount() : null;
            String currency = existing != null ? existing.getCurrency() : user.getCurrency();
            LocalDate date = existing != null ? existing.getDate() : LocalDate.now();
            Long categoryId = existing != null ? existing.getCategoryId() : null;
            String note = existing != null && existing.getNote() != null ? existing.getNote() : "";
            String method = existing != null && existing.getPaymentMethod() != null
                    ? existing.getPaymentMethod() : "";

            boolean isEdit = existing != null;
            String action = isEdit ? "/transactions/" + existing.getId() : "/transactions";
            StringBuilder sb = new StringBuilder();
            sb.append("<section class=\"card narrow\">")
              .append("<p class=\"kicker\">").append(isEdit ? "Edit" : "New").append(" transaction</p>")
              .append("<h1>").append(isEdit ? "Edit transaction" : "Add transaction").append("</h1>")
              .append(Html.notice("error", error))
              .append("<form method=\"post\" action=\"").append(action).append("\" class=\"form\">")
              .append("<div class=\"seg\">")
              .append("<label><input type=\"radio\" name=\"type\" value=\"expense\"")
              .append("expense".equals(type) ? " checked" : "").append("> Expense</label>")
              .append("<label><input type=\"radio\" name=\"type\" value=\"income\"")
              .append("income".equals(type) ? " checked" : "").append("> Income</label>")
              .append("</div>")
              .append("<label>Amount<input type=\"text\" name=\"amount\" required inputmode=\"decimal\" placeholder=\"0.00\" value=\"")
              .append(amount == null ? "" : Html.attr(amount.toPlainString())).append("\"></label>")
              .append("<label>Currency<select name=\"currency\">");
            for (String c : ConverterFactory.codes()) {
                sb.append("<option value=\"").append(c).append("\"")
                  .append(c.equals(currency) ? " selected" : "").append(">").append(c).append("</option>");
            }
            sb.append("</select></label>")
              .append("<label>Date<input type=\"date\" name=\"date\" required value=\"")
              .append(date).append("\"></label>")
              .append("<label>Category<select name=\"category_id\">")
              .append("<option value=\"\">— No category —</option>")
              .append(categoryOptions(categories, categoryId))
              .append("</select></label>")
              .append("<label>Note<input type=\"text\" name=\"note\" maxlength=\"255\" value=\"")
              .append(Html.attr(note)).append("\" placeholder=\"What was this for?\"></label>")
              .append("<label>Payment method<select name=\"payment_method\">")
              .append("<option value=\"\">—</option>");
            for (String m : METHODS) {
                sb.append("<option").append(m.equals(method) ? " selected" : "").append(">")
                  .append(m).append("</option>");
            }
            sb.append("</select></label>")
              .append("<div class=\"form-actions\">")
              .append("<button class=\"btn btn-primary btn-lg\" type=\"submit\">")
              .append(isEdit ? "Save changes" : "Add transaction").append("</button>")
              .append("<a class=\"btn btn-ghost\" href=\"/transactions\">Cancel</a>")
              .append("</div></form></section>");

            html(ex, 200, Html.page(isEdit ? "Edit transaction" : "Add transaction",
                    themeFor(ex, user), "transactions", user.getName(), sb.toString()));
        }

        private void create(HttpExchange ex, User user) throws Exception {
            Map<String, String> form = formParams(ex);
            try {
                Transaction t = buildFromForm(user, form, null);
                ctx.txns.create(t);
                ActivityLogService.get().log(user.getId(), "TXN_ADD", "Added " + t.describe());
                // Week 4: warn (not block) when the budget is exceeded.
                String warn = null;
                if ("expense".equals(t.getType()) && t.getCategoryId() != null) {
                    try {
                        ctx.budgetService.checkAfterAdd(user.getId(), t.getCategoryId(), t.getDate());
                    } catch (BudgetExceededException e) {
                        // The exception carries INR values (the base currency);
                        // format them in the user's display currency for the banner.
                        String code = user.getCurrency();
                        warn = "Heads up: budget exceeded for '"
                                + e.getCategoryName() + "': spent "
                                + ConverterFactory.format(
                                        ConverterFactory.convert(e.getSpent(), "INR", code), code)
                                + " against a budget of "
                                + ConverterFactory.format(
                                        ConverterFactory.convert(e.getBudget(), "INR", code), code)
                                + ".";
                        ActivityLogService.get().log(user.getId(), "BUDGET_WARN", warn);
                    }
                }
                if (warn != null) {
                    redirectWithNotice(ex, "/transactions", "warn", warn);
                } else {
                    redirectWithNotice(ex, "/transactions", "success", "Transaction added.");
                }
            } catch (InvalidAmountException | ValidationException e) {
                form(ex, user, null, e.getMessage());
            }
        }

        private void editForm(HttpExchange ex, User user, long id) throws Exception {
            Optional<Transaction> t = ctx.txns.findById(user.getId(), id);
            if (t.isEmpty()) {
                redirectWithNotice(ex, "/transactions", "error", "Transaction not found.");
                return;
            }
            form(ex, user, t.get(), null);
        }

        private void update(HttpExchange ex, User user, long id) throws Exception {
            Optional<Transaction> existing = ctx.txns.findById(user.getId(), id);
            if (existing.isEmpty()) {
                redirectWithNotice(ex, "/transactions", "error", "Transaction not found.");
                return;
            }
            Map<String, String> form = formParams(ex);
            try {
                Transaction t = buildFromForm(user, form, existing.get());
                ctx.txns.update(t);
                ActivityLogService.get().log(user.getId(), "TXN_UPDATE", "Updated " + t.describe());
                redirectWithNotice(ex, "/transactions", "success", "Transaction updated.");
            } catch (InvalidAmountException | ValidationException e) {
                form(ex, user, existing.get(), e.getMessage());
            }
        }

        private void delete(HttpExchange ex, User user, long id) throws Exception {
            Result<Transaction> result = ctx.undo.delete(user.getId(), id);
            if (result.isOk()) {
                redirectWithNotice(ex, "/transactions", "success",
                        "Deleted. You can undo this from the dashboard or here.");
            } else {
                redirectWithNotice(ex, "/transactions", "error", result.getError());
            }
        }

        private void undo(HttpExchange ex, User user) throws Exception {
            Result<Transaction> result = ctx.undo.undo(user.getId());
            redirectWithNotice(ex, "/transactions",
                    result.isOk() ? "success" : "error",
                    result.isOk() ? "Restored: " + result.getValue().describe() : result.getError());
        }

        /** Validate the form and build an Expense/Income (Week 4 validation). */
        private Transaction buildFromForm(User user, Map<String, String> form, Transaction existing)
                throws Exception {
            String type = param(form, "type");
            if (!type.equals("expense") && !type.equals("income")) {
                throw new ValidationException("Please choose expense or income.");
            }
            BigDecimal amount = Validation.parseAmount(form.get("amount"));
            String currency = param(form, "currency").toUpperCase();
            if (!ConverterFactory.codes().contains(currency)) {
                throw new ValidationException("Unknown currency.");
            }
            LocalDate date = Validation.parseDate(form.get("date"));
            Long categoryId = parseLongOrNull(param(form, "category_id"));
            if (categoryId != null
                    && ctx.categories.findById(user.getId(), categoryId).isEmpty()) {
                throw new ValidationException("That category doesn't exist.");
            }
            String note = Validation.optionalText(form.get("note"), 255);
            String method = Validation.optionalText(form.get("payment_method"), 40);

            Transaction t = "income".equals(type)
                    ? new Income(user.getId(), categoryId, amount, currency, date, note, method)
                    : new Expense(user.getId(), categoryId, amount, currency, date, note, method);
            if (existing != null) {
                t.setId(existing.getId());
            }
            return t;
        }

        // ---- small helpers -----------------------------------------------------

        private String categoryOptions(List<Category> categories, Long selected) {
            StringBuilder sb = new StringBuilder();
            for (Category c : categories) {
                sb.append("<option value=\"").append(c.getId()).append("\"")
                  .append(selected != null && selected == c.getId() ? " selected" : "")
                  .append(">").append(Html.esc(c.getName())).append("</option>");
            }
            return sb.toString();
        }

        private String emptyToNull(String s) {
            return (s == null || s.isBlank()) ? null : s.trim();
        }

        private Long parseLongOrNull(String s) {
            if (s == null || s.isBlank()) return null;
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private LocalDate parseDateOrNull(String s) {
            if (s == null || s.isBlank()) return null;
            try {
                return LocalDate.parse(s.trim());
            } catch (Exception e) {
                return null;
            }
        }
    }
}
