package com.jvn.core.tween;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleUnaryOperator;

import com.jvn.core.animation.Easing;

/**
 * Time-based scalar interpolation with optional delay, looping, yoyo (ping-pong),
 * and per-update / completion callbacks.
 *
 * <p>Legacy constructor form still works:</p>
 * <pre>{@code
 * Tween t = new Tween(0, 100, 500, Easing.Type.EASE_OUT_QUAD);
 * }</pre>
 *
 * <p>Preferred builder form:</p>
 * <pre>{@code
 * Tween t = Tween.from(0).to(100)
 *     .duration(500)
 *     .delay(100)
 *     .easing(Easing.Type.EASE_OUT_CUBIC)
 *     .loops(3)
 *     .yoyo()
 *     .onUpdate(v -> entity.x = v)
 *     .onComplete(() -> System.out.println("done"))
 *     .build();
 * engine.tweens().add(t.asTask());
 * }</pre>
 */
public class Tween {
  private final double start;
  private final double end;
  private final long durationMs;
  private final long delayMs;
  private final DoubleUnaryOperator easing;
  /** Total loops to play. Use {@link #LOOP_INFINITE} for unbounded. 1 = play once. */
  private final int loops;
  private final boolean yoyo;
  private final DoubleConsumer onUpdate;
  private final Runnable onComplete;

  private long delayRemainingMs;
  private long loopElapsedMs;
  private int loopsCompleted;
  private boolean finished;
  private double currentValue;

  public static final int LOOP_INFINITE = -1;

  // --- Legacy constructors (preserved for backward compatibility) -------------

  public Tween(double start, double end, long durationMs, DoubleUnaryOperator easing) {
    this(start, end, durationMs, 0L, easing, 1, false, null, null);
  }

  public Tween(double start, double end, long durationMs, Easing.Type easingType) {
    this(start, end, durationMs, 0L, toOperator(easingType), 1, false, null, null);
  }

  private Tween(
      double start,
      double end,
      long durationMs,
      long delayMs,
      DoubleUnaryOperator easing,
      int loops,
      boolean yoyo,
      DoubleConsumer onUpdate,
      Runnable onComplete) {
    this.start = start;
    this.end = end;
    this.durationMs = Math.max(1, durationMs);
    this.delayMs = Math.max(0, delayMs);
    this.easing = easing != null ? easing : (t -> t);
    this.loops = loops;
    this.yoyo = yoyo;
    this.onUpdate = onUpdate;
    this.onComplete = onComplete;
    this.delayRemainingMs = this.delayMs;
    this.loopElapsedMs = 0;
    this.loopsCompleted = 0;
    this.finished = false;
    this.currentValue = start;
  }

  /**
   * Advance this tween by {@code deltaMs} and return the current interpolated value.
   * After the tween finishes all loops, always returns the terminal value.
   */
  public double update(long deltaMs) {
    if (finished) return currentValue;
    if (deltaMs <= 0) return currentValue;

    long remaining = deltaMs;

    // Phase 1: burn down delay before the first loop starts.
    if (delayRemainingMs > 0) {
      long consumed = Math.min(remaining, delayRemainingMs);
      delayRemainingMs -= consumed;
      remaining -= consumed;
      if (remaining <= 0) return currentValue;
    }

    // Phase 2: advance time within loops.
    loopElapsedMs += remaining;
    while (true) {
      if (loopElapsedMs < durationMs) {
        double t = loopElapsedMs / (double) durationMs;
        currentValue = valueAt(t);
        if (onUpdate != null) onUpdate.accept(currentValue);
        return currentValue;
      }
      // Current loop finished.
      loopElapsedMs -= durationMs;
      loopsCompleted++;
      if (loops != LOOP_INFINITE && loopsCompleted >= loops) {
        currentValue = terminalValue();
        if (onUpdate != null) onUpdate.accept(currentValue);
        finished = true;
        if (onComplete != null) onComplete.run();
        return currentValue;
      }
      // Otherwise the next loop consumes the carry-over.
    }
  }

