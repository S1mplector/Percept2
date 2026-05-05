package com.jvn.core.rpg;

import java.io.Serializable;

public class RpgStats implements Serializable {
  private double maxHp;
  private double hp;
  private double maxMp;
  private double mp;
  private double attack;
  private double defense;
  private double speed;
  private double attackBonus;
  private double defenseBonus;
  private double speedBonus;

  public double getMaxHp() { return maxHp; }
  public void setMaxHp(double maxHp) { this.maxHp = maxHp; if (hp > maxHp) hp = maxHp; }
  public double getHp() { return hp; }
  public void setHp(double hp) { this.hp = clampToMax(hp, maxHp); }
  public double getMaxMp() { return maxMp; }
  public void setMaxMp(double maxMp) { this.maxMp = maxMp; if (mp > maxMp) mp = maxMp; }
  public double getMp() { return mp; }
  public void setMp(double mp) { this.mp = clampToMax(mp, maxMp); }

  public double getAttack() { return attack + attackBonus; }
  public double getBaseAttack() { return attack; }
  public void setAttack(double attack) { this.attack = attack; }

  public double getDefense() { return defense + defenseBonus; }
  public double getBaseDefense() { return defense; }
  public void setDefense(double defense) { this.defense = defense; }

  public double getSpeed() { return speed + speedBonus; }
  public double getBaseSpeed() { return speed; }
  public void setSpeed(double speed) { this.speed = speed; }

  public double getAttackBonus() { return attackBonus; }
  public void setAttackBonus(double attackBonus) { this.attackBonus = attackBonus; }
  public double getDefenseBonus() { return defenseBonus; }
  public void setDefenseBonus(double defenseBonus) { this.defenseBonus = defenseBonus; }
  public double getSpeedBonus() { return speedBonus; }
  public void setSpeedBonus(double speedBonus) { this.speedBonus = speedBonus; }

  public boolean isDead() { return maxHp > 0 && hp <= 0; }

  public RpgStats copy() {
    RpgStats c = new RpgStats();
    c.maxHp = maxHp; c.hp = hp;
    c.maxMp = maxMp; c.mp = mp;
    c.attack = attack; c.defense = defense; c.speed = speed;
    c.attackBonus = attackBonus; c.defenseBonus = defenseBonus; c.speedBonus = speedBonus;
    return c;
  }

  private double clampToMax(double v, double max) {
    if (max > 0 && v > max) return max;
    return v;
  }
}
