package com.expensetracker.web.pages;

import com.expensetracker.model.Category;
import com.expensetracker.model.User;
import com.expensetracker.service.ActivityLogService;
import com.expensetracker.util.Html;
import com.expensetracker.util.Validation;
import com.expensetracker.exception.ValidationException;
import com.expensetracker.web.AppContext;
import com.expensetracker.web.BaseHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Custom categories.
 *   GET  /categories          list + add form
 *   POST /categories          create
 *   POST /categories/delete   delete (custom ones only)
 */
public final class CategoryPages {

    private CategoryPages() {
    }

    public static class CategoryHandler extends BaseHandler {
        public CategoryHandler(AppContext ctx) {
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
                String sub = ex.getRequestURI().getPath().substring("/categories".length());

                if ("/delete".equals(sub) && "POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    Map<String, String> form = formParams(ex);
                    long id = Long.parseLong(param(form, "id"));
                    boolean deleted = ctx.categories.delete(user.getId(), id);
                    redirectWithNotice(ex, "/categories",
                            deleted ? "success" : "error",
                            deleted ? "Category deleted." : "Can't delete that category.");
                    return;
                }
                if ((sub.isEmpty() || "/".equals(sub))
                        && "POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    Map<String, String> form = formParams(ex);
                    try {
                        String name = Validation.requireText(form.get("name"), "a category name", 60);
                        String kind = param(form, "kind");
                        if (!kind.equals("expense") && !kind.equals("income") && !kind.equals("both")) {
                            kind = "both";
                        }
                        if (ctx.categories.findByName(user.getId(), name).isPresent()) {
                            throw new ValidationException("You already have a category called '" + name + "'.");
                        }
                        Category c = new Category(user.getId(), name, kind, false);
                        ctx.categories.create(c);
                        ActivityLogService.get().log(user.getId(), "CATEGORY_ADD",
                                "Created category " + name);
                        redirectWithNotice(ex, "/categories", "success",
                                "Category '" + name + "' created.");
                    } catch (ValidationException e) {
                        redirectWithNotice(ex, "/categories", "error", e.getMessage());
                    }
                    return;
                }
                page(ex, user);
            } catch (Exception e) {
                serverError(ex, e);
            }
        }

        private void page(HttpExchange ex, User user) throws Exception {
            Map<String, String> q = queryParams(ex);
            List<Category> categories = ctx.categories.listByUser(user.getId());

            StringBuilder rows = new StringBuilder();
            for (Category c : categories) {
                rows.append("<tr><td>").append(Html.esc(c.getName())).append("</td>")
                    .append("<td>").append(Html.esc(c.getKind())).append("</td>")
                    .append("<td>").append(c.isDefault()
                            ? "<span class=\"pill\">default</span>"
                            : "<span class=\"pill pill-custom\">custom</span>").append("</td>")
                    .append("<td class=\"row-actions\">");
                if (!c.isDefault()) {
                    rows.append("<form method=\"post\" action=\"/categories/delete\" class=\"inline-form\">")
                        .append("<input type=\"hidden\" name=\"id\" value=\"").append(c.getId()).append("\">")
                        .append("<button class=\"linklike\" type=\"submit\">Delete</button></form>");
                } else {
                    rows.append("<span class=\"muted\">—</span>");
                }
                rows.append("</td></tr>");
            }

            String body = flash(q) + """
                    <section class="page-head">
                      <div><p class="kicker">Organise your money</p><h1>Categories</h1></div>
                    </section>
                    <div class="grid-2">
                      <section class="card">
                        <h2>All categories</h2>
                        <table class="table"><thead><tr>
                          <th>Name</th><th>Used for</th><th>Type</th><th></th>
                        </tr></thead><tbody>%s</tbody></table>
                        <p class="muted">Default categories can't be deleted; your custom ones can.</p>
                      </section>
                      <section class="card">
                        <h2>New category</h2>
                        <form method="post" action="/categories" class="form">
                          <label>Name<input type="text" name="name" required maxlength="60"
                            placeholder="e.g. Subscriptions"></label>
                          <label>Used for
                            <select name="kind">
                              <option value="both">Both income &amp; expenses</option>
                              <option value="expense">Expenses only</option>
                              <option value="income">Income only</option>
                            </select>
                          </label>
                          <button class="btn btn-primary" type="submit">Create category</button>
                        </form>
                      </section>
                    </div>
                    """.formatted(rows.toString());

            html(ex, 200, Html.page("Categories", themeFor(ex, user), "categories",
                    user.getName(), body));
        }
    }
}
