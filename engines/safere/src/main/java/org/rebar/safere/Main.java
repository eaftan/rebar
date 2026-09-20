package org.rebar.safere;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.safere.Matcher;
import org.safere.Pattern;

final class Config {
  String name;
  String model;
  String pattern;
  boolean caseInsensitive;
  boolean unicode;
  // rebar benchmarks permit the haystack to be invalid UTF-8,
  // but this runner uses SafeRE's String API so it searches sequences
  // of UTF-16 code units. Invalid UTF-8 must be rejected rather than
  // silently replaced during decoding.
  String haystack;
  int maxIters;
  int maxWarmupIters;
  long maxTime;
  long maxWarmupTime;

  Pattern compileRegex() {
    return compilePattern(this.pattern);
  }

  Pattern compilePattern(String pat) {
    int flags = 0;
    if (this.caseInsensitive) {
      flags |= Pattern.CASE_INSENSITIVE;
    }
    if (this.unicode) {
      flags |= Pattern.UNICODE_CASE;
      flags |= Pattern.UNICODE_CHARACTER_CLASS;
    }
    return Pattern.compile(pat, flags);
  }
}

// A single Key-Length-Value item.
final class KlvItem {
  // The key name.
  final String key;
  // The value contents.
  final String value;
  // The length, in bytes, used up by this KLV item.
  // This is useful for parsing a sequence of KLV items.
  // This length says how much to skip ahead to start
  // parsing the next KLV item.
  final int length;

  KlvItem(CharsetDecoder decoder, List<Byte> raw) throws Exception {
    int keyEnd = raw.indexOf((byte) ':');
    if (keyEnd == -1) {
      throw new Exception("invalid KLV item: could not find first ':'");
    }
    this.key = decode(decoder, raw.subList(0, keyEnd));
    int consumed = keyEnd + 1;
    raw = raw.subList(keyEnd + 1, raw.size());

    int valueLenEnd = raw.indexOf((byte) ':');
    if (valueLenEnd == -1) {
      throw new Exception("invalid KLV item: could not find second ':'");
    }
    String valueLenStr = decode(decoder, raw.subList(0, valueLenEnd));
    consumed += valueLenEnd + 1;
    raw = raw.subList(valueLenEnd + 1, raw.size());

    int valueLen = Integer.parseInt(valueLenStr);
    if (raw.get(valueLen) != (byte) '\n') {
      throw new Exception("invalid KLV item: no line terminator");
    }
    this.length = consumed + valueLen + 1; // +1 for the line terminator
    this.value = decode(decoder, raw.subList(0, valueLen));
  }

  // Decodes a list of Bytes into a string.
  static String decode(CharsetDecoder decoder, List<Byte> list) throws Exception {
    return decoder.decode(toByteBuffer(list)).toString();
  }

  // Convert a list of bytes to a ByteBuffer. This is so we can decode the
  // raw UTF-8 bytes. What a kludge.
  static ByteBuffer toByteBuffer(List<Byte> list) {
    byte[] bytes = new byte[list.size()];
    for (int i = 0; i < list.size(); i++) {
      bytes[i] = list.get(i);
    }
    return ByteBuffer.wrap(bytes);
  }
}

// A representation of the data we gather from a single
// benchmark execution. That is, the time it took to run
// and the count reported for verification.
final class Sample {
  // The duration, in nanoseconds. This might not always
  // have nanosecond resolution, but its units are always
  // nanoseconds.
  final long duration;
  // The count reported by the benchmark. This is checked
  // against what is expected in the benchmark definition
  // by rebar.
  final int count;

  Sample(long duration, int count) {
    this.duration = duration;
    this.count = count;
  }
}

public final class Main {
  private Main() {}

