package com.jvn.scripting.jes.runtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class JesScenePauseTest {
  @Test
  public void pauseSkipsUpdate() {
    JesScene2D scene = new JesScene2D();
    scene.setPaused(true);
    double[] pos = {0,0};
    com.jvn.core.scene2d.Sprite2D s = new com.jvn.core.scene2d.Sprite2D("img", 1, 1);
    scene.add(s);
    scene.registerEntity("e", s);
    scene.update(16);
    assertEquals(pos[0], s.getX(), 1e-6);
    assertEquals(pos[1], s.getY(), 1e-6);
  }
}
