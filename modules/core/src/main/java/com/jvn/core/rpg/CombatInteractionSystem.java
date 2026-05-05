package com.jvn.core.rpg;

import com.jvn.core.math.Rect;
import com.jvn.core.physics.PhysicsWorld2D;
import com.jvn.core.tween.TweenRunner;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CombatInteractionSystem {
  public static class Hitbox {
    public String id;
    public int team;
    public Rect bounds = new Rect();
  }

  public static class DamagePopup {
    public String text;
    public double x, y;
    public double alpha = 1.0;
    public double lifetimeMs = 800;
    public double elapsedMs = 0;
  }

  private final PhysicsWorld2D world;
  private final List<Hitbox> hitboxes = new ArrayList<>();
  private final List<DamagePopup> popups = new ArrayList<>();
  private final TweenRunner tweens;

  public CombatInteractionSystem(PhysicsWorld2D world, TweenRunner tweens) {
    this.world = world;
    this.tweens = tweens;
  }

  public void registerHitbox(Hitbox hb) { if (hb != null) hitboxes.add(hb); }
  public void clearHitboxes() { hitboxes.clear(); }

  public List<Hitbox> getTargetsAt(double x, double y, int attackerTeam) {
    List<Hitbox> out = new ArrayList<>();
    for (Hitbox hb : hitboxes) {
      if (hb.team == attackerTeam) continue;
      if (hb.bounds.contains(x, y)) out.add(hb);
    }
    return out;
  }

  public void spawnDamagePopup(String text, double x, double y) {
    DamagePopup p = new DamagePopup();
    p.text = text;
    p.x = x;
    p.y = y;
    popups.add(p);
  }

  public List<DamagePopup> getPopups() { return popups; }

  public void update(long deltaMs) {
    if (deltaMs <= 0) return;
    Iterator<DamagePopup> it = popups.iterator();
    while (it.hasNext()) {
      DamagePopup p = it.next();
      p.elapsedMs += deltaMs;
      double t = Math.min(1.0, p.elapsedMs / p.lifetimeMs);
      p.alpha = 1.0 - t;
      p.y -= deltaMs * 0.02; // float up
      if (p.elapsedMs >= p.lifetimeMs) it.remove();
    }
  }
}
