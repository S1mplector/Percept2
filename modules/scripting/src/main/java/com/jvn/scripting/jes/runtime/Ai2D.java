package com.jvn.scripting.jes.runtime;

public class Ai2D {
  private String type;
  private String target;
  private double aggroRange;
  private double attackRange;
  private double attackIntervalMs;
  private double attackAmount;
  private double moveSpeed;
  private double attackCooldownMs;
  private double patrolRadius;
  private double patrolIntervalMs = 1500.0;
  private double patrolGoalX;
  private double patrolGoalY;
  private boolean hasPatrolGoal;
  private double patrolElapsedMs;
  private boolean requiresLineOfSight;
  private double guardRadius;
  private double fleeDistance;
  private double lastDirX;
  private double lastDirY;

  public String getType() { return type; }
  public void setType(String type) { this.type = type; }

  public String getTarget() { return target; }
  public void setTarget(String target) { this.target = target; }

  public double getAggroRange() { return aggroRange; }
  public void setAggroRange(double aggroRange) { this.aggroRange = aggroRange; }

  public double getAttackRange() { return attackRange; }
  public void setAttackRange(double attackRange) { this.attackRange = attackRange; }

  public double getAttackIntervalMs() { return attackIntervalMs; }
  public void setAttackIntervalMs(double attackIntervalMs) { this.attackIntervalMs = attackIntervalMs; }

  public double getAttackAmount() { return attackAmount; }
  public void setAttackAmount(double attackAmount) { this.attackAmount = attackAmount; }

  public double getMoveSpeed() { return moveSpeed; }
  public void setMoveSpeed(double moveSpeed) { this.moveSpeed = moveSpeed; }

  public double getAttackCooldownMs() { return attackCooldownMs; }
  public void setAttackCooldownMs(double attackCooldownMs) { this.attackCooldownMs = attackCooldownMs; }

  public double getPatrolRadius() { return patrolRadius; }
  public void setPatrolRadius(double patrolRadius) { this.patrolRadius = patrolRadius; }

  public double getPatrolIntervalMs() { return patrolIntervalMs; }
  public void setPatrolIntervalMs(double patrolIntervalMs) { this.patrolIntervalMs = patrolIntervalMs; }

  public void setPatrolGoal(double x, double y) { this.patrolGoalX = x; this.patrolGoalY = y; this.hasPatrolGoal = true; }
  public void clearPatrolGoal() { this.hasPatrolGoal = false; }
  public boolean hasPatrolGoal() { return hasPatrolGoal; }
  public double getPatrolGoalX() { return patrolGoalX; }
  public double getPatrolGoalY() { return patrolGoalY; }
  public double getPatrolElapsed() { return patrolElapsedMs; }
  public void setPatrolElapsed(double ms) { this.patrolElapsedMs = ms; }

  public boolean isRequiresLineOfSight() { return requiresLineOfSight; }
  public void setRequiresLineOfSight(boolean requiresLineOfSight) { this.requiresLineOfSight = requiresLineOfSight; }

  public double getGuardRadius() { return guardRadius; }
  public void setGuardRadius(double guardRadius) { this.guardRadius = guardRadius; }

  public double getFleeDistance() { return fleeDistance; }
  public void setFleeDistance(double fleeDistance) { this.fleeDistance = fleeDistance; }

  public double getLastDirX() { return lastDirX; }
  public double getLastDirY() { return lastDirY; }
  public void setLastDir(double x, double y) { this.lastDirX = x; this.lastDirY = y; }
}
