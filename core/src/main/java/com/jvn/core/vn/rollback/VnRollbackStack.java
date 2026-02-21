package com.jvn.core.vn.rollback;

import com.jvn.core.vn.VnState;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Manages a stack of rollback entries for VN state rewind.
 * Supports configurable max depth and provides forward/backward navigation.
 */
public final class VnRollbackStack {
    private static final int DEFAULT_MAX_ENTRIES = 100;

    private final Deque<VnRollbackEntry> history;
    private final Deque<VnRollbackEntry> future;
    private final int maxEntries;

    public VnRollbackStack() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public VnRollbackStack(int maxEntries) {
        this.maxEntries = Math.max(1, maxEntries);
        this.history = new ArrayDeque<>(this.maxEntries);
        this.future = new ArrayDeque<>();
    }

    /**
     * Push a new rollback entry onto the stack.
     * Clears any forward history (redo stack) since we've taken a new path.
     */
    public void push(VnRollbackEntry entry) {
        if (entry == null) return;

        // Clear forward history when a new entry is pushed
        future.clear();

        history.push(entry);

        // Trim if exceeding max entries
        while (history.size() > maxEntries) {
            // Remove oldest entry (from the tail)
            ((ArrayDeque<VnRollbackEntry>) history).removeLast();
        }
    }

    /**
     * Capture and push current state.
     */
    public void capture(VnState state, String speaker, String text) {
        push(VnRollbackEntry.capture(state, speaker, text));
    }

    /**
     * Roll back one step, returning the entry to restore.
     * The current state should be captured before calling this if forward navigation is desired.
     */
    public VnRollbackEntry rollback(VnRollbackEntry currentState) {
        if (history.isEmpty()) return null;

        // Save current state to future stack for potential forward navigation
        if (currentState != null) {
            future.push(currentState);
        }

        return history.pop();
    }

    /**
     * Roll forward one step (redo), returning the entry to restore.
     */
    public VnRollbackEntry rollforward(VnRollbackEntry currentState) {
        if (future.isEmpty()) return null;

        // Save current state to history stack
        if (currentState != null) {
            history.push(currentState);
        }

        return future.pop();
    }

    /**
     * Check if rollback is possible.
     */
    public boolean canRollback() {
        return !history.isEmpty();
    }

    /**
     * Check if roll forward is possible.
     */
    public boolean canRollforward() {
        return !future.isEmpty();
    }

    /**
     * Peek at the most recent rollback entry without removing it.
     */
    public VnRollbackEntry peek() {
        return history.peek();
    }

    /**
     * Get the number of entries in the rollback history.
     */
    public int size() {
        return history.size();
    }

    /**
     * Get the number of entries in the forward (redo) stack.
     */
    public int futureSize() {
        return future.size();
    }

    /**
     * Clear all rollback history and forward stack.
     */
    public void clear() {
        history.clear();
        future.clear();
    }

    /**
     * Clear only the forward stack.
     */
    public void clearFuture() {
        future.clear();
    }

    /**
     * Get max entries limit.
     */
    public int getMaxEntries() {
        return maxEntries;
    }
}
