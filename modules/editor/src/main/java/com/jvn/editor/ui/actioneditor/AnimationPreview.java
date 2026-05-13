package com.jvn.editor.ui.actioneditor;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.jvn.core.graphics.Camera2D;
import com.jvn.core.graphics.ViewportScaler2D;
import com.jvn.core.input.Input;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.editor.ui.ProjectViewportSpec;
import com.jvn.fx.scene2d.FxBlitter2D;
import com.jvn.scripting.jes.runtime.JesScene2D;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class AnimationPreview extends VBox {
    private static final double PIVOT_MIN = 0.0;
    
    /**
     * Get the constrained transform for an entity at a given time.
     * Applies parent-child and look-at constraints if present.
     */
    private ConstraintEvaluator.ConstrainedTransform getConstrainedTransform(
            String entityName, double baseX, double baseY, double baseRotationDeg,
            double baseScaleX, double baseScaleY, double timeMs) {
        if (project == null) {
            return new ConstraintEvaluator.ConstrainedTransform(baseX, baseY, baseRotationDeg, baseScaleX, baseScaleY);
        }
        
        Constraint constraint = project.getConstraint(entityName);
        return ConstraintEvaluator.evaluate(entityName, baseX, baseY, baseRotationDeg, baseScaleX, baseScaleY,
            constraint, project, timeMs);
    }
    private static final double PIVOT_MAX = 1.0;
    private static final double TRANSFORM_EPSILON = 1e-6;
    private static final double VIEW_ZOOM_MIN = 0.35;
    private static final double VIEW_ZOOM_MAX = 6.0;
    private static final double MATRIX_GIZMO_HANDLE_RADIUS_SQ = 100.0;
    private static final double MATRIX_GIZMO_AXIS_MIN = 18.0;
    private static final double MATRIX_GIZMO_AXIS_MAX = 72.0;
    private static final double WORLD_POSITION_LIMIT = 100_000.0;
    private static final double MATRIX_COMPONENT_LIMIT = 50.0;

    public enum ScrollZoomMode {
        VIEW,
        CAMERA
    }

    private static final class EntityFrame {
        final double x;
        final double y;
        final double w;
        final double h;
        final double originX;
        final double originY;
        final double scaleX;
        final double scaleY;
        final double rotationRad;
        final double matrixMxx;
        final double matrixMxy;
        final double matrixMyx;
        final double matrixMyy;
        final double matrixTx;
        final double matrixTy;

        EntityFrame(double x, double y, double w, double h, double originX, double originY,
                    double scaleX, double scaleY, double rotationRad,
                    double matrixMxx, double matrixMxy, double matrixMyx, double matrixMyy,
                    double matrixTx, double matrixTy) {
            this.x = x;
            this.y = y;
            this.w = Math.max(TRANSFORM_EPSILON, w);
            this.h = Math.max(TRANSFORM_EPSILON, h);
            this.originX = originX;
            this.originY = originY;
            this.scaleX = normalizeScale(scaleX);
            this.scaleY = normalizeScale(scaleY);
            this.rotationRad = rotationRad;
            this.matrixMxx = matrixMxx;
            this.matrixMxy = matrixMxy;
            this.matrixMyx = matrixMyx;
            this.matrixMyy = matrixMyy;
            this.matrixTx = matrixTx;
            this.matrixTy = matrixTy;
        }
    }

    private enum MatrixHandleKind {
        TRANSLATE,
        X_AXIS,
        Y_AXIS
    }

    private static final class MatrixDragState {
        final EntityFrame frame;
        final MatrixHandleKind handleKind;
        final double axisLength;

        MatrixDragState(EntityFrame frame, MatrixHandleKind handleKind, double axisLength) {
            this.frame = frame;
            this.handleKind = handleKind;
            this.axisLength = Math.max(TRANSFORM_EPSILON, axisLength);
        }
    }

    private static final class RotateDragState {
        final double pivotX;
        final double pivotY;
        double lastMouseAngleRad;
        double accumulatedThetaRad = 0.0;
        double currentMouseWorldX;
        double currentMouseWorldY;
        final double baseRotationDeg;
        final java.util.Map<String, FollowerState> followers = new java.util.LinkedHashMap<>();

        RotateDragState(double px, double py, double angle, double rot) {
            this.pivotX = px;
            this.pivotY = py;
            this.lastMouseAngleRad = angle;
            this.baseRotationDeg = rot;
        }
    }

    private static final class FollowerState {
        final double startX;
        final double startY;
        final double startRotationDeg;

        FollowerState(double x, double y, double rot) {
            this.startX = x;
            this.startY = y;
            this.startRotationDeg = rot;
        }
    }

    private static final class PivotDragState {
        final double baseX;
        final double baseY;
        final double baseW;
        final double baseH;
        final double baseOriginX;
        final double baseOriginY;
        final double baseScaleX;
        final double baseScaleY;
        final double baseRotationRad;

        PivotDragState(EntityFrame frame) {
            this.baseX = frame.x;
            this.baseY = frame.y;
            this.baseW = frame.w;
            this.baseH = frame.h;
            this.baseOriginX = frame.originX;
            this.baseOriginY = frame.originY;
            this.baseScaleX = frame.scaleX;
            this.baseScaleY = frame.scaleY;
            this.baseRotationRad = frame.rotationRad;
        }
    }

    private static final class ScaleDragState {
        final double baseScaleX;
        final double baseScaleY;
        final double baseRotationRad;
        final double localCornerX; // corner X in pre-scale local space
        final double localCornerY;

        ScaleDragState(EntityFrame frame, int corner) {
            this.baseScaleX = frame.scaleX;
            this.baseScaleY = frame.scaleY;
            this.baseRotationRad = frame.rotationRad;
            double ox = frame.originX;
            double oy = frame.originY;
            this.localCornerX = (corner == 0 || corner == 3) ? -ox * frame.w : (1.0 - ox) * frame.w;
            this.localCornerY = (corner == 0 || corner == 1) ? -oy * frame.h : (1.0 - oy) * frame.h;
        }
    }

    private static final class BackgroundSourceCandidate {
        final Entity2D entity;
        final String imagePath;
        final double naturalWidth;
        final double naturalHeight;

        BackgroundSourceCandidate(Entity2D entity, String imagePath, double naturalWidth, double naturalHeight) {
            this.entity = entity;
            this.imagePath = imagePath;
            this.naturalWidth = naturalWidth;
            this.naturalHeight = naturalHeight;
        }
    }

    private final Canvas canvas;
    public Canvas getPreviewCanvas() { return canvas; }
    private final GraphicsContext gc;
    private final FxBlitter2D blitter;
    private final Input input;
    private final Camera2D camera;

    private JesScene2D scene;
    private AnimationProject project;
    private boolean onionSkinning = false;
    private int onionFrames = 3;
    private boolean showInterpolationGhosts = false;
    private boolean showSpriteRegions = false;
    private SpritePixelAnalyzer pixelAnalyzer;
    private final Map<String, List<SpritePixelAnalyzer.DetectedRegion>> spriteRegionCache = new HashMap<>();
    private int interpolationGhostCount = 5;

    private boolean anchorPlacementMode = false;
    private Consumer<double[]> onAnchorPlacementAt;

    private javafx.animation.AnimationTimer spriteRegionAnimTimer;
    private long spriteRegionLastRenderNs = 0;
    private static final long SPRITE_REGION_RENDER_INTERVAL_NS = 40_000_000L; // 25 fps for marching ants

    private Entity2D selectedEntity;
    private String selectedEntityName;
    private String selectedGroupName;
    private final Set<String> selectedGroupMemberNames = new HashSet<>();
    private boolean draggingEntity = false;
    private boolean draggingGroup = false;
    private boolean draggingPivot = false;
    private boolean draggingRotate = false;
    private boolean draggingScale = false;
    private ScaleDragState scaleDragState;
    private boolean draggingOrbit = false;
    private boolean draggingOrbitAnchor = false;
    private boolean draggingCameraFrame = false;
    private PivotDragState pivotDragState;
    private MatrixDragState matrixDragState;
    private RotateDragState rotateDragState;
    private double dragEntityStartX, dragEntityStartY;
    private double groupDragStartTrackX, groupDragStartTrackY;
    private double groupDragAccumX, groupDragAccumY;
    private double dragCameraStartX, dragCameraStartY;
    private boolean pivotAxisLocked = false;
    private boolean pivotAxisIsHorizontal = false;
    private double pivotDragStartWorldX, pivotDragStartWorldY;
    private String pivotOverlayText = null;
    private double pivotOverlayScreenX, pivotOverlayScreenY;
    private double orbitRadius = 0.0;
    private boolean orbitToolEnabled = false;
    private boolean orbitAlignRotation = true;
    private final Map<String, double[]> orbitAnchors = new HashMap<>();
    private final Map<String, String> orbitAnchorSources = new HashMap<>();
    private final Map<String, double[]> orbitAnchorSourceOffsets = new HashMap<>();

    private boolean snapToGridEnabled = false;
    private double snapGridSize = 10.0;
    private boolean snapToEntityEnabled = false;
    private static final double ENTITY_SNAP_THRESHOLD = 8.0;

    private Consumer<String> onEntitySelected;
    private Consumer<PuppeteerAssetTransfer.Payload> onAssetDropped;
    private BiConsumer<String, double[]> onEntityMoved;
    private BiConsumer<String, double[]> onEntityMoveInteractionStarted;
    private BiConsumer<String, double[]> onEntityMoveInteractionFinished;
    private BiConsumer<String, double[]> onEntityPivotChanged;
    private BiConsumer<String, Double> onEntityRotationChanged;
    private BiConsumer<String, double[]> onEntityScaleChanged;
    private BiConsumer<String, double[]> onEntityMatrixChanged;
    private BiConsumer<String, double[]> onOrbitAnchorChanged;
    private BiConsumer<String, String> onOrbitAnchorSourceChanged;
    private BiConsumer<String, double[]> onOrbitAnchorSourceOffsetChanged;
    private Consumer<String> onOrbitAnchorRemoved;
    private Consumer<double[]> onCameraStateChanged;
    private Runnable onCameraInteractionStarted;
    private Consumer<double[]> onCameraMoved;
    private Consumer<double[]> onCameraInteractionFinished;
    private ProjectViewportSpec.Dimensions viewportSpec =
        new ProjectViewportSpec.Dimensions(ProjectViewportSpec.DEFAULT_WIDTH, ProjectViewportSpec.DEFAULT_HEIGHT);
    private double viewportScale = 1.0;
    private double viewportOffsetX = 0.0;
    private double viewportOffsetY = 0.0;
    private double viewportLogicalWidth = ProjectViewportSpec.DEFAULT_WIDTH;
    private double viewportLogicalHeight = ProjectViewportSpec.DEFAULT_HEIGHT;
    private double displayScale = 1.0;
    private double displayOffsetX = 0.0;
    private double displayOffsetY = 0.0;
    private double displayMinX = 0.0;
    private double displayMinY = 0.0;
    private double displayWidth = ProjectViewportSpec.DEFAULT_WIDTH;
    private double displayHeight = ProjectViewportSpec.DEFAULT_HEIGHT;
    private double viewZoomFactor = 1.0;
    private double viewPanX = 0.0;
    private double viewPanY = 0.0;

    public double getViewPanX() { return viewPanX; }
    public double getViewPanY() { return viewPanY; }
    public double getViewZoomFactor() { return viewZoomFactor; }

    public void setViewPanAndZoom(double panX, double panY, double zoomFactor) {
        this.viewPanX = panX;
        this.viewPanY = panY;
        this.viewZoomFactor = Math.max(VIEW_ZOOM_MIN, Math.min(VIEW_ZOOM_MAX, zoomFactor));
        if (getParent() != null) getParent().requestLayout();
        render();
    }
    private ScrollZoomMode scrollZoomMode = ScrollZoomMode.VIEW;
    private java.io.File projectRoot;
    private final Map<String, double[]> sourceImageSizeCache = new HashMap<>();
    private final Map<String, Image> sourceImageCache = new HashMap<>();
    private final Set<String> missingSourceImagePaths = new HashSet<>();
    private boolean moveInteractionActive = false;
    private String moveInteractionEntityName = null;
    private double moveInteractionStartX = 0.0;
    private double moveInteractionStartY = 0.0;
    private boolean assetDropHighlightActive = false;
    private String assetDropLabel = null;
    private boolean runtimeCameraSelected = false;
    private boolean showSafeGuides = true;
    private boolean showTitleGuides = true;
    private String matrixOverlayText = null;
    private double matrixOverlayScreenX = 0.0;
    private double matrixOverlayScreenY = 0.0;

    public void setProjectRoot(java.io.File root) {
        projectRoot = root;
        pixelAnalyzer = new SpritePixelAnalyzer(root);
        spriteRegionCache.clear();
        sourceImageSizeCache.clear();
        sourceImageCache.clear();
        missingSourceImagePaths.clear();
        blitter.setProjectRoot(root);
        viewportSpec = ProjectViewportSpec.resolve(root);
        render();
    }

    public AnimationPreview() {
        canvas = new Canvas(600, 300);
        gc = canvas.getGraphicsContext2D();
        blitter = new FxBlitter2D(gc);
        input = new Input();
        camera = new Camera2D();

        Pane container = new Pane(canvas);
        VBox.setVgrow(container, Priority.ALWAYS);

        getChildren().add(container);
        setStyle("-fx-background-color: #121212;");

        widthProperty().addListener((obs, oldVal, newVal) -> {
            canvas.setWidth(newVal.doubleValue());
            render();
        });

        heightProperty().addListener((obs, oldVal, newVal) -> {
            canvas.setHeight(newVal.doubleValue());
            render();
        });

        setupMouseControls();
        render();
    }

    public void setScene(JesScene2D scene) {
        this.scene = scene;
        if (scene != null) {
            scene.setInput(input);
            scene.setCamera(camera);
            Set<String> names = new HashSet<>(scene.names());
            java.util.List<String> removed = new java.util.ArrayList<>();
            orbitAnchors.keySet().removeIf(name -> {
                boolean drop = !names.contains(name);
                if (drop) removed.add(name);
                return drop;
            });
            orbitAnchorSources.entrySet().removeIf(entry -> {
                String target = entry.getKey();
                String source = entry.getValue();
                return target == null || !names.contains(target) || source == null || !names.contains(source);
            });
            orbitAnchorSourceOffsets.keySet().removeIf(name -> !orbitAnchorSources.containsKey(name));
            if (onOrbitAnchorRemoved != null) {
                for (String name : removed) onOrbitAnchorRemoved.accept(name);
            }
            if (selectedEntityName != null) {
                if (!names.contains(selectedEntityName)) {
                    selectedEntity = null;
                    selectedEntityName = null;
                } else {
                    selectedEntity = scene.find(selectedEntityName);
                }
            }
            if (selectedGroupName != null) {
                refreshSelectedGroupMembers();
                if (selectedGroupMemberNames.isEmpty()) {
                    selectedGroupName = null;
                }
            }
        } else {
            selectedEntity = null;
            selectedEntityName = null;
            selectedGroupName = null;
            selectedGroupMemberNames.clear();
            if (onOrbitAnchorRemoved != null) {
                for (String name : orbitAnchors.keySet()) onOrbitAnchorRemoved.accept(name);
            }
            orbitAnchors.clear();
            orbitAnchorSources.clear();
            orbitAnchorSourceOffsets.clear();
        }
        pivotDragState = null;
        matrixDragState = null;
        matrixOverlayText = null;
        cancelMoveInteraction();
        render();
    }

    public void setProject(AnimationProject project) {
        this.project = project;
        if (project != null) {
            setOrbitAnchors(project.getOrbitAnchorsView());
            setOrbitAnchorSources(project.getOrbitAnchorSourcesView());
            setOrbitAnchorSourceOffsets(project.getOrbitAnchorSourceOffsetsView());
        } else {
            setOrbitAnchors(Collections.emptyMap());
            setOrbitAnchorSources(Collections.emptyMap());
            setOrbitAnchorSourceOffsets(Collections.emptyMap());
        }
    }

    public JesScene2D getJesScene() { return scene; }
    public Camera2D getCamera() { return camera; }
    public ProjectViewportSpec.Dimensions getViewportDimensions() {
        return viewportSpec != null
            ? viewportSpec
            : new ProjectViewportSpec.Dimensions(ProjectViewportSpec.DEFAULT_WIDTH, ProjectViewportSpec.DEFAULT_HEIGHT);
    }

    public void render() {
        double w = Math.max(1.0, canvas.getWidth());
        double h = Math.max(1.0, canvas.getHeight());
        updateViewportTransform(w, h);

        gc.setFill(Color.web("#121212"));
        gc.fillRect(0, 0, w, h);

        gc.save();
        gc.translate(displayOffsetX, displayOffsetY);
        gc.scale(displayScale, displayScale);
        gc.translate(-displayMinX, -displayMinY);

        gc.setFill(Color.web("#0e0e0e"));
        gc.fillRect(displayMinX, displayMinY, displayWidth, displayHeight);
        drawGrid(displayMinX, displayMinY, displayWidth, displayHeight);

        blitter.setViewport(viewportLogicalWidth, viewportLogicalHeight);
        if (scene != null) {
            drawBackgroundSourceOverflowReference();
            Camera2D activeCamera = scene.getCamera();
            scene.setCamera(null);
            try {
                if (onionSkinning && project != null) drawOnionSkins();
                if (showInterpolationGhosts && project != null) drawInterpolationGhosts();
                scene.render(blitter, viewportLogicalWidth, viewportLogicalHeight);
                drawMotionPaths();
                if (selectedEntity != null) drawSelectionHighlight(selectedEntity);
                if (selectedGroupName != null && !selectedGroupMemberNames.isEmpty()) {
                    for (String memberName : selectedGroupMemberNames) {
                        Entity2D member = scene.find(memberName);
                        if (member != null) drawSelectionHighlight(member);
                    }
                }
                drawGroupDimming();
                drawSelectedEntityRegionBorder();
                if (showSpriteRegions) drawSpriteRegions();
            } finally {
                scene.setCamera(activeCamera);
            }
        } else {
            gc.setFill(Color.web("#a0a0a0"));
            gc.fillText("No scene loaded", displayMinX + displayWidth / 2 - 40, displayMinY + displayHeight / 2);
        }
        gc.restore();
        drawRuntimeFrame();
        drawCameraHud();
        drawAssetDropOverlay();

        if (pivotOverlayText != null) {
            gc.setFont(javafx.scene.text.Font.font("Monospaced", 11));
            double textW = pivotOverlayText.length() * 7.0;
            double textH = 16.0;
            double ox = Math.min(pivotOverlayScreenX, w - textW - 6);
            double oy = Math.max(pivotOverlayScreenY, textH + 4);
            gc.setFill(Color.web("#1a1a1a", 0.85));
            gc.fillRoundRect(ox - 4, oy - textH + 2, textW + 8, textH + 4, 4, 4);
            gc.setFill(Color.web("#f7d07a"));
            gc.fillText(pivotOverlayText, ox, oy);
        }
        if (matrixOverlayText != null) {
            gc.setFont(javafx.scene.text.Font.font("Monospaced", 11));
            double textW = matrixOverlayText.length() * 7.0;
            double textH = 16.0;
            double ox = Math.min(matrixOverlayScreenX, w - textW - 6);
            double oy = Math.max(matrixOverlayScreenY, textH + 4);
            gc.setFill(Color.web("#121820", 0.88));
            gc.fillRoundRect(ox - 4, oy - textH + 2, textW + 8, textH + 4, 4, 4);
            gc.setFill(Color.web("#8bd2ff"));
            gc.fillText(matrixOverlayText, ox, oy);
        }
        if (anchorPlacementMode) {
            String hint = selectedEntity != null
                ? "Anchor Placement  —  click on the entity to place  |  Esc to cancel"
                : "Anchor Placement  —  select an entity first";
            gc.setFont(javafx.scene.text.Font.font("Monospaced", 11));
            double textW = hint.length() * 6.6;
            gc.setFill(Color.web("#1a0e2a", 0.88));
            gc.fillRoundRect(10, h - 28, textW + 16, 20, 6, 6);
            gc.setFill(Color.web("#c084fc", 0.97));
            gc.fillText(hint, 18, h - 13);
        }
    }

    private void drawRuntimeFrame() {
        double z = Math.max(0.0001, camera.getZoom());
        double left = camera.getX();
        double top = camera.getY();
        double right = left + viewportLogicalWidth / z;
        double bottom = top + viewportLogicalHeight / z;
        double x0 = worldToScreenX(left);
        double y0 = worldToScreenY(top);
        double x1 = worldToScreenX(right);
        double y1 = worldToScreenY(bottom);
        double x = Math.min(x0, x1);
        double y = Math.min(y0, y1);
        double frameW = Math.abs(x1 - x0);
        double frameH = Math.abs(y1 - y0);
        Color frameColor = runtimeCameraSelected
            ? Color.web("#ffb067", 0.98)
            : Color.web("#ff4d4d", 0.95);

        gc.save();
        if (showSafeGuides) {
            drawGuideRect(x, y, frameW, frameH, 0.10, Color.web("#ffd27a", runtimeCameraSelected ? 0.42 : 0.28));
        }
        if (showTitleGuides) {
            drawGuideRect(x, y, frameW, frameH, 0.18, Color.web("#9bd6ff", runtimeCameraSelected ? 0.34 : 0.22));
        }

        gc.setStroke(Color.web("#ffffff", 0.15));
        gc.setLineWidth(1.0);
        gc.setLineDashes(4.0, 4.0);
        double thirdW = frameW / 3.0;
        double thirdH = frameH / 3.0;
        gc.strokeLine(x + thirdW, y, x + thirdW, y + frameH);
        gc.strokeLine(x + 2 * thirdW, y, x + 2 * thirdW, y + frameH);
        gc.strokeLine(x, y + thirdH, x + frameW, y + thirdH);
        gc.strokeLine(x, y + 2 * thirdH, x + frameW, y + 2 * thirdH);
        gc.setLineDashes((double[]) null);

        gc.setStroke(frameColor);
        gc.setLineWidth(2.0);
        gc.strokeRect(x + 0.5, y + 0.5, Math.max(0, frameW - 1.0), Math.max(0, frameH - 1.0));
        drawRuntimeFrameHandle(x, y, frameColor);
        gc.setFill(runtimeCameraSelected ? Color.web("#ffd2a8", 0.95) : Color.web("#ff7a7a", 0.9));
        gc.setFont(javafx.scene.text.Font.font("Monospaced", 10));
        gc.fillText("Runtime Frame", x + 20, Math.max(12, y - 6));
        gc.restore();
    }

    private void drawGuideRect(double x, double y, double frameW, double frameH, double insetRatio, Color color) {
        double insetX = frameW * insetRatio;
        double insetY = frameH * insetRatio;
        double guideX = x + insetX;
        double guideY = y + insetY;
        double guideW = Math.max(0.0, frameW - insetX * 2.0);
        double guideH = Math.max(0.0, frameH - insetY * 2.0);
        gc.setStroke(color);
        gc.setLineWidth(1.0);
        gc.setLineDashes(6.0, 4.0);
        gc.strokeRect(guideX + 0.5, guideY + 0.5, Math.max(0.0, guideW - 1.0), Math.max(0.0, guideH - 1.0));
        gc.setLineDashes((double[]) null);
    }

    private void drawRuntimeFrameHandle(double x, double y, Color frameColor) {
        double handleSize = 12.0;
        double handleX = x + 5.0;
        double handleY = y + 5.0;
        gc.setFill(Color.web("#050505", 0.82));
        gc.fillRoundRect(handleX, handleY, handleSize, handleSize, 3, 3);
        gc.setStroke(frameColor);
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(handleX + 0.5, handleY + 0.5, handleSize - 1.0, handleSize - 1.0, 3, 3);
        gc.strokeLine(handleX + 4.0, handleY + 6.0, handleX + 8.0, handleY + 6.0);
        gc.strokeLine(handleX + 6.0, handleY + 4.0, handleX + 6.0, handleY + 8.0);
    }

    private void drawCameraHud() {
        String modeText = scrollZoomMode == ScrollZoomMode.CAMERA ? "Camera Zoom" : "View Zoom";
        String hud = String.format(
            "Wheel: %s  |  Cam (x=%.1f y=%.1f z=%.2f)  |  View %.2fx",
            modeText, camera.getX(), camera.getY(), camera.getZoom(), viewZoomFactor
        );
        gc.save();
        gc.setFont(javafx.scene.text.Font.font("Monospaced", 10));
        double width = Math.max(280.0, hud.length() * 6.0 + 12.0);
        gc.setFill(Color.web("#050505", 0.75));
        gc.fillRoundRect(8, 8, width, 18, 6, 6);
        gc.setFill(Color.web("#d3dae8", 0.95));
        gc.fillText(hud, 14, 21);
        gc.restore();
    }

    private void drawAssetDropOverlay() {
        if (!assetDropHighlightActive) return;
        double x = displayOffsetX;
        double y = displayOffsetY;
        double w = Math.max(1.0, displayWidth * displayScale);
        double h = Math.max(1.0, displayHeight * displayScale);
        gc.save();
        gc.setFill(Color.web("#4da3ff", 0.08));
        gc.fillRoundRect(x + 2.0, y + 2.0, Math.max(0.0, w - 4.0), Math.max(0.0, h - 4.0), 14.0, 14.0);
        gc.setStroke(Color.web("#7bc0ff", 0.95));
        gc.setLineWidth(2.0);
        gc.setLineDashes(10.0, 6.0);
        gc.strokeRoundRect(x + 2.0, y + 2.0, Math.max(0.0, w - 4.0), Math.max(0.0, h - 4.0), 14.0, 14.0);
        gc.setLineDashes((double[]) null);
        String label = assetDropLabel == null || assetDropLabel.isBlank()
            ? "Drop to add asset"
            : assetDropLabel;
        gc.setFill(Color.web("#d9ecff", 0.96));
        gc.setFont(javafx.scene.text.Font.font("Monospaced", 12));
        gc.fillText(label, x + 18.0, y + 28.0);
        gc.restore();
    }

    private void updateViewportTransform(double canvasW, double canvasH) {
        if (viewportSpec == null) {
            viewportSpec = new ProjectViewportSpec.Dimensions(ProjectViewportSpec.DEFAULT_WIDTH, ProjectViewportSpec.DEFAULT_HEIGHT);
        }
        viewportLogicalWidth = Math.max(1.0, viewportSpec.width());
        viewportLogicalHeight = Math.max(1.0, viewportSpec.height());

        double[] bounds = computeDisplayBoundsWorld();
        double boundsMinX = bounds[0];
        double boundsMinY = bounds[1];
        double boundsMaxX = bounds[2];
        double boundsMaxY = bounds[3];
        double boundsWidth = Math.max(1.0, boundsMaxX - boundsMinX);
        double boundsHeight = Math.max(1.0, boundsMaxY - boundsMinY);

        var fit = ViewportScaler2D.fit(boundsWidth, boundsHeight, canvasW, canvasH);
        double fitScale = Math.max(1e-6, fit.scale());
        displayScale = fitScale * viewZoomFactor;

        double baseCenterX = (boundsMinX + boundsMaxX) * 0.5;
        double baseCenterY = (boundsMinY + boundsMaxY) * 0.5;
        double centerX = baseCenterX + viewPanX;
        double centerY = baseCenterY + viewPanY;

        displayWidth = Math.max(1.0, canvasW / Math.max(1e-6, displayScale));
        displayHeight = Math.max(1.0, canvasH / Math.max(1e-6, displayScale));
        displayMinX = centerX - displayWidth * 0.5;
        displayMinY = centerY - displayHeight * 0.5;
        displayOffsetX = 0.0;
        displayOffsetY = 0.0;

        // Keep legacy fields aligned with current visible world transform.
        viewportScale = displayScale;
        viewportOffsetX = worldToScreenX(camera.getX());
        viewportOffsetY = worldToScreenY(camera.getY());
    }

    private double[] computeDisplayBoundsWorld() {
        double z = Math.max(0.0001, camera.getZoom());
        double runtimeLeft = camera.getX();
        double runtimeTop = camera.getY();
        double runtimeRight = runtimeLeft + viewportLogicalWidth / z;
        double runtimeBottom = runtimeTop + viewportLogicalHeight / z;

        double minX = runtimeLeft;
        double minY = runtimeTop;
        double maxX = runtimeRight;
        double maxY = runtimeBottom;

        if (scene != null) {
            for (Entity2D entity : scene.getChildren()) {
                if (entity == null || !entity.isVisible()) continue;
                double[] corners = getEntityCorners(entity);
                if (corners.length < 8) continue;
                for (int i = 0; i < 4; i++) {
                    double x = corners[i * 2];
                    double y = corners[i * 2 + 1];
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
            includeFullBackgroundSourceBounds(minX, minY, maxX, maxY);
            minX = backgroundBoundsAccumulator[0];
            minY = backgroundBoundsAccumulator[1];
            maxX = backgroundBoundsAccumulator[2];
            maxY = backgroundBoundsAccumulator[3];
        }

        double width = Math.max(1.0, maxX - minX);
        double height = Math.max(1.0, maxY - minY);
        double padX = Math.max(24.0, width * 0.06);
        double padY = Math.max(24.0, height * 0.06);
        return new double[] { minX - padX, minY - padY, maxX + padX, maxY + padY };
    }

    private final double[] backgroundBoundsAccumulator = new double[4];

    private void includeFullBackgroundSourceBounds(double minX, double minY, double maxX, double maxY) {
        backgroundBoundsAccumulator[0] = minX;
        backgroundBoundsAccumulator[1] = minY;
        backgroundBoundsAccumulator[2] = maxX;
        backgroundBoundsAccumulator[3] = maxY;
        BackgroundSourceCandidate candidate = findBestBackgroundSourceCandidate();
        if (candidate == null) return;
        EntityFrame frame = describeEntity(candidate.entity);
        if (frame == null) return;

        EntityFrame fullSourceFrame = new EntityFrame(
            frame.x,
            frame.y,
            candidate.naturalWidth,
            candidate.naturalHeight,
            frame.originX,
            frame.originY,
            frame.scaleX,
            frame.scaleY,
            frame.rotationRad,
            frame.matrixMxx,
            frame.matrixMxy,
            frame.matrixMyx,
            frame.matrixMyy,
            frame.matrixTx,
            frame.matrixTy
        );
        double[] corners = computeWorldCorners(fullSourceFrame);
        for (int i = 0; i < 4; i++) {
            double x = corners[i * 2];
            double y = corners[i * 2 + 1];
            backgroundBoundsAccumulator[0] = Math.min(backgroundBoundsAccumulator[0], x);
            backgroundBoundsAccumulator[1] = Math.min(backgroundBoundsAccumulator[1], y);
            backgroundBoundsAccumulator[2] = Math.max(backgroundBoundsAccumulator[2], x);
            backgroundBoundsAccumulator[3] = Math.max(backgroundBoundsAccumulator[3], y);
        }
    }

    private BackgroundSourceCandidate findBestBackgroundSourceCandidate() {
        if (scene == null) return null;
        BackgroundSourceCandidate best = null;
        double bestRank = -1.0;

        for (Entity2D entity : scene.getChildren()) {
            if (!(entity instanceof com.jvn.core.scene2d.Sprite2D sprite)) continue;
            String path = sprite.getImagePath();
            if (path == null || path.isBlank()) continue;

            double[] natural = resolveImageSize(path);
            if (natural == null) continue;

            double spriteW = Math.max(1.0, sprite.getWidth());
            double spriteH = Math.max(1.0, sprite.getHeight());
            boolean hasOverflow = natural[0] > spriteW * 1.01 || natural[1] > spriteH * 1.01;
            if (!hasOverflow) continue;

            boolean likelyBackground = isLikelyBackground(path)
                || spriteW * spriteH >= viewportLogicalWidth * viewportLogicalHeight * 0.35;
            if (!likelyBackground) continue;

            double rank = natural[0] * natural[1];
            if (best == null || rank > bestRank) {
                bestRank = rank;
                best = new BackgroundSourceCandidate(entity, path, natural[0], natural[1]);
            }
        }
        return best;
    }

    private void drawBackgroundSourceOverflowReference() {
        BackgroundSourceCandidate candidate = findBestBackgroundSourceCandidate();
        if (candidate == null) return;

        Image image = resolveSourceImage(candidate.imagePath);
        if (image == null) return;

        EntityFrame frame = describeEntity(candidate.entity);
        if (frame == null) return;

        double left = -frame.originX * candidate.naturalWidth;
        double top = -frame.originY * candidate.naturalHeight;

        gc.save();
        gc.translate(frame.x, frame.y);
        gc.rotate(Math.toDegrees(frame.rotationRad));
        gc.scale(frame.scaleX, frame.scaleY);
        gc.setGlobalAlpha(0.38);
        gc.drawImage(image, left, top, candidate.naturalWidth, candidate.naturalHeight);
        gc.setGlobalAlpha(1.0);
        gc.restore();
    }

    private boolean isLikelyBackground(String path) {
        String p = path == null ? "" : path.toLowerCase();
        return p.contains("background") || p.contains("/bg") || p.contains("_bg") || p.contains("backdrop");
    }

    private double[] resolveImageSize(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) return null;
        String key = imagePath.trim();
        if (sourceImageSizeCache.containsKey(key)) {
            double[] cached = sourceImageSizeCache.get(key);
            return cached[0] > 0 && cached[1] > 0 ? cached : null;
        }
        Image image = resolveSourceImage(key);
        double[] size;
        if (image != null && image.getWidth() > 1 && image.getHeight() > 1) {
            size = new double[] { image.getWidth(), image.getHeight() };
        } else {
            size = new double[] { -1, -1 };
        }
        sourceImageSizeCache.put(key, size);
        return size[0] > 0 && size[1] > 0 ? size : null;
    }

    private Image resolveSourceImage(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) return null;
        String key = imagePath.trim();
        Image cached = sourceImageCache.get(key);
        if (cached != null) return cached;
        if (missingSourceImagePaths.contains(key)) return null;

        Image loaded = loadImageByPath(key);
        if (loaded != null && loaded.getWidth() > 1 && loaded.getHeight() > 1) {
            sourceImageCache.put(key, loaded);
            return loaded;
        }
        missingSourceImagePaths.add(key);
        return null;
    }

    private Image loadImageByPath(String path) {
        try {
            java.net.URL classpath = getClass().getClassLoader().getResource(path);
            if (classpath != null) return new Image(classpath.toExternalForm(), false);
        } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
        }
        try {
            java.io.File absolute = new java.io.File(path);
            if (absolute.isAbsolute() && absolute.isFile()) {
                return new Image(absolute.toURI().toString(), false);
            }
        } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
        }
        try {
            if (projectRoot != null) {
                java.io.File relative = new java.io.File(projectRoot, path);
                if (relative.isFile()) return new Image(relative.toURI().toString(), false);
            }
        } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
        }
        return null;
    }

    private void drawMotionPaths() {
        if (project == null) return;
        double z = Math.max(1e-6, displayScale);

        gc.save();
        applyCameraTransform();

        for (EntityTrack track : project.getTracks()) {
            java.util.List<SplinePath.Point> controlPoints =
                SplinePath.buildControlPoints(track, project.getTotalDurationMs());
            if (controlPoints.size() < 2) continue;

            java.util.List<SplinePath.Point> curve = SplinePath.catmullRom(controlPoints, 16);
            boolean isSelectedPath = selectedEntityName != null
                && selectedEntityName.equals(track.getEntityName());
            Color stroke = isSelectedPath
                ? Color.web("#8bd2ff", 0.92)
                : Color.web("#4da3ff", 0.28);
            Color pointFill = isSelectedPath
                ? Color.web("#f0b673", 0.95)
                : Color.web("#4da3ff", 0.55);

            gc.setStroke(stroke);
            gc.setLineWidth((isSelectedPath ? 2.5 : 1.25) / z);
            gc.setLineDashes(4.0 / z, 3.0 / z);
            gc.beginPath();
            boolean first = true;
            for (SplinePath.Point pt : curve) {
                if (first) { gc.moveTo(pt.x, pt.y); first = false; }
                else gc.lineTo(pt.x, pt.y);
            }
            gc.stroke();
            gc.setLineDashes((double[]) null);

            gc.setFill(pointFill);
            for (SplinePath.Point pt : controlPoints) {
                double radius = (isSelectedPath ? 4.0 : 2.5) / z;
                gc.fillOval(pt.x - radius, pt.y - radius, radius * 2.0, radius * 2.0);
            }
        }
        gc.restore();
    }

    private void drawOnionSkins() {
        if (project == null) return;
        double z = Math.max(1e-6, displayScale);
        double now = project.getPlayheadMs();
        double dur = project.getTotalDurationMs();
        double step = 66.666; // Fixed 15 FPS equivalent

        gc.save();
        applyCameraTransform();

        for (EntityTrack track : project.getTracks()) {
            java.util.List<Keyframe> xFrames = track.getKeyframes(PropertyType.X);
            java.util.List<Keyframe> yFrames = track.getKeyframes(PropertyType.Y);
            if (xFrames.isEmpty() && yFrames.isEmpty()) continue;

            boolean hasPivotX = !track.getKeyframes(PropertyType.PIVOT_X).isEmpty();
            boolean hasPivotY = !track.getKeyframes(PropertyType.PIVOT_Y).isEmpty();
            boolean hasRotation = !track.getKeyframes(PropertyType.ROTATION).isEmpty();
            boolean hasScaleX = !track.getKeyframes(PropertyType.SCALE_X).isEmpty();
            boolean hasScaleY = !track.getKeyframes(PropertyType.SCALE_Y).isEmpty();

            Entity2D entity = scene != null ? scene.find(track.getEntityName()) : null;
            Image spriteImage = null;
            double entityW = 20.0 / z;
            double entityH = 20.0 / z;
            if (entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
                entityW = Math.max(1.0, sprite.getWidth());
                entityH = Math.max(1.0, sprite.getHeight());
                spriteImage = resolveSourceImage(sprite.getImagePath());
            }

            for (int i = -onionFrames; i <= onionFrames; i++) {
                if (i == 0) continue;
                double t = now + i * step;
                if (t < 0 || t > dur) continue;

                double baseX = track.getValueAt(PropertyType.X, t);
                double baseY = track.getValueAt(PropertyType.Y, t);
                double baseRot = hasRotation ? track.getValueAt(PropertyType.ROTATION, t) : 0.0;
                double baseScaleX = hasScaleX ? track.getValueAt(PropertyType.SCALE_X, t) : 1.0;
                double baseScaleY = hasScaleY ? track.getValueAt(PropertyType.SCALE_Y, t) : 1.0;
                
                ConstraintEvaluator.ConstrainedTransform constrained = getConstrainedTransform(
                    track.getEntityName(), baseX, baseY, baseRot, baseScaleX, baseScaleY, t);
                double x = constrained.x;
                double y = constrained.y;
                double rot = Math.toRadians(constrained.rotationDeg);
                double sX = constrained.scaleX;
                double sY = constrained.scaleY;

                double alpha = 0.3 * (1.0 - Math.abs(i) / (double)(onionFrames + 1));
                Color color = i < 0 ? Color.web("#f38ba8", alpha) : Color.web("#58d68d", alpha);

                gc.setStroke(color);
                gc.setLineWidth(1.5 / z);

                if (hasPivotX || hasPivotY || hasRotation || hasScaleX || hasScaleY) {
                    double pivX = hasPivotX ? track.getValueAt(PropertyType.PIVOT_X, t) : 0.5;
                    double pivY = hasPivotY ? track.getValueAt(PropertyType.PIVOT_Y, t) : 0.5;

                    gc.save();
                    gc.translate(x, y);
                    gc.rotate(Math.toDegrees(rot));
                    gc.scale(sX, sY);
                    
                    double offX = -pivX * entityW;
                    double offY = -pivY * entityH;
                    
                    if (spriteImage != null) {
                        gc.setGlobalAlpha(alpha + 0.1);
                        gc.drawImage(spriteImage, offX, offY, entityW, entityH);
                        gc.setStroke(color);
                        gc.setLineWidth(2.0 / z);
                        gc.strokeRect(offX, offY, entityW, entityH);
                    } else {
                        gc.strokeRect(offX, offY, entityW, entityH);
                    }

                    gc.setFill(color);
                    double pivDot = 2.0 / z / Math.max(0.01, Math.max(Math.abs(sX), Math.abs(sY)));
                    gc.fillOval(-pivDot, -pivDot, pivDot * 2, pivDot * 2);
                    gc.restore();
                } else {
                    double offX = -0.5 * entityW;
                    double offY = -0.5 * entityH;
                    if (spriteImage != null) {
                        gc.setGlobalAlpha(alpha + 0.1);
                        gc.drawImage(spriteImage, x + offX, y + offY, entityW, entityH);
                        gc.setStroke(color);
                        gc.setLineWidth(2.0 / z);
                        gc.strokeRect(x + offX, y + offY, entityW, entityH);
                    } else {
                        gc.strokeRect(x + offX, y + offY, entityW, entityH);
                    }
                }

                gc.setFill(color);
                gc.setFont(javafx.scene.text.Font.font(8.0 / z));
                gc.fillText(String.format("%.0f", t), x + entityW / 2 + (2.0 / z), y);
            }
        }
        gc.restore();
    }

    private void drawInterpolationGhosts() {
        if (project == null || selectedEntityName == null || selectedEntityName.isBlank()) return;

        EntityTrack track = project.getTrack(selectedEntityName);
        if (track == null) return;

        java.util.List<Keyframe> xKeyframes = track.getKeyframes(PropertyType.X);
        if (xKeyframes.isEmpty()) return;

        double now = project.getPlayheadMs();
        double dur = project.getTotalDurationMs();

        // Sort keyframes by time
        xKeyframes.sort(java.util.Comparator.comparingDouble(Keyframe::getTimeMs));

        // Find previous and next keyframes around current playhead
        Keyframe prevKfX = null, nextKfX = null;
        for (Keyframe kf : xKeyframes) {
            if (kf.getTimeMs() <= now) {
                if (prevKfX == null || kf.getTimeMs() > prevKfX.getTimeMs()) {
                    prevKfX = kf;
                }
            } else {
                if (nextKfX == null || kf.getTimeMs() < nextKfX.getTimeMs()) {
                    nextKfX = kf;
                }
            }
        }

        // If playhead is before all keyframes, use first two keyframes
        if (prevKfX == null && nextKfX != null && xKeyframes.size() >= 2) {
            prevKfX = xKeyframes.get(0);
            nextKfX = xKeyframes.get(1);
        }
        // If playhead is after all keyframes, use last two keyframes
        else if (nextKfX == null && prevKfX != null && xKeyframes.size() >= 2) {
            prevKfX = xKeyframes.get(xKeyframes.size() - 2);
            nextKfX = xKeyframes.get(xKeyframes.size() - 1);
        }

        if (prevKfX == null || nextKfX == null) return;
        
        double startTime = prevKfX.getTimeMs();
        double endTime = nextKfX.getTimeMs();
        double span = endTime - startTime;
        if (span <= 0) return;
        
        double z = Math.max(1e-6, displayScale);
        int ghostCount = Math.max(2, Math.min(20, interpolationGhostCount));
        
        gc.save();
        applyCameraTransform();
        
        Entity2D entity = scene != null ? scene.find(selectedEntityName) : null;
        Image spriteImage = null;
        double entityW = 20.0 / z;
        double entityH = 20.0 / z;
        if (entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
            entityW = Math.max(1.0, sprite.getWidth());
            entityH = Math.max(1.0, sprite.getHeight());
            spriteImage = resolveSourceImage(sprite.getImagePath());
        }
        
        boolean hasPivotX = !track.getKeyframes(PropertyType.PIVOT_X).isEmpty();
        boolean hasPivotY = !track.getKeyframes(PropertyType.PIVOT_Y).isEmpty();
        boolean hasRotation = !track.getKeyframes(PropertyType.ROTATION).isEmpty();
        boolean hasScaleX = !track.getKeyframes(PropertyType.SCALE_X).isEmpty();
        boolean hasScaleY = !track.getKeyframes(PropertyType.SCALE_Y).isEmpty();
        
        // Render ghost frames between keyframes
        for (int i = 1; i <= ghostCount; i++) {
            double t = startTime + (span * i / (ghostCount + 1));
            if (t < 0 || t > dur) continue;
            
            double baseX = track.getValueAt(PropertyType.X, t);
            double baseY = track.getValueAt(PropertyType.Y, t);
            double baseRot = hasRotation ? track.getValueAt(PropertyType.ROTATION, t) : 0.0;
            double baseScaleX = hasScaleX ? track.getValueAt(PropertyType.SCALE_X, t) : 1.0;
            double baseScaleY = hasScaleY ? track.getValueAt(PropertyType.SCALE_Y, t) : 1.0;
            
            ConstraintEvaluator.ConstrainedTransform constrained = getConstrainedTransform(
                track.getEntityName(), baseX, baseY, baseRot, baseScaleX, baseScaleY, t);
            double x = constrained.x;
            double y = constrained.y;
            double rot = Math.toRadians(constrained.rotationDeg);
            double sX = constrained.scaleX;
            double sY = constrained.scaleY;
            
            // Opacity fades toward the edges
            double alpha = 0.4 * (1.0 - Math.abs(i - (ghostCount + 1) / 2.0) / ((ghostCount + 1) / 2.0));
            Color ghostColor = Color.web("#89b4fa", alpha);
            
            gc.setStroke(ghostColor);
            gc.setLineWidth(1.5 / z);
            
            if (hasPivotX || hasPivotY || hasRotation || hasScaleX || hasScaleY) {
                double pivX = hasPivotX ? track.getValueAt(PropertyType.PIVOT_X, t) : 0.5;
                double pivY = hasPivotY ? track.getValueAt(PropertyType.PIVOT_Y, t) : 0.5;
                
                gc.save();
                gc.translate(x, y);
                gc.rotate(Math.toDegrees(rot));
                gc.scale(sX, sY);
                
                double offX = -pivX * entityW;
                double offY = -pivY * entityH;
                
                if (spriteImage != null) {
                    gc.setGlobalAlpha(alpha + 0.1);
                    gc.drawImage(spriteImage, offX, offY, entityW, entityH);
                    gc.setStroke(ghostColor);
                    gc.setLineWidth(2.0 / z);
                    gc.strokeRect(offX, offY, entityW, entityH);
                } else {
                    gc.strokeRect(offX, offY, entityW, entityH);
                }
                
                gc.setFill(ghostColor);
                double pivDot = 2.0 / z / Math.max(0.01, Math.max(Math.abs(sX), Math.abs(sY)));
                gc.fillOval(-pivDot, -pivDot, pivDot * 2, pivDot * 2);
                gc.restore();
            } else {
                double offX = -0.5 * entityW;
                double offY = -0.5 * entityH;
                if (spriteImage != null) {
                    gc.setGlobalAlpha(alpha + 0.1);
                    gc.drawImage(spriteImage, x + offX, y + offY, entityW, entityH);
                    gc.setStroke(ghostColor);
                    gc.setLineWidth(2.0 / z);
                    gc.strokeRect(x + offX, y + offY, entityW, entityH);
                } else {
                    gc.strokeRect(x + offX, y + offY, entityW, entityH);
                }
            }
            
            // Draw time label on ghost frame
            gc.setFill(ghostColor);
            gc.setFont(javafx.scene.text.Font.font(8.0 / z));
            gc.fillText(String.format("%.0f", t), x + entityW / 2 + (2.0 / z), y);
        }
        
        gc.restore();
    }

    private void drawGrid(double minX, double minY, double w, double h) {
        double step = 100.0;
        if (step * displayScale < 8.0) return;
        double maxX = minX + w;
        double maxY = minY + h;
        double startX = Math.floor(minX / step) * step;
        double startY = Math.floor(minY / step) * step;

        gc.setStroke(Color.color(1, 1, 1, 0.04));
        gc.setLineWidth(1.0 / Math.max(1e-6, displayScale));
        for (double x = startX; x <= maxX; x += step) {
            gc.strokeLine(x, minY, x, maxY);
        }
        for (double y = startY; y <= maxY; y += step) {
            gc.strokeLine(minX, y, maxX, y);
        }
    }

    public void setOnEntitySelected(Consumer<String> callback) { this.onEntitySelected = callback; }
    public void setOnAssetDropped(Consumer<PuppeteerAssetTransfer.Payload> callback) { this.onAssetDropped = callback; }
    public void setOnEntityMoved(BiConsumer<String, double[]> callback) { this.onEntityMoved = callback; }
    public void setOnEntityMoveInteractionStarted(BiConsumer<String, double[]> callback) { this.onEntityMoveInteractionStarted = callback; }
    public void setOnEntityMoveInteractionFinished(BiConsumer<String, double[]> callback) { this.onEntityMoveInteractionFinished = callback; }
    public void setOnEntityPivotChanged(BiConsumer<String, double[]> callback) { this.onEntityPivotChanged = callback; }
    public void setOnEntityRotationChanged(BiConsumer<String, Double> callback) { this.onEntityRotationChanged = callback; }
    public void setOnEntityScaleChanged(BiConsumer<String, double[]> callback) { this.onEntityScaleChanged = callback; }
    public void setOnEntityMatrixChanged(BiConsumer<String, double[]> callback) { this.onEntityMatrixChanged = callback; }
    public void setOnOrbitAnchorChanged(BiConsumer<String, double[]> callback) { this.onOrbitAnchorChanged = callback; }
    public void setOnOrbitAnchorSourceChanged(BiConsumer<String, String> callback) { this.onOrbitAnchorSourceChanged = callback; }
    public void setOnOrbitAnchorSourceOffsetChanged(BiConsumer<String, double[]> callback) { this.onOrbitAnchorSourceOffsetChanged = callback; }
    public void setOnOrbitAnchorRemoved(Consumer<String> callback) { this.onOrbitAnchorRemoved = callback; }
    public void setOnCameraStateChanged(Consumer<double[]> callback) { this.onCameraStateChanged = callback; }
    public void setOnCameraInteractionStarted(Runnable callback) { this.onCameraInteractionStarted = callback; }
    public void setOnCameraMoved(Consumer<double[]> callback) { this.onCameraMoved = callback; }
    public void setOnCameraInteractionFinished(Consumer<double[]> callback) { this.onCameraInteractionFinished = callback; }

    public Entity2D getSelectedEntity() { return selectedEntity; }
    public String getSelectedEntityName() { return selectedEntityName; }
    public String getSelectedGroupName() { return selectedGroupName; }

    public void selectEntity(String entityName) {
        if (entityName == null || entityName.isBlank() || scene == null) {
            clearSelection();
            return;
        }
        Entity2D entity = scene.find(entityName);
        if (entity == null) {
            clearSelection();
            return;
        }
        boolean changed = entity != selectedEntity || !entityName.equals(selectedEntityName);
        selectedEntity = entity;
        selectedEntityName = entityName;
        selectedGroupName = null;
        selectedGroupMemberNames.clear();
        pivotDragState = null;
        matrixDragState = null;
        matrixOverlayText = null;
        startSpriteRegionAnimation();
        if (changed) render();
    }

    public void selectGroup(String groupName) {
        if (groupName == null || groupName.isBlank() || project == null || project.getGroup(groupName) == null) {
            clearSelection();
            return;
        }
        Set<String> nextMembers = new HashSet<>(project.collectGroupEntityNames(groupName));
        boolean changed = !groupName.equals(selectedGroupName) || !nextMembers.equals(selectedGroupMemberNames);
        selectedEntity = null;
        selectedEntityName = null;
        selectedGroupName = groupName;
        selectedGroupMemberNames.clear();
        selectedGroupMemberNames.addAll(nextMembers);
        pivotDragState = null;
        matrixDragState = null;
        matrixOverlayText = null;
        stopSpriteRegionAnimation();
        if (changed) render();
    }

    public void clearSelection() {
        boolean changed = selectedEntity != null || selectedEntityName != null || selectedGroupName != null || !selectedGroupMemberNames.isEmpty();
        selectedEntity = null;
        selectedEntityName = null;
        selectedGroupName = null;
        selectedGroupMemberNames.clear();
        pivotDragState = null;
        scaleDragState = null;
        matrixDragState = null;
        matrixOverlayText = null;
        draggingScale = false;
        stopSpriteRegionAnimation();
        if (changed) render();
    }

    private void startSpriteRegionAnimation() {
        if (spriteRegionAnimTimer != null) return;
        spriteRegionAnimTimer = new javafx.animation.AnimationTimer() {
            @Override public void handle(long now) {
                if (now - spriteRegionLastRenderNs >= SPRITE_REGION_RENDER_INTERVAL_NS) {
                    spriteRegionLastRenderNs = now;
                    render();
                }
            }
        };
        spriteRegionAnimTimer.start();
    }

    private void stopSpriteRegionAnimation() {
        if (spriteRegionAnimTimer != null) {
            spriteRegionAnimTimer.stop();
            spriteRegionAnimTimer = null;
        }
    }

    public boolean isOnionSkinning() { return onionSkinning; }
    public void setOnionSkinning(boolean onionSkinning) { this.onionSkinning = onionSkinning; render(); }
    public void setOnionFrames(int frames) { this.onionFrames = Math.max(1, Math.min(10, frames)); }
    public boolean isShowInterpolationGhosts() { return showInterpolationGhosts; }
    public void setShowInterpolationGhosts(boolean show) { this.showInterpolationGhosts = show; render(); }

    public boolean isShowSpriteRegions() { return showSpriteRegions; }
    public void setShowSpriteRegions(boolean show) { this.showSpriteRegions = show; render(); }
    public int getInterpolationGhostCount() { return interpolationGhostCount; }

    public boolean isAnchorPlacementMode() { return anchorPlacementMode; }
    public void setAnchorPlacementMode(boolean enabled) { anchorPlacementMode = enabled; render(); }
    public void setOnAnchorPlacementAt(Consumer<double[]> callback) { this.onAnchorPlacementAt = callback; }

    /**
     * Computes the world-space position of a named anchor on the given entity and sets
     * it as that entity's orbit pivot, enabling rotation around the anchor point.
     */
    public void useAnchorAsOrbitPivot(String entityName, String anchorName) {
        if (entityName == null || anchorName == null || project == null || scene == null) return;
        Entity2D entity = scene.find(entityName);
        if (entity == null) return;
        Anchor anchor = project.getAnchor(entityName, anchorName);
        if (anchor == null) return;
        EntityFrame frame = describeEntity(entity);
        if (frame == null) return;
        double[] worldPos = computeAnchorWorldPosition(anchor, entity, frame);
        setOrbitAnchor(entityName, worldPos[0], worldPos[1]);
        render();
    }

    private double[] computeAnchorWorldPosition(Anchor anchor, Entity2D entity, EntityFrame frame) {
        double ex = entity.getX();
        double ey = entity.getY();
        if (anchor.isRelative()) {
            double cos = Math.cos(frame.rotationRad);
            double sin = Math.sin(frame.rotationRad);
            double lx = (anchor.getX() - frame.originX) * frame.w * frame.scaleX;
            double ly = (anchor.getY() - frame.originY) * frame.h * frame.scaleY;
            return new double[]{ex + lx * cos - ly * sin, ey + lx * sin + ly * cos};
        } else {
            return new double[]{anchor.getX(), anchor.getY()};
        }
    }
    public void setInterpolationGhostCount(int count) { this.interpolationGhostCount = Math.max(2, Math.min(20, count)); render(); }
    public boolean isOrbitToolEnabled() { return orbitToolEnabled; }
    public void setOrbitToolEnabled(boolean enabled) { this.orbitToolEnabled = enabled; render(); }
    public boolean isOrbitAlignRotation() { return orbitAlignRotation; }
    public void setOrbitAlignRotation(boolean enabled) { this.orbitAlignRotation = enabled; }

    public boolean isSnapToGridEnabled() { return snapToGridEnabled; }
    public void setSnapToGridEnabled(boolean enabled) { this.snapToGridEnabled = enabled; }
    public double getSnapGridSize() { return snapGridSize; }
    public void setSnapGridSize(double size) { this.snapGridSize = Math.max(1, size); }
    public boolean isSnapToEntityEnabled() { return snapToEntityEnabled; }
    public void setSnapToEntityEnabled(boolean enabled) { this.snapToEntityEnabled = enabled; }
    public ScrollZoomMode getScrollZoomMode() { return scrollZoomMode; }
    public void setScrollZoomMode(ScrollZoomMode mode) {
        this.scrollZoomMode = mode == null ? ScrollZoomMode.VIEW : mode;
        render();
    }

    public void setRuntimeCameraSelected(boolean selected) {
        if (runtimeCameraSelected == selected) return;
        runtimeCameraSelected = selected;
        render();
    }

    public void setShowSafeGuides(boolean enabled) {
        if (showSafeGuides == enabled) return;
        showSafeGuides = enabled;
        render();
    }

    public boolean isShowSafeGuides() {
        return showSafeGuides;
    }

    public void setShowTitleGuides(boolean enabled) {
        if (showTitleGuides == enabled) return;
        showTitleGuides = enabled;
        render();
    }

    public boolean isShowTitleGuides() {
        return showTitleGuides;
    }

    private double[] applySnap(double x, double y) {
        if (snapToGridEnabled && snapGridSize > 0) {
            x = Math.round(x / snapGridSize) * snapGridSize;
            y = Math.round(y / snapGridSize) * snapGridSize;
        }
        if (snapToEntityEnabled && scene != null && selectedEntityName != null) {
            double bestDx = Double.MAX_VALUE, bestDy = Double.MAX_VALUE;
            double snapX = x, snapY = y;
            for (var entry : scene.exportNamed().entrySet()) {
                if (entry.getKey().equals(selectedEntityName)) continue;
                Entity2D other = entry.getValue();
                if (other == null) continue;
                double dx = Math.abs(other.getX() - x);
                double dy = Math.abs(other.getY() - y);
                if (dx < ENTITY_SNAP_THRESHOLD && dx < bestDx) { bestDx = dx; snapX = other.getX(); }
                if (dy < ENTITY_SNAP_THRESHOLD && dy < bestDy) { bestDy = dy; snapY = other.getY(); }
            }
            x = snapX;
            y = snapY;
        }
        return new double[]{x, y};
    }
    public boolean hasSelectedOrbitAnchor() {
        return selectedEntityName != null && orbitAnchors.containsKey(selectedEntityName);
    }

    public Map<String, double[]> getOrbitAnchorsSnapshot() {
        Map<String, double[]> copy = new HashMap<>();
        for (Map.Entry<String, double[]> entry : orbitAnchors.entrySet()) {
            double[] value = entry.getValue();
            if (value == null || value.length < 2) continue;
            copy.put(entry.getKey(), new double[]{value[0], value[1]});
        }
        return copy;
    }

    public Map<String, String> getOrbitAnchorSourcesSnapshot() {
        return new HashMap<>(orbitAnchorSources);
    }

    public void setOrbitAnchors(Map<String, double[]> anchors) {
        orbitAnchors.clear();
        if (anchors == null || anchors.isEmpty()) {
            orbitAnchorSources.clear();
            orbitAnchorSourceOffsets.clear();
            render();
            return;
        }
        for (Map.Entry<String, double[]> entry : anchors.entrySet()) {
            String name = entry.getKey();
            double[] value = entry.getValue();
            if (name == null || name.isBlank() || value == null || value.length < 2) continue;
            if (!Double.isFinite(value[0]) || !Double.isFinite(value[1])) continue;
            orbitAnchors.put(name, new double[]{value[0], value[1]});
        }
        orbitAnchorSources.keySet().removeIf(name -> !orbitAnchors.containsKey(name));
        orbitAnchorSourceOffsets.keySet().removeIf(name -> !orbitAnchorSources.containsKey(name));
        render();
    }

    public void setOrbitAnchorSources(Map<String, String> sources) {
        orbitAnchorSources.clear();
        if (sources != null && !sources.isEmpty()) {
            for (Map.Entry<String, String> entry : sources.entrySet()) {
                String target = entry.getKey();
                String source = entry.getValue();
                if (target == null || target.isBlank() || source == null || source.isBlank()) continue;
                if (target.equals(source)) continue;
                if (!orbitAnchors.containsKey(target)) continue;
                orbitAnchorSources.put(target, source);
                orbitAnchorSourceOffsets.putIfAbsent(target, new double[]{0.0, 0.0});
            }
        }
        orbitAnchorSourceOffsets.keySet().removeIf(name -> !orbitAnchorSources.containsKey(name));
        render();
    }

    public void setOrbitAnchorSourceOffsets(Map<String, double[]> offsets) {
        orbitAnchorSourceOffsets.clear();
        if (offsets != null && !offsets.isEmpty()) {
            for (Map.Entry<String, double[]> entry : offsets.entrySet()) {
                String target = entry.getKey();
                double[] value = entry.getValue();
                if (target == null || target.isBlank() || value == null || value.length < 2) continue;
                if (!Double.isFinite(value[0]) || !Double.isFinite(value[1])) continue;
                if (!orbitAnchorSources.containsKey(target)) continue;
                orbitAnchorSourceOffsets.put(target, new double[]{value[0], value[1]});
            }
        }
        for (String target : orbitAnchorSources.keySet()) {
            orbitAnchorSourceOffsets.putIfAbsent(target, new double[]{0.0, 0.0});
        }
        render();
    }

    public void clearOrbitAnchorForSelectedEntity() {
        if (selectedEntityName == null) return;
        orbitAnchors.remove(selectedEntityName);
        orbitAnchorSources.remove(selectedEntityName);
        orbitAnchorSourceOffsets.remove(selectedEntityName);
        if (onOrbitAnchorRemoved != null) onOrbitAnchorRemoved.accept(selectedEntityName);
        if (onOrbitAnchorSourceOffsetChanged != null) {
            onOrbitAnchorSourceOffsetChanged.accept(selectedEntityName, null);
        }
        render();
    }

    private boolean resolveEntityLocked(String entityName) {
        if (project == null || entityName == null || entityName.isBlank()) return false;
        EntityTrack track = project.getTrack(entityName);
        if (track != null && track.isLocked()) return true;
        if (track != null && track.hasParent()) {
            EntityGroup parent = project.getGroup(track.getParentGroupName());
            if (parent != null && parent.isLocked()) return true;
        }
        return false;
    }

    private String findEntityNameAt(double screenX, double screenY, boolean updateSelection) {
        if (scene == null) return null;
        if (!isInsideViewport(screenX, screenY)) {
            if (updateSelection) {
                selectedEntity = null;
                selectedEntityName = null;
            }
            return null;
        }
        double[] world = screenToWorld(screenX, screenY);
        double worldX = world[0];
        double worldY = world[1];

        String bestName = null;
        Entity2D bestEntity = null;
        int bestLayer = Integer.MIN_VALUE;
        int bestRenderOrder = Integer.MIN_VALUE;
        var named = scene.exportNamed();
        var children = scene.getChildren();
        Map<Entity2D, Integer> renderOrder = new IdentityHashMap<>();
        for (int i = 0; i < children.size(); i++) {
            renderOrder.put(children.get(i), i);
        }
        for (var entry : named.entrySet()) {
            String name = entry.getKey();
            if (resolveEntityLocked(name)) continue;
            Entity2D entity = entry.getValue();
            if (entity == null) continue;
            double[] corners = getEntityCorners(entity);
            if (containsPointInQuad(corners, worldX, worldY)) {
                int layer = project != null
                    ? project.computeEffectiveLayerOrder(name)
                    : (int) Math.round(entity.getZ());
                int order = renderOrder.getOrDefault(entity, Integer.MIN_VALUE);
                if (bestEntity == null || layer > bestLayer || (layer == bestLayer && order > bestRenderOrder)) {
                    bestName = name;
                    bestEntity = entity;
                    bestLayer = layer;
                    bestRenderOrder = order;
                }
            }
        }
        if (updateSelection) {
            selectedEntity = bestEntity;
            selectedEntityName = bestName;
        }
        return bestName;
    }

    private EntityFrame describeEntity(Entity2D entity) {
        if (entity == null) return null;
        double w = 40.0;
        double h = 40.0;
        if (entity instanceof com.jvn.core.scene2d.Sprite2D sprite) {
            w = sprite.getWidth();
            h = sprite.getHeight();
        } else if (entity instanceof com.jvn.core.scene2d.CharacterEntity2D character) {
            w = character.getDrawWidth();
            h = character.getDrawHeight();
        } else if (entity instanceof com.jvn.core.scene2d.Panel2D panel) {
            w = panel.getWidth();
            h = panel.getHeight();
        } else if (entity instanceof com.jvn.core.scene2d.SpriteAnimation2D) {
            w = 40.0;
            h = 40.0;
        }
        return new EntityFrame(
            entity.getX(),
            entity.getY(),
            w,
            h,
            entity.getOriginX(),
            entity.getOriginY(),
            entity.getScaleX(),
            entity.getScaleY(),
            Math.toRadians(entity.getRotationDeg()),
            entity.getMatrixMxx(),
            entity.getMatrixMxy(),
            entity.getMatrixMyx(),
            entity.getMatrixMyy(),
            entity.getMatrixTx(),
            entity.getMatrixTy()
        );
    }

    private double[] getEntityCorners(Entity2D entity) {
        EntityFrame frame = describeEntity(entity);
        if (frame == null) return new double[0];
        return computeWorldCorners(frame);
    }

    private static double[] computeWorldCorners(EntityFrame frame) {
        double left = -frame.originX * frame.w;
        double top = -frame.originY * frame.h;
        double right = left + frame.w;
        double bottom = top + frame.h;

        double[] corners = new double[8];
        fillCorner(corners, 0, frame, left, top);
        fillCorner(corners, 2, frame, right, top);
        fillCorner(corners, 4, frame, right, bottom);
        fillCorner(corners, 6, frame, left, bottom);
        return corners;
    }

    private static void fillCorner(double[] out, int offset, EntityFrame frame, double localX, double localY) {
        double mx = frame.matrixMxx * localX + frame.matrixMxy * localY + frame.matrixTx;
        double my = frame.matrixMyx * localX + frame.matrixMyy * localY + frame.matrixTy;
        double sx = mx * frame.scaleX;
        double sy = my * frame.scaleY;
        double cos = Math.cos(frame.rotationRad);
        double sin = Math.sin(frame.rotationRad);
        out[offset] = frame.x + sx * cos - sy * sin;
        out[offset + 1] = frame.y + sx * sin + sy * cos;
    }

    private static double[] transformMatrixPointToWorld(EntityFrame frame, double localX, double localY) {
        double[] out = new double[2];
        fillCorner(out, 0, frame, localX, localY);
        return out;
    }

    private static double[] inverseStandardTransform(EntityFrame frame, double worldX, double worldY) {
        double dx = worldX - frame.x;
        double dy = worldY - frame.y;
        double cos = Math.cos(frame.rotationRad);
        double sin = Math.sin(frame.rotationRad);
        double unrotX = cos * dx + sin * dy;
        double unrotY = -sin * dx + cos * dy;
        return new double[] {
            unrotX / frame.scaleX,
            unrotY / frame.scaleY
        };
    }

    private static double resolveMatrixAxisLength(EntityFrame frame) {
        if (frame == null) return MATRIX_GIZMO_AXIS_MIN;
        double basis = Math.min(Math.max(frame.w, TRANSFORM_EPSILON), Math.max(frame.h, TRANSFORM_EPSILON));
        return clamp(basis * 0.32, MATRIX_GIZMO_AXIS_MIN, MATRIX_GIZMO_AXIS_MAX);
    }

    private static boolean containsPointInQuad(double[] corners, double x, double y) {
        if (corners == null || corners.length < 8) return false;
        double sign = 0.0;
        for (int i = 0; i < 4; i++) {
            int i0 = i * 2;
            int i1 = ((i + 1) % 4) * 2;
            double x0 = corners[i0];
            double y0 = corners[i0 + 1];
            double x1 = corners[i1];
            double y1 = corners[i1 + 1];
            double cross = (x1 - x0) * (y - y0) - (y1 - y0) * (x - x0);
            if (Math.abs(cross) <= TRANSFORM_EPSILON) continue;
            double current = Math.signum(cross);
            if (sign == 0.0) {
                sign = current;
            } else if (current != sign) {
                return false;
            }
        }
        return true;
    }

    private boolean isInsideViewport(double screenX, double screenY) {
        double right = displayOffsetX + displayWidth * displayScale;
        double bottom = displayOffsetY + displayHeight * displayScale;
        return screenX >= displayOffsetX && screenX <= right && screenY >= displayOffsetY && screenY <= bottom;
    }

    private boolean isInsideRuntimeFrame(double screenX, double screenY) {
        double z = Math.max(0.0001, camera.getZoom());
        double left = worldToScreenX(camera.getX());
        double top = worldToScreenY(camera.getY());
        double right = worldToScreenX(camera.getX() + viewportLogicalWidth / z);
        double bottom = worldToScreenY(camera.getY() + viewportLogicalHeight / z);
        double minX = Math.min(left, right);
        double minY = Math.min(top, bottom);
        double maxX = Math.max(left, right);
        double maxY = Math.max(top, bottom);
        return screenX >= minX && screenX <= maxX && screenY >= minY && screenY <= maxY;
    }

    private boolean isNearRuntimeFrameHandle(double screenX, double screenY) {
        double handleX = Math.min(worldToScreenX(camera.getX()),
            worldToScreenX(camera.getX() + viewportLogicalWidth / Math.max(0.0001, camera.getZoom()))) + 5.0;
        double handleY = Math.min(worldToScreenY(camera.getY()),
            worldToScreenY(camera.getY() + viewportLogicalHeight / Math.max(0.0001, camera.getZoom()))) + 5.0;
        double size = 12.0;
        return screenX >= handleX && screenX <= handleX + size
            && screenY >= handleY && screenY <= handleY + size;
    }

    private double screenToViewportX(double screenX) {
        return displayMinX + (screenX - displayOffsetX) / Math.max(1e-6, displayScale);
    }

    private double screenToViewportY(double screenY) {
        return displayMinY + (screenY - displayOffsetY) / Math.max(1e-6, displayScale);
    }

    private double[] screenToWorld(double screenX, double screenY) {
        return new double[]{ screenToViewportX(screenX), screenToViewportY(screenY) };
    }

    private boolean hasOrbitAnchor(String entityName) {
        return entityName != null && orbitAnchors.containsKey(entityName);
    }

    private double[] getOrbitAnchor(String entityName) {
        if (entityName == null) return null;
        String sourceName = orbitAnchorSources.get(entityName);
        if (sourceName != null && !sourceName.isBlank() && scene != null) {
            Entity2D source = scene.find(sourceName);
            if (source != null) {
                double sx = source.getX();
                double sy = source.getY();
                if (Double.isFinite(sx) && Double.isFinite(sy)) {
                    double[] offset = orbitAnchorSourceOffsets.get(entityName);
                    double ox = (offset != null && offset.length >= 2 && Double.isFinite(offset[0])) ? offset[0] : 0.0;
                    double oy = (offset != null && offset.length >= 2 && Double.isFinite(offset[1])) ? offset[1] : 0.0;
                    return new double[]{sx + ox, sy + oy};
                }
            }
        }
        double[] anchor = orbitAnchors.get(entityName);
        if (anchor == null || anchor.length < 2) return null;
        return anchor;
    }

    private void setOrbitAnchor(String entityName, double worldX, double worldY) {
        if (entityName == null || entityName.isBlank()) return;
        if (!Double.isFinite(worldX) || !Double.isFinite(worldY)) return;
        if (scene != null && scene.find(entityName) == null) return;
        orbitAnchors.put(entityName, new double[]{worldX, worldY});
        orbitAnchorSources.remove(entityName);
        orbitAnchorSourceOffsets.remove(entityName);
        if (onOrbitAnchorChanged != null) {
            onOrbitAnchorChanged.accept(entityName, new double[]{worldX, worldY});
        }
        if (onOrbitAnchorSourceChanged != null) {
            onOrbitAnchorSourceChanged.accept(entityName, null);
        }
        if (onOrbitAnchorSourceOffsetChanged != null) {
            onOrbitAnchorSourceOffsetChanged.accept(entityName, null);
        }
    }

    private void setOrbitAnchorSource(String entityName, String sourceEntityName) {
        if (scene == null) return;
        Entity2D source = scene.find(sourceEntityName);
        if (source == null) return;
        setOrbitAnchorSource(entityName, sourceEntityName, source.getX(), source.getY());
    }

    private void setOrbitAnchorSource(String entityName, String sourceEntityName, double anchorWorldX, double anchorWorldY) {
        if (entityName == null || entityName.isBlank()) return;
        if (sourceEntityName == null || sourceEntityName.isBlank()) return;
        if (entityName.equals(sourceEntityName)) return;
        if (scene == null) return;
        Entity2D target = scene.find(entityName);
        Entity2D source = scene.find(sourceEntityName);
        if (target == null || source == null) return;
        double sx = source.getX();
        double sy = source.getY();
        if (!Double.isFinite(sx) || !Double.isFinite(sy)) return;
        double anchorX = Double.isFinite(anchorWorldX) ? anchorWorldX : sx;
        double anchorY = Double.isFinite(anchorWorldY) ? anchorWorldY : sy;
        double offsetX = anchorX - sx;
        double offsetY = anchorY - sy;
        orbitAnchors.put(entityName, new double[]{anchorX, anchorY});
        orbitAnchorSources.put(entityName, sourceEntityName);
        orbitAnchorSourceOffsets.put(entityName, new double[]{offsetX, offsetY});
        if (onOrbitAnchorChanged != null) {
            onOrbitAnchorChanged.accept(entityName, new double[]{anchorX, anchorY});
        }
        if (onOrbitAnchorSourceChanged != null) {
            onOrbitAnchorSourceChanged.accept(entityName, sourceEntityName);
        }
        if (onOrbitAnchorSourceOffsetChanged != null) {
            onOrbitAnchorSourceOffsetChanged.accept(entityName, new double[]{offsetX, offsetY});
        }
    }

    private boolean isNearOrbitAnchorHandle(double screenX, double screenY) {
        double[] anchor = getOrbitAnchor(selectedEntityName);
        if (anchor == null) return false;
        double ax = worldToScreenX(anchor[0]);
        double ay = worldToScreenY(anchor[1]);
        double pointerX = screenX;
        double pointerY = screenY;
        double dx = pointerX - ax;
        double dy = pointerY - ay;
        return dx * dx + dy * dy <= 100;
    }

    private boolean isNearRotateHandle(double screenX, double screenY) {
        if (selectedEntity == null) return false;
        double px = worldToScreenX(selectedEntity.getX());
        double py = worldToScreenY(selectedEntity.getY());
        double dist = Math.hypot(screenX - px, screenY - py);
        double targetDist = 45.0; // distance of rotation handle from pivot
        return Math.abs(dist - targetDist) <= 8.0;
    }

    private boolean shouldAllowMatrixTranslateHandle(boolean altDown) {
        return altDown || (selectedEntity != null && selectedEntity.hasSupplementalTransform());
    }

    private boolean isNearMatrixHandle(double screenX, double screenY, MatrixHandleKind handleKind) {
        if (selectedEntity == null || handleKind == null) return false;
        EntityFrame frame = describeEntity(selectedEntity);
        if (frame == null) return false;
        double axisLength = resolveMatrixAxisLength(frame);
        double[] point = switch (handleKind) {
            case TRANSLATE -> transformMatrixPointToWorld(frame, 0.0, 0.0);
            case X_AXIS -> transformMatrixPointToWorld(frame, axisLength, 0.0);
            case Y_AXIS -> transformMatrixPointToWorld(frame, 0.0, axisLength);
        };
        double dx = screenX - worldToScreenX(point[0]);
        double dy = screenY - worldToScreenY(point[1]);
        return dx * dx + dy * dy <= MATRIX_GIZMO_HANDLE_RADIUS_SQ;
    }

    private void beginMatrixInteraction(MatrixHandleKind handleKind) {
        if (selectedEntity == null || selectedEntityName == null || handleKind == null) return;
        EntityFrame frame = describeEntity(selectedEntity);
        if (frame == null) return;
        matrixDragState = new MatrixDragState(frame, handleKind, resolveMatrixAxisLength(frame));
        beginMoveInteraction(selectedEntityName, selectedEntity);
    }

    private void applyMatrixDrag(double screenX, double screenY) {
        if (matrixDragState == null || selectedEntity == null || selectedEntityName == null) return;
        EntityFrame frame = matrixDragState.frame;
        double[] world = screenToWorld(screenX, screenY);
        if (!Double.isFinite(world[0]) || !Double.isFinite(world[1])) return;
        double[] local = inverseStandardTransform(frame, world[0], world[1]);
        if (!Double.isFinite(local[0]) || !Double.isFinite(local[1])) return;

        double mxx = frame.matrixMxx;
        double mxy = frame.matrixMxy;
        double myx = frame.matrixMyx;
        double myy = frame.matrixMyy;
        double tx = frame.matrixTx;
        double ty = frame.matrixTy;

        switch (matrixDragState.handleKind) {
            case TRANSLATE -> {
                tx = clamp(local[0], -WORLD_POSITION_LIMIT, WORLD_POSITION_LIMIT);
                ty = clamp(local[1], -WORLD_POSITION_LIMIT, WORLD_POSITION_LIMIT);
                matrixOverlayText = String.format("Matrix T: (%.2f, %.2f)", tx, ty);
            }
            case X_AXIS -> {
                mxx = clamp((local[0] - tx) / matrixDragState.axisLength, -MATRIX_COMPONENT_LIMIT, MATRIX_COMPONENT_LIMIT);
                myx = clamp((local[1] - ty) / matrixDragState.axisLength, -MATRIX_COMPONENT_LIMIT, MATRIX_COMPONENT_LIMIT);
                matrixOverlayText = String.format("Matrix X: (%.2f, %.2f)", mxx, myx);
            }
            case Y_AXIS -> {
                mxy = clamp((local[0] - tx) / matrixDragState.axisLength, -MATRIX_COMPONENT_LIMIT, MATRIX_COMPONENT_LIMIT);
                myy = clamp((local[1] - ty) / matrixDragState.axisLength, -MATRIX_COMPONENT_LIMIT, MATRIX_COMPONENT_LIMIT);
                matrixOverlayText = String.format("Matrix Y: (%.2f, %.2f)", mxy, myy);
            }
        }

        matrixOverlayScreenX = screenX + 14.0;
        matrixOverlayScreenY = screenY - 6.0;
        selectedEntity.setSupplementalTransform(mxx, mxy, myx, myy, tx, ty);
        if (onEntityMatrixChanged != null) {
            onEntityMatrixChanged.accept(selectedEntityName, new double[]{mxx, mxy, myx, myy, tx, ty});
        }
        render();
    }

    private void setupMouseControls() {
        canvas.setOnDragOver(e -> {
            PuppeteerAssetTransfer.Payload payload = PuppeteerAssetTransfer.fromDragboard(e.getDragboard());
            if (payload != null) {
                e.acceptTransferModes(TransferMode.COPY);
                setAssetDropHighlight(true, "Drop to add '" + payload.suggestedName() + "' as a prop");
            }
            e.consume();
        });
        canvas.setOnDragExited(e -> {
            setAssetDropHighlight(false, null);
            e.consume();
        });
        canvas.setOnDragDropped(e -> {
            PuppeteerAssetTransfer.Payload payload = PuppeteerAssetTransfer.fromDragboard(e.getDragboard());
            boolean success = payload != null;
            if (payload != null && onAssetDropped != null) {
                onAssetDropped.accept(payload);
            }
            setAssetDropHighlight(false, null);
            e.setDropCompleted(success);
            e.consume();
        });

        canvas.setOnScroll(e -> {
            if (!isInsideViewport(e.getX(), e.getY())) return;
            double factor = Math.pow(1.05, e.getDeltaY() / 40.0);
            if (scrollZoomMode == ScrollZoomMode.CAMERA) {
                double z = camera.getZoom();
                double[] world = screenToWorld(e.getX(), e.getY());
                double worldX = world[0];
                double worldY = world[1];
                double relativeX = (worldX - camera.getX()) * z;
                double relativeY = (worldY - camera.getY()) * z;
                double newZ = Math.max(0.1, Math.min(5.0, z * factor));
                camera.setZoom(newZ);
                camera.setPosition(worldX - relativeX / newZ, worldY - relativeY / newZ);
            } else {
                double[] worldBefore = screenToWorld(e.getX(), e.getY());
                viewZoomFactor = clamp(viewZoomFactor * factor, VIEW_ZOOM_MIN, VIEW_ZOOM_MAX);
                updateViewportTransform(Math.max(1.0, canvas.getWidth()), Math.max(1.0, canvas.getHeight()));
                double[] worldAfter = screenToWorld(e.getX(), e.getY());
                viewPanX += worldBefore[0] - worldAfter[0];
                viewPanY += worldBefore[1] - worldAfter[1];
            }
            notifyCameraStateChanged();
            render();
        });

        final double[] panStart = new double[2];
        final boolean[] panning = {false};

        canvas.setOnMousePressed(e -> {
            if (e.isMiddleButtonDown() || e.isSecondaryButtonDown()) {
                panning[0] = true;
                panStart[0] = e.getX();
                panStart[1] = e.getY();
                return;
            }

            if (e.isPrimaryButtonDown()) {
                if (!isInsideViewport(e.getX(), e.getY())) {
                    clearSelection();
                    draggingEntity = false;
                    draggingGroup = false;
                    draggingOrbit = false;
                    draggingPivot = false;
                    draggingRotate = false;
                    draggingOrbitAnchor = false;
                    pivotDragState = null;
                    matrixDragState = null;
                    rotateDragState = null;
                    matrixOverlayText = null;
                    cancelMoveInteraction();
                    render();
                    return;
                }
                // Anchor placement mode: click on the selected entity to set anchor position
                if (anchorPlacementMode && selectedEntity != null) {
                    double[] world = screenToWorld(e.getX(), e.getY());
                    double[] corners = getEntityCorners(selectedEntity);
                    if (containsPointInQuad(corners, world[0], world[1])) {
                        EntityFrame frame = describeEntity(selectedEntity);
                        if (frame != null) {
                            double dx = world[0] - selectedEntity.getX();
                            double dy = world[1] - selectedEntity.getY();
                            double cos = Math.cos(-frame.rotationRad);
                            double sin = Math.sin(-frame.rotationRad);
                            double unrotX = dx * cos - dy * sin;
                            double unrotY = dx * sin + dy * cos;
                            double localX = unrotX / Math.max(1e-6, frame.scaleX);
                            double localY = unrotY / Math.max(1e-6, frame.scaleY);
                            double relX = Math.max(0.0, Math.min(1.0, frame.originX + localX / Math.max(1e-6, frame.w)));
                            double relY = Math.max(0.0, Math.min(1.0, frame.originY + localY / Math.max(1e-6, frame.h)));
                            if (onAnchorPlacementAt != null) {
                                onAnchorPlacementAt.accept(new double[]{relX, relY});
                            }
                            anchorPlacementMode = false;
                            render();
                            return;
                        }
                    }
                }

                if (runtimeCameraSelected && isNearRuntimeFrameHandle(e.getX(), e.getY())) {
                    draggingCameraFrame = true;
                    dragCameraStartX = e.getX();
                    dragCameraStartY = e.getY();
                    if (onCameraInteractionStarted != null) {
                        onCameraInteractionStarted.run();
                    }
                    return;
                }
                if (orbitToolEnabled && e.isShiftDown() && e.isAltDown()) {
                    if (selectedEntityName != null && !selectedEntityName.isBlank()) {
                        String anchorSource = findEntityNameAt(e.getX(), e.getY(), false);
                        if (anchorSource != null && !anchorSource.equals(selectedEntityName)) {
                            Entity2D sourceEntity = scene != null ? scene.find(anchorSource) : null;
                            if (sourceEntity != null) {
                                double[] world = screenToWorld(e.getX(), e.getY());
                                setOrbitAnchorSource(selectedEntityName, anchorSource, world[0], world[1]);
                                render();
                                return;
                            }
                        }
                    }
                }

                if (orbitToolEnabled && e.isShiftDown()) {
                    String target = selectedEntityName;
                    if (target == null || target.isBlank()) {
                        target = findEntityNameAt(e.getX(), e.getY(), true);
                        if (target != null && onEntitySelected != null) {
                            onEntitySelected.accept(target);
                        }
                    }
                    if (target != null && !target.isBlank()) {
                        double[] world = screenToWorld(e.getX(), e.getY());
                        setOrbitAnchor(target, world[0], world[1]);
                        render();
                    }
                    return;
                }

                if (orbitToolEnabled && selectedEntity != null && isNearOrbitAnchorHandle(e.getX(), e.getY())) {
                    if (resolveEntityLocked(selectedEntityName)) return;
                    draggingOrbitAnchor = true;
                    return;
                }

                if (selectedEntity != null && isNearMatrixHandle(e.getX(), e.getY(), MatrixHandleKind.X_AXIS)) {
                    if (resolveEntityLocked(selectedEntityName)) return;
                    beginMatrixInteraction(MatrixHandleKind.X_AXIS);
                    return;
                }
                if (selectedEntity != null && isNearMatrixHandle(e.getX(), e.getY(), MatrixHandleKind.Y_AXIS)) {
                    if (resolveEntityLocked(selectedEntityName)) return;
                    beginMatrixInteraction(MatrixHandleKind.Y_AXIS);
                    return;
                }
                if (selectedEntity != null
                    && shouldAllowMatrixTranslateHandle(e.isAltDown())
                    && isNearMatrixHandle(e.getX(), e.getY(), MatrixHandleKind.TRANSLATE)) {
                    if (resolveEntityLocked(selectedEntityName)) return;
                    beginMatrixInteraction(MatrixHandleKind.TRANSLATE);
                    return;
                }

                if (selectedEntity != null && supportsPivotEntity(selectedEntity) && isNearPivotHandle(e.getX(), e.getY())) {
                    if (resolveEntityLocked(selectedEntityName)) return;
                    pivotDragState = buildPivotDragState(selectedEntity);
                    draggingPivot = true;
                    beginMoveInteraction(selectedEntityName, selectedEntity);
                    pivotAxisLocked = false;
                    pivotAxisIsHorizontal = false;
                    double[] startWorld = screenToWorld(e.getX(), e.getY());
                    pivotDragStartWorldX = startWorld[0];
                    pivotDragStartWorldY = startWorld[1];
                    return;
                }
                
                int scaleCorner = findNearScaleHandle(e.getX(), e.getY());
                if (selectedEntity != null && scaleCorner >= 0) {
                    if (resolveEntityLocked(selectedEntityName)) return;
                    EntityFrame scFrame = describeEntity(selectedEntity);
                    if (scFrame != null) {
                        scaleDragState = new ScaleDragState(scFrame, scaleCorner);
                        draggingScale = true;
                        beginMoveInteraction(selectedEntityName, selectedEntity);
                    }
                    return;
                }

                if (selectedEntity != null && isNearRotateHandle(e.getX(), e.getY())) {
                    if (resolveEntityLocked(selectedEntityName)) return;
                    double[] world = screenToWorld(e.getX(), e.getY());
                    double angle = Math.atan2(world[1] - selectedEntity.getY(), world[0] - selectedEntity.getX());
                    rotateDragState = new RotateDragState(selectedEntity.getX(), selectedEntity.getY(), angle, selectedEntity.getRotationDeg());
                    if (orbitAnchorSources != null && scene != null) {
                        for (java.util.Map.Entry<String, String> entry : orbitAnchorSources.entrySet()) {
                            if (selectedEntityName.equals(entry.getValue())) {
                                String followerName = entry.getKey();
                                Entity2D follower = scene.find(followerName);
                                if (follower != null) {
                                    rotateDragState.followers.put(followerName, new FollowerState(follower.getX(), follower.getY(), follower.getRotationDeg()));
                                }
                            }
                        }
                    }
                    draggingRotate = true;
                    beginMoveInteraction(selectedEntityName, selectedEntity);
                    return;
                }

                String hitName = findEntityNameAt(e.getX(), e.getY(), selectedGroupName == null);
                if (hitName != null) {
                    if (selectedGroupName != null && selectedGroupMemberNames.contains(hitName)) {
                        beginGroupMoveInteraction(selectedGroupName);
                        draggingGroup = true;
                        dragEntityStartX = e.getX();
                        dragEntityStartY = e.getY();
                        render();
                        return;
                    }
                    if (orbitToolEnabled && hasOrbitAnchor(hitName)) {
                        double[] anchor = getOrbitAnchor(hitName);
                        beginMoveInteraction(selectedEntityName, selectedEntity);
                        orbitRadius = Math.hypot(selectedEntity.getX() - anchor[0], selectedEntity.getY() - anchor[1]);
                        if (orbitRadius < 0.0001) {
                            double[] world = screenToWorld(e.getX(), e.getY());
                            orbitRadius = Math.hypot(world[0] - anchor[0], world[1] - anchor[1]);
                        }
                        draggingOrbit = true;
                    } else {
                        beginMoveInteraction(selectedEntityName, selectedEntity);
                        draggingEntity = true;
                        dragEntityStartX = e.getX();
                        dragEntityStartY = e.getY();
                    }
                    if (onEntitySelected != null) onEntitySelected.accept(hitName);
                    drawSelectionHighlight(selectedEntity);
                } else {
                    clearSelection();
                    draggingEntity = false;
                    draggingGroup = false;
                    draggingOrbit = false;
                    draggingRotate = false;
                    pivotDragState = null;
                    matrixDragState = null;
                    rotateDragState = null;
                    matrixOverlayText = null;
                    cancelMoveInteraction();
                }
                render();
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (panning[0]) {
                double dx = (e.getX() - panStart[0]) / Math.max(1e-6, displayScale);
                double dy = (e.getY() - panStart[1]) / Math.max(1e-6, displayScale);
                panStart[0] = e.getX();
                panStart[1] = e.getY();
                if (scrollZoomMode == ScrollZoomMode.CAMERA) {
                    camera.setPosition(
                        camera.getX() - dx,
                        camera.getY() - dy
                    );
                } else {
                    viewPanX -= dx;
                    viewPanY -= dy;
                }
                notifyCameraStateChanged();
                render();
            } else if (draggingCameraFrame) {
                double[] prevWorld = screenToWorld(dragCameraStartX, dragCameraStartY);
                double[] world = screenToWorld(e.getX(), e.getY());
                double dx = world[0] - prevWorld[0];
                double dy = world[1] - prevWorld[1];
                dragCameraStartX = e.getX();
                dragCameraStartY = e.getY();
                camera.setPosition(camera.getX() + dx, camera.getY() + dy);
                if (onCameraMoved != null) {
                    onCameraMoved.accept(new double[]{camera.getX(), camera.getY(), camera.getZoom()});
                }
                notifyCameraStateChanged();
                render();
            } else if (draggingOrbitAnchor && selectedEntityName != null) {
                double[] world = screenToWorld(e.getX(), e.getY());
                setOrbitAnchor(selectedEntityName, world[0], world[1]);
                render();
            } else if (draggingOrbit && selectedEntity != null && selectedEntityName != null) {
                double[] anchor = getOrbitAnchor(selectedEntityName);
                if (anchor == null) return;
                double[] world = screenToWorld(e.getX(), e.getY());
                if (!Double.isFinite(world[0]) || !Double.isFinite(world[1])) return;
                double angle = Math.atan2(world[1] - anchor[1], world[0] - anchor[0]);
                double radius = Math.max(0.0001, orbitRadius);
                double nextX = clamp(anchor[0] + radius * Math.cos(angle), -WORLD_POSITION_LIMIT, WORLD_POSITION_LIMIT);
                double nextY = clamp(anchor[1] + radius * Math.sin(angle), -WORLD_POSITION_LIMIT, WORLD_POSITION_LIMIT);
                selectedEntity.setPosition(nextX, nextY);

                if (orbitAlignRotation) {
                    double rotDeg = Math.toDegrees(angle);
                    selectedEntity.setRotationDeg(rotDeg);
                    if (onEntityRotationChanged != null) {
                        onEntityRotationChanged.accept(selectedEntityName, rotDeg);
                    }
                }
                if (onEntityMoved != null) {
                    onEntityMoved.accept(selectedEntityName, new double[]{nextX, nextY});
                }
                render();
            } else if (draggingRotate && rotateDragState != null && selectedEntity != null && selectedEntityName != null) {
                double[] world = screenToWorld(e.getX(), e.getY());
                rotateDragState.currentMouseWorldX = world[0];
                rotateDragState.currentMouseWorldY = world[1];
                double currentAngle = Math.atan2(world[1] - rotateDragState.pivotY, world[0] - rotateDragState.pivotX);
                
                double dThetaRad = currentAngle - rotateDragState.lastMouseAngleRad;
                while (dThetaRad > Math.PI) dThetaRad -= 2 * Math.PI;
                while (dThetaRad < -Math.PI) dThetaRad += 2 * Math.PI;
                
                rotateDragState.lastMouseAngleRad = currentAngle;
                rotateDragState.accumulatedThetaRad += dThetaRad;
                
                double totalDThetaRad = rotateDragState.accumulatedThetaRad;
                double dThetaDeg = Math.toDegrees(totalDThetaRad);
                
                double newRotation = rotateDragState.baseRotationDeg + dThetaDeg;
                selectedEntity.setRotationDeg(newRotation);
                if (onEntityRotationChanged != null) {
                    onEntityRotationChanged.accept(selectedEntityName, newRotation);
                }
                
                for (java.util.Map.Entry<String, FollowerState> entry : rotateDragState.followers.entrySet()) {
                    String followerName = entry.getKey();
                    FollowerState state = entry.getValue();
                    Entity2D follower = scene.find(followerName);
                    if (follower != null) {
                        double dx = state.startX - rotateDragState.pivotX;
                        double dy = state.startY - rotateDragState.pivotY;
                        double newX = rotateDragState.pivotX + dx * Math.cos(totalDThetaRad) - dy * Math.sin(totalDThetaRad);
                        double newY = rotateDragState.pivotY + dx * Math.sin(totalDThetaRad) + dy * Math.cos(totalDThetaRad);
                        double newRot = state.startRotationDeg + dThetaDeg;
                        follower.setPosition(newX, newY);
                        follower.setRotationDeg(newRot);
                        if (onEntityMoved != null) onEntityMoved.accept(followerName, new double[]{newX, newY});
                        if (onEntityRotationChanged != null) onEntityRotationChanged.accept(followerName, newRot);
                    }
                }
                render();
            } else if (draggingScale && scaleDragState != null && selectedEntity != null && selectedEntityName != null) {
                double[] world = screenToWorld(e.getX(), e.getY());
                if (!Double.isFinite(world[0]) || !Double.isFinite(world[1])) return;
                double dx = world[0] - selectedEntity.getX();
                double dy = world[1] - selectedEntity.getY();
                double cos = Math.cos(scaleDragState.baseRotationRad);
                double sin = Math.sin(scaleDragState.baseRotationRad);
                double unrotX = cos * dx + sin * dy;
                double unrotY = -sin * dx + cos * dy;
                double newScaleX = Math.abs(scaleDragState.localCornerX) < 1e-6 ? scaleDragState.baseScaleX
                    : clamp(unrotX / scaleDragState.localCornerX, -50, 50);
                double newScaleY = Math.abs(scaleDragState.localCornerY) < 1e-6 ? scaleDragState.baseScaleY
                    : clamp(unrotY / scaleDragState.localCornerY, -50, 50);
                if (e.isShiftDown()) {
                    double factor = Math.sqrt(Math.abs(newScaleX / scaleDragState.baseScaleX) * Math.abs(newScaleY / scaleDragState.baseScaleY));
                    newScaleX = scaleDragState.baseScaleX * factor;
                    newScaleY = scaleDragState.baseScaleY * factor;
                }
                selectedEntity.setScale(newScaleX, newScaleY);
                if (onEntityScaleChanged != null) onEntityScaleChanged.accept(selectedEntityName, new double[]{newScaleX, newScaleY});
                render();
            } else if (matrixDragState != null && selectedEntity != null && selectedEntityName != null) {
                applyMatrixDrag(e.getX(), e.getY());
            } else if (draggingPivot && selectedEntity != null) {
                if (pivotDragState == null) {
                    pivotDragState = buildPivotDragState(selectedEntity);
                }
                if (pivotDragState == null) return;
                double[] world = screenToWorld(e.getX(), e.getY());
                double worldX = world[0];
                double worldY = world[1];

                if (e.isShiftDown()) {
                    if (!pivotAxisLocked) {
                        double adx = Math.abs(worldX - pivotDragStartWorldX);
                        double ady = Math.abs(worldY - pivotDragStartWorldY);
                        if (adx > 2.0 || ady > 2.0) {
                            pivotAxisLocked = true;
                            pivotAxisIsHorizontal = adx >= ady;
                        }
                    }
                    if (pivotAxisLocked) {
                        if (pivotAxisIsHorizontal) {
                            worldY = pivotDragState.baseY;
                        } else {
                            worldX = pivotDragState.baseX;
                        }
                    }
                } else {
                    pivotAxisLocked = false;
                }

                double dx = worldX - pivotDragState.baseX;
                double dy = worldY - pivotDragState.baseY;
                double cos = Math.cos(pivotDragState.baseRotationRad);
                double sin = Math.sin(pivotDragState.baseRotationRad);
                double unrotX = cos * dx + sin * dy;
                double unrotY = -sin * dx + cos * dy;
                double localX = unrotX / pivotDragState.baseScaleX;
                double localY = unrotY / pivotDragState.baseScaleY;

                double pivotX = clampPivot(pivotDragState.baseOriginX + localX / pivotDragState.baseW);
                double pivotY = clampPivot(pivotDragState.baseOriginY + localY / pivotDragState.baseH);
                selectedEntity.setPosition(worldX, worldY);
                setEntityPivot(selectedEntity, pivotX, pivotY);

                pivotOverlayText = String.format("Pivot: (%.2f, %.2f)", pivotX, pivotY);
                pivotOverlayScreenX = e.getX() + 14;
                pivotOverlayScreenY = e.getY() - 6;

                if (onEntityPivotChanged != null && selectedEntityName != null) {
                    onEntityPivotChanged.accept(selectedEntityName, new double[]{pivotX, pivotY});
                }
                if (onEntityMoved != null && selectedEntityName != null) {
                    onEntityMoved.accept(selectedEntityName, new double[]{worldX, worldY});
                }
                render();
            } else if (draggingGroup && selectedGroupName != null && !selectedGroupMemberNames.isEmpty()) {
                double[] prevWorld = screenToWorld(dragEntityStartX, dragEntityStartY);
                double[] world = screenToWorld(e.getX(), e.getY());
                if (!Double.isFinite(world[0]) || !Double.isFinite(world[1])
                    || !Double.isFinite(prevWorld[0]) || !Double.isFinite(prevWorld[1])) return;
                double dx = world[0] - prevWorld[0];
                double dy = world[1] - prevWorld[1];
                dragEntityStartX = e.getX();
                dragEntityStartY = e.getY();
                groupDragAccumX += dx;
                groupDragAccumY += dy;
                if (scene != null) {
                    for (String memberName : selectedGroupMemberNames) {
                        Entity2D member = scene.find(memberName);
                        if (member != null) {
                            double mx = clamp(member.getX() + dx, -WORLD_POSITION_LIMIT, WORLD_POSITION_LIMIT);
                            double my = clamp(member.getY() + dy, -WORLD_POSITION_LIMIT, WORLD_POSITION_LIMIT);
                            member.setPosition(mx, my);
                        }
                    }
                }
                if (onEntityMoved != null) {
                    onEntityMoved.accept(selectedGroupName, new double[]{
                        groupDragStartTrackX + groupDragAccumX,
                        groupDragStartTrackY + groupDragAccumY
                    });
                }
                render();
            } else if (draggingEntity && selectedEntity != null) {
                double[] prevWorld = screenToWorld(dragEntityStartX, dragEntityStartY);
                double[] world = screenToWorld(e.getX(), e.getY());
                if (!Double.isFinite(world[0]) || !Double.isFinite(world[1])
                    || !Double.isFinite(prevWorld[0]) || !Double.isFinite(prevWorld[1])) return;
                double dx = world[0] - prevWorld[0];
                double dy = world[1] - prevWorld[1];
                dragEntityStartX = e.getX();
                dragEntityStartY = e.getY();
                double newX = clamp(selectedEntity.getX() + dx, -WORLD_POSITION_LIMIT, WORLD_POSITION_LIMIT);
                double newY = clamp(selectedEntity.getY() + dy, -WORLD_POSITION_LIMIT, WORLD_POSITION_LIMIT);
                double[] snapped = applySnap(newX, newY);
                selectedEntity.setPosition(snapped[0], snapped[1]);
                if (onEntityMoved != null && selectedEntityName != null) {
                    onEntityMoved.accept(selectedEntityName,
                        new double[]{selectedEntity.getX(), selectedEntity.getY()});
                }
                render();
            }
        });

        canvas.setOnMouseReleased(e -> {
            endMoveInteraction();
            panning[0] = false;
            if (draggingCameraFrame && onCameraInteractionFinished != null) {
                onCameraInteractionFinished.accept(new double[]{camera.getX(), camera.getY(), camera.getZoom()});
            }
            draggingCameraFrame = false;
            draggingEntity = false;
            draggingGroup = false;
            draggingPivot = false;
            draggingOrbit = false;
            draggingRotate = false;
            draggingScale = false;
            draggingOrbitAnchor = false;
            pivotDragState = null;
            matrixDragState = null;
            rotateDragState = null;
            scaleDragState = null;
            pivotAxisLocked = false;
            if (pivotOverlayText != null) {
                pivotOverlayText = null;
                render();
            }
            if (matrixOverlayText != null) {
                matrixOverlayText = null;
                render();
            }
        });

        canvas.setFocusTraversable(true);
        canvas.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE && anchorPlacementMode) {
                anchorPlacementMode = false;
                render();
                e.consume();
            }
        });
    }

    private void beginMoveInteraction(String entityName, Entity2D entity) {
        if (entityName == null || entityName.isBlank() || entity == null) return;
        if (moveInteractionActive && entityName.equals(moveInteractionEntityName)) return;
        moveInteractionActive = true;
        moveInteractionEntityName = entityName;
        moveInteractionStartX = entity.getX();
        moveInteractionStartY = entity.getY();
        if (onEntityMoveInteractionStarted != null) {
            onEntityMoveInteractionStarted.accept(entityName, new double[]{moveInteractionStartX, moveInteractionStartY});
        }
    }

    private void beginGroupMoveInteraction(String groupName) {
        if (groupName == null || groupName.isBlank() || project == null) return;
        EntityGroup group = project.getGroup(groupName);
        if (group == null) return;
        if (moveInteractionActive && groupName.equals(moveInteractionEntityName)) return;
        moveInteractionActive = true;
        moveInteractionEntityName = groupName;
        groupDragStartTrackX = group.getGroupTrack().getValueAt(PropertyType.X, project.getPlayheadMs());
        groupDragStartTrackY = group.getGroupTrack().getValueAt(PropertyType.Y, project.getPlayheadMs());
        groupDragAccumX = 0.0;
        groupDragAccumY = 0.0;
        moveInteractionStartX = groupDragStartTrackX;
        moveInteractionStartY = groupDragStartTrackY;
        if (onEntityMoveInteractionStarted != null) {
            onEntityMoveInteractionStarted.accept(groupName, new double[]{moveInteractionStartX, moveInteractionStartY});
        }
    }

    private void endMoveInteraction() {
        if (!moveInteractionActive) return;
        String entityName = moveInteractionEntityName;
        double endX = moveInteractionStartX;
        double endY = moveInteractionStartY;
        if (entityName != null) {
            if (scene != null) {
                Entity2D entity = scene.find(entityName);
                if (entity != null) {
                    endX = entity.getX();
                    endY = entity.getY();
                }
            }
            if ((endX == moveInteractionStartX && endY == moveInteractionStartY) && project != null) {
                EntityGroup group = project.getGroup(entityName);
                if (group != null) {
                    endX = group.getGroupTrack().getValueAt(PropertyType.X, project.getPlayheadMs());
                    endY = group.getGroupTrack().getValueAt(PropertyType.Y, project.getPlayheadMs());
                }
            }
        }
        moveInteractionActive = false;
        moveInteractionEntityName = null;
        if (entityName != null && onEntityMoveInteractionFinished != null) {
            onEntityMoveInteractionFinished.accept(entityName, new double[]{endX, endY});
        }
    }

    private void cancelMoveInteraction() {
        moveInteractionActive = false;
        moveInteractionEntityName = null;
        groupDragAccumX = 0.0;
        groupDragAccumY = 0.0;
    }

    private void refreshSelectedGroupMembers() {
        selectedGroupMemberNames.clear();
        if (project == null || selectedGroupName == null || selectedGroupName.isBlank()) {
            return;
        }
        selectedGroupMemberNames.addAll(project.collectGroupEntityNames(selectedGroupName));
    }

    private void drawSelectionHighlight(Entity2D entity) {
        if (entity == null) return;
        double z = Math.max(1e-6, displayScale);
        double[] corners = getEntityCorners(entity);
        if (corners.length < 8) return;
        double[] wx = new double[4];
        double[] wy = new double[4];
        double minWx = Double.POSITIVE_INFINITY;
        double minWy = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            wx[i] = corners[i * 2];
            wy[i] = corners[i * 2 + 1];
            if (wx[i] < minWx) minWx = wx[i];
            if (wy[i] < minWy) minWy = wy[i];
        }

        gc.save();
        applyCameraTransform();

        // Bounding box
        gc.setStroke(Color.web("#c084fc", 0.85));
        gc.setLineWidth(1.5 / z);
        gc.setLineDashes(6.0 / z, 4.0 / z);
        gc.beginPath();
        gc.moveTo(wx[0], wy[0]);
        gc.lineTo(wx[1], wy[1]);
        gc.lineTo(wx[2], wy[2]);
        gc.lineTo(wx[3], wy[3]);
        gc.closePath();
        gc.stroke();
        gc.setLineDashes((double[]) null);

        // Rotation drag line
        if (rotateDragState != null) {
            double dashOffset = (System.currentTimeMillis() / 20.0) % 20.0;
            gc.setStroke(Color.web("#f0b673", 0.85));
            gc.setLineWidth(1.5 / z);
            gc.setLineDashes(5.0 / z, 5.0 / z);
            gc.setLineDashOffset(-dashOffset / z);
            gc.strokeLine(rotateDragState.pivotX, rotateDragState.pivotY, rotateDragState.currentMouseWorldX, rotateDragState.currentMouseWorldY);
            gc.setLineDashes((double[]) null);
            gc.setLineDashOffset(0);
        }

        drawRotateHandleWorld(entity, z);
        drawMatrixGizmoWorld(entity, z);
        if (selectedEntityName != null && hasOrbitAnchor(selectedEntityName)) {
            drawOrbitAnchorWorld(selectedEntityName, entity, z);
        }
        drawAnchors(entity, z);
        drawScaleHandlesWorld(entity, z);
        if (supportsPivotEntity(entity)) drawPivotHandleWorld(entity, z);

        if (selectedEntityName != null) {
            gc.setFill(Color.web("#f0b673"));
            gc.setFont(javafx.scene.text.Font.font(javafx.scene.text.Font.getDefault().getFamily(), 10.0 / z));
            gc.fillText(selectedEntityName, minWx, minWy - (6.0 / z));
        }
        gc.restore();
    }

    private void drawMatrixGizmoWorld(Entity2D entity, double zoom) {
        EntityFrame frame = describeEntity(entity);
        if (frame == null) return;
        double z = Math.max(0.0001, zoom);
        double axisLength = resolveMatrixAxisLength(frame);
        double[] origin = transformMatrixPointToWorld(frame, 0.0, 0.0);
        double[] xHandle = transformMatrixPointToWorld(frame, axisLength, 0.0);
        double[] yHandle = transformMatrixPointToWorld(frame, 0.0, axisLength);

        gc.setLineWidth(1.35 / z);
        gc.setStroke(Color.web("#6ed0ff", 0.85));
        gc.strokeLine(origin[0], origin[1], xHandle[0], xHandle[1]);
        gc.setStroke(Color.web("#8fe388", 0.85));
        gc.strokeLine(origin[0], origin[1], yHandle[0], yHandle[1]);

        double originRadius = 3.5 / z;
        gc.setFill(Color.web("#6ed0ff", 0.95));
        gc.fillOval(origin[0] - originRadius, origin[1] - originRadius, originRadius * 2.0, originRadius * 2.0);

        double handleRadius = 3.0 / z;
        gc.setFill(Color.web("#ff9f7a", 0.95));
        gc.fillOval(xHandle[0] - handleRadius, xHandle[1] - handleRadius, handleRadius * 2.0, handleRadius * 2.0);
        gc.setFill(Color.web("#9ef0a5", 0.95));
        gc.fillOval(yHandle[0] - handleRadius, yHandle[1] - handleRadius, handleRadius * 2.0, handleRadius * 2.0);

        gc.setFill(Color.web("#6ed0ff", 0.75));
        gc.setFont(javafx.scene.text.Font.font(8.0 / z));
        gc.fillText("M", origin[0] + (5.0 / z), origin[1] - (5.0 / z));
        gc.setFill(Color.web("#ff9f7a", 0.75));
        gc.fillText("X", xHandle[0] + (4.0 / z), xHandle[1] - (4.0 / z));
        gc.setFill(Color.web("#9ef0a5", 0.75));
        gc.fillText("Y", yHandle[0] + (4.0 / z), yHandle[1] - (4.0 / z));
    }

    private void drawAnchors(Entity2D entity, double zoom) {
        if (project == null || entity == null || selectedEntityName == null) return;

        Map<String, Anchor> anchors = project.getAnchorsForEntity(selectedEntityName);
        if (anchors == null || anchors.isEmpty()) return;

        double z = Math.max(0.0001, zoom);
        EntityFrame frame = describeEntity(entity);
        if (frame == null) return;

        double ex = entity.getX();
        double ey = entity.getY();
        double cos = Math.cos(frame.rotationRad);
        double sin = Math.sin(frame.rotationRad);

        for (Anchor anchor : anchors.values()) {
            double ax, ay;

            if (anchor.isRelative()) {
                // Entity-local normalized coords: (0,0) = top-left, (1,1) = bottom-right
                double lx = (anchor.getX() - frame.originX) * frame.w * frame.scaleX;
                double ly = (anchor.getY() - frame.originY) * frame.h * frame.scaleY;
                ax = ex + lx * cos - ly * sin;
                ay = ey + lx * sin + ly * cos;
            } else {
                // World-space: the anchor position is already in world coordinates
                ax = anchor.getX();
                ay = anchor.getY();
            }

            // Dashed line from entity pivot to anchor
            gc.setStroke(Color.web("#a855f7", 0.55));
            gc.setLineWidth(0.9 / z);
            gc.setLineDashes(3.5 / z, 2.5 / z);
            gc.strokeLine(ex, ey, ax, ay);
            gc.setLineDashes((double[]) null);

            // Crosshair
            double arm    = 6.0 / z;
            double radius = 2.5 / z;
            gc.setStroke(Color.web("#a855f7"));
            gc.setLineWidth(1.3 / z);
            gc.strokeLine(ax - arm, ay, ax + arm, ay);
            gc.strokeLine(ax, ay - arm, ax, ay + arm);
            gc.setFill(Color.web("#a855f7", 0.9));
            gc.fillOval(ax - radius, ay - radius, radius * 2.0, radius * 2.0);

            // Label
            gc.setFill(Color.web("#c084fc", 0.85));
            gc.setFont(javafx.scene.text.Font.font(8.0 / z));
            gc.fillText(anchor.getName(), ax + (8.0 / z), ay - (5.0 / z));
        }
    }

    private void drawGroupDimming() {
        if (project == null || selectedEntityName == null || selectedEntityName.isBlank() || scene == null) return;

        EntityTrack selectedTrack = project.getTrack(selectedEntityName);
        if (selectedTrack == null) return;

        String parentGroupName = selectedTrack.getParentGroupName();
        if (parentGroupName == null || parentGroupName.isBlank()) return;

        // Check if selected entity IS the group track (top entity of the group)
        EntityGroup group = project.getGroup(parentGroupName);
        if (group != null && group.getGroupTrack().getEntityName().equals(selectedEntityName)) {
            // Group track selected - don't dim anything
            return;
        }

        // Selected entity is a child of the group - draw light grey borders on other entities
        List<String> groupEntityNames = project.collectGroupEntityNames(parentGroupName);
        if (groupEntityNames.isEmpty()) return;

        for (String entityName : groupEntityNames) {
            if (entityName.equals(selectedEntityName)) continue; // Don't border the selected entity

            Entity2D entity = scene.find(entityName);
            if (entity == null || !entity.isVisible()) continue;

            double[] corners = getEntityCorners(entity);
            if (corners.length < 8) continue;

            // Draw light grey border outline
            gc.save();
            gc.setStroke(Color.web("#888888", 0.5));
            gc.setLineWidth(1.5);
            gc.beginPath();
            gc.moveTo(corners[0], corners[1]);
            gc.lineTo(corners[2], corners[3]);
            gc.lineTo(corners[4], corners[5]);
            gc.lineTo(corners[6], corners[7]);
            gc.closePath();
            gc.stroke();
            gc.restore();
        }
    }

    /**
     * Draws an animated marching-ants border around the opaque pixel regions of the
     * currently selected sprite entity. Called every frame by the sprite region timer.
     */
    private void drawSelectedEntityRegionBorder() {
        if (pixelAnalyzer == null || selectedEntity == null) return;
        if (!(selectedEntity instanceof com.jvn.core.scene2d.Sprite2D sprite)) return;

        String imagePath = sprite.getImagePath();
        if (imagePath == null || imagePath.isBlank()) return;

        EntityFrame frame = describeEntity(selectedEntity);
        if (frame == null) return;

        double spriteW = sprite.getWidth();
        double spriteH = sprite.getHeight();
        double entityX = selectedEntity.getX();
        double entityY = selectedEntity.getY();
        double cos = Math.cos(frame.rotationRad);
        double sin = Math.sin(frame.rotationRad);
        double z = Math.max(1e-6, displayScale);
        double dashOffset = (System.currentTimeMillis() / 40.0) % 16.0;

        gc.save();
        gc.setStroke(Color.web("#c084fc", 0.92));
        gc.setLineWidth(1.8 / z);
        gc.setLineDashes(5.0 / z, 3.0 / z);
        gc.setLineDashOffset(-dashOffset / z);

        for (String layerPath : imagePath.split("\\|")) {
            String path = layerPath == null ? "" : layerPath.trim();
            if (path.isEmpty()) continue;

            List<SpritePixelAnalyzer.DetectedRegion> regions =
                spriteRegionCache.computeIfAbsent(path, pixelAnalyzer::detectRegions);
            if (regions.isEmpty()) continue;

            double[] imageSize = resolveImageSize(path);
            if (imageSize == null) continue;
            double imgToSpriteX = spriteW / Math.max(1.0, imageSize[0]);
            double imgToSpriteY = spriteH / Math.max(1.0, imageSize[1]);

            for (SpritePixelAnalyzer.DetectedRegion region : regions) {
                // Image px → sprite-display px → apply origin offset and entity scale
                double lx = (-frame.originX * spriteW + region.minX * imgToSpriteX) * frame.scaleX;
                double ly = (-frame.originY * spriteH + region.minY * imgToSpriteY) * frame.scaleY;
                double lw = region.width  * imgToSpriteX * frame.scaleX;
                double lh = region.height * imgToSpriteY * frame.scaleY;

                // Rotate the four corners into world space
                double[] c = new double[8];
                c[0] = entityX + lx * cos - ly * sin;
                c[1] = entityY + lx * sin + ly * cos;
                c[2] = entityX + (lx + lw) * cos - ly * sin;
                c[3] = entityY + (lx + lw) * sin + ly * cos;
                c[4] = entityX + (lx + lw) * cos - (ly + lh) * sin;
                c[5] = entityY + (lx + lw) * sin + (ly + lh) * cos;
                c[6] = entityX + lx * cos - (ly + lh) * sin;
                c[7] = entityY + lx * sin + (ly + lh) * cos;

                gc.beginPath();
                gc.moveTo(c[0], c[1]);
                gc.lineTo(c[2], c[3]);
                gc.lineTo(c[4], c[5]);
                gc.lineTo(c[6], c[7]);
                gc.closePath();
                gc.stroke();
            }
        }

        gc.setLineDashes((double[]) null);
        gc.restore();
    }

    /**
     * Draws static (non-animated) region borders for all visible sprite entities.
     * Only active when the "Show Sprite Regions" debug toggle is on.
     * Skips the selected entity — it already gets the animated border above.
     */
    private void drawSpriteRegions() {
        if (pixelAnalyzer == null || scene == null) return;

        double z = Math.max(1e-6, displayScale);

        for (Entity2D entity : scene.getChildren()) {
            if (!(entity instanceof com.jvn.core.scene2d.Sprite2D sprite)) continue;
            if (!entity.isVisible()) continue;
            if (entity == selectedEntity) continue; // already drawn with animation

            String imagePath = sprite.getImagePath();
            if (imagePath == null || imagePath.isBlank()) continue;

            EntityFrame frame = describeEntity(entity);
            if (frame == null) continue;

            double spriteW = sprite.getWidth();
            double spriteH = sprite.getHeight();
            double entityX = entity.getX();
            double entityY = entity.getY();
            double cos = Math.cos(frame.rotationRad);
            double sin = Math.sin(frame.rotationRad);

            gc.save();
            gc.setStroke(Color.web("#00ffcc", 0.45));
            gc.setLineWidth(1.0 / z);
            gc.setLineDashes(4.0 / z, 3.0 / z);

            for (String layerPath : imagePath.split("\\|")) {
                String path = layerPath == null ? "" : layerPath.trim();
                if (path.isEmpty()) continue;

                List<SpritePixelAnalyzer.DetectedRegion> regions =
                    spriteRegionCache.computeIfAbsent(path, pixelAnalyzer::detectRegions);
                if (regions.isEmpty()) continue;

                double[] imageSize = resolveImageSize(path);
                if (imageSize == null) continue;
                double imgToSpriteX = spriteW / Math.max(1.0, imageSize[0]);
                double imgToSpriteY = spriteH / Math.max(1.0, imageSize[1]);

                for (SpritePixelAnalyzer.DetectedRegion region : regions) {
                    double lx = (-frame.originX * spriteW + region.minX * imgToSpriteX) * frame.scaleX;
                    double ly = (-frame.originY * spriteH + region.minY * imgToSpriteY) * frame.scaleY;
                    double lw = region.width  * imgToSpriteX * frame.scaleX;
                    double lh = region.height * imgToSpriteY * frame.scaleY;

                    double[] c = new double[8];
                    c[0] = entityX + lx * cos - ly * sin;
                    c[1] = entityY + lx * sin + ly * cos;
                    c[2] = entityX + (lx + lw) * cos - ly * sin;
                    c[3] = entityY + (lx + lw) * sin + ly * cos;
                    c[4] = entityX + (lx + lw) * cos - (ly + lh) * sin;
                    c[5] = entityY + (lx + lw) * sin + (ly + lh) * cos;
                    c[6] = entityX + lx * cos - (ly + lh) * sin;
                    c[7] = entityY + lx * sin + (ly + lh) * cos;

                    gc.beginPath();
                    gc.moveTo(c[0], c[1]);
                    gc.lineTo(c[2], c[3]);
                    gc.lineTo(c[4], c[5]);
                    gc.lineTo(c[6], c[7]);
                    gc.closePath();
                    gc.stroke();
                }
            }

            gc.setLineDashes((double[]) null);
            gc.restore();
        }
    }

    private boolean supportsPivotEntity(Entity2D entity) {
        return entity != null;
    }

    private boolean isNearPivotHandle(double screenX, double screenY) {
        if (selectedEntity == null) return false;
        double px = worldToScreenX(selectedEntity.getX());
        double py = worldToScreenY(selectedEntity.getY());
        double pointerX = screenX;
        double pointerY = screenY;
        double dx = pointerX - px;
        double dy = pointerY - py;
        return dx * dx + dy * dy <= 100;
    }

    private void drawScaleHandlesWorld(Entity2D entity, double zoom) {
        double[] corners = getEntityCorners(entity);
        if (corners.length < 8) return;
        double z = Math.max(0.0001, zoom);
        double half = 4.5 / z;
        for (int i = 0; i < 4; i++) {
            double wx = corners[i * 2];
            double wy = corners[i * 2 + 1];
            gc.setFill(draggingScale && scaleDragState != null
                ? Color.web("#f0b673")
                : Color.web("#e0e0e0", 0.95));
            gc.setStroke(Color.web("#1a1a1a", 0.75));
            gc.setLineWidth(0.9 / z);
            gc.fillRect(wx - half, wy - half, half * 2, half * 2);
            gc.strokeRect(wx - half, wy - half, half * 2, half * 2);
        }
    }

    private int findNearScaleHandle(double screenX, double screenY) {
        if (selectedEntity == null) return -1;
        double[] corners = getEntityCorners(selectedEntity);
        if (corners.length < 8) return -1;
        for (int i = 0; i < 4; i++) {
            double sx = worldToScreenX(corners[i * 2]);
            double sy = worldToScreenY(corners[i * 2 + 1]);
            if (Math.hypot(screenX - sx, screenY - sy) <= 9.0) return i;
        }
        return -1;
    }

    private void drawPivotHandleWorld(Entity2D entity, double zoom) {
        double z = Math.max(0.0001, zoom);
        double px = entity.getX();
        double py = entity.getY();
        double arm = 7.0 / z;
        double radius = 3.0 / z;

        gc.setStroke(Color.web("#f7d07a"));
        gc.setLineWidth(1.5 / z);
        gc.strokeLine(px - arm, py, px + arm, py);
        gc.strokeLine(px, py - arm, px, py + arm);
        gc.setFill(Color.web("#f7d07a", 0.8));
        gc.fillOval(px - radius, py - radius, radius * 2.0, radius * 2.0);

        EntityFrame frame = describeEntity(entity);
        if (frame != null) {
            double ox = frame.originX;
            double oy = frame.originY;
            boolean isNonCenter = Math.abs(ox - 0.5) > 0.01 || Math.abs(oy - 0.5) > 0.01;

            if (isNonCenter) {
                double cos = Math.cos(frame.rotationRad);
                double sin = Math.sin(frame.rotationRad);
                double cx = (0.5 - ox) * frame.w * frame.scaleX;
                double cy = (0.5 - oy) * frame.h * frame.scaleY;
                double centerWx = px + cx * cos - cy * sin;
                double centerWy = py + cx * sin + cy * cos;
                double dotR = 2.0 / z;
                gc.setFill(Color.web("#f7d07a", 0.4));
                gc.fillOval(centerWx - dotR, centerWy - dotR, dotR * 2.0, dotR * 2.0);
                gc.setStroke(Color.web("#f7d07a", 0.3));
                gc.setLineWidth(0.8 / z);
                gc.setLineDashes(3.0 / z, 2.0 / z);
                gc.strokeLine(px, py, centerWx, centerWy);
                gc.setLineDashes((double[]) null);
            }

            String label = String.format("(%.2f, %.2f)", ox, oy);
            gc.setFill(Color.web("#f7d07a", 0.7));
            gc.setFont(javafx.scene.text.Font.font(8.0 / z));
            gc.fillText(label, px + arm + (2.0 / z), py - (2.0 / z));
        }
    }

    private void drawRotateHandleWorld(Entity2D entity, double zoom) {
        double z = Math.max(0.0001, zoom);
        double px = entity.getX();
        double py = entity.getY();
        double radius = 45.0 / z;

        gc.setStroke(Color.web("#a08af0", 0.6));
        gc.setLineWidth(3.0 / z);
        gc.strokeOval(px - radius, py - radius, radius * 2.0, radius * 2.0);

        gc.setStroke(Color.web("#c4b8fa", 0.9));
        gc.setLineWidth(1.0 / z);
        gc.strokeOval(px - radius, py - radius, radius * 2.0, radius * 2.0);
    }

    private void drawOrbitAnchorWorld(String entityName, Entity2D entity, double zoom) {
        double[] anchor = getOrbitAnchor(entityName);
        if (anchor == null || entity == null) return;
        double z = Math.max(0.0001, zoom);
        double ax = anchor[0];
        double ay = anchor[1];
        double ex = entity.getX();
        double ey = entity.getY();
        double arm = 7.0 / z;
        double radius = 3.0 / z;

        gc.setStroke(Color.web("#58d68d", 0.75));
        gc.setLineWidth(1.0 / z);
        gc.setLineDashes(4.0 / z, 3.0 / z);
        if (draggingOrbitAnchor && selectedEntityName != null && selectedEntityName.equals(entityName)) {
            double dashOffset = (System.currentTimeMillis() / 20.0) % 20.0;
            gc.setLineDashOffset(-dashOffset / z);
        }
        gc.strokeLine(ax, ay, ex, ey);
        gc.setLineDashes((double[]) null);
        gc.setLineDashOffset(0);

        gc.setStroke(Color.web("#58d68d"));
        gc.setLineWidth(1.5 / z);
        gc.strokeLine(ax - arm, ay, ax + arm, ay);
        gc.strokeLine(ax, ay - arm, ax, ay + arm);
        gc.setFill(Color.web("#58d68d", 0.9));
        gc.fillOval(ax - radius, ay - radius, radius * 2.0, radius * 2.0);
    }

    private PivotDragState buildPivotDragState(Entity2D entity) {
        EntityFrame frame = describeEntity(entity);
        return frame == null ? null : new PivotDragState(frame);
    }

    private void setEntityPivot(Entity2D entity, double pivotX, double pivotY) {
        entity.setOrigin(clampPivot(pivotX), clampPivot(pivotY));
    }

    private static double clampPivot(double v) {
        return clamp(v, PIVOT_MIN, PIVOT_MAX);
    }

    private static double normalizeScale(double value) {
        if (Math.abs(value) >= TRANSFORM_EPSILON) return value;
        return value < 0.0 ? -TRANSFORM_EPSILON : TRANSFORM_EPSILON;
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private void applyCameraTransform() {
        // Scene is rendered in world-overview mode; runtime camera is represented by the red frame.
    }

    private double[] worldToViewport(double worldX, double worldY) {
        return new double[]{worldX, worldY};
    }

    private double worldToScreenX(double worldX) {
        return displayOffsetX + (worldX - displayMinX) * displayScale;
    }

    private double worldToScreenY(double worldY) {
        return displayOffsetY + (worldY - displayMinY) * displayScale;
    }

    public void fitToContent() {
        if (scene == null) return;
        updateViewportTransform(Math.max(1.0, canvas.getWidth()), Math.max(1.0, canvas.getHeight()));

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        for (var entity : scene.getChildren()) {
            double x = entity.getX();
            double y = entity.getY();
            minX = Math.min(minX, x - 50);
            minY = Math.min(minY, y - 50);
            maxX = Math.max(maxX, x + 50);
            maxY = Math.max(maxY, y + 50);
        }

        if (minX == Double.MAX_VALUE) return;

        double w = viewportLogicalWidth;
        double h = viewportLogicalHeight;
        double contentW = maxX - minX;
        double contentH = maxY - minY;

        double zx = w / Math.max(1, contentW);
        double zy = h / Math.max(1, contentH);
        double z = Math.max(0.1, Math.min(zx, zy) * 0.8);

        camera.setZoom(z);
        camera.setPosition(
            minX + contentW / 2 - w / (2 * z),
            minY + contentH / 2 - h / (2 * z)
        );
        notifyCameraStateChanged();
        render();
    }

    private void notifyCameraStateChanged() {
        if (onCameraStateChanged != null) {
            onCameraStateChanged.accept(new double[] { camera.getX(), camera.getY(), camera.getZoom() });
        }
    }

    private void setAssetDropHighlight(boolean active, String label) {
        boolean changed = assetDropHighlightActive != active;
        String normalized = label == null ? null : label.trim();
        if (!changed && java.util.Objects.equals(assetDropLabel, normalized)) return;
        assetDropHighlightActive = active;
        assetDropLabel = active ? normalized : null;
        render();
    }
}
