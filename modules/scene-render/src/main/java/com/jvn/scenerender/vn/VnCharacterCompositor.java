package com.jvn.scenerender.vn;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.jvn.core.animation.TimelineDrivenEntity;
import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.BoundedImageCache;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.scene2d.RenderDiagnostics;
import com.jvn.core.scene2d.RenderTarget2D;
import com.jvn.core.scene2d.Sprite2D;
import com.jvn.core.vn.CharacterPosition;
import com.jvn.core.vn.EyeFocusResolver;
import com.jvn.core.vn.LayeredCharacterResolver;
import com.jvn.core.vn.VnCharacter;
import com.jvn.core.vn.VnCharacterSceneAccessor;
import com.jvn.core.vn.VnEyeFocusProfile;
import com.jvn.core.vn.VnEyeFocusProfileStore;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.VnState;
import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.scenerender.assets.AssetDimensionProbe;
import org.jspecify.annotations.Nullable;

/**
 * Character sprite compositing/drawing collaborator ported from the original monolithic
 * {@code VnRenderer} (JavaFX {@code GraphicsContext}-bound) onto the platform-agnostic
 * {@link Blitter2D} drawing abstraction.
 *
 * <p>Covers character draw ordering, expression crossfades (both whole-sprite and per-layer),
 * timeline/eye-focus-driven independent layer drawing, sprite layout math, and multi-layer
 * composite sprite construction.
 *
 * <h2>Port notes</h2>
 * <ul>
 *   <li><b>Offscreen compositing replaces {@code Canvas.snapshot}.</b> The original built a
 *   multi-layer sprite by drawing N source {@code Image}s into a {@code Canvas} and snapshotting
 *   to a {@code WritableImage}. This port builds a {@link RenderTarget2D} via
 *   {@link Blitter2D#createRenderTarget} and draws each resolved layer path into it, caching the
 *   target in {@link #compositeSpriteCache}. The cache is wired with the eviction listener so a
 *   dropped target's backend resources are released ({@code RenderTarget2D extends AutoCloseable}).</li>
 *   <li><b>Dimension-only reads no longer force compositing.</b> Every former
 *   {@code image.getWidth()}/{@code getHeight()} read that only fed layout math now goes through
 *   {@link AssetDimensionProbe}, mirroring the original's own split between {@code firstAvailableImage}
 *   (size probing) and {@code loadSpriteSourceImage} (full compositing).</li>
 *   <li><b>Image "is loaded" checks become dimension-probe null checks.</b> The original tested a
 *   loaded {@code Image} for non-null/non-error/positive-size; here a non-null {@code double[]}
 *   from the probe carries the identical "this asset resolves" semantics.</li>
 *   <li><b>Rounded-rect placeholder silhouettes draw as square rects.</b> {@link Blitter2D} has no
 *   rounded-rect primitive, matching the precedent set by sibling collaborators.</li>
 * </ul>
 */
final class VnCharacterCompositor {

  /**
   * 96 MiB, matching the original {@code modules/fx} {@code VnRenderer}'s
   * {@code COMPOSITE_SPRITE_CACHE_BUDGET_BYTES}. Declared here rather than read off the
   * {@code VnRenderer} facade so this collaborator has no dependency on that (later-built) class.
   */
  private static final long COMPOSITE_SPRITE_CACHE_BUDGET_BYTES = 96L * 1024L * 1024L;

  private static final int MAX_CACHED_LAYER_PATH_SPECS = 256;

  /**
   * Position is bucketed to this grid (in scene pixels) when building the stage-lighting cache
   * key, so idle-bob/breathing jitter of a few pixels reuses the last lit bitmap instead of
   * forcing a full per-pixel relight every frame.
   */
  private static final double LIGHTING_CACHE_POSITION_GRID_PX = 4.0;

  private final Blitter2D blitter;
  private final AssetCatalog assetCatalog = new AssetCatalog();

  // Completed composites are kept apart from transient per-layer working data so full-canvas
  // layers cannot evict the composite they just built, which would force a rebuild every frame.
  private final BoundedImageCache<RenderTarget2D> compositeSpriteCache = new BoundedImageCache<>(
      64, COMPOSITE_SPRITE_CACHE_BUDGET_BYTES, VnCharacterCompositor::renderTargetWeightBytes,
      (key, target) -> target.close());

  // Independently transformed layers are a live render working set, not ordinary reusable source
  // data. Keep exactly the layers used by consecutive frames resident so two large layered
  // characters cannot evict and synchronously reload one another every frame.
  private final FrameRetainedCache<double[]> timelineLayerWorkingSet = new FrameRetainedCache<>();

  private final Map<String, List<String>> layerPathCache = new HashMap<>();
  private final List<CharacterRenderEntry> reusableCharacterEntries = new ArrayList<>();

  private @Nullable VnCharacterSceneAccessor timelineAccessor;
  private @Nullable Map<String, VnEyeFocusProfile> eyeFocusProfiles;
  // Unannotated (rather than @Nullable) to match MenuBackgroundRenderer's established convention:
  // AssetDimensionProbe.dimensionsOf's projectRoot parameter is itself unannotated even though it
  // null-checks internally, so a @Nullable field here trips NullAway at every call site.
  private File projectRoot;
  private @Nullable StageLitCharacterDrawer stageLightingRenderer;

  private double characterHeightFactor = 0.85;
  private double characterBaselineY = 1.0;

