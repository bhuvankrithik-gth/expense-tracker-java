package com.expensetracker.web;

import com.expensetracker.model.User;
import com.expensetracker.service.SessionStore;
import com.expensetracker.util.Html;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Shared HTTP plumbing for every page handler: parameter parsing, cookies,
 * session lookup, HTML responses and redirects. Keeps the page handlers
 * focused on their own logic.
 */
public abstract class BaseHandler implements HttpHandler {

    protected final AppContext ctx;

    protected BaseHandler(AppContext ctx) {
        this.ctx = ctx;
    }

    // ---- request parsing ---------------------------------------------------

    /** Parse "a=1&b=2" style data (query string or form body). */
    protected Map<String, String> parseParams(String raw) {
        Map<String, String> params = new HashMap<>();
        if (raw == null || raw.isBlank()) {
            return params;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            params.put(decode(key), decode(value));
        }
        return params;
    }

    private String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    protected Map<String, String> queryParams(HttpExchange ex) {
        return parseParams(ex.getRequestURI().getRawQuery());
    }

    /** Read an application/x-www-form-urlencoded POST body. */
    protected Map<String, String> formParams(HttpExchange ex) throws IOException {
        byte[] body = ex.getRequestBody().readAllBytes();
        return parseParams(new String(body, StandardCharsets.UTF_8));
    }

    protected String param(Map<String, String> params, String name) {
        String v = params.get(name);
        return v == null ? "" : v.trim();
    }

    // ---- cookies & sessions -------------------------------------------------

    protected String cookie(HttpExchange ex, String name) {
        String header = ex.getRequestHeaders().getFirst("Cookie");
        if (header == null) {
            return null;
        }
        for (String part : header.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals(name)) {
                return decode(kv[1].trim());
            }
        }
        return null;
    }

    /** The logged-in user, or empty when not logged in / session expired. */
    protected Optional<User> currentUser(HttpExchange ex) {
        SessionStore.Session session = ctx.sessions.get(cookie(ex, "ET_SESSION"));
        if (session == null) {
            return Optional.empty();
        }
        try {
            return ctx.users.findById(session.userId());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    protected void setSessionCookie(HttpExchange ex, String token) {
        // HttpOnly: JavaScript cannot read it (XSS protection). SameSite=Lax: CSRF mitigation.
        ex.getResponseHeaders().add("Set-Cookie",
                "ET_SESSION=" + token + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=1800");
    }

    protected void clearSessionCookie(HttpExchange ex) {
        ex.getResponseHeaders().add("Set-Cookie",
                "ET_SESSION=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
    }

    protected void setThemeCookie(HttpExchange ex, String theme) {
        ex.getResponseHeaders().add("Set-Cookie",
                "et_theme=" + theme + "; Path=/; SameSite=Lax; Max-Age=31536000");
    }

    // ---- responses ----------------------------------------------------------

    protected void html(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    protected void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().set("Location", location);
        ex.sendResponseHeaders(303, -1); // 303: follow with GET (PRG pattern)
        ex.close();
    }

    /** Redirect with a one-time notice banner: ?msg=..&kind=.. (URL-encoded). */
    protected void redirectWithNotice(HttpExchange ex, String location, String kind, String message)
            throws IOException {
        String sep = location.contains("?") ? "&" : "?";
        redirect(ex, location + sep + "kind=" + urlEncode(kind) + "&msg=" + urlEncode(message));
    }

    private String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Render the ?msg=&kind= flash banner, if present. */
    protected String flash(Map<String, String> query) {
        String msg = query.get("msg");
        String kind = query.getOrDefault("kind", "info");
        return Html.notice(kind, msg == null ? null : decode(msg));
    }

    /** Theme for the page shell: the user's saved theme, else the cookie, else light. */
    protected String themeFor(HttpExchange ex, User user) {
        if (user != null && user.getTheme() != null) {
            return user.getTheme();
        }
        String cookieTheme = cookie(ex, "et_theme");
        return "dark".equals(cookieTheme) ? "dark" : "light";
    }

    /** Send a 500 page for unexpected errors (never leak stack traces). */
    protected void serverError(HttpExchange ex, Exception e) {
        System.out.println("[web] ERROR " + ex.getRequestURI() + ": " + e);
        try {
            String body = Html.page("Something went wrong", "light", null, null,
                    """
                    <section class="card narrow">
                      <h1>Something went wrong</h1>
                      <p class="muted">An unexpected error occurred. Please try again, \
                      or go back to the <a href="/dashboard">dashboard</a>.</p>
                    </section>""");
            html(ex, 500, body);
        } catch (IOException io) {
            // nothing more we can do
        }
    }
}
