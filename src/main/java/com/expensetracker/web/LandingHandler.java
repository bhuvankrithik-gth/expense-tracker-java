package com.expensetracker.web;

import com.expensetracker.util.Html;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

/**
 * GET / — public landing page. Logged-in users are bounced to the dashboard.
 */
public class LandingHandler extends BaseHandler {

    public LandingHandler(AppContext ctx) {
        super(ctx);
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                redirect(ex, "/");
                return;
            }
            if (currentUser(ex).isPresent()) {
                redirect(ex, "/dashboard");
                return;
            }
            String body = """
                    <section class="hero">
                      <p class="kicker">Plain Java · MySQL · No frameworks</p>
                      <h1>Know where every<br>rupee goes.</h1>
                      <p class="lede">Ledgerline is a personal expense tracker written in pure
                      Java 17 — its own HTTP server, plain JDBC, hand-drawn SVG charts.
                      Track income and expenses, set monthly budgets, and see your money
                      clearly.</p>
                      <div class="hero-cta">
                        <a class="btn btn-primary btn-lg" href="/signup">Start tracking — it's free</a>
                        <a class="btn btn-ghost btn-lg" href="/login">Log in</a>
                      </div>
                      <dl class="hero-stats">
                        <div><dt>Budgets</dt><dd>Monthly limits per category, with warnings</dd></div>
                        <div><dt>Reports</dt><dd>SVG charts drawn by Java — no JavaScript</dd></div>
                        <div><dt>Control</dt><dd>CSV export, backups, undo for deletes</dd></div>
                      </dl>
                    </section>
                    <section class="card">
                      <h2>How it works</h2>
                      <ol class="steps">
                        <li><strong>Create an account</strong> — your password is hashed with PBKDF2, never stored plain.</li>
                        <li><strong>Add transactions</strong> — income and expenses with categories, dates and notes.</li>
                        <li><strong>Set budgets</strong> — get warned the moment a category goes over for the month.</li>
                        <li><strong>Read the reports</strong> — category breakdowns and monthly trends, in your currency.</li>
                      </ol>
                    </section>
                    """;
            html(ex, 200, Html.page("Track every rupee", themeFor(ex, null), null, null, body));
        } catch (Exception e) {
            serverError(ex, e);
        }
    }
}
