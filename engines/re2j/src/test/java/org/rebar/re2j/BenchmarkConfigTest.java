package org.rebar.re2j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.re2j.Pattern;
import java.io.ByteArrayOutputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

final class BenchmarkConfigTest {
  @Test
  void parsesByteLengthsAndUsesRe2jFlags() throws Exception {
    ByteArrayOutputStream bytes = input("count", "é", "É");
    write(bytes, "case-insensitive", "true");
    write(bytes, "unicode", "true");
    write(bytes, "max-iters", "5");
    write(bytes, "max-warmup-iters", "2");
    write(bytes, "max-time", "3000000");
    write(bytes, "max-warmup-time", "1000000");
    BenchmarkConfig config = BenchmarkConfig.parse(bytes.toByteArray());
    assertEquals("É", config.haystack());
    assertEquals(Pattern.CASE_INSENSITIVE, config.compileRegex().flags());
    assertEquals(1, StringWorkloads.create(config).run());
    assertEquals(5, config.maxIters());
    assertEquals(2, config.maxWarmupIters());
    assertEquals(3000000L, config.maxTime());
    assertEquals(1000000L, config.maxWarmupTime());
  }

  @Test
  void rejectsMultiplePatternsInsteadOfSilentlyUsingTheLast() {
    ByteArrayOutputStream bytes = input("count", "a", "ab");
    write(bytes, "pattern", "b");
    assertThrows(IllegalArgumentException.class, () -> BenchmarkConfig.parse(bytes.toByteArray()));
  }

  @Test
  void rejectsMalformedLengthsAndUtf8() {
    assertThrows(
        IllegalArgumentException.class,
        () -> BenchmarkConfig.parse("model:99:count\n".getBytes(StandardCharsets.UTF_8)));
    ByteArrayOutputStream bytes = input("count", ".", "");
    bytes.writeBytes("haystack:2:".getBytes(StandardCharsets.UTF_8));
    bytes.writeBytes(new byte[] {(byte) 0xc3, (byte) 0x28, '\n'});
    assertThrows(CharacterCodingException.class, () -> BenchmarkConfig.parse(bytes.toByteArray()));
  }

  @Test
  void countsUtf16SpansAndOptionalCaptures() throws Exception {
    assertEquals(2, run("count-spans", ".", "😀"));
    assertEquals(2, run("count-captures", "(a)(b)?", "a"));
  }

  @Test
  void grepHandlesCrLfAndLfWhilePreservingEmbeddedCr() throws Exception {
    assertEquals(1, run("grep", "^a$", "a\r\na\ra\n"));
    assertEquals(6, run("grep-captures", "(a)", "a\r\na\ra\n"));
    assertEquals(2, run("grep", "", "a\r\na\ra\n"));
    assertEquals(0, run("grep", "", ""));
    assertEquals(1, run("grep", "^a$", "a\r"));
    assertEquals(2, run("grep-captures", "(\\r)", "a\ra\n"));
  }

  private static int run(String model, String pattern, String haystack) throws Exception {
    return StringWorkloads.create(
            BenchmarkConfig.parse(input(model, pattern, haystack).toByteArray()))
        .run();
  }

  private static ByteArrayOutputStream input(String model, String pattern, String haystack) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    write(bytes, "model", model);
    write(bytes, "pattern", pattern);
    write(bytes, "haystack", haystack);
    return bytes;
  }

  private static void write(ByteArrayOutputStream bytes, String key, String value) {
    byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
    bytes.writeBytes((key + ":" + encoded.length + ":").getBytes(StandardCharsets.UTF_8));
    bytes.writeBytes(encoded);
    bytes.write('\n');
  }
}
