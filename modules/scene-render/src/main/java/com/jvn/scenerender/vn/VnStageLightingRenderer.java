package com.jvn.scenerender.vn;

import java.io.File;
import java.util.Optional;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.BoundedImageCache;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.RenderDiagnostics;
import com.jvn.core.scene2d.RenderFeature;
import com.jvn.core.scene2d.RenderTarget2D;
import com.jvn.core.vn.stage.VnStagePreset;
import com.jvn.scenerender.assets.AssetDimensionProbe;
import org.jspecify.annotations.Nullable;

/**
 * Stage-lighting drawing collaborator ported from the original monolithic JavaFX
 * {@code VnRenderer} onto the platform-agnostic {@link Blitter2D} abstraction.
 *
 * <h2>Port notes</h2>
 * <ul>
 *   <li><b>Offscreen pixel round-trip replaces {@code Image}-in/{@code Image}-out.</b> The original
 *   handed a loaded JavaFX {@code Image} straight to {@code VnStageLightingSupport} and cached the
 *   lit {@code Image}. Here the source pixels are resolved by drawing the unlit source into a
 *   throwaway {@link RenderTarget2D} and calling {@link RenderTarget2D#readPixelsArgb()}; the lit
 *   {@code int[]} is written into an output target that is cached and blitted. On a cache hit only
 *   the final {@code drawRenderTarget} runs, matching the original's cost profile.</li>
 *   <li><b>Both lit-character entry points draw the result themselves</b> rather than returning it,
 *   keeping {@code VnCharacterCompositor}'s call sites a single line.</li>
 *   <li><b>Degrades safely.</b> Without {@link RenderFeature#PIXEL_ACCESS} there is no way to
 *   relight, so the unlit source is drawn instead and the gap is reported once via
 *   {@link RenderDiagnostics}. A backend lacking pixel access renders an unlit scene, never throws.</li>
 * </ul>
 */
final class VnStageLightingRenderer implements VnCharacterCompositor.StageLitCharacterDrawer {

  /**
   * 48 MiB / 64 MiB, matching the original {@code modules/fx} {@code VnRenderer}'s
   * {@code STAGE_BACKGROUND_CACHE_BUDGET_BYTES} / {@code STAGE_CHARACTER_CACHE_BUDGET_BYTES}.
   * Declared here rather than read off the {@code VnRenderer} facade so this collaborator has no
   * dependency on that (later-built) class, exactly as {@code VnCharacterCompositor} does.
   */
  private static final long STAGE_BACKGROUND_CACHE_BUDGET_BYTES = 48L * 1024L * 1024L;
  private static final long STAGE_CHARACTER_CACHE_BUDGET_BYTES = 64L * 1024L * 1024L;

  private final Blitter2D blitter;
  private final AssetCatalog assetCatalog = new AssetCatalog();

  private final BoundedImageCache<RenderTarget2D> stageBackgroundCache = new BoundedImageCache<>(
      16, STAGE_BACKGROUND_CACHE_BUDGET_BYTES, VnStageLightingRenderer::renderTargetWeightBytes,
      (key, target) -> target.close());
  private final BoundedImageCache<RenderTarget2D> stageCharacterCache = new BoundedImageCache<>(
      64, STAGE_CHARACTER_CACHE_BUDGET_BYTES, VnStageLightingRenderer::renderTargetWeightBytes,
      (key, target) -> target.close());

  // Unannotated to match VnCharacterCompositor's convention: AssetDimensionProbe.dimensionsOf's
  // projectRoot parameter is itself unannotated, so a @Nullable field trips NullAway at call sites.
  @SuppressWarnings("NullAway.Init")
  private File projectRoot;

  /** Stage-relight cache misses (recomposes) this frame, polled by {@code VnRenderer}. */
  private int stageLightingRecomposeCount;

  VnStageLightingRenderer(Blitter2D blitter) {
    this.blitter = blitter;
  }

  void beginFrame() {
    stageLightingRecomposeCount = 0;
  }

  /** Stage relights (cache misses) since the last {@link #beginFrame()}. */
  int stageLightingRecomposeCount() {
    return stageLightingRecomposeCount;
  }

  void setProjectRoot(File root) {
    if (java.util.Objects.equals(this.projectRoot, root)) return;
    this.projectRoot = root;
    clearCache();
  }

