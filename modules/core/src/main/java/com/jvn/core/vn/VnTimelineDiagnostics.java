package com.jvn.core.vn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jvn.core.animation.TimelineData;
import com.jvn.core.animation.TimelineDataDiagnostics;

/**
 * Context-aware diagnostics for timelines played inside a VN scene.
 *
 * <p>The general {@link TimelineDataDiagnostics} pass validates timeline data.
 * This pass adds the character composition information that only VNS knows:
 * which characters are currently rendered, which layer IDs are active, and
 * which stable layer/group targets the renderer can actually resolve.</p>
 */
public final class VnTimelineDiagnostics {
  public enum Severity { INFO, WARNING, ERROR }

  public enum Code {
    TIMELINE_DATA,
    CHARACTER_NOT_VISIBLE,
    DEFERRED_LAYER_TARGET,
    DEFERRED_GROUP_TARGET,
    STALE_EXPRESSION_ALIAS,
    EXPRESSION_SPECIFIC_TARGET,
    UNKNOWN_CHARACTER_TARGET,
    EXACT_LAYER_REPLACEMENT_SCOPE,
    PERSISTENT_LAYER_STATE
  }

  public record Finding(
      Code code,
      Severity severity,
      String target,
      String description,
      String quickFix,
      boolean blocksPlayback
  ) {
    public Finding {
      code = code == null ? Code.TIMELINE_DATA : code;
      severity = severity == null ? Severity.INFO : severity;
      target = target == null || target.isBlank() ? "(timeline)" : target.trim();
      description = description == null ? "" : description.trim();
      quickFix = quickFix == null || quickFix.isBlank() ? null : quickFix.trim();
    }
  }

  public record Report(List<Finding> findings) {
    public Report {
      findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public boolean blocksPlayback() {
      return findings.stream().anyMatch(Finding::blocksPlayback);
    }

    public List<Finding> blockingFindings() {
      return findings.stream().filter(Finding::blocksPlayback).toList();
    }

    public long warningCount() {
      return findings.stream().filter(finding -> finding.severity() == Severity.WARNING).count();
    }
  }

  private record CharacterContext(
      String characterId,
      String safeCharacter,
      String expression,
      String safeExpression,
      VnCharacter character,
      Set<String> activeLayers,
      boolean visible
  ) {
  }

  private VnTimelineDiagnostics() {
  }

  public static Report diagnose(TimelineData data, VnScenario scenario, VnState state) {
    List<Finding> findings = new ArrayList<>();
    addTimelineDataFindings(data, findings);
    if (data == null || scenario == null || state == null) {
      return new Report(findings);
    }

    Map<String, VnState.CharacterSlot> activeSlots = activeSlotsByCharacter(state);
    List<CharacterContext> contexts = new ArrayList<>();
    for (VnCharacter character : scenario.getCharacters().values()) {
      if (character == null || character.getId() == null || character.getId().isBlank()) continue;
      VnState.CharacterSlot slot = activeSlots.get(character.getId());
      String expression = slot == null || slot.getExpression() == null || slot.getExpression().isBlank()
          ? "neutral"
          : slot.getExpression();
      Set<String> activeLayers = new LinkedHashSet<>();
      if (slot != null) {
        for (String layerId : character.getExpressionLayerIds(expression)) {
          String safeLayer = selectorSafeName(layerId);
          if (!safeLayer.isBlank()) activeLayers.add(safeLayer);
        }
      }
      contexts.add(new CharacterContext(
          character.getId(),
          selectorSafeName(character.getId()),
          expression,
          selectorSafeName(expression),
          character,
          Collections.unmodifiableSet(activeLayers),
          slot != null));
    }
    contexts.sort(Comparator.comparingInt((CharacterContext context) -> context.safeCharacter().length()).reversed());

    Set<String> diagnosedTargets = new LinkedHashSet<>();
    for (TimelineData.Track track : data.getTracks()) {
      if (track == null || track.getEntityName() == null || track.getEntityName().isBlank()) continue;
      String target = track.getEntityName().trim();
      if (!diagnosedTargets.add(target) || target.startsWith("__")) continue;
      diagnoseTarget(target, contexts, findings);
    }
    return new Report(findings);
  }

