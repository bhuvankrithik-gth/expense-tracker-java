package com.expensetracker.web;

import com.expensetracker.dao.Db;
import com.expensetracker.service.ActivityLogService;
import com.expensetracker.web.pages.AlertPages;
import com.expensetracker.web.pages.AuthPages;
import com.expensetracker.web.pages.BudgetPages;
import com.expensetracker.web.pages.CategoryPages;
import com.expensetracker.web.pages.DashboardPages;
import com.expensetracker.web.pages.ReportPages;
import com.expensetracker.web.pages.SettingsPages;
import com.expensetracker.web.pages.TransactionPages;

import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Application entry point. Wires everything together with plain Java:
 * <ul>
 *   <li>Week 12 — {@link Db#initSchema()} ensures the MySQL tables exist.</li>
 *   <li>Week 5 — the HttpServer runs on a FIXED THREAD POOL; background
 *       threads handle session cleanup, activity-log writing and budget alerts.</li>
 *   <li>Week 2 — handlers are plain objects sharing one {@link AppContext}.</li>
 * </ul>
 * Run: {@code java -jar target/expense-tracker-1.0.0.jar} (env vars for DB config).
 */
public class App {

    public static void main(String[] args) throws Exception {
        int port = port();

        // 1. Database tables (CREATE TABLE IF NOT EXISTS from schema.sql).
        Db.initSchema();

        // 2. Shared services + DAOs.
        File backupDir = new File("backups");
        AppContext ctx = new AppContext(backupDir);

        // 3. Week 5 — background threads (all daemon: JVM can exit cleanly).
        ctx.sessions.startCleanupThread();   // expire old sessions
        ActivityLogService.get().start();    // producer-consumer log writer
        ctx.watcher.start();                 // budget alert scanner

        // 4. HTTP server on a fixed thread pool (Week 5 multithreading).
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new LandingHandler(ctx));
        server.createContext("/signup", new AuthPages.SignupHandler(ctx));
        server.createContext("/login", new AuthPages.LoginHandler(ctx));
        server.createContext("/logout", new AuthPages.LogoutHandler(ctx));
        server.createContext("/dashboard", new DashboardPages.DashboardHandler(ctx));
        server.createContext("/transactions", new TransactionPages.TransactionHandler(ctx));
        server.createContext("/budgets", new BudgetPages.BudgetHandler(ctx));
        server.createContext("/categories", new CategoryPages.CategoryHandler(ctx));
        server.createContext("/reports", new ReportPages.ReportHandler(ctx));
        server.createContext("/settings", new SettingsPages.SettingsHandler(ctx));
        server.createContext("/alerts", new AlertPages.AlertHandler(ctx));
        server.createContext("/static", new StaticHandler());
        // Week 5: fixed pool of 16 worker threads serves all requests concurrently.
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();

        System.out.println("Ledgerline expense tracker running at http://localhost:" + port);
        System.out.println("DB: " + Db.jdbcUrl());
    }

    private static int port() {
        // Render / Railway inject PORT; APP_PORT is our own documented variable.
        String raw = System.getenv("PORT");
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("APP_PORT");
        }
        try {
            return raw == null || raw.isBlank() ? 8080 : Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 8080;
        }
    }
}
