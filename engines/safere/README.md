This runner benchmarks [SafeRE], a linear-time Java regex library. Its search
models use [JMH] to measure `org.safere.Pattern` and `org.safere.Matcher`.

## Build toolchain

Install a JDK 21 or newer and [Apache Maven 3.9 or newer][maven-install]. Set
`JAVA_HOME` to the JDK and put its `bin` directory and Maven's `bin` directory
on `PATH`. Check that `java -version`, `javac -version`, and `mvn -version` all
work, and that Maven reports the intended JDK. The first build needs access to
Maven Central to download SafeRE, JMH, and the Maven plugins. Rebar invokes the build
script with `sh`, so it also needs a POSIX shell.

`rebar build -e '^safere$'` uses the `org.safere:safere:0.11.0` release,
compiles the runner, copies its runtime dependencies to `target/dependency`, and
checks the Java code with Spotless, Error Prone, and PMD. To benchmark a newer
SafeRE release, change the `org.safere:safere` dependency version in `pom.xml`
and rebuild. Rebar's version command records the SafeRE and JVM versions in
measurements.

Run `mvn verify` from this directory to build and check the runner directly.
Run `mvn spotless:apply` to format Java source before committing changes.

As with `java/hotspot`, the runner rejects invalid UTF-8 haystacks. For normal
measurements, it runs JMH `SampleTime` with one fork and one thread. It uses up
to two warmup iterations and three measurement iterations, dividing Rebar's
warmup and measurement time budgets equally among them. JMH samples individual
invocations; the runner writes those durations in nanoseconds with the checked
result count in Rebar's `duration_ns,count` format. The fork checks the count
before sampling. JMH setup and fork startup are outside those time budgets.
Rebar's `max-iters` limits the number of samples written, while
`max-warmup-iters` limits JMH warmup iterations. Neither option bounds the
number of invocations JMH performs within an iteration. With zero time budgets,
as in `rebar measure --test`, the runner executes directly for correctness.
The unused compilation model also retains the direct Rebar timing loop.

The default 3-second measurement and 1.5-second warmup budgets yield three
1-second measurement iterations and two 750-millisecond warmup iterations.
These are a starting point for discussion: one fork keeps each workload within
Rebar's default 10-second process timeout, but independent forks would give
better evidence about JVM startup variation. Rebar aggregates the JMH sampled
invocations, so its summary statistics do not reflect variation across forks.
SafeRE is intentionally omitted from curated compilation benchmarks for the
same reason `java/hotspot` is omitted. Patterns using unsupported syntax, such
as backreferences or lookaround, are also excluded.

[SafeRE]: https://github.com/eaftan/safere
[JMH]: https://github.com/openjdk/jmh
[maven-install]: https://maven.apache.org/install.html
