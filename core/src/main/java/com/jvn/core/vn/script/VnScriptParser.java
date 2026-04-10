package com.jvn.core.vn.script;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jvn.core.animation.Easing;
import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.vn.CharacterPosition;
import com.jvn.core.vn.Choice;
import com.jvn.core.vn.LayeredCharacterResolver;
import com.jvn.core.vn.VnArgTokenizer;
import com.jvn.core.vn.VnConditionEvaluator;
import com.jvn.core.vn.VnParticleCommand;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioBuilder;
import com.jvn.core.vn.VnTransition;
import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.core.vn.stage.VnStagePresetLoader;

/**
 * Parses text-based VN scripts into {@link VnScenario} objects.
 */
public class VnScriptParser {
  private static final Pattern SCENARIO_PATTERN = Pattern.compile("^@scenario\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARACTER_PATTERN = Pattern.compile("^@character\\s+(\\S+)\\s+\"([^\"]*)\"$", Pattern.CASE_INSENSITIVE);
  private static final Pattern BACKGROUND_PATTERN = Pattern.compile("^@background\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARIMG_PATTERN = Pattern.compile("^@charimg\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARLAYER_PATTERN = Pattern.compile("^@charlayer\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARPRESET_PATTERN = Pattern.compile("^@charpreset\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern STAGE_PRESET_PATTERN = Pattern.compile("^@stagepreset\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern VAR_PATTERN = Pattern.compile("^@var\\s+(\\S+)(?:\\s*=\\s*(.+)|\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern LABEL_PATTERN = Pattern.compile("^@label\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern LABEL_LEGACY_PATTERN = Pattern.compile("^label\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern DIALOGUE_PATTERN = Pattern.compile("^([^:]+):\\s*(.+)$");
  private static final Pattern DIALOGUE_QUOTED_PATTERN = Pattern.compile("^(\\S+)\\s+\"((?:[^\"\\\\]|\\\\.)*)\"$");
  private static final Pattern COMMAND_PATTERN = Pattern.compile("^\\[(.+)]$");
  private static final Pattern POSITION_PATTERN = Pattern.compile("^@position\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern DEFINE_PATTERN = Pattern.compile("^@define\\s+(\\S+)(?:\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern INCLUDE_PATTERN = Pattern.compile("^@include\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern DEFINE_SUB_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
  private static final Pattern CHOICE_CONDITION_SUFFIX_PATTERN = Pattern.compile("^(.*)\\[if\\s+(.+)]\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern IF_GOTO_PATTERN = Pattern.compile("^(.+?)\\s+goto\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern LABEL_NAME_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_.:-]*$");

  public interface IncludeResolver {
    InputStream open(String path) throws IOException;
  }

  private boolean isIntegerToken(String token) {
    if (token == null || token.isBlank()) return false;
    int start = (token.charAt(0) == '-') ? 1 : 0;
    if (start >= token.length()) return false;
    for (int i = start; i < token.length(); i++) {
      if (!Character.isDigit(token.charAt(i))) return false;
    }
    return true;
  }

  private boolean isBooleanToken(String token) {
    if (token == null || token.isBlank()) return false;
    String t = token.trim().toLowerCase();
    return "true".equals(t) || "false".equals(t)
        || "on".equals(t) || "off".equals(t)
        || "1".equals(t) || "0".equals(t)
        || "yes".equals(t) || "no".equals(t);
  }

  private boolean parseBooleanToken(String token) {
    String t = token.trim().toLowerCase();
    return "true".equals(t) || "on".equals(t) || "1".equals(t) || "yes".equals(t);
  }

  private String quoteTokenIfNeeded(String token) {
    if (token == null) return "\"\"";
    boolean needsQuotes = token.isBlank()
        || token.chars().anyMatch(Character::isWhitespace)
        || token.indexOf('"') >= 0
        || token.indexOf('\\') >= 0;
    if (!needsQuotes) return token;
    return "\"" + token.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private float parseVolumeToken(String token,
                                 String sourceName,
                                 int lineNumber,
                                 String rawLine,
                                 String commandName) throws IOException {
    final float volume;
    try {
      volume = Float.parseFloat(token);
    } catch (NumberFormatException ex) {
      throw parseError(sourceName, lineNumber, commandName + " volume must be numeric", rawLine);
    }
    if (volume < 0f || volume > 1f) {
      throw parseError(sourceName, lineNumber, commandName + " volume must be between 0 and 1", rawLine);
    }
    return volume;
  }

  private float parseUnitRangeToken(String token,
                                    String sourceName,
                                    int lineNumber,
                                    String rawLine,
                                    String commandName,
                                    String fieldName) throws IOException {
    final float value;
    try {
      value = Float.parseFloat(token);
    } catch (NumberFormatException ex) {
      throw parseError(sourceName, lineNumber, commandName + " " + fieldName + " must be numeric", rawLine);
    }
    if (value < 0f || value > 1f) {
      throw parseError(sourceName, lineNumber, commandName + " " + fieldName + " must be between 0 and 1", rawLine);
    }
    return value;
  }

  private KeyValueOption parseKeyValueOption(String token,
                                             String sourceName,
                                             int lineNumber,
                                             String rawLine,
                                             String commandName) throws IOException {
    if (token == null || token.isBlank()) {
      throw parseError(sourceName, lineNumber, commandName + " has an empty option token", rawLine);
    }
    int eq = token.indexOf('=');
    int colon = token.indexOf(':');
    int sep;
    if (eq > 0 && colon > 0) sep = Math.min(eq, colon);
    else sep = Math.max(eq, colon);
    if (sep <= 0 || sep >= token.length() - 1) {
      throw parseError(sourceName, lineNumber, commandName + " options must use key:value or key=value syntax", rawLine);
    }
    String key = token.substring(0, sep).trim().toLowerCase();
    String value = token.substring(sep + 1).trim();
    if (key.isEmpty() || value.isEmpty()) {
      throw parseError(sourceName, lineNumber, commandName + " has malformed option: " + token, rawLine);
    }
    return new KeyValueOption(key, value);
  }

  private String formatNumber(double value) {
    if (Math.abs(value - Math.rint(value)) < 1e-9) {
      return Long.toString((long) Math.rint(value));
    }
    String s = String.format(java.util.Locale.ROOT, "%.4f", value);
    return s.replaceAll("0+$", "").replaceAll("\\.$", "");
  }

  private static class ParseState {
    String scenarioId = "untitled";
    VnScenarioBuilder builder;
    boolean scenarioDeclared = false;
    boolean contentEmitted = false;
    List<Choice> pendingChoices = new ArrayList<>();
    Map<String, com.jvn.core.vn.VnCharacter.Builder> charBuilders = new HashMap<>();
    Map<String, Map<String, String>> charLayers = new HashMap<>();
    Map<String, String> defines = new HashMap<>();
    Map<String, LabelDeclaration> declaredLabels = new HashMap<>();
    List<LabelReference> labelReferences = new ArrayList<>();
    Deque<ConditionalBlock> conditionalBlocks = new ArrayDeque<>();
    int syntheticLabelCounter = 0;
    Map<String, CharacterPosition> customPositions = new HashMap<>();
    Map<String, String> inlineCompositeExpressions = new HashMap<>();
    String pendingVoiceTrackId;

    CharacterPosition getCustomPosition(String name) {
      if (name == null || name.isBlank()) return null;
      return customPositions.get(name.trim().toLowerCase());
    }
  }

  private static final class LabelDeclaration {
    final String source;
    final int line;
    final boolean synthetic;

    LabelDeclaration(String source, int line, boolean synthetic) {
      this.source = source;
      this.line = line;
      this.synthetic = synthetic;
    }
  }

  private record KeyValueOption(String key, String value) {}

  private static final class LabelReference {
    final String label;
    final String source;
    final int line;
    final String rawLine;
    final String kind;

    LabelReference(String label, String source, int line, String rawLine, String kind) {
      this.label = label;
      this.source = source;
      this.line = line;
      this.rawLine = rawLine;
      this.kind = kind;
    }
  }

  private static final class ConditionalBlock {
    String falseLabel;
    final String endLabel;
    boolean elseSeen;
    final String source;
    final int line;

    ConditionalBlock(String falseLabel, String endLabel, String source, int line) {
      this.falseLabel = falseLabel;
      this.endLabel = endLabel;
      this.source = source;
      this.line = line;
      this.elseSeen = false;
    }
  }

  private static final class ParsedChoice {
    final String text;
    final String target;
    final String condition;

    ParsedChoice(String text, String target, String condition) {
      this.text = text;
      this.target = target;
      this.condition = condition;
    }

    Choice toChoice() {
      Choice.Builder b = Choice.builder().text(text);
      if (condition != null && !condition.isBlank()) b.condition(condition);
      if (target != null && !target.isBlank()) b.targetLabel(target);
      return b.build();
    }
  }

  private static final class IfGoto {
    final String expression;
    final String label;

    IfGoto(String expression, String label) {
      this.expression = expression;
      this.label = label;
    }
  }

  private record LayerReference(String characterId, String layerId) {}

  public VnScenario parse(InputStream input) throws IOException {
    return parse(input, "<input>", null);
  }

  public VnScenario parse(InputStream input, String sourceName, IncludeResolver resolver) throws IOException {
    ParseState state = new ParseState();
    Deque<String> includeStack = new ArrayDeque<>();
    String src = normalizeSourceName(sourceName);
    includeStack.push(src);
    try {
      parseInto(input, src, resolver, state, includeStack);
    } finally {
      includeStack.pop();
    }

    if (!state.conditionalBlocks.isEmpty()) {
      ConditionalBlock open = state.conditionalBlocks.peek();
      throw parseError(open.source, open.line, "Unclosed [if] block (missing [endif])", "[if ...]");
    }

    ensureBuilder(state);
    flushChoices(state.builder, state.pendingChoices);
    flushPendingVoice(state);
    validateLabelReferences(state);

    // Finalize characters with expressions (replaces earlier simple character entries).
    for (var e : state.charBuilders.entrySet()) {
      state.builder.addCharacter(e.getValue().build());
    }
    return state.builder.build();
  }

  public VnScenario parse(String scriptPath) throws IOException {
    AssetCatalog cat = new AssetCatalog();
    try (InputStream in = cat.open(AssetType.SCRIPT, scriptPath)) {
      return parse(in, scriptPath, p -> cat.open(AssetType.SCRIPT, p));
    }
  }

  public VnScenario parseFromString(String script) throws IOException {
    byte[] bytes = script == null ? new byte[0] : script.getBytes(StandardCharsets.UTF_8);
    return parse(new java.io.ByteArrayInputStream(bytes), "<string>", null);
  }

  private void parseInto(InputStream input,
                         String sourceName,
                         IncludeResolver resolver,
                         ParseState state,
                         Deque<String> includeStack) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    String line;
    int lineNumber = 0;

    while ((line = reader.readLine()) != null) {
      lineNumber++;
      String rawLine = line;
      String trimmed = rawLine.trim();

      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }

      Matcher defineMatcher = DEFINE_PATTERN.matcher(trimmed);
      if (defineMatcher.matches()) {
        String key = defineMatcher.group(1);
        String value = defineMatcher.group(2) != null ? defineMatcher.group(2).trim() : "";
        state.defines.put(key, stripQuotes(value));
        continue;
      }

      Matcher includeMatcher = INCLUDE_PATTERN.matcher(trimmed);
      if (includeMatcher.matches()) {
        String includePath = stripQuotes(includeMatcher.group(1).trim());
        if (includePath.isEmpty()) continue;
        if (resolver == null) {
          throw parseError(sourceName, lineNumber, "Include resolver not configured", rawLine);
        }
        String resolved = normalizeSourceName(resolveIncludePath(sourceName, includePath));
        if (includeStack.contains(resolved)) {
          throw parseError(sourceName, lineNumber, "Include cycle detected for " + resolved, rawLine);
        }
        try (InputStream inc = resolver.open(resolved)) {
          includeStack.push(resolved);
          try {
            parseInto(inc, resolved, resolver, state, includeStack);
          } finally {
            includeStack.pop();
          }
        }
        continue;
      }

      if (!state.defines.isEmpty()) {
        rawLine = applyDefines(rawLine, state.defines);
        trimmed = rawLine.trim();
      }

      Matcher scenarioMatcher = SCENARIO_PATTERN.matcher(trimmed);
      if (scenarioMatcher.matches()) {
        if (state.scenarioDeclared) {
          throw parseError(sourceName, lineNumber, "Scenario already declared", rawLine);
        }
        if (state.contentEmitted) {
          throw parseError(sourceName, lineNumber, "@scenario must appear before script content", rawLine);
        }
        state.scenarioDeclared = true;
        state.scenarioId = scenarioMatcher.group(1).trim();
        state.builder = new VnScenarioBuilder(state.scenarioId);
        continue;
      }

      ensureBuilder(state);

      Matcher varMatcher = VAR_PATTERN.matcher(trimmed);
      if (varMatcher.matches()) {
        state.contentEmitted = true;
        flushPendingVoice(state);
        String key = varMatcher.group(1).trim();
        String value = varMatcher.group(2) != null ? varMatcher.group(2) : varMatcher.group(3);
        if (value == null || value.isBlank()) {
          value = "true";
        }
        state.builder.external("var", "set " + key + " " + value.trim());
        continue;
      }

      Matcher charMatcher = CHARACTER_PATTERN.matcher(trimmed);
      if (charMatcher.matches()) {
        state.contentEmitted = true;
        String id = charMatcher.group(1);
        String name = charMatcher.group(2);
        com.jvn.core.vn.VnCharacter.Builder cb = state.charBuilders.get(id);
        if (cb == null) {
          cb = com.jvn.core.vn.VnCharacter.builder(id);
          state.charBuilders.put(id, cb);
        }
        cb.displayName(name);
        state.builder.addCharacter(id, name);
        continue;
      }

      Matcher bgMatcher = BACKGROUND_PATTERN.matcher(trimmed);
      if (bgMatcher.matches()) {
        state.contentEmitted = true;
        String id = bgMatcher.group(1);
        String path = bgMatcher.group(2).trim();
        state.builder.addBackground(id, path);
        continue;
      }

      Matcher imgMatcher = CHARIMG_PATTERN.matcher(trimmed);
      if (imgMatcher.matches()) {
        state.contentEmitted = true;
        String id = imgMatcher.group(1);
        String expr = imgMatcher.group(2);
        String path = imgMatcher.group(3).trim();
        com.jvn.core.vn.VnCharacter.Builder cb = state.charBuilders.get(id);
        if (cb == null) {
          cb = com.jvn.core.vn.VnCharacter.builder(id);
          state.charBuilders.put(id, cb);
        }
        cb.addExpression(expr, path);
        continue;
      }

      Matcher charLayerMatcher = CHARLAYER_PATTERN.matcher(trimmed);
      if (charLayerMatcher.matches()) {
        state.contentEmitted = true;
        String id = charLayerMatcher.group(1);
        String layerId = charLayerMatcher.group(2);
        String path = charLayerMatcher.group(3).trim();
        if (path.isEmpty()) {
          throw parseError(sourceName, lineNumber, "@charlayer path cannot be empty", rawLine);
        }
        state.charLayers.computeIfAbsent(id, k -> new HashMap<>()).put(layerId, path);
        continue;
      }

      Matcher charPresetMatcher = CHARPRESET_PATTERN.matcher(trimmed);
      if (charPresetMatcher.matches()) {
        state.contentEmitted = true;
        String id = charPresetMatcher.group(1);
        String expr = charPresetMatcher.group(2);
        String spec = charPresetMatcher.group(3).trim();
        if (spec.isEmpty()) {
          throw parseError(sourceName, lineNumber, "@charpreset layer spec cannot be empty", rawLine);
        }
        String resolvedSpec = resolveLayerPresetSpec(state, id, spec, sourceName, lineNumber, rawLine);
        com.jvn.core.vn.VnCharacter.Builder cb = state.charBuilders.get(id);
        if (cb == null) {
          cb = com.jvn.core.vn.VnCharacter.builder(id);
          state.charBuilders.put(id, cb);
        }
        cb.addExpression(expr, resolvedSpec);
        continue;
      }

      Matcher stagePresetMatcher = STAGE_PRESET_PATTERN.matcher(trimmed);
      if (stagePresetMatcher.matches()) {
        state.contentEmitted = true;
        String id = stagePresetMatcher.group(1).trim();
        String presetPath = stripQuotes(stagePresetMatcher.group(2).trim());
        if (presetPath.isEmpty()) {
          throw parseError(sourceName, lineNumber, "@stagepreset path cannot be empty", rawLine);
        }
        if (resolver == null) {
          throw parseError(sourceName, lineNumber, "@stagepreset requires a resolver", rawLine);
        }
        String resolved = normalizeSourceName(resolveIncludePath(sourceName, presetPath));
        try (InputStream presetStream = resolver.open(resolved)) {
          VnStagePreset stagePreset = VnStagePresetLoader.load(id, resolved, presetStream);
          state.builder.addStagePreset(stagePreset);
        }
        continue;
      }

      Matcher positionMatcher = POSITION_PATTERN.matcher(trimmed);
      if (positionMatcher.matches()) {
        String posName = positionMatcher.group(1).trim().toLowerCase();
        String coords = positionMatcher.group(2).trim();
        if (CharacterPosition.predefined(posName) != null) {
          throw parseError(sourceName, lineNumber, "@position name '" + posName + "' conflicts with a predefined position", rawLine);
        }
        String[] parts = coords.split("\\s+");
        try {
          double px = Double.parseDouble(parts[0]);
          double py = parts.length >= 2 ? Double.parseDouble(parts[1]) : -1.0;
          state.customPositions.put(posName, CharacterPosition.named(posName, px, py));
        } catch (NumberFormatException e) {
          throw parseError(sourceName, lineNumber, "@position coordinates must be numbers: " + coords, rawLine);
        }
        continue;
      }

      Matcher labelMatcher = LABEL_PATTERN.matcher(trimmed);
      if (labelMatcher.matches()) {
        state.contentEmitted = true;
        flushChoices(state.builder, state.pendingChoices);
        flushPendingVoice(state);
        String label = labelMatcher.group(1).trim();
        registerLabel(state, label, sourceName, lineNumber, rawLine, false);
        state.builder.label(label);
        continue;
      }

      Matcher legacyLabelMatcher = LABEL_LEGACY_PATTERN.matcher(trimmed);
      if (legacyLabelMatcher.matches()) {
        state.contentEmitted = true;
        flushChoices(state.builder, state.pendingChoices);
        flushPendingVoice(state);
        String label = legacyLabelMatcher.group(1).trim();
        registerLabel(state, label, sourceName, lineNumber, rawLine, false);
        state.builder.label(label);
        continue;
      }

      if (trimmed.startsWith(">")) {
        state.contentEmitted = true;
        flushPendingVoice(state);
        ParsedChoice parsedChoice = parseChoiceSegment(trimmed.substring(1).trim(), sourceName, lineNumber, rawLine);
        if (parsedChoice != null) {
          if (parsedChoice.target != null) {
            addLabelReference(state, parsedChoice.target, sourceName, lineNumber, rawLine, "choice");
          }
          state.pendingChoices.add(parsedChoice.toChoice());
        }
        continue;
      }

      // Inline timeline { ... } block
      if (trimmed.startsWith("timeline") && (trimmed.endsWith("{") || trimmed.equals("timeline"))) {
        state.contentEmitted = true;
        flushChoices(state.builder, state.pendingChoices);
        flushPendingVoice(state);
        ensureBuilder(state);
        StringBuilder block = new StringBuilder();
        int braceDepth = 0;
        // Count opening braces on the first line
        for (char c : trimmed.toCharArray()) { if (c == '{') braceDepth++; }
        if (braceDepth == 0) {
          // Opening brace on next line
          String nextLine;
          boolean foundOpeningBrace = false;
          while ((nextLine = reader.readLine()) != null) {
            lineNumber++;
            String nt = nextLine.trim();
            if (nt.isEmpty() || nt.startsWith("#")) continue;
            if (nt.equals("{")) {
              braceDepth = 1;
              foundOpeningBrace = true;
              break;
            }
            throw parseError(sourceName, lineNumber, "Expected '{' after timeline", nextLine);
          }
          if (!foundOpeningBrace) {
            throw parseError(sourceName, lineNumber, "Expected '{' after timeline", rawLine);
          }
        }
        while (braceDepth > 0 && (line = reader.readLine()) != null) {
          lineNumber++;
          for (char c : line.toCharArray()) {
            if (c == '{') braceDepth++;
            else if (c == '}') braceDepth--;
          }
          if (braceDepth > 0) block.append(line).append('\n');
          else {
            // Remove trailing } from the line content if there's anything before it
            int lastBrace = line.lastIndexOf('}');
            if (lastBrace > 0) block.append(line, 0, lastBrace).append('\n');
          }
        }
        if (braceDepth != 0) {
          throw parseError(sourceName, lineNumber, "Unterminated inline timeline block", rawLine);
        }
        String inlineCode = block.toString();
        state.builder.external("jes_timeline_inline", inlineCode);
        continue;
      }

      Matcher cmdMatcher = COMMAND_PATTERN.matcher(trimmed);
      if (cmdMatcher.matches()) {
        state.contentEmitted = true;
        flushChoices(state.builder, state.pendingChoices);
        parseCommand(cmdMatcher.group(1), sourceName, lineNumber, rawLine, state);
        continue;
      }

      Matcher dialogueMatcher = DIALOGUE_PATTERN.matcher(trimmed);
      if (dialogueMatcher.matches()) {
        state.contentEmitted = true;
        flushChoices(state.builder, state.pendingChoices);
        String speakerId = dialogueMatcher.group(1).trim();
        String text = dialogueMatcher.group(2).trim();
        emitDialogue(state, speakerId, text);
        continue;
      }

      Matcher quotedDialogueMatcher = DIALOGUE_QUOTED_PATTERN.matcher(trimmed);
      if (quotedDialogueMatcher.matches()) {
        state.contentEmitted = true;
        flushChoices(state.builder, state.pendingChoices);
        String speakerId = quotedDialogueMatcher.group(1).trim();
        String text = unescapeQuoted(quotedDialogueMatcher.group(2));
        emitDialogue(state, speakerId, text);
        continue;
      }

      throw parseError(sourceName, lineNumber, "Unrecognized syntax", rawLine);
    }
  }

  private void parseCommand(String commandBody,
                            String sourceName,
                            int lineNumber,
                            String rawLine,
                            ParseState state) throws IOException {
    String body = commandBody == null ? "" : commandBody.trim();
    if (body.isEmpty()) {
      throw parseError(sourceName, lineNumber, "Empty command [] is not allowed", rawLine);
    }

    String[] parts = body.split("\\s+", 2);
    String cmd = parts[0].toLowerCase();
    String arg = parts.length > 1 ? parts[1].trim() : null;

    if ("voice".equals(cmd)) {
      if (state.pendingVoiceTrackId != null && !state.pendingVoiceTrackId.isBlank()) {
        flushPendingVoice(state);
      }
    } else {
      flushPendingVoice(state);
    }

    switch (cmd) {
      case "background":
      case "bg": {
        String bgId = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.background(bgId);
        return;
      }
      case "jump": {
        String label = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        addLabelReference(state, label, sourceName, lineNumber, rawLine, "jump");
        state.builder.jump(label);
        return;
      }
      case "end":
        flushPendingVoice(state);
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.end();
        return;
      case "bgm": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        if (toks.length == 0 || toks[0].isBlank()) {
          throw parseError(sourceName, lineNumber, "[bgm] requires a track id", rawLine);
        }

        String track = toks[0];
        boolean loop = true;
        Float volume = null;

        for (int i = 1; i < toks.length; i++) {
          String option = toks[i].trim();
          if (option.isEmpty()) continue;

          int eq = option.indexOf('=');
          if (eq > 0) {
            String key = option.substring(0, eq).trim().toLowerCase();
            String value = option.substring(eq + 1).trim();
            if (key.isEmpty() || value.isEmpty()) {
              throw parseError(sourceName, lineNumber, "[bgm] malformed option: " + option, rawLine);
            }
            switch (key) {
              case "loop" -> {
                if (!isBooleanToken(value)) {
                  throw parseError(sourceName, lineNumber, "[bgm] loop must be true/false/on/off/1/0", rawLine);
                }
                loop = parseBooleanToken(value);
              }
              case "vol", "volume" -> {
                volume = parseVolumeToken(value, sourceName, lineNumber, rawLine, "[bgm]");
              }
              default -> throw parseError(sourceName, lineNumber, "[bgm] unknown option: " + key, rawLine);
            }
          } else if (i == 1 && isBooleanToken(option)) {
            // Backward-friendly shorthand: [bgm track false]
            loop = parseBooleanToken(option);
          } else {
            throw parseError(sourceName, lineNumber, "[bgm] unrecognized token: " + option, rawLine);
          }
        }

        state.builder.playBgm(track, loop);
        if (volume != null) {
          state.builder.external("settings", "volume bgm " + formatNumber(volume));
        }
        return;
      }
      case "bgm_stop":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.stopBgm();
        return;
      case "bgm_fadeout":
        if (arg != null && !arg.isBlank()) {
          try {
            state.builder.fadeOutBgm(Long.parseLong(arg.trim()));
          } catch (NumberFormatException ex) {
            throw parseError(sourceName, lineNumber, "[bgm_fadeout] expects an integer duration in ms", rawLine);
          }
        } else {
          state.builder.fadeOutBgm();
        }
        return;
      case "bgm_pause":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("audio", "pause");
        return;
      case "bgm_resume":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("audio", "resume");
        return;
      case "bgm_seek": {
        String secondsArg = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        double seconds;
        try {
          seconds = Double.parseDouble(secondsArg);
        } catch (NumberFormatException ex) {
          throw parseError(sourceName, lineNumber, "[bgm_seek] expects a numeric seconds value", rawLine);
        }
        if (seconds < 0) {
          throw parseError(sourceName, lineNumber, "[bgm_seek] seconds must be >= 0", rawLine);
        }
        state.builder.external("audio", "seek " + formatNumber(seconds));
        return;
      }
      case "bgm_crossfade": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        String track = null;
        Long durationMs = null;
        Boolean loop = null;
        int positionalCount = 0;

        for (String rawToken : toks) {
          String token = rawToken == null ? "" : rawToken.trim();
          if (token.isEmpty()) continue;

          if (isNamedOptionToken(token, "bgm_crossfade")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[bgm_crossfade]");
            switch (option.key()) {
              case "track", "id", "file", "path" -> track = option.value();
              case "dur", "duration", "ms" -> {
                durationMs = parseLongValue(option.value(), "[bgm_crossfade]", "duration", sourceName, lineNumber, rawLine);
                if (durationMs < 0) {
                  throw parseError(sourceName, lineNumber, "[bgm_crossfade] duration must be >= 0", rawLine);
                }
              }
              case "loop" -> {
                if (!isBooleanToken(option.value())) {
                  throw parseError(sourceName, lineNumber, "[bgm_crossfade] loop must be true/false/on/off/1/0", rawLine);
                }
                loop = parseBooleanToken(option.value());
              }
              default -> throw parseError(sourceName, lineNumber, "[bgm_crossfade] unknown option: " + option.key(), rawLine);
            }
            continue;
          }

          positionalCount++;
          if (track == null) {
            track = token;
            continue;
          }
          if (durationMs == null) {
            durationMs = parseLongValue(token, "[bgm_crossfade]", "duration", sourceName, lineNumber, rawLine);
            if (durationMs < 0) {
              throw parseError(sourceName, lineNumber, "[bgm_crossfade] duration must be >= 0", rawLine);
            }
            continue;
          }
          if (loop == null && isBooleanToken(token)) {
            loop = parseBooleanToken(token);
            continue;
          }
          throw parseError(sourceName, lineNumber, "[bgm_crossfade] too many arguments", rawLine);
        }

        if (track == null || track.isBlank() || durationMs == null) {
          throw parseError(sourceName, lineNumber, "[bgm_crossfade] expects: [bgm_crossfade <trackId> <ms> [loop]] or named options", rawLine);
        }

        String normalized = "crossfade " + quoteTokenIfNeeded(track) + " " + durationMs;
        if (loop != null) normalized += " " + loop;
        state.builder.external("audio", normalized);
        return;
      }
      case "sfx": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        if (toks.length == 0) {
          throw parseError(sourceName, lineNumber, "[sfx] requires a track id", rawLine);
        }
        String track = null;
        for (String rawToken : toks) {
          String token = rawToken == null ? "" : rawToken.trim();
          if (token.isEmpty()) continue;
          if (isNamedOptionToken(token, "sfx")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[sfx]");
            switch (option.key()) {
              case "track", "id", "file", "path" -> track = option.value();
              default -> throw parseError(sourceName, lineNumber, "[sfx] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (track == null) {
            track = token;
            continue;
          }
          throw parseError(sourceName, lineNumber, "[sfx] unexpected token: " + token, rawLine);
        }
        if (track == null || track.isBlank()) {
          throw parseError(sourceName, lineNumber, "[sfx] requires a track id", rawLine);
        }
        state.builder.playSfx(track);
        return;
      }
      case "sfx_stop":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("audio", "sfx_stop");
        return;
      case "voice": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        if (toks.length == 0) {
          throw parseError(sourceName, lineNumber, "[voice] requires a track id", rawLine);
        }
        String track = null;
        for (String rawToken : toks) {
          String token = rawToken == null ? "" : rawToken.trim();
          if (token.isEmpty()) continue;
          if (isNamedOptionToken(token, "voice")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[voice]");
            switch (option.key()) {
              case "track", "id", "file", "path" -> track = option.value();
              default -> throw parseError(sourceName, lineNumber, "[voice] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (track == null) {
            track = token;
            continue;
          }
          throw parseError(sourceName, lineNumber, "[voice] unexpected token: " + token, rawLine);
        }
        if (track == null || track.isBlank()) {
          throw parseError(sourceName, lineNumber, "[voice] requires a track id", rawLine);
        }
        state.pendingVoiceTrackId = track;
        return;
      }
      case "voice_stop":
        flushPendingVoice(state);
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("audio", "voice_stop");
        return;
      case "audio_stop_all":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("audio", "stop_all");
        return;
      case "audio_pause_all":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("audio", "pause_all");
        return;
      case "audio_resume_all":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("audio", "resume_all");
        return;
      case "audio": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("audio", payload);
        return;
      }
      case "volume": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        String channel = null;
        Float level = null;
        for (String rawToken : toks) {
          String token = rawToken == null ? "" : rawToken.trim();
          if (token.isEmpty()) continue;
          if (isNamedOptionToken(token, "volume")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[volume]");
            switch (option.key()) {
              case "channel", "target", "type" -> channel = option.value();
              case "level", "value", "vol", "volume" ->
                  level = parseUnitRangeToken(option.value(), sourceName, lineNumber, rawLine, "[volume]", "level");
              default -> throw parseError(sourceName, lineNumber, "[volume] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (channel == null) {
            channel = token;
            continue;
          }
          if (level == null) {
            level = parseUnitRangeToken(token, sourceName, lineNumber, rawLine, "[volume]", "level");
            continue;
          }
          throw parseError(sourceName, lineNumber, "[volume] unexpected token: " + token, rawLine);
        }
        if (channel == null || channel.isBlank() || level == null) {
          throw parseError(sourceName, lineNumber, "[volume] expects: [volume <channel> <level>] or named options", rawLine);
        }
        state.builder.external("settings", "volume " + channel + " " + formatNumber(level));
        return;
      }
      case "textspeed": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        Integer value = null;
        for (String rawToken : toks) {
          String token = rawToken == null ? "" : rawToken.trim();
          if (token.isEmpty()) continue;
          if (isNamedOptionToken(token, "textspeed")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[textspeed]");
            switch (option.key()) {
              case "value", "speed", "chars" -> value = parseIntegerValue(option.value(), "[textspeed]", "value", sourceName, lineNumber, rawLine);
              default -> throw parseError(sourceName, lineNumber, "[textspeed] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (value == null) {
            value = parseIntegerValue(token, "[textspeed]", "value", sourceName, lineNumber, rawLine);
            continue;
          }
          throw parseError(sourceName, lineNumber, "[textspeed] unexpected token: " + token, rawLine);
        }
        if (value == null) {
          throw parseError(sourceName, lineNumber, "[textspeed] requires a value", rawLine);
        }
        state.builder.external("settings", "textspeed " + value);
        return;
      }
      case "autodelay": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        Long value = null;
        for (String rawToken : toks) {
          String token = rawToken == null ? "" : rawToken.trim();
          if (token.isEmpty()) continue;
          if (isNamedOptionToken(token, "autodelay")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[autodelay]");
            switch (option.key()) {
              case "value", "delay", "ms", "duration" -> value = parseLongValue(option.value(), "[autodelay]", "value", sourceName, lineNumber, rawLine);
              default -> throw parseError(sourceName, lineNumber, "[autodelay] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (value == null) {
            value = parseLongValue(token, "[autodelay]", "value", sourceName, lineNumber, rawLine);
            continue;
          }
          throw parseError(sourceName, lineNumber, "[autodelay] unexpected token: " + token, rawLine);
        }
        if (value == null) {
          throw parseError(sourceName, lineNumber, "[autodelay] requires a value", rawLine);
        }
        state.builder.external("settings", "autodelay " + value);
        return;
      }
      case "hud": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("hud", payload);
        return;
      }
      case "save":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("save", "");
        return;
      case "quickload":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("save", "quickload");
        return;
      case "skip":
        state.builder.external("mode", "skip " + (arg == null ? "" : arg));
        return;
      case "auto":
        state.builder.external("mode", "auto " + (arg == null ? "" : arg));
        return;
      case "ui":
        state.builder.external("ui", arg == null ? "" : arg);
        return;
      case "visualizer":
      case "viz": {
        String payload = (arg == null || arg.isBlank()) ? "toggle" : arg.trim();
        state.builder.external("ui", "visualizer " + payload);
        return;
      }
      case "history":
        state.builder.external("history", arg == null ? "" : arg);
        return;
      case "screen":
        state.builder.external("screen", arg == null ? "" : arg);
        return;
      case "persistent": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("persistent", payload);
        return;
      }
      case "jes_push": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("jes", "push " + payload);
        return;
      }
      case "jes_replace": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("jes", "replace " + payload);
        return;
      }
      case "jes_pop":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("jes", "pop");
        return;
      case "jes_call": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("jes", "call " + payload);
        return;
      }
      case "wait": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        Long waitMs = null;
        for (String rawToken : toks) {
          String token = rawToken == null ? "" : rawToken.trim();
          if (token.isEmpty()) continue;
          if (isNamedOptionToken(token, "wait")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[wait]");
            switch (option.key()) {
              case "ms", "dur", "duration", "time", "value" -> waitMs = parseLongValue(option.value(), "[wait]", "duration", sourceName, lineNumber, rawLine);
              default -> throw parseError(sourceName, lineNumber, "[wait] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (waitMs == null) {
            waitMs = parseLongValue(token, "[wait]", "duration", sourceName, lineNumber, rawLine);
            continue;
          }
          throw parseError(sourceName, lineNumber, "[wait] unexpected token: " + token, rawLine);
        }
        if (waitMs == null) {
          throw parseError(sourceName, lineNumber, "[wait] expects an integer duration in ms", rawLine);
        }
        state.builder.waitMs(waitMs);
        return;
      }
      case "show": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        if (toks.length < 2) {
          throw parseError(sourceName, lineNumber, "[show] expects: [show <charId> <pos|at x,y[,z]> [expression] [layer]]", rawLine);
        }
        String charId = toks[0];
        CharacterPosition pos = null;
        Integer layerOrder = null;
        String expr = "neutral";
        boolean exprSet = false;
        for (int i = 1; i < toks.length; i++) {
          String token = toks[i].trim();
          if (token.isEmpty()) continue;
          if ("at".equalsIgnoreCase(token) && i + 1 < toks.length && !isNamedOptionToken(toks[i + 1], "show")) {
            InlinePosition ip = parseAtPosition(toks[++i], sourceName, lineNumber, rawLine);
            pos = ip.position();
            if (layerOrder == null) layerOrder = ip.layerOrder();
            continue;
          }
          if (isNamedOptionToken(token, "show")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[show]");
            switch (option.key()) {
              case "pos", "position" -> pos = parsePosition(option.value(), sourceName, lineNumber, rawLine, state);
              case "at", "coord", "coords", "xy" -> {
                InlinePosition ip = parseAtPosition(option.value(), sourceName, lineNumber, rawLine);
                pos = ip.position();
                if (layerOrder == null) layerOrder = ip.layerOrder();
              }
              case "expr", "expression", "preset" -> {
                expr = resolveInlineExpressionToken(state, charId, option.value(), sourceName, lineNumber, rawLine);
                exprSet = true;
              }
              case "layer", "z", "zorder" -> layerOrder = parseIntegerValue(option.value(), "[show]", "layer", sourceName, lineNumber, rawLine);
              default -> throw parseError(sourceName, lineNumber, "[show] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (pos == null) {
            pos = parsePosition(token, sourceName, lineNumber, rawLine, state);
            continue;
          }
          if (isIntegerToken(token)) {
            layerOrder = Integer.parseInt(token);
            continue;
          }
          if (!exprSet) {
            expr = resolveInlineExpressionToken(state, charId, token, sourceName, lineNumber, rawLine);
            exprSet = true;
            continue;
          }
          throw parseError(sourceName, lineNumber, "[show] unexpected token: " + token, rawLine);
        }
        if (pos == null) {
          throw parseError(sourceName, lineNumber, "[show] requires a position via positional arg, pos=..., or at=...", rawLine);
        }
        state.builder.show(charId, expr, pos, layerOrder);
        return;
      }
      case "hide": {
        String charId = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.hide(charId);
        return;
      }
      case "move": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        if (toks.length < 2) {
          throw parseError(sourceName, lineNumber, "[move] expects: [move <charId> <pos|at x,y> [expression] [easing] [durationMs]]", rawLine);
        }
        String moveCharId = toks[0];
        CharacterPosition movePos = null;
        String moveExpr = null;
        Easing.Type moveEasing = null;
        long moveDur = 0;
        for (int ti = 1; ti < toks.length; ti++) {
          String tok = toks[ti].trim();
          if (tok.isEmpty()) continue;
          if ("at".equalsIgnoreCase(tok) && ti + 1 < toks.length && !isNamedOptionToken(toks[ti + 1], "move")) {
            InlinePosition mip = parseAtPosition(toks[++ti], sourceName, lineNumber, rawLine);
            movePos = mip.position();
            continue;
          }
          if (isNamedOptionToken(tok, "move")) {
            KeyValueOption option = parseKeyValueOption(tok, sourceName, lineNumber, rawLine, "[move]");
            switch (option.key()) {
              case "pos", "position" -> movePos = parsePosition(option.value(), sourceName, lineNumber, rawLine, state);
              case "at", "coord", "coords", "xy" -> movePos = parseAtPosition(option.value(), sourceName, lineNumber, rawLine).position();
              case "expr", "expression", "preset" ->
                  moveExpr = resolveInlineExpressionToken(state, moveCharId, option.value(), sourceName, lineNumber, rawLine);
              case "ease", "easing" -> {
                moveEasing = parseEasingToken(option.value());
                if (moveEasing == null) {
                  throw parseError(sourceName, lineNumber, "[move] unknown easing: " + option.value(), rawLine);
                }
              }
              case "dur", "duration", "ms" -> {
                moveDur = parseLongValue(option.value(), "[move]", "duration", sourceName, lineNumber, rawLine);
                if (moveDur < 0) throw parseError(sourceName, lineNumber, "[move] duration must be >= 0", rawLine);
              }
              default -> throw parseError(sourceName, lineNumber, "[move] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (movePos == null) {
            movePos = parsePosition(tok, sourceName, lineNumber, rawLine, state);
            continue;
          }
          if (isIntegerToken(tok)) {
            moveDur = Long.parseLong(tok);
            if (moveDur < 0) throw parseError(sourceName, lineNumber, "[move] duration must be >= 0", rawLine);
            continue;
          }
          Easing.Type parsed = parseEasingToken(tok);
          if (parsed != null) {
            moveEasing = parsed;
            continue;
          }
          if (moveExpr == null) {
            moveExpr = resolveInlineExpressionToken(state, moveCharId, tok, sourceName, lineNumber, rawLine);
            continue;
          }
          throw parseError(sourceName, lineNumber, "[move] unexpected token: " + tok, rawLine);
        }
        if (movePos == null) {
          throw parseError(sourceName, lineNumber, "[move] requires a destination via positional arg, pos=..., or at=...", rawLine);
        }
        state.builder.move(moveCharId, movePos, moveExpr, moveEasing, moveDur);
        return;
      }
      case "stage": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        String presetId = null;
        for (String rawToken : toks) {
          String token = rawToken == null ? "" : rawToken.trim();
          if (token.isEmpty()) continue;
          if (isNamedOptionToken(token, "stage")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[stage]");
            switch (option.key()) {
              case "preset", "id", "name", "mode" -> presetId = option.value();
              default -> throw parseError(sourceName, lineNumber, "[stage] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (presetId == null) {
            presetId = token;
            continue;
          }
          throw parseError(sourceName, lineNumber, "[stage] unexpected token: " + token, rawLine);
        }
        if (presetId == null || presetId.isBlank()) {
          throw parseError(sourceName, lineNumber, "[stage] requires a preset id", rawLine);
        }
        state.builder.external("stage", presetId);
        return;
      }
      case "transition": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        VnTransition.TransitionType type = null;
        long dur = 500;
        String bg = null;
        for (String tok : toks) {
          String token = tok.trim();
          if (token.isEmpty()) continue;
          if (isNamedOptionToken(token, "transition")) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[transition]");
            switch (option.key()) {
              case "type", "kind", "style" -> {
                type = parseTransitionType(option.value());
                if (type == null) throw parseError(sourceName, lineNumber, "Unknown transition type: " + option.value(), rawLine);
              }
              case "dur", "duration", "ms" -> {
                dur = parseLongValue(option.value(), "[transition]", "duration", sourceName, lineNumber, rawLine);
                if (dur < 0) throw parseError(sourceName, lineNumber, "[transition] duration must be >= 0", rawLine);
              }
              case "bg", "background" -> bg = option.value();
              default -> throw parseError(sourceName, lineNumber, "[transition] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (type == null) {
            type = parseTransitionType(token);
            if (type == null) throw parseError(sourceName, lineNumber, "Unknown transition type: " + token, rawLine);
            continue;
          }
          if (isIntegerToken(token)) {
            dur = Long.parseLong(token);
            if (dur < 0) throw parseError(sourceName, lineNumber, "[transition] duration must be >= 0", rawLine);
            continue;
          }
          if (bg == null) {
            bg = token;
            continue;
          }
          throw parseError(sourceName, lineNumber, "[transition] unexpected token: " + token, rawLine);
        }
        if (type == null) {
          throw parseError(sourceName, lineNumber, "[transition] requires a type via positional arg or type=...", rawLine);
        }
        state.builder.transition(type, dur, bg);
        return;
      }
      case "particles":
      case "particle":
      case "weather": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = VnArgTokenizer.tokenizeToArray(payload);
        if (toks.length == 0) {
          throw parseError(sourceName, lineNumber, "[particles] requires a preset or preset=...", rawLine);
        }
        VnParticleCommand.Preset preset = null;
        float intensity = 0.5f;
        int layer = 100;
        boolean intensitySet = false;
        boolean layerSet = false;
        for (String rawToken : toks) {
          String token = rawToken == null ? "" : rawToken.trim();
          if (token.isEmpty()) continue;
          if (isNamedOptionToken(token, cmd)) {
            KeyValueOption option = parseKeyValueOption(token, sourceName, lineNumber, rawLine, "[particles]");
            switch (option.key()) {
              case "preset", "type", "kind", "mode", "weather" -> preset = VnParticleCommand.Preset.parse(option.value());
              case "intensity", "amount", "strength", "level" -> {
                intensity = parseUnitRangeToken(option.value(), sourceName, lineNumber, rawLine, "[particles]", "intensity");
                intensitySet = true;
              }
              case "layer", "z", "zorder" -> {
                layer = parseIntegerValue(option.value(), "[particles]", "layer", sourceName, lineNumber, rawLine);
                layerSet = true;
              }
              default -> throw parseError(sourceName, lineNumber, "[particles] unknown option: " + option.key(), rawLine);
            }
            continue;
          }
          if (preset == null) {
            preset = VnParticleCommand.Preset.parse(token);
            continue;
          }
          if (!intensitySet) {
            intensity = parseUnitRangeToken(token, sourceName, lineNumber, rawLine, "[particles]", "intensity");
            intensitySet = true;
            continue;
          }
          if (!layerSet && isIntegerToken(token)) {
            layer = Integer.parseInt(token);
            layerSet = true;
            continue;
          }
          throw parseError(sourceName, lineNumber, "[particles] unexpected token: " + token, rawLine);
        }
        if (preset == null) {
          throw parseError(sourceName, lineNumber, "[particles] requires a preset via positional arg or preset=...", rawLine);
        }
        if (preset == VnParticleCommand.Preset.NONE) {
          state.builder.particles(VnParticleCommand.stop());
        } else {
          state.builder.particles(VnParticleCommand.start(preset, intensity, layer));
        }
        return;
      }
      case "menu":
        state.builder.external("menu", arg == null ? "" : arg);
        return;
      case "settings":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("menu", "settings");
        return;
      case "mainmenu":
        state.builder.external("menu", "main" + (arg == null || arg.isBlank() ? "" : (" " + arg)));
        return;
      case "load": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("vns", "replace " + payload);
        return;
      }
      case "goto": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("vns", "goto " + payload);
        return;
      }
      case "set": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("var", "set " + payload);
        return;
      }
      case "inc": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("var", "inc " + payload);
        return;
      }
      case "dec": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("var", "dec " + payload);
        return;
      }
      case "flag": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("var", "flag " + payload);
        return;
      }
      case "unflag": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("var", "unflag " + payload);
        return;
      }
      case "clear": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("var", "clear " + payload);
        return;
      }
      case "if": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        IfGoto ifGoto = parseIfGoto(payload);
        if (ifGoto != null) {
          validateConditionExpression(ifGoto.expression, sourceName, lineNumber, rawLine);
          addLabelReference(state, ifGoto.label, sourceName, lineNumber, rawLine, "if-goto");
          state.builder.external("cond", "if " + ifGoto.expression + " goto " + ifGoto.label);
        } else {
          startConditionalBlock(state, payload, sourceName, lineNumber, rawLine);
        }
        return;
      }
      case "elif": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        continueConditionalBlock(state, payload, sourceName, lineNumber, rawLine);
        return;
      }
      case "else":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        enterElseBlock(state, sourceName, lineNumber, rawLine);
        return;
      case "endif":
      case "/if":
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        closeConditionalBlock(state, sourceName, lineNumber, rawLine);
        return;
      case "call": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = payload.split("\\s+", 2);
        String provider = toks[0].trim();
        if (provider.isBlank()) {
          throw parseError(sourceName, lineNumber, "[call] requires a provider", rawLine);
        }
        String providerPayload = toks.length > 1 ? toks[1] : "";
        state.builder.external(provider, providerPayload);
        return;
      }
      case "gosub": {
        // Subroutine call - pushes return address and jumps to label
        String label = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        addLabelReference(state, label, sourceName, lineNumber, rawLine, "gosub");
        state.builder.call(label);
        return;
      }
      case "return":
        // Return from subroutine - pops return address from call stack
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.returnFromCall();
        return;
      case "char":
      case "character": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external(
            "char",
            normalizeCharacterInteropPayload(state, payload, sourceName, lineNumber, rawLine));
        return;
      }
      case "choice": {
        flushPendingVoice(state);
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        List<Choice> inlineChoices = parseInlineChoices(payload, sourceName, lineNumber, rawLine, state);
        if (inlineChoices.isEmpty()) {
          throw parseError(sourceName, lineNumber, "[choice] must contain at least one option", rawLine);
        }
        state.builder.choiceNodes(inlineChoices);
        return;
      }
      case "phone":
        state.builder.external("phone", arg == null ? "" : arg);
        return;
      case "jes":
        state.builder.external("jes", arg == null ? "" : arg);
        return;
      case "java":
        state.builder.external("java", arg == null ? "" : arg);
        return;
      default:
        throw parseError(sourceName, lineNumber, "Unknown command [" + cmd + "]", rawLine);
    }
  }

  private void startConditionalBlock(ParseState state,
                                     String expression,
                                     String sourceName,
                                     int lineNumber,
                                     String rawLine) throws IOException {
    validateConditionExpression(expression, sourceName, lineNumber, rawLine);

    String thenLabel = nextSyntheticLabel(state, "if_then");
    String falseLabel = nextSyntheticLabel(state, "if_false");
    String endLabel = nextSyntheticLabel(state, "if_end");

    state.builder.external("cond", "if " + expression + " goto " + thenLabel);
    state.builder.jump(falseLabel);

    registerLabel(state, thenLabel, sourceName, lineNumber, rawLine, true);
    state.builder.label(thenLabel);

    state.conditionalBlocks.push(new ConditionalBlock(falseLabel, endLabel, sourceName, lineNumber));
  }

  private void continueConditionalBlock(ParseState state,
                                        String expression,
                                        String sourceName,
                                        int lineNumber,
                                        String rawLine) throws IOException {
    ConditionalBlock block = state.conditionalBlocks.peek();
    if (block == null) {
      throw parseError(sourceName, lineNumber, "[elif] used without matching [if]", rawLine);
    }
    if (block.elseSeen) {
      throw parseError(sourceName, lineNumber, "[elif] cannot appear after [else]", rawLine);
    }

    validateConditionExpression(expression, sourceName, lineNumber, rawLine);

    state.builder.jump(block.endLabel);
    registerLabel(state, block.falseLabel, sourceName, lineNumber, rawLine, true);
    state.builder.label(block.falseLabel);

    String thenLabel = nextSyntheticLabel(state, "elif_then");
    String nextFalseLabel = nextSyntheticLabel(state, "elif_false");

    state.builder.external("cond", "if " + expression + " goto " + thenLabel);
    state.builder.jump(nextFalseLabel);

    registerLabel(state, thenLabel, sourceName, lineNumber, rawLine, true);
    state.builder.label(thenLabel);

    block.falseLabel = nextFalseLabel;
  }

  private void enterElseBlock(ParseState state,
                              String sourceName,
                              int lineNumber,
                              String rawLine) throws IOException {
    ConditionalBlock block = state.conditionalBlocks.peek();
    if (block == null) {
      throw parseError(sourceName, lineNumber, "[else] used without matching [if]", rawLine);
    }
    if (block.elseSeen) {
      throw parseError(sourceName, lineNumber, "Duplicate [else] in the same [if] block", rawLine);
    }

    state.builder.jump(block.endLabel);
    registerLabel(state, block.falseLabel, sourceName, lineNumber, rawLine, true);
    state.builder.label(block.falseLabel);
    block.falseLabel = null;
    block.elseSeen = true;
  }

  private void closeConditionalBlock(ParseState state,
                                     String sourceName,
                                     int lineNumber,
                                     String rawLine) throws IOException {
    ConditionalBlock block = state.conditionalBlocks.poll();
    if (block == null) {
      throw parseError(sourceName, lineNumber, "[endif] used without matching [if]", rawLine);
    }

    if (!block.elseSeen && block.falseLabel != null) {
      registerLabel(state, block.falseLabel, sourceName, lineNumber, rawLine, true);
      state.builder.label(block.falseLabel);
    }

    registerLabel(state, block.endLabel, sourceName, lineNumber, rawLine, true);
    state.builder.label(block.endLabel);
  }

  private List<Choice> parseInlineChoices(String arg,
                                          String sourceName,
                                          int lineNumber,
                                          String rawLine,
                                          ParseState state) throws IOException {
    List<Choice> out = new ArrayList<>();
    String[] rawChoices = arg.split("\\|");
    for (String raw : rawChoices) {
      if (raw == null || raw.isBlank()) continue;
      ParsedChoice parsed = parseChoiceSegment(raw.trim(), sourceName, lineNumber, rawLine);
      if (parsed == null) continue;
      if (parsed.target != null) {
        addLabelReference(state, parsed.target, sourceName, lineNumber, rawLine, "inline-choice");
      }
      out.add(parsed.toChoice());
    }
    return out;
  }

  private ParsedChoice parseChoiceSegment(String segment,
                                          String sourceName,
                                          int lineNumber,
                                          String rawLine) throws IOException {
    if (segment == null) return null;
    String work = segment.trim();
    if (work.isEmpty()) return null;

    String condition = null;
    Matcher condMatcher = CHOICE_CONDITION_SUFFIX_PATTERN.matcher(work);
    if (condMatcher.matches()) {
      work = condMatcher.group(1).trim();
      condition = condMatcher.group(2).trim();
      validateConditionExpression(condition, sourceName, lineNumber, rawLine);
    }

    String text = work;
    String target = null;
    int arrow = work.indexOf("->");
    if (arrow >= 0) {
      text = work.substring(0, arrow).trim();
      target = work.substring(arrow + 2).trim();
      if (target.isEmpty()) {
        throw parseError(sourceName, lineNumber, "Choice target label is empty", rawLine);
      }
    }

    if (text.isEmpty()) {
      throw parseError(sourceName, lineNumber, "Choice text is empty", rawLine);
    }

    return new ParsedChoice(text, target, condition);
  }

  private String resolveDisplayName(ParseState state, String speakerId) {
    String displayName = speakerId;
    com.jvn.core.vn.VnCharacter.Builder cb = state.charBuilders.get(speakerId);
    if (cb != null) {
      String dn = cb.getDisplayName();
      if (dn != null) displayName = dn;
    }
    return displayName;
  }

  private void emitDialogue(ParseState state, String speakerId, String text) {
    String displayName = resolveDisplayName(state, speakerId);
    if (state.pendingVoiceTrackId != null && !state.pendingVoiceTrackId.isBlank()) {
      state.builder.dialogue(displayName, text, state.pendingVoiceTrackId);
      state.pendingVoiceTrackId = null;
      return;
    }
    state.builder.dialogue(displayName, text);
  }

  private void flushChoices(VnScenarioBuilder builder, List<Choice> choices) {
    if (!choices.isEmpty()) {
      builder.choiceNodes(new ArrayList<>(choices));
      choices.clear();
    }
  }

  private void flushPendingVoice(ParseState state) {
    if (state.pendingVoiceTrackId == null || state.pendingVoiceTrackId.isBlank()) return;
    ensureBuilder(state);
    state.builder.playVoice(state.pendingVoiceTrackId);
    state.pendingVoiceTrackId = null;
  }

  private boolean isNamedOptionToken(String token, String commandName) {
    if (token == null || token.isBlank() || commandName == null) return false;
    int eq = token.indexOf('=');
    int colon = token.indexOf(':');
    int sep;
    if (eq > 0 && colon > 0) sep = Math.min(eq, colon);
    else sep = Math.max(eq, colon);
    if (sep <= 0) return false;
    String key = token.substring(0, sep).trim().toLowerCase();
    return switch (commandName) {
      case "show" -> switch (key) {
        case "pos", "position", "at", "coord", "coords", "xy",
             "expr", "expression", "preset", "layer", "z", "zorder" -> true;
        default -> false;
      };
      case "move" -> switch (key) {
        case "pos", "position", "at", "coord", "coords", "xy",
             "expr", "expression", "preset", "ease", "easing", "dur", "duration", "ms" -> true;
        default -> false;
      };
      case "transition" -> switch (key) {
        case "type", "kind", "style", "dur", "duration", "ms", "bg", "background" -> true;
        default -> false;
      };
      case "bgm_crossfade" -> switch (key) {
        case "track", "id", "file", "path", "dur", "duration", "ms", "loop" -> true;
        default -> false;
      };
      case "volume" -> switch (key) {
        case "channel", "target", "type", "level", "value", "vol", "volume" -> true;
        default -> false;
      };
      case "textspeed" -> switch (key) {
        case "value", "speed", "chars" -> true;
        default -> false;
      };
      case "autodelay" -> switch (key) {
        case "value", "delay", "ms", "duration" -> true;
        default -> false;
      };
      case "wait" -> switch (key) {
        case "ms", "dur", "duration", "time", "value" -> true;
        default -> false;
      };
      case "stage" -> switch (key) {
        case "preset", "id", "name", "mode" -> true;
        default -> false;
      };
      case "sfx", "voice" -> switch (key) {
        case "track", "id", "file", "path" -> true;
        default -> false;
      };
      case "particles", "particle", "weather" -> switch (key) {
        case "preset", "type", "kind", "mode", "weather",
             "intensity", "amount", "strength", "level",
             "layer", "z", "zorder" -> true;
        default -> false;
      };
      default -> false;
    };
  }

  private int parseIntegerValue(String token,
                                String commandName,
                                String fieldName,
                                String sourceName,
                                int lineNumber,
                                String rawLine) throws IOException {
    try {
      return Integer.parseInt(token.trim());
    } catch (NumberFormatException ex) {
      throw parseError(sourceName, lineNumber, commandName + " " + fieldName + " must be an integer", rawLine);
    }
  }

  private long parseLongValue(String token,
                              String commandName,
                              String fieldName,
                              String sourceName,
                              int lineNumber,
                              String rawLine) throws IOException {
    try {
      return Long.parseLong(token.trim());
    } catch (NumberFormatException ex) {
      throw parseError(sourceName, lineNumber, commandName + " " + fieldName + " must be an integer", rawLine);
    }
  }

  private String resolveLayerPresetSpec(ParseState state,
                                        String characterId,
                                        String spec,
                                        String sourceName,
                                        int lineNumber,
                                        String rawLine) throws IOException {
    String[] tokens = spec.split("\\|");
    List<String> resolved = new ArrayList<>();
    for (String token : tokens) {
      if (token == null) continue;
      String part = token.trim();
      if (part.isEmpty()) continue;
      if (part.startsWith("$")) {
        String rawRef = part.substring(1).trim();
        if (rawRef.isEmpty()) {
          throw parseError(sourceName, lineNumber, "@charpreset contains empty $layer reference", rawLine);
        }
        String path = LayeredCharacterResolver.resolveLayerPath(state.charLayers, characterId, rawRef);
        LayeredCharacterResolver.CharacterRef layerRef = LayeredCharacterResolver.parseReference(rawRef, characterId);
        if (path == null || path.isBlank()) {
          throw parseError(
              sourceName,
              lineNumber,
              "Unknown @charlayer reference '$" + rawRef + "' for character '" + layerRef.characterId() + "'",
              rawLine
          );
        }
        resolved.add(path);
      } else if (part.startsWith("@")) {
        String rawPresetRef = part.substring(1).trim();
        if (rawPresetRef.isEmpty()) {
          throw parseError(sourceName, lineNumber, "@charpreset contains empty @preset reference", rawLine);
        }
        LayeredCharacterResolver.CharacterRef presetRef = LayeredCharacterResolver.parseReference(rawPresetRef, characterId);
        com.jvn.core.vn.VnCharacter.Builder presetCharacter = state.charBuilders.get(presetRef.characterId());
        String presetPath = presetCharacter == null ? null : presetCharacter.getExpressionPath(presetRef.localId());
        if (presetPath == null || presetPath.isBlank()) {
          throw parseError(
              sourceName,
              lineNumber,
              "Unknown @charpreset reference '@" + rawPresetRef + "' for character '" + presetRef.characterId() + "'",
              rawLine
          );
        }
        resolved.addAll(splitResolvedLayerSpec(presetPath));
      } else {
        resolved.add(part);
      }
    }
    if (resolved.isEmpty()) {
      throw parseError(sourceName, lineNumber, "@charpreset produced no layers", rawLine);
    }
    return String.join(" | ", resolved);
  }

  private String normalizeCharacterInteropPayload(ParseState state,
                                                  String payload,
                                                  String sourceName,
                                                  int lineNumber,
                                                  String rawLine) throws IOException {
    String[] toks = VnArgTokenizer.tokenizeToArray(payload);
    if (toks.length < 2) return payload;

    String characterId = toks[0].trim();
    String cmd = toks[1].trim().toLowerCase();
    if (characterId.isEmpty() || cmd.isEmpty()) return payload;

    switch (cmd) {
      case "move": {
        int nextIdx = 3;
        if (toks.length >= 4 && "at".equalsIgnoreCase(toks[2])) {
          nextIdx = 4;
        }
        for (int ti = nextIdx; ti < toks.length; ti++) {
          String tok = toks[ti].trim();
          if (tok.isEmpty()) continue;
          if (isIntegerToken(tok)) continue;
          Easing.Type easing = parseEasingToken(tok);
          if (easing != null) continue;
          toks[ti] = resolveInlineExpressionToken(state, characterId, tok, sourceName, lineNumber, rawLine);
          break;
        }
        return joinNormalizedTokens(toks);
      }
      case "show": {
        int exprIdx = 3;
        if (toks.length >= 4 && "at".equalsIgnoreCase(toks[2])) {
          exprIdx = 4;
        }
        if (exprIdx < toks.length) {
          toks[exprIdx] = resolveInlineExpressionToken(state, characterId, toks[exprIdx], sourceName, lineNumber, rawLine);
        }
        return joinNormalizedTokens(toks);
      }
      case "expression":
      case "expr": {
        if (toks.length >= 3) {
          toks[2] = resolveInlineExpressionToken(state, characterId, toks[2], sourceName, lineNumber, rawLine);
        }
        return joinNormalizedTokens(toks);
      }
      default:
        return payload;
    }
  }

  private String joinNormalizedTokens(String[] tokens) {
    StringBuilder sb = new StringBuilder();
    for (String token : tokens) {
      if (token == null || token.isEmpty()) continue;
      if (sb.length() > 0) sb.append(' ');
      sb.append(quoteTokenIfNeeded(token));
    }
    return sb.toString();
  }

  private String resolveInlineExpressionToken(ParseState state,
                                              String characterId,
                                              String rawToken,
                                              String sourceName,
                                              int lineNumber,
                                              String rawLine) throws IOException {
    String token = rawToken == null ? "" : rawToken.trim();
    if (token.isEmpty()) return token;
    if (!token.startsWith("@") && token.indexOf('$') < 0 && token.indexOf('+') < 0) {
      return token;
    }

    if (token.startsWith("@") && token.indexOf('+') < 0 && token.indexOf('$') < 0) {
      String presetName = token.substring(1).trim();
      if (presetName.isEmpty()) {
        throw parseError(sourceName, lineNumber, "Inline preset reference cannot be empty", rawLine);
      }
      return presetName;
    }

    List<String> resolvedParts = new ArrayList<>();
    for (String rawPart : token.split("\\+")) {
      String part = rawPart == null ? "" : rawPart.trim();
      if (part.isEmpty()) {
        throw parseError(sourceName, lineNumber, "Inline composite expression contains an empty segment", rawLine);
      }
      if (part.startsWith("@")) {
        String presetName = part.substring(1).trim();
        if (presetName.isEmpty()) {
          throw parseError(sourceName, lineNumber, "Inline preset reference cannot be empty", rawLine);
        }
        String presetPath = getOrCreateCharacterBuilder(state, characterId).getExpressionPath(presetName);
        if (presetPath == null || presetPath.isBlank()) {
          throw parseError(
              sourceName,
              lineNumber,
              "Unknown character preset '@" + presetName + "' for character '" + characterId + "'",
              rawLine);
        }
        resolvedParts.addAll(splitResolvedLayerSpec(presetPath));
        continue;
      }
      if (part.startsWith("$")) {
        resolvedParts.add(resolveLayerReferencePath(state, characterId, part.substring(1), sourceName, lineNumber, rawLine));
        continue;
      }
      throw parseError(
          sourceName,
          lineNumber,
          "Inline composite segments must use @preset or $layer syntax: " + part,
          rawLine);
    }

    if (resolvedParts.isEmpty()) {
      throw parseError(sourceName, lineNumber, "Inline composite expression produced no layers", rawLine);
    }

    String resolvedSpec = String.join(" | ", resolvedParts);
    String cacheKey = characterId + "|" + resolvedSpec;
    String existing = state.inlineCompositeExpressions.get(cacheKey);
    if (existing != null) {
      return existing;
    }

    String exprName = buildInlineExpressionName(token, resolvedSpec);
    com.jvn.core.vn.VnCharacter.Builder builder = getOrCreateCharacterBuilder(state, characterId);
    if (!builder.hasExpression(exprName)) {
      builder.addExpression(exprName, resolvedSpec);
    }
    state.inlineCompositeExpressions.put(cacheKey, exprName);
    return exprName;
  }

  private List<String> splitResolvedLayerSpec(String spec) {
    String[] tokens = spec.split("\\|");
    List<String> resolved = new ArrayList<>();
    for (String token : tokens) {
      if (token == null) continue;
      String trimmed = token.trim();
      if (!trimmed.isEmpty()) {
        resolved.add(trimmed);
      }
    }
    return resolved;
  }

  private String resolveLayerReferencePath(ParseState state,
                                           String defaultCharacterId,
                                           String rawRef,
                                           String sourceName,
                                           int lineNumber,
                                           String rawLine) throws IOException {
    String path = LayeredCharacterResolver.resolveLayerPath(state.charLayers, defaultCharacterId, rawRef);
    LayeredCharacterResolver.CharacterRef layerRef = LayeredCharacterResolver.parseReference(rawRef, defaultCharacterId);
    if (path == null || path.isBlank()) {
      throw parseError(
          sourceName,
          lineNumber,
          "Unknown @charlayer reference '$" + rawRef + "' for character '" + layerRef.characterId() + "'",
          rawLine);
    }
    return path;
  }

  private com.jvn.core.vn.VnCharacter.Builder getOrCreateCharacterBuilder(ParseState state, String characterId) {
    com.jvn.core.vn.VnCharacter.Builder cb = state.charBuilders.get(characterId);
    if (cb == null) {
      cb = com.jvn.core.vn.VnCharacter.builder(characterId);
      state.charBuilders.put(characterId, cb);
    }
    return cb;
  }

  private String buildInlineExpressionName(String token, String resolvedSpec) {
    String base = token
        .replace('@', ' ')
        .replace('$', ' ')
        .replace('+', ' ')
        .replace(':', ' ')
        .replace('.', ' ')
        .trim()
        .replaceAll("[^A-Za-z0-9_]+", "_")
        .replaceAll("_+", "_");
    if (base.isEmpty()) {
      base = "composite";
    }
    if (!Character.isLetter(base.charAt(0)) && base.charAt(0) != '_') {
      base = "_" + base;
    }
    String hash = Integer.toUnsignedString(resolvedSpec.hashCode(), 36);
    return "__inline_" + base.toLowerCase() + "_" + hash;
  }

  private void validateLabelReferences(ParseState state) throws IOException {
    for (LabelReference ref : state.labelReferences) {
      if (ref.label == null || ref.label.isBlank()) continue;
      if (!state.declaredLabels.containsKey(ref.label)) {
        throw parseError(
            ref.source,
            ref.line,
            "Undefined label '" + ref.label + "' referenced by " + ref.kind,
            ref.rawLine
        );
      }
    }
  }

  private void registerLabel(ParseState state,
                             String label,
                             String sourceName,
                             int lineNumber,
                             String rawLine,
                             boolean synthetic) throws IOException {
    String normalized = label == null ? "" : label.trim();
    if (normalized.isEmpty()) {
      throw parseError(sourceName, lineNumber, "Label name is empty", rawLine);
    }
    if (!LABEL_NAME_PATTERN.matcher(normalized).matches()) {
      throw parseError(sourceName, lineNumber, "Invalid label name '" + normalized + "'", rawLine);
    }

    LabelDeclaration prev = state.declaredLabels.get(normalized);
    if (prev != null) {
      String where = " (previously declared at " + prev.source + ":" + prev.line + ")";
      throw parseError(sourceName, lineNumber, "Duplicate label '" + normalized + "'" + where, rawLine);
    }
    state.declaredLabels.put(normalized, new LabelDeclaration(sourceName, lineNumber, synthetic));
  }

  private void addLabelReference(ParseState state,
                                 String label,
                                 String sourceName,
                                 int lineNumber,
                                 String rawLine,
                                 String kind) {
    if (label == null || label.isBlank()) return;
    state.labelReferences.add(new LabelReference(label.trim(), sourceName, lineNumber, rawLine, kind));
  }

  private String nextSyntheticLabel(ParseState state, String prefix) {
    state.syntheticLabelCounter++;
    return "__" + prefix + "_" + state.syntheticLabelCounter;
  }

  private IfGoto parseIfGoto(String payload) {
    Matcher m = IF_GOTO_PATTERN.matcher(payload == null ? "" : payload.trim());
    if (!m.matches()) return null;
    String expr = m.group(1) == null ? "" : m.group(1).trim();
    String label = m.group(2) == null ? "" : m.group(2).trim();
    if (expr.isEmpty() || label.isEmpty()) return null;
    return new IfGoto(expr, label);
  }

  private void validateConditionExpression(String expression,
                                           String sourceName,
                                           int lineNumber,
                                           String rawLine) throws IOException {
    try {
      VnConditionEvaluator.validate(expression);
    } catch (IllegalArgumentException ex) {
      throw parseError(sourceName, lineNumber, "Invalid condition expression: " + ex.getMessage(), rawLine);
    }
  }

  private record InlinePosition(CharacterPosition position, Integer layerOrder) {}

  private InlinePosition parseAtPosition(String coordToken, String sourceName,
                                         int lineNumber, String rawLine) throws IOException {
    String[] parts = coordToken.split(",");
    if (parts.length < 1 || parts.length > 3) {
      throw parseError(sourceName, lineNumber, "'at' expects x,y or x,y,z format, got: " + coordToken, rawLine);
    }
    try {
      double x = Double.parseDouble(parts[0].trim());
      double y = parts.length >= 2 ? Double.parseDouble(parts[1].trim()) : -1.0;
      Integer z = parts.length == 3 ? Integer.parseInt(parts[2].trim()) : null;
      return new InlinePosition(CharacterPosition.at(x, y), z);
    } catch (NumberFormatException e) {
      throw parseError(sourceName, lineNumber, "'at' coordinates must be numbers: " + coordToken, rawLine);
    }
  }

  private CharacterPosition parsePosition(String token,
                                          String sourceName,
                                          int lineNumber,
                                          String rawLine,
                                          ParseState state) throws IOException {
    CharacterPosition predefined = CharacterPosition.predefined(token);
    if (predefined != null) return predefined;
    // Check custom positions defined via @position
    CharacterPosition custom = state.getCustomPosition(token);
    if (custom != null) return custom;
    throw parseError(sourceName, lineNumber, "Unknown character position: " + token, rawLine);
  }

  private VnTransition.TransitionType parseTransitionType(String token) {
    String t = token.trim().toUpperCase();
    try {
      return VnTransition.TransitionType.valueOf(t);
    } catch (IllegalArgumentException e) {
      if (t.equals("FADE")) return VnTransition.TransitionType.FADE;
      if (t.equals("DISSOLVE")) return VnTransition.TransitionType.DISSOLVE;
      if (t.equals("CROSSFADE")) return VnTransition.TransitionType.CROSSFADE;
      if (t.equals("SLIDE_LEFT")) return VnTransition.TransitionType.SLIDE_LEFT;
      if (t.equals("SLIDE_RIGHT")) return VnTransition.TransitionType.SLIDE_RIGHT;
      if (t.equals("WIPE")) return VnTransition.TransitionType.WIPE;
      if (t.equals("PIXELATE") || t.equals("PIXEL")) return VnTransition.TransitionType.PIXELATE;
      if (t.equals("BLINDS") || t.equals("BLIND")) return VnTransition.TransitionType.BLINDS;
      if (t.equals("IRIS_IN") || t.equals("IRISIN")) return VnTransition.TransitionType.IRIS_IN;
      if (t.equals("IRIS_OUT") || t.equals("IRISOUT")) return VnTransition.TransitionType.IRIS_OUT;
      return null;
    }
  }

  private void ensureBuilder(ParseState state) {
    if (state.builder == null) {
      state.builder = new VnScenarioBuilder(state.scenarioId);
    }
  }

  private String requireArg(String arg,
                            String cmd,
                            String sourceName,
                            int lineNumber,
                            String rawLine) throws IOException {
    if (arg == null || arg.isBlank()) {
      throw parseError(sourceName, lineNumber, "[" + cmd + "] requires arguments", rawLine);
    }
    return arg.trim();
  }

  private void ensureNoArg(String arg,
                           String cmd,
                           String sourceName,
                           int lineNumber,
                           String rawLine) throws IOException {
    if (arg != null && !arg.isBlank()) {
      throw parseError(sourceName, lineNumber, "[" + cmd + "] does not accept arguments", rawLine);
    }
  }

  private IOException parseError(String sourceName, int lineNumber, String message, String line) {
    String src = (sourceName == null || sourceName.isBlank()) ? "<script>" : sourceName;
    return new IOException("Parse error in " + src + " at line " + lineNumber + ": " + message + " -> " + line);
  }

  private String applyDefines(String line, Map<String, String> defines) {
    Matcher m = DEFINE_SUB_PATTERN.matcher(line);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String key = m.group(1);
      String val = defines.getOrDefault(key, "");
      m.appendReplacement(sb, Matcher.quoteReplacement(val));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private String resolveIncludePath(String sourceName, String includePath) {
    if (includePath.startsWith("/") || includePath.contains(":")) return includePath;
    String src = normalizeSourceName(sourceName);
    int idx = src.lastIndexOf('/');
    if (idx < 0) return includePath;
    return src.substring(0, idx + 1) + includePath;
  }

  private String normalizeSourceName(String sourceName) {
    if (sourceName == null || sourceName.isBlank()) return "<script>";
    return sourceName.replace('\\', '/');
  }

  private String stripQuotes(String value) {
    if (value == null) return "";
    String t = value.trim();
    if (t.length() >= 2) {
      char c0 = t.charAt(0);
      char c1 = t.charAt(t.length() - 1);
      if ((c0 == '"' && c1 == '"') || (c0 == '\'' && c1 == '\'')) {
        return t.substring(1, t.length() - 1);
      }
    }
    return t;
  }

  private String unescapeQuoted(String value) {
    if (value == null || value.isEmpty()) return "";
    StringBuilder out = new StringBuilder(value.length());
    boolean esc = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (esc) {
        switch (c) {
          case 'n' -> out.append('\n');
          case 't' -> out.append('\t');
          case 'r' -> out.append('\r');
          case '"' -> out.append('"');
          case '\\' -> out.append('\\');
          default -> out.append(c);
        }
        esc = false;
      } else if (c == '\\') {
        esc = true;
      } else {
        out.append(c);
      }
    }
    if (esc) out.append('\\');
    return out.toString();
  }

  private Easing.Type parseEasingToken(String token) {
    com.jvn.core.animation.EasingSpec spec = com.jvn.core.animation.EasingSpec.tryParse(token);
    return spec == null ? null : spec.getType();
  }
}
