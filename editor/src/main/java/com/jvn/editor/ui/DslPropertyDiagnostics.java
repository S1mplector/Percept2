package com.jvn.editor.ui;

import com.jvn.core.menu.config.MenuActionType;

import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Line-aware diagnostics for layout/menu DSL files.
 * Produces warnings with line numbers and quick-fix hints.
 */
public final class DslPropertyDiagnostics {
  private DslPropertyDiagnostics() {
  }

  private static final Pattern QUOTED_KEY = Pattern.compile("'([^']+)'");
  private static final Set<String> MENU_STYLE_REQUIRED_COLORS = Set.of(
      "itemColor",
      "itemSelectedColor",
      "itemDisabledColor"
  );
  private static final Set<String> MENU_STYLE_COLOR_KEYS = Set.of(
      "itemColor", "itemSelectedColor", "itemHoverColor", "itemDisabledColor",
      "itemShadowColor", "titleColor", "titleShadowColor", "hintsColor", "backgroundColor"
  );
  private static final Set<String> MENU_STYLE_NUMERIC_KEYS = Set.of(
      "itemFontSize", "itemShadowOffsetX", "itemShadowOffsetY", "itemOpacity",
      "buttonTextPaddingX", "buttonTextPaddingY",
      "titleFontSize", "hintsFontSize", "backgroundOpacity"
  );
  private static final Set<String> MENU_STYLE_ASSET_KEYS = Set.of(
      "buttonAsset", "buttonSelectedAsset", "buttonHoverAsset", "buttonDisabledAsset", "backgroundAsset"
  );
  private static final Set<String> MENU_SCREEN_BOOLEAN_ITEM_FIELDS = Set.of("enabled", "slotPreviewEnabled");
  private static final Set<String> MENU_SCREEN_NORMALIZED_FIELDS = Set.of(
      "boundsX", "boundsY", "slotPreviewX", "slotPreviewY"
  );
  private static final Set<String> MENU_SCREEN_POSITIVE_NORMALIZED_FIELDS = Set.of(
      "boundsWidth", "boundsHeight", "slotPreviewWidth", "slotPreviewHeight"
  );
  private static final Set<String> MENU_SCREEN_ACTIONS = buildKnownActions();

  public static List<String> menuLayoutIssues(String text, Set<String> knownKeys) {
    ParsedText parsed = parseProperties(text);
    LinkedHashSet<String> issues = new LinkedHashSet<>(parsed.issues());

    for (PropertyLine line : parsed.properties()) {
      String key = line.key();
      String value = line.value();
      if (!knownKeys.contains(key)) {
        issues.add(unknownKeyIssue(line.line(), key, knownKeys,
            "Fix typo or move to Custom Properties if this extension is intentional."));
        continue;
      }
      switch (key) {
        case "listYStart" -> addMinCheck(issues, line.line(), key, value, 0.0,
            "Set to a value >= 0. Use 0..1 for normalized Y.");
        case "lineHeight" -> addExclusiveMinCheck(issues, line.line(), key, value, 0.0,
            "Use a positive row height, for example 62.");
        case "listWidthFactor" -> addRangeCheck(issues, line.line(), key, value, 0.1, 1.0,
            "Use 0.1..1.0 (for example 0.44).");
        case "hintsBottomMargin" -> addMinCheck(issues, line.line(), key, value, 0.0,
            "Use a value >= 0.");
        case "titleY" -> addMinCheck(issues, line.line(), key, value, 0.0,
            "Use >= 0. Use 0..1 for normalized position.");
        case "textAlign" -> {
          String normalized = normalize(value);
          if (!Set.of("left", "center", "right").contains(normalized)) {
            issues.add(issue(line.line(), key,
                "Unsupported align value '" + value + "'.",
                "Use one of: left, center, right."));
          }
        }
        default -> {
          // No-op for future keys that are added to known set.
        }
      }
    }
    return new ArrayList<>(issues);
  }

