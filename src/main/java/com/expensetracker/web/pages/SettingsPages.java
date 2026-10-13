package com.expensetracker.web.pages;

import com.expensetracker.model.User;
import com.expensetracker.service.ActivityLogService;
import com.expensetracker.service.ConverterFactory;
import com.expensetracker.service.CsvService;
import com.expensetracker.util.BackupFiles;
import com.expensetracker.util.Html;
import com.expensetracker.util.Result;
import com.expensetracker.web.AppContext;
import com.expensetracker.web.BaseHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Settings: display currency + theme, CSV export/import (Week 6),
 * backup &amp; restore via Java serialization (Week 7).
 *   GET  /settings
 *   POST /settings/preferences
 *   GET  /settings/export.csv
 *   POST /settings/import
 *   POST /settings/backup
 *   POST /settings/restore
 */
public final class SettingsPages {

    private SettingsPages() {
    }

    public static class SettingsHandler extends BaseHandler {
        public SettingsHandler(AppContext ctx) {
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
                String sub = ex.getRequestURI().getPath().substring("/settings".length());
                String method = ex.getRequestMethod();

                if ("/preferences".equals(sub) && "POST".equalsIgnoreCase(method)) {
                    savePreferences(ex, user);
                } else if ("/export.csv".equals(sub) && "GET".equalsIgnoreCase(method)) {
                    exportCsv(ex, user);
                } else if ("/import".equals(sub) && "POST".equalsIgnoreCase(method)) {
                    importCsv(ex, user);
                } else if ("/backup".equals(sub) && "POST".equalsIgnoreCase(method)) {
                    backup(ex, user);
                } else if ("/restore".equals(sub) && "POST".equalsIgnoreCase(method)) {
                    restore(ex, user);
                } else {
                    page(ex, user);
                }
            } catch (Exception e) {
                serverError(ex, e);
            }
        }

        private void savePreferences(HttpExchange ex, User user) throws Exception {
            Map<String, String> form = formParams(ex);
            String currency = param(form, "currency").toUpperCase();
            if (!ConverterFactory.codes().contains(currency)) {
                currency = "INR";
            }
            String theme = "dark".equals(param(form, "theme")) ? "dark" : "light";
            ctx.users.updatePreferences(user.getId(), currency, theme);
            setThemeCookie(ex, theme);
            ActivityLogService.get().log(user.getId(), "SETTINGS",
                    "Preferences updated (" + currency + "/" + theme + ")");
            redirectWithNotice(ex, "/settings", "success", "Preferences saved.");
        }

