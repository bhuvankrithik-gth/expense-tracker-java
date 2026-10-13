package com.expensetracker.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Week 9 — HAND-WRITTEN doubly linked list (no java.util.LinkedList used here).
 * Stores recently deleted items so the user can "undo" a delete.
 * Supports: insert (push at the tail), delete (pop from the tail),
 * and display (most-recent-first traversal).
 *
 * @param <T> the type of stored item (here: deleted {@code Transaction}s)
 */
public class UndoList<T> {

    /** Doubly linked node — each node points both ways. */
    private static class Node<T> {
        T data;
        Node<T> prev;
        Node<T> next;

        Node(T data) {
            this.data = data;
        }
    }

    private Node<T> head; // oldest
    private Node<T> tail; // newest (most recently deleted)
    private int size;

    /** Insert: push an item at the tail (the "most recent" end). */
    public synchronized void insert(T item) {
        Node<T> node = new Node<>(item);
        if (tail == null) {
            head = tail = node;
        } else {
            node.prev = tail;
            tail.next = node;
            tail = node;
        }
        size++;
    }

    /**
     * Delete: pop the most recently inserted item (LIFO undo order).
     * Returns null when the history is empty.
     */
    public synchronized T delete() {
        if (tail == null) {
            return null;
        }
        T data = tail.data;
        tail = tail.prev;
        if (tail == null) {
            head = null;
        } else {
            tail.next = null;
        }
        size--;
        return data;
    }

    /** Peek at the most recent item without removing it. */
    public synchronized T peek() {
        return tail == null ? null : tail.data;
    }

    public synchronized int size() {
        return size;
    }

    public synchronized boolean isEmpty() {
        return size == 0;
    }

    /** Display: walk from tail to head, most-recent-first. */
    public synchronized List<T> display() {
        List<T> items = new ArrayList<>();
        Node<T> current = tail;
        while (current != null) {
            items.add(current.data);
            current = current.prev;
        }
        return items;
    }
}
