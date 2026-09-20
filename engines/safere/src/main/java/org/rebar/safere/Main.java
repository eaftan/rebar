package org.rebar.safere;

import java.io.InputStream;
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
import org.safere.Pattern;
import org.safere.Utf8Input;

public final class Main {
  private record Sample(long duration, int count) {}

  private Main() {}

  public static void main(String... args) throws Exception {
    if (args.length < 1 || args.length > 2) {
      throw new IllegalArgumentException("usage: Main <string|utf8> [version]");
    }
    InputMode mode = InputMode.parse(args[0]);
    if (args.length == 2 && args[1].equals("version")) {
      printVersion();
      return;
    }
    if (args.length != 1) {
      throw new IllegalArgumentException("usage: Main <string|utf8> [version]");
    }

    byte[] raw = System.in.readAllBytes();
    BenchmarkConfig config = BenchmarkConfig.parse(raw, mode);
    if (config.model().equals("compile")) {
      Utf8Input utf8Input =
          mode == InputMode.UTF8 ? Utf8Input.validated(config.haystackBytes()) : null;
      for (Sample sample :
          sampleDirect(
              config,
              config::compileRegex,
              pattern ->
                  mode == InputMode.UTF8
                      ? Utf8Workloads.countMatches(pattern, utf8Input)
                      : Workloads.countMatches(pattern, config.haystack()))) {
        System.out.printf("%d,%d%n", sample.duration(), sample.count());
      }
      return;
    }

    Workloads.IntWorkload workload = Workloads.create(config, mode);
    // Rebar's --test runs one operation to check its count. Starting a JMH fork
    // for every correctness check adds no useful measurement information.
    if (config.maxTime() == 0 && config.maxWarmupTime() == 0) {
      for (Sample sample : sampleDirect(config, workload::run, Integer::intValue)) {
        System.out.printf("%d,%d%n", sample.duration(), sample.count());
      }
      return;
    }
    int count = workload.run();
    runJmh(raw, config, mode, count);
  }

  private static void printVersion() throws Exception {
    Properties properties = new Properties();
    try (InputStream stream =
        Pattern.class.getResourceAsStream("/META-INF/maven/org.safere/safere/pom.properties")) {
      if (stream == null) {
        throw new IllegalStateException("SafeRE version metadata is missing");
      }
      properties.load(stream);
    }
    String version = properties.getProperty("version");
    if (version == null) {
      throw new IllegalStateException("SafeRE version metadata has no version");
    }
    System.out.printf(
        "SafeRE %s (%s %s; scanner=%s)%n",
        version,
        System.getProperty("java.vm.name"),
        System.getProperty("java.vm.version"),
        System.getProperty("org.safere.experimental.vectorScanProvider", "swar"));
  }

  private static void runJmh(byte[] raw, BenchmarkConfig config, InputMode mode, int count)
      throws Exception {
    Path input = Files.createTempFile("rebar-safere-", ".klv");
    Path output = Files.createTempFile("rebar-safere-jmh-", ".log");
    try {
      Files.write(input, raw);
      int warmupIterations = config.maxWarmupIters() > 0 && config.maxWarmupTime() > 0 ? 1 : 0;
      long measurementNs = Math.max(1_000_000L, config.maxTime());
      long warmupNs = Math.max(1_000_000L, config.maxWarmupTime());
      Options options =
          new OptionsBuilder()
              .include("^" + SafeReBenchmark.class.getName() + ".run$")
              .mode(Mode.SampleTime)
              .timeUnit(TimeUnit.NANOSECONDS)
              .forks(1)
              .threads(1)
              .warmupIterations(warmupIterations)
              .warmupTime(TimeValue.nanoseconds(warmupNs))
              .measurementIterations(1)
              .measurementTime(TimeValue.nanoseconds(measurementNs))
              .jvmArgsAppend(
                  "--add-modules=jdk.incubator.vector",
                  "-Dorg.safere.experimental.vectorScanProvider=vector",
                  "-Drebar.safere.input=" + input,
                  "-Drebar.safere.mode=" + mode.argument(),
                  "-Drebar.safere.count=" + count)
              .output(output.toString())
              .build();
      var results = new Runner(options).run();
      if (results.size() != 1) {
        throw new Exception("JMH returned " + results.size() + " results; expected one");
      }
      RunResult result = results.iterator().next();
      Statistics statistics = result.getPrimaryResult().getStatistics();
      long total = statistics.getN();
      long limit = Math.min(total, config.maxIters());
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
          System.out.printf("%d,%d%n", duration, count);
        }
        emitted = selected;
      }
      if (seen != total || emitted != limit) {
        throw new Exception("JMH sample count changed while reporting results");
      }
    } catch (Exception e) {
      System.err.print(Files.readString(output));
      throw e;
    } finally {
      Files.deleteIfExists(input);
      Files.deleteIfExists(output);
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
