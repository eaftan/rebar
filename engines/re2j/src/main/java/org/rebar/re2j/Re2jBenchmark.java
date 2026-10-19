package org.rebar.re2j;

import com.google.re2j.Pattern;
import java.nio.file.Files;
import java.nio.file.Path;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** JMH state that prepares and measures one RE2/J workload in a fork. */
@State(Scope.Thread)
public class Re2jBenchmark {
  /** Path to the Rebar KLV input supplied by the parent runner. */
  @Param("unset")
  public String inputPath;

  /** Result that the fork must reproduce before measuring. */
  @Param("0")
  public int expectedCount;

  private BenchmarkConfig config;
  private Workloads.Workload workload;

  /**
   * Prepares the workload and verifies its result before JMH measures it.
   *
   * @throws Exception if input parsing or workload execution fails
   */
  @Setup
  public void setup() throws Exception {
    config = BenchmarkConfig.parse(Files.readAllBytes(Path.of(inputPath)));
    int actual;
    if (config.model().equals("compile")) {
      Pattern pattern = config.compileRegex();
      actual = StringWorkloads.countMatches(pattern, config.haystack());
    } else {
      workload = StringWorkloads.create(config);
      actual = workload.run();
    }
    if (actual != expectedCount) {
      throw new IllegalStateException(
          "JMH fork count " + actual + " differed from " + expectedCount);
    }
  }

  /**
   * Runs one measured invocation of the prepared workload.
   *
   * @return the workload result consumed by JMH
   * @throws Exception if the workload fails
   */
  @Benchmark
  public int run() throws Exception {
    return workload.run();
  }

  /**
   * Compiles a fresh pattern without searching the verification haystack.
   *
   * @return the compiled pattern consumed by JMH
   */
  @Benchmark
  public Pattern compile() {
    return config.compileRegex();
  }
}
