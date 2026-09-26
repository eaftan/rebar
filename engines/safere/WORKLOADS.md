# SafeRE / RE2/J workload comparison

RE2/J 1.8 supports 266 workloads. Each SafeRE 0.11.0 mode (`string`,
`utf8`, and `utf8-vector`) supports the same 275 workloads.
There are 264 shared workloads, two RE2/J-only workloads, and eleven
SafeRE-only workloads.

## Supported only by RE2/J

Both workloads pass in RE2/J and fail in all three SafeRE modes.

| Workload | SafeRE result | Explanation |
| --- | --- | --- |
| `unicode/codepoints/contiguous-greek` | Compilation rejects `\p{Greek}`. | SafeRE does not accept this script-property spelling. It accepts `\p{IsGreek}` and `\p{sc=Greek}`, but the workload uses `\p{Greek}`. Substituting an alias would change the pattern being benchmarked. |
| `opt/accelerate/whole-line` | 213,755 matching lines; expected 239,963. | SafeRE's Java-style multiline anchors make `(?m)^.*$` fail on empty input. The corpus contains 26,208 empty lines, accounting for the entire difference. RE2/J matches those lines. This behavior is also reproduced by `java.util.regex`. |

## Supported only by SafeRE

All eleven workloads pass in every SafeRE mode. RE2/J results below use
the unchanged workload patterns. For span workloads, SafeRE String counts
UTF-16 code units; its UTF-8 modes count bytes.

| Workload | RE2/J result | Explanation |
| --- | --- | --- |
| `curated/03-date/unicode` | 111,817 span units; expected 111,841. | The workload requires Unicode digits, but RE2/J's `\d` is ASCII-only. |
| `curated/08-words/all-russian` | 529 span units; expected 107,391. | The workload requires Unicode `\w` and word boundaries. RE2/J uses ASCII word characters and boundaries. |
| `curated/08-words/long-russian` | 12 span units; expected 5,481. | RE2/J's ASCII `\w` and word boundaries cannot find the required Russian words. |
| `grep/long-words-unicode` | 5,073 matching lines; expected 5,075. | Unicode word characters and boundaries are required even in this mostly ASCII corpus; RE2/J's ASCII definitions miss two lines. |
| `opt/nfa-sparse/small-repeated-class-bytes` | Compilation rejects `(?-u`. | RE2/J does not support this scoped Unicode-disabling flag. The byte-oriented character class also cannot be represented faithfully by its String runner; stripping the flag would change the pattern's semantics. |
| `opt/nfa-sparse/small-repeated-class-unicode` | Compilation rejects `(?-u`. | RE2/J does not support the exact scoped flag in the pattern. Removing it to obtain the expected zero matches would not establish support for this workload. |
| `opt/onepass/first-three-words-russian` | 4 capture counts; expected 19,224. | The workload requires Unicode `\w`; RE2/J's ASCII word class does not recognize the Russian words. |
| `opt/onepass/word-boundary-russian` | 3 capture counts; expected 873. | The workload requires Unicode `\b`; RE2/J's ASCII word boundaries do not recognize the required boundaries. |
| `unicode/word/boundary-any-russian` | 638 span units; expected 529,194 bytes. | The pattern requires Unicode `\w` and `\b`, which RE2/J cannot enable. SafeRE spans are 264,916 UTF-16 units or 529,194 bytes. |
| `unicode/word/boundary-long-russian` | 0 span units; expected 21,332 bytes. | RE2/J's ASCII `\w` and `\b` cannot match the required long Russian words. SafeRE spans are 10,666 UTF-16 units or 21,332 bytes. |
| `unicode/word/around-holmes-russian` | 0 span units; expected 44 bytes. | The Unicode word classes and boundaries around `Холмс` are unsupported in RE2/J. SafeRE spans are 23 UTF-16 units or 44 bytes. |

Pattern translation or a custom Unicode word-boundary layer would change
the engine under measurement, so these workloads are excluded from RE2/J.
