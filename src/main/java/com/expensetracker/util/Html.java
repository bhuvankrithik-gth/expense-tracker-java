package com.expensetracker.util;

/**
 * HTML helpers: escaping (XSS protection) and the shared page shell.
 * Every page is generated from Java text blocks / StringBuilder templates —
 * no JSP, no template engine (per the project constraints).
 */
public final class Html {

    private Html() {
    }

    /**
     * Escape user-controlled text before embedding it in HTML.
     * SECURITY: prevents stored XSS from notes, names, etc.
     */
    public static String esc(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /** Escape text for use inside an HTML attribute value. */
    public static String attr(String s) {
        return esc(s);
    }

    /**
     * Full page shell: head (fonts via CSS link only, no JS), nav, content.
     * @param title   page title (escaped by caller contract — pass plain text)
     * @param theme   "light" or "dark" (CSS variables switch the palette)
     * @param active  nav key to highlight, or null
     * @param userName logged-in user's name, or null for public pages
     * @param body    already-built inner HTML
     */
    public static String page(String title, String theme, String active,
                              String userName, String body) {
        String nav = userName == null ? publicNav(active) : appNav(active, userName);
        return """
                <!DOCTYPE html>
                <html lang="en" data-theme="%s">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s · Ledgerline</title>
                  <link rel="preconnect" href="https://fonts.googleapis.com">
                  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                  <link href="https://fonts.googleapis.com/css2?family=Fraunces:opsz,wght@9..144,500;9..144,600;9..144,700&family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
                  <link rel="stylesheet" href="/static/style.css">
                </head>
                <body>
                  <header class="site-header">
                    <div class="wrap header-inner">
                      <a class="brand" href="%s"><span class="brand-mark">◈</span> Ledgerline</a>
                      %s
                    </div>
                  </header>
                  <main class="wrap">
                %s
                  </main>
                  <footer class="site-footer">
                    <div class="wrap">
                      <p>Ledgerline — a plain-Java expense tracker. Built with Java 17, HttpServer &amp; JDBC.</p>
                    </div>
                  </footer>
                </body>
                </html>
                """.formatted(esc(theme), esc(title), userName == null ? "/" : "/dashboard", nav, body);
    }

    private static String publicNav(String active) {
        return """
                <nav class="nav">
                  <a href="/login" class="%s">Log in</a>
                  <a href="/signup" class="btn btn-primary %s">Sign up</a>
                </nav>""".formatted(navClass(active, "login"), navClass(active, "signup"));
    }

    private static String appNav(String active, String userName) {
        return """
                <nav class="nav">
                  <a href="/dashboard" class="%s">Dashboard</a>
                  <a href="/transactions" class="%s">Transactions</a>
                  <a href="/budgets" class="%s">Budgets</a>
                  <a href="/categories" class="%s">Categories</a>
                  <a href="/reports" class="%s">Reports</a>
                  <a href="/settings" class="%s">Settings</a>
                  <span class="nav-user">%s</span>
                  <a href="/logout" class="btn btn-ghost">Log out</a>
                </nav>""".formatted(
                navClass(active, "dashboard"), navClass(active, "transactions"),
                navClass(active, "budgets"), navClass(active, "categories"),
                navClass(active, "reports"), navClass(active, "settings"),
                esc(userName));
    }

    private static String navClass(String active, String key) {
        return key.equals(active) ? "active" : "";
    }

    /** Dismissible-style notice banner (info / success / warn / error). */
    public static String notice(String kind, String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        return """
                <div class="notice notice-%s" role="alert">%s</div>
                """.formatted(esc(kind), esc(message));
    }

    /** Friendly empty-state card used by list pages. */
    public static String emptyState(String heading, String text, String ctaHref, String ctaLabel) {
        String cta = (ctaHref != null)
                ? "<a class=\"btn btn-primary\" href=\"" + attr(ctaHref) + "\">" + esc(ctaLabel) + "</a>"
                : "";
        return """
                <div class="empty">
                  <div class="empty-mark">◇</div>
                  <h3>%s</h3>
                  <p>%s</p>
                  %s
                </div>
                """.formatted(esc(heading), esc(text), cta);
    }
}
