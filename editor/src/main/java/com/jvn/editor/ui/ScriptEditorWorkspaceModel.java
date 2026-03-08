package com.jvn.editor.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure data/model layer for the script editor sidebar.
 * Keeps filesystem indexing, metadata extraction, filtering, and safe
 * script creation out of the JavaFX view code.
 */
public final class ScriptEditorWorkspaceModel {
  private static final Pattern LABEL_PATTERN = Pattern.compile("^\\s*@label\\s+([^\\s#]+)");
  private static final Pattern INCLUDE_PATTERN = Pattern.compile("^\\s*@include\\b");

  private ScriptEditorWorkspaceModel() {}

  public static WorkspaceSnapshot emptySnapshot() {
    return new WorkspaceSnapshot(null, null, List.of(), 0, 0);
  }

  public static WorkspaceSnapshot index(File launchRoot) {
    if (launchRoot == null || !launchRoot.isDirectory()) return emptySnapshot();
    Path scriptsRoot = resolveScriptsRoot(launchRoot);
    if (scriptsRoot == null) {
      return new WorkspaceSnapshot(launchRoot, null, List.of(), 0, 0);
    }

    List<ScriptFileEntry> scripts = collectScripts(launchRoot, scriptsRoot);
    int totalLabels = scripts.stream().mapToInt(ScriptFileEntry::labelCount).sum();
    Set<String> folders = new LinkedHashSet<>();
    for (ScriptFileEntry entry : scripts) {
      Path parent = Path.of(entry.relativePath()).getParent();
      if (parent != null) folders.add(parent.toString().replace('\\', '/'));
    }
    return new WorkspaceSnapshot(launchRoot, scriptsRoot, List.copyOf(scripts), folders.size(), totalLabels);
  }

  public static Path resolveScriptsRoot(File root) {
    if (root == null) return null;
    Path[] candidates = new Path[] {
        root.toPath().resolve("scripts"),
        root.toPath().resolve("game/scripts"),
        root.toPath().resolve("runtime/src/main/resources/game/scripts")
    };
    for (Path candidate : candidates) {
      if (Files.isDirectory(candidate)) return candidate;
    }
    return null;
  }

  public static List<ScriptFileEntry> filter(List<ScriptFileEntry> scripts, String query) {
    if (scripts == null || scripts.isEmpty()) return List.of();
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    if (needle.isEmpty()) return List.copyOf(scripts);

    List<ScriptFileEntry> filtered = new ArrayList<>();
    for (ScriptFileEntry entry : scripts) {
      String haystack = (entry.relativePath() + "\n"
          + entry.projectRelativePath() + "\n"
          + entry.displayName() + "\n"
          + String.join("\n", entry.labelNames())).toLowerCase(Locale.ROOT);
      if (haystack.contains(needle)) filtered.add(entry);
    }
    return filtered;
  }

  public static File createScript(File launchRoot, String requestedRelativePath) throws IOException {
    Objects.requireNonNull(launchRoot, "launchRoot");
    Path scriptsRoot = resolveScriptsRoot(launchRoot);
    if (scriptsRoot == null) {
      scriptsRoot = launchRoot.toPath().resolve("scripts");
      Files.createDirectories(scriptsRoot);
    }

    String normalized = normalizeRelativeScriptPath(requestedRelativePath);
    Path target = scriptsRoot.resolve(normalized).normalize();
    if (!target.startsWith(scriptsRoot.normalize())) {
      throw new IOException("Script path must stay inside the scripts directory.");
    }

    Files.createDirectories(target.getParent());
    if (!Files.exists(target)) {
      Files.writeString(target, defaultTemplate(target), StandardCharsets.UTF_8);
    }
    return target.toFile();
  }

  public static String normalizeRelativeScriptPath(String requestedRelativePath) {
    String input = requestedRelativePath == null ? "" : requestedRelativePath.trim().replace('\\', '/');
    while (input.startsWith("/")) input = input.substring(1);
    if (input.startsWith("scripts/")) input = input.substring("scripts/".length());
    if (input.isBlank()) input = "story/new_scene";
    if (input.endsWith("/")) input += "new_scene";
    if (!input.toLowerCase(Locale.ROOT).endsWith(".vns")) input += ".vns";
    return input;
  }

  private static List<ScriptFileEntry> collectScripts(File launchRoot, Path scriptsRoot) {
    List<ScriptFileEntry> scripts = new ArrayList<>();
    try (var stream = Files.walk(scriptsRoot, 16)) {
      stream.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".vns"))
          .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
          .forEach(path -> scripts.add(analyzeScript(launchRoot, scriptsRoot, path)));
    } catch (IOException ignored) {
    }
    return scripts;
  }

  private static ScriptFileEntry analyzeScript(File launchRoot, Path scriptsRoot, Path file) {
    List<String> lines;
    try {
      lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (IOException ex) {
      lines = List.of();
    }

    int includeCount = 0;
    List<String> labelNames = new ArrayList<>();
    for (String line : lines) {
      Matcher labelMatcher = LABEL_PATTERN.matcher(line);
      if (labelMatcher.find()) {
        labelNames.add(labelMatcher.group(1));
      }
      if (INCLUDE_PATTERN.matcher(line).find()) includeCount++;
    }

    String relative = scriptsRoot.relativize(file).toString().replace('\\', '/');
    String projectRelative = launchRoot.toPath().relativize(file).toString().replace('\\', '/');
    long sizeBytes = safeSize(file);
    long lastModifiedMillis = safeLastModified(file);
    String displayName = file.getFileName().toString();

    return new ScriptFileEntry(
        file.toFile(),
        displayName,
        relative,
        projectRelative,
        lines.size(),
        labelNames.size(),
        includeCount,
        List.copyOf(labelNames),
        sizeBytes,
        lastModifiedMillis);
  }

  private static long safeSize(Path file) {
    try {
      return Files.size(file);
    } catch (IOException ex) {
      return 0L;
    }
  }

  private static long safeLastModified(Path file) {
    try {
      return Files.getLastModifiedTime(file).toMillis();
    } catch (IOException ex) {
      return 0L;
    }
  }

  private static String defaultTemplate(Path target) {
    String fileName = target.getFileName().toString();
    String stem = fileName.endsWith(".vns") ? fileName.substring(0, fileName.length() - 4) : fileName;
    String title = stem.replace('_', ' ').replace('-', ' ').trim();
    if (title.isBlank()) title = "New Scene";
    title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
    return "# " + title + "\n\n@label start\nnarrator: TODO\n";
  }

  public record ScriptFileEntry(
      File file,
      String displayName,
      String relativePath,
      String projectRelativePath,
      int lineCount,
      int labelCount,
      int includeCount,
      List<String> labelNames,
      long sizeBytes,
      long lastModifiedMillis) {
  }

  public record WorkspaceSnapshot(
      File launchRoot,
      Path scriptsRoot,
      List<ScriptFileEntry> scripts,
      int folderCount,
      int totalLabelCount) {

    public boolean hasScriptsRoot() {
      return scriptsRoot != null;
    }
  }
}