        private void exportCsv(HttpExchange ex, User user) throws Exception {
            String csv = ctx.csv.exportCsv(user.getId());
            byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/csv; charset=UTF-8");
            ex.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"ledgerline-transactions.csv\"");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
            ActivityLogService.get().log(user.getId(), "CSV_EXPORT", "Exported transactions");
        }

        private void importCsv(HttpExchange ex, User user) throws Exception {
            Map<String, String> form = formParams(ex);
            Result<CsvService.ImportSummary> r =
                    ctx.csv.importCsv(user.getId(), param(form, "csv"));
            if (r.isOk()) {
                CsvService.ImportSummary s = r.getValue();
                ActivityLogService.get().log(user.getId(), "CSV_IMPORT",
                        "Imported " + s.imported() + ", skipped " + s.skipped());
                String msg = "Imported " + s.imported() + " transaction(s)"
                        + (s.skipped() > 0 ? ", skipped " + s.skipped() + " bad row(s)." : ".");
                if (!s.errors().isEmpty() && s.errors().size() <= 5) {
                    msg += " " + String.join(" ", s.errors());
                }
                redirectWithNotice(ex, "/settings", s.skipped() > 0 ? "warn" : "success", msg);
            } else {
                redirectWithNotice(ex, "/settings", "error", r.getError());
            }
        }

        private void backup(HttpExchange ex, User user) throws Exception {
            Result<String> r = ctx.backups.backup(user.getId());
            if (r.isOk()) {
                ActivityLogService.get().log(user.getId(), "BACKUP", "Backup " + r.getValue());
                redirectWithNotice(ex, "/settings", "success",
                        "Backup saved as " + r.getValue());
            } else {
                redirectWithNotice(ex, "/settings", "error", r.getError());
            }
        }

        private void restore(HttpExchange ex, User user) throws Exception {
            Map<String, String> form = formParams(ex);
            String file = param(form, "file");
            if (file.isEmpty()) {
                redirectWithNotice(ex, "/settings", "error", "Choose a backup file first.");
                return;
            }
            Result<Integer> r = ctx.backups.restore(user.getId(), file);
            if (r.isOk()) {
                ActivityLogService.get().log(user.getId(), "RESTORE",
                        "Restored " + r.getValue() + " transactions from " + file);
                redirectWithNotice(ex, "/settings", "success",
                        "Restored " + r.getValue() + " transactions from backup.");
            } else {
                redirectWithNotice(ex, "/settings", "error", r.getError());
            }
        }

        private void page(HttpExchange ex, User user) throws Exception {
            Map<String, String> q = queryParams(ex);
            List<BackupFiles.BackupInfo> backups = ctx.backups.list(user.getId());

            StringBuilder currencyOptions = new StringBuilder();
            for (String c : ConverterFactory.codes()) {
                currencyOptions.append("<option value=\"").append(c).append("\"")
                        .append(c.equals(user.getCurrency()) ? " selected" : "").append(">")
                        .append(c).append("</option>");
            }

            StringBuilder backupRows = new StringBuilder();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
                    .withZone(ZoneId.systemDefault());
            for (BackupFiles.BackupInfo b : backups) {
                backupRows.append("<tr><td>").append(Html.esc(b.fileName())).append("</td>")
                    .append("<td class=\"num\">").append(String.format("%.1f KB", b.sizeBytes() / 1024.0))
                    .append("</td><td>").append(fmt.format(Instant.ofEpochMilli(b.modifiedAt())))
                    .append("</td><td class=\"row-actions\">")
                    .append("<form method=\"post\" action=\"/settings/restore\" class=\"inline-form\">")
                    .append("<input type=\"hidden\" name=\"file\" value=\"").append(Html.attr(b.fileName())).append("\">")
                    .append("<button class=\"linklike\" type=\"submit\">Restore</button></form>")
                    .append("</td></tr>");
            }

            String body = flash(q) + """
                    <section class="page-head">
                      <div><p class="kicker">Make it yours</p><h1>Settings</h1></div>
                    </section>
                    <div class="grid-2">
                      <section class="card">
                        <h2>Preferences</h2>
                        <form method="post" action="/settings/preferences" class="form">
                          <label>Display currency
                            <select name="currency">%s</select>
                          </label>
                          <p class="muted">Totals and charts are converted into this currency.
                          Exchange rates are fixed demo rates (see README).</p>
                          <label>Theme
                            <select name="theme">
                              <option value="light"%s>Light</option>
                              <option value="dark"%s>Dark</option>
                            </select>
                          </label>
                          <button class="btn btn-primary" type="submit">Save preferences</button>
                        </form>
                      </section>
                      <section class="card">
                        <h2>Export / import (CSV)</h2>
                        <p><a class="btn btn-ghost" href="/settings/export.csv">⬇ Download all transactions (CSV)</a></p>
                        <form method="post" action="/settings/import" class="form">
                          <label>Paste CSV to import
                            <textarea name="csv" rows="6" placeholder="type,amount,currency,date,category,note,payment_method&#10;expense,250,INR,2026-10-13,Food,Lunch,UPI"></textarea>
                          </label>
                          <p class="muted">Header row is optional. Bad rows are skipped and reported;
                          unknown categories are created automatically.</p>
                          <button class="btn btn-primary" type="submit">Import CSV</button>
                        </form>
                      </section>
                    </div>
                    <section class="card">
                      <div class="card-head"><h2>Backup &amp; restore</h2>
                        <form method="post" action="/settings/backup" class="inline-form">
                          <button class="btn btn-primary" type="submit">Create backup now</button>
                        </form>
                      </div>
                      <p class="muted">Backups use Java serialization — your whole dataset is written
                      to a <code>.ser</code> file. Restoring <strong>replaces</strong> your current data.</p>
                      %s
                    </section>
                    """.formatted(currencyOptions,
                    "light".equals(user.getTheme()) ? " selected" : "",
                    "dark".equals(user.getTheme()) ? " selected" : "",
                    backupRows.isEmpty()
                            ? "<p class=\"muted\">No backups yet. Create one above.</p>"
                            : "<table class=\"table\"><thead><tr><th>File</th>"
                              + "<th class=\"num\">Size</th><th>Created</th><th></th>"
                              + "</tr></thead><tbody>" + backupRows + "</tbody></table>");

            html(ex, 200, Html.page("Settings", themeFor(ex, user), "settings",
                    user.getName(), body));
        }
    }
}
