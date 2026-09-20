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
import org.safere.Pattern;

public final class Main {
  private record Sample(long duration, int count) {}

  private Main() {}

  public static void main(String... args) throws Exception {
    if (args.length == 1 && args[0].equals("version")) {
      printVersion();
      return;
    }

    byte[] raw = System.in.readAllBytes();
    BenchmarkConfig config = BenchmarkConfig.parse(raw);
    if (config.model().equals("compile")) {
      for (Sample sample :
          sampleDirect(
              config,
              config::compileRegex,
              pattern -> Workloads.countMatches(pattern, config.haystack()))) {
        System.out.printf("%d,%d%n", sample.duration(), sample.count());
      }
      return;
    }

    Workloads.IntWorkload workload = Workloads.create(config);
    // Rebar's --test runs one operation to check its count. Starting a JMH fork
    // for every correctness check adds no useful measurement information.
    if (config.maxTime() == 0 && config.maxWarmupTime() == 0) {
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
        "SafeRE %s (%s %s)%n",
        version, System.getProperty("java.vm.name"), System.getProperty("java.vm.version"));
  }

  private static void runJmh(byte[] raw, BenchmarkConfig config, int count) throws Exception {
    Path input = Files.createTempFile("rebar-safere-", ".klv");
    Path output = Files.createTempFile("rebar-safere-jmh-", ".log");
    try {
      Files.write(input, raw);
      int measurementIterations = Math.min(3, config.maxIters());
      int warmupIterations = Math.min(2, config.maxWarmupIters());
      long measurementNs = Math.max(1_000_000L, config.maxTime() / measurementIterations);
      long warmupNs =
          warmupIterations == 0
              ? 1_000_000L
              : Math.max(1_000_000L, config.maxWarmupTime() / warmupIterations);
      Options options =
          new OptionsBuilder()
              .include("^" + SafeReBenchmark.class.getName() + ".run$")
              .mode(Mode.SampleTime)
              .timeUnit(TimeUnit.NANOSECONDS)
              .forks(1)
              .threads(1)
              .warmupIterations(warmupIterations)
              .warmupTime(TimeValue.nanoseconds(warmupNs))
              .measurementIterations(measurementIterations)
              .measurementTime(TimeValue.nanoseconds(measurementNs))
              .jvmArgsAppend("-Drebar.safere.input=" + input, "-Drebar.safere.count=" + count)
              .output(output.toString())
              .build();
      var results = new Runner(options).run();
      if (results.size() != 1) {
        throw new Exception("JMH returned " + results.size() + " results; expected one");
      }
      RunResult result = results.iterator().next();
      Iterator<Map.Entry<Double, Long>> samples =
          result.getPrimaryResult().getStatistics().getRawData();
      long emitted = 0;
      while (samples.hasNext() && emitted < config.maxIters()) {
        Map.Entry<Double, Long> sample = samples.next();
        long duration = Math.max(1, Math.round(sample.getKey()));
        long n = Math.min(sample.getValue(), config.maxIters() - emitted);
        for (long i = 0; i < n; i++) {
          System.out.printf("%d,%d%n", duration, count);
        }
        emitted += n;
      }
      if (emitted == 0) {
        throw new Exception("JMH returned no samples");
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
