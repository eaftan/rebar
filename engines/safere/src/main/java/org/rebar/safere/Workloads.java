package org.rebar.safere;

final class Workloads {
  private Workloads() {}

  static Workload create(BenchmarkConfig config, InputMode mode) {
    return mode == InputMode.STRING
        ? StringWorkloads.create(config)
        : Utf8Workloads.create(config);
  }

  @FunctionalInterface
  interface Workload {
    int run() throws Exception;
  }
}
