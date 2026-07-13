package com.jvn.editor.ui;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

/**
 * Exports developer-mode logs and runtime diagnostics into a user-selected folder.
 */
public final class DeveloperDiagnosticsExporter {
  private static final DateTimeFormatter BUNDLE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
  private static final long MAX_SINGLE_FILE_BYTES = 128L * 1024L * 1024L;

  private DeveloperDiagnosticsExporter() {}

  public static void chooseAndExport(Window owner,
                                     String appName,
                                     Supplier<List<Path>> contextRootsSupplier) {
    if (!Platform.isFxApplicationThread()) {
      Platform.runLater(() -> chooseAndExport(owner, appName, contextRootsSupplier));
      return;
    }

    DirectoryChooser chooser = new DirectoryChooser();
    chooser.setTitle("Save JVN Developer Diagnostics");
    Path initial = desktopDirectory();
    if (Files.isDirectory(initial)) chooser.setInitialDirectory(initial.toFile());
    File destination = chooser.showDialog(owner);
    if (destination == null) return;

    Task<ExportResult> task = new Task<>() {
      @Override
      protected ExportResult call() throws Exception {
        return export(destination.toPath(), appName, safeRoots(contextRootsSupplier));
      }
    };
    task.setOnSucceeded(e -> {
      ExportResult result = task.getValue();
      EditorDialogs.showTextBlock(
          owner,
          "Developer Diagnostics Saved",
          "Saved JVN developer diagnostics.",
          result.summary(),
          "Close");
      EditorPathExplorer.show(owner, result.zipFile().toFile());
    });
    task.setOnFailed(e -> EditorDialogs.error(
        owner,
        "Developer Diagnostics",
        "Could not save the developer diagnostics bundle.",
        task.getException()));
    Thread thread = new Thread(task, "jvn-developer-diagnostics-export");
    thread.setDaemon(true);
    thread.start();
  }

  static ExportResult export(Path destinationRoot, String appName, List<Path> contextRoots) throws IOException {
    Path destination = destinationRoot.toAbsolutePath().normalize();
    Files.createDirectories(destination);

    String stamp = BUNDLE_TIME.format(LocalDateTime.now());
    Path bundleDir = destination.resolve("jvn-diagnostics-" + stamp);
    Files.createDirectories(bundleDir);

    List<SourceDir> sources = candidateSources(contextRoots);
    List<CandidateFile> files = discoverFiles(sources);
    List<String> manifest = new ArrayList<>();
    manifest.add("JVN Developer Diagnostics Bundle");
    manifest.add("Created: " + LocalDateTime.now());
    manifest.add("App: " + safeAppName(appName));
    manifest.add("");
    manifest.add("Context roots:");
    if (contextRoots == null || contextRoots.isEmpty()) {
      manifest.add("- (none)");
    } else {
      for (Path root : contextRoots) manifest.add("- " + root.toAbsolutePath().normalize());
    }
    manifest.add("");
    manifest.add("Sources:");
    for (SourceDir source : sources) {
      manifest.add("- " + source.label() + ": " + source.dir());
    }
    manifest.add("");
    manifest.add("Copied files:");

    int copied = 0;
    int skipped = 0;
    Set<Path> copiedPaths = new LinkedHashSet<>();
    Path logsRoot = bundleDir.resolve("logs");
    for (CandidateFile file : files) {
      Path source = file.path().toAbsolutePath().normalize();
      if (!copiedPaths.add(source)) continue;
      long size;
      try {
        size = Files.size(source);
      } catch (IOException ex) {
        skipped++;
        manifest.add("- SKIP unreadable: " + source + " (" + ex.getMessage() + ")");
        continue;
      }
      if (size > MAX_SINGLE_FILE_BYTES) {
        skipped++;
        manifest.add("- SKIP too large: " + source + " (" + formatBytes(size) + ")");
        continue;
      }
      Path target = logsRoot.resolve(file.sourceLabel()).resolve(file.relativePath()).normalize();
      if (!target.startsWith(logsRoot)) {
        skipped++;
        manifest.add("- SKIP unsafe relative path: " + source);
        continue;
      }
      try {
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        copied++;
        manifest.add("- " + source + " -> logs/" + file.sourceLabel() + "/" + normalizeZipPath(file.relativePath()));
      } catch (IOException ex) {
        skipped++;
        manifest.add("- SKIP copy failed: " + source + " (" + ex.getMessage() + ")");
      }
    }

    Files.writeString(bundleDir.resolve("runtime-info.txt"), runtimeInfo(appName, contextRoots), StandardCharsets.UTF_8);
    Files.writeString(bundleDir.resolve("diagnostics-manifest.txt"), String.join(System.lineSeparator(), manifest), StandardCharsets.UTF_8);

    Path zip = destination.resolve(bundleDir.getFileName().toString() + ".zip");
    zipDirectory(bundleDir, zip);
    return new ExportResult(bundleDir, zip, copied, skipped, files.size());
  }

