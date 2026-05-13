package com.jvn.testkit.jmh;

import com.jvn.core.animation.TimelineData;
import com.jvn.core.animation.TimelineRunner;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@State(Scope.Benchmark)
public class TimelineRunnerBench {

  private TimelineRunner runner;

  @Setup
  public void setup() {
    TimelineData timeline = new TimelineData("bench", 5000.0);
    runner = new TimelineRunner(timeline, null);
  }

  @Benchmark
  public void tickTimeline() {
    runner.update(16L);
  }
}
