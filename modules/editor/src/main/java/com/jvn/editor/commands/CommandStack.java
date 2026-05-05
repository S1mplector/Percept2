package com.jvn.editor.commands;

import java.util.ArrayDeque;
import java.util.Deque;

public class CommandStack {
  private final Deque<Command> undo = new ArrayDeque<>();
  private final Deque<Command> redo = new ArrayDeque<>();
  private Runnable onChange = () -> {};

  public void pushAndExecute(Command c) {
    if (c == null) return;
    c.execute();
    undo.push(c);
    redo.clear();
    notifyChanged();
  }

  public void undo() {
    if (undo.isEmpty()) return;
    Command c = undo.pop();
    c.undo();
    redo.push(c);
    notifyChanged();
  }

  public void redo() {
    if (redo.isEmpty()) return;
    Command c = redo.pop();
    c.redo();
    undo.push(c);
    notifyChanged();
  }

  public void clear() {
    undo.clear();
    redo.clear();
    notifyChanged();
  }

  public boolean canUndo() {
    return !undo.isEmpty();
  }

  public boolean canRedo() {
    return !redo.isEmpty();
  }

  public String undoDescription() {
    return describe(canUndo() ? undo.peek() : null);
  }

  public String redoDescription() {
    return describe(canRedo() ? redo.peek() : null);
  }

  public void setOnChange(Runnable onChange) {
    this.onChange = onChange == null ? () -> {} : onChange;
  }

  private String describe(Command command) {
    if (command == null) return "";
    String description = command.description();
    if (description == null) return "Change";
    String trimmed = description.trim();
    return trimmed.isEmpty() ? "Change" : trimmed;
  }

  private void notifyChanged() {
    onChange.run();
  }
}
