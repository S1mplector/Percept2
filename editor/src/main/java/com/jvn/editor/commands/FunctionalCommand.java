package com.jvn.editor.commands;

public class FunctionalCommand implements Command {
  private final Runnable apply;
  private final Runnable undo;
  private final String description;

  public FunctionalCommand(Runnable apply, Runnable undo) {
    this("Change", apply, undo);
  }

  public FunctionalCommand(String description, Runnable apply, Runnable undo) {
    this.description = description;
    this.apply = apply;
    this.undo = undo;
  }

  @Override public void execute() { if (apply != null) apply.run(); }
  @Override public void undo() { if (undo != null) undo.run(); }
  @Override public String description() { return description; }
}
