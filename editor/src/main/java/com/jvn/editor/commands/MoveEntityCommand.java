package com.jvn.editor.commands;

import com.jvn.core.scene2d.Entity2D;

public class MoveEntityCommand implements Command {
  private final Entity2D entity;
  private final double sx, sy;
  private final double tx, ty;

  public MoveEntityCommand(Entity2D entity, double sx, double sy, double tx, double ty) {
    this.entity = entity;
    this.sx = sx; this.sy = sy;
    this.tx = tx; this.ty = ty;
  }

  @Override public void execute() { if (entity != null) entity.setPosition(tx, ty); }
  @Override public void undo() { if (entity != null) entity.setPosition(sx, sy); }
  @Override public String description() { return "Move Entity"; }
}
