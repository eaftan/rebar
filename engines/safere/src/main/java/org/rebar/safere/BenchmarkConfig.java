package org.rebar.safere;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.safere.Pattern;

record BenchmarkConfig(
    String model,
    String pattern,
    boolean caseInsensitive,
    boolean unicode,
    String haystack,
    int maxIters,
    int maxWarmupIters,
    long maxTime,
    long maxWarmupTime) {

  static BenchmarkConfig parse(byte[] input) throws CharacterCodingException {
    CharsetDecoder decoder =
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
    String model = null;
    String pattern = null;
    String haystack = null;
    boolean caseInsensitive = false;
    boolean unicode = false;
    int maxIters = 0;
    int maxWarmupIters = 0;
    long maxTime = 0;
    long maxWarmupTime = 0;

    for (int offset = 0; offset < input.length; ) {
      KlvEntry entry = readEntry(input, offset, decoder);
      offset = entry.nextOffset();
      switch (entry.key()) {
        // Rebar uses the name to identify the result outside this process.
        case "name" -> {}
        case "model" -> model = entry.value();
        case "pattern" -> pattern = entry.value();
        case "case-insensitive" -> caseInsensitive = entry.value().equals("true");
        case "unicode" -> unicode = entry.value().equals("true");
        case "haystack" -> haystack = entry.value();
        case "max-iters" -> maxIters = Integer.parseInt(entry.value());
        case "max-warmup-iters" -> maxWarmupIters = Integer.parseInt(entry.value());
        case "max-time" -> maxTime = Long.parseLong(entry.value());
        case "max-warmup-time" -> maxWarmupTime = Long.parseLong(entry.value());
        default -> throw new IllegalArgumentException("unrecognized KLV key: " + entry.key());
      }
    }
    if (model == null) {
      throw new IllegalArgumentException("missing benchmark model");
    }
    if (!model.equals("regex-redux") && pattern == null) {
      throw new IllegalArgumentException("missing pattern");
    }
    if (haystack == null) {
      throw new IllegalArgumentException("missing haystack");
    }
    return new BenchmarkConfig(
        model,
        pattern,
        caseInsensitive,
        unicode,
        haystack,
        maxIters,
        maxWarmupIters,
        maxTime,
        maxWarmupTime);
  }

  Pattern compileRegex() {
    return compilePattern(pattern);
  }

  Pattern compilePattern(String regex) {
    int flags = 0;
    if (caseInsensitive) {
      flags |= Pattern.CASE_INSENSITIVE;
    }
    if (unicode) {
      flags |= Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
    }
    return Pattern.compile(regex, flags);
  }

  private static KlvEntry readEntry(byte[] input, int offset, CharsetDecoder decoder)
      throws CharacterCodingException {
    int keyEnd = findColon(input, offset);
    int lengthEnd = findColon(input, keyEnd + 1);
    String key = decode(input, offset, keyEnd - offset, decoder);
    int valueLength = Integer.parseInt(decode(input, keyEnd + 1, lengthEnd - keyEnd - 1, decoder));
    int valueStart = lengthEnd + 1;
    if (valueLength < 0 || valueLength >= input.length - valueStart) {
      throw new IllegalArgumentException("invalid KLV value length for " + key);
    }
    int valueEnd = valueStart + valueLength;
    if (input[valueEnd] != '\n') {
      throw new IllegalArgumentException("missing KLV line terminator for " + key);
    }
    return new KlvEntry(key, decode(input, valueStart, valueLength, decoder), valueEnd + 1);
  }

  private static int findColon(byte[] input, int start) {
    for (int i = start; i < input.length; i++) {
      if (input[i] == ':') {
        return i;
      }
    }
    throw new IllegalArgumentException("missing KLV delimiter");
  }

  private static String decode(byte[] input, int start, int length, CharsetDecoder decoder)
      throws CharacterCodingException {
    return decoder.decode(ByteBuffer.wrap(input, start, length)).toString();
  }

  private record KlvEntry(String key, String value, int nextOffset) {}
}