  VnCharacterCompositor(Blitter2D blitter) {
    this.blitter = blitter;
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Collaborator wiring
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Stage-lit character drawing, owned by {@code VnStageLightingRenderer}. Declared as a narrow
   * functional interface rather than a direct type reference so this collaborator neither depends
   * on nor forward-references that class; the facade wires the real implementation in.
   *
   * <p>The implementor owns the lit-bitmap cache keyed by
   * {@link VnCharacterCompositor#stageCharacterCacheKey}, which stays here as a pure, separately
   * tested function.
   */
  interface StageLitCharacterDrawer {
    void drawLitCharacter(
        String path,
        String spriteTag,
        double x,
        double y,
        double drawWidth,
        double drawHeight,
        double canvasWidth,
        double canvasHeight,
        @Nullable VnStagePreset stage);

    /**
     * Draws an already-composited multi-layer sprite with stage lighting applied. The {@code
     * composite} target is owned by the caller and must not be closed by the implementation.
     */
    void drawLitComposite(
        RenderTarget2D composite,
        String spriteTag,
        double x,
        double y,
        double drawWidth,
        double drawHeight,
        double canvasWidth,
        double canvasHeight,
        @Nullable VnStagePreset stage);
  }

  void setStageLightingRenderer(@Nullable StageLitCharacterDrawer renderer) {
    this.stageLightingRenderer = renderer;
  }

  void setTimelineAccessor(@Nullable VnCharacterSceneAccessor accessor) {
    this.timelineAccessor = accessor;
  }

  void setProjectRoot(File root) {
    if (java.util.Objects.equals(this.projectRoot, root)) return;
    this.projectRoot = root;
    this.eyeFocusProfiles = null;
    clearCache();
  }

  void setCharacterFraming(double heightFactor, double baselineY) {
    this.characterHeightFactor = heightFactor;
    this.characterBaselineY = baselineY;
  }

  void clearCache() {
    compositeSpriteCache.clear();
    timelineLayerWorkingSet.clear();
    layerPathCache.clear();
  }

  void beginFrame() {
    timelineLayerWorkingSet.beginFrame();
  }

  void endFrame() {
    timelineLayerWorkingSet.endFrame();
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Record carriers
  // ─────────────────────────────────────────────────────────────────────────

  private record SpriteLayer(
      String path,
      String layerId,
      List<String> targetNames,
      List<GroupLayerTarget> groupTargets,
      boolean resolved
  ) {
  }

  private record GroupLayerTarget(
      VnCharacter.LayerGroup group,
      List<String> targetNames
  ) {
  }

  private record LayerTransformTarget(
      Entity2D proxy,
      VnCharacter.@Nullable LayerGroup group,
      boolean exact
  ) {
  }

  record CharacterRenderEntry(
      CharacterPosition position,
      VnState.CharacterSlot slot,
      VnState.CharacterVisual visual,
      int order
  ) {
  }

  record LayerDrawPlanEntry(String layerId, String path, double alpha) {}

  record SpriteLayout(double width, double height, double baselineY, boolean canvasAligned) {}

  private record EyeFocusDraw(
      boolean active,
      String selectedLayerId,
      String selectedPath,
      String selectedTargetName,
      String replacementSlotLayerId,
      double nudgeX,
      double nudgeY,
      Set<String> mappedLayerIds
  ) {
    static EyeFocusDraw inactive() {
      return new EyeFocusDraw(false, "", "", "", "", 0.0, 0.0, Set.of());
    }

    boolean isMappedLayer(String layerId) {
      return layerId != null && mappedLayerIds.contains(layerId);
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Draw ordering
  // ─────────────────────────────────────────────────────────────────────────

  List<CharacterRenderEntry> orderedCharacterEntries(VnState state) {
    Map<CharacterPosition, VnState.CharacterSlot> characters = state.getVisibleCharacters();

    reusableCharacterEntries.clear();
    for (Map.Entry<CharacterPosition, VnState.CharacterSlot> entry : characters.entrySet()) {
      reusableCharacterEntries.add(new CharacterRenderEntry(
          entry.getKey(),
          entry.getValue(),
          state.getCharacterVisual(entry.getKey()),
          positionOrdinal(entry.getKey())));
    }
    int detachedOrder = 1_000;
    for (VnState.DetachedCharacterSlot detached : state.getDetachedCharacters().values()) {
      if (detached == null || detached.getSlot() == null) continue;
      CharacterPosition basePosition = detached.getBasePosition();
      reusableCharacterEntries.add(new CharacterRenderEntry(
          basePosition,
          detached.getSlot(),
          detached.getVisual(),
          positionOrdinal(basePosition) + detachedOrder++));
    }
    reusableCharacterEntries.sort(
        java.util.Comparator
            .comparingInt((CharacterRenderEntry e) ->
                e.slot() != null ? e.slot().getLayerOrder() : 0)
            .thenComparingInt(CharacterRenderEntry::order)
    );
    return reusableCharacterEntries;
  }

  private int positionOrdinal(@Nullable CharacterPosition position) {
    if (position == null) return 0;
    return position.getOrdinal();
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Main entry point
  // ─────────────────────────────────────────────────────────────────────────

  void renderCharacterEntry(
      CharacterRenderEntry entry,
      VnState state,
      VnScenario scenario,
      VnStagePreset stage,
      double width,
      double height) {
    if (entry == null) return;
    CharacterPosition position = entry.position();
    VnState.CharacterSlot slot = entry.slot();
    if (slot == null) return;
    VnState.CharacterVisual visual = entry.visual();
    double alpha = visual != null ? visual.getAlpha() : 1.0;
    double offsetX = visual != null ? visual.getOffsetX() : 0.0;
    double offsetY = visual != null ? visual.getOffsetY() : 0.0;

    VnCharacter character = scenario.getCharacter(slot.getCharacterId());
    if (character != null) {
      String expression = slot.getExpression();
      String imagePath = character.getExpressionPath(expression);
      VnState.ExpressionTransition transition = state != null ? state.getExpressionTransition(slot) : null;
      if (transition != null && transition.appliesTo(expression)) {
        LayeredCharacterResolver.ExpressionLayerDiff layerDiff = transition.getLayerDiff();
        if (layerDiff != null) {
          renderLayeredExpressionCrossfade(layerDiff, transition, character, position,
              width, height, offsetX, offsetY, slot.getCharacterId(), state, scenario, stage, alpha);
          return;
        }
        String fromPath = character.getExpressionPath(transition.getFromExpression());
        String toPath = character.getExpressionPath(transition.getToExpression());
        if (fromPath != null && toPath != null) {
          preloadSpriteSource(fromPath);
          preloadSpriteSource(toPath);
          double progress = transition.getProgress();
          renderCharacterSpriteWithAlpha(
              fromPath, transition.getFromExpression(), character, position,
              width, height, offsetX, offsetY, slot.getCharacterId(), state, scenario, stage,
              alpha * (1.0 - progress));
          renderCharacterSpriteWithAlpha(
              toPath, transition.getToExpression(), character, position,
              width, height, offsetX, offsetY, slot.getCharacterId(), state, scenario, stage,
              alpha * progress);
          return;
        }
      }
      if (imagePath != null) {
        renderCharacterSpriteWithAlpha(imagePath, expression, character, position,
            width, height, offsetX, offsetY, slot.getCharacterId(), state, scenario, stage, alpha);
      }
    }
  }

  private void preloadSpriteSource(String imagePath) {
    if (imagePath == null || imagePath.isBlank()) return;
    List<String> layerPaths = layerPathsFor(imagePath);
    compositeSpriteFor(imagePath, layerPaths);
  }

  private void renderCharacterSpriteWithAlpha(
      String imagePath,
      String expression,
      VnCharacter character,
      CharacterPosition position,
      double width,
      double height,
      double offsetX,
      double offsetY,
      String characterId,
      VnState state,
      VnScenario scenario,
      VnStagePreset stage,
      double alpha) {
    if (imagePath == null || alpha <= 0.001) return;
    blitter.push();
    if (alpha < 0.999) blitter.setGlobalAlpha(alpha);
    applyGroupTransforms(characterId, state);
    renderCharacterSprite(imagePath, expression, character, position, width, height, offsetX, offsetY,
        characterId, state, scenario, stage);
    blitter.pop();
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Layered expression crossfade
  // ─────────────────────────────────────────────────────────────────────────

  private void renderLayeredExpressionCrossfade(
      LayeredCharacterResolver.ExpressionLayerDiff layerDiff,
      VnState.ExpressionTransition transition,
      VnCharacter character,
      CharacterPosition position,
      double width,
      double height,
      double offsetX,
      double offsetY,
      String characterId,
      VnState state,
      VnScenario scenario,
      VnStagePreset stage,
      double alpha) {
    String fromExpression = transition.getFromExpression();
    String toExpression = transition.getToExpression();
    Map<String, String> fromLayerPathsById = layerPathsById(character, fromExpression);
    Map<String, String> toLayerPathsById = layerPathsById(character, toExpression);
    List<String> toLayerOrder = character.getExpressionLayerIds(toExpression);

    String toImagePath = character.getExpressionPath(toExpression);
    List<String> toImageLayerPaths = layerPathsFor(toImagePath);
    double[] reference = spriteSourceDimensions(toImageLayerPaths);
    double spriteHeight = height * characterHeightFactor * characterScale(character);
    double spriteWidth = reference != null ? reference[0] * (spriteHeight / reference[1]) : spriteHeight * 0.5;
    double x = position.computeScreenX(width, spriteWidth) + offsetX;
    double y = position.computeScreenY(height, spriteHeight, characterBaselineY) + offsetY;

    List<LayerDrawPlanEntry> plan = buildLayerCrossfadePlan(
        layerDiff, toLayerOrder, fromLayerPathsById, toLayerPathsById, alpha, transition.getProgress());

    blitter.push();
    applyGroupTransforms(characterId, state);
    for (LayerDrawPlanEntry planEntry : plan) {
      if (planEntry.alpha() <= 0.001) continue;
      double[] layerDims = timelineLayerDimensions(planEntry.path());
      if (layerDims == null) {
        String context = characterId + ":" + fromExpression + "->" + toExpression + ":" + planEntry.layerId();
        BlitterMissingAssetPlaceholder.report(blitter, planEntry.path(), context, x, y, spriteWidth, spriteHeight);
        continue;
      }
      blitter.push();
      if (planEntry.alpha() < 0.999) blitter.setGlobalAlpha(planEntry.alpha());
      drawCharacterImage(planEntry.path(), planEntry.path(), x, y, spriteWidth, spriteHeight, width, height, stage);
      blitter.pop();
    }
    blitter.pop();
  }

  /**
   * Builds a per-layer draw plan for an expression transition: unchanged layers draw once at
   * full alpha (no flicker), changed pairs crossfade, added layers fade in, removed layers fade out.
   */
  static List<LayerDrawPlanEntry> buildLayerCrossfadePlan(
      LayeredCharacterResolver.ExpressionLayerDiff diff,
      List<String> toLayerOrder,
      Map<String, String> fromLayerPathsById,
      Map<String, String> toLayerPathsById,
      double baseAlpha,
      double progress) {
    List<LayerDrawPlanEntry> plan = new ArrayList<>();
    for (String layerId : diff.unchangedLayerIds()) {
      String path = toLayerPathsById.get(layerId);
      if (path != null) plan.add(new LayerDrawPlanEntry(layerId, path, baseAlpha));
    }
    for (LayeredCharacterResolver.LayerChange change : diff.changedPairs()) {
      String fromPath = fromLayerPathsById.get(change.fromLayerId());
      if (fromPath != null) plan.add(new LayerDrawPlanEntry(change.fromLayerId(), fromPath, baseAlpha * (1.0 - progress)));
      String toPath = toLayerPathsById.get(change.toLayerId());
      if (toPath != null) plan.add(new LayerDrawPlanEntry(change.toLayerId(), toPath, baseAlpha * progress));
    }
    for (String layerId : diff.addedLayerIds()) {
      String path = toLayerPathsById.get(layerId);
      if (path != null) plan.add(new LayerDrawPlanEntry(layerId, path, baseAlpha * progress));
    }
    for (String layerId : diff.removedLayerIds()) {
      String path = fromLayerPathsById.get(layerId);
      if (path != null) plan.add(new LayerDrawPlanEntry(layerId, path, baseAlpha * (1.0 - progress)));
    }
    return plan;
  }

  /**
   * Warns once per missing character layer path so a blank/silhouette sprite is traceable
   * back to the offending character/expression/layer instead of failing silently.
   */
  static void reportMissingCharacterLayers(VnCharacter character, String characterId, String expression, String imagePath) {
    if (character == null) return;
    List<String> layerIds = character.getExpressionLayerIds(expression);
    if (layerIds.isEmpty()) {
      RenderDiagnostics.missingAsset(imagePath, characterId + ":" + expression);
      return;
    }
    List<String> layerPaths = parseLayerPaths(character.getExpressionPath(expression));
    for (int i = 0; i < layerIds.size(); i++) {
      String layerId = layerIds.get(i);
      String path = i < layerPaths.size() ? layerPaths.get(i) : character.getLayerPath(layerId);
      RenderDiagnostics.missingAsset(path == null ? imagePath : path, characterId + ":" + expression + ":" + layerId);
    }
  }

  private Map<String, String> layerPathsById(VnCharacter character, String expression) {
    List<String> layerIds = character.getExpressionLayerIds(expression);
    List<String> layerPaths = layerPathsFor(character.getExpressionPath(expression));
    Map<String, String> byId = new LinkedHashMap<>();
    for (int i = 0; i < layerIds.size(); i++) {
      String layerId = layerIds.get(i);
      String path = i < layerPaths.size() ? layerPaths.get(i) : character.getLayerPath(layerId);
      if (path != null) byId.put(layerId, path);
    }
    return byId;
  }

  private void applyGroupTransforms(String targetId, VnState state) {
    if (state == null || timelineAccessor == null) return;
    String parentId = state.getDynamicGroups().get(targetId);
    if (parentId == null) return;

    applyGroupTransforms(parentId, state);

    Entity2D proxy = timelineAccessor.getProxy(parentId);
    if (proxy != null) {
      double px = proxy.getX();
      double py = proxy.getY();
      blitter.translate(px, py);
      if (proxy.getRotationDeg() != 0.0) blitter.rotateDeg(proxy.getRotationDeg());
      if (proxy.getScaleX() != 1.0 || proxy.getScaleY() != 1.0) blitter.scale(proxy.getScaleX(), proxy.getScaleY());
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Whole-sprite drawing
  // ─────────────────────────────────────────────────────────────────────────

  private void renderCharacterSprite(
      String imagePath,
      String expression,
      VnCharacter character,
      CharacterPosition position,
      double width,
      double height,
      double offsetX,
      double offsetY,
      String characterId,
      VnState state,
      VnScenario scenario,
      VnStagePreset stage) {
    List<String> layerPaths = layerPathsFor(imagePath);
    double[] reference = spriteSourceDimensions(layerPaths);
    if (reference == null) {
      reportMissingCharacterLayers(character, characterId, expression, imagePath);
    }
    SpriteLayout spriteLayout = resolveSpriteLayout(
        reference == null ? 0.0 : reference[0],
        reference == null ? 0.0 : reference[1],
        width,
        height,
        characterHeightFactor,
        characterBaselineY,
        characterScale(character));
    double spriteHeight = spriteLayout.height();
    double spriteWidth = spriteLayout.width();
    double defaultX = position.computeScreenX(width, spriteWidth) + offsetX;
    double defaultY = position.computeScreenY(height, spriteHeight, spriteLayout.baselineY()) + offsetY;

    if (timelineAccessor != null && characterId != null) {
      Entity2D proxy = timelineAccessor.getProxy(characterId);
      if (proxy != null && hasTimelinePosition(proxy)) {
        double px = timelineDrawX(proxy, defaultX);
        double py = timelineDrawY(proxy, defaultY);
        boolean hasEyeFocus = state != null && state.getEyeFocusRequest(characterId) != null && layerPaths.size() > 1;
        if (hasEyeFocus && reference != null && renderTimelineDrivenLayers(
            character, expression, characterId, layerPaths, px, py, spriteWidth, spriteHeight, width, height, state, scenario, stage)) {
          return;
        }
        if (reference != null) {
          drawSpriteSource(imagePath, layerPaths, px, py, spriteWidth, spriteHeight, width, height, stage);
        } else {
          drawPlaceholderSilhouette(px, py, spriteWidth, spriteHeight, false);
        }
        return;
      }
      if (reference != null && renderTimelineDrivenLayers(
          character, expression, characterId, layerPaths, defaultX, defaultY, spriteWidth, spriteHeight, width, height, state, scenario, stage)) {
        return;
      }
    } else if (reference != null && renderTimelineDrivenLayers(
        character, expression, characterId, layerPaths, defaultX, defaultY, spriteWidth, spriteHeight, width, height, state, scenario, stage)) {
      return;
    }
    double resolvedX = timelineDisplacementFallbackX(defaultX, state, characterId, offsetX);
    double resolvedY = timelineDisplacementFallbackY(defaultY, state, characterId, offsetY);
    if (reference == null) {
      drawPlaceholderSilhouette(resolvedX, resolvedY, spriteWidth, spriteHeight, true);
      return;
    }

    drawCharacterImageWithTimelineTransform(
        imagePath, layerPaths, resolvedX, resolvedY, spriteWidth, spriteHeight, width, height, stage, state, characterId);
  }

  /**
   * Placeholder box drawn when a sprite's source cannot be resolved. {@link Blitter2D} has no
   * rounded-rect primitive, so the original's {@code fillRoundRect}/{@code strokeRoundRect}
   * (radius 20) become square rects — the same simplification sibling collaborators make.
   */
  private void drawPlaceholderSilhouette(double x, double y, double width, double height, boolean stroked) {
    blitter.setFill(200.0 / 255.0, 200.0 / 255.0, 200.0 / 255.0, 0.4);
    blitter.fillRect(x, y, width, height);
    if (stroked) {
      blitter.setStroke(1.0, 1.0, 1.0, 1.0);
      blitter.setStrokeWidth(2);
      blitter.strokeRect(x, y, width, height);
    }
  }

  private void drawCharacterImageWithTimelineTransform(
      String imagePath,
      List<String> layerPaths,
      double x,
      double y,
      double width,
      double height,
      double canvasWidth,
      double canvasHeight,
      VnStagePreset stage,
      VnState state,
      String characterId) {
    VnState.TimelineTransform transform = state == null ? null : state.getTimelineTransform(characterId);
    if (transform == null || !hasRenderableTimelineTransform(transform)) {
      drawSpriteSource(imagePath, layerPaths, x, y, width, height, canvasWidth, canvasHeight, stage);
      return;
    }

    double originX = transform.hasPivotX() ? transform.getPivotX() : 0.5;
    double originY = transform.hasPivotY() ? transform.getPivotY() : 1.0;
    double scaleX = transform.hasScaleX() ? transform.getScaleX() : 1.0;
    double scaleY = transform.hasScaleY() ? transform.getScaleY() : 1.0;
    double rotation = transform.hasRotation() ? transform.getRotationDeg() : 0.0;
    double pivotX = x + originX * width;
    double pivotY = y + originY * height;

    blitter.push();
    blitter.translate(pivotX, pivotY);
    if (Math.abs(rotation) > 1e-6) blitter.rotateDeg(rotation);
    if (Math.abs(scaleX - 1.0) > 1e-6 || Math.abs(scaleY - 1.0) > 1e-6) blitter.scale(scaleX, scaleY);
    blitter.translate(-pivotX, -pivotY);
    drawSpriteSource(imagePath, layerPaths, x, y, width, height, canvasWidth, canvasHeight, stage);
    blitter.pop();
  }

  /**
   * Draws a character sprite's resolved source. Multi-layer sprites composite through a cached
   * {@link RenderTarget2D} (the offscreen replacement for the original's {@code Canvas} snapshot);
   * single-layer sprites draw their one path directly, exactly as the original's
   * {@code firstAvailableImage} fallback did.
   */
  private void drawSpriteSource(
      String imagePathSpec,
      List<String> layerPaths,
      double x,
      double y,
      double drawWidth,
      double drawHeight,
      double canvasWidth,
      double canvasHeight,
      VnStagePreset stage) {
    RenderTarget2D composite = compositeSpriteFor(imagePathSpec, layerPaths);
    if (composite != null) {
      if (stage != null && !stage.getLights().isEmpty() && stageLightingRenderer != null) {
        stageLightingRenderer.drawLitComposite(
            composite,
            imagePathSpec,
            x,
            y,
            drawWidth,
            drawHeight,
            canvasWidth,
            canvasHeight,
            stage);
      } else {
        blitter.drawRenderTarget(composite, x, y, drawWidth, drawHeight);
      }
      return;
    }
    String path = firstResolvableLayerPath(layerPaths);
    if (path == null) return;
    drawCharacterImage(path, path, x, y, drawWidth, drawHeight, canvasWidth, canvasHeight, stage);
  }

  private boolean hasRenderableTimelineTransform(VnState.TimelineTransform transform) {
    if (transform == null) return false;
    return (transform.hasScaleX() && Math.abs(transform.getScaleX() - 1.0) > 1e-6)
        || (transform.hasScaleY() && Math.abs(transform.getScaleY() - 1.0) > 1e-6)
        || (transform.hasRotation() && Math.abs(transform.getRotationDeg()) > 1e-6);
  }

  private double timelineDisplacementFallbackX(double defaultX, VnState state, String characterId, double visualOffsetX) {
    VnState.TimelineDisplacement displacement = state == null ? null : state.getTimelineDisplacement(characterId);
    if (displacement == null || !displacement.hasX() || Math.abs(visualOffsetX) > 1e-6) return defaultX;
    return defaultX + displacement.getX();
  }

  private double timelineDisplacementFallbackY(double defaultY, VnState state, String characterId, double visualOffsetY) {
    VnState.TimelineDisplacement displacement = state == null ? null : state.getTimelineDisplacement(characterId);
    if (displacement == null || !displacement.hasY() || Math.abs(visualOffsetY) > 1e-6) return defaultY;
    return defaultY + displacement.getY();
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Timeline / eye-focus driven independent layer drawing
  // ─────────────────────────────────────────────────────────────────────────

  private boolean renderTimelineDrivenLayers(
      VnCharacter character,
      String expression,
      String characterId,
      List<String> layerPaths,
      double defaultX,
      double defaultY,
      double spriteWidth,
      double spriteHeight,
      double canvasWidth,
      double canvasHeight,
      VnState state,
      VnScenario scenario,
      VnStagePreset stage
  ) {
    if (layerPaths == null || layerPaths.size() <= 1) return false;
    List<SpriteLayer> layers = spriteLayers(character, expression, characterId, layerPaths);
    Map<String, Entity2D> inferredReplacementProxies = inferredReplacementProxies(
        character, characterId, expression, layers);
    EyeFocusDraw eyeFocus = resolveEyeFocusDraw(
        state, scenario, character, expression, characterId, layers,
        defaultX, defaultY, spriteWidth, spriteHeight, canvasWidth, canvasHeight);
    boolean hasLayerProxy = false;
    if (timelineAccessor != null) {
      for (SpriteLayer layer : layers) {
        if (layer != null && hasAnyLayerTransformProxy(layer, inferredReplacementProxies)) {
          hasLayerProxy = true;
          break;
        }
      }
    }
    if (!hasLayerProxy && !eyeFocus.active()) return false;

    for (SpriteLayer layer : layers) {
      if (layer == null) continue;
      double[] layerDims = timelineLayerDimensions(layer.path());
      if (layerDims == null) {
        BlitterMissingAssetPlaceholder.report(
            blitter, layer.path(), "layer:" + layer.layerId(), defaultX, defaultY, spriteWidth, spriteHeight);
        continue;
      }
      SpriteLayer drawLayer = new SpriteLayer(
          layer.path(), layer.layerId(), layer.targetNames(), layer.groupTargets(), true);
      double nudgeX = 0.0;
      double nudgeY = 0.0;
      if (eyeFocus.active() && eyeFocus.isMappedLayer(layer.layerId())) {
        boolean selectedLayerPresent = expressionContainsLayer(layers, eyeFocus.selectedLayerId());
        boolean drawSelected = layer.layerId().equals(eyeFocus.selectedLayerId())
            || (!selectedLayerPresent && layer.layerId().equals(eyeFocus.replacementSlotLayerId()));
        if (!drawSelected) continue;
        double[] selectedDims = timelineLayerDimensions(eyeFocus.selectedPath());
        if (selectedDims == null) {
          BlitterMissingAssetPlaceholder.report(
              blitter, eyeFocus.selectedPath(), "layer:" + eyeFocus.selectedLayerId(),
              defaultX, defaultY, spriteWidth, spriteHeight);
          continue;
        }
        drawLayer = new SpriteLayer(
            eyeFocus.selectedPath(),
            eyeFocus.selectedLayerId(),
            timelineDeclaredLayerTargetNames(character, characterId, expression, eyeFocus.selectedLayerId()),
            layer.groupTargets(),
            true);
        nudgeX = eyeFocus.nudgeX();
        nudgeY = eyeFocus.nudgeY();
      }
      List<LayerTransformTarget> transforms = resolveLayerTransforms(
          drawLayer, inferredReplacementProxies);
      if (transforms.stream().anyMatch(target -> target.exact() && target.proxy() != null && !target.proxy().isVisible())) {
        continue;
      }
      drawTimelineLayer(drawLayer, transforms, defaultX + nudgeX, defaultY + nudgeY,
          spriteWidth, spriteHeight, canvasWidth, canvasHeight, stage);
    }
    return true;
  }

  private EyeFocusDraw resolveEyeFocusDraw(
      VnState state,
      VnScenario scenario,
      VnCharacter character,
      String expression,
      String characterId,
      List<SpriteLayer> layers,
      double defaultX,
      double defaultY,
      double spriteWidth,
      double spriteHeight,
      double canvasWidth,
      double canvasHeight
  ) {
    if (state == null || scenario == null || character == null || characterId == null || layers == null || layers.isEmpty()) {
      return EyeFocusDraw.inactive();
    }
    VnState.EyeFocusRequest request = state.getEyeFocusRequest(characterId);
    if (request == null) return EyeFocusDraw.inactive();

    VnEyeFocusProfile profile = resolveEyeFocusProfile(character, expression, request.expression());
    if (profile == null || profile.layerIds().isEmpty()) return EyeFocusDraw.inactive();

    double targetX;
    double targetY;
    if (request.hasPointTarget()) {
      targetX = request.targetX();
      targetY = request.targetY();
    } else if (request.hasCharacterTarget()) {
      double[] point = characterFocusPoint(state, scenario, request.targetCharacterId(), canvasWidth, canvasHeight);
      if (point == null) return EyeFocusDraw.inactive();
      targetX = point[0];
      targetY = point[1];
    } else {
      return EyeFocusDraw.inactive();
    }

    double sourceX = defaultX + spriteWidth * profile.sourceX();
    double sourceY = defaultY + spriteHeight * profile.sourceY();
    double dx = (targetX - sourceX) / Math.max(1.0, spriteWidth);
    double dy = (targetY - sourceY) / Math.max(1.0, spriteHeight);
    EyeFocusResolver.Result resolved = EyeFocusResolver.resolve(
        0.0,
        0.0,
        dx,
        dy,
        request.deadZone(),
        profile.maxNudgePx(),
        request.strength());

    String selectedLayerId = profile.layerIdFor(resolved.keypadIndex());
    if (selectedLayerId == null || selectedLayerId.isBlank()) {
      selectedLayerId = profile.layerIdFor(5);
    }
    if (selectedLayerId == null || selectedLayerId.isBlank()) return EyeFocusDraw.inactive();

    String selectedPath = character.getLayerPath(selectedLayerId);
    if (selectedPath == null || selectedPath.isBlank()) {
      for (SpriteLayer layer : layers) {
        if (layer != null && selectedLayerId.equals(layer.layerId())) {
          selectedPath = layer.path();
          break;
        }
      }
    }
    if (selectedPath == null || selectedPath.isBlank()) return EyeFocusDraw.inactive();

    Set<String> mapped = new LinkedHashSet<>();
    for (String layerId : profile.layerIds().values()) {
      if (layerId != null && !layerId.isBlank()) mapped.add(layerId);
    }
    String replacementSlot = replacementSlotLayerId(profile, layers, mapped, selectedLayerId);
    if (replacementSlot.isBlank()) return EyeFocusDraw.inactive();
    String selectedTargetName = timelineLayerTargetName(characterId, expression, selectedLayerId);
    return new EyeFocusDraw(
        true,
        selectedLayerId,
        selectedPath,
        selectedTargetName == null ? "" : selectedTargetName,
        replacementSlot,
        resolved.nudgeX(),
        resolved.nudgeY(),
        Set.copyOf(mapped));
  }

  private @Nullable VnEyeFocusProfile resolveEyeFocusProfile(
      VnCharacter character, String expression, String requestedExpression) {
    if (character == null) return null;
    Map<String, VnEyeFocusProfile> profiles = eyeFocusProfiles();
    String currentExpression = expression == null || expression.isBlank() ? "neutral" : expression;
    VnEyeFocusProfile profile = profiles.get(VnEyeFocusProfile.key(character.getId(), currentExpression));
    if (profile != null) return profile;
    if (requestedExpression != null && !requestedExpression.isBlank()) {
      profile = profiles.get(VnEyeFocusProfile.key(character.getId(), requestedExpression));
      if (profile != null) return profile;
    }
    profile = profiles.get(VnEyeFocusProfile.key(character.getId(), "neutral"));
    if (profile != null) return profile;
    return VnEyeFocusProfile.autoDetect(character, currentExpression).orElse(null);
  }

  private Map<String, VnEyeFocusProfile> eyeFocusProfiles() {
    Map<String, VnEyeFocusProfile> cached = eyeFocusProfiles;
    if (cached != null) return cached;
    if (projectRoot != null) {
      cached = VnEyeFocusProfileStore.byKey(VnEyeFocusProfileStore.load(projectRoot));
    } else {
      cached = VnEyeFocusProfileStore.loadFromAssets(assetCatalog);
    }
    eyeFocusProfiles = cached;
    return cached;
  }

  private String replacementSlotLayerId(
      VnEyeFocusProfile profile,
      List<SpriteLayer> layers,
      Set<String> mapped,
      String selectedLayerId
  ) {
    for (SpriteLayer layer : layers) {
      if (layer != null && selectedLayerId.equals(layer.layerId())) {
        return selectedLayerId;
      }
    }
    String neutral = profile.layerIdFor(5);
    if (neutral != null && !neutral.isBlank()) {
      for (SpriteLayer layer : layers) {
        if (layer != null && neutral.equals(layer.layerId())) {
          return neutral;
        }
      }
    }
    for (SpriteLayer layer : layers) {
      if (layer != null && mapped.contains(layer.layerId())) {
        return layer.layerId();
      }
    }
    return "";
  }

  private boolean expressionContainsLayer(List<SpriteLayer> layers, String layerId) {
    if (layers == null || layerId == null || layerId.isBlank()) return false;
    for (SpriteLayer layer : layers) {
      if (layer != null && layerId.equals(layer.layerId())) return true;
    }
    return false;
  }

  private double @Nullable [] characterFocusPoint(
      VnState state, VnScenario scenario, String characterId, double canvasWidth, double canvasHeight) {
    if (state == null || scenario == null || characterId == null || characterId.isBlank()) return null;
    CharacterPosition position = state.getCharacterPosition(characterId);
    VnState.CharacterSlot slot = position == null ? null : state.getVisibleCharacters().get(position);
    VnState.CharacterVisual visual = position == null ? null : state.getCharacterVisual(position);
    VnState.DetachedCharacterSlot detached;
    if (slot == null) {
      detached = state.getDetachedCharacter(characterId);
      if (detached == null) return null;
      position = detached.getBasePosition();
      slot = detached.getSlot();
      visual = detached.getVisual();
    }
    if (slot == null) return null;
    VnCharacter character = scenario.getCharacter(slot.getCharacterId());
    if (character == null) return null;
    String imagePath = character.getExpressionPath(slot.getExpression());
    List<String> layerPaths = layerPathsFor(imagePath);
    double[] reference = spriteSourceDimensions(layerPaths);
    SpriteLayout spriteLayout = resolveSpriteLayout(
        reference == null ? 0.0 : reference[0],
        reference == null ? 0.0 : reference[1],
        canvasWidth,
        canvasHeight,
        characterHeightFactor,
        characterBaselineY,
        characterScale(character));
    double spriteHeight = spriteLayout.height();
    double spriteWidth = spriteLayout.width();
    double offsetX = visual != null ? visual.getOffsetX() : 0.0;
    double offsetY = visual != null ? visual.getOffsetY() : 0.0;
    VnState.TimelineDisplacement displacement = state.getTimelineDisplacement(characterId);
    if (displacement != null) {
      if (displacement.hasX() && Math.abs(offsetX) < 1e-6) offsetX += displacement.getX();
      if (displacement.hasY() && Math.abs(offsetY) < 1e-6) offsetY += displacement.getY();
    }
    double x = position.computeScreenX(canvasWidth, spriteWidth) + offsetX;
    double y = position.computeScreenY(canvasHeight, spriteHeight, spriteLayout.baselineY()) + offsetY;
    return new double[] {x + spriteWidth * 0.5, y + spriteHeight * 0.26};
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Sprite layout math
  // ─────────────────────────────────────────────────────────────────────────

  static SpriteLayout resolveSpriteLayout(
      double imageWidth,
      double imageHeight,
      double viewportWidth,
      double viewportHeight,
      double characterHeightFactor,
      double characterBaselineY,
      double characterScale
  ) {
    double safeViewportHeight = Math.max(1.0, viewportHeight);
    double safeScale = Double.isFinite(characterScale) && characterScale > 0.0 ? characterScale : 1.0;
    boolean canvasAligned = isCanvasAlignedSprite(imageWidth, imageHeight, viewportWidth, safeViewportHeight);
    double spriteHeight = safeViewportHeight
        * (canvasAligned ? 1.0 : characterHeightFactor)
        * safeScale;
    double spriteWidth = imageWidth > 0.0 && imageHeight > 0.0
        ? imageWidth * (spriteHeight / imageHeight)
        : spriteHeight * 0.5;
    double baselineY = canvasAligned ? 1.0 : characterBaselineY;
    return new SpriteLayout(spriteWidth, spriteHeight, baselineY, canvasAligned);
  }

  private static boolean isCanvasAlignedSprite(
      double imageWidth,
      double imageHeight,
      double viewportWidth,
      double viewportHeight
  ) {
    if (imageWidth <= 0.0 || imageHeight <= 0.0 || viewportWidth <= 0.0 || viewportHeight <= 0.0) {
      return false;
    }
    double imageAspect = imageWidth / imageHeight;
    double viewportAspect = viewportWidth / viewportHeight;
    return Math.abs(imageAspect - viewportAspect) <= Math.max(1e-6, viewportAspect * 0.001);
  }

  static double characterScale(VnCharacter character) {
    if (character == null) return 1.0;
    return Math.max(0.1, Math.min(3.0, character.getScale()));
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Layer transform resolution
  // ─────────────────────────────────────────────────────────────────────────

  private void drawTimelineLayer(
      SpriteLayer layer,
      List<LayerTransformTarget> transforms,
      double x,
      double y,
      double width,
      double height,
      double canvasWidth,
      double canvasHeight,
      VnStagePreset stage
  ) {
    blitter.push();
    double alpha = 1.0;
    if (transforms != null) {
      for (LayerTransformTarget target : transforms) {
        if (target != null && target.proxy() instanceof Sprite2D sprite) {
          alpha *= sprite.getAlpha();
        }
      }
    }
    if (alpha < 0.999) blitter.setGlobalAlpha(Math.max(0.0, Math.min(1.0, alpha)));
    if (transforms != null) {
      for (LayerTransformTarget target : transforms) {
        applyLayerTransformTarget(target, x, y, width, height);
      }
    }
    drawCharacterImage(layer.path(), layer.path(), x, y, width, height, canvasWidth, canvasHeight, stage);
    blitter.pop();
  }

  private void applyLayerTransformTarget(LayerTransformTarget target,
                                         double baseX,
                                         double baseY,
                                         double width,
                                         double height) {
    if (target == null || target.proxy() == null) return;
    Entity2D proxy = target.proxy();
    double dx = timelineTransformOffsetX(proxy, baseX);
    double dy = timelineTransformOffsetY(proxy, baseY);
    if (Math.abs(dx) > 1e-6 || Math.abs(dy) > 1e-6) {
      blitter.translate(dx, dy);
    }

    double originX = transformOriginX(target);
    double originY = transformOriginY(target);
    double pivotX = baseX + originX * width;
    double pivotY = baseY + originY * height;
    blitter.translate(pivotX, pivotY);
    if (proxy.getRotationDeg() != 0.0) blitter.rotateDeg(proxy.getRotationDeg());
    if (proxy.getScaleX() != 1.0 || proxy.getScaleY() != 1.0) blitter.scale(proxy.getScaleX(), proxy.getScaleY());
    blitter.translate(-pivotX, -pivotY);
  }

  private double transformOriginX(LayerTransformTarget target) {
    Entity2D proxy = target == null ? null : target.proxy();
    if (proxy == null) return 0.5;
    VnCharacter.LayerGroup group = target.group();
    if (group != null && group.hasPivot() && !hasAuthoredProxyOriginX(proxy)) {
      return group.pivotX();
    }
    return proxy.getOriginX();
  }

  private double transformOriginY(LayerTransformTarget target) {
    Entity2D proxy = target == null ? null : target.proxy();
    if (proxy == null) return 1.0;
    VnCharacter.LayerGroup group = target.group();
    if (group != null && group.hasPivot() && !hasAuthoredProxyOriginY(proxy)) {
      return group.pivotY();
    }
    return proxy.getOriginY();
  }

  private boolean hasAuthoredProxyOriginX(Entity2D proxy) {
    return proxy instanceof VnCharacterSceneAccessor.TimelineProxyEntity timelineProxy
        && timelineProxy.hasTimelineOriginX();
  }

  private boolean hasAuthoredProxyOriginY(Entity2D proxy) {
    return proxy instanceof VnCharacterSceneAccessor.TimelineProxyEntity timelineProxy
        && timelineProxy.hasTimelineOriginY();
  }

  private double timelineTransformOffsetX(Entity2D proxy, double defaultX) {
    if (proxy == null) return 0.0;
    if (proxy instanceof TimelineDrivenEntity driven) {
      return driven.hasTimelineX() ? proxy.getX() : 0.0;
    }
    return hasTimelinePosition(proxy) ? proxy.getX() - defaultX : 0.0;
  }

  private double timelineTransformOffsetY(Entity2D proxy, double defaultY) {
    if (proxy == null) return 0.0;
    if (proxy instanceof TimelineDrivenEntity driven) {
      return driven.hasTimelineY() ? proxy.getY() : 0.0;
    }
    return hasTimelinePosition(proxy) ? proxy.getY() - defaultY : 0.0;
  }

  private boolean hasAnyLayerTransformProxy(
      SpriteLayer layer,
      Map<String, Entity2D> inferredReplacementProxies
  ) {
    if (timelineAccessor == null || layer == null) return false;
    if (firstProxy(layer.targetNames()) != null) return true;
    if (layer.groupTargets() != null) {
      for (GroupLayerTarget groupTarget : layer.groupTargets()) {
        if (groupTarget != null && firstProxy(groupTarget.targetNames()) != null) return true;
      }
    }
    return inferredReplacementProxies != null && inferredReplacementProxies.containsKey(layer.layerId());
  }

  private List<LayerTransformTarget> resolveLayerTransforms(
      SpriteLayer layer,
      Map<String, Entity2D> inferredReplacementProxies
  ) {
    if (timelineAccessor == null || layer == null) return List.of();
    List<LayerTransformTarget> transforms = new ArrayList<>();
    if (layer.groupTargets() != null) {
      for (GroupLayerTarget groupTarget : layer.groupTargets()) {
        if (groupTarget == null) continue;
        Entity2D proxy = firstProxy(groupTarget.targetNames());
        if (proxy != null) transforms.add(new LayerTransformTarget(proxy, groupTarget.group(), true));
      }
    }

    Entity2D exact = firstProxy(layer.targetNames());
    if (exact != null) {
      transforms.add(new LayerTransformTarget(exact, null, true));
      return List.copyOf(transforms);
    }

    Entity2D inferred = inferredReplacementProxies == null ? null : inferredReplacementProxies.get(layer.layerId());
    if (inferred != null) {
      transforms.add(new LayerTransformTarget(inferred, null, false));
      return List.copyOf(transforms);
    }

    return List.copyOf(transforms);
  }

  private Map<String, Entity2D> inferredReplacementProxies(
      VnCharacter character,
      String characterId,
      String expression,
      List<SpriteLayer> expressionLayers
  ) {
    if (timelineAccessor == null || character == null || expressionLayers == null || expressionLayers.isEmpty()) {
      return Map.of();
    }
    Set<String> activeLayerIds = new LinkedHashSet<>();
    for (SpriteLayer layer : expressionLayers) {
      if (layer != null && layer.layerId() != null && !layer.layerId().isBlank()) {
        activeLayerIds.add(layer.layerId());
      }
    }
    Map<String, Entity2D> animatedLayers = new LinkedHashMap<>();
    for (String declaredLayerId : character.getLayerIds()) {
      if (declaredLayerId == null || declaredLayerId.isBlank() || activeLayerIds.contains(declaredLayerId)) continue;
      Entity2D proxy = firstProxy(timelineDeclaredLayerTargetNames(
          character, characterId, expression, declaredLayerId));
      if (proxy != null) animatedLayers.put(declaredLayerId, proxy);
    }
    if (animatedLayers.isEmpty()) return Map.of();

    Map<String, Entity2D> inferred = new LinkedHashMap<>();
    for (SpriteLayer layer : expressionLayers) {
      if (layer == null || layer.layerId() == null || layer.layerId().isBlank()
          || firstProxy(layer.targetNames()) != null) {
        continue;
      }
      String inferredLayerId = LayeredCharacterResolver.inferReplacementLayerId(
          layer.layerId(), animatedLayers.keySet());
      if (inferredLayerId != null) {
        Entity2D proxy = animatedLayers.get(inferredLayerId);
        if (proxy != null) inferred.put(layer.layerId(), proxy);
      }
    }
    return inferred.isEmpty() ? Map.of() : Map.copyOf(inferred);
  }

  private List<String> timelineDeclaredLayerTargetNames(
      VnCharacter character,
      String characterId,
      String expression,
      String layerId
  ) {
    return com.jvn.core.vn.LayerTargetNaming.declaredLayerTargetNames(character, characterId, expression, layerId);
  }

  private @Nullable Entity2D firstProxy(List<String> targetNames) {
    if (timelineAccessor == null || targetNames == null || targetNames.isEmpty()) return null;
    for (String targetName : targetNames) {
      if (targetName == null || targetName.isBlank()) continue;
      Entity2D proxy = timelineAccessor.getProxy(targetName);
      if (proxy != null) return proxy;
    }
    return null;
  }

  private boolean hasTimelinePosition(Entity2D proxy) {
    if (proxy instanceof TimelineDrivenEntity driven) {
      return driven.hasTimelineX() || driven.hasTimelineY();
    }
    return proxy != null && (Math.abs(proxy.getX()) > 1e-6 || Math.abs(proxy.getY()) > 1e-6);
  }

  private double timelineDrawX(Entity2D proxy, double defaultX) {
    if (proxy instanceof TimelineDrivenEntity driven) {
      return driven.hasTimelineX() ? defaultX + proxy.getX() : defaultX;
    }
    return hasTimelinePosition(proxy) ? proxy.getX() : defaultX;
  }

  private double timelineDrawY(Entity2D proxy, double defaultY) {
    if (proxy instanceof TimelineDrivenEntity driven) {
      return driven.hasTimelineY() ? defaultY + proxy.getY() : defaultY;
    }
    return hasTimelinePosition(proxy) ? proxy.getY() : defaultY;
  }

  private List<SpriteLayer> spriteLayers(
      VnCharacter character, String expression, String characterId, List<String> layerPaths) {
    List<SpriteLayer> layers = new ArrayList<>();
    List<String> layerIds = character != null ? character.getExpressionLayerIds(expression) : List.of();
    for (int i = 0; i < layerPaths.size(); i++) {
      String path = layerPaths.get(i);
      String layerId = i < layerIds.size() ? layerIds.get(i) : "";
      if (layerId == null || layerId.isBlank()) layerId = fallbackLayerId(path, i);
      List<String> targetNames = timelineDeclaredLayerTargetNames(character, characterId, expression, layerId);
      List<GroupLayerTarget> groupTargets = timelineGroupTargets(character, characterId, expression, layerId);
      // Layer rasters are only needed when an active timeline or eye-focus request requires
      // independent drawing. Resolving them here made every static composite reload all of its
      // full-canvas sources on every frame, even though the composite itself was cached.
      layers.add(new SpriteLayer(path, layerId, targetNames, groupTargets, false));
    }
    return layers;
  }

  private @Nullable String timelineLayerTargetName(String characterId, String expression, String layerId) {
    List<String> names = timelineLayerTargetNames(characterId, expression, layerId);
    return names.isEmpty() ? null : names.get(0);
  }

  private List<String> timelineLayerTargetNames(String characterId, String expression, String layerId) {
    return com.jvn.core.vn.LayerTargetNaming.layerTargetNames(characterId, expression, layerId);
  }

  private List<GroupLayerTarget> timelineGroupTargets(
      VnCharacter character,
      String characterId,
      String expression,
      String layerId
  ) {
    if (character == null || layerId == null || layerId.isBlank()) return List.of();
    List<VnCharacter.LayerGroup> chain = character.getLayerGroupChainForLayer(layerId);
    if (chain.isEmpty()) return List.of();
    List<GroupLayerTarget> targets = new ArrayList<>();
    for (VnCharacter.LayerGroup group : chain) {
      if (group == null || group.id().isBlank()) continue;
      List<String> names = timelineGroupTargetNames(characterId, expression, group.id());
      if (!names.isEmpty()) targets.add(new GroupLayerTarget(group, names));
    }
    return List.copyOf(targets);
  }

  static List<String> timelineGroupTargetNames(String characterId, String expression, String groupId) {
    return com.jvn.core.vn.LayerTargetNaming.groupTargetNames(characterId, expression, groupId);
  }

  private String fallbackLayerId(String path, int index) {
    if (path == null || path.isBlank()) return "layer" + (index + 1);
    String normalized = path.replace('\\', '/');
    int slash = normalized.lastIndexOf('/');
    String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
    int dot = name.lastIndexOf('.');
    if (dot > 0) name = name.substring(0, dot);
    String safe = selectorSafeName(name);
    return safe.isBlank() ? "layer" + (index + 1) : safe;
  }

  private String selectorSafeName(String raw) {
    return com.jvn.core.vn.LayerTargetNaming.selectorSafeName(raw);
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Layer path parsing
  // ─────────────────────────────────────────────────────────────────────────

  private List<String> layerPathsFor(String imagePathSpec) {
    if (imagePathSpec == null) return List.of();
    List<String> cached = layerPathCache.get(imagePathSpec);
    if (cached != null) return cached;
    if (layerPathCache.size() >= MAX_CACHED_LAYER_PATH_SPECS) layerPathCache.clear();
    List<String> parsed = parseLayerPaths(imagePathSpec);
    layerPathCache.put(imagePathSpec, parsed);
    return parsed;
  }

  static List<String> parseLayerPaths(String imagePathSpec) {
    if (imagePathSpec == null || imagePathSpec.isBlank()) return List.of();
    if (imagePathSpec.indexOf('|') < 0) return List.of(imagePathSpec.trim());

    List<String> layers = new ArrayList<>();
    int start = 0;
    for (int separator; (separator = imagePathSpec.indexOf('|', start)) >= 0; start = separator + 1) {
      String path = imagePathSpec.substring(start, separator).trim();
      if (!path.isEmpty()) layers.add(path);
    }
    String path = imagePathSpec.substring(start).trim();
    if (!path.isEmpty()) layers.add(path);
    return layers.isEmpty() ? List.of(imagePathSpec.trim()) : List.copyOf(layers);
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Asset resolution / composite building
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Dimensions of the first layer path that resolves, replacing the original
   * {@code firstAvailableImage}'s size-probing role. A {@code null} result carries the same
   * "no source resolved" meaning the original's {@code null}/error {@code Image} did.
   */
  private double @Nullable [] spriteSourceDimensions(List<String> layerPaths) {
    if (layerPaths == null) return null;
    for (String path : layerPaths) {
      Optional<double[]> dims = AssetDimensionProbe.dimensionsOf(assetCatalog, path, projectRoot);
      if (dims.isPresent()) return dims.get();
    }
    return null;
  }

  private @Nullable String firstResolvableLayerPath(List<String> layerPaths) {
    if (layerPaths == null) return null;
    for (String path : layerPaths) {
      if (AssetDimensionProbe.dimensionsOf(assetCatalog, path, projectRoot).isPresent()) return path;
    }
    return null;
  }

  /**
   * Per-frame working-set dimensions lookup for an independently drawn layer. Non-null means the
   * asset resolves — the {@code isLoadedImage} check's replacement.
   */
  private double @Nullable [] timelineLayerDimensions(String path) {
    return timelineLayerWorkingSet.getOrLoad(
        path, p -> AssetDimensionProbe.dimensionsOf(assetCatalog, p, projectRoot).orElse(null));
  }

  /**
   * Returns the cached composited {@link RenderTarget2D} for a multi-layer sprite spec, building
   * it on first use. Single-layer sprites return {@code null} — they draw their one path directly
   * rather than paying for an offscreen round-trip.
   */
  @Nullable RenderTarget2D compositeSpriteFor(String imagePathSpec, List<String> layerPaths) {
    if (imagePathSpec == null || imagePathSpec.isBlank()) return null;
    if (layerPaths == null || layerPaths.size() <= 1) return null;
    String cacheKey = "__composite_sprite__:" + imagePathSpec;
    RenderTarget2D cached = compositeSpriteCache.get(cacheKey);
    if (cached != null && cached.isValid()) return cached;
    RenderTarget2D built = buildCompositeSprite(layerPaths);
    if (built != null) compositeSpriteCache.put(cacheKey, built);
    return built;
  }

  private @Nullable RenderTarget2D buildCompositeSprite(List<String> layerPaths) {
    int width = 1;
    int height = 1;
    List<String> resolvedLayers = new ArrayList<>();
    for (String path : layerPaths) {
      Optional<double[]> dims = AssetDimensionProbe.dimensionsOf(assetCatalog, path, projectRoot);
      if (dims.isEmpty()) continue;
      resolvedLayers.add(path);
      width = Math.max(width, (int) Math.round(dims.get()[0]));
      height = Math.max(height, (int) Math.round(dims.get()[1]));
    }
    if (resolvedLayers.isEmpty()) return null;
    RenderTarget2D target = blitter.createRenderTarget(width, height, 1.0);
    Blitter2D targetBlitter = target.getBlitter();
    for (String path : resolvedLayers) {
      targetBlitter.drawImage(path, 0, 0, width, height);
    }
    return target;
  }

  private static long renderTargetWeightBytes(RenderTarget2D target) {
    long w = Math.max(1L, Math.round(target.getWidth() * target.getPixelScale()));
    long h = Math.max(1L, Math.round(target.getHeight() * target.getPixelScale()));
    return w * h * 4L; // 4 bytes per packed ARGB pixel, same accounting FxImageMemory used for Image
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Character image drawing / stage lighting handoff
  // ─────────────────────────────────────────────────────────────────────────

  private void drawCharacterImage(String path,
                                  String spriteTag,
                                  double x,
                                  double y,
                                  double drawWidth,
                                  double drawHeight,
                                  double canvasWidth,
                                  double canvasHeight,
                                  VnStagePreset stage) {
    if (path == null || path.isBlank()) return;
    if (stage == null || stage.getLights().isEmpty() || stageLightingRenderer == null) {
      blitter.drawImage(path, x, y, drawWidth, drawHeight);
      return;
    }
    stageLightingRenderer.drawLitCharacter(
        path, spriteTag, x, y, drawWidth, drawHeight, canvasWidth, canvasHeight, stage);
  }

  /**
   * Builds the stage-lighting cache key for a character layer. Position is snapped to
   * {@link #LIGHTING_CACHE_POSITION_GRID_PX} so idle-bob/breathing jitter reuses the last
   * lit bitmap instead of forcing a relight every frame; the draw itself still uses the
   * exact float position passed separately to the draw call. Everything else that can change
   * rendered output (sprite identity, stage/lighting config, drawn size, canvas size) stays
   * exact so a real change always invalidates the cache.
   */
  static String stageCharacterCacheKey(
      String spriteTag,
      String stageCacheToken,
      double x,
      double y,
      double drawWidth,
      double drawHeight,
      double canvasWidth,
      double canvasHeight) {
    long gx = Math.round(x / LIGHTING_CACHE_POSITION_GRID_PX);
    long gy = Math.round(y / LIGHTING_CACHE_POSITION_GRID_PX);
    return spriteTag
        + "|stage:" + stageCacheToken
        + "|pos:" + gx + "," + gy
        + "|size:" + Math.round(drawWidth) + "x" + Math.round(drawHeight)
        + "|canvas:" + Math.round(canvasWidth) + "x" + Math.round(canvasHeight);
  }

  /** Draws a flat stack of layer paths at one rect, with no per-layer transform resolution. */
  void drawLayerStack(List<String> layerPaths, double x, double y, double width, double height) {
    if (layerPaths == null) return;
    for (String path : layerPaths) {
      if (path == null || path.isBlank()) continue;
      if (AssetDimensionProbe.dimensionsOf(assetCatalog, path, projectRoot).isPresent()) {
        blitter.drawImage(path, x, y, width, height);
      }
    }
  }
}
