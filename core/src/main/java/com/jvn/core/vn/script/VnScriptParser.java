package com.jvn.core.vn.script;

import com.jvn.core.vn.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
  private static final Pattern CHARACTER_PATTERN = Pattern.compile("^@character\\s+(\\S+)\\s+\"([^\"]+)\"$");
  private static final Pattern BACKGROUND_PATTERN = Pattern.compile("^@background\\s+(\\S+)\\s+(.+)$");
  private static final Pattern LABEL_PATTERN = Pattern.compile("^@label\\s+(.+)$");
  private static final Pattern DIALOGUE_PATTERN = Pattern.compile("^([^:]+):\\s*(.+)$");
  private static final Pattern CHOICE_PATTERN = Pattern.compile("^>\\s*(.+?)(?:\\s*->\\s*(.+))?$");
  private static final Pattern COMMAND_PATTERN = Pattern.compile("^\\[([^\\]]+)\\]$");
  
  public VnScenario parse(InputStream input) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(input));
    String scenarioId = "untitled";
    VnScenarioBuilder builder = null;
    List<Choice> pendingChoices = new ArrayList<>();
    
    String line;
    int lineNumber = 0;
    
    while ((line = reader.readLine()) != null) {
      lineNumber++;
      line = line.trim();
      
      // Skip empty lines and comments
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      
      // Scenario declaration
      Matcher scenarioMatcher = SCENARIO_PATTERN.matcher(line);
      if (scenarioMatcher.matches()) {
        scenarioId = scenarioMatcher.group(1);
        builder = new VnScenarioBuilder(scenarioId);
        continue;
      }
      
      if (builder == null) {
        builder = new VnScenarioBuilder(scenarioId);
      }
      
      // Character definition
      Matcher charMatcher = CHARACTER_PATTERN.matcher(line);
      if (charMatcher.matches()) {
        String id = charMatcher.group(1);
        String name = charMatcher.group(2);
        builder.addCharacter(id, name);
        continue;
      }
      
      // Background definition
      Matcher bgMatcher = BACKGROUND_PATTERN.matcher(line);
      if (bgMatcher.matches()) {
        String id = bgMatcher.group(1);
        String path = bgMatcher.group(2);
        builder.addBackground(id, path);
        continue;
      }
      
      // Label
      Matcher labelMatcher = LABEL_PATTERN.matcher(line);
      if (labelMatcher.matches()) {
        flushChoices(builder, pendingChoices);
        builder.label(labelMatcher.group(1));
        continue;
      }
      
      // Choice
      Matcher choiceMatcher = CHOICE_PATTERN.matcher(line);
      if (choiceMatcher.matches()) {
        String text = choiceMatcher.group(1);
        String target = choiceMatcher.groupCount() > 1 ? choiceMatcher.group(2) : null;
        Choice.Builder choiceBuilder = Choice.builder().text(text);
        if (target != null) {
          choiceBuilder.targetLabel(target);
        }
        pendingChoices.add(choiceBuilder.build());
        continue;
      }
      
      // Commands
      Matcher cmdMatcher = COMMAND_PATTERN.matcher(line);
      if (cmdMatcher.matches()) {
        flushChoices(builder, pendingChoices);
        String[] parts = cmdMatcher.group(1).split("\\s+", 2);
        String cmd = parts[0];
        String arg = parts.length > 1 ? parts[1] : null;
        
        switch (cmd.toLowerCase()) {
          case "background":
          case "bg":
            if (arg != null) builder.background(arg);
            break;
          case "jump":
            if (arg != null) builder.jump(arg);
            break;
          case "end":
            builder.end();
            break;
          case "bgm":
            if (arg != null && !arg.isEmpty()) builder.playBgm(arg, true);
            break;
          case "bgm_stop":
            builder.stopBgm();
            break;
          case "sfx":
            if (arg != null && !arg.isEmpty()) builder.playSfx(arg);
            break;
          case "voice":
            if (arg != null && !arg.isEmpty()) {
              // Voice handled like SFX for now
              builder.playSfx(arg);
            }
            break;
          case "wait":
            if (arg != null) {
              try { builder.waitMs(Long.parseLong(arg)); } catch (NumberFormatException ignored) {}
            }
            break;
          case "show":
            if (arg != null) {
              String[] toks = arg.split("\\s+");
              if (toks.length >= 2) {
                String charId = toks[0];
                CharacterPosition pos = parsePosition(toks[1]);
                String expr = toks.length >= 3 ? toks[2] : "neutral";
                builder.show(charId, expr, pos);
              }
            }
            break;
          case "hide":
            if (arg != null) builder.hide(arg);
            break;
          case "transition":
            if (arg != null) {
              String[] toks = arg.split("\\s+");
              if (toks.length >= 1) {
                VnTransition.TransitionType type = parseTransitionType(toks[0]);
                long dur = toks.length >= 2 ? parseLongSafe(toks[1], 500) : 500;
                String bg = toks.length >= 3 ? toks[2] : null;
                builder.transition(type, dur, bg);
              }
            }
            break;
        }
        continue;
      }
      
      // Dialogue
      Matcher dialogueMatcher = DIALOGUE_PATTERN.matcher(line);
      if (dialogueMatcher.matches()) {
        flushChoices(builder, pendingChoices);
        String speaker = dialogueMatcher.group(1).trim();
        String text = dialogueMatcher.group(2).trim();
        builder.dialogue(speaker, text);
        continue;
      }
      
      throw new IOException("Parse error at line " + lineNumber + ": " + line);
    }
    
    flushChoices(builder, pendingChoices);
    return builder.build();
  }
  
  private void flushChoices(VnScenarioBuilder builder, List<Choice> choices) {
    if (!choices.isEmpty()) {
      builder.choiceWithTargets(choices.stream()
        .map(c -> new String[]{c.getText(), c.getTargetLabel()})
        .toArray(String[][]::new));
      choices.clear();
    }
  }
  
  public VnScenario parseFromString(String script) throws IOException {
    return parse(new java.io.ByteArrayInputStream(script.getBytes()));
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
}