  private static void diagnoseTarget(
      String target,
      List<CharacterContext> contexts,
      List<Finding> findings
  ) {
    for (CharacterContext context : contexts) {
      if (context.safeCharacter().isBlank()) continue;
      if (target.equals(context.characterId()) || target.equals(context.safeCharacter())) {
        if (!context.visible()) {
          findings.add(blocking(
              Code.CHARACTER_NOT_VISIBLE,
              target,
              "Timeline targets character '" + context.characterId() + "', but that character is not currently shown",
              "Move the timeline after [show " + context.characterId() + " ...], or remove this target"));
        }
        return;
      }
      if (!target.startsWith(context.safeCharacter() + "_")) continue;

      if (!context.visible()) {
        findings.add(blocking(
            Code.CHARACTER_NOT_VISIBLE,
            target,
            "Timeline targets a layer of character '" + context.characterId() + "', but that character is not currently shown",
            "Move the timeline after [show " + context.characterId() + " ...]"));
        return;
      }
      if (context.character().getLayerIds().isEmpty()) return;

      if (diagnoseDeclaredLayer(target, context, findings)) return;
      if (diagnoseDeclaredGroup(target, context, findings)) return;

      findings.add(blocking(
          Code.UNKNOWN_CHARACTER_TARGET,
          target,
          "Timeline target looks like part of '" + context.characterId()
              + "' but does not match a declared layer or layer group",
          "Choose a stable target named " + context.safeCharacter()
              + "_<layer>, declare an @chargroup, or correct the target spelling"));
      return;
    }
  }

  private static boolean diagnoseDeclaredLayer(
      String target,
      CharacterContext context,
      List<Finding> findings
  ) {
    for (String layerId : context.character().getLayerIds()) {
      String safeLayer = selectorSafeName(layerId);
      if (safeLayer.isBlank()) continue;
      String stableTarget = context.safeCharacter() + "_" + safeLayer;
      String currentTarget = context.safeCharacter() + "_" + context.safeExpression() + "_" + safeLayer;
      boolean exactStable = target.equals(stableTarget);
      boolean exactCurrent = target.equals(currentTarget);
      boolean expressionAlias = target.startsWith(context.safeCharacter() + "_")
          && target.endsWith("_" + safeLayer);
      if (!exactStable && !exactCurrent && !expressionAlias) continue;

      if (!context.activeLayers().contains(safeLayer)) {
        findings.add(new Finding(
            Code.DEFERRED_LAYER_TARGET,
            Severity.WARNING,
            target,
            "Declared layer '" + layerId + "' is not visible yet; its transform will be pre-armed for a later compatible [show]",
            "Keep this when intentional, or move the timeline after the compatible [show] to preview the motion immediately",
            false));
        return true;
      }
      if (!exactStable && !exactCurrent) {
        findings.add(new Finding(
            Code.STALE_EXPRESSION_ALIAS,
            Severity.WARNING,
            target,
            "Expression-qualified layer alias belongs to another composition and is being retained for compatibility",
            "Replace it with the stable target '" + stableTarget + "'",
            false));
        return true;
      }

      if (exactCurrent && !currentTarget.equals(stableTarget)) {
        findings.add(new Finding(
            Code.EXPRESSION_SPECIFIC_TARGET,
            Severity.WARNING,
            target,
            "Expression-qualified layer target only resolves while '" + context.expression() + "' is shown",
            "Prefer the stable target '" + stableTarget + "' for shared layers",
            false));
      }

      addExactLayerScopeWarning(layerId, stableTarget, context, findings);
      addPersistentLayerStateWarning(layerId, stableTarget, context, findings);
      return true;
    }
    return false;
  }

  private static boolean diagnoseDeclaredGroup(
      String target,
      CharacterContext context,
      List<Finding> findings
  ) {
    for (VnCharacter.LayerGroup group : context.character().getLayerGroups().values()) {
      if (group == null) continue;
      String safeGroup = selectorSafeName(group.id());
      if (safeGroup.isBlank()) continue;
      String stableTarget = context.safeCharacter() + "_" + safeGroup;
      String currentTarget = context.safeCharacter() + "_" + context.safeExpression() + "_" + safeGroup;
      boolean exactStable = target.equals(stableTarget);
      boolean exactCurrent = target.equals(currentTarget);
      boolean expressionAlias = target.startsWith(context.safeCharacter() + "_")
          && target.endsWith("_" + safeGroup);
      if (!exactStable && !exactCurrent && !expressionAlias) continue;

      boolean memberVisible = group.layerIds().stream()
          .map(VnTimelineDiagnostics::selectorSafeName)
          .anyMatch(context.activeLayers()::contains);
      if (!memberVisible) {
        findings.add(new Finding(
            Code.DEFERRED_GROUP_TARGET,
            Severity.WARNING,
            target,
            "Layer group '" + group.id() + "' has no visible member yet; its transform will be pre-armed",
            "Keep this when intentional, or move the timeline after a compatible [show]",
            false));
        return true;
      }
      if (!exactStable && !exactCurrent) {
        findings.add(new Finding(
            Code.STALE_EXPRESSION_ALIAS,
            Severity.WARNING,
            target,
            "Expression-qualified group alias belongs to another composition and is being retained for compatibility",
            "Replace it with the stable group target '" + stableTarget + "'",
            false));
      } else if (exactCurrent && !currentTarget.equals(stableTarget)) {
        findings.add(new Finding(
            Code.EXPRESSION_SPECIFIC_TARGET,
            Severity.WARNING,
            target,
            "Expression-qualified group target only resolves while '" + context.expression() + "' is shown",
            "Prefer the stable group target '" + stableTarget + "'",
            false));
      }
      return true;
    }
    return false;
  }