  public static void main(String... args) throws Exception {
    if (args.length == 1 && args[0].equals("version")) {
      Properties properties = new Properties();
      try (InputStream stream =
          Pattern.class.getResourceAsStream("/META-INF/maven/org.safere/safere/pom.properties")) {
        if (stream == null) {
          throw new Exception("SafeRE version metadata is missing");
        }
        properties.load(stream);
      }
      String version = properties.getProperty("version");
      if (version == null) {
        throw new Exception("SafeRE version metadata has no version");
      }
      String vmName = System.getProperty("java.vm.name");
      String vmVersion = System.getProperty("java.vm.version");
      System.out.printf("SafeRE %s (%s %s)%n", version, vmName, vmVersion);
      return;
    }

    // We create a decoder that will specifically fail on invalid UTF-8.
    // This prevents cases where we get an invalid UTF-8 haystack and
    // silently lossily decode it. That would make SafeRE search a
    // different haystack from the other engines. The String API used by
    // this runner therefore requires a valid UTF-8 haystack.
    CharsetDecoder decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

    List<Byte> raw = readStdin();
    Config config = new Config();
    while (!raw.isEmpty()) {
      KlvItem klv = new KlvItem(decoder, raw);
      raw = raw.subList(klv.length, raw.size());
      if (klv.key.equals("name")) {
        config.name = klv.value;
      } else if (klv.key.equals("model")) {
        config.model = klv.value;
      } else if (klv.key.equals("pattern")) {
        config.pattern = klv.value;
      } else if (klv.key.equals("case-insensitive")) {
        config.caseInsensitive = klv.value.equals("true");
      } else if (klv.key.equals("unicode")) {
        config.unicode = klv.value.equals("true");
      } else if (klv.key.equals("haystack")) {
        config.haystack = klv.value;
      } else if (klv.key.equals("max-iters")) {
        config.maxIters = Integer.parseInt(klv.value);
      } else if (klv.key.equals("max-warmup-iters")) {
        config.maxWarmupIters = Integer.parseInt(klv.value);
      } else if (klv.key.equals("max-time")) {
        config.maxTime = Long.parseLong(klv.value);
      } else if (klv.key.equals("max-warmup-time")) {
        config.maxWarmupTime = Long.parseLong(klv.value);
      } else {
        throw new Exception(String.format("unrecognized KLV key '%s'", klv.key));
      }
    }
    if (!config.model.equals("regex-redux") && config.pattern == null) {
      throw new Exception("missing pattern, must be provided once");
    }

    // Run our selected model and print the samples.
    List<Sample> samples;
    if (config.model.equals("compile")) {
      samples = modelCompile(config);
    } else if (config.model.equals("count")) {
      samples = modelCount(config);
    } else if (config.model.equals("count-spans")) {
      samples = modelCountSpans(config);
    } else if (config.model.equals("count-captures")) {
      samples = modelCountCaptures(config);
    } else if (config.model.equals("grep")) {
      samples = modelGrep(config);
    } else if (config.model.equals("grep-captures")) {
      samples = modelGrepCaptures(config);
    } else if (config.model.equals("regex-redux")) {
      samples = modelRegexRedux(config);
    } else {
      throw new Exception(String.format("unrecognized benchmark model %s", config.model));
    }
    for (Sample s : samples) {
      System.out.printf("%d,%d\n", s.duration, s.count);
    }
  }

  static List<Sample> modelCompile(Config config) throws Exception {
    return runAndCount(
        config,
        re -> {
          int count = 0;
          Matcher m = re.matcher(config.haystack);
          while (m.find()) {
            count++;
          }
          return count;
        },
        () -> config.compileRegex());
  }

  static List<Sample> modelCount(Config config) throws Exception {
    Pattern re = config.compileRegex();
    return runAndCount(
        config,
        n -> n,
        () -> {
          int count = 0;
          Matcher m = re.matcher(config.haystack);
          while (m.find()) {
            count++;
          }
          return count;
        });
  }

  static List<Sample> modelCountSpans(Config config) throws Exception {
    Pattern re = config.compileRegex();
    return runAndCount(
        config,
        n -> n,
        () -> {
          int sum = 0;
          Matcher m = re.matcher(config.haystack);
          while (m.find()) {
            sum += m.end() - m.start();
          }
          return sum;
        });
  }

  static List<Sample> modelCountCaptures(Config config) throws Exception {
    Pattern re = config.compileRegex();
    return runAndCount(
        config,
        n -> n,
        () -> {
          int count = 0;
          Matcher m = re.matcher(config.haystack);
          while (m.find()) {
            for (int i = 0; i < m.groupCount() + 1; i++) {
              String cap = m.group(i);
              if (cap != null) {
                count++;
              }
            }
          }
          return count;
        });
  }

  static List<Sample> modelGrep(Config config) throws Exception {
    Pattern re = config.compileRegex();
    return runAndCount(
        config,
        n -> n,
        () -> {
          // The one-element array lets the line callback update the count.
          int[] count = new int[] {0};
          config
              .haystack
              .lines()
              .forEach(
                  line -> {
                    if (re.matcher(line).find()) {
                      count[0]++;
                    }
                  });
          return count[0];
        });
  }

