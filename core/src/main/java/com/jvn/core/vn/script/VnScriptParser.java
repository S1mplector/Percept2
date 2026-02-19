package com.jvn.core.vn.script;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.ArrayDeque;
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
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScenarioBuilder;
import com.jvn.core.vn.VnTransition;

/**
 * Parses text-based VN scripts into VnScenario objects
 * 
 * Script format:
 * @scenario id
 * @character id "Display Name"
 * @background id path/to/image.png
 * @label labelName
 * Speaker: Dialogue text
 * > Choice 1 -> label1
 * > Choice 2 -> label2
 * [background bgId]
 * [jump labelName]
 * [end]
 */
public class VnScriptParser {
  
  private static final Pattern SCENARIO_PATTERN = Pattern.compile("^@scenario\\s+(.+)$");
  private static final Pattern CHARACTER_PATTERN = Pattern.compile("^@character\\s+(\\S+)\\s+\"([^\"]*)\"$");
  private static final Pattern BACKGROUND_PATTERN = Pattern.compile("^@background\\s+(\\S+)\\s+(.+)$");
  private static final Pattern CHARIMG_PATTERN = Pattern.compile("^@charimg\\s+(\\S+)\\s+(\\S+)\\s+(.+)$");
  private static final Pattern LABEL_PATTERN = Pattern.compile("^@label\\s+(.+)$");
  private static final Pattern LABEL_LEGACY_PATTERN = Pattern.compile("^label\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern DIALOGUE_PATTERN = Pattern.compile("^([^:]+):\\s*(.+)$");
  private static final Pattern DIALOGUE_QUOTED_PATTERN = Pattern.compile("^(\\S+)\\s+\"((?:[^\"\\\\]|\\\\.)*)\"$");
  private static final Pattern CHOICE_PATTERN = Pattern.compile("^>\\s*(.+?)(?:\\s*->\\s*(.+))?$");
  private static final Pattern COMMAND_PATTERN = Pattern.compile("^\\[(.+)\\]$");
  private static final Pattern DEFINE_PATTERN = Pattern.compile("^@define\\s+(\\S+)(?:\\s+(.+))?$");
  private static final Pattern INCLUDE_PATTERN = Pattern.compile("^@include\\s+(.+)$");
  private static final Pattern DEFINE_SUB_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

  public interface IncludeResolver {
    InputStream open(String path) throws IOException;
  }

  private static class ParseState {
    String scenarioId = "untitled";
    VnScenarioBuilder builder;
    List<Choice> pendingChoices = new ArrayList<>();
    Map<String, com.jvn.core.vn.VnCharacter.Builder> charBuilders = new HashMap<>();
    Map<String, String> defines = new HashMap<>();
  }
  
  public VnScenario parse(InputStream input) throws IOException {
    return parse(input, "<input>", null);
  }

  public VnScenario parse(InputStream input, String sourceName, IncludeResolver resolver) throws IOException {
    ParseState state = new ParseState();
    Deque<String> includeStack = new ArrayDeque<>();
    String src = normalizeSourceName(sourceName);
    includeStack.push(src);
    parseInto(input, src, resolver, state, includeStack);
    includeStack.pop();

    ensureBuilder(state);
    flushChoices(state.builder, state.pendingChoices);
    // Finalize characters with expressions (replaces any earlier simple character entries)
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

  private void parseInto(InputStream input, String sourceName, IncludeResolver resolver,
                         ParseState state, Deque<String> includeStack) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(input));
    String line;
    int lineNumber = 0;

    while ((line = reader.readLine()) != null) {
      lineNumber++;
      String rawLine = line;
      String trimmed = rawLine.trim();

      // Skip empty lines and comments
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }

      Matcher defineMatcher = DEFINE_PATTERN.matcher(trimmed);
      if (defineMatcher.matches()) {
        String key = defineMatcher.group(1);
        String value = defineMatcher.group(2) != null ? defineMatcher.group(2).trim() : "";
        value = stripQuotes(value);
        state.defines.put(key, value);
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
          parseInto(inc, resolved, resolver, state, includeStack);
          includeStack.pop();
        }
        continue;
      }

      if (!state.defines.isEmpty()) {
        rawLine = applyDefines(rawLine, state.defines);
        trimmed = rawLine.trim();
      }