  private static List<Path> safeRoots(Supplier<List<Path>> supplier) {
    if (supplier == null) return List.of();
    try {
      List<Path> roots = supplier.get();
      return roots == null ? List.of() : roots;
    } catch (Exception ignored) {
      return List.of();
    }
  }

  private static List<SourceDir> candidateSources(List<Path> contextRoots) {
    Map<Path, SourceDir> sources = new LinkedHashMap<>();
    addSource(sources, "user-jvn-logs", defaultUserLogDirectory(), 5);
    addSource(sources, "engine-hub-launcher-logs", engineHubLauncherLogDirectory(), 3);
    addSource(sources, "gradle-daemon-logs", userGradleDaemonDirectory(), 5);
    addContextSources(sources, Path.of(System.getProperty("user.dir", ".")), "process");
    if (contextRoots != null) {
      int index = 1;
      for (Path root : contextRoots) {
        addContextSources(sources, root, "context-" + index);
        index++;
      }
    }
    return new ArrayList<>(sources.values());
  }

  private static void addContextSources(Map<Path, SourceDir> sources, Path root, String labelPrefix) {
    if (root == null) return;
    Path normalized = root.toAbsolutePath().normalize();
    addSource(sources, labelPrefix + "-jvn", normalized.resolve(".jvn"), 3);
    addSource(sources, labelPrefix + "-jvn-logs", normalized.resolve(".jvn").resolve("logs"), 5);
    addSource(sources, labelPrefix + "-logs", normalized.resolve("logs"), 5);
    addSource(sources, labelPrefix + "-build-tmp", normalized.resolve("build").resolve("tmp"), 5);
    addSource(sources, labelPrefix + "-gradle-daemon", normalized.resolve(".jvn-gradle-user-home").resolve("daemon"), 5);
  }

  private static void addSource(Map<Path, SourceDir> sources, String label, Path dir, int depth) {
    if (dir == null) return;
    Path normalized = dir.toAbsolutePath().normalize();
    if (!Files.isDirectory(normalized) || sources.containsKey(normalized)) return;
    sources.put(normalized, new SourceDir(safePathName(label), normalized, Math.max(1, depth)));
  }

  private static List<CandidateFile> discoverFiles(List<SourceDir> sources) {
    List<CandidateFile> files = new ArrayList<>();
    for (SourceDir source : sources) {
      try (var stream = Files.find(source.dir(), source.depth(),
          (path, attrs) -> attrs.isRegularFile() && isDiagnosticFile(path),
          FileVisitOption.FOLLOW_LINKS)) {
        stream.forEach(path -> files.add(new CandidateFile(
            path.toAbsolutePath().normalize(),
            source.label(),
            safeRelative(source.dir(), path))));
      } catch (Exception ignored) {
        // Best-effort diagnostics: inaccessible log folders should not block export.
      }
    }
    files.sort(Comparator.comparing(file -> file.path().toString()));
    return files;
  }

