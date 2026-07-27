package org.rebar.regex;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Models {
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

  private static final String[] REGEX_REDUX_VARIANTS = {
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

  private Models() {}

  static int count(Pattern regex, String haystack) {
    int count = 0;
    Matcher matcher = regex.matcher(haystack);
    while (matcher.find()) {
      count++;
    }
    return count;
  }

  static int countSpans(Pattern regex, String haystack) {
    int sum = 0;
    Matcher matcher = regex.matcher(haystack);
    while (matcher.find()) {
      sum += matcher.end() - matcher.start();
    }
    return sum;
  }

  static int countCaptures(Pattern regex, String haystack) {
    int count = 0;
    Matcher matcher = regex.matcher(haystack);
    while (matcher.find()) {
      for (int group = 0; group <= matcher.groupCount(); group++) {
        if (matcher.group(group) != null) {
          count++;
        }
      }
    }
    return count;
  }

  static int grep(Pattern regex, String haystack) {
    int[] count = {0};
    haystack.lines().forEach(
        line -> {
          if (regex.matcher(line).find()) {
            count[0]++;
          }
        });
    return count[0];
  }

  static int grepCaptures(Pattern regex, String haystack) {
    int[] count = {0};
    haystack.lines().forEach(
        line -> {
          Matcher matcher = regex.matcher(line);
          while (matcher.find()) {
            for (int group = 0; group <= matcher.groupCount(); group++) {
              if (matcher.group(group) != null) {
                count[0]++;
              }
            }
          }
        });
    return count[0];
  }

  static int regexRedux(Config config) {
    StringBuilder result = new StringBuilder();
    String sequence = config.haystack();
    int initialLength = sequence.length();
    sequence = config.compile(">[^\n]*\n|\n").matcher(sequence).replaceAll("");
    int cleanedLength = sequence.length();

    for (String variant : REGEX_REDUX_VARIANTS) {
      int count = count(config.compile(variant), sequence);
      result.append(variant).append(' ').append(count).append('\n');
    }

    sequence = config.compile("tHa[Nt]").matcher(sequence).replaceAll("<4>");
    sequence = config.compile("aND|caN|Ha[DS]|WaS").matcher(sequence).replaceAll("<3>");
    sequence = config.compile("a[NSt]|BY").matcher(sequence).replaceAll("<2>");
    sequence = config.compile("<[^>]*>").matcher(sequence).replaceAll("|");
    sequence = config.compile("\\|[^|][^|]*\\|").matcher(sequence).replaceAll("-");

    result
        .append('\n')
        .append(initialLength)
        .append('\n')
        .append(cleanedLength)
        .append('\n')
        .append(sequence.length())
        .append('\n');
    if (!result.toString().equals(REGEX_REDUX_EXPECTED)) {
      throw new IllegalStateException("regex-redux result did not match expected output");
    }
    return sequence.length();
  }
}