      // Scenario declaration
      Matcher scenarioMatcher = SCENARIO_PATTERN.matcher(trimmed);
      if (scenarioMatcher.matches()) {
        state.scenarioId = scenarioMatcher.group(1);
        state.builder = new VnScenarioBuilder(state.scenarioId);
        continue;
      }

      ensureBuilder(state);

      // Character definition
      Matcher charMatcher = CHARACTER_PATTERN.matcher(trimmed);
      if (charMatcher.matches()) {
        String id = charMatcher.group(1);
        String name = charMatcher.group(2);
        // Track/merge builder so expressions from @charimg can be added later
        com.jvn.core.vn.VnCharacter.Builder cb = state.charBuilders.get(id);
        if (cb == null) { cb = com.jvn.core.vn.VnCharacter.builder(id); state.charBuilders.put(id, cb); }
        cb.displayName(name);
        // Keep compatibility by adding a simple character entry now (will be replaced at end if @charimg used)
        state.builder.addCharacter(id, name);
        continue;
      }

      // Background definition
      Matcher bgMatcher = BACKGROUND_PATTERN.matcher(trimmed);
      if (bgMatcher.matches()) {
        String id = bgMatcher.group(1);
        String path = bgMatcher.group(2);
        state.builder.addBackground(id, path);
        continue;
      }

      // Character image mapping: @charimg <charId> <expression> <path>
      Matcher imgMatcher = CHARIMG_PATTERN.matcher(trimmed);
      if (imgMatcher.matches()) {
        String id = imgMatcher.group(1);
        String expr = imgMatcher.group(2);
        String path = imgMatcher.group(3);
        com.jvn.core.vn.VnCharacter.Builder cb = state.charBuilders.get(id);
        if (cb == null) { cb = com.jvn.core.vn.VnCharacter.builder(id); state.charBuilders.put(id, cb); }
        cb.addExpression(expr, path);
        continue;
      }

      // Label
      Matcher labelMatcher = LABEL_PATTERN.matcher(trimmed);
      if (labelMatcher.matches()) {
        flushChoices(state.builder, state.pendingChoices);
        state.builder.label(labelMatcher.group(1));
        continue;
      }
      Matcher legacyLabelMatcher = LABEL_LEGACY_PATTERN.matcher(trimmed);
      if (legacyLabelMatcher.matches()) {
        flushChoices(state.builder, state.pendingChoices);
        state.builder.label(legacyLabelMatcher.group(1));
        continue;
      }

      // Choice
      Matcher choiceMatcher = CHOICE_PATTERN.matcher(trimmed);
      if (choiceMatcher.matches()) {
        String text = choiceMatcher.group(1);
        String target = choiceMatcher.groupCount() > 1 ? choiceMatcher.group(2) : null;
        String cond = null;
        Matcher m = Pattern.compile("^(.*)\\[if\\s+([^\\]]+)\\]$").matcher(text);
        if (m.matches()) {
          text = m.group(1).trim();
          cond = m.group(2).trim();
        }
        Choice.Builder choiceBuilder = Choice.builder().text(text);
        if (cond != null) choiceBuilder.condition(cond);
        if (target != null) {
          choiceBuilder.targetLabel(target);
        }
        state.pendingChoices.add(choiceBuilder.build());
        continue;
      }

