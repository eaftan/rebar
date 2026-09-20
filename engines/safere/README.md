This runner benchmarks [SafeRE], a linear-time Java regex library, using the
same benchmark models and timing loop as Rebar's `java/hotspot` runner. The only
regex API change is to use `org.safere.Pattern` and `org.safere.Matcher`.

`rebar build -e '^safere$'` needs Java 21 or newer, `javac`, Maven, and access
to the `org.safere:safere:0.11.0` release. It copies the pinned JAR into this
directory and compiles `Main.java`. Rebar's version command loads SafeRE and
reads the version embedded in that JAR, so the version is also a build receipt.
The recorded version includes the JVM name and version to distinguish results
from different Java runtimes.

As with `java/hotspot`, the runner rejects invalid UTF-8 haystacks. Both Java
runners use Rebar's warmup and `System.nanoTime()` sampling scheme. Repeating a
fixed pattern and haystack can interact with HotSpot's JIT, so these results
should be interpreted as Rebar measurements rather than JMH measurements.
SafeRE is intentionally omitted from curated compilation benchmarks for the
same reason `java/hotspot` is omitted. Patterns using unsupported syntax, such
as backreferences or lookaround, are also excluded.

[SafeRE]: https://github.com/eaftan/safere
