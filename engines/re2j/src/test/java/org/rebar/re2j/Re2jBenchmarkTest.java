package org.rebar.re2j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.re2j.Pattern;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class Re2jBenchmarkTest {
  @TempDir Path directory;

  @Test
  void compilationProducesFreshPatternsWithTheConfiguredFlags() throws Exception {
    Re2jBenchmark benchmark = compilation();
    benchmark.setup();

    Pattern first = benchmark.compile();
    Pattern second = benchmark.compile();
    assertNotSame(first, second);
    assertEquals(Pattern.CASE_INSENSITIVE, first.flags());
    assertEquals(2, StringWorkloads.countMatches(first, "É é"));
  }

  @Test
  void compilationSetupRejectsAnIncorrectMatchCount() throws Exception {
    Re2jBenchmark benchmark = compilation();
    benchmark.expectedCount = 1;
    IllegalStateException error = assertThrows(IllegalStateException.class, benchmark::setup);
    assertEquals("JMH fork count 2 differed from 1", error.getMessage());
  }

  private Re2jBenchmark compilation() throws Exception {
    ByteArrayOutputStream input = new ByteArrayOutputStream();
    write(input, "model", "compile");
    write(input, "pattern", "é");
    write(input, "haystack", "É é");
    write(input, "case-insensitive", "true");
    write(input, "unicode", "true");
    Path path = directory.resolve("string.klv");
    Files.write(path, input.toByteArray());

    Re2jBenchmark benchmark = new Re2jBenchmark();
    benchmark.inputPath = path.toString();
    benchmark.expectedCount = 2;
    return benchmark;
  }

  private static void write(ByteArrayOutputStream output, String key, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    output.writeBytes((key + ":" + bytes.length + ":").getBytes(StandardCharsets.UTF_8));
    output.writeBytes(bytes);
    output.write('\n');
  }
}