  public static List<String> menuStyleIssues(String text, Set<String> knownKeys) {
    ParsedText parsed = parseProperties(text);
    LinkedHashSet<String> issues = new LinkedHashSet<>(parsed.issues());

    for (PropertyLine line : parsed.properties()) {
      String key = line.key();
      String value = line.value();
      if (!knownKeys.contains(key)) {
        issues.add(unknownKeyIssue(line.line(), key, knownKeys,
            "Fix typo or keep in Custom Properties only if runtime supports this key."));
        continue;
      }
      if (MENU_STYLE_REQUIRED_COLORS.contains(key) && normalize(value).isBlank()) {
        issues.add(issue(line.line(), key, key + " is required.", "Set a hex color like #D3D3D3."));
      }
      if (MENU_STYLE_COLOR_KEYS.contains(key) && !normalize(value).isBlank() && !isValidColor(value)) {
        issues.add(issue(line.line(), key,
            "Invalid color value '" + value + "'.",
            "Use #RRGGBB or #RRGGBBAA."));
      }
      if (MENU_STYLE_ASSET_KEYS.contains(key) && value.contains("\\")) {
        issues.add(issue(line.line(), key,
            "Asset path uses backslashes.",
            "Use forward slashes, for example assets/ui/menu/button.png."));
      }
      if (MENU_STYLE_NUMERIC_KEYS.contains(key)) {
        switch (key) {
          case "itemFontSize" -> addRangeCheck(issues, line.line(), key, value, 8, 96,
              "Use 8..96.");
          case "itemShadowOffsetX", "itemShadowOffsetY" -> addRangeCheck(issues, line.line(), key, value, -20, 20,
              "Use -20..20.");
          case "itemOpacity", "backgroundOpacity" -> addRangeCheck(issues, line.line(), key, value, 0, 1,
              "Use 0..1.");
          case "buttonTextPaddingX" -> addRangeCheck(issues, line.line(), key, value, 0, 240,
              "Use 0..240.");
          case "buttonTextPaddingY" -> addRangeCheck(issues, line.line(), key, value, -120, 120,
              "Use -120..120.");
          case "titleFontSize" -> addRangeCheck(issues, line.line(), key, value, 8, 120,
              "Use 8..120.");
          case "hintsFontSize" -> addRangeCheck(issues, line.line(), key, value, 8, 48,
              "Use 8..48.");
          default -> {
            // no-op
          }
        }
      }
    }
    return new ArrayList<>(issues);
  }

  public static List<String> menuScreenIssues(String text, Set<String> topLevelKeys, Set<String> itemKeys) {
    ParsedText parsed = parseProperties(text);
    LinkedHashSet<String> issues = new LinkedHashSet<>(parsed.issues());

    for (PropertyLine line : parsed.properties()) {
      String key = line.key();
      String value = line.value();
      if (key.startsWith("item.")) {
        int secondDot = key.indexOf('.', "item.".length());
        if (secondDot <= "item.".length() || secondDot >= key.length() - 1) {
          issues.add(issue(line.line(), key,
              "Malformed item key.",
              "Use item.<id>.<field>, for example item.start.label."));
          continue;
        }
        String itemId = key.substring("item.".length(), secondDot).trim();
        String field = key.substring(secondDot + 1).trim();
        if (itemId.isBlank()) {
          issues.add(issue(line.line(), key,
              "Item id is empty in key '" + key + "'.",
              "Use a stable id like item.start.label."));
          continue;
        }
        if (!itemKeys.contains(field)) {
          issues.add(unknownFieldIssue(line.line(), key, field, itemKeys,
              "Fix typo or remove unsupported item field."));
          continue;
        }
        if (MENU_SCREEN_BOOLEAN_ITEM_FIELDS.contains(field)) {
          addBooleanCheck(issues, line.line(), key, value, "Use true or false.");
        }
        if (MENU_SCREEN_NORMALIZED_FIELDS.contains(field)) {
          addRangeCheck(issues, line.line(), key, value, 0, 1,
              "Use a normalized value in 0..1.");
        }
        if (MENU_SCREEN_POSITIVE_NORMALIZED_FIELDS.contains(field)) {
          addRangeCheck(issues, line.line(), key, value, 0.000001, 1,
              "Use a normalized size in (0..1].");
        }
        if ("action".equals(field)) {
          String action = normalize(value).replace('-', '_');
          if (!action.isBlank() && !MENU_SCREEN_ACTIONS.contains(action)) {
            String suggestion = closest(action, MENU_SCREEN_ACTIONS);
            String fix = suggestion == null
                ? "Use a built-in action (e.g. open_menu, back, save_menu) or keep custom knowingly."
                : "Did you mean '" + suggestion + "'?";
            issues.add(issue(line.line(), key, "Unknown action '" + value + "'.", fix));
          }
        }
        continue;
      }

      if (!topLevelKeys.contains(key)) {
        issues.add(unknownKeyIssue(line.line(), key, topLevelKeys,
            "Fix typo or keep in Additional custom keys only if runtime supports it."));
        continue;
      }

      if ("wrapSelection".equals(key)) {
        addBooleanCheck(issues, line.line(), key, value, "Use true or false.");
      } else if ("items".equals(key)) {
        List<String> ids = parseCsv(value);
        if (ids.isEmpty()) {
          issues.add(issue(line.line(), key, "Items list is empty.", "Add at least one item id, e.g. items=start,back."));
        }
      }
    }
    return new ArrayList<>(issues);
  }