  static List<Sample> modelGrepCaptures(Config config) throws Exception {
    Pattern re = config.compileRegex();
    return runAndCount(
        config,
        n -> n,
        () -> {
          int[] count = new int[] {0};
          config
              .haystack
              .lines()
              .forEach(
                  line -> {
                    Matcher m = re.matcher(line);
                    while (m.find()) {
                      for (int i = 0; i < m.groupCount() + 1; i++) {
                        String cap = m.group(i);
                        if (cap != null) {
                          count[0]++;
                        }
                      }
                    }
                  });
          return count[0];
        });
  }

  static List<Sample> modelRegexRedux(Config config) throws Exception {
    return runAndCount(
        config,
        n -> n,
        () -> {
          String expected =
"""
agggtaaa|tttaccct 6
[cgt]gggtaaa|tttaccc[acg] 26
a[act]ggtaaa|tttacc[agt]t 86
ag[act]gtaaa|tttac[agt]ct 58
agg[act]taaa|ttta[agt]cct 113
aggg[acg]aaa|ttt[cgt]ccct 31
agggt[cgt]aa|tt[acg]accct 31
agggta[cgt]a|t[acg]taccct 32
agggtaa[cgt]|[acg]ttaccct 43

1016745
1000000
547899
""";

          StringBuilder result = new StringBuilder();
          String seq = config.haystack;
          int ilen = seq.length();
          seq = config.compilePattern(">[^\n]*\n|\n").matcher(seq).replaceAll("");
          int clen = seq.length();

          String[] variants =
              new String[] {
                "agggtaaa|tttaccct",
                "[cgt]gggtaaa|tttaccc[acg]",
                "a[act]ggtaaa|tttacc[agt]t",
                "ag[act]gtaaa|tttac[agt]ct",
                "agg[act]taaa|ttta[agt]cct",
                "aggg[acg]aaa|ttt[cgt]ccct",
                "agggt[cgt]aa|tt[acg]accct",
                "agggta[cgt]a|t[acg]taccct",
                "agggtaa[cgt]|[acg]ttaccct",
              };
          for (int i = 0; i < variants.length; i++) {
            String variant = variants[i];
            Pattern re = config.compilePattern(variant);
            int count = 0;
            Matcher m = re.matcher(seq);
            while (m.find()) {
              count++;
            }
            result.append(String.format("%s %d\n", variant, count));
          }

          seq = config.compilePattern("tHa[Nt]").matcher(seq).replaceAll("<4>");
          seq = config.compilePattern("aND|caN|Ha[DS]|WaS").matcher(seq).replaceAll("<3>");
          seq = config.compilePattern("a[NSt]|BY").matcher(seq).replaceAll("<2>");
          seq = config.compilePattern("<[^>]*>").matcher(seq).replaceAll("|");
          seq = config.compilePattern("\\|[^|][^|]*\\|").matcher(seq).replaceAll("-");

          result.append(String.format("\n%d\n%d\n%d\n", ilen, clen, seq.length()));
          if (!result.toString().trim().equals(expected.trim())) {
            throw new Exception("result did not match expected");
          }
          return seq.length();
        });
  }

  @FunctionalInterface
  interface Count<T> {
    int call(T t) throws Exception;
  }

  @FunctionalInterface
  interface Bench<T> {
    T call() throws Exception;
  }

  static <T> List<Sample> runAndCount(Config config, Count<T> count, Bench<T> bench)
      throws Exception {
    long warmupStart = System.nanoTime();
    for (int i = 0; i < config.maxWarmupIters; i++) {
      T result = bench.call();
      count.call(result);
      if ((System.nanoTime() - warmupStart) >= config.maxWarmupTime) {
        break;
      }
    }

    List<Sample> samples = new ArrayList<>();
    long runStart = System.nanoTime();
    for (int i = 0; i < config.maxIters; i++) {
      long benchStart = System.nanoTime();
      T result = bench.call();
      long elapsed = System.nanoTime() - benchStart;
      int n = count.call(result);
      samples.add(new Sample(elapsed, n));
      if ((System.nanoTime() - runStart) >= config.maxTime) {
        break;
      }
    }
    return samples;
  }

  static List<Byte> readStdin() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf = new byte[1024];
    int nread;
    while ((nread = System.in.read(buf)) > 0) {
      out.write(buf, 0, nread);
    }
    List<Byte> list = new ArrayList<>();
    for (byte b : out.toByteArray()) {
      list.add(b);
    }
    return list;
  }
}
