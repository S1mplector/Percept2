package com.jvn.core.tween;

import java.util.function.DoubleUnaryOperator;

public class Tween {
  private final double start;
  private final double end;
  private final long durationMs;
  private long elapsedMs;
  private final DoubleUnaryOperator easing;
  private boolean finished;

  public Tween(double start, double end, long durationMs, DoubleUnaryOperator easing) {
    this.start = start;
    this.end = end;
    this.durationMs = Math.max(1, durationMs);
    // Default to linear easing if none provided
    this.easing = easing != null ? easing : (t -> t);
    this.elapsedMs = 0;
    this.finished = false;
  }

  public double update(long deltaMs) {
    if (finished) return end;
    elapsedMs += deltaMs;
    if (elapsedMs >= durationMs) {
      elapsedMs = durationMs;
      finished = true;
    }
    double t = elapsedMs / (double) durationMs;
    double k = easing.applyAsDouble(t);
    return start + (end - start) * k;
  }

  public boolean isFinished() { return finished; }
  public void reset() { elapsedMs = 0; finished = false; }
}