  public static List<String> dialogueIssues(String text, List<String> parserDiagnostics) {
    ParsedText parsed = parseProperties(text);
    LinkedHashSet<String> issues = new LinkedHashSet<>(parsed.issues());

    if (parserDiagnostics == null || parserDiagnostics.isEmpty()) {
      return new ArrayList<>(issues);
    }

    for (String diagnostic : parserDiagnostics) {
      if (diagnostic == null || diagnostic.isBlank()) continue;
      String message = diagnostic.trim();
      String key = extractQuotedKey(message);
      Integer line = key == null ? null : parsed.lastLineByKey().get(key);
      String quickFix = quickFixForDialogue(message, key);
      if (line != null) {
        issues.add(issue(line, key == null ? "dsl" : key, message, quickFix));
      } else {
        issues.add("DSL: " + message + (quickFix == null ? "" : " Quick fix: " + quickFix));
      }
    }
    return new ArrayList<>(issues);
  }

  private static ParsedText parseProperties(String text) {
    List<PropertyLine> lines = new ArrayList<>();
    Map<String, Integer> lastLineByKey = new LinkedHashMap<>();
    Map<String, Integer> firstLineByKey = new LinkedHashMap<>();
    List<String> issues = new ArrayList<>();

    String normalized = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
    String[] rows = normalized.split("\n", -1);
    for (int i = 0; i < rows.length; i++) {
      int lineNo = i + 1;
      String raw = rows[i];
      String trimmed = raw == null ? "" : raw.trim();
      if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("!")) continue;

      int sep = findSeparator(raw);
      if (sep < 0) {
        issues.add(issue(lineNo, "dsl", "Malformed line '" + trimmed + "'.", "Use key=value."));
        continue;
      }
      String key = raw.substring(0, sep).trim();
      String value = raw.substring(sep + 1).trim();
      if (key.isBlank()) {
        issues.add(issue(lineNo, "dsl", "Property key is empty.", "Provide a key name before '='."));
        continue;
      }
      lines.add(new PropertyLine(lineNo, key, value));
      Integer firstLine = firstLineByKey.putIfAbsent(key, lineNo);
      if (firstLine != null) {
        issues.add(issue(lineNo, key,
            "Duplicate key '" + key + "' (first defined at L" + firstLine + ").",
            "Remove duplicate or keep only one declaration."));
      }
      lastLineByKey.put(key, lineNo);
    }
    return new ParsedText(lines, lastLineByKey, issues);
  }

  private static int findSeparator(String raw) {
    if (raw == null) return -1;
    int eq = raw.indexOf('=');
    int colon = raw.indexOf(':');
    if (eq < 0) return colon;
    if (colon < 0) return eq;
    return Math.min(eq, colon);
  }

  private static void addBooleanCheck(LinkedHashSet<String> issues, int line, String key, String value, String fix) {
    String normalized = normalize(value);
    if (normalized.equals("true") || normalized.equals("false")
        || normalized.equals("yes") || normalized.equals("no")
        || normalized.equals("1") || normalized.equals("0")) {
      return;
    }
    issues.add(issue(line, key, "Invalid boolean '" + value + "'.", fix));
  }

  private static void addExclusiveMinCheck(LinkedHashSet<String> issues, int line, String key, String value, double minExclusive, String fix) {
    Double parsed = tryParseDouble(value);
    if (parsed == null) {
      issues.add(issue(line, key, "Invalid number '" + value + "'.", "Use a finite number."));
      return;
    }
    if (parsed <= minExclusive) {
      issues.add(issue(line, key, "Value " + formatNumber(parsed) + " must be > " + formatNumber(minExclusive) + ".", fix));
    }
  }

  private static void addMinCheck(LinkedHashSet<String> issues, int line, String key, String value, double minInclusive, String fix) {
    Double parsed = tryParseDouble(value);
    if (parsed == null) {
      issues.add(issue(line, key, "Invalid number '" + value + "'.", "Use a finite number."));
      return;
    }
    if (parsed < minInclusive) {
      issues.add(issue(line, key, "Value " + formatNumber(parsed) + " must be >= " + formatNumber(minInclusive) + ".", fix));
    }
  }

  private static void addRangeCheck(LinkedHashSet<String> issues, int line, String key, String value, double min, double max, String fix) {
    Double parsed = tryParseDouble(value);
    if (parsed == null) {
      issues.add(issue(line, key, "Invalid number '" + value + "'.", "Use a finite number."));
      return;
    }
    if (parsed < min || parsed > max) {
      issues.add(issue(line, key,
          "Value " + formatNumber(parsed) + " is out of range [" + formatNumber(min) + ", " + formatNumber(max) + "].",
          fix));
    }
  }

  private static String unknownKeyIssue(int line, String key, Set<String> knownKeys, String fallbackFix) {
    String suggestion = closest(key, knownKeys);
    String fix = suggestion == null ? fallbackFix : "Did you mean '" + suggestion + "'?";
    return issue(line, key, "Unknown key '" + key + "'.", fix);
  }

  private static String unknownFieldIssue(int line, String key, String field, Set<String> knownFields, String fallbackFix) {
    String suggestion = closest(field, knownFields);
    String fix = suggestion == null ? fallbackFix : "Did you mean field '" + suggestion + "'?";
    return issue(line, key, "Unknown item field '" + field + "' in key '" + key + "'.", fix);
  }

  private static String quickFixForDialogue(String message, String key) {
    String lower = normalize(message);
    if (lower.contains("unknown dialogue layout key")) {
      return "Fix typo or remove unsupported key.";
    }
    if (lower.contains("unknown textbox button field")) {
      return "Use known fields: label, action, target, enabled, asset, hoverAsset, disabledAsset, boundsPoints, x, y, width, height.";
    }
    if (lower.contains("malformed textbox button key")) {
      return "Use textBoxButton.<id>.<field>.";
    }
    if (lower.contains("invalid number")) {
      return "Enter a finite number, for example 0.58 or 24.";
    }
    if (lower.contains("invalid integer")) {
      return "Use an integer value.";
    }
    if (lower.contains("invalid boolean")) {
      return "Use true or false.";
    }
    if (lower.contains("was adjusted to")) {
      return "Use a value inside the allowed runtime range to avoid implicit clamping.";
    }
    if (lower.contains("open_menu without target")) {
      return "Add " + (key == null ? "a target key." : key.replace(".action", ".target") + "=<menuId>.");
    }
    if (lower.contains("duplicate textbox button id")) {
      return "Make button ids unique in textBoxButton.ids.";
    }
    if (lower.contains("invalid bounds points")) {
      return "Provide at least 3 valid points.";
    }
    return "Review this key/value in the layout docs and adjust the declaration.";
  }

  private static String extractQuotedKey(String diagnostic) {
    if (diagnostic == null || diagnostic.isBlank()) return null;
    Matcher matcher = QUOTED_KEY.matcher(diagnostic);
    if (!matcher.find()) return null;
    return matcher.group(1);
  }

  private static Set<String> buildKnownActions() {
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (MenuActionType type : MenuActionType.values()) {
      if (type == null) continue;
      out.add(type.name().toLowerCase(Locale.ROOT));
    }
    // Common aliases used by authored menu files.
    out.addAll(Arrays.asList("noop", "no_op", "none"));
    return out;
  }

  private static List<String> parseCsv(String value) {
    List<String> out = new ArrayList<>();
    if (value == null || value.isBlank()) return out;
    for (String part : value.split(",")) {
      String id = part.trim();
      if (!id.isBlank()) out.add(id);
    }
    return out;
  }

  private static boolean isValidColor(String value) {
    try {
      Color.web(value.trim());
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  private static Double tryParseDouble(String value) {
    if (value == null || value.isBlank()) return null;
    try {
      double parsed = Double.parseDouble(value.trim());
      if (!Double.isFinite(parsed)) return null;
      return parsed;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
  }

  private static String issue(int line, String key, String message, String quickFix) {
    return "L" + line + " " + key + ": " + message + " Quick fix: " + quickFix;
  }

  private static String closest(String raw, Set<String> candidates) {
    if (raw == null || raw.isBlank() || candidates == null || candidates.isEmpty()) return null;
    String target = normalize(raw);
    String best = null;
    int bestDistance = Integer.MAX_VALUE;
    for (String candidate : candidates) {
      if (candidate == null || candidate.isBlank()) continue;
      String normalized = normalize(candidate);
      int distance = levenshtein(target, normalized);
      if (distance < bestDistance) {
        bestDistance = distance;
        best = candidate;
      }
    }
    if (best == null) return null;
    return bestDistance <= 4 ? best : null;
  }

  private static int levenshtein(String a, String b) {
    int[][] dp = new int[a.length() + 1][b.length() + 1];
    for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
    for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
    for (int i = 1; i <= a.length(); i++) {
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        dp[i][j] = Math.min(
            Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
            dp[i - 1][j - 1] + cost
        );
      }
    }
    return dp[a.length()][b.length()];
  }

  private static String formatNumber(double value) {
    if (Math.rint(value) == value) return Long.toString(Math.round(value));
    return String.format(Locale.ROOT, "%.4f", value)
        .replaceAll("0+$", "")
        .replaceAll("\\.$", "");
  }

  private record PropertyLine(int line, String key, String value) {
  }

  private record ParsedText(List<PropertyLine> properties, Map<String, Integer> lastLineByKey, List<String> issues) {
  }
}
