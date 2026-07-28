package com.jvn.core.tween;

import com.jvn.core.animation.Easing;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the enriched {@link Tween}: delay, loops, yoyo, callbacks, builder wiring,
 * and legacy-constructor backwards compatibility.
 */
public class TweenTest {

  private static final double EPS = 1e-9;

  @Test
  public void legacyConstructorInterpolatesLinearly() {
    Tween t = new Tween(0, 100, 1000, d -> d);
    assertEquals(0.0, t.update(0), EPS);
    assertEquals(25.0, t.update(250), EPS);
    assertEquals(75.0, t.update(500), EPS);
    assertFalse(t.isFinished());
    assertEquals(100.0, t.update(500), EPS);
    assertTrue(t.isFinished());
  }

  @Test
  public void durationCoercesNonPositiveToOneMs() {
    // Zero duration would divide by zero; coerced to 1ms so the first non-zero update finishes.
    Tween t = Tween.from(0).to(10).duration(0).build();
    assertEquals(10.0, t.update(1), EPS);
    assertTrue(t.isFinished());
  }

  @Test
  public void delayBlocksProgressUntilConsumed() {
    Tween t = Tween.from(0).to(100).duration(100).delay(200).build();
    t.update(150);
    assertEquals(0.0, t.currentValue(), EPS, "still in delay phase");
    t.update(50);
    assertEquals(0.0, t.currentValue(), EPS, "exactly at end of delay, loop not yet advanced");
    t.update(50);
    assertEquals(50.0, t.currentValue(), EPS, "50ms into a 100ms loop");
  }

  @Test
  public void multiLoopWithoutYoyoRestartsFromStart() {
    AtomicInteger completes = new AtomicInteger();
    Tween t = Tween.from(0).to(10)
        .duration(100)
        .loops(3)
        .onComplete(completes::incrementAndGet)
        .build();

    // Three loops of 100ms. Each loop ramps 0 -> 10 and then restarts.
    t.update(50);
    assertEquals(5.0, t.currentValue(), EPS);
    t.update(50);
    // Loop 1 done, loop 2 starts from 0. Since we used exactly 50ms, we're 0ms into loop 2.
    assertEquals(0.0, t.currentValue(), EPS);
    assertEquals(1, t.loopsCompleted());
    t.update(100);
    // Loop 2 done, loop 3 starts.
    assertEquals(2, t.loopsCompleted());
    t.update(100);
    assertTrue(t.isFinished());
    assertEquals(10.0, t.currentValue(), EPS);
    assertEquals(1, completes.get(), "onComplete fires exactly once");
  }

  @Test
  public void yoyoAlternatesDirection() {
    Tween t = Tween.from(0).to(10).duration(100).loops(2).yoyo().build();

    t.update(50);
    assertEquals(5.0, t.currentValue(), EPS, "forward loop midpoint");
    t.update(50);
    // End of loop 1, start of loop 2 (reverse).
    assertEquals(1, t.loopsCompleted());
    t.update(50);
    assertEquals(5.0, t.currentValue(), EPS, "reverse loop midpoint still reads 5");
    t.update(50);
    assertTrue(t.isFinished());
    // Terminal of an even-indexed (2) yoyo returns to start.
    assertEquals(0.0, t.currentValue(), EPS);
  }

  @Test
  public void infiniteLoopsNeverFinish() {
    Tween t = Tween.from(0).to(1).duration(10).loopForever().build();
    for (int i = 0; i < 1000; i++) t.update(10);
    assertFalse(t.isFinished());
    assertTrue(t.loopsCompleted() >= 999);
  }

  @Test
  public void onUpdateReceivesEveryStep() {
    AtomicReference<Double> last = new AtomicReference<>(Double.NaN);
    Tween t = Tween.from(0).to(10).duration(100)
        .onUpdate(last::set).build();
    t.update(30);
    assertEquals(3.0, last.get(), EPS);
    t.update(70);
    // Terminal callback fires with 10.
    assertEquals(10.0, last.get(), EPS);
  }

  @Test
  public void builderWithEasingTypeUsesAnimationEasing() {
    // EASE_OUT_QUAD at t=0.5 → 0.5 * (2 - 0.5) = 0.75
    Tween t = Tween.from(0).to(100).duration(100).easing(Easing.Type.EASE_OUT_QUAD).build();
    t.update(50);
    assertEquals(75.0, t.currentValue(), EPS);
  }

  @Test
  public void asTaskIntegratesWithTweenRunner() {
    TweenRunner runner = new TweenRunner();
    AtomicInteger completes = new AtomicInteger();
    Tween t = Tween.from(0).to(5).duration(100).onComplete(completes::incrementAndGet).build();
    runner.add(t.asTask());

    runner.update(60);
    assertFalse(t.isFinished());
    runner.update(60);
    assertTrue(t.isFinished());
    assertEquals(1, completes.get());
    // Once finished, the runner drops it.
    assertEquals(0, runner.activeCount());
  }

  @Test
  public void builderStartOnEnqueuesAndReturnsTween() {
    TweenRunner runner = new TweenRunner();
    Tween t = Tween.from(0).to(1).duration(50).startOn(runner);
    assertNotNull(t);
    runner.update(50);
    assertTrue(t.isFinished());
  }

  @Test
  public void resetRestoresInitialState() {
    Tween t = Tween.from(0).to(10).duration(100).build();
    t.update(70);
    t.reset();
    assertEquals(0.0, t.currentValue(), EPS);
    assertFalse(t.isFinished());
    t.update(100);
    assertEquals(10.0, t.currentValue(), EPS);
    assertTrue(t.isFinished());
  }

  @Test
  public void hugeInfiniteLoopDeltaAdvancesWithoutIteratingEveryLoop() {
    Tween t = Tween.from(0).to(10).duration(10).loopForever().yoyo().build();

    double value = t.update(Long.MAX_VALUE);

    assertFalse(t.isFinished());
    assertTrue(Double.isFinite(value));
    assertEquals(Integer.MAX_VALUE, t.loopsCompleted(),
        "the public loop count saturates instead of overflowing");
  }

  @Test
  public void invalidLoopCountsGracefullyPlayOnce() {
    Tween t = Tween.from(0).to(10).duration(10).loops(0).build();
    t.update(10);
    assertTrue(t.isFinished());
    assertEquals(1, t.loopsCompleted());
  }
}
