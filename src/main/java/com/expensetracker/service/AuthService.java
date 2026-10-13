package com.expensetracker.service;

import com.expensetracker.dao.CategoryDao;
import com.expensetracker.dao.UserDao;
import com.expensetracker.exception.ValidationException;
import com.expensetracker.model.User;
import com.expensetracker.util.Passwords;
import com.expensetracker.util.Result;
import com.expensetracker.util.Validation;

import java.sql.SQLException;

/**
 * Sign-up / log-in logic. Passwords are hashed with PBKDF2 (never stored
 * plain); a new user gets the 7 default categories seeded automatically.
 */
public class AuthService {

    private final UserDao userDao = new UserDao();
    private final CategoryDao categoryDao = new CategoryDao();

    /** Register a new account. Returns the created user or a friendly error. */
    public Result<User> signup(String name, String email, String password) {
        try {
            String cleanName = Validation.requireText(name, "your name", 100);
            Validation.requireEmail(email);
            String cleanEmail = email.trim().toLowerCase();
            if (!Passwords.isStrongEnough(password)) {
                return Result.fail("Password must be at least 6 characters.");
            }
            if (userDao.findByEmail(cleanEmail).isPresent()) {
                return Result.fail("An account with that email already exists. Try logging in.");
            }
            User user = new User(cleanName, cleanEmail, Passwords.hash(password));
            userDao.create(user);
            categoryDao.ensureDefaults(user.getId()); // seed Food, Travel, ...
            return Result.ok(user);
        } catch (ValidationException e) {
            return Result.fail(e.getMessage());
        } catch (SQLException e) {
            return Result.fail("Database error during sign-up. Please try again.");
        }
    }

    /** Verify credentials. Returns the user or a friendly error. */
    public Result<User> login(String email, String password) {
        try {
            String cleanEmail = email == null ? "" : email.trim().toLowerCase();
            if (cleanEmail.isEmpty() || password == null || password.isEmpty()) {
                return Result.fail("Please enter your email and password.");
            }
            return userDao.findByEmail(cleanEmail)
                    .filter(u -> Passwords.verify(password, u.getPasswordHash()))
                    .map(Result::ok)
                    .orElse(Result.fail("Wrong email or password. Please try again."));
        } catch (SQLException e) {
            return Result.fail("Database error during login. Please try again.");
        }
    }
}
