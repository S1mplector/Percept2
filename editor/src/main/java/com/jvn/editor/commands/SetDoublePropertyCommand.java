package com.jvn.editor.commands;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class SetDoublePropertyCommand implements Command {
  private final DoubleConsumer setter;
  private final double newVal;
  private final double oldVal;
  private final String description;

  public SetDoublePropertyCommand(DoubleSupplier getter, DoubleConsumer setter, double newVal) {
    this(getter, setter, newVal, "Adjust Value");
  }

  public SetDoublePropertyCommand(DoubleSupplier getter, DoubleConsumer setter, double newVal, String description) {
    this.setter = setter;
    this.newVal = newVal;
    this.oldVal = getter.getAsDouble();
    this.description = description;
  }

  @Override public void execute() { setter.accept(newVal); }
  @Override public void undo() { setter.accept(oldVal); }
  @Override public String description() { return description; }
}
