package com.jvn.core.localization;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Extracts translatable VNS and UI text from a project tree.
 */
public final class TranslationExtractor {
  private static final Pattern CHARACTER = Pattern.compile("^@character\\s+\\S+\\s+\"([^\"]*)\"\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern DIALOGUE_COLON = Pattern.compile("^([^:#@\\[][^:]{0,96}):\\s*(.+)$");
  private static final Pattern DIALOGUE_QUOTED = Pattern.compile("^(\\S+)\\s+\"((?:[^\"\\\\]|\\\\.)*)\"\\s*$");
  private static final Pattern CHOICE_CONDITION = Pattern.compile("^(.*)\\[if\\s+.+]\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern COMMAND = Pattern.compile("^\\[(\\S+)(?:\\s+(.+))?]$");
  private static final Set<String> SCRIPT_BLOCK_STARTS = Set.of("java", "timeline");
  private static final Set<String> SCRIPT_BLOCK_ENDS = Set.of("/java", "/timeline");

  private TranslationExtractor() {
  }

  public static List<TranslationEntry> extract(Path projectRoot) throws IOException {
    Path root = projectRoot == null ? Path.of(".").toAbsolutePath().normalize() : projectRoot.toAbsolutePath().normalize();
    Map<String, MutableEntry> entries = new LinkedHashMap<>();
    for (Path file : discoverFiles(root)) {
      String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
      if (name.endsWith(".vns")) {
        extractVns(root, file, entries);
      } else if (isUiConfigFile(name)) {
        extractProperties(root, file, entries);
      }
    }
    return entries.values().stream()
        .map(MutableEntry::freeze)
        .sorted(Comparator.comparing(e -> e.occurrences().isEmpty() ? "" : e.occurrences().get(0)))
        .toList();
  }

  private static List<Path> discoverFiles(Path root) throws IOException {
    if (!Files.exists(root)) return List.of();
    try (Stream<Path> stream = Files.walk(root, 12, FileVisitOption.FOLLOW_LINKS)) {
      return stream
          .filter(Files::isRegularFile)
          .filter(path -> !isIgnored(root, path))
          .filter(path -> {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            return name.endsWith(".vns") || isUiConfigFile(name);
          })
          .sorted()
          .toList();
    }
  }

  private static boolean isIgnored(Path root, Path path) {
    Path rel;
    try {
      rel = root.relativize(path.toAbsolutePath().normalize());
    } catch (Exception ex) {
      return true;
    }
    for (Path part : rel) {
      String token = part.toString();
      if (token.equals(".git") || token.equals(".gradle") || token.equals(".idea") || token.equals("build")) {
        return true;
      }
    }
    return false;
  }

  private static boolean isUiConfigFile(String name) {
    return name.endsWith(".menu")
        || name.endsWith(".layout")
        || name.endsWith(".buttonlayout")
        || name.endsWith(".style")
        || name.endsWith(".screen")
        || name.endsWith(".properties")
        || "dialogue.layout".equals(name)
        || "menu.registry".equals(name);
  }

  private static void extractVns(Path root, Path file, Map<String, MutableEntry> entries) throws IOException {
    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    String rel = slash(root.relativize(file));
    boolean inSkippedBlock = false;
    for (int i = 0; i < lines.size(); i++) {
      String raw = lines.get(i);
      String trimmed = raw == null ? "" : raw.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) continue;

      Matcher command = COMMAND.matcher(trimmed);
      if (command.matches()) {
        String name = command.group(1).toLowerCase(Locale.ROOT);
        if (SCRIPT_BLOCK_STARTS.contains(name)) {
          inSkippedBlock = true;
          continue;
        }
        if (SCRIPT_BLOCK_ENDS.contains(name)) {
          inSkippedBlock = false;
          continue;
        }
        if (!inSkippedBlock) {
          extractVnsCommand(root, file, rel, i + 1, name, command.group(2), entries);
        }
        continue;
      }
      if (inSkippedBlock) continue;

      Matcher character = CHARACTER.matcher(trimmed);
      if (character.matches()) {
        add(entries, "character", character.group(1), rel + ":" + (i + 1) + " character");
        continue;
      }

      Matcher quoted = DIALOGUE_QUOTED.matcher(trimmed);
      if (quoted.matches()) {
        add(entries, "dialogue", unescapeQuoted(quoted.group(2)), rel + ":" + (i + 1) + " dialogue speaker=" + quoted.group(1));
        continue;
      }

      Matcher dialogue = DIALOGUE_COLON.matcher(trimmed);
      if (dialogue.matches()) {
        String speaker = dialogue.group(1).trim();
        String text = dialogue.group(2).trim();
        if (!speaker.isEmpty() && !text.isEmpty()) {
          add(entries, "dialogue", stripInlineComment(text), rel + ":" + (i + 1) + " dialogue speaker=" + speaker);
        }
        continue;
      }

      if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
        String text = parseChoiceText(trimmed.substring(2));
        add(entries, "choice", text, rel + ":" + (i + 1) + " choice");
      }
    }
  }

  private static void extractVnsCommand(
      Path root,
      Path file,
      String rel,
      int line,
      String commandName,
      String payload,
      Map<String, MutableEntry> entries
  ) {
    if (payload == null || payload.isBlank()) return;
    switch (commandName) {
      case "choice", "choices", "menu" -> {
        for (String part : payload.split("\\|")) {
          add(entries, "choice", parseChoiceText(part), rel + ":" + line + " choice");
        }
      }
      case "hud" -> add(entries, "hud", payload.trim(), rel + ":" + line + " hud");
      case "screen" -> extractScreenCommandOptions(entries, payload, rel + ":" + line);
      default -> {
      }
    }
  }

  private static void extractScreenCommandOptions(Map<String, MutableEntry> entries, String payload, String occurrencePrefix) {
    for (String token : payload.split("\\s+")) {
      int eq = token.indexOf('=');
      if (eq <= 0 || eq >= token.length() - 1) continue;
      String key = token.substring(0, eq).toLowerCase(Locale.ROOT);
      String value = unquote(token.substring(eq + 1));
      if (key.equals("title") || key.equals("text") || key.equals("body") || key.equals("label")) {
        add(entries, "screen", value, occurrencePrefix + " screen." + key);
      }
    }
  }

  private static void extractProperties(Path root, Path file, Map<String, MutableEntry> entries) throws IOException {
    Properties props = new Properties();
    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      props.load(reader);
    }
    Map<String, Integer> lines = propertyLineNumbers(file);
    String rel = slash(root.relativize(file));
    for (String key : props.stringPropertyNames().stream().sorted().toList()) {
      if (!isTextProperty(key)) continue;
      String value = props.getProperty(key);
      if (!looksTranslatable(value)) continue;
      int line = lines.getOrDefault(key, 0);
      add(entries, propertyKind(key), value, rel + (line > 0 ? ":" + line : "") + " property " + key);
    }
  }

  private static Map<String, Integer> propertyLineNumbers(Path file) throws IOException {
    Map<String, Integer> out = new LinkedHashMap<>();
    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i).trim();
      if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue;
      int sep = separatorIndex(line);
      if (sep <= 0) continue;
      String key = line.substring(0, sep).trim();
      if (!key.isEmpty()) out.putIfAbsent(unescapeKey(key), i + 1);
    }
    return out;
  }

  private static int separatorIndex(String line) {
    boolean escaped = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (escaped) {
        escaped = false;
        continue;
      }
      if (c == '\\') {
        escaped = true;
        continue;
      }
      if (c == '=' || c == ':') return i;
      if (Character.isWhitespace(c)) return i;
    }
    return -1;
  }

  private static String parseChoiceText(String raw) {
    if (raw == null) return "";
    String text = raw.trim();
    Matcher cond = CHOICE_CONDITION.matcher(text);
    if (cond.matches()) text = cond.group(1).trim();
    int arrow = text.indexOf("->");
    if (arrow >= 0) text = text.substring(0, arrow).trim();
    return stripInlineComment(text);
  }

  private static String stripInlineComment(String value) {
    if (value == null) return "";
    String text = value.trim();
    int hash = text.indexOf(" #");
    if (hash >= 0) text = text.substring(0, hash).trim();
    return text;
  }

  private static void add(Map<String, MutableEntry> entries, String kind, String sourceText, String occurrence) {
    if (!looksTranslatable(sourceText)) return;
    String text = sourceText.trim();
    String key = Localization.sourceKey(text);
    MutableEntry entry = entries.computeIfAbsent(key, ignored -> new MutableEntry(key, text, kind));
    entry.occurrences.add(occurrence);
  }

  private static boolean isTextProperty(String key) {
    if (key == null || key.isBlank()) return false;
    String lower = key.toLowerCase(Locale.ROOT);
    if (lower.contains("color")
        || lower.contains("font")
        || lower.contains("asset")
        || lower.contains("path")
        || lower.endsWith(".action")
        || lower.endsWith(".target")
        || lower.endsWith(".style")
        || lower.endsWith(".enabled")) {
      return false;
    }
    return lower.endsWith("label")
        || lower.endsWith("title")
        || lower.endsWith("titletext")
        || lower.endsWith("subtitletext")
        || lower.endsWith("hint")
        || lower.endsWith("hintstext")
        || lower.endsWith("text")
        || lower.endsWith("body")
        || lower.endsWith("tooltip")
        || lower.endsWith("placeholder");
  }

  private static String propertyKind(String key) {
    String lower = key.toLowerCase(Locale.ROOT);
    if (lower.contains("label")) return "label";
    if (lower.contains("hint")) return "hint";
    if (lower.contains("title")) return "title";
    return "ui";
  }

  private static boolean looksTranslatable(String value) {
    if (value == null) return false;
    String text = value.trim();
    if (text.isEmpty()) return false;
    if (text.startsWith("i18n:")) return false;
    String lower = text.toLowerCase(Locale.ROOT);
    if (lower.equals("true") || lower.equals("false") || lower.equals("on") || lower.equals("off")) return false;
    if (text.matches("[-+]?\\d+(?:\\.\\d+)?")) return false;
    if (text.matches("#?[0-9a-fA-F]{6,8}")) return false;
    if (lower.matches(".*\\.(png|jpg|jpeg|webp|gif|ogg|mp3|wav|flac|vns|jes|json|xml|properties)$")) return false;
    return text.codePoints().anyMatch(cp -> Character.isLetter(cp) || Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
  }

  private static String unescapeQuoted(String text) {
    if (text == null || text.indexOf('\\') < 0) return text == null ? "" : text;
    StringBuilder out = new StringBuilder();
    boolean escaped = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (escaped) {
        switch (c) {
          case 'n' -> out.append('\n');
          case 'r' -> out.append('\r');
          case 't' -> out.append('\t');
          case '"' -> out.append('"');
          case '\\' -> out.append('\\');
          default -> out.append(c);
        }
        escaped = false;
      } else if (c == '\\') {
        escaped = true;
      } else {
        out.append(c);
      }
    }
    if (escaped) out.append('\\');
    return out.toString();
  }

  private static String unquote(String value) {
    if (value == null) return "";
    String text = value.trim();
    if (text.length() >= 2 && ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'")))) {
      return unescapeQuoted(text.substring(1, text.length() - 1));
    }
    return text;
  }

  private static String unescapeKey(String key) {
    return key.replace("\\:", ":").replace("\\=", "=").replace("\\ ", " ").replace("\\\\", "\\");
  }

  private static String slash(Path path) {
    return path == null ? "" : path.toString().replace('\\', '/');
  }

  private static final class MutableEntry {
    final String key;
    final String sourceText;
    final String kind;
    final Set<String> occurrences = new LinkedHashSet<>();

    MutableEntry(String key, String sourceText, String kind) {
      this.key = key;
      this.sourceText = sourceText;
      this.kind = kind;
    }

    TranslationEntry freeze() {
      return new TranslationEntry(key, sourceText, kind, new ArrayList<>(occurrences));
    }
  }
}
