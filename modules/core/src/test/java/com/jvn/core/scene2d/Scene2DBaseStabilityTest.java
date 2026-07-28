package com.jvn.core.scene2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class Scene2DBaseStabilityTest {

  @Test
  void mutationsDuringUpdateApplyAfterStableTraversal() {
    Scene2DBase scene = new Scene2DBase();
    AtomicInteger firstUpdates = new AtomicInteger();
    AtomicInteger secondUpdates = new AtomicInteger();
    AtomicInteger addedUpdates = new AtomicInteger();
    Entity2D added = new Entity2D() {
      @Override public void update(long deltaMs) { addedUpdates.incrementAndGet(); }
    };
    Entity2D first = new Entity2D() {
      @Override public void update(long deltaMs) {
        firstUpdates.incrementAndGet();
        scene.remove(this);
        scene.add(added);
      }
    };
    Entity2D second = new Entity2D() {
      @Override public void update(long deltaMs) { secondUpdates.incrementAndGet(); }
    };
    scene.add(first);
    scene.add(second);

    scene.update(16);

    assertEquals(1, firstUpdates.get());
    assertEquals(1, secondUpdates.get(), "removing an earlier child must not skip its neighbour");
    assertEquals(0, addedUpdates.get(), "new children start on the next traversal");
    assertEquals(2, scene.getChildren().size());
    assertSame(second, scene.getChildren().get(0));
    assertSame(added, scene.getChildren().get(1));

    scene.update(16);
    assertEquals(1, addedUpdates.get());
  }

  @Test
  void negativeDeltasAreSanitizedForEntities() {
    Scene2DBase scene = new Scene2DBase();
    long[] received = {-1};
    scene.add(new Entity2D() {
      @Override public void update(long deltaMs) { received[0] = deltaMs; }
    });
    scene.update(-10);
    assertEquals(0, received[0]);
  }
}