  private static void addExactLayerScopeWarning(
      String layerId,
      String stableTarget,
      CharacterContext context,
      List<Finding> findings
  ) {
    for (VnCharacter.LayerGroup group : context.character().getLayerGroups().values()) {
      if (group == null || group.layerIds().size() < 2 || !group.containsLayerId(layerId)) continue;
      String groupTarget = context.safeCharacter() + "_" + selectorSafeName(group.id());
      findings.add(new Finding(
          Code.EXACT_LAYER_REPLACEMENT_SCOPE,
          Severity.INFO,
          stableTarget,
          "This exact layer has variants in group '" + group.id()
              + "'; the renderer will infer the replacement lane when the match is unique",
          "Use '" + groupTarget + "' only when you want an explicit override or the inferred lane is ambiguous",
          false));
      return;
    }
  }

  private static void addPersistentLayerStateWarning(
      String layerId,
      String stableTarget,
      CharacterContext context,
      List<Finding> findings
  ) {
    String safeLayer = selectorSafeName(layerId);
    boolean hiddenByAnotherExpression = context.character().getExpressionLayerIdsByName().values().stream()
        .map(layerIds -> layerIds.stream().map(VnTimelineDiagnostics::selectorSafeName).toList())
        .anyMatch(layerIds -> !layerIds.contains(safeLayer));
    if (!hiddenByAnotherExpression) return;
    findings.add(new Finding(
        Code.PERSISTENT_LAYER_STATE,
        Severity.WARNING,
        stableTarget,
        "Layer proxy state persists while absent, follows a uniquely inferred replacement lane, and applies again if this layer returns",
        "Author an explicit reset before reusing $" + layerId
            + " when that persistence is not intended",
        false));
  }

  private static void addTimelineDataFindings(TimelineData data, List<Finding> findings) {
    for (TimelineDataDiagnostics.Message message : TimelineDataDiagnostics.diagnose(data)) {
      Severity severity = switch (message.severity()) {
        case ERROR -> Severity.ERROR;
        case WARNING -> Severity.WARNING;
        case INFO -> Severity.INFO;
      };
      findings.add(new Finding(
          Code.TIMELINE_DATA,
          severity,
          message.target(),
          message.description(),
          message.quickFix(),
          severity == Severity.ERROR));
    }
  }

  private static Map<String, VnState.CharacterSlot> activeSlotsByCharacter(VnState state) {
    Map<String, VnState.CharacterSlot> slots = new LinkedHashMap<>();
    for (VnState.CharacterSlot slot : state.getVisibleCharacters().values()) {
      if (slot != null && slot.getCharacterId() != null && !slot.getCharacterId().isBlank()) {
        slots.put(slot.getCharacterId(), slot);
      }
    }
    for (VnState.DetachedCharacterSlot detached : state.getDetachedCharacters().values()) {
      VnState.CharacterSlot slot = detached == null ? null : detached.getSlot();
      if (slot != null && slot.getCharacterId() != null && !slot.getCharacterId().isBlank()) {
        slots.put(slot.getCharacterId(), slot);
      }
    }
    return slots;
  }

  private static Finding blocking(Code code, String target, String description, String quickFix) {
    return new Finding(code, Severity.ERROR, target, description, quickFix, true);
  }

  static String selectorSafeName(String raw) {
    String value = raw == null ? "" : raw.trim();
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      out.append(Character.isLetterOrDigit(ch) || ch == '_' || ch == '-' ? ch : '_');
    }
    String cleaned = out.toString().replaceAll("_+", "_");
    while (cleaned.startsWith("_")) cleaned = cleaned.substring(1);
    while (cleaned.endsWith("_")) cleaned = cleaned.substring(0, cleaned.length() - 1);
    return cleaned;
  }
}
