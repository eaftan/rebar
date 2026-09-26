package org.rebar.re2j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.util.MultisetStatistics;

final class MainTest {
  @Test
  void limitsSamplesAcrossTheFullDurationDistribution() throws Exception {
    assertEquals(List.of("10,7", "20,7", "100,7"), samples(3));
    assertEquals(List.of("20,7"), samples(1));
  }

  @Test
  void preservesEverySampleWhenBelowTheLimit() throws Exception {
    assertEquals(List.of("10,7", "10,7", "20,7", "20,7", "100,7", "100,7"), samples(10));
  }

  @Test
  void deletesTemporaryFilesOnTermination() throws Exception {
    // Process.destroy sends SIGTERM on Unix, but forcibly terminates Windows JVMs.
    assumeTrue(!System.getProperty("os.name").startsWith("Windows"));
    Process child = new ProcessBuilder(
        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-cp", System.getProperty("java.class.path"), TempFileProcess.class.getName())
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start();
    Path input = null;
    Path output = null;
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
      input = Path.of(reader.readLine());
      output = Path.of(reader.readLine());
      assertTrue(Files.exists(input));
      assertTrue(Files.exists(output));
      child.destroy();
      assertTrue(child.waitFor(10, TimeUnit.SECONDS));
      assertFalse(Files.exists(input));
      assertFalse(Files.exists(output));
    } finally {
      child.destroyForcibly();
      child.waitFor(10, TimeUnit.SECONDS);
      if (input != null) Files.deleteIfExists(input);
      if (output != null) Files.deleteIfExists(output);
    }
  }

  public static final class TempFileProcess {
    public static void main(String[] args) throws Exception {
      System.out.println(Main.createTempFile("rebar-re2j-test-", ".klv"));
      System.out.println(Main.createTempFile("rebar-re2j-test-jmh-", ".log"));
      System.out.flush();
      System.in.read();
    }
  }

  private static List<String> samples(long maxIters) throws Exception {
    MultisetStatistics statistics = new MultisetStatistics();
    statistics.addValue(10, 2);
    statistics.addValue(20, 2);
    statistics.addValue(100, 2);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (PrintStream output = new PrintStream(bytes, false, StandardCharsets.UTF_8)) {
      Main.emitSamples(statistics, maxIters, 7, output);
    }
    return bytes.toString(StandardCharsets.UTF_8).lines().toList();
  }
}