      // Commands
      Matcher cmdMatcher = COMMAND_PATTERN.matcher(trimmed);
      if (cmdMatcher.matches()) {
        flushChoices(state.builder, state.pendingChoices);
        String[] parts = cmdMatcher.group(1).split("\\s+", 2);
        String cmd = parts[0];
        String arg = parts.length > 1 ? parts[1] : null;

        switch (cmd.toLowerCase()) {
          case "background":
          case "bg":
            if (arg != null) state.builder.background(arg);
            break;
          case "jump":
            if (arg != null) state.builder.jump(arg);
            break;
          case "end":
            state.builder.end();
            break;
          case "bgm":
            if (arg != null && !arg.isEmpty()) state.builder.playBgm(arg, true);
            break;
          case "bgm_stop":
            state.builder.stopBgm();
            break;
          case "bgm_fadeout":
            if (arg != null && !arg.isEmpty()) {
              try { state.builder.fadeOutBgm(Long.parseLong(arg)); } catch (NumberFormatException ignored) { state.builder.fadeOutBgm(); }
            } else {
              state.builder.fadeOutBgm();
            }
            break;
          case "bgm_pause":
            state.builder.external("audio", "pause");
            break;
          case "bgm_resume":
            state.builder.external("audio", "resume");
            break;
          case "bgm_seek":
            if (arg != null && !arg.isEmpty()) state.builder.external("audio", "seek " + arg);
            break;
          case "bgm_crossfade":
            if (arg != null && !arg.isEmpty()) state.builder.external("audio", "crossfade " + arg);
            break;
          case "sfx":
            if (arg != null && !arg.isEmpty()) state.builder.playSfx(arg);
            break;
          case "voice":
            if (arg != null && !arg.isEmpty()) {
              state.builder.playVoice(arg);
            }
            break;
          case "volume":
            if (arg != null && !arg.isEmpty()) state.builder.external("settings", "volume " + arg);
            break;
          case "textspeed":
            if (arg != null && !arg.isEmpty()) state.builder.external("settings", "textspeed " + arg);
            break;
          case "autodelay":
            if (arg != null && !arg.isEmpty()) state.builder.external("settings", "autodelay " + arg);
            break;
          case "hud":
            if (arg != null && !arg.isEmpty()) state.builder.external("hud", arg);
            break;
          case "save":
            state.builder.external("save", "");
            break;
          case "quickload":
            state.builder.external("save", "quickload");
            break;
          case "skip":
            state.builder.external("mode", "skip " + (arg == null ? "" : arg));
            break;
          case "auto":
            state.builder.external("mode", "auto " + (arg == null ? "" : arg));
            break;
          case "ui":
            state.builder.external("ui", arg == null ? "" : arg);
            break;
          case "history":
            state.builder.external("history", arg == null ? "" : arg);
            break;
          case "screen":
            state.builder.external("screen", arg == null ? "" : arg);
            break;
          case "jes_push":
            if (arg != null && !arg.isBlank()) state.builder.external("jes", "push " + arg);
            break;
          case "jes_replace":
            if (arg != null && !arg.isBlank()) state.builder.external("jes", "replace " + arg);
            break;
          case "jes_pop":
            state.builder.external("jes", "pop");
            break;
          case "jes_call":
            if (arg != null && !arg.isBlank()) state.builder.external("jes", "call " + arg);
            break;
          case "wait":
            if (arg != null) {
              try { state.builder.waitMs(Long.parseLong(arg)); } catch (NumberFormatException ignored) {}
            }
            break;
          case "show":
            if (arg != null) {
              String[] toks = arg.split("\\s+");
              if (toks.length >= 2) {
                String charId = toks[0];
                CharacterPosition pos = parsePosition(toks[1]);
                String expr = toks.length >= 3 ? toks[2] : "neutral";
                state.builder.show(charId, expr, pos);
              }
            }
            break;
          case "hide":
            if (arg != null) state.builder.hide(arg);
            break;
          case "transition":
            if (arg != null) {
              String[] toks = arg.split("\\s+");
              if (toks.length >= 1) {
                VnTransition.TransitionType type = parseTransitionType(toks[0]);
                long dur = toks.length >= 2 ? parseLongSafe(toks[1], 500) : 500;
                String bg = toks.length >= 3 ? toks[2] : null;
                state.builder.transition(type, dur, bg);
              }
            }
            break;
          case "menu":
            state.builder.external("menu", arg == null ? "" : arg);
            break;
          case "settings":
            state.builder.external("menu", "settings");
            break;
          case "mainmenu":
            state.builder.external("menu", "main" + (arg == null || arg.isBlank() ? "" : (" " + arg)));
            break;
          case "load":
            if (arg != null && !arg.isBlank()) state.builder.external("vns", "replace " + arg);
            break;
          case "goto":
            if (arg != null && !arg.isBlank()) state.builder.external("vns", "goto " + arg);
            break;
          case "set":
            if (arg != null && !arg.isBlank()) state.builder.external("var", "set " + arg);
            break;
          case "inc":
            if (arg != null && !arg.isBlank()) state.builder.external("var", "inc " + arg);
            break;
          case "dec":
            if (arg != null && !arg.isBlank()) state.builder.external("var", "dec " + arg);
            break;
          case "flag":
            if (arg != null && !arg.isBlank()) state.builder.external("var", "flag " + arg);
            break;
          case "unflag":
            if (arg != null && !arg.isBlank()) state.builder.external("var", "unflag " + arg);
            break;
          case "clear":
            if (arg != null && !arg.isBlank()) state.builder.external("var", "clear " + arg);
            break;
          case "if":
            if (arg != null && !arg.isBlank()) state.builder.external("cond", "if " + arg);
            break;
          case "call":
            // Syntax: [call <provider> <payload...>]
            if (arg != null && !arg.isBlank()) {
              String[] toks = arg.split("\\s+", 2);
              String provider = toks[0];
              String payload = toks.length > 1 ? toks[1] : "";
              state.builder.external(provider, payload);
            }
            break;
          case "choice":
            // Legacy inline choice syntax:
            // [choice Continue->next | Exit->ending]
            if (arg != null && !arg.isBlank()) {
              List<Choice> inlineChoices = parseInlineChoices(arg);
              if (!inlineChoices.isEmpty()) {
                state.builder.choiceNodes(inlineChoices);
              }
            }
            break;
          case "jes":
            // Shortcut for [call jes <payload>]
            state.builder.external("jes", arg == null ? "" : arg);
            break;
          case "java":
            // Shortcut for [call java <payload>]
            state.builder.external("java", arg == null ? "" : arg);
            break;
        }
        continue;
      }

