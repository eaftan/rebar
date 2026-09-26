package org.rebar.re2j;

import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.util.Statistics;

/** Entry point for Rebar's RE2/J engine. */
public final class Main {
  private record Sample(long duration, int count) {}

  private Main() {}

  /**
   * Runs a Rebar workload or prints the RE2/J version.
   *
   * @param args optionally {@code version}
   * @throws Exception if input parsing or workload execution fails
   */
  public static void main(String... args) throws Exception {
    if (args.length == 1 && args[0].equals("version")) {
      printVersion();
      return;
    }
    if (args.length != 0) {
      throw new IllegalArgumentException("usage: Main [version]");
    }

    byte[] raw = System.in.readAllBytes();
    BenchmarkConfig config = BenchmarkConfig.parse(raw);
    if (config.model().equals("compile")) {
      for (Sample sample :
          sampleDirect(
              config,
              config::compileRegex,
              pattern -> StringWorkloads.countMatches(pattern, config.haystack()))) {
        System.out.printf("%d,%d%n", sample.duration(), sample.count());
      }
      return;
    }

    Workloads.Workload workload = StringWorkloads.create(config);
    // A zero measurement budget still runs one operation for its count.
    if (config.maxTime() == 0) {
      for (Sample sample : sampleDirect(config, workload::run, Integer::intValue)) {
        System.out.printf("%d,%d%n", sample.duration(), sample.count());
      }
      return;
    }
    int count = workload.run();
    runJmh(raw, config, count);
  }

  private static void printVersion() throws Exception {
    Properties properties = new Properties();
    try (InputStream stream =
        Main.class.getResourceAsStream("/re2j-version.properties")) {
      if (stream == null) {
        throw new IllegalStateException("RE2/J version metadata is missing");
      }
      properties.load(stream);
    }
    String version = properties.getProperty("version");
    if (version == null) {
      throw new IllegalStateException("RE2/J version metadata has no version");
    }
    System.out.printf(
        "RE2/J %s (%s %s)%n",
        version,
        System.getProperty("java.vm.name"),
        System.getProperty("java.vm.version"));
  }

  private static void runJmh(byte[] raw, BenchmarkConfig config, int count) throws Exception {
    Path input = createTempFile("rebar-re2j-", ".klv");
    Path output = createTempFile("rebar-re2j-jmh-", ".log");
    try {
      Files.write(input, raw);
      int warmupIterations = config.maxWarmupIters() > 0 && config.maxWarmupTime() > 0 ? 1 : 0;
      var optionsBuilder =
          new OptionsBuilder()
              .include("^" + Re2jBenchmark.class.getName() + ".run$")
              .mode(Mode.SampleTime)
              .timeUnit(TimeUnit.NANOSECONDS)
              .forks(1)
              .threads(1)
              .warmupIterations(warmupIterations)
              .warmupTime(TimeValue.nanoseconds(config.maxWarmupTime()))
              .measurementIterations(1)
              .measurementTime(TimeValue.nanoseconds(config.maxTime()))
              .param("inputPath", input.toString())
              .param("expectedCount", Integer.toString(count))
              .output(output.toString());
      Options options = optionsBuilder.build();
      var results = new Runner(options).run();
      if (results.size() != 1) {
        throw new Exception("JMH returned " + results.size() + " results; expected one");
      }
      RunResult result = results.iterator().next();
      emitSamples(result.getPrimaryResult().getStatistics(), config.maxIters(), count, System.out);
    } catch (Exception e) {
      System.err.print(Files.readString(output));
      throw e;
    } finally {
      Files.deleteIfExists(input);
      Files.deleteIfExists(output);
    }
  }

  static Path createTempFile(String prefix, String suffix) throws Exception {
    Path path = Files.createTempFile(prefix, suffix);
    // Termination signals do not unwind runJmh's finally block. Register
    // shutdown cleanup immediately, including if creating the second file fails.
    path.toFile().deleteOnExit();
    return path;
  }

  static void emitSamples(Statistics statistics, long maxIters, int count, PrintStream output)
      throws Exception {
    long total = statistics.getN();
    long limit = Math.min(total, maxIters);
    if (limit == 0) {
      throw new Exception("JMH returned no samples");
    }
    Iterator<Map.Entry<Double, Long>> samples = statistics.getRawData();
    long seen = 0;
    long emitted = 0;
    while (samples.hasNext()) {
      Map.Entry<Double, Long> sample = samples.next();
      long duration = Math.max(1, Math.round(sample.getKey()));
      seen += sample.getValue();
      // JMH's raw samples are sorted by duration. Select evenly across the
      // cumulative distribution so a small max-iters does not keep only the fastest samples.
      long selected = Math.round((double) seen * limit / total);
      long n = selected - emitted;
      for (long i = 0; i < n; i++) {
        output.printf("%d,%d%n", duration, count);
      }
      emitted = selected;
    }
    if (seen != total || emitted != limit) {
      throw new Exception("JMH sample count changed while reporting results");
    }
  }

  private static <T> List<Sample> sampleDirect(
      BenchmarkConfig config, Callable<T> operation, ToIntFunction<T> count) throws Exception {
    long warmupStart = System.nanoTime();
    for (int i = 0; i < config.maxWarmupIters(); i++) {
      operation.call();
      if ((System.nanoTime() - warmupStart) >= config.maxWarmupTime()) {
        break;
      }
    }

    List<Sample> samples = new ArrayList<>();
    long runStart = System.nanoTime();
    for (int i = 0; i < config.maxIters(); i++) {
      long benchStart = System.nanoTime();
      T result = operation.call();
      long elapsed = System.nanoTime() - benchStart;
      int n = count.applyAsInt(result);
      samples.add(new Sample(elapsed, n));
      if ((System.nanoTime() - runStart) >= config.maxTime()) {
        break;
      }
    }
    return samples;
  }
}
