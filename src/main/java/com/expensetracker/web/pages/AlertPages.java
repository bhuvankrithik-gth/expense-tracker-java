package com.expensetracker.web.pages;

import com.expensetracker.model.User;
import com.expensetracker.web.AppContext;
import com.expensetracker.web.BaseHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Optional;

/**
 * POST /alerts/read — mark all budget alerts as read.
 */
public final class AlertPages {

    private AlertPages() {
    }

    public static class AlertHandler extends BaseHandler {
        public AlertHandler(AppContext ctx) {
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
                if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    ctx.alerts.markAllRead(userOpt.get().getId());
                }
                redirect(ex, "/dashboard");
            } catch (Exception e) {
                serverError(ex, e);
            }
        }
    }
}
