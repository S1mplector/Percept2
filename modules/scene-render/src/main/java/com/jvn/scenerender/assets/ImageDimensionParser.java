package com.jvn.scenerender.assets;

import java.util.Optional;

/**
 * TeaVM-safe image dimension parsing — reads just the format header, never
 * decodes pixel data. Replaces {@code javax.imageio.ImageIO}/{@code
 * java.awt.image.BufferedImage}, neither of which TeaVM's JS backend
 * supports, with a byte-level header parser that works identically on
 * every platform (desktop keeps using this too, rather than maintaining
 * two decode paths).
 */
final class ImageDimensionParser {
  private static final byte[] PNG_SIGNATURE = {
      (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'
  };
  private static final byte[] JPEG_SOI = { (byte) 0xFF, (byte) 0xD8 };

  private ImageDimensionParser() {}

  /** @return {@code {width, height}} if {@code data} is a recognizable PNG or JPEG header, else empty. */
  static Optional<int[]> parse(byte[] data) {
    if (data == null) return Optional.empty();
    Optional<int[]> png = parsePng(data);
    if (png.isPresent()) return png;
    return parseJpeg(data);
  }

  private static Optional<int[]> parsePng(byte[] data) {
    if (data.length < 24) return Optional.empty();
    for (int i = 0; i < PNG_SIGNATURE.length; i++) {
      if (data[i] != PNG_SIGNATURE[i]) return Optional.empty();
    }
    // IHDR is always the first chunk in a valid PNG: 4-byte length (unused
    // here), 4-byte type "IHDR" at offset 12, then 4-byte width + 4-byte
    // height, both big-endian, at offsets 16 and 20.
    if (data[12] != 'I' || data[13] != 'H' || data[14] != 'D' || data[15] != 'R') {
      return Optional.empty();
    }
    int width = readBigEndianInt(data, 16);
    int height = readBigEndianInt(data, 20);
    if (width <= 0 || height <= 0) return Optional.empty();
    return Optional.of(new int[] { width, height });
  }

  private static Optional<int[]> parseJpeg(byte[] data) {
    if (data.length < 4 || data[0] != JPEG_SOI[0] || data[1] != JPEG_SOI[1]) {
      return Optional.empty();
    }
    int offset = 2;
    while (offset + 4 <= data.length) {
      if ((data[offset] & 0xFF) != 0xFF) return Optional.empty(); // malformed marker
      int marker = data[offset + 1] & 0xFF;
      // SOF0 (0xC0) through SOF15 (0xCF), excluding DHT (0xC4), JPG (0xC8),
      // and DAC (0xCC) which share the 0xCx range but aren't SOF markers.
      boolean isSof = marker >= 0xC0 && marker <= 0xCF
          && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
      if (isSof) {
        if (offset + 9 > data.length) return Optional.empty();
        // SOF segment layout: FFCx, 2-byte length, 1-byte precision,
        // 2-byte height, 2-byte width, ... — height comes before width,
        // unlike PNG.
        int height = readBigEndianShort(data, offset + 5);
        int width = readBigEndianShort(data, offset + 7);
        if (width <= 0 || height <= 0) return Optional.empty();
        return Optional.of(new int[] { width, height });
      }
      if (marker == 0xD8 || marker == 0xD9) { // SOI/EOI carry no length field
        offset += 2;
        continue;
      }
      int segmentLength = readBigEndianShort(data, offset + 2);
      if (segmentLength < 2) return Optional.empty();
      offset += 2 + segmentLength;
    }
    return Optional.empty();
  }

  private static int readBigEndianInt(byte[] data, int offset) {
    return ((data[offset] & 0xFF) << 24)
        | ((data[offset + 1] & 0xFF) << 16)
        | ((data[offset + 2] & 0xFF) << 8)
        | (data[offset + 3] & 0xFF);
  }

  private static int readBigEndianShort(byte[] data, int offset) {
    return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
  }
}
