package com.jvn.editor.commands;

public interface Command {
  void execute();
  void undo();
  default void redo() { execute(); }
  default String description() { return "Change"; }
}
