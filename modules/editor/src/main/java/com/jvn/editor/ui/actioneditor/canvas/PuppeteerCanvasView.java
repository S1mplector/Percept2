package com.jvn.editor.ui.actioneditor.canvas;

import java.io.File;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.jvn.editor.ui.actioneditor.AnimationPreview;
import com.jvn.editor.ui.actioneditor.AnimationPreview.ScrollZoomMode;
import com.jvn.editor.ui.actioneditor.AnimationProject;
import com.jvn.editor.ui.ProjectViewportSpec;
import com.jvn.scripting.jes.runtime.JesScene2D;
// ProjectViewportSpec.Dimensions is the return type of getViewportDimensions()

import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Region;

/**
 * Thin façade over {@link AnimationPreview} that exposes a stable surface for
 * the controller to drive rendering without coupling to the full PuppeteerWindow.
 */
public final class PuppeteerCanvasView {

  private final AnimationPreview preview;

  public PuppeteerCanvasView(AnimationPreview preview) {
    if (preview == null) throw new IllegalArgumentException("preview must not be null");
    this.preview = preview;
  }

  // --- Lifecycle ---

  public Region asRegion() { return preview; }
  public Canvas getCanvas() { return preview.getPreviewCanvas(); }
  public void requestFocus() { preview.requestFocus(); }

  // --- Scene / project ---

  public void setScene(JesScene2D scene) { preview.setScene(scene); }
  public void setProject(AnimationProject project) { preview.setProject(project); }
  public void setProjectRoot(File root) { preview.setProjectRoot(root); }

  // --- Viewport ---

  public void setViewPanAndZoom(double panX, double panY, double zoom) {
    preview.setViewPanAndZoom(panX, panY, zoom);
  }

  public double getViewPanX() { return preview.getViewPanX(); }
  public double getViewPanY() { return preview.getViewPanY(); }
  public double getViewZoomFactor() { return preview.getViewZoomFactor(); }

  public ProjectViewportSpec.Dimensions getViewportDimensions() {
    return preview.getViewportDimensions();
  }

  public void fitToContent() { preview.fitToContent(); }

  // --- Selection ---

  public void selectEntity(String name) { preview.selectEntity(name); }
  public void selectGroup(String groupName) { preview.selectGroup(groupName); }
  public void clearSelection() { preview.clearSelection(); }
  public String getSelectedEntityName() { return preview.getSelectedEntityName(); }

  // --- Snapping ---

  public void setSnapToGridEnabled(boolean enabled) { preview.setSnapToGridEnabled(enabled); }
  public void setSnapToEntityEnabled(boolean enabled) { preview.setSnapToEntityEnabled(enabled); }

  // --- Overlay toggles ---

  public void setShowSafeGuides(boolean show) { preview.setShowSafeGuides(show); }
  public void setShowTitleGuides(boolean show) { preview.setShowTitleGuides(show); }
  public void setOnionSkinning(boolean enabled) { preview.setOnionSkinning(enabled); }
  public void setShowInterpolationGhosts(boolean show) { preview.setShowInterpolationGhosts(show); }

  public boolean isShowSafeGuides() { return preview.isShowSafeGuides(); }
  public boolean isShowTitleGuides() { return preview.isShowTitleGuides(); }
  public boolean isOnionSkinning() { return preview.isOnionSkinning(); }
  public boolean isShowInterpolationGhosts() { return preview.isShowInterpolationGhosts(); }

  // --- Orbit ---

  public void setOrbitToolEnabled(boolean enabled) { preview.setOrbitToolEnabled(enabled); }
  public boolean isOrbitToolEnabled() { return preview.isOrbitToolEnabled(); }
  public void setOrbitAlignRotation(boolean align) { preview.setOrbitAlignRotation(align); }
  public boolean isOrbitAlignRotation() { return preview.isOrbitAlignRotation(); }

  public void setOrbitAnchors(Map<String, double[]> anchors) { preview.setOrbitAnchors(anchors); }
  public void setOrbitAnchorSources(Map<String, String> sources) { preview.setOrbitAnchorSources(sources); }
  public void setOrbitAnchorSourceOffsets(Map<String, double[]> offsets) { preview.setOrbitAnchorSourceOffsets(offsets); }
  public void clearOrbitAnchorForSelectedEntity() { preview.clearOrbitAnchorForSelectedEntity(); }

  // --- Camera ---

  public Object getCamera() { return preview.getCamera(); }
  public void setRuntimeCameraSelected(boolean selected) { preview.setRuntimeCameraSelected(selected); }
  public void setScrollZoomMode(ScrollZoomMode mode) { preview.setScrollZoomMode(mode); }
  public ScrollZoomMode getScrollZoomMode() { return preview.getScrollZoomMode(); }

  // --- Callbacks ---

  public void setOnEntitySelected(Consumer<String> cb) { preview.setOnEntitySelected(cb); }
  public void setOnEntityMoved(BiConsumer<String, double[]> cb) { preview.setOnEntityMoved(cb); }
  public void setOnEntityMatrixChanged(BiConsumer<String, double[]> cb) { preview.setOnEntityMatrixChanged(cb); }
  public void setOnEntityMoveInteractionStarted(BiConsumer<String, double[]> cb) { preview.setOnEntityMoveInteractionStarted(cb); }
  public void setOnEntityMoveInteractionFinished(BiConsumer<String, double[]> cb) { preview.setOnEntityMoveInteractionFinished(cb); }
  public void setOnEntityRotationChanged(BiConsumer<String, Double> cb) { preview.setOnEntityRotationChanged(cb); }
  public void setOnEntityScaleChanged(BiConsumer<String, double[]> cb) { preview.setOnEntityScaleChanged(cb); }
  public void setOnEntityPivotChanged(BiConsumer<String, double[]> cb) { preview.setOnEntityPivotChanged(cb); }
  public void setOnCameraMoved(Consumer<double[]> cb) { preview.setOnCameraMoved(cb); }
  public void setOnCameraStateChanged(Consumer<double[]> cb) { preview.setOnCameraStateChanged(cb); }
  public void setOnCameraInteractionStarted(Runnable cb) { preview.setOnCameraInteractionStarted(cb); }
  public void setOnCameraInteractionFinished(Consumer<double[]> cb) { preview.setOnCameraInteractionFinished(cb); }
  public void setOnOrbitAnchorChanged(BiConsumer<String, double[]> cb) { preview.setOnOrbitAnchorChanged(cb); }
  public void setOnOrbitAnchorRemoved(Consumer<String> cb) { preview.setOnOrbitAnchorRemoved(cb); }
  public void setOnOrbitAnchorSourceChanged(BiConsumer<String, String> cb) { preview.setOnOrbitAnchorSourceChanged(cb); }
  public void setOnOrbitAnchorSourceOffsetChanged(BiConsumer<String, double[]> cb) { preview.setOnOrbitAnchorSourceOffsetChanged(cb); }
  // --- Sizing ---

  public void setMinWidth(double w) { preview.setMinWidth(w); }
  public void setMinHeight(double h) { preview.setMinHeight(h); }
  public double getWidth() { return preview.getWidth(); }
  public double getHeight() { return preview.getHeight(); }
}
