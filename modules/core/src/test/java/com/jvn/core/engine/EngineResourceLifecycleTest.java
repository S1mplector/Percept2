package com.jvn.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EngineResourceLifecycleTest {

  @Test
  void stopClosesOwnedResourcesOnceInReverseOrder() {
    Engine engine = new Engine(null);
    StringBuilder closed = new StringBuilder();
    engine.own((AutoCloseable) () -> closed.append('a'));
    engine.own((AutoCloseable) () -> closed.append('b'));

    engine.start();
    engine.stop();
    engine.stop();

    assertEquals("ba", closed.toString());
    assertThrows(IllegalStateException.class, engine::start);
  }

  @Test
  void resourceRegisteredAfterStopIsClosedImmediately() {
    Engine engine = new Engine(null);
    engine.stop();
    int[] closes = {0};

    engine.own((AutoCloseable) () -> closes[0]++);

    assertEquals(1, closes[0]);
  }
}
