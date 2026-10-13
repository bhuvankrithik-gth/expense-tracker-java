package com.expensetracker;

import com.expensetracker.util.UndoList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Week-9 hand-written doubly linked list (undo history).
 */
class UndoListTest {

    @Test
    void insertThenDeleteIsLifo() {
        UndoList<String> list = new UndoList<>();
        list.insert("first");
        list.insert("second");
        list.insert("third");

        assertEquals(3, list.size());
        assertEquals("third", list.delete());  // most recent first
        assertEquals("second", list.delete());
        assertEquals("first", list.delete());
        assertTrue(list.isEmpty());
    }

    @Test
    void deleteOnEmptyReturnsNull() {
        UndoList<String> list = new UndoList<>();
        assertNull(list.delete());
        assertNull(list.peek());
        assertEquals(0, list.size());
    }

    @Test
    void peekDoesNotRemove() {
        UndoList<String> list = new UndoList<>();
        list.insert("x");
        assertEquals("x", list.peek());
        assertEquals(1, list.size());
    }

    @Test
    void displayIsMostRecentFirst() {
        UndoList<Integer> list = new UndoList<>();
        list.insert(1);
        list.insert(2);
        list.insert(3);
        assertEquals(java.util.List.of(3, 2, 1), list.display());
    }

    @Test
    void listRemainsConsistentAfterMixedOps() {
        UndoList<String> list = new UndoList<>();
        list.insert("a");
        assertEquals("a", list.delete());
        list.insert("b");
        list.insert("c");
        assertEquals("c", list.delete());
        assertEquals(java.util.List.of("b"), list.display());
        assertEquals("b", list.delete());
        assertTrue(list.isEmpty());
    }
}
