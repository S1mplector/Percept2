package com.jvn.editor.ui;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.vn.CharacterPosition;
import com.jvn.core.vn.LayeredCharacterResolver;
import com.jvn.core.vn.VnCharacter;
import com.jvn.core.vn.VnNode;
import com.jvn.core.vn.VnNodeType;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.script.VnScriptParser;
import com.jvn.editor.EditorApp;
import com.jvn.editor.ui.actioneditor.AnimationProject;
import com.jvn.editor.ui.actioneditor.CodeImporter;
import com.jvn.editor.ui.actioneditor.EntityTrack;
import com.jvn.editor.ui.actioneditor.PropertyType;
import com.jvn.scripting.jes.runtime.JesScene2D;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in, project-corpus regression for Puppeteer's Launch-at-Cursor reconstruction.
 *
 * <p>Run with:
 * <pre>
 * WAS_I_WRITE_ROOT=/absolute/path/to/Was_I_Write ./gradlew :editor:test \
 *   --tests com.jvn.editor.ui.PuppeteerWasIWriteCorpusTest
 * </pre>
 *
 * <p>The VNS parser is the independent oracle for command expansion and layered-character
 * declarations. Every physical cursor line is resolved and handed through the real private
 * scene-construction method used by the editor.
 */
@SuppressWarnings("NullAway")
class PuppeteerWasIWriteCorpusTest {
  private static final int MAX_REPORTED_FAILURES = 250;

