package org.rebar.safere;

import java.nio.file.Files;
import java.nio.file.Path;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class SafeReBenchmark {
  private Workloads.IntWorkload workload;

  @Setup
  public void setup() throws Exception {
    String input = System.getProperty("rebar.safere.input");
    if (input == null) {
      throw new IllegalStateException("missing rebar.safere.input");
    }
    InputMode mode = InputMode.parse(System.getProperty("rebar.safere.mode"));
    workload =
        Workloads.create(BenchmarkConfig.parse(Files.readAllBytes(Path.of(input)), mode), mode);
    int expected = Integer.parseInt(System.getProperty("rebar.safere.count"));
    int actual = workload.run();
    if (actual != expected) {
      throw new IllegalStateException("JMH fork count " + actual + " differed from " + expected);
    }
  }

  @Benchmark
  public int run() throws Exception {
    return workload.run();
  }
}
