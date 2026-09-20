This runner benchmarks [SafeRE], a linear-time Java regex library, using the
same benchmark models and timing loop as Rebar's `java/hotspot` runner. The only
regex API change is to use `org.safere.Pattern` and `org.safere.Matcher`.

`rebar build -e '^safere$'` needs Java 21 or newer and Maven. The Maven build
refreshes `org.safere:safere:0.12.0-SNAPSHOT` from SafeRE's Central Portal
snapshot repository, compiles the runner, copies its runtime dependency to
`target/dependency`, and checks the Java code with Spotless, Error Prone, and
PMD. Rebar's version command records the SafeRE version, SHA-256 of the exact
resolved JAR, and JVM identity so results from changing snapshots remain
distinguishable. Snapshots may expire, and the dependency version will need to
advance when SafeRE begins its next development cycle.

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
