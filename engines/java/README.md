This directory contains a Java runner program for benchmarking [Java regular
expressions][java-regex]. Specifically, this is for the `java.util.regex`
package in the JDK. Java's regex engine is principally backtracking based.

This program otherwise makes the following choices:

* It will throw an exception if given a haystack that contains invalid UTF-8.
Namely, as far as I can tell, there is no way to use Java's regex engine on
arbitrary bytes. Its API seems to suggest that it is only possible to run it on
sequences of UTF-16 code units.

## Benchmark harness

This runner uses the [Java Microbenchmark Harness][jmh] (JMH). Rebar still
provides the benchmark definition over stdin and checks the result count, but
JMH controls the generated invocation loop, warmup, measurement and JVM forks.

JMH is the OpenJDK benchmark harness used for Java and other JVM
microbenchmarks. OpenJDK's in-tree microbenchmark suite is itself
[based on JMH][jep-230]. In this adapter it:

* JMH generates and calibrates the invocation loop, amortizing timer and loop
  overhead over many operations.
* Warmup is separated from measurement in every fork, allowing HotSpot's
  interpreter, tiered compilation and profile-guided optimization to settle
  before scores are collected.
* Independent JVM forks prevent one long-lived VM's compiled code, profiles
  and other process state from becoming the entire sample.
* Benchmark return values are consumed by JMH, making it harder for the JIT to
  eliminate work whose result would otherwise be unused.
* JMH computes the aggregate average-time score using its own result model.

The runner uses one fixed JMH configuration:

* Average-time mode, reported as nanoseconds per operation.
* 1 benchmark thread.
* 2 independent JVM forks.
* 2 warmup iterations per fork.
* 5 measurement iterations per fork.
* At most 500 milliseconds per warmup or measurement iteration.

This configuration was selected by measuring repeatability and runtime across
a representative sample of Rebar workloads. Rebar's limits may reduce the
iteration counts and time budgets, but cannot increase them. For this runner,
`max-iters` and `max-warmup-iters` limit JMH iterations rather than individual
regex operations.

The runner emits JMH's aggregate average-time score, rounded to the nearest
nanosecond, as one ordinary Rebar `duration,count` sample. This keeps the
existing runner protocol and makes Rebar's median of that one sample equal to
the JMH score within the protocol's integer-nanosecond precision. The
verification count is computed separately, outside the measured JMH forks.

If Rebar's timeout expires, it terminates the JMH controller and its fork JVMs
as one process tree.

## Prerequisites and JDK selection

The Java adapter is a Maven project. Maven and a JDK are optional Rebar engine
dependencies: they are needed only when building or running
`java/hotspot`. Building Rebar itself and using other engines does not require
either one.

The adapter requires:

* JDK 17 or newer. Rebar does not download or bundle a JDK.
* Maven, installed using the
  [official Maven installation instructions][maven-install]. Maven can be
  installed with most system package managers or from its binary distribution.
  The first build downloads JMH and its build dependencies using Maven's
  normal repository configuration; subsequent builds can use Maven's local
  cache.

There are two Java selections involved:

* Maven uses the JDK reported by `mvn --version`, normally selected through
  `JAVA_HOME`.
* Rebar runs the `java` executable found on `PATH`. JMH then launches its fork
  JVMs from that controller JVM's `java.home`, so the controller and benchmark
  forks use the same JDK.

Set both `JAVA_HOME` and `PATH` when the machine has multiple JDKs:

```bash
export JAVA_HOME=/path/to/jdk
export PATH="$JAVA_HOME/bin:$PATH"

java -version
mvn --version
```

The two version commands should identify the intended installation, and the
runtime reported by `java -version` must be JDK 17 or newer. Recording
`java/hotspot` results also records the runtime VM version reported by the
adapter. The Maven build compiles the adapter with `--release 17`, regardless
of which newer supported JDK performs the build.

## Building and validating

JMH is pinned in `pom.xml`, and Maven builds a shaded
`target/rebar-java.jar` containing the adapter and its runtime dependencies.
The normal Rebar engine configuration builds it with:

```bash
rebar build -e '^java/hotspot$'
rebar measure -e '^java/hotspot$' -f '^test/' --test
```

It can also be built directly:

```bash
mvn -f engines/java/pom.xml package
```

For a standard measurement:

```bash
rebar measure -e '^java/hotspot$' -f '^curated/'
```

## Compilation

The `compile` model measures steady-state compilation. It repeatedly calls
`Pattern.compile` after JVM warmup, while returning each resulting `Pattern` to
JMH so that the work cannot be eliminated. This does not measure the latency of
the first compilation in a fresh JVM.

Java remains excluded from the curated compilation benchmarks. A cold
compilation benchmark would need a distinct single-shot configuration with no
warmup and a fresh JVM fork for each measurement.

## Unicode

Java's regex engine has pretty good Unicode support. It does Unicode case
folding for case insensitive matching, `\w`/`\s`/`\d` are all Unicode-aware.
`\b` is also Unicode-aware. And things like `.` match entire codepoints.

Java also supports disabling Unicode mode, with independent toggles for
Unicode case folding and Unicode character classes. The `unicode` toggle
in rebar's benchmark definition toggles both of Java's Unicode options in
tandem.

[java-regex]: https://docs.oracle.com/javase/7/docs/api/java/util/regex/Pattern.html
[jmh]: https://github.com/openjdk/jmh
[jep-230]: https://openjdk.org/jeps/230
[maven-install]: https://maven.apache.org/install.html
