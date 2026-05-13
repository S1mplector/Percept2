package com.jvn.testkit.jmh;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@State(Scope.Benchmark)
public class RenderFrameBench {

  @Benchmark
  public void renderFrame() {
    // Placeholder: replace with actual VnRenderer.renderFrame() call once
    // a headless test fixture is available.
    long sum = 0;
    for (int i = 0; i < 10_000; i++) {
      sum += i;
    }
    // prevent dead-code elimination
    if (sum < 0) throw new AssertionError();
  }
}