      // Dialogue
      Matcher dialogueMatcher = DIALOGUE_PATTERN.matcher(trimmed);
      if (dialogueMatcher.matches()) {
        flushChoices(state.builder, state.pendingChoices);
        String speakerId = dialogueMatcher.group(1).trim();
        String text = dialogueMatcher.group(2).trim();
        // Look up display name from character definitions
        String displayName = speakerId;
        com.jvn.core.vn.VnCharacter.Builder cb = state.charBuilders.get(speakerId);
        if (cb != null) {
          String dn = cb.getDisplayName();
          if (dn != null) displayName = dn;
        }
        state.builder.dialogue(displayName, text);
        continue;
      }

      // Legacy quoted dialogue: speaker "text"
      Matcher quotedDialogueMatcher = DIALOGUE_QUOTED_PATTERN.matcher(trimmed);
      if (quotedDialogueMatcher.matches()) {
        flushChoices(state.builder, state.pendingChoices);
        String speakerId = quotedDialogueMatcher.group(1).trim();
        String text = unescapeQuoted(quotedDialogueMatcher.group(2));
        String displayName = speakerId;
        com.jvn.core.vn.VnCharacter.Builder cb = state.charBuilders.get(speakerId);
        if (cb != null) {
          String dn = cb.getDisplayName();
          if (dn != null) displayName = dn;
        }
        state.builder.dialogue(displayName, text);
        continue;
      }

      throw parseError(sourceName, lineNumber, "Unrecognized syntax", rawLine);
    }
  }
  
  private void flushChoices(VnScenarioBuilder builder, List<Choice> choices) {
    if (!choices.isEmpty()) {
      builder.choiceNodes(new java.util.ArrayList<>(choices));
      choices.clear();
    }
  }

  private List<Choice> parseInlineChoices(String arg) {
    List<Choice> out = new ArrayList<>();
    String[] rawChoices = arg.split("\\|");
    for (String raw : rawChoices) {
      if (raw == null) continue;
      String segment = raw.trim();
      if (segment.isEmpty()) continue;

      String text = segment;
      String target = null;
      int arrow = segment.indexOf("->");
      if (arrow >= 0) {
        text = segment.substring(0, arrow).trim();
        target = segment.substring(arrow + 2).trim();
        if (target != null && target.isEmpty()) target = null;
      }

      String cond = null;
      Matcher m = Pattern.compile("^(.*)\\[if\\s+([^\\]]+)\\]$").matcher(text);
      if (m.matches()) {
        text = m.group(1).trim();
        cond = m.group(2).trim();
      }
      if (text.isEmpty()) continue;

      Choice.Builder choiceBuilder = Choice.builder().text(text);
      if (cond != null && !cond.isEmpty()) choiceBuilder.condition(cond);
      if (target != null) choiceBuilder.targetLabel(target);
      out.add(choiceBuilder.build());
    }
    return out;
  }
  
  public VnScenario parseFromString(String script) throws IOException {
    return parse(new java.io.ByteArrayInputStream(script.getBytes()), "<string>", null);
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
      return VnTransition.TransitionType.NONE;
    }
  }

  private long parseLongSafe(String s, long def) {
    try {
      return Long.parseLong(s);
    } catch (Exception e) {
      return def;
    }
  }

  private void ensureBuilder(ParseState state) {
    if (state.builder == null) {
      state.builder = new VnScenarioBuilder(state.scenarioId);
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