  private double valueAt(double tNormalized) {
    double phase = tNormalized;
    if (yoyo && (loopsCompleted & 1) == 1) {
      // Odd loop: play backwards.
      phase = 1.0 - tNormalized;
    }
    double k = easing.applyAsDouble(phase);
    return start + (end - start) * k;
  }

  /** The value held after the final loop completes (accounts for yoyo parity). */
  private double terminalValue() {
    if (!yoyo) return end;
    // With yoyo, the final resting point alternates: even completed loops end at start,
    // odd completed loops end at end.
    return (loopsCompleted & 1) == 1 ? end : start;
  }

  public boolean isFinished() { return finished; }
  public double currentValue() { return currentValue; }
  public int loopsCompleted() { return loopsCompleted; }

  public void reset() {
    delayRemainingMs = delayMs;
    loopElapsedMs = 0;
    loopsCompleted = 0;
    finished = false;
    currentValue = start;
  }

  /**
   * Wrap this tween as a {@link TweenRunner.TweenTask} so it can be driven by
   * a shared {@link TweenRunner}. The task advances this tween each frame and
   * reports finished when the tween finishes.
   */
  public TweenRunner.TweenTask asTask() {
    return new TweenRunner.TweenTask() {
      @Override public void update(long deltaMs) { Tween.this.update(deltaMs); }
      @Override public boolean isFinished() { return Tween.this.isFinished(); }
    };
  }

  // --- Builder ---------------------------------------------------------------

  /** Begin building a tween starting at {@code start}. */
  public static Builder from(double start) { return new Builder(start); }

  /** Convenience: build a tween from {@code start} to {@code end}. */
  public static Builder range(double start, double end) { return new Builder(start).to(end); }

  private static DoubleUnaryOperator toOperator(Easing.Type type) {
    Easing.Type resolved = type != null ? type : Easing.Type.LINEAR;
    return t -> Easing.apply(resolved, t);
  }

  public static final class Builder {
    private final double start;
    private double end;
    private long durationMs = 500;
    private long delayMs = 0;
    private DoubleUnaryOperator easing = t -> t;
    private int loops = 1;
    private boolean yoyo = false;
    private DoubleConsumer onUpdate;
    private Runnable onComplete;

    private Builder(double start) {
      this.start = start;
      this.end = start;
    }

    public Builder to(double end) { this.end = end; return this; }
    public Builder duration(long millis) { this.durationMs = millis; return this; }
    public Builder delay(long millis) { this.delayMs = millis; return this; }

    public Builder easing(DoubleUnaryOperator fn) { this.easing = fn != null ? fn : t -> t; return this; }
    public Builder easing(Easing.Type type) { this.easing = toOperator(type); return this; }

    /** Play {@code count} loops. {@link Tween#LOOP_INFINITE} for unbounded. */
    public Builder loops(int count) { this.loops = count; return this; }
    public Builder loopForever() { this.loops = LOOP_INFINITE; return this; }

    /** Alternate direction every loop (ping-pong). Requires loops &gt; 1 to have effect. */
    public Builder yoyo() { this.yoyo = true; return this; }
    public Builder yoyo(boolean enabled) { this.yoyo = enabled; return this; }

    /** Invoked after each {@link Tween#update(long)} with the current value. */
    public Builder onUpdate(DoubleConsumer fn) { this.onUpdate = fn; return this; }

    /** Invoked once when the tween finishes all loops (never for {@link Tween#LOOP_INFINITE}). */
    public Builder onComplete(Runnable fn) { this.onComplete = fn; return this; }

    public Tween build() {
      return new Tween(start, end, durationMs, delayMs, easing, loops, yoyo, onUpdate, onComplete);
    }

    /** Convenience: build and enqueue on the given runner, returning the tween. */
    public Tween startOn(TweenRunner runner) {
      Tween tween = build();
      if (runner != null) runner.add(tween.asTask());
      return tween;
    }
  }
}
