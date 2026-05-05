package com.jvn.scripting.jes.runtime;

import java.util.HashMap;
import java.util.Map;

/**
 * Serializable snapshot of a JesScene2D state.
 */
public class JesSceneState {
  public String playerName;
  public double[] playerPosition;
  public String playerFacing;
  public Map<String,double[]> entityPositions = new HashMap<>();
  public Map<String, StatsSnapshot> stats = new HashMap<>();
  public Map<String, Map<String,Integer>> inventories = new HashMap<>();
  public Map<String, Integer> inventorySlots = new HashMap<>();
  public Map<String, Map<String,String>> equipment = new HashMap<>();

  public static class StatsSnapshot {
    public double maxHp;
    public double hp;
    public double maxMp;
    public double mp;
    public double atk;
    public double def;
    public double speed;
    public double atkBonus;
    public double defBonus;
    public double speedBonus;
    public String deathCall;
    public boolean removeOnDeath;
  }
}
