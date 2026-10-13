package com.expensetracker.web.pages;

import com.expensetracker.model.User;
import com.expensetracker.service.ActivityLogService;
import com.expensetracker.util.Html;
import com.expensetracker.util.Result;
import com.expensetracker.web.AppContext;
import com.expensetracker.web.BaseHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;

/**
 * Sign-up, log-in and log-out. Sessions are created in the SessionStore
 * (HashMap) and handed to the browser as an HttpOnly cookie.
 */
public final class AuthPages {

    private AuthPages() {
    }

    public static class SignupHandler extends BaseHandler {
        public SignupHandler(AppContext ctx) {
            super(ctx);
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    Map<String, String> form = formParams(ex);
                    Result<User> result = ctx.auth.signup(
                            param(form, "name"), param(form, "email"), form.get("password"));
                    if (result.isOk()) {
                        User user = result.getValue();
                        setSessionCookie(ex, ctx.sessions.create(user.getId(), user.getName()));
                        ActivityLogService.get().log(user.getId(), "SIGNUP", "New account created");
                        redirectWithNotice(ex, "/dashboard", "success",
                                "Welcome, " + user.getName() + "! Your account is ready.");
                    } else {
                        html(ex, 200, Html.page("Sign up", themeFor(ex, null), "signup", null,
                                signupForm(result.getError(), param(form, "name"), param(form, "email"))));
                    }
                    return;
                }
                if (currentUser(ex).isPresent()) {
                    redirect(ex, "/dashboard");
                    return;
                }
                html(ex, 200, Html.page("Sign up", themeFor(ex, null), "signup", null,
                        signupForm(null, "", "")));
            } catch (Exception e) {
                serverError(ex, e);
            }
        }
    }

    public static class LoginHandler extends BaseHandler {
        public LoginHandler(AppContext ctx) {
            super(ctx);
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    Map<String, String> form = formParams(ex);
                    Result<User> result = ctx.auth.login(param(form, "email"), form.get("password"));
                    if (result.isOk()) {
                        User user = result.getValue();
                        setSessionCookie(ex, ctx.sessions.create(user.getId(), user.getName()));
                        ActivityLogService.get().log(user.getId(), "LOGIN", "Logged in");
                        redirectWithNotice(ex, "/dashboard", "success",
                                "Welcome back, " + user.getName() + "!");
                    } else {
                        html(ex, 200, Html.page("Log in", themeFor(ex, null), "login", null,
                                loginForm(result.getError(), param(form, "email"))));
                    }
                    return;
                }
                if (currentUser(ex).isPresent()) {
                    redirect(ex, "/dashboard");
                    return;
                }
                Map<String, String> query = queryParams(ex);
                html(ex, 200, Html.page("Log in", themeFor(ex, null), "login", null,
                        flash(query) + loginForm(null, "")));
            } catch (Exception e) {
                serverError(ex, e);
            }
        }
    }

    public static class LogoutHandler extends BaseHandler {
        public LogoutHandler(AppContext ctx) {
            super(ctx);
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            ctx.sessions.invalidate(cookie(ex, "ET_SESSION"));
            clearSessionCookie(ex);
            redirectWithNotice(ex, "/login", "info", "You've been logged out. See you soon!");
        }
    }

    // ---- forms --------------------------------------------------------------

    private static String signupForm(String error, String name, String email) {
        return """
                <section class="card narrow">
                  <p class="kicker">Create your account</p>
                  <h1>Start tracking today</h1>
                %s
                  <form method="post" action="/signup" class="form">
                    <label>Name
                      <input type="text" name="name" required maxlength="100" value="%s" autocomplete="name">
                    </label>
                    <label>Email
                      <input type="email" name="email" required maxlength="190" value="%s" autocomplete="email">
                    </label>
                    <label>Password <span class="muted">(min 6 characters)</span>
                      <input type="password" name="password" required minlength="6" autocomplete="new-password">
                    </label>
                    <button class="btn btn-primary btn-lg" type="submit">Create account</button>
                  </form>
                  <p class="muted">Already have an account? <a href="/login">Log in</a></p>
                </section>
                """.formatted(Html.notice("error", error), Html.attr(name), Html.attr(email));
    }

    private static String loginForm(String error, String email) {
        return """
                <section class="card narrow">
                  <p class="kicker">Welcome back</p>
                  <h1>Log in</h1>
                %s
                  <form method="post" action="/login" class="form">
                    <label>Email
                      <input type="email" name="email" required maxlength="190" value="%s" autocomplete="email">
                    </label>
                    <label>Password
                      <input type="password" name="password" required autocomplete="current-password">
                    </label>
                    <button class="btn btn-primary btn-lg" type="submit">Log in</button>
                  </form>
                  <p class="muted">New here? <a href="/signup">Create an account</a></p>
                </section>
                """.formatted(Html.notice("error", error), Html.attr(email));
    }
}
