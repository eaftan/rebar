package org.rebar.safere;

import java.util.Iterator;
import org.safere.Matcher;
import org.safere.Pattern;

final class Workloads {
  private static final String REGEX_REDUX_EXPECTED =
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

  private Workloads() {}

  static IntWorkload create(BenchmarkConfig config) {
    return switch (config.model()) {
      case "count" -> modelCount(config);
      case "count-spans" -> modelCountSpans(config);
      case "count-captures" -> modelCountCaptures(config);
      case "grep" -> modelGrep(config);
      case "grep-captures" -> modelGrepCaptures(config);
      case "regex-redux" -> modelRegexRedux(config);
      default ->
          throw new IllegalArgumentException("unrecognized benchmark model: " + config.model());
    };
  }

  static int countMatches(Pattern pattern, String haystack) {
    int count = 0;
    Matcher matcher = pattern.matcher(haystack);
    while (matcher.find()) {
      count++;
    }
    return count;
  }

  private static IntWorkload modelCount(BenchmarkConfig config) {
    Pattern pattern = config.compileRegex();
    return () -> countMatches(pattern, config.haystack());
  }

  private static IntWorkload modelCountSpans(BenchmarkConfig config) {
    Pattern pattern = config.compileRegex();
    return () -> {
      int sum = 0;
      Matcher matcher = pattern.matcher(config.haystack());
      while (matcher.find()) {
        sum += matcher.end() - matcher.start();
      }
      return sum;
    };
  }

  private static IntWorkload modelCountCaptures(BenchmarkConfig config) {
    Pattern pattern = config.compileRegex();
    return () -> {
      int count = 0;
      Matcher matcher = pattern.matcher(config.haystack());
      while (matcher.find()) {
        for (int group = 0; group <= matcher.groupCount(); group++) {
          if (matcher.group(group) != null) {
            count++;
          }
        }
      }
      return count;
    };
  }

  private static IntWorkload modelGrep(BenchmarkConfig config) {
    Pattern pattern = config.compileRegex();
    return () -> {
      int count = 0;
      Iterator<String> lines = config.haystack().lines().iterator();
      while (lines.hasNext()) {
        if (pattern.matcher(lines.next()).find()) {
          count++;
        }
      }
      return count;
    };
  }

  private static IntWorkload modelGrepCaptures(BenchmarkConfig config) {
    Pattern pattern = config.compileRegex();
    return () -> {
      int count = 0;
      Iterator<String> lines = config.haystack().lines().iterator();
      while (lines.hasNext()) {
        Matcher matcher = pattern.matcher(lines.next());
        while (matcher.find()) {
          for (int group = 0; group <= matcher.groupCount(); group++) {
            if (matcher.group(group) != null) {
              count++;
            }
          }
        }
      }
      return count;
    };
  }

  private static IntWorkload modelRegexRedux(BenchmarkConfig config) {
    return () -> {
      StringBuilder result = new StringBuilder();
      String sequence = config.haystack();
      int inputLength = sequence.length();
      sequence = config.compilePattern(">[^\n]*\n|\n").matcher(sequence).replaceAll("");
      int cleanedLength = sequence.length();

      String[] variants = {
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
      for (String variant : variants) {
        Pattern pattern = config.compilePattern(variant);
        int count = 0;
        Matcher matcher = pattern.matcher(sequence);
        while (matcher.find()) {
          count++;
        }
        result.append(variant).append(' ').append(count).append('\n');
      }

      sequence = config.compilePattern("tHa[Nt]").matcher(sequence).replaceAll("<4>");
      sequence = config.compilePattern("aND|caN|Ha[DS]|WaS").matcher(sequence).replaceAll("<3>");
      sequence = config.compilePattern("a[NSt]|BY").matcher(sequence).replaceAll("<2>");
      sequence = config.compilePattern("<[^>]*>").matcher(sequence).replaceAll("|");
      sequence = config.compilePattern("\\|[^|][^|]*\\|").matcher(sequence).replaceAll("-");

      result
          .append('\n')
          .append(inputLength)
          .append('\n')
          .append(cleanedLength)
          .append('\n')
          .append(sequence.length())
          .append('\n');
      if (!result.toString().trim().equals(REGEX_REDUX_EXPECTED.trim())) {
        throw new IllegalStateException("regex-redux result did not match expected output");
      }
      return sequence.length();
    };
  }

  @FunctionalInterface
  interface IntWorkload {
    int run() throws Exception;
  }
}
