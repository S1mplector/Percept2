package com.jvn.editor.commands;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SetStringPropertyCommand implements Command {
  private final Consumer<String> setter;
  private final String newVal;
  private final String oldVal;
  private final String description;

  public SetStringPropertyCommand(Supplier<String> getter, Consumer<String> setter, String newVal) {
    this(getter, setter, newVal, "Edit Text");
  }

  public SetStringPropertyCommand(Supplier<String> getter, Consumer<String> setter, String newVal, String description) {
    this.setter = setter;
    this.newVal = newVal == null ? "" : newVal;
    this.oldVal = safe(getter.get());
    this.description = description;
  }

  @Override public void execute() { setter.accept(newVal); }
  @Override public void undo() { setter.accept(oldVal); }
  @Override public String description() { return description; }

  private static String safe(String s) { return s == null ? "" : s; }
}
