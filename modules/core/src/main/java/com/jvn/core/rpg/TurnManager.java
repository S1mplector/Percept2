package com.jvn.core.rpg;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Simplified ATB/turn manager: advances meters each update and yields ready combatants.
 */
public class TurnManager implements Serializable {
  private final List<Combatant> combatants = new ArrayList<>();
  private double atbThreshold = 100.0;

  public void add(Combatant c) { if (c != null) combatants.add(c); }
  public void clear() { combatants.clear(); }
  public List<Combatant> getCombatants() { return combatants; }

  public void setAtbThreshold(double threshold) { this.atbThreshold = threshold <= 0 ? 1.0 : threshold; }

  public List<Combatant> step(double deltaMs) {
    List<Combatant> ready = new ArrayList<>();
    double dt = deltaMs / 1000.0;
    for (Combatant c : combatants) {
      if (c.getStats().isDead()) continue;
      double gain = Math.max(0, c.getStats().getSpeed());
      c.setAtb(c.getAtb() + gain * dt);
      if (c.getAtb() >= atbThreshold) {
        ready.add(c);
        c.setAtb(0);
      }
    }
    ready.sort(Comparator.comparingDouble(Combatant::getSpeed).reversed());
    return ready;
  }
}
