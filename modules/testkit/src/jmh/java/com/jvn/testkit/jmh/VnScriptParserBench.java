package com.jvn.testkit.jmh;

import com.jvn.core.vn.script.VnScriptParser;

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
public class VnScriptParserBench {

  private VnScriptParser parser;
  private String script;

  @Setup
  public void setup() {
    parser = new VnScriptParser();
    StringBuilder sb = new StringBuilder();
    sb.append("@scenario bench_scenario\n");
    for (int i = 0; i < 500; i++) {
      sb.append("@say narrator \"Line ").append(i).append("\"\n");
    }
    script = sb.toString();
  }

  @Benchmark
  public Object parseScript() throws Exception {
    return parser.parseFromString(script);
  }
}
