package com.expensetracker.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Week 7 — session store backed by a HashMap (ConcurrentHashMap for
 * thread safety, since the HttpServer thread pool touches it concurrently).
 * Sessions live in memory: token -> Session. Cookies are HttpOnly and
 * sessions expire after 30 minutes of inactivity (sliding window).
 */
public class SessionStore {

    /** One logged-in session. */
    public static final class Session {
        private final long userId;
        private final String userName;
        private volatile Instant lastSeen;

        Session(long userId, String userName) {
            this.userId = userId;
            this.userName = userName;
            this.lastSeen = Instant.now();
        }

        public long userId() { return userId; }
        public String userName() { return userName; }
    }

    private static final Duration TIMEOUT = Duration.ofMinutes(30);

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /** Create a session and return its token (stored in the HttpOnly cookie). */
    public String create(long userId, String userName) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(userId, userName));
        return token;
    }

    /** Look up a session; returns null when missing or expired. Refreshes expiry. */
    public Session get(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Session s = sessions.get(token);
        if (s == null) {
            return null;
        }
        if (Instant.now().isAfter(s.lastSeen.plus(TIMEOUT))) {
            sessions.remove(token); // lazy expiry
            return null;
        }
        s.lastSeen = Instant.now(); // sliding window
        return s;
    }

    /** Log out: drop the session. */
    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /** Background cleanup: sweep expired sessions every 10 minutes (Week 5 thread). */
    public void startCleanupThread() {
        Thread sweeper = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(Duration.ofMinutes(10).toMillis());
                    Instant cutoff = Instant.now().minus(TIMEOUT);
                    sessions.entrySet().removeIf(e -> e.getValue().lastSeen.isBefore(cutoff));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // preserve interrupt status
                }
            }
        }, "session-sweeper");
        sweeper.setDaemon(true);
        sweeper.start();
    }
}
