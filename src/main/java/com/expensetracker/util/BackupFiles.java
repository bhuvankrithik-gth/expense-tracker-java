package com.expensetracker.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Week 6 — File I/O helper. Lists backup files with a hand-written
 * RECURSIVE directory walk (not Files.walk, so the recursion is visible
 * for the viva), newest first.
 */
public final class BackupFiles {

    private BackupFiles() {
    }

    /** Info about one backup file on disk. */
    public record BackupInfo(String fileName, long sizeBytes, long modifiedAt) {
    }

    /**
     * Recursively collect "*.ser" backup files belonging to a user.
     * @param rootDir the backups directory (created on demand)
     * @param userId  only files named "user_&lt;id&gt;_*.ser" are returned
     */
    public static List<BackupInfo> listForUser(File rootDir, long userId) {
        List<BackupInfo> found = new ArrayList<>();
        if (rootDir != null && rootDir.isDirectory()) {
            collectRecursive(rootDir, "user_" + userId + "_", found);
        }
        // Newest first — Comparator on the timestamp.
        found.sort(Comparator.comparingLong(BackupInfo::modifiedAt).reversed());
        return Collections.unmodifiableList(found);
    }

    /** The recursive step: descend into subdirectories, collect matching files. */
    private static void collectRecursive(File dir, String prefix, List<BackupInfo> out) {
        File[] entries = dir.listFiles();
        if (entries == null) {
            return; // not readable — stop this branch
        }
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collectRecursive(entry, prefix, out); // <-- recursion
            } else if (entry.isFile()
                    && entry.getName().startsWith(prefix)
                    && entry.getName().endsWith(".ser")) {
                out.add(new BackupInfo(entry.getName(), entry.length(), entry.lastModified()));
            }
        }
    }
}
