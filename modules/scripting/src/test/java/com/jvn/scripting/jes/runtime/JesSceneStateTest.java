package com.jvn.scripting.jes.runtime;

import com.jvn.core.scene2d.Sprite2D;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JesSceneStateTest {
  @Test
  public void savesAndRestoresCoreState() {
    JesScene2D scene = new JesScene2D();
    Sprite2D hero = new Sprite2D("hero.png", 10, 10);
    hero.setPosition(5, 6);
    scene.add(hero);
    scene.registerEntity("hero", hero);
    scene.setPlayerName("hero");
    scene.setGridSize(1, 1);

    Stats stats = new Stats();
    stats.setMaxHp(10);
    stats.setHp(8);
    stats.setAtk(4);
    stats.setAtkBonus(1);
    scene.setStats("hero", stats);

    Inventory inv = new Inventory();
    inv.setSlots(2);
    inv.add("potion", 3);
    scene.setInventory("hero", inv);

    Equipment eq = new Equipment();
    eq.set("weapon", "sword");
    scene.setEquipment("hero", eq);

    // Save baseline
    scene.setPlayerFacing("up"); // force a known value
    JesSceneState saved = scene.saveState();

    // Mutate
    hero.setPosition(100, 100);
    stats.setHp(1);
    inv.remove("potion", 3);
    eq.set("weapon", null);
    scene.setPlayerFacing("left");

    // Restore
    scene.loadState(saved);

    assertEquals(5.0, hero.getX(), 1e-6);
    assertEquals(6.0, hero.getY(), 1e-6);
    assertEquals(8.0, stats.getHp(), 1e-6);
    assertEquals(4.0, stats.getBaseAtk(), 1e-6);
    assertEquals(1.0, stats.getAtkBonus(), 1e-6);
    assertEquals(3, scene.getInventory("hero").getCount("potion"));
    assertEquals("sword", scene.getEquipment("hero").get("weapon"));
    assertEquals("up", scene.getPlayerFacing());
  }
}
