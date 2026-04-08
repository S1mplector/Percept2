package com.jvn.editor.ui;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jvn.core.vn.script.VnScriptParser;

/**
 * Shared VNS static analysis used by editor features (code lint, diagnostics panel, and flow map).
 */
public final class VnsScriptAnalyzer {
  private static final Pattern LABEL_PATTERN = Pattern.compile("^\\s*(?:@label|label)\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern BG_DECL_PATTERN = Pattern.compile("^\\s*@background\\s+(\\S+)\\s+(.+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern COMMAND_PATTERN = Pattern.compile("^\\s*\\[(.+)]\\s*$");
  private static final Pattern CHOICE_IF_SUFFIX_PATTERN = Pattern.compile("^(.*)\\[if\\s+(.+)]\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern IF_GOTO_PATTERN = Pattern.compile("^(.+?)\\s+goto\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern PARSE_LINE_PATTERN = Pattern.compile("\\bat line (\\d+)\\b", Pattern.CASE_INSENSITIVE);

  private VnsScriptAnalyzer() {
  }

  public static Analysis analyze(String text, File projectRoot) {
    return analyze(text, projectRoot, null);
  }

  public static Analysis analyze(String text, File projectRoot, File sourceFile) {
    String source = text == null ? "" : text;
    List<Diagnostic> diagnostics = new ArrayList<>();

    // Strict parser diagnostics first.
    try {
      parseWithIncludeResolver(source, projectRoot, sourceFile);
    } catch (Exception ex) {
      int line = parseLineFromMessage(ex.getMessage()) - 1;
      int start = 0;
      int end = Math.max(0, source.length());
      if (line >= 0) {
        int[] bounds = lineBounds(source, line);
        start = bounds[0];
        end = bounds[1];
      }
      diagnostics.add(Diagnostic.error(
          "parse_error",
          ex.getMessage(),
          start,
          end,
          Math.max(line, 0),
          null,
          null,
          -1
      ));
    }

    List<LineInfo> lines = splitLines(source);
    Map<String, LabelNode> labelsByName = new LinkedHashMap<>();
    List<LabelRef> refs = new ArrayList<>();
    Map<String, String> backgrounds = new LinkedHashMap<>();

    for (LineInfo line : lines) {
      String trimmed = line.trimmed();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

      Matcher labelMatcher = LABEL_PATTERN.matcher(line.text);
      if (labelMatcher.matches()) {
        String name = labelMatcher.group(1);
        int tokenStart = line.start + safeIndexOf(line.text, name, 0);
        int tokenEnd = tokenStart + name.length();
        labelsByName.putIfAbsent(name, new LabelNode(name, line.index, tokenStart, tokenEnd));
      }

      Matcher bgMatcher = BG_DECL_PATTERN.matcher(line.text);
      if (bgMatcher.matches()) {
        String bgId = bgMatcher.group(1).trim();
        String path = bgMatcher.group(2).trim();
        backgrounds.put(bgId, path);

        if (!assetExists(projectRoot, path)) {
          int pathStart = line.start + safeIndexOf(line.text, path, 0);
          diagnostics.add(Diagnostic.error(
              "missing_asset",
              "Missing background asset: " + path,
              pathStart,
              pathStart + path.length(),
              line.index,
              null,
              path,
              -1
          ));
        }
      }

      if (trimmed.startsWith(">")) {
        addChoiceReference(line, trimmed.substring(1).trim(), refs);
        continue;
      }

      Matcher cmdMatcher = COMMAND_PATTERN.matcher(trimmed);
      if (!cmdMatcher.matches()) continue;

      String body = cmdMatcher.group(1).trim();
      if (body.isEmpty()) continue;
      String[] parts = body.split("\\s+", 2);
      String cmd = parts[0].toLowerCase(Locale.ROOT);
      String arg = parts.length > 1 ? parts[1].trim() : "";

      if ("jump".equals(cmd) && !arg.isBlank()) {
        String target = firstToken(arg);
        int st = line.start + safeIndexOf(line.text, target, 0);
        refs.add(new LabelRef(target, st, st + target.length(), line.index));
      } else if ("if".equals(cmd) && !arg.isBlank()) {
        Matcher m = IF_GOTO_PATTERN.matcher(arg);
        if (m.matches()) {
          String target = m.group(2).trim();
          int st = line.start + safeIndexOf(line.text, target, 0);
          refs.add(new LabelRef(target, st, st + target.length(), line.index));
        }
      } else if ("choice".equals(cmd) && !arg.isBlank()) {
        String[] segs = arg.split("\\|");
        for (String seg : segs) {
          addChoiceReference(line, seg == null ? "" : seg.trim(), refs);
        }
      } else if (("bg".equals(cmd) || "background".equals(cmd)) && !arg.isBlank()) {
        String bgId = firstToken(arg);
        if (!backgrounds.containsKey(bgId)) {
          int st = line.start + safeIndexOf(line.text, bgId, 0);
          diagnostics.add(Diagnostic.error(
              "missing_asset",
              "Unknown background id: " + bgId,
              st,
              st + bgId.length(),
              line.index,
              null,
              null,
              -1
          ));
        }
      }
    }

    for (LabelRef ref : refs) {
      if (!labelsByName.containsKey(ref.label)) {
        diagnostics.add(Diagnostic.error(
            "undefined_label",
            "Undefined label: " + ref.label,
            ref.start,
            ref.end,
            ref.line,
            ref.label,
            null,
            -1
        ));
      }
    }

    List<LabelNode> labels = new ArrayList<>(labelsByName.values());
    labels.sort((a, b) -> Integer.compare(a.line(), b.line()));
    List<FlowEdge> edges = computeFlowEdges(source, lines, labelsByName, labels);

    if (!labels.isEmpty()) {
      String startLabel = pickStartLabel(labelsByName, labels);
      Set<String> reachable = reachableLabels(startLabel, edges);
      for (int i = 0; i < labels.size(); i++) {
        LabelNode label = labels.get(i);
        if (reachable.contains(label.name())) continue;
        int blockEnd = i + 1 < labels.size() ? labels.get(i + 1).tokenStart() : source.length();
        diagnostics.add(Diagnostic.warning(
            "unreachable_label",
            "Unreachable label: " + label.name(),
            label.tokenStart(),
            label.tokenEnd(),
            label.line(),
            label.name(),
            null,
            blockEnd
        ));
      }
    }

    diagnostics.sort((a, b) -> {
      int sa = a.start();
      int sb = b.start();
      if (sa != sb) return Integer.compare(sa, sb);
      if (a.warning() != b.warning()) return a.warning() ? 1 : -1;
      return a.kind().compareTo(b.kind());
    });

    String startLabel = labels.isEmpty() ? null : pickStartLabel(labelsByName, labels);

    // Compute script statistics
    ScriptStats stats = computeStats(lines, labels, edges);

    return new Analysis(source, diagnostics, labels, edges, startLabel, backgrounds, stats);
  }

  private static void parseWithIncludeResolver(String source, File projectRoot, File sourceFile) throws Exception {
    VnScriptParser parser = new VnScriptParser();
    File effectiveRoot = resolveProjectRoot(projectRoot, sourceFile);
    if (effectiveRoot == null || !effectiveRoot.exists()) {
      parser.parseFromString(source);
      return;
    }

    String sourceName = resolveSourceName(effectiveRoot, sourceFile);
    byte[] bytes = source == null ? new byte[0] : source.getBytes(StandardCharsets.UTF_8);
    try (InputStream in = new ByteArrayInputStream(bytes)) {
      parser.parse(in, sourceName, includePath -> openIncludeForDiagnostics(effectiveRoot, sourceName, includePath));
    }
  }

  private static File resolveProjectRoot(File projectRoot, File sourceFile) {
    File inferred = inferProjectRootFromScript(sourceFile);
    if (projectRoot == null) {
      if (inferred != null) return inferred;
      return sourceFile == null ? null : sourceFile.getParentFile();
    }
    if (sourceFile == null || inferred == null) return projectRoot;

    try {
      Path suppliedPath = projectRoot.toPath().toAbsolutePath().normalize();
      Path inferredPath = inferred.toPath().toAbsolutePath().normalize();
      Path sourcePath = sourceFile.toPath().toAbsolutePath().normalize();

      boolean suppliedContainsSource = sourcePath.startsWith(suppliedPath);
      boolean suppliedHasScripts = Files.isDirectory(suppliedPath.resolve("scripts"));
      boolean inferredHasScripts = Files.isDirectory(inferredPath.resolve("scripts"));
      if (!suppliedContainsSource) return inferred;
      if (!suppliedHasScripts && inferredHasScripts && suppliedPath.startsWith(inferredPath)) {
        return inferred;
      }
    } catch (Exception ignored) {
    }
    return projectRoot;
  }

  private static File inferProjectRootFromScript(File sourceFile) {
    if (sourceFile == null) return null;
    Path current = sourceFile.toPath().toAbsolutePath().normalize().getParent();
    while (current != null) {
      Path name = current.getFileName();
      if (name != null && "scripts".equalsIgnoreCase(name.toString())) {
        Path parent = current.getParent();
        return (parent != null ? parent : current).toFile();
      }
      current = current.getParent();
    }
    return null;
  }

  private static String resolveSourceName(File projectRoot, File sourceFile) {
    if (sourceFile == null) return "story/_analysis.vns";
    try {
      Path root = projectRoot.toPath().toAbsolutePath().normalize();
      Path scriptsRoot = root.resolve("scripts").normalize();
      Path file = sourceFile.toPath().toAbsolutePath().normalize();
      if (file.startsWith(scriptsRoot)) {
        return scriptsRoot.relativize(file).toString().replace('\\', '/');
      }
      if (file.startsWith(root)) {
        return root.relativize(file).toString().replace('\\', '/');
      }
    } catch (Exception ignored) {
    }
    return sourceFile.getName();
  }

  private static InputStream openIncludeForDiagnostics(File projectRoot, String sourceName, String includePath) throws IOException {
    String normalized = includePath == null ? "" : includePath.trim().replace('\\', '/');
    if (normalized.isBlank()) {
      throw new IOException("Include path is empty");
    }

    Path root = projectRoot.toPath().toAbsolutePath().normalize();
    Path scriptsRoot = root.resolve("scripts").normalize();
    if (!Files.isDirectory(scriptsRoot)) {
      scriptsRoot = root;
    }

    List<Path> candidates = new ArrayList<>();
    if (normalized.startsWith("/")) {
      candidates.add(scriptsRoot.resolve(normalized.substring(1)));
    } else {
      if (sourceName != null && sourceName.contains("/")) {
        Path sourceBase = scriptsRoot.resolve(sourceName).normalize().getParent();
        if (sourceBase != null) {
          candidates.add(sourceBase.resolve(normalized));
        }
      }
      candidates.add(scriptsRoot.resolve(normalized));
    }
    candidates.add(root.resolve(normalized));

    for (Path candidate : candidates) {
      if (candidate == null) continue;
      Path resolved = candidate.toAbsolutePath().normalize();
      if (!resolved.startsWith(root)) continue;
      if (Files.isRegularFile(resolved)) {
        return Files.newInputStream(resolved);
      }
    }

    throw new IOException("Included script not found: " + includePath);
  }

  private static String pickStartLabel(Map<String, LabelNode> labelsByName, List<LabelNode> labels) {
    if (labelsByName.containsKey("start")) return "start";
    return labels.isEmpty() ? null : labels.get(0).name();
  }

  private static Set<String> reachableLabels(String startLabel, List<FlowEdge> edges) {
    if (startLabel == null || startLabel.isBlank()) return Set.of();
    Map<String, Set<String>> adjacency = new HashMap<>();
    for (FlowEdge edge : edges) {
      if (!edge.definedTarget()) continue;
      adjacency.computeIfAbsent(edge.fromLabel(), k -> new HashSet<>()).add(edge.toLabel());
    }

    Set<String> reachable = new HashSet<>();
    ArrayDeque<String> queue = new ArrayDeque<>();
    queue.add(startLabel);
    while (!queue.isEmpty()) {
      String current = queue.removeFirst();
      if (!reachable.add(current)) continue;
      for (String next : adjacency.getOrDefault(current, Set.of())) {
        if (!reachable.contains(next)) queue.addLast(next);
      }
    }
    return reachable;
  }

  private static List<FlowEdge> computeFlowEdges(String source,
                                                 List<LineInfo> lines,
                                                 Map<String, LabelNode> labelsByName,
                                                 List<LabelNode> orderedLabels) {
    if (labelsByName.isEmpty() || orderedLabels.isEmpty()) return List.of();

    List<FlowEdge> edges = new ArrayList<>();
    Set<String> dedupe = new LinkedHashSet<>();

    for (int idx = 0; idx < orderedLabels.size(); idx++) {
      LabelNode label = orderedLabels.get(idx);
      int startLine = label.line() + 1;
      int endLine = idx + 1 < orderedLabels.size() ? orderedLabels.get(idx + 1).line() : lines.size();

      boolean terminal = false;
      for (int lineIndex = startLine; lineIndex < endLine; lineIndex++) {
        if (lineIndex < 0 || lineIndex >= lines.size()) continue;
        LineInfo line = lines.get(lineIndex);
        String trimmed = line.trimmed();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

        if (trimmed.startsWith(">")) {
          addChoiceEdge(label.name(), line, trimmed.substring(1).trim(), labelsByName, dedupe, edges);
          continue;
        }

        Matcher cmdMatcher = COMMAND_PATTERN.matcher(trimmed);
        if (!cmdMatcher.matches()) continue;
        String body = cmdMatcher.group(1).trim();
        if (body.isEmpty()) continue;
        String[] parts = body.split("\\s+", 2);
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1].trim() : "";

        if ("jump".equals(cmd)) {
          String target = firstToken(arg);
          addEdge(label.name(), target, line.index, FlowEdgeKind.JUMP, labelsByName, dedupe, edges);
          terminal = true;
          break;
        }

        if ("end".equals(cmd)) {
          terminal = true;
          break;
        }

        if ("if".equals(cmd)) {
          Matcher m = IF_GOTO_PATTERN.matcher(arg);
          if (m.matches()) {
            String target = m.group(2).trim();
            addEdge(label.name(), target, line.index, FlowEdgeKind.IF_GOTO, labelsByName, dedupe, edges);
          }
          continue;
        }

        if ("choice".equals(cmd)) {
          String[] segments = arg.split("\\|");
          for (String segment : segments) {
            addChoiceEdge(label.name(), line, segment == null ? "" : segment.trim(), labelsByName, dedupe, edges);
          }
        }
      }

      if (!terminal && idx + 1 < orderedLabels.size()) {
        String nextLabel = orderedLabels.get(idx + 1).name();
        addEdge(label.name(), nextLabel, label.line(), FlowEdgeKind.FALLTHROUGH, labelsByName, dedupe, edges);
      }
    }

    return edges;
  }

  private static void addChoiceEdge(String fromLabel,
                                    LineInfo line,
                                    String segment,
                                    Map<String, LabelNode> labelsByName,
                                    Set<String> dedupe,
                                    List<FlowEdge> edges) {
    LabelRef ref = extractChoiceReference(line, segment);
    if (ref == null) return;
    addEdge(fromLabel, ref.label, line.index, FlowEdgeKind.CHOICE, labelsByName, dedupe, edges);
  }

  private static void addEdge(String fromLabel,
                              String toLabel,
                              int sourceLine,
                              FlowEdgeKind kind,
                              Map<String, LabelNode> labelsByName,
                              Set<String> dedupe,
                              List<FlowEdge> edges) {
    if (fromLabel == null || fromLabel.isBlank() || toLabel == null || toLabel.isBlank()) return;
    boolean definedTarget = labelsByName.containsKey(toLabel);
    String key = fromLabel + "|" + toLabel + "|" + kind + "|" + sourceLine;
    if (!dedupe.add(key)) return;
    edges.add(new FlowEdge(fromLabel, toLabel, sourceLine, kind, definedTarget));
  }

  private static void addChoiceReference(LineInfo line, String segment, List<LabelRef> refs) {
    LabelRef ref = extractChoiceReference(line, segment);
    if (ref != null) refs.add(ref);
  }

  private static LabelRef extractChoiceReference(LineInfo line, String segment) {
    if (segment == null || segment.isBlank()) return null;
    String work = segment.trim();

    Matcher suffix = CHOICE_IF_SUFFIX_PATTERN.matcher(work);
    if (suffix.matches()) {
      work = suffix.group(1).trim();
    }

    int arrow = work.indexOf("->");
    if (arrow < 0) return null;

    String right = work.substring(arrow + 2).trim();
    if (right.isEmpty()) return null;

    String target = firstToken(right);
    int targetInLine = safeIndexOf(line.text, target, 0);
    int start = line.start + targetInLine;
    return new LabelRef(target, start, start + target.length(), line.index);
  }

  private static boolean assetExists(File projectRoot, String path) {
    if (path == null || path.isBlank()) return false;
    File resolved = resolveAssetPath(projectRoot, path);
    return resolved != null && resolved.exists() && resolved.isFile();
  }

  private static File resolveAssetPath(File projectRoot, String rawPath) {
    if (rawPath == null || rawPath.isBlank()) return null;
    String normalized = rawPath.trim().replace('\\', '/');
    File direct = new File(normalized);
    if (direct.isAbsolute()) return direct;
    if (projectRoot == null) return null;

    List<String> candidates = new ArrayList<>();
    candidates.add(normalized);

    String rel = normalized;
    if (rel.startsWith("./")) rel = rel.substring(2);
    if (rel.startsWith("game/images/")) rel = rel.substring("game/images/".length());
    if (rel.startsWith("images/")) rel = rel.substring("images/".length());
    if (rel.startsWith("assets/")) rel = rel.substring("assets/".length());

    candidates.add("assets/" + rel);
    candidates.add("assets/images/" + rel);
    candidates.add("assets/backgrounds/" + rel);

    String fileName = rel.contains("/") ? rel.substring(rel.lastIndexOf('/') + 1) : rel;
    if (!fileName.isBlank()) {
      candidates.add("assets/backgrounds/" + fileName);
      candidates.add("assets/images/backgrounds/" + fileName);
      candidates.add("game/images/backgrounds/" + fileName);
    }

    for (String candidate : candidates) {
      File f = new File(projectRoot, candidate);
      if (f.exists()) return f;
    }
    return new File(projectRoot, normalized);
  }

  private static int parseLineFromMessage(String message) {
    if (message == null || message.isBlank()) return -1;
    Matcher m = PARSE_LINE_PATTERN.matcher(message);
    if (!m.find()) return -1;
    try {
      return Integer.parseInt(m.group(1));
    } catch (Exception ignored) {
      return -1;
    }
  }

  private static List<LineInfo> splitLines(String text) {
    List<LineInfo> out = new ArrayList<>();
    if (text == null) {
      out.add(new LineInfo(0, 0, 0, ""));
      return out;
    }
    int lineIndex = 0;
    int start = 0;
    for (int i = 0; i <= text.length(); i++) {
      if (i == text.length() || text.charAt(i) == '\n') {
        String line = text.substring(start, i);
        out.add(new LineInfo(lineIndex, start, i, line));
        lineIndex++;
        start = i + 1;
      }
    }
    if (out.isEmpty()) out.add(new LineInfo(0, 0, 0, ""));
    return out;
  }

  private static int[] lineBounds(String text, int zeroBasedLine) {
    if (zeroBasedLine < 0) return new int[] {0, 0};
    int currentLine = 0;
    int start = 0;
    for (int i = 0; i <= text.length(); i++) {
      if (i == text.length() || text.charAt(i) == '\n') {
        if (currentLine == zeroBasedLine) {
          return new int[] {start, i};
        }
        currentLine++;
        start = i + 1;
      }
    }
    return new int[] {Math.max(0, text.length() - 1), text.length()};
  }

  private static String firstToken(String value) {
    if (value == null) return "";
    String t = value.trim();
    if (t.isEmpty()) return "";
    int sp = t.indexOf(' ');
    return sp < 0 ? t : t.substring(0, sp);
  }

  private static int safeIndexOf(String text, String needle, int fallback) {
    if (text == null || needle == null || needle.isEmpty()) return fallback;
    int idx = text.indexOf(needle);
    return idx >= 0 ? idx : fallback;
  }

  private static final class LineInfo {
    final int index;
    final int start;
    final int end;
    final String text;

    LineInfo(int index, int start, int end, String text) {
      this.index = index;
      this.start = start;
      this.end = end;
      this.text = text;
    }

    String trimmed() {
      return text == null ? "" : text.trim();
    }
  }

  private static final class LabelRef {
    final String label;
    final int start;
    final int end;
    final int line;

    LabelRef(String label, int start, int end, int line) {
      this.label = label;
      this.start = start;
      this.end = end;
      this.line = line;
    }
  }

  private static final Pattern DIALOGUE_LINE_PATTERN = Pattern.compile("^\\s*(\\w+)\\s*:\\s*(.+)$");

  private static ScriptStats computeStats(List<LineInfo> lines,
                                           List<LabelNode> labels,
                                           List<FlowEdge> edges) {
    int wordCount = 0;
    int dialogueLineCount = 0;
    Map<String, Integer> characterLineMap = new LinkedHashMap<>();
    int choiceBranchCount = 0;

    for (LineInfo line : lines) {
      String trimmed = line.trimmed();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

      // Count choice branches
      if (trimmed.startsWith(">")) {
        choiceBranchCount++;
        continue;
      }

      // Skip labels and directives
      if (LABEL_PATTERN.matcher(line.text).matches()) continue;
      if (BG_DECL_PATTERN.matcher(line.text).matches()) continue;
      if (trimmed.startsWith("@")) continue;

      // Skip commands (lines like [command args])
      if (COMMAND_PATTERN.matcher(trimmed).matches()) continue;

      // What's left is dialogue or narration
      String dialogueText = trimmed;
      Matcher dialogueMatcher = DIALOGUE_LINE_PATTERN.matcher(trimmed);
      if (dialogueMatcher.matches()) {
        String charId = dialogueMatcher.group(1).trim();
        dialogueText = dialogueMatcher.group(2).trim();
        characterLineMap.merge(charId, 1, Integer::sum);
      }

      dialogueLineCount++;
      // Count words (split on whitespace)
      String[] words = dialogueText.split("\\s+");
      for (String w : words) {
        if (!w.isBlank()) wordCount++;
      }
    }

    // Estimated playtime: ~150 words per minute for reading VN dialogue
    // Plus ~3 seconds per choice interaction
    double minutes = wordCount / 150.0 + (choiceBranchCount * 3.0) / 60.0;

    return new ScriptStats(
        wordCount,
        dialogueLineCount,
        characterLineMap,
        choiceBranchCount,
        labels.size(),
        Math.round(minutes * 10.0) / 10.0
    );
  }

  public record Analysis(String source,
                         List<Diagnostic> diagnostics,
                         List<LabelNode> labels,
                         List<FlowEdge> edges,
                         String startLabel,
                         Map<String, String> backgrounds,
                         ScriptStats stats) {
    public Analysis {
      diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
      labels = labels == null ? List.of() : List.copyOf(labels);
      edges = edges == null ? List.of() : List.copyOf(edges);
      backgrounds = backgrounds == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(backgrounds));
      stats = stats == null ? ScriptStats.EMPTY : stats;
    }
  }

  /**
   * Aggregated script statistics.
   */
  public record ScriptStats(
      int wordCount,
      int dialogueLineCount,
      Map<String, Integer> characterLineMap,
      int choiceBranchCount,
      int labelCount,
      double estimatedPlaytimeMinutes
  ) {
    static final ScriptStats EMPTY = new ScriptStats(0, 0, Map.of(), 0, 0, 0.0);

    public ScriptStats {
      characterLineMap = characterLineMap == null
          ? Map.of()
          : Collections.unmodifiableMap(new LinkedHashMap<>(characterLineMap));
    }
  }

  public record LabelNode(String name, int line, int tokenStart, int tokenEnd) {
  }

  public enum FlowEdgeKind {
    JUMP,
    CHOICE,
    IF_GOTO,
    FALLTHROUGH
  }

  public record FlowEdge(String fromLabel,
                         String toLabel,
                         int sourceLine,
                         FlowEdgeKind kind,
                         boolean definedTarget) {
  }

  public static final class Diagnostic {
    private final String kind;
    private final String message;
    private final int start;
    private final int end;
    private final int line;
    private final boolean warning;
    private final String label;
    private final String assetPath;
    private final int blockEnd;

    private Diagnostic(String kind,
                       String message,
                       int start,
                       int end,
                       int line,
                       boolean warning,
                       String label,
                       String assetPath,
                       int blockEnd) {
      this.kind = kind == null ? "unknown" : kind;
      this.message = message == null ? "" : message;
      this.start = Math.max(0, start);
      this.end = Math.max(this.start, end);
      this.line = Math.max(0, line);
      this.warning = warning;
      this.label = label;
      this.assetPath = assetPath;
      this.blockEnd = blockEnd;
    }

    public static Diagnostic error(String kind,
                                   String message,
                                   int start,
                                   int end,
                                   int line,
                                   String label,
                                   String assetPath,
                                   int blockEnd) {
      return new Diagnostic(kind, message, start, end, line, false, label, assetPath, blockEnd);
    }

    public static Diagnostic warning(String kind,
                                     String message,
                                     int start,
                                     int end,
                                     int line,
                                     String label,
                                     String assetPath,
                                     int blockEnd) {
      return new Diagnostic(kind, message, start, end, line, true, label, assetPath, blockEnd);
    }

    public String kind() {
      return kind;
    }

    public String message() {
      return message;
    }

    public int start() {
      return start;
    }

    public int end() {
      return end;
    }

    public int line() {
      return line;
    }

    public boolean warning() {
      return warning;
    }

    public String label() {
      return label;
    }

    public String assetPath() {
      return assetPath;
    }

    public int blockEnd() {
      return blockEnd;
    }
  }
}