  void clearCache() {
    stageBackgroundCache.clear();
    stageCharacterCache.clear();
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Stage-lit background
  // ─────────────────────────────────────────────────────────────────────────

  void drawStageBackground(
      String backgroundPath, double width, double height, @Nullable VnStagePreset stage) {
    if (backgroundPath == null || backgroundPath.isBlank() || width <= 0 || height <= 0) return;

    String key = stageBackgroundCacheKey(
        backgroundPath,
        stage == null ? "none" : stage.getCacheToken(),
        (int) Math.round(width),
        (int) Math.round(height));
    RenderTarget2D cached = stageBackgroundCache.get(key);
    if (cached != null && cached.isValid()) {
      blitter.drawRenderTarget(cached, 0, 0, width, height);
      return;
    }
    if (!blitter.supports(RenderFeature.PIXEL_ACCESS)) {
      RenderDiagnostics.unsupported(blitter, RenderFeature.PIXEL_ACCESS, "drawStageBackground");
      blitter.drawImage(backgroundPath, 0, 0, width, height);
      return;
    }

    int[] sourceArgb;
    int srcW;
    int srcH;
    Optional<double[]> dims = AssetDimensionProbe.dimensionsOf(assetCatalog, backgroundPath, projectRoot);
    if (dims.isEmpty()) {
      BlitterMissingAssetPlaceholder.report(
          blitter, backgroundPath, "stage-background", 0, 0, width, height);
      return;
    }
    srcW = Math.max(1, (int) Math.round(dims.get()[0]));
    srcH = Math.max(1, (int) Math.round(dims.get()[1]));
    RenderTarget2D sourceTarget = blitter.createRenderTarget(srcW, srcH, 1.0);
    try {
      sourceTarget.getBlitter().drawImage(backgroundPath, 0, 0, srcW, srcH);
      sourceArgb = sourceTarget.readPixelsArgb();
    } finally {
      sourceTarget.close();
    }

    int[] litArgb = VnStageLightingSupport.buildLitBackground(sourceArgb, srcW, srcH, stage, width, height);
    int outW = Math.max(1, (int) Math.round(width));
    int outH = Math.max(1, (int) Math.round(height));
    RenderTarget2D outputTarget = blitter.createRenderTarget(outW, outH, 1.0);
    outputTarget.writePixelsArgb(litArgb);
    stageBackgroundCache.put(key, outputTarget);
    blitter.drawRenderTarget(outputTarget, 0, 0, width, height);
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Light overlays
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Flat tint/overlay pass applied when the stage's background grade could not be baked into a lit
   * background. Ported verbatim from the original; only {@code Color} became
   * {@link VnStageLightingSupport.Rgba}.
   */
  void applyStageBackgroundFallbackOverlay(
      @Nullable VnStagePreset stage, double width, double height) {
    if (stage == null) return;
    VnStagePreset.BackgroundGrade grade = stage.getBackgroundGrade();
    if (grade == null) return;

    VnStageLightingSupport.Rgba tint =
        VnStageLightingSupport.Rgba.parse(grade.tintColor(), VnStageLightingSupport.Rgba.WHITE);
    double tintStrength = VnStageLightingSupport.clamp(grade.tintStrength(), 0.0, 1.0);
    if (tintStrength > 1e-6) {
      blitter.setFill(tint.r(), tint.g(), tint.b(), tintStrength * 0.14);
      blitter.fillRect(0, 0, width, height);
    }

    VnStageLightingSupport.Rgba overlay =
        VnStageLightingSupport.Rgba.parse(grade.overlayColor(), VnStageLightingSupport.Rgba.BLACK);
    double overlayOpacity = VnStageLightingSupport.clamp(grade.overlayOpacity(), 0.0, 1.0);
    if (overlayOpacity > 1e-6) {
      blitter.setFill(overlay.r(), overlay.g(), overlay.b(), overlayOpacity * 0.20);
      blitter.fillRect(0, 0, width, height);
    }
  }

  /**
   * Draws the additive glow overlays for every light on {@code layer}. Muted lights, and all
   * non-solo lights while any light is soloed, are skipped, exactly as the original did.
   */
  void renderStageLightOverlays(
      @Nullable VnStagePreset stage,
      double canvasWidth,
      double canvasHeight,
      VnStagePreset.@Nullable LightLayer layer) {
    if (stage == null || layer == null || stage.getLights().isEmpty()) return;
    boolean hasSolo = stage.hasSoloLights();
    double minDimension = Math.max(1.0, Math.min(canvasWidth, canvasHeight));

    for (VnStagePreset.Light light : stage.getLights()) {
      if (light == null || light.layer() != layer || light.muted() || (hasSolo && !light.solo())) continue;
      VnStageLightingSupport.Rgba color = VnStageLightingSupport.Rgba.parse(
          light.color(), VnStageLightingSupport.Rgba.parse("#ffd7a8", VnStageLightingSupport.Rgba.WHITE));
      double alpha = layer == VnStagePreset.LightLayer.FOREGROUND
          ? VnStageLightingSupport.foregroundLightAlpha(light)
          : VnStageLightingSupport.backgroundLightAlpha(light);
      if (alpha <= 1e-6) continue;

      double targetX = light.sceneX() * canvasWidth;
      double targetY = light.sceneY() * canvasHeight;
      double sourceX = light.sourceX() * canvasWidth;
      double sourceY = light.sourceY() * canvasHeight;
      double radius = Math.max(10.0, light.radius() * minDimension);

      switch (light.type()) {
        case POLYGON -> drawPolygonLightOverlay(light, color, alpha, canvasWidth, canvasHeight);
        case CONE -> drawConeLightOverlay(sourceX, sourceY, targetX, targetY, radius, color, alpha);
        case STRIP, WINDOW ->
            drawStripLightOverlay(sourceX, sourceY, targetX, targetY, radius * 0.42, color, alpha);
        default -> drawRadialLightOverlay(targetX, targetY, radius, color, alpha);
      }
    }
  }

  /**
   * The original's {@code default} case filled an oval with a JavaFX {@code RadialGradient} of three
   * stops (alpha, alpha*0.55, 0). Here the same three stops go through
   * {@link Blitter2D#setFillRadialGradient}, gated behind {@link RenderFeature#RADIAL_GRADIENT}; a
   * backend without gradients degrades to a flat fill rather than dropping the light entirely.
   */
  private void drawRadialLightOverlay(
      double targetX, double targetY, double radius, VnStageLightingSupport.Rgba color, double alpha) {
    if (blitter.supports(RenderFeature.RADIAL_GRADIENT)) {
      double[] positions = {0.0, 0.42, 1.0};
      double[] colorsRgba = {
          color.r(), color.g(), color.b(), alpha,
          color.r(), color.g(), color.b(), alpha * 0.55,
          color.r(), color.g(), color.b(), 0.0
      };
      blitter.setFillRadialGradient(targetX, targetY, radius, positions, colorsRgba);
    } else {
      RenderDiagnostics.unsupported(blitter, RenderFeature.RADIAL_GRADIENT, "renderStageLightOverlays");
      blitter.setFill(color.r(), color.g(), color.b(), alpha * 0.55);
    }
    blitter.fillCircle(targetX, targetY, radius);
  }

  private void drawPolygonLightOverlay(
      VnStagePreset.Light light,
      VnStageLightingSupport.Rgba color,
      double alpha,
      double canvasWidth,
      double canvasHeight) {
    java.util.List<VnStagePreset.Point> polygon = light.polygon();
    if (polygon == null || polygon.size() < 3) return;
    double[] xy = new double[polygon.size() * 2];
    for (int i = 0; i < polygon.size(); i++) {
      xy[i * 2] = polygon.get(i).x() * canvasWidth;
      xy[i * 2 + 1] = polygon.get(i).y() * canvasHeight;
    }
    blitter.setFill(color.r(), color.g(), color.b(), alpha);
    blitter.fillPolygon(xy);
  }

  private void drawConeLightOverlay(
      double sourceX,
      double sourceY,
      double targetX,
      double targetY,
      double radius,
      VnStageLightingSupport.Rgba color,
      double alpha) {
    double dx = targetX - sourceX;
    double dy = targetY - sourceY;
    double length = Math.max(1.0, Math.hypot(dx, dy));
    double nx = dx / length;
    double ny = dy / length;
    double px = -ny;
    double py = nx;
    double endWidth = Math.max(18.0, radius * 0.48);
    double sourceWidth = Math.max(6.0, endWidth * 0.18);
    double[] xy = {
        sourceX + px * sourceWidth, sourceY + py * sourceWidth,
        sourceX - px * sourceWidth, sourceY - py * sourceWidth,
        targetX - px * endWidth, targetY - py * endWidth,
        targetX + px * endWidth, targetY + py * endWidth
    };
    blitter.setFill(color.r(), color.g(), color.b(), alpha);
    blitter.fillPolygon(xy);
  }

  private void drawStripLightOverlay(
      double sourceX,
      double sourceY,
      double targetX,
      double targetY,
      double halfWidth,
      VnStageLightingSupport.Rgba color,
      double alpha) {
    double dx = targetX - sourceX;
    double dy = targetY - sourceY;
    double length = Math.max(1.0, Math.hypot(dx, dy));
    double px = -dy / length;
    double py = dx / length;
    double[] xy = {
        sourceX + px * halfWidth, sourceY + py * halfWidth,
        sourceX - px * halfWidth, sourceY - py * halfWidth,
        targetX - px * halfWidth, targetY - py * halfWidth,
        targetX + px * halfWidth, targetY + py * halfWidth
    };
    blitter.setFill(color.r(), color.g(), color.b(), alpha);
    blitter.fillPolygon(xy);
  }

  // ─────────────────────────────────────────────────────────────────────────
  //  Stage-lit characters
  // ─────────────────────────────────────────────────────────────────────────

  @Override
  public void drawLitCharacter(
      String path,
      String spriteTag,
      double x,
      double y,
      double drawWidth,
      double drawHeight,
      double canvasWidth,
      double canvasHeight,
      @Nullable VnStagePreset stage) {
    if (path == null || path.isBlank() || drawWidth <= 0 || drawHeight <= 0) return;

    // With no active lights there is nothing to relight, and buildLitCharacter would hand back the
    // source array at its own dimensions rather than the requested draw size — a mismatch the
    // output target would reject. Draw unlit instead.
    if (stage == null || stage.getLights().isEmpty()) {
      blitter.drawImage(path, x, y, drawWidth, drawHeight);
      return;
    }

    String key = characterCacheKey(spriteTag, stage, x, y, drawWidth, drawHeight, canvasWidth, canvasHeight);
    if (drawCachedCharacter(key, x, y, drawWidth, drawHeight)) return;
    if (!blitter.supports(RenderFeature.PIXEL_ACCESS)) {
      RenderDiagnostics.unsupported(blitter, RenderFeature.PIXEL_ACCESS, "drawLitCharacter");
      blitter.drawImage(path, x, y, drawWidth, drawHeight);
      return;
    }

    Optional<double[]> dims = AssetDimensionProbe.dimensionsOf(assetCatalog, path, projectRoot);
    if (dims.isEmpty()) {
      BlitterMissingAssetPlaceholder.report(blitter, path, spriteTag, x, y, drawWidth, drawHeight);
      return;
    }
    int srcW = Math.max(1, (int) Math.round(dims.get()[0]));
    int srcH = Math.max(1, (int) Math.round(dims.get()[1]));

    int[] sourceArgb;
    RenderTarget2D sourceTarget = blitter.createRenderTarget(srcW, srcH, 1.0);
    try {
      sourceTarget.getBlitter().drawImage(path, 0, 0, srcW, srcH);
      sourceArgb = sourceTarget.readPixelsArgb();
    } finally {
      sourceTarget.close();
    }

    drawLit(sourceArgb, srcW, srcH, key, spriteTag, x, y,
        drawWidth, drawHeight, canvasWidth, canvasHeight, stage);
  }

  /**
   * Stage-lights an already-composited multi-layer sprite. Pixels are read straight off
   * {@code composite} rather than re-resolved from a path, because a composite has no single
   * source asset. The composite belongs to {@code VnCharacterCompositor}'s own cache, so it is
   * only read here and never closed.
   */
  @Override
  public void drawLitComposite(
      RenderTarget2D composite,
      String spriteTag,
      double x,
      double y,
      double drawWidth,
      double drawHeight,
      double canvasWidth,
      double canvasHeight,
      @Nullable VnStagePreset stage) {
    if (composite == null || !composite.isValid() || drawWidth <= 0 || drawHeight <= 0) return;

    // Same null-stage reasoning as drawLitCharacter; the unlit fallback for a composite is a
    // direct blit of the caller's target.
    if (stage == null || stage.getLights().isEmpty()) {
      blitter.drawRenderTarget(composite, x, y, drawWidth, drawHeight);
      return;
    }

    String key = characterCacheKey(spriteTag, stage, x, y, drawWidth, drawHeight, canvasWidth, canvasHeight);
    if (drawCachedCharacter(key, x, y, drawWidth, drawHeight)) return;
    if (!blitter.supports(RenderFeature.PIXEL_ACCESS)) {
      RenderDiagnostics.unsupported(blitter, RenderFeature.PIXEL_ACCESS, "drawLitComposite");
      blitter.drawRenderTarget(composite, x, y, drawWidth, drawHeight);
      return;
    }

    int srcW = Math.max(1, (int) Math.round(composite.getWidth()));
    int srcH = Math.max(1, (int) Math.round(composite.getHeight()));
    int[] sourceArgb = composite.readPixelsArgb();

    drawLit(sourceArgb, srcW, srcH, key, spriteTag, x, y,
        drawWidth, drawHeight, canvasWidth, canvasHeight, stage);
  }

  /**
   * Shared tail of both lit-character paths: relight the given source pixels, cache the output
   * target under {@code key}, and blit it. Only how the source pixels were obtained differs.
   */
  private void drawLit(
      int[] sourceArgb,
      int srcW,
      int srcH,
      String key,
      String spriteTag,
      double x,
      double y,
      double drawWidth,
      double drawHeight,
      double canvasWidth,
      double canvasHeight,
      @Nullable VnStagePreset stage) {
    stageLightingRecomposeCount++;
    int[] litArgb = VnStageLightingSupport.buildLitCharacter(
        sourceArgb, srcW, srcH, spriteTag, x, y, drawWidth, drawHeight, canvasWidth, canvasHeight, stage);
    int outW = Math.max(1, (int) Math.round(drawWidth));
    int outH = Math.max(1, (int) Math.round(drawHeight));
    RenderTarget2D outputTarget = blitter.createRenderTarget(outW, outH, 1.0);
    outputTarget.writePixelsArgb(litArgb);
    stageCharacterCache.put(key, outputTarget);
    blitter.drawRenderTarget(outputTarget, x, y, drawWidth, drawHeight);
  }

  private boolean drawCachedCharacter(String key, double x, double y, double drawWidth, double drawHeight) {
    RenderTarget2D cached = stageCharacterCache.get(key);
    if (cached == null || !cached.isValid()) return false;
    blitter.drawRenderTarget(cached, x, y, drawWidth, drawHeight);
    return true;
  }

  /**
   * Both lit-character paths share one cache and one key builder: each ultimately produces a lit
   * {@link RenderTarget2D} identified by sprite identity, stage, position and size. For a
   * composite the sprite identity is the layered path spec, which is also what
   * {@code VnCharacterCompositor} keys its own composite cache on, so distinct sprites cannot
   * collide.
   */
  private static String characterCacheKey(
      String spriteTag,
      @Nullable VnStagePreset stage,
      double x,
      double y,
      double drawWidth,
      double drawHeight,
      double canvasWidth,
      double canvasHeight) {
    return VnCharacterCompositor.stageCharacterCacheKey(
        spriteTag,
        stage == null ? "none" : stage.getCacheToken(),
        x, y, drawWidth, drawHeight, canvasWidth, canvasHeight);
  }

  static String stageBackgroundCacheKey(
      String backgroundPath, String stageCacheToken, int canvasWidth, int canvasHeight) {
    return backgroundPath
        + "|stage:" + stageCacheToken
        + "|size:" + canvasWidth + "x" + canvasHeight;
  }

  private static long renderTargetWeightBytes(RenderTarget2D target) {
    long w = Math.max(1L, Math.round(target.getWidth() * target.getPixelScale()));
    long h = Math.max(1L, Math.round(target.getHeight() * target.getPixelScale()));
    return w * h * 4L; // 4 bytes per packed ARGB pixel
  }
}
