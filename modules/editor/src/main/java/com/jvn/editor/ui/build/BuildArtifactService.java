package com.jvn.editor.ui.build;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BuildArtifactService {

  public record ArtifactSummary(String name, long bytes, long lastModifiedMillis, boolean checksumAvailable) {
    public ArtifactSummary(String name, long bytes, long lastModifiedMillis) {
      this(name, bytes, lastModifiedMillis, false);
    }
  }

  public static List<ArtifactSummary> summarizeArtifacts(File outDir) {
    if (outDir == null || !outDir.isDirectory()) return List.of();
    File[] files = outDir.listFiles(file -> file.isFile()
        && !file.isHidden()
        && !file.getName().endsWith(".sha256"));
    if (files == null || files.length == 0) return List.of();
    List<ArtifactSummary> summaries = new ArrayList<>();
    for (File file : files) {
      summaries.add(new ArtifactSummary(
          file.getName(),
          file.length(),
          file.lastModified(),
          new File(file.getParentFile(), file.getName() + ".sha256").isFile()));
    }
    summaries.sort((a, b) -> Long.compare(b.lastModifiedMillis(), a.lastModifiedMillis()));
    return List.copyOf(summaries);
  }

  public static String formatArtifactInventory(List<ArtifactSummary> artifacts) {
    if (artifacts == null || artifacts.isEmpty()) return "none yet";
    long totalBytes = 0;
    for (ArtifactSummary a : artifacts) totalBytes += a.bytes();
    StringBuilder out = new StringBuilder();
    out.append(artifacts.size()).append(artifacts.size() == 1 ? " artifact" : " artifacts")
        .append("  ").append(formatBytes(totalBytes)).append(" total\n");
    int shown = Math.min(artifacts.size(), 6);
    for (int i = 0; i < shown; i++) {
      ArtifactSummary artifact = artifacts.get(i);
      out.append(artifact.name())
          .append("  ")
          .append(formatBytes(artifact.bytes()))
          .append("  ")
          .append(formatTimestamp(artifact.lastModifiedMillis()));
      if (artifact.checksumAvailable()) {
        out.append("  sha256");
      }
      if (i < shown - 1 || artifacts.size() > shown) out.append('\n');
    }
    if (artifacts.size() > shown) {
      out.append("+").append(artifacts.size() - shown).append(" more");
    }
    return out.toString();
  }

  public static String formatBytes(long bytes) {
    if (bytes < 1024L) return bytes + " B";
    double value = bytes;
    String[] units = {"KB", "MB", "GB"};
    int unitIndex = -1;
    do {
      value /= 1024.0;
      unitIndex++;
    } while (value >= 1024.0 && unitIndex < units.length - 1);
    return String.format(Locale.ROOT, value >= 10.0 ? "%.0f %s" : "%.1f %s", value, units[unitIndex]);
  }

  private static String formatTimestamp(long millis) {
    if (millis <= 0L) return "unknown time";
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis));
  }

  public static void zipDirectory(File sourceDir, File zipFile) throws java.io.IOException {
    java.nio.file.Path sourcePath = sourceDir.toPath();
    try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
        new java.io.BufferedOutputStream(new java.io.FileOutputStream(zipFile)))) {
      java.nio.file.Files.walk(sourcePath)
          .filter(p -> !java.nio.file.Files.isDirectory(p))
          .forEach(path -> {
            String entry = sourcePath.relativize(path).toString().replace('\\', '/');
            try {
              zos.putNextEntry(new java.util.zip.ZipEntry(entry));
              java.nio.file.Files.copy(path, zos);
              zos.closeEntry();
            } catch (java.io.IOException ex) {
              throw new java.io.UncheckedIOException(ex);
            }
          });
    }
  }
}
