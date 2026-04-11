package com.jvn.editor.commands;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class SetBooleanPropertyCommand implements Command {
  private final Consumer<Boolean> setter;
  private final boolean newVal;
  private final boolean oldVal;
  private final String description;

  public SetBooleanPropertyCommand(BooleanSupplier getter, Consumer<Boolean> setter, boolean newVal) {
    this(getter, setter, newVal, "Toggle Setting");
  }

  public SetBooleanPropertyCommand(BooleanSupplier getter, Consumer<Boolean> setter, boolean newVal, String description) {
    this.setter = setter;
    this.newVal = newVal;
    this.oldVal = getter.getAsBoolean();
    this.description = description;
  }

  @Override public void execute() { setter.accept(newVal); }
  @Override public void undo() { setter.accept(oldVal); }
  @Override public String description() { return description; }
}
