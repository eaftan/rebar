package org.rebar.regex;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

record Config(
    String name,
    String model,
    String expression,
    boolean caseInsensitive,
    boolean unicode,
    String haystack,
    long maxIters,
    long maxWarmupIters,
    long maxTime,
    long maxWarmupTime) {

  static Config parse(byte[] input) throws CharacterCodingException {
    String name = null;
    String model = null;
    List<String> expressions = new ArrayList<>();
    boolean caseInsensitive = false;
    boolean unicode = false;
    String haystack = null;
    long maxIters = 0;
    long maxWarmupIters = 0;
    long maxTime = 0;
    long maxWarmupTime = 0;

    int offset = 0;
    while (offset < input.length) {
      int keyEnd = indexOf(input, (byte) ':', offset);
      if (keyEnd < 0) {
        throw new IllegalArgumentException("invalid KLV item: missing key terminator");
      }
      String key = decode(input, offset, keyEnd - offset);

      int lengthEnd = indexOf(input, (byte) ':', keyEnd + 1);
      if (lengthEnd < 0) {
        throw new IllegalArgumentException("invalid KLV item: missing length terminator");
      }
      int valueLength =
          Integer.parseInt(decode(input, keyEnd + 1, lengthEnd - keyEnd - 1));
      if (valueLength < 0) {
        throw new IllegalArgumentException("invalid KLV item: negative value length");
      }

      int valueStart = lengthEnd + 1;
      int valueEnd = Math.addExact(valueStart, valueLength);
      if (valueEnd >= input.length || input[valueEnd] != '\n') {
        throw new IllegalArgumentException("invalid KLV item: missing line terminator");
      }
      String value = decode(input, valueStart, valueLength);
      offset = valueEnd + 1;

      switch (key) {
        case "name" -> name = value;
        case "model" -> model = value;
        case "pattern" -> expressions.add(value);
        case "case-insensitive" -> caseInsensitive = parseBoolean(key, value);
        case "unicode" -> unicode = parseBoolean(key, value);
        case "haystack" -> haystack = value;
        case "max-iters" -> maxIters = Long.parseLong(value);
        case "max-warmup-iters" -> maxWarmupIters = Long.parseLong(value);
        case "max-time" -> maxTime = Long.parseLong(value);
        case "max-warmup-time" -> maxWarmupTime = Long.parseLong(value);
        default -> throw new IllegalArgumentException("unrecognized KLV key '" + key + "'");
      }
    }

    if (model == null) {
      throw new IllegalArgumentException("missing model");
    }
    if (!model.equals("regex-redux") && expressions.size() != 1) {
      throw new IllegalArgumentException("number of patterns must be 1");
    }
    if (model.equals("regex-redux") && !expressions.isEmpty()) {
      throw new IllegalArgumentException("regex-redux does not accept a pattern");
    }
    if (haystack == null) {
      throw new IllegalArgumentException("missing haystack");
    }
    if (maxIters < 0 || maxWarmupIters < 0 || maxTime < 0 || maxWarmupTime < 0) {
      throw new IllegalArgumentException("benchmark limits must not be negative");
    }

    String expression = expressions.isEmpty() ? null : expressions.get(0);
    return new Config(
        name,
        model,
        expression,
        caseInsensitive,
        unicode,
        haystack,
        maxIters,
        maxWarmupIters,
        maxTime,
        maxWarmupTime);
  }

  Pattern compileRegex() {
    return compile(expression);
  }

  Pattern compile(String regex) {
    int flags = 0;
    if (caseInsensitive) {
      flags |= Pattern.CASE_INSENSITIVE;
    }
    if (unicode) {
      flags |= Pattern.UNICODE_CASE;
      flags |= Pattern.UNICODE_CHARACTER_CLASS;
    }
    return Pattern.compile(regex, flags);
  }

  private static boolean parseBoolean(String key, String value) {
    return switch (value) {
      case "true" -> true;
      case "false" -> false;
      default -> throw new IllegalArgumentException(
          "invalid boolean value for '" + key + "': " + value);
    };
  }

  private static int indexOf(byte[] input, byte needle, int start) {
    for (int i = start; i < input.length; i++) {
      if (input[i] == needle) {
        return i;
      }
    }
    return -1;
  }

  private static String decode(byte[] input, int offset, int length)
      throws CharacterCodingException {
    return StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(input, offset, length))
        .toString();
  }
}
