# RE2/J workload eligibility

The initial RE2/J 1.8 correctness sweep enabled all 360 workload definitions:
264 passed and 96 failed. Failures were investigated before removing entries.

28 initially failing workloads were retained after fixes:

- Two grep workloads exposed the reference runner's use of `String.lines()`,
  which incorrectly splits bare CR for Rebar's LF/CRLF model. The runner now
  preserves embedded CR and strips a final CR from each LF-delimited line.
- Nine span-count workloads needed UTF-16 expected counts.
- Fifteen functionality tests needed RE2/J in their existing engine-specific
  expected-count rules (ASCII Perl classes/boundaries, Unicode case folding,
  codepoint matching, and end-of-input dollar semantics).
- Two Unicode property workloads needed counts for RE2/J's Unicode 6.0 tables.
  The 930 hundred-letter matches and 33 Greek runs were independently checked
  against [the release's generated tables][tables].

The remaining 68 workloads are excluded below. The three full-dictionary
timeouts were retried with a 60-second timeout; all reached a
`StackOverflowError` in RE2/J's `Machine.add`, including the compilation
model's untimed verification search. No workload was excluded solely because
it exceeded the initial 10-second timeout.

The compatibility sweep passed all 292 selected workloads, including 27
compilation workloads. After that sweep, 26 compilation performance workloads
were also removed to follow SafeRE and `java/hotspot`'s measurement policy:
repeated compilation of invariant patterns can produce misleading JVM timings.
These are policy exclusions, not regex incompatibilities.

The final selection is 266 workloads. The only remaining compilation workload,
`test/model/compile`, checks basic correctness using the reference runner's
direct loop; see [README.md](README.md).

A subsequent [bidirectional SafeRE comparison](SAFERE_COMPARISON.md) verified
all 23 initial differences and then tested SafeRE on every non-compilation
workload. It added 16 missing SafeRE workloads in total and verified the
remaining two RE2/J-only and eleven SafeRE-only exclusions.

## Verification

Checks used OpenJDK 26.0.1 and Maven 3.9.16, compiling with Java release 21:

- `cargo build --locked`
- `cargo test --locked` (19 tests, including process-tree cleanup on timeout
  and interruption)
- `rebar build -e '^re2j$'` (Maven clean/verify and eight JUnit tests)
- `rebar measure -e '^re2j$' --test --timeout 10s`
- Short positive-budget measurements of all five basic search models,
  regex-redux, and representative curated/span workloads through JMH forks.
- A forced three-second timeout during a ten-second JMH measurement leaves
  no surviving runner descendants; a normal short JMH measurement succeeds.
- Timeout and interruption checks leave no temporary KLV or JMH log files
  on Unix, where shutdown cleanup backs up the normal-path cleanup.
- `git diff --check`

## Incompatible workloads

| Workload | Observed incompatibility |
| --- | --- |
| `aho-corasick/compile/dictionary-english` | Multiple patterns; no RE2/J multi-pattern search API. |
| `aho-corasick/compile/dictionary-english-10` | Multiple patterns; no RE2/J multi-pattern search API. |
| `aho-corasick/compile/dictionary-english-15` | Multiple patterns; no RE2/J multi-pattern search API. |
| `aho-corasick/dictionary/english` | Multiple patterns; no RE2/J multi-pattern search API. |
| `aho-corasick/dictionary/english-tiny` | Multiple patterns; no RE2/J multi-pattern search API. |
| `aho-corasick/dictionary/english-10` | Multiple patterns; no RE2/J multi-pattern search API. |
| `aho-corasick/dictionary/english-15` | Multiple patterns; no RE2/J multi-pattern search API. |
| `aho-corasick/dictionary/i787-noword` | Multiple patterns; no RE2/J multi-pattern search API. |
| `aho-corasick/teddy/sherlock4-nocase-ascii` | Multiple patterns; no RE2/J multi-pattern search API. |
| `aho-corasick/teddy/sherlock4-nocase-unicode` | Multiple patterns; no RE2/J multi-pattern search API. |
| `aho-corasick/teddy/sherlock5-nocase-ascii` | Multiple patterns; no RE2/J multi-pattern search API. |
| `aho-corasick/teddy/sherlock5-nocase-unicode` | Multiple patterns; no RE2/J multi-pattern search API. |
| `aho-corasick/teddy/reported-i787-keywords` | Multiple patterns; no RE2/J multi-pattern search API. |
| `curated/03-date/unicode` | Unicode digit classes required; RE2/J uses ASCII `\d`. count mismatch, expected 111841, got 111817. |
| `curated/03-date/compile-unicode` | Unicode digit classes required; RE2/J uses ASCII `\d`. count mismatch, expected 5, got 2. |
| `curated/05-lexer-veryl/multi` | Multiple patterns; no RE2/J multi-pattern search API. |
| `curated/08-words/all-russian` | Unicode word classes/boundaries required; RE2/J uses ASCII definitions. count mismatch, expected 107391, got 529. |
| `curated/08-words/long-russian` | Unicode word classes/boundaries required; RE2/J uses ASCII definitions. count mismatch, expected 5481, got 12. |
| `curated/12-dictionary/multi` | Multiple patterns; no RE2/J multi-pattern search API. |
| `curated/12-dictionary/compile-multi` | Multiple patterns; no RE2/J multi-pattern search API. |
| `curated/13-noseyparker/single` | invalid repeat count: `{20,1024}`. |
| `curated/13-noseyparker/multi` | Multiple patterns; no RE2/J multi-pattern search API. |
| `curated/13-noseyparker/compile-single` | invalid repeat count: `{20,1024}`. |
| `curated/13-noseyparker/compile-multi` | Multiple patterns; no RE2/J multi-pattern search API. |
| `dictionary/compile/english` | RE2/J `Machine.add` stack overflow (confirmed with a 60-second timeout). |
| `dictionary/search/english` | RE2/J `Machine.add` stack overflow (confirmed with a 60-second timeout). |
| `dictionary/search/english-tiny` | RE2/J `Machine.add` stack overflow (confirmed with a 60-second timeout). |
| `grep/long-words-unicode` | Unicode word classes/boundaries required; RE2/J uses ASCII definitions. count mismatch, expected 5075, got 5073. |
| `opt/backtrack/words-russian` | Unicode word classes/boundaries required; RE2/J uses ASCII definitions. count mismatch, expected 27996, got 16. |
| `opt/fixed-length/too-small-unicode` | invalid character class range: `\p{math}`. |
| `opt/literal-alt/pattern-per-word` | Multiple patterns; no RE2/J multi-pattern search API. |
| `opt/nfa-sparse/small-repeated-class-bytes` | invalid or unsupported Perl syntax: `(?-u`. |
| `opt/nfa-sparse/small-repeated-class-unicode` | invalid or unsupported Perl syntax: `(?-u`. |
| `opt/onepass/first-three-words-russian` | Unicode word classes/boundaries required; RE2/J uses ASCII definitions. count mismatch, expected 19224, got 4. |
| `opt/onepass/word-boundary-english` | Byte-versus-character matching: the ASCII regex over UTF-8 bytes gives 579 captures, but character searches (also independently reproduced with JDK regex) give 573. |
| `opt/onepass/word-boundary-russian` | Unicode word classes/boundaries required; RE2/J uses ASCII definitions. count mismatch, expected 873, got 3. |
| `test/unicode/invalid-utf8/dot-matches-xFF` | Invalid UTF-8; not representable by this String runner. |
| `test/unicode/invalid-utf8/dot-no-matches-xFF` | Invalid UTF-8; not representable by this String runner. |
| `test/unicode/invalid-utf8/dot-matches-codepoint-prefix` | Invalid UTF-8; not representable by this String runner. |
| `test/unicode/invalid-utf8/dot-no-matches-codepoint-prefix` | Invalid UTF-8; not representable by this String runner. |
| `test/unicode/invalid-utf8/xFF-matches-xFF` | Invalid UTF-8; not representable by this String runner. |
| `test/unicode/letter/pLetter-matches-bmp-delta` | invalid character class range: `\p{Letter}`. |
| `test/unicode/letter/pLetter-casei-matches-bmp-delta` | invalid character class range: `\p{lEtTeR}`. |
| `test/unicode/letter/pLetter-gc-equals-matches-bmp-delta` | invalid character class range: `\p{gc=Letter}`. |
| `test/unicode/letter/pLetter-gc-colon-matches-bmp-delta` | invalid character class range: `\p{gc:Letter}`. |
| `unicode/codepoints/letters-alt` | invalid or unsupported Perl syntax: `(?x`. |
| `unicode/codepoints/letters-lower-or-upper` | invalid character class range: `\p{Lowercase}`. |
| `unicode/compile/match-every-line` | Invalid UTF-8; not representable by this String runner. |
| `unicode/compile/match-every-line-ascii` | Invalid UTF-8; not representable by this String runner. |
| `unicode/compile/huge-character-class` | invalid escape sequence: `\u`. |
| `unicode/word/boundary-any-russian` | Unicode word classes/boundaries required; RE2/J uses ASCII definitions. count mismatch, expected 529194, got 638. |
| `unicode/word/boundary-long-russian` | Unicode word classes/boundaries required; RE2/J uses ASCII definitions. count mismatch, expected 21332, got 0. |
| `unicode/word/around-holmes-russian` | Unicode word classes/boundaries required; RE2/J uses ASCII definitions. count mismatch, expected 44, got 0. |
| `wild/grapheme/compile` | invalid or unsupported Perl syntax: `(?x`. |
| `wild/grapheme/source-code` | invalid or unsupported Perl syntax: `(?x`. |
| `wild/grapheme/codepoints` | invalid or unsupported Perl syntax: `(?x`. |
| `wild/parol-veryl/multi-patternid-ascii` | Multiple patterns; no RE2/J multi-pattern search API. |
| `wild/parol-veryl/multi-captures-ascii` | Multiple patterns; no RE2/J multi-pattern search API. |
| `wild/ruff/whitespace-around-keywords` | Invalid UTF-8; not representable by this String runner. |
| `wild/ruff/noqa` | Invalid UTF-8; not representable by this String runner. |
| `wild/ruff/unnecessary-coding-comment` | Invalid UTF-8; not representable by this String runner. |
| `wild/ruff/string-quote-prefix` | Invalid UTF-8; not representable by this String runner. |
| `wild/ruff/space-around-operator` | Invalid UTF-8; not representable by this String runner. |
| `wild/ruff/shebang` | Invalid UTF-8; not representable by this String runner. |
| `wild/rustsec-cargo-audit/original-unix` | Invalid UTF-8; not representable by this String runner. |
| `wild/rustsec-cargo-audit/original-windows` | Invalid UTF-8; not representable by this String runner. |
| `wild/rustsec-cargo-audit/both-slashes` | Invalid UTF-8; not representable by this String runner. |
| `wild/rustsec-cargo-audit/both-alternate` | Invalid UTF-8; not representable by this String runner. |

[tables]: https://github.com/google/re2j/blob/re2j-1.8/java/com/google/re2j/UnicodeTables.java
