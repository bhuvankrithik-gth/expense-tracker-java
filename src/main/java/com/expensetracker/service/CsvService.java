package com.expensetracker.service;

import com.expensetracker.dao.CategoryDao;
import com.expensetracker.dao.TransactionDao;
import com.expensetracker.exception.InvalidAmountException;
import com.expensetracker.exception.ValidationException;
import com.expensetracker.model.Category;
import com.expensetracker.model.Expense;
import com.expensetracker.model.Income;
import com.expensetracker.model.Transaction;
import com.expensetracker.util.Result;
import com.expensetracker.util.Validation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Week 6 — File I/O: CSV export and import of a user's transactions.
 * Columns: type,amount,currency,date,category,note,payment_method
 * Every imported row is validated; bad rows are skipped and reported.
 */
public class CsvService {

    private final TransactionDao txnDao = new TransactionDao();
    private final CategoryDao categoryDao = new CategoryDao();

    /** Build the CSV text for all of a user's transactions. */
    public String exportCsv(long userId) throws Exception {
        List<Transaction> txns = txnDao.listAll(userId);
        StringBuilder sb = new StringBuilder("type,amount,currency,date,category,note,payment_method\n");
        for (Transaction t : txns) {
            sb.append(t.getType()).append(',')
              .append(t.getAmount().toPlainString()).append(',')
              .append(csvCell(t.getCurrency())).append(',')
              .append(t.getDate()).append(',')
              .append(csvCell(t.getCategoryName() == null ? "" : t.getCategoryName())).append(',')
              .append(csvCell(t.getNote() == null ? "" : t.getNote())).append(',')
              .append(csvCell(t.getPaymentMethod() == null ? "" : t.getPaymentMethod()))
              .append('\n');
        }
        return sb.toString();
    }

    /** Quote a cell if it contains a comma, quote or newline (minimal CSV escaping). */
    private String csvCell(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** Result of an import: how many rows were added and which were skipped. */
    public record ImportSummary(int imported, int skipped, List<String> errors) {
    }

    /**
     * Parse uploaded CSV text and insert the valid rows.
     * Unknown categories are created on the fly as custom categories.
     */
    public Result<ImportSummary> importCsv(long userId, String csvText) {
        int imported = 0, skipped = 0;
        List<String> errors = new java.util.ArrayList<>();
        try {
            List<Category> categories = categoryDao.listByUser(userId);
            String[] lines = csvText.split("\\r?\\n");
            int start = 0;
            if (lines.length > 0 && lines[0].trim().toLowerCase().startsWith("type,")) {
                start = 1; // skip header row
            }
            for (int i = start; i < lines.length; i++) {
                String line = lines[i];
                if (line.isBlank()) {
                    continue;
                }
                try {
                    importRow(userId, categories, parseRow(line));
                    imported++;
                } catch (ValidationException | InvalidAmountException e) {
                    skipped++;
                    errors.add("Row " + (i + 1) + ": " + e.getMessage());
                }
            }
            return Result.ok(new ImportSummary(imported, skipped, errors));
        } catch (Exception e) {
            return Result.fail("Could not read the CSV file: " + e.getMessage());
        }
    }

    /** Minimal CSV row parser that honours double-quoted cells. */
    private List<String> parseRow(String line) {
        List<String> cells = new java.util.ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++; // escaped quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }

    private void importRow(long userId, List<Category> categories, List<String> cells)
            throws Exception {
        if (cells.size() < 7) {
            throw new ValidationException("expected 7 columns, found " + cells.size());
        }
        String type = cells.get(0).trim().toLowerCase();
        if (!type.equals("expense") && !type.equals("income")) {
            throw new ValidationException("type must be 'expense' or 'income'");
        }
        BigDecimal amount = Validation.parseAmount(cells.get(1));
        String currency = cells.get(2).trim().toUpperCase();
        if (!ConverterFactory.codes().contains(currency)) {
            throw new ValidationException("unknown currency '" + cells.get(2).trim() + "'");
        }
        LocalDate date = Validation.parseDate(cells.get(3));
        String categoryName = cells.get(4).trim();
        Long categoryId = null;
        if (!categoryName.isEmpty()) {
            Optional<Category> existing = categories.stream()
                    .filter(c -> c.getName().equalsIgnoreCase(categoryName)).findFirst();
            if (existing.isPresent()) {
                categoryId = existing.get().getId();
            } else {
                // Auto-create unknown categories so no data is lost.
                Category created = new Category(userId, categoryName, type, false);
                categoryDao.create(created);
                categories.add(created);
                categoryId = created.getId();
            }
        }
        String note = Validation.optionalText(cells.get(5), 255);
        String method = Validation.optionalText(cells.get(6), 40);

        Transaction t = type.equals("income")
                ? new Income(userId, categoryId, amount, currency, date, note, method)
                : new Expense(userId, categoryId, amount, currency, date, note, method);
        txnDao.create(t);
    }
}
