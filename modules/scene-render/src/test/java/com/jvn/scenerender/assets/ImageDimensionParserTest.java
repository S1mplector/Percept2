package com.jvn.scenerender.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ImageDimensionParserTest {

  @Test
  void parsesRealPngTestFixtureTier1() throws IOException {
    byte[] data = readResource("game/images/probe/tier1.png");
    Optional<int[]> dims = ImageDimensionParser.parse(data);
    assertTrue(dims.isPresent());
    assertEquals(4, dims.get()[0]);
    assertEquals(3, dims.get()[1]);
  }

  @Test
  void parsesRealPngTestFixtureTier2() throws IOException {
    byte[] data = readResource("raw-assets/tier2.png");
    Optional<int[]> dims = ImageDimensionParser.parse(data);
    assertTrue(dims.isPresent());
    assertEquals(5, dims.get()[0]);
    assertEquals(2, dims.get()[1]);
  }

  @Test
  void parsesRealJpegTestFixture() throws IOException {
    byte[] data = readResource("game/images/probe/tier3.jpg");
    Optional<int[]> dims = ImageDimensionParser.parse(data);
    assertTrue(dims.isPresent(), "expected the JPEG SOF0 marker scan to find real dimensions");
    assertEquals(12, dims.get()[0]);
    assertEquals(8, dims.get()[1]);
  }

  @Test
  void rejectsTruncatedPngHeader() {
    byte[] truncated = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n' }; // only 6 of 8 signature bytes
    assertTrue(ImageDimensionParser.parse(truncated).isEmpty());
  }

  @Test
  void rejectsNonImageBytes() {
    byte[] garbage = "not an image".getBytes();
    assertTrue(ImageDimensionParser.parse(garbage).isEmpty());
  }

  @Test
  void rejectsEmptyInput() {
    assertTrue(ImageDimensionParser.parse(new byte[0]).isEmpty());
  }

  private static byte[] readResource(String classpathPath) throws IOException {
    try (InputStream in = ImageDimensionParserTest.class.getClassLoader().getResourceAsStream(classpathPath)) {
      if (in == null) throw new IOException("test fixture not found on classpath: " + classpathPath);
      return in.readAllBytes();
    }
  }
}
