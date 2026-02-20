package com.jvn.editor.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Generic undo/redo manager for visual editors.
 * Stores snapshots of state as strings (serialized properties).
 */
public class UndoManager {
  private static final int MAX_HISTORY = 50;

  private final Deque<String> undoStack = new ArrayDeque<>();
  private final Deque<String> redoStack = new ArrayDeque<>();
  private String currentState = "";
  private boolean suppressCapture = false;
  private Consumer<Boolean> onUndoAvailableChanged;
  private Consumer<Boolean> onRedoAvailableChanged;

  public UndoManager() {
  }

  public void setOnUndoAvailableChanged(Consumer<Boolean> listener) {
    this.onUndoAvailableChanged = listener;
  }

  public void setOnRedoAvailableChanged(Consumer<Boolean> listener) {
    this.onRedoAvailableChanged = listener;
  }

  /**
   * Capture the current state before a change.
   * Call this before modifying state.
   */
  public void captureState(String state) {
    if (suppressCapture) return;
    if (state.equals(currentState)) return;

    if (!currentState.isEmpty()) {
      undoStack.push(currentState);
      if (undoStack.size() > MAX_HISTORY) {
        undoStack.removeLast();
      }
    }
    currentState = state;
    redoStack.clear();
    notifyListeners();
  }

  /**
   * Set the initial state without adding to history.
   */
  public void setInitialState(String state) {
    currentState = state;
    undoStack.clear();
    redoStack.clear();
    notifyListeners();
  }

  /**
   * Undo the last change. Returns the previous state, or null if nothing to undo.
   */
  public String undo() {
    if (undoStack.isEmpty()) return null;
    redoStack.push(currentState);
    currentState = undoStack.pop();
    notifyListeners();
    return currentState;
  }

  /**
   * Redo the last undone change. Returns the state, or null if nothing to redo.
   */
  public String redo() {
    if (redoStack.isEmpty()) return null;
    undoStack.push(currentState);
    currentState = redoStack.pop();
    notifyListeners();
    return currentState;
  }

  public boolean canUndo() {
    return !undoStack.isEmpty();
  }

  public boolean canRedo() {
    return !redoStack.isEmpty();
  }

  public String getCurrentState() {
    return currentState;
  }

  /**
   * Suppress state capture temporarily (e.g., during programmatic updates).
   */
  public void setSuppressCapture(boolean suppress) {
    this.suppressCapture = suppress;
  }

  /**
   * Clear all history.
   */
  public void clear() {
    undoStack.clear();
    redoStack.clear();
    currentState = "";
    notifyListeners();
  }

  private void notifyListeners() {
    if (onUndoAvailableChanged != null) {
      onUndoAvailableChanged.accept(canUndo());
    }
    if (onRedoAvailableChanged != null) {
      onRedoAvailableChanged.accept(canRedo());
    }
  }
}
