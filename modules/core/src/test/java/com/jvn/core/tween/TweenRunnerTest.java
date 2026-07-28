package com.jvn.core.tween;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for {@link TweenRunner}'s safe iteration semantics.
 */
public class TweenRunnerTest {

  /** Trivial finishing task that completes after N updates. */
  private static class Counting extends TweenRunner.TweenTask {
    final String id;
    int remaining;
    boolean finished;
    Counting(String id, int updatesUntilDone) { this.id = id; this.remaining = updatesUntilDone; }
    @Override public void update(long deltaMs) {
      if (--remaining <= 0) finished = true;
    }
    @Override public boolean isFinished() { return finished; }
  }

  @Test
  public void finishedTasksAreRemovedAfterUpdate() {
    TweenRunner runner = new TweenRunner();
    Counting a = new Counting("a", 1);
    Counting b = new Counting("b", 3);
    runner.add(a);
    runner.add(b);

    runner.update(16);
    assertEquals(1, runner.activeCount(), "finished tasks should be compacted out");
    assertTrue(a.isFinished());

    runner.update(16);
    runner.update(16);
    assertEquals(0, runner.activeCount());
  }

  @Test
  public void chainedAddDuringUpdateDoesNotThrowAndDefersToNextFrame() {
    TweenRunner runner = new TweenRunner();
    List<String> ran = new ArrayList<>();

    Counting follower = new Counting("follower", 1);
    Counting leader = new Counting("leader", 1) {
      @Override public void update(long deltaMs) {
        super.update(deltaMs);
        ran.add(id);
        // Schedule the follower from inside an update — must not CME.
        runner.add(follower);
      }
    };
    runner.add(leader);

    // Frame 1: leader runs and queues follower; follower must NOT run yet.
    runner.update(16);
    assertEquals(List.of("leader"), ran);
    assertEquals(1, runner.activeCount(), "follower was deferred to next frame");

    // Frame 2: follower now runs and finishes.
    runner.update(16);
    assertTrue(follower.isFinished());
    assertEquals(0, runner.activeCount());
  }

  @Test
  public void addNullIsIgnored() {
    TweenRunner runner = new TweenRunner();
    runner.add(null);
    runner.update(16);
    assertEquals(0, runner.activeCount());
  }

  @Test
  public void allFinishingOnSameFrameDoesNotLoseOrders() {
    TweenRunner runner = new TweenRunner();
    List<String> updateOrder = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      final String id = "t" + i;
      runner.add(new TweenRunner.TweenTask() {
        boolean done;
        @Override public void update(long deltaMs) { updateOrder.add(id); done = true; }
        @Override public boolean isFinished() { return done; }
      });
    }
    runner.update(16);
    assertEquals(10, updateOrder.size(), "every queued task must update exactly once");
    assertEquals(0, runner.activeCount());
  }

  @Test
  public void throwingTaskDoesNotCorruptCompactionOrDeferredAdds() {
    TweenRunner runner = new TweenRunner();
    Counting follower = new Counting("follower", 1);
    runner.add(new TweenRunner.TweenTask() {
      @Override public void update(long deltaMs) {
        runner.add(follower);
        throw new IllegalStateException("boom");
      }
      @Override public boolean isFinished() { return false; }
    });
    Counting healthy = new Counting("healthy", 1);
    runner.add(healthy);

    assertThrows(IllegalStateException.class, () -> runner.update(16));

    assertTrue(healthy.isFinished(), "later tasks should still receive their update");
    assertEquals(1, runner.activeCount(), "only the deferred follower should remain");
    runner.update(16);
    assertEquals(0, runner.activeCount());
  }
}