  private static boolean isDiagnosticFile(Path path) {
    if (path == null || path.getFileName() == null) return false;
    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".log")
        || name.endsWith(".out")
        || name.endsWith(".err")
        || name.endsWith(".txt")
        || name.endsWith(".json")
        || name.endsWith(".md")
        || name.endsWith(".hprof")
        || name.contains("crash")
        || name.contains("audit")
        || name.contains("diagnostic");
  }

  private static Path safeRelative(Path root, Path path) {
    try {
      Path relative = root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize());
      return Path.of(safePathName(relative.toString()));
    } catch (Exception ignored) {
      return Path.of(safePathName(path.getFileName() == null ? "log" : path.getFileName().toString()));
    }
  }

  private static void zipDirectory(Path dir, Path zip) throws IOException {
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
      try (var stream = Files.walk(dir)) {
        for (Path path : stream.filter(Files::isRegularFile).toList()) {
          String entryName = normalizeZipPath(dir.relativize(path));
          ZipEntry entry = new ZipEntry(entryName);
          out.putNextEntry(entry);
          Files.copy(path, out);
          out.closeEntry();
        }
      }
    }
  }

  private static String runtimeInfo(String appName, List<Path> contextRoots) {
    Runtime runtime = Runtime.getRuntime();
    MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    MemoryUsage heap = memoryBean.getHeapMemoryUsage();
    MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
    List<String> lines = new ArrayList<>();
    lines.add("App: " + safeAppName(appName));
    lines.add("Created: " + LocalDateTime.now());
    lines.add("Java: " + System.getProperty("java.version", "unknown"));
    lines.add("Java vendor: " + System.getProperty("java.vendor", "unknown"));
    lines.add("Java home: " + System.getProperty("java.home", "unknown"));
    lines.add("OS: " + System.getProperty("os.name", "unknown") + " "
        + System.getProperty("os.version", "") + " " + System.getProperty("os.arch", ""));
    lines.add("User dir: " + System.getProperty("user.dir", ""));
    lines.add("User home: " + System.getProperty("user.home", ""));
    lines.add("Developer mode editor: " + System.getProperty("jvn.editor.developerMode", "false"));
    lines.add("Developer mode launcher: " + System.getProperty("jvn.launcher.developerMode", "false"));
    lines.add("Heap used/committed/max: " + mb(heap.getUsed()) + " / " + mb(heap.getCommitted()) + " / " + mb(heap.getMax()));
    lines.add("Non-heap used/committed/max: " + mb(nonHeap.getUsed()) + " / " + mb(nonHeap.getCommitted()) + " / " + mb(nonHeap.getMax()));
    lines.add("Runtime free/total/max: " + mb(runtime.freeMemory()) + " / " + mb(runtime.totalMemory()) + " / " + mb(runtime.maxMemory()));
    lines.add("Processors: " + runtime.availableProcessors());
    lines.add("Input args: " + ManagementFactory.getRuntimeMXBean().getInputArguments());
    lines.add("");
    lines.add("Context roots:");
    if (contextRoots == null || contextRoots.isEmpty()) {
      lines.add("- (none)");
    } else {
      for (Path root : contextRoots) lines.add("- " + root.toAbsolutePath().normalize());
    }
    return String.join(System.lineSeparator(), lines) + System.lineSeparator();
  }

  private static Path defaultUserLogDirectory() {
    String home = System.getProperty("user.home", "").trim();
    if (home.isEmpty()) return Path.of(".jvn", "logs");
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("mac")) return Path.of(home, "Library", "Logs", "JVN");
    return Path.of(home, ".jvn", "logs");
  }

  private static Path engineHubLauncherLogDirectory() {
    String home = System.getProperty("user.home", "").trim();
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("win")) {
      String localAppData = System.getenv("LOCALAPPDATA");
      if (localAppData != null && !localAppData.isBlank()) {
        return Path.of(localAppData, "JVN Engine Hub", "Logs");
      }
      return Path.of(home, "AppData", "Local", "JVN Engine Hub", "Logs");
    }
    if (os.contains("mac")) return Path.of(home, "Library", "Logs", "JVN Engine Hub");
    return Path.of(home, ".local", "state", "jvn-engine-hub");
  }

  private static Path userGradleDaemonDirectory() {
    String gradleUserHome = System.getenv("GRADLE_USER_HOME");
    if (gradleUserHome != null && !gradleUserHome.isBlank()) {
      return Path.of(gradleUserHome, "daemon");
    }
    return Path.of(System.getProperty("user.home", "."), ".gradle", "daemon");
  }

  private static Path desktopDirectory() {
    Path desktop = Path.of(System.getProperty("user.home", "."), "Desktop");
    return Files.isDirectory(desktop) ? desktop : Path.of(System.getProperty("user.home", "."));
  }

  private static String safeAppName(String appName) {
    return appName == null || appName.isBlank() ? "JVN" : appName.trim();
  }

  private static String safePathName(String name) {
    String value = name == null || name.isBlank() ? "log" : name.trim();
    return value.replace('\\', '/')
        .replaceAll("^/+", "")
        .replaceAll("[^A-Za-z0-9._/-]+", "_");
  }

  private static String normalizeZipPath(Path path) {
    return path.toString().replace('\\', '/');
  }

  private static String mb(long bytes) {
    if (bytes < 0L) return "unknown";
    return (bytes / (1024L * 1024L)) + " MB";
  }

  private static String formatBytes(long bytes) {
    if (bytes < 1024L) return bytes + " B";
    double kb = bytes / 1024.0;
    if (kb < 1024.0) return String.format(Locale.ROOT, "%.1f KB", kb);
    return String.format(Locale.ROOT, "%.1f MB", kb / 1024.0);
  }

  public record ExportResult(Path bundleDir, Path zipFile, int copiedFiles, int skippedFiles, int discoveredFiles) {
    String summary() {
      return "Folder: " + bundleDir.toAbsolutePath() + System.lineSeparator()
          + "Zip: " + zipFile.toAbsolutePath() + System.lineSeparator()
          + "Discovered files: " + discoveredFiles + System.lineSeparator()
          + "Copied files: " + copiedFiles + System.lineSeparator()
          + "Skipped files: " + skippedFiles + System.lineSeparator()
          + System.lineSeparator()
          + "Send the .zip file when reporting freezes, memory growth, launch failures, or long-session slowdowns.";
    }
  }

  private record SourceDir(String label, Path dir, int depth) {}
  private record CandidateFile(Path path, String sourceLabel, Path relativePath) {}
}
