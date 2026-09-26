package org.rebar.re2j;

/** A prepared Rebar workload. */
final class Workloads {
  private Workloads() {}

  @FunctionalInterface
  interface Workload {
    int run() throws Exception;
  }
}
