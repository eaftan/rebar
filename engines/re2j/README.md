This runner benchmarks [RE2/J], a pure Java port of RE2, as the `re2j`
engine. It uses RE2/J's String API and [JMH], following the Maven and
measurement setup of [the SafeRE runner PR][safere-pr].

## Build

Install JDK 21 or newer and Apache Maven 3.9 or newer. Set `JAVA_HOME` and
`PATH` so that `java -version`, `javac -version`, and `mvn -version` use
the intended JDK. The first build needs access to Maven Central.

Run `rebar build -e '^re2j$'`, or `mvn clean verify` in this directory.
The Maven build compiles the runner and JMH annotation processor output,
runs the JUnit tests, and copies runtime dependencies to `target/dependency`.
Rebar invokes `mvn -q clean verify` directly.

The dependency is pinned to `com.google.re2j:re2j:1.8`. To benchmark a newer
release, change `re2j.version` in `pom.xml` and rebuild. Maven generates
the runner's version resource from that same property because the published
RE2/J JAR does not contain version metadata. Measurements record both the
RE2/J dependency version and the JVM name and version.

## Measurements

Search measurements use JMH 1.37 `SampleTime` with one fork and one thread.
The runner maps a positive Rebar measurement time budget to one JMH measurement
iteration of that duration. A positive warmup time budget enables one warmup
iteration when `max-warmup-iters` is positive. Parsing, UTF-8 decoding,
pattern compilation for search models, and correctness checks happen before
timed invocations. The benchmark returns its result for JMH to consume.

The runner emits individual samples in Rebar's `duration_ns,count` format.
`max-iters` limits reported samples, selecting evenly across JMH's duration
distribution when needed. It does not limit JMH invocations. Similarly,
`max-warmup-iters` enables or disables JMH warmup rather than bounding its
invocations. JMH setup and fork startup are outside these time budgets.
One fork fits Rebar's usual process timeout, but the results do not describe
variation across independent JVM forks.

Rebar isolates runner process trees and terminates them on timeout or
interruption, so the JMH child JVM cannot continue running after its runner
is stopped.

A zero measurement budget, including `rebar measure --test`, uses the
reference runner's direct timing loop. The `compile` model also uses this
loop: it times only compilation and verifies the compiled pattern afterwards.
Compilation timings are subject to JVM JIT effects, as discussed for the
[existing Java runner](../java/README.md).
Compilation performance workloads are excluded for this reason, following
SafeRE and `java/hotspot`. Only `test/model/compile` remains enabled as a
basic compilation correctness check.

For a short JMH smoke test:

```
rebar measure -e '^re2j$' -f '^test/model/count$' \
  --max-time 100ms --max-warmup-time 100ms --max-iters 5
```

## Input and regex semantics

The runner strictly decodes UTF-8 before timing and searches Java strings.
It reports match spans in UTF-16 code units. Matching itself consumes
Unicode codepoints, including supplementary characters.

RE2/J always uses Unicode case folding for case-insensitive matching, while
`\w`, `\d`, `\s`, and `\b` use ASCII definitions. Rebar's `unicode`
toggle has no equivalent RE2/J flag and is not applied. Unicode properties
supported by RE2/J remain available through explicit pattern syntax.

RE2/J 1.8's generated character-property tables use Unicode 6.0. This can
change counts relative to engines with newer Unicode tables.

Multiple-pattern workloads are rejected rather than combining patterns or
silently using only the last. Unsupported syntax and incompatible workload
semantics are excluded based on the full correctness sweep; see
[WORKLOADS.md](WORKLOADS.md) for the audit.

[RE2/J]: https://github.com/google/re2j
[JMH]: https://github.com/openjdk/jmh
[safere-pr]: https://github.com/eaftan/rebar/pull/1
