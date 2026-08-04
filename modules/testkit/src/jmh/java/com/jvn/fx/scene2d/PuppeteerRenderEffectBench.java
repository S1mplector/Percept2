package com.jvn.fx.scene2d;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/** Measures CPU submission work removed by the Puppeteer GPU brightness fast path. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
public class PuppeteerRenderEffectBench {
  @Param({"65536", "2073600"})
  public int pixelCount;

  private int[] source;
  private int[] working;
  private double[] brightnessMatrix;

  @Setup
  public void setup() {
    source = new int[pixelCount];
    working = new int[pixelCount];
    Arrays.fill(source, 0xffd08040);
    brightnessMatrix = new double[] {
        0.65, 0.0, 0.0, 0.0, 0.0,
        0.0, 0.65, 0.0, 0.0, 0.0,
        0.0, 0.0, 0.65, 0.0, 0.0,
        0.0, 0.0, 0.0, 1.0, 0.0
    };
  }

  @Benchmark
  public int cpuBrightnessFrame() {
    System.arraycopy(source, 0, working, 0, source.length);
    PixelEffects.applyColorMatrix(working, brightnessMatrix);
    return working[working.length - 1];
  }

  @Benchmark
  public double gpuBrightnessSubmission() {
    return FxBlitter2D.gpuBrightnessFor(brightnessMatrix);
  }
}
