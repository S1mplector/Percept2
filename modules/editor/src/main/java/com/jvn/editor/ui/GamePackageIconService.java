package com.jvn.editor.ui;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

final class GamePackageIconService {
  static final int MIN_SOURCE_SIZE = 512;

  record IconResult(File source, File platformIcon, int width, int height) {}

  private GamePackageIconService() {}

  static IconResult install(File pngSource, File projectRoot, String hostOs) throws IOException, InterruptedException {
    BufferedImage image = readAndValidate(pngSource);
    File packagingDir = new File(projectRoot, "packaging");
    Files.createDirectories(packagingDir.toPath());
    File storedSource = new File(packagingDir, "icon-source.png");
    if (!pngSource.getCanonicalFile().equals(storedSource.getCanonicalFile())) {
      Files.copy(pngSource.toPath(), storedSource.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    File platformIcon;
    switch (normalizeHost(hostOs)) {
      case "macos" -> {
        platformIcon = new File(packagingDir, "icon.icns");
        writeIcns(storedSource, platformIcon);
      }
      case "windows" -> {
        platformIcon = new File(packagingDir, "icon.ico");
        writeIco(image, platformIcon);
      }
      default -> {
        platformIcon = new File(packagingDir, "icon.png");
        Files.copy(storedSource.toPath(), platformIcon.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
    }
    return new IconResult(storedSource, platformIcon, image.getWidth(), image.getHeight());
  }

  static BufferedImage readAndValidate(File source) throws IOException {
    if (source == null || !source.isFile()) throw new IOException("Choose a readable PNG image.");
    if (!source.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
      throw new IOException("Game icon source must be a PNG image.");
    }
    BufferedImage image = ImageIO.read(source);
    if (image == null) throw new IOException("The selected file is not a readable PNG image.");
    if (image.getWidth() != image.getHeight()) {
      throw new IOException("Game icon must be square; selected image is " + image.getWidth() + "x" + image.getHeight() + ".");
    }
    if (image.getWidth() < MIN_SOURCE_SIZE) {
      throw new IOException("Game icon must be at least " + MIN_SOURCE_SIZE + "x" + MIN_SOURCE_SIZE + ".");
    }
    if (!image.getColorModel().hasAlpha() || !hasTransparentPixel(image)) {
      throw new IOException("Game icon must include transparent pixels around the artwork.");
    }
    return image;
  }

  static void writeIco(BufferedImage source, File destination) throws IOException {
    BufferedImage icon = resize(source, 256);
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    if (!ImageIO.write(icon, "png", png)) throw new IOException("Could not encode Windows icon PNG data.");
    byte[] data = png.toByteArray();
    ByteBuffer header = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
    header.putShort((short) 0).putShort((short) 1).putShort((short) 1);
    header.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0);
    header.putShort((short) 1).putShort((short) 32);
    header.putInt(data.length).putInt(22);
    Files.createDirectories(destination.toPath().getParent());
    try (var output = Files.newOutputStream(destination.toPath())) {
      output.write(header.array());
      output.write(data);
    }
  }

  private static void writeIcns(File source, File destination) throws IOException, InterruptedException {
    Path iconset = destination.toPath().getParent().resolve(".jvn-icon.iconset");
    deleteTree(iconset);
    Files.createDirectories(iconset);
    List<Integer> sizes = List.of(16, 32, 128, 256, 512);
    try {
      for (int size : sizes) {
        run("sips", "-z", Integer.toString(size), Integer.toString(size), source.getAbsolutePath(),
            "--out", iconset.resolve("icon_" + size + "x" + size + ".png").toString());
        int retina = size * 2;
        run("sips", "-z", Integer.toString(retina), Integer.toString(retina), source.getAbsolutePath(),
            "--out", iconset.resolve("icon_" + size + "x" + size + "@2x.png").toString());
      }
      run("iconutil", "-c", "icns", iconset.toString(), "-o", destination.getAbsolutePath());
    } finally {
      deleteTree(iconset);
    }
  }

  private static void run(String... command) throws IOException, InterruptedException {
    Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes());
    int exit = process.waitFor();
    if (exit != 0) throw new IOException(command[0] + " failed: " + output.trim());
  }

  private static BufferedImage resize(BufferedImage source, int size) {
    BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = target.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      graphics.drawImage(source, 0, 0, size, size, null);
    } finally {
      graphics.dispose();
    }
    return target;
  }

  private static boolean hasTransparentPixel(BufferedImage image) {
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if ((image.getRGB(x, y) >>> 24) < 255) return true;
      }
    }
    return false;
  }

  private static String normalizeHost(String hostOs) {
    String value = hostOs == null ? "" : hostOs.toLowerCase(Locale.ROOT);
    if (value.contains("mac")) return "macos";
    if (value.contains("win")) return "windows";
    return "linux";
  }

  private static void deleteTree(Path root) throws IOException {
    if (!Files.exists(root)) return;
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
    }
  }
}
