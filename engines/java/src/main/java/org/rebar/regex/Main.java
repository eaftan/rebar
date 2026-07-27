package org.rebar.regex;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.runner.options.VerboseMode;

public final class Main {
  private static final int FORKS = 2;
  private static final int WARMUP_ITERATIONS = 2;
  private static final int MEASUREMENT_ITERATIONS = 5;
  private static final long MAX_ITERATION_TIME_NANOS =
      TimeUnit.MILLISECONDS.toNanos(500);

  private Main() {}

  public static void main(String... args) throws Exception {
    if (args.length == 1 && args[0].equals("version")) {
      System.out.printf(
          "%s %s%n",
          System.getProperty("java.vm.name"),
          System.getProperty("java.vm.version"));
      return;
    }
    if (args.length != 0) {
      throw new IllegalArgumentException("expected no arguments or 'version'");
    }

    byte[] input = System.in.readAllBytes();
    Config config = Config.parse(input);
    int verificationCount = verify(config);

    if (config.maxIters() == 0) {
      return;
    }

    Path configPath = Files.createTempFile("rebar-java-", ".klv");
    Thread cleanupHook =
        new Thread(() -> deleteConfig(configPath), "rebar-java-config-cleanup");
    Runtime.getRuntime().addShutdownHook(cleanupHook);
    try {
      Files.write(configPath, input);
      Collection<RunResult> results =
          new Runner(options(config, configPath)).run();
      emitResult(results, verificationCount);
    } finally {
      try {
        Runtime.getRuntime().removeShutdownHook(cleanupHook);
      } catch (IllegalStateException ignored) {
        // The hook is already running because the JVM is shutting down.
      }
      deleteConfig(configPath);
    }
  }

  private static void deleteConfig(Path configPath) {
    try {
      Files.deleteIfExists(configPath);
    } catch (java.io.IOException error) {
      System.err.printf(
          "failed to delete temporary benchmark config %s: %s%n",
          configPath,
          error.getMessage());
    }
  }

  private static Options options(Config config, Path configPath) {
    int forks = (int) Math.min(FORKS, config.maxIters());
    if (config.maxWarmupIters() > 0) {
      forks = (int) Math.min(forks, config.maxWarmupIters());
    }
    int warmupIterations =
        config.maxWarmupIters() == 0
            ? 0
            : (int)
                Math.min(
                    WARMUP_ITERATIONS, config.maxWarmupIters() / forks);
    int measurementIterations =
        (int)
            Math.min(MEASUREMENT_ITERATIONS, config.maxIters() / forks);

    List<String> forkJvmArgs = new ArrayList<>();
    forkJvmArgs.add(
        "-D" + RebarBenchmark.CONFIG_PROPERTY + "=" + configPath.toAbsolutePath());
    if (Runtime.version().feature() >= 24) {
      // JMH 1.37 uses sun.misc.Unsafe. JDK 24 and newer otherwise relay a
      // deprecation warning from every fork to the controller's stdout, which
      // would corrupt rebar's result protocol.
      forkJvmArgs.add("--sun-misc-unsafe-memory-access=allow");
    }

    ChainedOptionsBuilder options =
        new OptionsBuilder()
            .include(
                "^"
                    + RebarBenchmark.class.getName()
                    + "\\."
                    + methodName(config.model())
                    + "$")
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.NANOSECONDS)
            .threads(1)
            .forks(forks)
            .warmupIterations(warmupIterations)
            .measurementIterations(measurementIterations)
            .measurementTime(
                iterationTime(
                    config.maxTime(), measurementIterations, forks))
            .jvmArgsAppend(forkJvmArgs.toArray(String[]::new))
            .verbosity(VerboseMode.SILENT)
            .shouldFailOnError(true);
    if (warmupIterations > 0) {
      options.warmupTime(
          iterationTime(
              config.maxWarmupTime(), warmupIterations, forks));
    }
    return options.build();
  }

  private static TimeValue iterationTime(
      long budgetNanos, int iterations, int forks) {
    long nanos = Math.max(1, budgetNanos / iterations / forks);
    return TimeValue.nanoseconds(
        Math.min(MAX_ITERATION_TIME_NANOS, nanos));
  }

  private static String methodName(String model) {
    return switch (model) {
      case "compile" -> "compile";
      case "count" -> "count";
      case "count-spans" -> "countSpans";
      case "count-captures" -> "countCaptures";
      case "grep" -> "grep";
      case "grep-captures" -> "grepCaptures";
      case "regex-redux" -> "regexRedux";
      default -> throw new IllegalArgumentException("unrecognized benchmark model " + model);
    };
  }

  private static int verify(Config config) {
    return switch (config.model()) {
      case "compile", "count" ->
          Models.count(config.compileRegex(), config.haystack());
      case "count-spans" ->
          Models.countSpans(config.compileRegex(), config.haystack());
      case "count-captures" ->
          Models.countCaptures(config.compileRegex(), config.haystack());
      case "grep" ->
          Models.grep(config.compileRegex(), config.haystack());
      case "grep-captures" ->
          Models.grepCaptures(config.compileRegex(), config.haystack());
      case "regex-redux" -> Models.regexRedux(config);
      default -> throw new IllegalArgumentException(
          "unrecognized benchmark model " + config.model());
    };
  }

  private static void emitResult(Collection<RunResult> runResults, int count)
      throws RunnerException {
    if (runResults.size() != 1) {
      throw new RunnerException(
          "expected one JMH run result, got " + runResults.size());
    }
    RunResult runResult = runResults.iterator().next();
    var primary = runResult.getPrimaryResult();
    if (!primary.getScoreUnit().equals("ns/op")) {
      throw new RunnerException(
          "unexpected aggregate JMH score unit: " + primary.getScoreUnit());
    }
    double score = primary.getScore();
    if (!Double.isFinite(score) || score < 0) {
      throw new RunnerException("JMH produced an invalid aggregate result");
    }
    System.out.printf("%d,%d%n", Math.max(1, Math.round(score)), count);
  }
}
