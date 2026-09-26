This runner benchmarks [SafeRE], a linear-time Java regex library, as three Rebar
engines. `safere/string` searches Java strings with `org.safere.Matcher` and
reports UTF-16 match offsets. `safere/utf8` searches the original UTF-8 bytes
with `org.safere.Utf8Matcher` and reports byte offsets. `safere/utf8-vector`
uses the same UTF-8 API with SafeRE's experimental Vector API scanner enabled.
All three use [JMH] for search measurements and share one Maven build.

## Build toolchain

Install a JDK 21 or newer and [Apache Maven 3.9 or newer][maven-install]. Set
`JAVA_HOME` to the JDK and put its `bin` directory and Maven's `bin` directory
on `PATH`. Check that `java -version`, `javac -version`, and `mvn -version` all
work, and that Maven reports the intended JDK. The first build needs access to
Maven Central to download SafeRE, JMH, and the Maven plugins. Rebar invokes
Maven directly with `mvn -q clean verify`.

`rebar build -e '^safere/(string|utf8|utf8-vector)$'` uses the
`org.safere:safere:0.11.0` release, compiles the runner, copies its runtime
dependencies to `target/dependency`, and runs the JUnit tests. To benchmark a
newer SafeRE release, change the `org.safere:safere` dependency version in
`pom.xml` and rebuild. Rebar's version command records the SafeRE version, JVM
version, and configured scanner in measurements.

Run `mvn verify` from this directory to build and test the runner directly.

`safere/string` and `safere/utf8` use SafeRE's default scanner.
`safere/utf8-vector` enables the experimental Vector API scanner with
`--add-modules=jdk.incubator.vector` and
`-Dorg.safere.experimental.vectorScanProvider=vector`. The runner also passes
those flags to the JMH fork. This lets SafeRE use SIMD instructions for
supported scans when the JVM and CPU can run them. A JDK with the incubator
module is required to run `safere/utf8-vector`.

All three engines reject invalid UTF-8 haystacks. The String engine decodes the
haystack before timing; the UTF-8 engines validate it once before timing and
then searches borrowed byte views. UTF-8 grep splits lines over the original
bytes at LF, strips one trailing CR, and preserves embedded bare CR. String
grep follows the same line rules. UTF-8 grep uses SafeRE's capture-free
`Pattern.find(Utf8Input)` for boolean
line checks. UTF-8 regex-redux performs replacements through `Utf8Sink`.
`count-spans` sums UTF-16 code units for the String engine and bytes for the
UTF-8 engine, so Rebar uses engine-specific expected counts where needed.

For normal measurements, the runner uses JMH `SampleTime` with one fork and one
thread. It uses one warmup iteration for Rebar's full warmup time budget when
warmup is enabled, then one measurement iteration for the full measurement
time budget. JMH samples individual invocations; the runner writes their
durations and checked result count in Rebar's `duration_ns,count` format. The
fork checks the count before sampling. JMH setup and fork startup are outside
those time budgets.
Rebar's `max-iters` limits the number of samples written. If JMH records more
samples, the runner selects evenly across their duration distribution rather
than retaining only the fastest. `max-warmup-iters` controls whether JMH warms
up at all: zero skips warmup, and any positive value enables its single warmup
iteration when the warmup time budget is positive. Neither iteration limit
bounds the number of invocations JMH performs within an iteration. With a zero
measurement time budget, as in `rebar measure --test`, the runner uses the
direct loop and still runs any configured warmup before its one measurement.
The unused compilation model also retains the direct Rebar timing loop.

The default 3-second measurement and 1.5-second warmup budgets yield one
3-second measurement iteration and one 1.5-second warmup iteration. One fork
helps each workload fit Rebar's default 10-second process timeout, but
independent forks would give better evidence about JVM startup variation.
Rebar aggregates the JMH sampled invocations, so its summary statistics do not
reflect variation across forks.
SafeRE is intentionally omitted from curated compilation benchmarks for the
same reason `java/hotspot` is omitted. Patterns using unsupported syntax, such
as backreferences or lookaround, are also excluded.

The [workload comparison](WORKLOADS.md) documents the current differences
against RE2/J 1.8 and explains each engine-specific workload exclusion.
Each SafeRE mode selects 275 workloads. The runner explicitly rejects
multi-pattern input instead of silently measuring its last pattern.

[SafeRE]: https://github.com/eaftan/safere
[JMH]: https://github.com/openjdk/jmh
[maven-install]: https://maven.apache.org/install.html
