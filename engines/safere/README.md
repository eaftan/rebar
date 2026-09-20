This runner benchmarks [SafeRE], a linear-time Java regex library, using the
same benchmark models and timing loop as Rebar's `java/hotspot` runner. The only
regex API change is to use `org.safere.Pattern` and `org.safere.Matcher`.

## Build toolchain

Install a JDK 21 or newer and [Apache Maven 3.9 or newer][maven-install]. Set
`JAVA_HOME` to the JDK and put its `bin` directory and Maven's `bin` directory
on `PATH`. Check that `java -version`, `javac -version`, and `mvn -version` all
work, and that Maven reports the intended JDK. The first build needs access to
Maven Central to download SafeRE and the Maven plugins. Rebar invokes the build
script with `sh`, so it also needs a POSIX shell.

`rebar build -e '^safere$'` uses the `org.safere:safere:0.11.0` release,
compiles the runner, copies its runtime dependency to `target/dependency`, and
checks the Java code with Spotless, Error Prone, and PMD. To benchmark a newer
SafeRE release, change the `org.safere:safere` dependency version in `pom.xml`
and rebuild. Rebar's version command records the SafeRE and JVM versions in
measurements.

Run `mvn verify` from this directory to build and check the runner directly.
Run `mvn spotless:apply` to format Java source before committing changes.

As with `java/hotspot`, the runner rejects invalid UTF-8 haystacks. Both Java
runners use Rebar's warmup and `System.nanoTime()` sampling scheme. Repeating a
fixed pattern and haystack can interact with HotSpot's JIT, so these results
should be interpreted as Rebar measurements rather than JMH measurements.
SafeRE is intentionally omitted from curated compilation benchmarks for the
same reason `java/hotspot` is omitted. Patterns using unsupported syntax, such
as backreferences or lookaround, are also excluded.

[SafeRE]: https://github.com/eaftan/safere
[maven-install]: https://maven.apache.org/install.html
