package com.jvn.editor.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.jvn.core.animation.TimelineData;
import com.jvn.core.animation.TimelineDataParser;
import com.jvn.core.vn.CharacterPosition;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnScene;
import com.jvn.core.vn.VnTimelineDiagnostics;

/** Static VNS adapter for the shared VN timeline diagnostics engine. */
final class VnsTimelineDiagnostics {
  private VnsTimelineDiagnostics() {
  }

  static List<VnsScriptAnalyzer.Diagnostic> analyze(String source, VnScenario scenario) {
    return analyze(source, scenario, EditorPreferences.DEFAULT_LARGE_TIMELINE_BLOCK_ACTION_THRESHOLD);
  }

  static List<VnsScriptAnalyzer.Diagnostic> analyze(
      String source, VnScenario scenario, int largeBlockActionThreshold) {
    if (source == null || source.isBlank() || scenario == null) return List.of();
    String[] lines = source.split("\n", -1);
    PuppeteerLauncherPanel.SceneSnapshot document = PuppeteerLauncherPanel.resolveSnapshot(
        source, Math.max(0, lines.length - 1));
    if (document.inlineTimelineHistory == null || document.inlineTimelineHistory.isEmpty()) return List.of();

    List<VnsScriptAnalyzer.Diagnostic> diagnostics = new ArrayList<>();
    for (PuppeteerLauncherPanel.InlineTimelineContext context : document.inlineTimelineHistory) {
      if (context == null || context.body() == null || context.body().isBlank()) continue;
      try {
        TimelineData data = TimelineDataParser.parse(
            "_vns_diagnostic_" + Math.max(0, context.startLine()),
            context.body());
        PuppeteerLauncherPanel.SceneSnapshot snapshot = PuppeteerLauncherPanel.resolveSnapshot(
            source, Math.max(0, context.startLine() - 1));
        VnScene scene = sceneAtSnapshot(scenario, snapshot);
        VnTimelineDiagnostics.Report report = VnTimelineDiagnostics.diagnose(
            data, scenario, scene.getState());
        for (VnTimelineDiagnostics.Finding finding : report.findings()) {
          diagnostics.add(toEditorDiagnostic(source, context, finding));
        }
        VnsScriptAnalyzer.Diagnostic sizeHint =
            largeBlockHint(source, context, data, largeBlockActionThreshold);
        if (sizeHint != null) diagnostics.add(sizeHint);
      } catch (RuntimeException ignored) {
        // The strict VNS/JES parser already reports syntax failures with a more
        // precise token location. Avoid adding a second generic diagnostic.
      }
    }
    return List.copyOf(diagnostics);
  }

  private static int countActions(TimelineData data) {
    int count = 0;
    for (TimelineData.Track track : data.getTracks()) {
      for (List<?> keyframes : track.getAllKeyframes().values()) {
        count += keyframes.size();
      }
      for (List<?> keyframes : track.getAllCustomKeyframes().values()) {
        count += keyframes.size();
      }
    }
    count += data.getAudioCues().size();
    count += data.getEventCues().size();
    return count;
  }

  private static VnsScriptAnalyzer.Diagnostic largeBlockHint(
      String source,
      PuppeteerLauncherPanel.InlineTimelineContext context,
      TimelineData data,
      int threshold) {
    if (threshold <= 0) return null;
    int actionCount = countActions(data);
    if (actionCount <= threshold) return null;

    int startLine = Math.max(0, context.startLine());
    int[] lineBounds = lineBounds(source, startLine);
    String duration = VnsCodeEditor.formatTimelineOutlineDuration(context.body());
    String message = String.format(Locale.ROOT,
        "Large timeline block (%d actions, %s, %d track%s, lines %d-%d). "
            + "Fold this block or open it in Puppeteer for easier editing.",
        actionCount,
        duration == null ? "unknown duration" : duration,
        data.getTracks().size(),
        data.getTracks().size() == 1 ? "" : "s",
        startLine + 1,
        Math.max(startLine + 1, context.endLine() + 1));
    return VnsScriptAnalyzer.Diagnostic.info(
        "timeline_large_block",
        message,
        lineBounds[0],
        lineBounds[1],
        startLine,
        null,
        null,
        -1);
  }

  private static VnScene sceneAtSnapshot(
      VnScenario scenario,
      PuppeteerLauncherPanel.SceneSnapshot snapshot
  ) {
    VnScene scene = new VnScene(scenario);
    if (snapshot == null) return scene;
    for (PuppeteerLauncherPanel.CharacterEntry character : snapshot.characters) {
      if (character == null || character.characterId == null || character.characterId.isBlank()) continue;
      CharacterPosition base = character.customPosition && Double.isFinite(character.positionX)
          ? CharacterPosition.at(character.positionX, character.positionY)
          : CharacterPosition.predefined(character.position);
      String displaySlot = character.displaySlot == null || character.displaySlot.isBlank()
          ? "timeline-diagnostic-" + character.characterId
          : character.displaySlot;
      scene.getState().showCharacter(
          base == null ? CharacterPosition.CENTER : base,
          character.characterId,
          character.expression == null || character.expression.isBlank() ? "neutral" : character.expression,
          character.layerOrder,
          displaySlot);
    }
    return scene;
  }

  private static VnsScriptAnalyzer.Diagnostic toEditorDiagnostic(
      String source,
      PuppeteerLauncherPanel.InlineTimelineContext context,
      VnTimelineDiagnostics.Finding finding
  ) {
    int line = Math.max(0, context.startLine());
    int[] lineBounds = lineBounds(source, line);
    int start = lineBounds[0];
    int end = lineBounds[1];
    if (finding.target() != null && !finding.target().startsWith("(")) {
      int searchEnd = offsetForLine(source, Math.max(line + 1, context.endLine() + 1));
      int found = source.indexOf("\"" + finding.target() + "\"", start);
      if (found >= start && found < searchEnd) {
        start = found + 1;
        end = start + finding.target().length();
        line = lineForOffset(source, start);
      }
    }
    String message = finding.description();
    if (finding.quickFix() != null) message += " Fix: " + finding.quickFix();
    String kind = "timeline_" + finding.code().name().toLowerCase(Locale.ROOT);
    if (finding.blocksPlayback() || finding.severity() == VnTimelineDiagnostics.Severity.ERROR) {
      return VnsScriptAnalyzer.Diagnostic.error(kind, message, start, end, line, null, null, -1);
    }
    return VnsScriptAnalyzer.Diagnostic.warning(kind, message, start, end, line, null, null, -1);
  }

  private static int[] lineBounds(String source, int line) {
    int start = offsetForLine(source, line);
    int end = source.indexOf('\n', start);
    if (end < 0) end = source.length();
    return new int[] {start, Math.max(start + 1, end)};
  }

  private static int offsetForLine(String source, int line) {
    if (source == null || source.isEmpty() || line <= 0) return 0;
    int offset = 0;
    for (int current = 0; current < line && offset < source.length(); current++) {
      int newline = source.indexOf('\n', offset);
      if (newline < 0) return source.length();
      offset = newline + 1;
    }
    return offset;
  }

  private static int lineForOffset(String source, int offset) {
    int line = 0;
    int limit = Math.max(0, Math.min(offset, source == null ? 0 : source.length()));
    for (int i = 0; i < limit; i++) {
      if (source.charAt(i) == '\n') line++;
    }
    return line;
  }
}