  @Test
  void everyCursorLineConstructsTheCanonicalSceneWithoutDroppingEntities() throws Exception {
    String configuredRoot = System.getProperty("wasIWriteRoot", "").trim();
    if (configuredRoot.isEmpty()) {
      configuredRoot = System.getenv().getOrDefault("WAS_I_WRITE_ROOT", "").trim();
    }
    Assumptions.assumeTrue(!configuredRoot.isEmpty(),
        "Set WAS_I_WRITE_ROOT=/path/to/Was_I_Write to run the external corpus");
    Path projectRoot = Path.of(configuredRoot).toAbsolutePath().normalize();
    Assumptions.assumeTrue(Files.isRegularFile(projectRoot.resolve("jvn.project")),
        "Was I Write project root not found: " + projectRoot);

    List<Path> scripts;
    try (Stream<Path> stream = Files.walk(projectRoot.resolve("scripts"))) {
      scripts = stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".vns"))
          .sorted(Comparator.comparing(path -> projectRoot.relativize(path).toString()))
          .toList();
    }
    assertTrue(!scripts.isEmpty(), "No .vns files found under " + projectRoot.resolve("scripts"));

    EditorApp app = new EditorApp();
    Field projectRootField = EditorApp.class.getDeclaredField("projectRoot");
    projectRootField.setAccessible(true);
    projectRootField.set(app, projectRoot.toFile());
    Method buildScene = EditorApp.class.getDeclaredMethod(
        "buildSceneFromSnapshot", PuppeteerLauncherPanel.SceneSnapshot.class);
    buildScene.setAccessible(true);
    Method captureBaselines = EditorApp.class.getDeclaredMethod(
        "captureRuntimeExportBaselines", JesScene2D.class);
    captureBaselines.setAccessible(true);
    Method applyTimelineEndState = EditorApp.class.getDeclaredMethod(
        "applySnapshotTimelineEndStateToScene",
        JesScene2D.class,
        PuppeteerLauncherPanel.SceneSnapshot.class,
        String.class,
        Map.class);
    applyTimelineEndState.setAccessible(true);

    List<String> failures = new ArrayList<>();
    int cursorCount = 0;
    int constructedSceneCount = 0;
    for (Path script : scripts) {
      String source = Files.readString(script, StandardCharsets.UTF_8);
      VnScenario canonical;
      try {
        canonical = parseCanonical(projectRoot, script, source);
      } catch (Exception ex) {
        failures.add(relative(projectRoot, script) + ": canonical VNS parse failed: " + rootMessage(ex));
        continue;
      }

      PuppeteerLauncherPanel.SceneSnapshot eofSnapshot = resolve(projectRoot, script, source,
          Math.max(0, source.split("\n", -1).length - 1));
      validateDeclarations(projectRoot, script, canonical, eofSnapshot, failures);

      Map<Integer, List<VnNode>> nodesByLine = new LinkedHashMap<>();
      for (VnNode node : canonical.getNodes()) {
        nodesByLine.computeIfAbsent(node.getSourceLine(), ignored -> new ArrayList<>()).add(node);
      }
      CanonicalScene expected = new CanonicalScene();
      String[] lines = source.split("\n", -1);
      for (int cursor = 0; cursor < lines.length; cursor++) {
        cursorCount++;
        for (VnNode node : nodesByLine.getOrDefault(cursor + 1, List.of())) {
          expected.apply(node);
        }

        PuppeteerLauncherPanel.SceneSnapshot snapshot;
        try {
          snapshot = resolve(projectRoot, script, source, cursor);
        } catch (Exception ex) {
          addFailure(failures, projectRoot, script, cursor,
              "snapshot resolution threw " + rootMessage(ex));
          continue;
        }
        compareSnapshot(projectRoot, script, cursor, canonical, expected, snapshot, failures);

        try {
          JesScene2D scene = (JesScene2D) buildScene.invoke(app, snapshot);
          constructedSceneCount++;
          compareConstructedScene(projectRoot, script, cursor, snapshot, scene, failures);
          compareGroupedTimelineReplayAtBlockEnd(
              projectRoot, script, cursor, snapshot, scene, app,
              captureBaselines, applyTimelineEndState, failures);
        } catch (Exception ex) {
          addFailure(failures, projectRoot, script, cursor,
              "launch scene construction threw " + rootMessage(ex));
        }
      }
    }

    String summary = "Checked " + cursorCount + " cursor lines and constructed "
        + constructedSceneCount + " launch scenes across " + scripts.size() + " VNS files.";
    assertTrue(failures.isEmpty(), summary + "\n" + String.join("\n", failures));
    System.out.println(summary);
  }

  private static VnScenario parseCanonical(Path projectRoot, Path script, String source) throws IOException {
    VnScriptParser parser = new VnScriptParser();
    return parser.parse(
        new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)),
        script.toString(),
        includePath -> Files.newInputStream(resolveInclude(projectRoot, script, includePath)));
  }

  private static PuppeteerLauncherPanel.SceneSnapshot resolve(
      Path projectRoot, Path script, String source, int cursor) {
    return PuppeteerLauncherPanel.resolveSnapshot(
        source,
        cursor,
        script.toString(),
        (sourceName, includePath) -> {
          Path sourcePath = sourceName == null || sourceName.isBlank()
              ? script
              : Path.of(sourceName).toAbsolutePath().normalize();
          Path included = resolveInclude(projectRoot, sourcePath, includePath);
          return new PuppeteerLauncherPanel.ResolvedInclude(
              included.toString(), Files.readString(included, StandardCharsets.UTF_8));
        });
  }

  private static Path resolveInclude(Path projectRoot, Path sourceFile, String includePath)
      throws IOException {
    String normalized = includePath == null ? "" : includePath.trim().replace('\\', '/');
    List<Path> candidates = new ArrayList<>();
    if (normalized.startsWith("/")) {
      candidates.add(projectRoot.resolve("scripts").resolve(normalized.substring(1)));
    } else {
      Path parent = sourceFile == null ? null : sourceFile.toAbsolutePath().normalize().getParent();
      if (parent != null) candidates.add(parent.resolve(normalized));
      candidates.add(projectRoot.resolve("scripts").resolve(normalized));
      candidates.add(projectRoot.resolve(normalized));
    }
    for (Path candidate : candidates) {
      Path resolved = candidate.toAbsolutePath().normalize();
      if (resolved.startsWith(projectRoot) && Files.isRegularFile(resolved)) return resolved;
    }
    throw new IOException("Included script not found: " + includePath + " from " + sourceFile);
  }

  private static void validateDeclarations(
      Path projectRoot,
      Path script,
      VnScenario canonical,
      PuppeteerLauncherPanel.SceneSnapshot snapshot,
      List<String> failures) {
    for (Map.Entry<String, String> background : snapshot.backgroundPaths.entrySet()) {
      if (canonical.getBackground(background.getKey()) == null) {
        failures.add(relative(projectRoot, script) + ": background declaration missing from canonical parser: "
            + background.getKey());
      }
      validateAsset(projectRoot, script, "background " + background.getKey(), background.getValue(), failures);
    }
    for (VnCharacter character : canonical.getCharacters().values()) {
      for (Map.Entry<String, String> layer : character.getLayerPaths().entrySet()) {
        validateAsset(projectRoot, script,
            "charlayer " + character.getId() + "/" + layer.getKey(), layer.getValue(), failures);
      }
      for (Map.Entry<String, List<String>> preset : character.getExpressionLayerIdsByName().entrySet()) {
        List<String> expectedPaths = splitPathSpec(character.getExpressionPath(preset.getKey()));
        List<String> actualPaths = splitPathSpec(snapshot.resolveCharacterPath(character.getId(), preset.getKey()));
        if (!expectedPaths.equals(actualPaths)) {
          failures.add(relative(projectRoot, script) + ": charpreset " + character.getId() + "/"
              + preset.getKey() + " expected paths " + expectedPaths + " but snapshot resolved " + actualPaths);
        }
      }
      for (Map.Entry<String, VnCharacter.LayerGroup> group : character.getLayerGroups().entrySet()) {
        PuppeteerLauncherPanel.CharacterLayerGroupEntry actual =
            snapshot.resolveCharacterLayerGroup(character.getId(), group.getKey());
        if (actual == null) {
          failures.add(relative(projectRoot, script) + ": chargroup missing from snapshot: "
              + character.getId() + "/" + group.getKey());
          continue;
        }
        VnCharacter.LayerGroup expected = group.getValue();
        List<String> expectedPaths = groupPaths(character, expected.layerIds());
        List<String> actualPaths = groupPaths(character, actual.layerIds);
        if (!expectedPaths.equals(actualPaths)
            || !Objects.equals(expected.parentId(), actual.parentGroupId)
            || expected.hasPivot() != actual.hasPivot
            || Math.abs(expected.pivotX() - actual.pivotX) > 1e-9
            || Math.abs(expected.pivotY() - actual.pivotY) > 1e-9) {
          failures.add(relative(projectRoot, script) + ": chargroup " + character.getId() + "/"
              + group.getKey() + " differs: expected paths=" + expectedPaths + ", snapshot paths=" + actualPaths
              + " parent=" + actual.parentGroupId + " pivot=" + actual.pivotX + "," + actual.pivotY
              + " hasPivot=" + actual.hasPivot);
        }
      }
    }
  }

  private static void compareSnapshot(
      Path projectRoot,
      Path script,
      int cursor,
      VnScenario canonical,
      CanonicalScene expected,
      PuppeteerLauncherPanel.SceneSnapshot snapshot,
      List<String> failures) {
    if (!Objects.equals(expected.backgroundId, snapshot.backgroundId)) {
      addFailure(failures, projectRoot, script, cursor,
          "background expected=" + expected.backgroundId + " actual=" + snapshot.backgroundId);
    }
    if (!Objects.equals(expected.previousBackgroundId, snapshot.previousBackgroundId)) {
      addFailure(failures, projectRoot, script, cursor,
          "previous background expected=" + expected.previousBackgroundId
              + " actual=" + snapshot.previousBackgroundId);
    }

    Map<String, PuppeteerLauncherPanel.CharacterEntry> actualByKey = new LinkedHashMap<>();
    for (PuppeteerLauncherPanel.CharacterEntry entry : snapshot.characters) {
      actualByKey.put(sceneKey(entry.characterId, entry.displaySlot), entry);
    }
    if (!expected.visible.keySet().equals(actualByKey.keySet())) {
      addFailure(failures, projectRoot, script, cursor,
          "visible keys expected=" + expected.visible.keySet() + " actual=" + actualByKey.keySet());
    }
    for (Map.Entry<String, CanonicalCharacter> expectedEntry : expected.visible.entrySet()) {
      CanonicalCharacter wanted = expectedEntry.getValue();
      PuppeteerLauncherPanel.CharacterEntry actual = actualByKey.get(expectedEntry.getKey());
      if (actual == null) continue;
      if (!Objects.equals(wanted.characterId, actual.characterId)
          || !Objects.equals(emptyToNull(wanted.displaySlot), emptyToNull(actual.displaySlot))) {
        addFailure(failures, projectRoot, script, cursor,
            expectedEntry.getKey() + " identity expected=" + wanted.characterId + "/" + wanted.displaySlot
                + " actual=" + actual.characterId + "/" + actual.displaySlot);
      }
      comparePosition(failures, projectRoot, script, cursor, expectedEntry.getKey(), wanted.position, actual);
      if (!Objects.equals(wanted.layerOrder, actual.layerOrder)) {
        addFailure(failures, projectRoot, script, cursor,
            expectedEntry.getKey() + " z expected=" + wanted.layerOrder + " actual=" + actual.layerOrder);
      }
      VnCharacter canonicalCharacter = canonical.getCharacter(wanted.characterId);
      if (canonicalCharacter != null) {
        List<String> expectedPaths = splitPathSpec(canonicalCharacter.getExpressionPath(wanted.expression));
        List<String> actualPaths = splitPathSpec(snapshot.resolveCharacterPath(actual.characterId, actual.expression));
        if (!expectedPaths.equals(actualPaths)) {
          addFailure(failures, projectRoot, script, cursor,
              expectedEntry.getKey() + " layer paths expected=" + expectedPaths + " actual=" + actualPaths
                  + " (expressions " + wanted.expression + " / " + actual.expression + ")");
        }
        if (Math.abs(canonicalCharacter.getScale() - actual.scale) > 1e-9) {
          addFailure(failures, projectRoot, script, cursor,
              expectedEntry.getKey() + " scale expected=" + canonicalCharacter.getScale()
                  + " actual=" + actual.scale);
        }
      }
    }
  }

  private static void comparePosition(
      List<String> failures,
      Path projectRoot,
      Path script,
      int cursor,
      String key,
      CharacterPosition expected,
      PuppeteerLauncherPanel.CharacterEntry actual) {
    if (expected == null) return;
    CharacterPosition base = expected.getBasePosition();
    if (base.isCustom()) {
      if (!actual.customPosition
          || Math.abs(base.getXFraction() - actual.positionX) > 1e-9
          || Math.abs(base.getYFraction() - actual.positionY) > 1e-9) {
        addFailure(failures, projectRoot, script, cursor,
            key + " position expected=" + base + " actual=" + actual.position + "("
                + actual.positionX + "," + actual.positionY + ")");
      }
      return;
    }
    String expectedName = base.getName().toLowerCase(Locale.ROOT);
    if (!expectedName.equals(actual.position)) {
      addFailure(failures, projectRoot, script, cursor,
          key + " position expected=" + expectedName + " actual=" + actual.position);
    }
  }

  private static void compareConstructedScene(
      Path projectRoot,
      Path script,
      int cursor,
      PuppeteerLauncherPanel.SceneSnapshot snapshot,
      JesScene2D scene,
      List<String> failures) {
    if (snapshot.backgroundId != null && scene.find("bg_current") == null) {
      addFailure(failures, projectRoot, script, cursor, "constructed scene dropped current background");
    }
    if (snapshot.previousBackgroundId != null && scene.find("bg_prev") == null) {
      addFailure(failures, projectRoot, script, cursor, "constructed scene dropped previous background");
    }
    for (PuppeteerLauncherPanel.CharacterEntry character : snapshot.characters) {
      List<PuppeteerLauncherPanel.CharacterLayerEntry> layers =
          snapshot.resolveCharacterLayers(character.characterId, character.expression);
      if (layers.isEmpty()) {
        if (scene.find(character.characterId) == null) {
          addFailure(failures, projectRoot, script, cursor,
              "constructed scene dropped entity " + character.characterId);
        }
        continue;
      }
      for (PuppeteerLauncherPanel.CharacterLayerEntry layer : layers) {
        String name = PuppeteerLauncherPanel.snapshotStableLayerEntityName(
            character.characterId, layer.layerId);
        Entity2D entity = scene.find(name);
        if (entity == null) {
          addFailure(failures, projectRoot, script, cursor,
              "constructed scene dropped layer entity " + name);
        }
      }
    }
  }

  private static void compareGroupedTimelineReplayAtBlockEnd(
      Path projectRoot,
      Path script,
      int cursor,
      PuppeteerLauncherPanel.SceneSnapshot snapshot,
      JesScene2D scene,
      EditorApp app,
      Method captureBaselines,
      Method applyTimelineEndState,
      List<String> failures) throws Exception {
    if (snapshot == null || !snapshot.hasInlineTimelineHistory()) return;
    PuppeteerLauncherPanel.InlineTimelineContext context = snapshot.inlineTimelineHistory.get(
        snapshot.inlineTimelineHistory.size() - 1);
    if (context == null || context.endLine() != cursor) return;

    AnimationProject timeline = CodeImporter.importCode(
        "corpus_inline_" + (context.startLine() + 1),
        "timeline {\n" + context.body().strip() + "\n}\n");
    Map<String, List<Entity2D>> groupMembersByTarget = new LinkedHashMap<>();
    for (PuppeteerLauncherPanel.CharacterEntry character : snapshot.characters) {
      List<PuppeteerLauncherPanel.CharacterLayerEntry> visibleLayers =
          snapshot.resolveCharacterLayers(character.characterId, character.expression);
      List<PuppeteerLauncherPanel.CharacterLayerGroupEntry> visibleGroups =
          snapshot.resolveCharacterLayerGroups(character.characterId, character.expression);
      for (PuppeteerLauncherPanel.CharacterLayerGroupEntry group : visibleGroups) {
        List<Entity2D> members = new ArrayList<>();
        for (PuppeteerLauncherPanel.CharacterLayerEntry layer : visibleLayers) {
          if (!activeGroupChain(layer.layerId, visibleGroups).contains(group)) continue;
          Entity2D member = scene.find(PuppeteerLauncherPanel.snapshotStableLayerEntityName(
              character.characterId, layer.layerId));
          if (member != null && !members.contains(member)) members.add(member);
        }
        for (String target : PuppeteerLauncherPanel.equivalentSnapshotLayerGroupEntityNames(
            snapshot, character, group.groupId)) {
          groupMembersByTarget.put(target, members);
        }
      }
    }

    Map<String, List<EntityState>> before = new LinkedHashMap<>();
    for (EntityTrack track : timeline.getTracks()) {
      List<Entity2D> members = groupMembersByTarget.get(track.getEntityName());
      if (members == null || members.isEmpty()
          || !hasNonIdentitySpatialEnd(track, timeline.getTotalDurationMs())) continue;
      before.put(track.getEntityName(), members.stream().map(EntityState::capture).toList());
    }
    if (before.isEmpty()) return;

    @SuppressWarnings("unchecked")
    Map<String, Map<PropertyType, Double>> baselines =
        (Map<String, Map<PropertyType, Double>>) captureBaselines.invoke(app, scene);
    applyTimelineEndState.invoke(app, scene, snapshot, null, baselines);

    for (Map.Entry<String, List<EntityState>> checked : before.entrySet()) {
      List<Entity2D> members = groupMembersByTarget.getOrDefault(checked.getKey(), List.of());
      boolean anyChanged = false;
      for (int i = 0; i < Math.min(checked.getValue().size(), members.size()); i++) {
        if (!checked.getValue().get(i).sameAs(members.get(i))) {
          anyChanged = true;
          break;
        }
      }
      if (!anyChanged) {
        addFailure(failures, projectRoot, script, cursor,
            "inline timeline target '" + checked.getKey()
                + "' did not apply its end-state transform to any visible chargroup member");
      }
    }
  }

  private static List<PuppeteerLauncherPanel.CharacterLayerGroupEntry> activeGroupChain(
      String layerId,
      List<PuppeteerLauncherPanel.CharacterLayerGroupEntry> groups) {
    PuppeteerLauncherPanel.CharacterLayerGroupEntry deepest = null;
    int deepestDepth = -1;
    for (PuppeteerLauncherPanel.CharacterLayerGroupEntry candidate : groups) {
      if (candidate == null || !candidate.layerIds.contains(layerId)) continue;
      int depth = groupDepth(candidate, groups, new LinkedHashSet<>());
      if (depth > deepestDepth) {
        deepest = candidate;
        deepestDepth = depth;
      }
    }
    if (deepest == null) return List.of();
    List<PuppeteerLauncherPanel.CharacterLayerGroupEntry> chain = new ArrayList<>();
    Set<String> seen = new LinkedHashSet<>();
    PuppeteerLauncherPanel.CharacterLayerGroupEntry current = deepest;
    while (current != null && seen.add(current.groupId)) {
      chain.add(0, current);
      current = groupById(groups, current.parentGroupId);
    }
    return List.copyOf(chain);
  }

  private static int groupDepth(
      PuppeteerLauncherPanel.CharacterLayerGroupEntry group,
      List<PuppeteerLauncherPanel.CharacterLayerGroupEntry> groups,
      Set<String> seen) {
    if (group == null || !seen.add(group.groupId)) return 0;
    PuppeteerLauncherPanel.CharacterLayerGroupEntry parent = groupById(groups, group.parentGroupId);
    return parent == null ? 0 : 1 + groupDepth(parent, groups, seen);
  }

  private static PuppeteerLauncherPanel.CharacterLayerGroupEntry groupById(
      List<PuppeteerLauncherPanel.CharacterLayerGroupEntry> groups, String groupId) {
    if (groupId == null || groupId.isBlank()) return null;
    for (PuppeteerLauncherPanel.CharacterLayerGroupEntry group : groups) {
      if (group != null && groupId.equals(group.groupId)) return group;
    }
    return null;
  }

  private static boolean hasNonIdentitySpatialEnd(EntityTrack track, double timeMs) {
    if (track == null) return false;
    if (track.hasKeyframes(PropertyType.X) && Math.abs(track.getValueAt(PropertyType.X, timeMs)) > 1e-9) return true;
    if (track.hasKeyframes(PropertyType.Y) && Math.abs(track.getValueAt(PropertyType.Y, timeMs)) > 1e-9) return true;
    if (track.hasKeyframes(PropertyType.ROTATION)
        && Math.abs(track.getValueAt(PropertyType.ROTATION, timeMs)) > 1e-9) return true;
    // Repeated mirror blocks are multiplicative in the canonical runtime, so a non-zero final
    // mirror keyframe does not by itself prove the complete history should differ from baseline.
    // Move/rotate/scale tracks provide the unambiguous group-propagation oracle here.
    if (track.hasKeyframes(PropertyType.SCALE_X)
        && Math.abs(track.getValueAt(PropertyType.SCALE_X, timeMs) - 1.0) > 1e-9) return true;
    return track.hasKeyframes(PropertyType.SCALE_Y)
        && Math.abs(track.getValueAt(PropertyType.SCALE_Y, timeMs) - 1.0) > 1e-9;
  }

  private static void validateAsset(
      Path projectRoot, Path script, String declaration, String pathSpec, List<String> failures) {
    if (pathSpec == null || pathSpec.isBlank()) return;
    for (String raw : pathSpec.split("\\|")) {
      String normalized = raw.trim().replace('\\', '/');
      if (normalized.isBlank() || normalized.contains("://")) continue;
      Path asset = Path.of(normalized);
      if (!asset.isAbsolute()) asset = projectRoot.resolve(normalized);
      if (!Files.isRegularFile(asset.normalize())) {
        failures.add(relative(projectRoot, script) + ": " + declaration
            + " points to missing asset " + normalized);
      }
    }
  }

  private static List<String> splitPathSpec(String pathSpec) {
    if (pathSpec == null || pathSpec.isBlank()) return List.of();
    return Stream.of(pathSpec.split("\\|"))
        .map(String::trim)
        .filter(path -> !path.isEmpty())
        .toList();
  }

  private static List<String> groupPaths(VnCharacter character, List<String> layerIds) {
    if (character == null || layerIds == null || layerIds.isEmpty()) return List.of();
    Map<String, Map<String, String>> layers = Map.of(character.getId(), character.getLayerPaths());
    List<String> paths = new ArrayList<>();
    for (String layerId : layerIds) {
      String path = LayeredCharacterResolver.resolveLayerPath(layers, character.getId(), layerId);
      if (path != null && !path.isBlank() && !paths.contains(path)) paths.add(path);
    }
    return List.copyOf(paths);
  }

  private static String sceneKey(String characterId, String displaySlot) {
    return displaySlot == null || displaySlot.isBlank()
        ? "character:" + characterId
        : "slot:" + displaySlot;
  }

  private static String emptyToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static String relative(Path projectRoot, Path script) {
    return projectRoot.relativize(script.toAbsolutePath().normalize()).toString().replace(File.separatorChar, '/');
  }

  private static void addFailure(
      List<String> failures, Path projectRoot, Path script, int cursor, String detail) {
    if (failures.size() >= MAX_REPORTED_FAILURES) return;
    failures.add(relative(projectRoot, script) + ":" + (cursor + 1) + ": " + detail);
  }

  private static String rootMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) current = current.getCause();
    String message = current.getMessage();
    return current.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
  }

  private static final class CanonicalScene {
    private String backgroundId;
    private String previousBackgroundId;
    private final Map<String, CanonicalCharacter> visible = new LinkedHashMap<>();

    private void apply(VnNode node) {
      if (node == null) return;
      switch (node.getType()) {
        case BACKGROUND -> setBackground(node.getBackgroundId());
        case TRANSITION -> {
          if (node.getTransition() != null) setBackground(node.getTransition().getTargetBackgroundId());
        }
        case SHOW -> {
          CanonicalCharacter character = new CanonicalCharacter(
              node.getCharacterToShow(), node.getDisplaySlot(), node.getShowPosition(),
              node.getShowExpression(), node.getShowLayerOrder());
          visible.put(sceneKey(character.characterId, character.displaySlot), character);
        }
        case HIDE -> {
          String slot = emptyToNull(node.getDisplaySlot());
          if (slot != null) {
            visible.remove(sceneKey(null, slot));
          } else if (node.getCharacterToHide() != null) {
            visible.values().removeIf(character -> node.getCharacterToHide().equals(character.characterId));
          }
        }
        case MOVE -> {
          String key = emptyToNull(node.getDisplaySlot()) != null
              ? sceneKey(null, node.getDisplaySlot())
              : sceneKey(node.getCharacterToShow(), null);
          CanonicalCharacter old = visible.get(key);
          if (old != null) {
            visible.put(key, new CanonicalCharacter(
                old.characterId,
                old.displaySlot,
                node.getShowPosition() == null ? old.position : node.getShowPosition(),
                node.getShowExpression() == null ? old.expression : node.getShowExpression(),
                old.layerOrder));
          }
        }
        default -> {
        }
      }
    }

    private void setBackground(String id) {
      if (id == null) return;
      previousBackgroundId = backgroundId;
      backgroundId = id;
    }
  }

  private record CanonicalCharacter(
      String characterId,
      String displaySlot,
      CharacterPosition position,
      String expression,
      Integer layerOrder) {
  }

  private record EntityState(double x, double y, double rotation, double scaleX, double scaleY) {
    private static EntityState capture(Entity2D entity) {
      return new EntityState(
          entity.getX(), entity.getY(), entity.getRotationDeg(), entity.getScaleX(), entity.getScaleY());
    }

    private boolean sameAs(Entity2D entity) {
      return Math.abs(x - entity.getX()) <= 1e-9
          && Math.abs(y - entity.getY()) <= 1e-9
          && Math.abs(rotation - entity.getRotationDeg()) <= 1e-9
          && Math.abs(scaleX - entity.getScaleX()) <= 1e-9
          && Math.abs(scaleY - entity.getScaleY()) <= 1e-9;
    }
  }
}
