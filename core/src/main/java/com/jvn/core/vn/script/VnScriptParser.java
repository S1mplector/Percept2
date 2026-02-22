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

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.vn.CharacterPosition;
import com.jvn.core.vn.Choice;
import com.jvn.core.vn.VnConditionEvaluator;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioBuilder;
import com.jvn.core.vn.VnTransition;

/**
 * Parses text-based VN scripts into {@link VnScenario} objects.
 */
public class VnScriptParser {
  private static final Pattern SCENARIO_PATTERN = Pattern.compile("^@scenario\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARACTER_PATTERN = Pattern.compile("^@character\\s+(\\S+)\\s+\"([^\"]*)\"$", Pattern.CASE_INSENSITIVE);
  private static final Pattern BACKGROUND_PATTERN = Pattern.compile("^@background\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARIMG_PATTERN = Pattern.compile("^@charimg\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern VAR_PATTERN = Pattern.compile("^@var\\s+(\\S+)(?:\\s*=\\s*(.+)|\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern LABEL_PATTERN = Pattern.compile("^@label\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern LABEL_LEGACY_PATTERN = Pattern.compile("^label\\s+(\\S+)\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern DIALOGUE_PATTERN = Pattern.compile("^([^:]+):\\s*(.+)$");
  private static final Pattern DIALOGUE_QUOTED_PATTERN = Pattern.compile("^(\\S+)\\s+\"((?:[^\"\\\\]|\\\\.)*)\"$");
  private static final Pattern COMMAND_PATTERN = Pattern.compile("^\\[(.+)]$");
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
    Map<String, String> defines = new HashMap<>();
    Map<String, LabelDeclaration> declaredLabels = new HashMap<>();
    List<LabelReference> labelReferences = new ArrayList<>();
    Deque<ConditionalBlock> conditionalBlocks = new ArrayDeque<>();
    int syntheticLabelCounter = 0;
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

      Matcher labelMatcher = LABEL_PATTERN.matcher(trimmed);
      if (labelMatcher.matches()) {
        state.contentEmitted = true;
        flushChoices(state.builder, state.pendingChoices);
        String label = labelMatcher.group(1).trim();
        registerLabel(state, label, sourceName, lineNumber, rawLine, false);
        state.builder.label(label);
        continue;
      }

      Matcher legacyLabelMatcher = LABEL_LEGACY_PATTERN.matcher(trimmed);
      if (legacyLabelMatcher.matches()) {
        state.contentEmitted = true;
        flushChoices(state.builder, state.pendingChoices);
        String label = legacyLabelMatcher.group(1).trim();
        registerLabel(state, label, sourceName, lineNumber, rawLine, false);
        state.builder.label(label);
        continue;
      }

      if (trimmed.startsWith(">")) {
        state.contentEmitted = true;
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
        ensureBuilder(state);
        StringBuilder block = new StringBuilder();
        int braceDepth = 0;
        // Count opening braces on the first line
        for (char c : trimmed.toCharArray()) { if (c == '{') braceDepth++; }
        if (braceDepth == 0) {
          // Opening brace on next line
          String nextLine;
          while ((nextLine = reader.readLine()) != null) {
            lineNumber++;
            String nt = nextLine.trim();
            if (nt.isEmpty() || nt.startsWith("#")) continue;
            if (nt.equals("{")) { braceDepth = 1; break; }
            throw parseError(sourceName, lineNumber, "Expected '{' after timeline", nextLine);
          }
        }
        while (braceDepth > 0 && (line = reader.readLine()) != null) {
          lineNumber++;
          String lt = line.trim();
          for (char c : lt.toCharArray()) {
            if (c == '{') braceDepth++;
            else if (c == '}') braceDepth--;
          }
          if (braceDepth > 0) block.append(lt).append('\n');
          else {
            // Remove trailing } from the line content if there's anything before it
            int lastBrace = lt.lastIndexOf('}');
            if (lastBrace > 0) block.append(lt, 0, lastBrace).append('\n');
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
        String displayName = resolveDisplayName(state, speakerId);
        state.builder.dialogue(displayName, text);
        continue;
      }

      Matcher quotedDialogueMatcher = DIALOGUE_QUOTED_PATTERN.matcher(trimmed);
      if (quotedDialogueMatcher.matches()) {
        state.contentEmitted = true;
        flushChoices(state.builder, state.pendingChoices);
        String speakerId = quotedDialogueMatcher.group(1).trim();
        String text = unescapeQuoted(quotedDialogueMatcher.group(2));
        String displayName = resolveDisplayName(state, speakerId);
        state.builder.dialogue(displayName, text);
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
        ensureNoArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.end();
        return;
      case "bgm": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = payload.split("\\s+");
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
        String[] toks = payload.split("\\s+");
        if (toks.length < 2 || toks[0].isBlank()) {
          throw parseError(sourceName, lineNumber, "[bgm_crossfade] expects: [bgm_crossfade <trackId> <ms> [loop]]", rawLine);
        }

        String track = toks[0];
        long durationMs;
        try {
          durationMs = Long.parseLong(toks[1]);
        } catch (NumberFormatException ex) {
          throw parseError(sourceName, lineNumber, "[bgm_crossfade] duration must be an integer in ms", rawLine);
        }
        if (durationMs < 0) {
          throw parseError(sourceName, lineNumber, "[bgm_crossfade] duration must be >= 0", rawLine);
        }

        String normalized = "crossfade " + track + " " + durationMs;
        if (toks.length >= 3) {
          String loopToken = toks[2];
          String loopValue = loopToken;
          int eq = loopToken.indexOf('=');
          if (eq > 0) {
            String key = loopToken.substring(0, eq).trim().toLowerCase();
            if (!"loop".equals(key)) {
              throw parseError(sourceName, lineNumber, "[bgm_crossfade] unknown option: " + key, rawLine);
            }
            loopValue = loopToken.substring(eq + 1).trim();
          }
          if (!isBooleanToken(loopValue)) {
            throw parseError(sourceName, lineNumber, "[bgm_crossfade] loop must be true/false/on/off/1/0", rawLine);
          }
          normalized += " " + parseBooleanToken(loopValue);
        }
        if (toks.length > 3) {
          throw parseError(sourceName, lineNumber, "[bgm_crossfade] too many arguments", rawLine);
        }

        state.builder.external("audio", normalized);
        return;
      }
      case "sfx": {
        String track = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.playSfx(track);
        return;
      }
      case "voice": {
        String track = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.playVoice(track);
        return;
      }
      case "volume": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("settings", "volume " + payload);
        return;
      }
      case "textspeed": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("settings", "textspeed " + payload);
        return;
      }
      case "autodelay": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.external("settings", "autodelay " + payload);
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
      case "history":
        state.builder.external("history", arg == null ? "" : arg);
        return;
      case "screen":
        state.builder.external("screen", arg == null ? "" : arg);
        return;
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
        String msArg = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        try {
          state.builder.waitMs(Long.parseLong(msArg));
        } catch (NumberFormatException ex) {
          throw parseError(sourceName, lineNumber, "[wait] expects an integer duration in ms", rawLine);
        }
        return;
      }
      case "show": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = payload.split("\\s+");
        if (toks.length < 2) {
          throw parseError(sourceName, lineNumber, "[show] expects: [show <charId> <pos> [expression] [layer]]", rawLine);
        }
        String charId = toks[0];
        CharacterPosition pos = parsePosition(toks[1]);
        String expr = "neutral";
        Integer layerOrder = null;
        if (toks.length >= 3) {
          if (toks.length == 3 && isIntegerToken(toks[2])) {
            layerOrder = Integer.parseInt(toks[2]);
          } else {
            expr = toks[2];
            if (toks.length >= 4) {
              if (!isIntegerToken(toks[3])) {
                throw parseError(sourceName, lineNumber, "[show] layer must be an integer", rawLine);
              }
              layerOrder = Integer.parseInt(toks[3]);
            }
          }
        }
        state.builder.show(charId, expr, pos, layerOrder);
        return;
      }
      case "hide": {
        String charId = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        state.builder.hide(charId);
        return;
      }
      case "transition": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        String[] toks = payload.split("\\s+");
        VnTransition.TransitionType type = parseTransitionType(toks[0]);
        if (type == null) {
          throw parseError(sourceName, lineNumber, "Unknown transition type: " + toks[0], rawLine);
        }
        long dur = 500;
        if (toks.length >= 2) {
          try {
            dur = Long.parseLong(toks[1]);
          } catch (NumberFormatException ex) {
            throw parseError(sourceName, lineNumber, "[transition] duration must be an integer", rawLine);
          }
        }
        String bg = toks.length >= 3 ? toks[2] : null;
        state.builder.transition(type, dur, bg);
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
        state.builder.external("char", payload);
        return;
      }
      case "choice": {
        String payload = requireArg(arg, cmd, sourceName, lineNumber, rawLine);
        List<Choice> inlineChoices = parseInlineChoices(payload, sourceName, lineNumber, rawLine, state);
        if (inlineChoices.isEmpty()) {
          throw parseError(sourceName, lineNumber, "[choice] must contain at least one option", rawLine);
        }
        state.builder.choiceNodes(inlineChoices);
        return;
      }
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

  private void flushChoices(VnScenarioBuilder builder, List<Choice> choices) {
    if (!choices.isEmpty()) {
      builder.choiceNodes(new ArrayList<>(choices));
      choices.clear();
    }
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

  private CharacterPosition parsePosition(String token) {
    String t = token.trim().toUpperCase();
    try {
      return CharacterPosition.valueOf(t);
    } catch (IllegalArgumentException e) {
      if (t.equals("L")) return CharacterPosition.LEFT;
      if (t.equals("C") || t.equals("CENTER")) return CharacterPosition.CENTER;
      if (t.equals("R")) return CharacterPosition.RIGHT;
      if (t.equals("FL")) return CharacterPosition.FAR_LEFT;
      if (t.equals("FR")) return CharacterPosition.FAR_RIGHT;
      return CharacterPosition.CENTER;
    }
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
}
