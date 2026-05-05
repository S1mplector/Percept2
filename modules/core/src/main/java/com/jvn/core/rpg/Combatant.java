package com.jvn.core.rpg;

import java.io.Serializable;

public class Combatant implements Serializable {
  private String id;
  private RpgStats stats = new RpgStats();
  private double atb;
  private int team; // 0 player, 1 enemy, etc.
  private boolean actedThisRound;

  public Combatant() {}
  public Combatant(String id, RpgStats stats, int team) {
    this.id = id;
    if (stats != null) this.stats = stats;
    this.team = team;
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public RpgStats getStats() { return stats; }
  public void setStats(RpgStats stats) { if (stats != null) this.stats = stats; }
  public double getAtb() { return atb; }
  public void setAtb(double atb) { this.atb = atb; }
  public int getTeam() { return team; }
  public void setTeam(int team) { this.team = team; }
  public boolean hasActedThisRound() { return actedThisRound; }
  public void setActedThisRound(boolean actedThisRound) { this.actedThisRound = actedThisRound; }
  public double getSpeed() { return stats == null ? 0 : stats.getSpeed(); }
}
