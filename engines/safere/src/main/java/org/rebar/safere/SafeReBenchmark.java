package org.rebar.safere;

import java.nio.file.Files;
import java.nio.file.Path;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@State(Scope.Thread)
public class SafeReBenchmark {
  @Param("unset")
  public String inputPath;

  @Param("string")
  public String inputMode;

  @Param("0")
  public int expectedCount;

  private Workloads.Workload workload;

  @Setup
  public void setup() throws Exception {
    InputMode mode = InputMode.parse(inputMode);
    workload =
        Workloads.create(BenchmarkConfig.parse(Files.readAllBytes(Path.of(inputPath)), mode), mode);
    int actual = workload.run();
    if (actual != expectedCount) {
      throw new IllegalStateException(
          "JMH fork count " + actual + " differed from " + expectedCount);
    }
  }

  @Benchmark
  public int run() throws Exception {
    return workload.run();
  }
}
