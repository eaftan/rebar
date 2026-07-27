package org.rebar.regex;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

public class RebarBenchmark {
  static final String CONFIG_PROPERTY = "rebar.config";

  @State(Scope.Thread)
  public static class CompileState {
    Config config;

    @Setup
    public void setup() throws IOException {
      config = readConfig();
    }
  }

  @State(Scope.Thread)
  public static class SearchState {
    Config config;
    Pattern regex;

    @Setup
    public void setup() throws IOException {
      config = readConfig();
      regex = config.compileRegex();
    }
  }

  @State(Scope.Thread)
  public static class RegexReduxState {
    Config config;

    @Setup
    public void setup() throws IOException {
      config = readConfig();
    }
  }

  @Benchmark
  public Pattern compile(CompileState state) {
    return state.config.compileRegex();
  }

  @Benchmark
  public int count(SearchState state) {
    return Models.count(state.regex, state.config.haystack());
  }

  @Benchmark
  public int countSpans(SearchState state) {
    return Models.countSpans(state.regex, state.config.haystack());
  }

  @Benchmark
  public int countCaptures(SearchState state) {
    return Models.countCaptures(state.regex, state.config.haystack());
  }

  @Benchmark
  public int grep(SearchState state) {
    return Models.grep(state.regex, state.config.haystack());
  }

  @Benchmark
  public int grepCaptures(SearchState state) {
    return Models.grepCaptures(state.regex, state.config.haystack());
  }

  @Benchmark
  public int regexRedux(RegexReduxState state) {
    return Models.regexRedux(state.config);
  }

  private static Config readConfig() throws IOException {
    String path = System.getProperty(CONFIG_PROPERTY);
    if (path == null) {
      throw new IllegalStateException("missing system property " + CONFIG_PROPERTY);
    }
    return Config.parse(Files.readAllBytes(Path.of(path)));
  }
}
