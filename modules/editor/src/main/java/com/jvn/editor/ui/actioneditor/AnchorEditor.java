package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/**
 * Minimal visual anchor editor. Automatically mirrors the entity selected via the
 * Entities tab or viewport — no dropdown needed. A zoomable, scrollable mini canvas
 * shows the sprite; clicking on the sprite places an anchor at that normalized
 * position. Each anchor can be promoted to the entity's orbit-rotation pivot.
 */
public class AnchorEditor extends VBox {

    // ── Style constants ──────────────────────────────────────────────────────
    private static final String S_HEADER   = "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #e0e0e0;";
    private static final String S_SECTION  = "-fx-text-fill: #c0c0c0; -fx-font-size: 11px; -fx-font-weight: bold;";
    private static final String S_DIM      = "-fx-text-fill: #666; -fx-font-size: 10px;";
    private static final String S_FIELD    = "-fx-background-color: #2d2d2d; -fx-text-fill: #e0e0e0; "
                                           + "-fx-border-color: #444; -fx-border-radius: 3; -fx-background-radius: 3;";
    private static final String S_BTN_CONFIRM = "-fx-background-color: #1a4a6e; -fx-text-fill: #7bc0ff; "
                                              + "-fx-background-radius: 3; -fx-font-size: 11px;";
    private static final String S_BTN_CANCEL  = "-fx-background-color: #2a2a2a; -fx-text-fill: #888; "
                                              + "-fx-background-radius: 3; -fx-font-size: 11px;";
    private static final String S_BTN_PIVOT   = "-fx-background-color: #1a3a20; -fx-text-fill: #58d68d; "
                                              + "-fx-border-color: #2e7d45; -fx-background-radius: 3; "
                                              + "-fx-font-size: 10px; -fx-padding: 2 6 2 6;";
    private static final String S_BTN_REMOVE  = "-fx-background-color: #7a1e1e; -fx-text-fill: #e0e0e0; "
                                              + "-fx-background-radius: 3; -fx-font-size: 10px; -fx-padding: 2 6 2 6;";
    private static final String S_BTN_ZOOM    = "-fx-background-color: #383838; -fx-text-fill: #d0d0d0; "
                                              + "-fx-background-radius: 3; -fx-font-size: 12px; "
                                              + "-fx-min-width: 26; -fx-pref-width: 26; -fx-max-width: 26; "
                                              + "-fx-min-height: 22; -fx-pref-height: 22; "
                                              + "-fx-border-color: #505050; -fx-border-radius: 3;";

    private static final int    CANVAS_BASE_H = 190;
    private static final int    CANVAS_EXPANDED_H = 520;
    private static final double PAD           = 10.0;
    private static final double DOT_R         = 5.0;
    private static final double HIT_R         = DOT_R * 2.5;
    private static final double ZOOM_MIN      = 0.25;
    private static final double ZOOM_MAX      = 5.0;
    private static final double ZOOM_STEP     = 1.4;
    private static final long   ZOOM_ANIM_NS  = 160_000_000L;

    private record GroupVisualRegion(
        double sourceX,
        double sourceY,
        double sourceW,
        double sourceH,
        double minX,
        double minY,
        double maxX,
        double maxY
    ) {}

    private record GroupVisualItem(
        String entityName,
        AnimationProject.SceneEntitySnapshot snapshot,
        Image image,
        List<GroupVisualRegion> regions
    ) {}

    private record GroupVisualBounds(
        List<GroupVisualItem> items,
        double minX,
        double minY,
        double maxX,
        double maxY
    ) {
        boolean isEmpty() {
            return items == null || items.isEmpty()
                || !Double.isFinite(minX) || !Double.isFinite(minY)
                || !Double.isFinite(maxX) || !Double.isFinite(maxY)
                || maxX <= minX || maxY <= minY;
        }

        double width() {
            return isEmpty() ? 0.0 : maxX - minX;
        }

        double height() {
            return isEmpty() ? 0.0 : maxY - minY;
        }
    }

    private record ZoomFocus(
        double relX,
        double relY,
        double viewportX,
        double viewportY
    ) {}

    // ── State ────────────────────────────────────────────────────────────────
    private AnimationProject project;
    private AnimationPreview preview;
    private File             projectRoot;
    private SpritePixelAnalyzer pixelAnalyzer;
    private final Map<String, List<SpritePixelAnalyzer.DetectedRegion>> spriteRegionCache = new HashMap<>();
    private Runnable         onAnchorChanged;
    /** Called with (entityName, anchor) when a new anchor is confirmed. */
    private BiConsumer<String, Anchor> onAnchorPlaced;
    /** Called with (entityName, anchor) when the user clicks "Pivot" on an anchor row. */
    private BiConsumer<String, Anchor> onAnchorUsedAsPivot;

    private String  currentEntityName;
    private boolean currentEntityIsGroup;
    private Image  spriteImage;
    private GroupVisualBounds groupVisualBounds;
    private String selectedAnchorName;

    // zoom
    private double canvasZoom = 1.0;
    private Label  lblZoom;
    private AnimationTimer zoomAnimator;
    private boolean canvasExpanded;
    private Button btnExpandCanvas;

    // pending placement (normalized coords, or -1 when inactive)
    private double pendingRelX = -1;
    private double pendingRelY = -1;

    // ── UI nodes ─────────────────────────────────────────────────────────────
    private Label      lblEntityName;
    private Canvas     miniCanvas;
    private GraphicsContext gc;
    private ScrollPane canvasScroll;
    private HBox       pendingRow;
    private TextField  txtPendingName;
    private VBox       anchorsContainer;
    private Label      lblNoAnchors;
    private ScrollPane anchorListScroll;

    // ── Constructor ──────────────────────────────────────────────────────────
    public AnchorEditor() {
        setSpacing(0);
        setFillWidth(true);
        buildUI();
    }

    // ── Build UI ─────────────────────────────────────────────────────────────
    private void buildUI() {
        // Header
        Label header = new Label("Anchors");
        header.setStyle(S_HEADER);
        header.setPadding(new Insets(10, 10, 6, 10));

        // Entity chip
        Circle chipDot = new Circle(4, Color.web("#a855f7"));
        lblEntityName = new Label("No entity selected");
        lblEntityName.setStyle("-fx-text-fill: #555; -fx-font-size: 11px; -fx-font-style: italic;");
        HBox chipRow = new HBox(6, chipDot, lblEntityName);
        chipRow.setAlignment(Pos.CENTER_LEFT);
        chipRow.setPadding(new Insets(6, 10, 6, 10));

        // Canvas + scroll pane
        miniCanvas = new Canvas(300, CANVAS_BASE_H);
        gc = miniCanvas.getGraphicsContext2D();
        miniCanvas.setOnMousePressed(e -> onCanvasClick(e.getX(), e.getY()));

        canvasScroll = new ScrollPane(miniCanvas);
        canvasScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        canvasScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        canvasScroll.setFitToWidth(false);
        canvasScroll.setFitToHeight(false);
        canvasScroll.setStyle(
            "-fx-background: #141414; -fx-background-color: #141414; "
            + "-fx-border-color: transparent;"
        );
        canvasScroll.setMaxHeight(CANVAS_BASE_H + 2);
        canvasScroll.setPrefHeight(canvasBaseHeight() + 2);
        VBox.setVgrow(canvasScroll, Priority.NEVER);
        canvasScroll.setOnScroll(e -> {
            if (e.isControlDown() || e.isShortcutDown()) {
                double[] focus = canvasFocusFromViewportEvent(e.getX(), e.getY());
                smoothZoomTo(
                    e.getDeltaY() > 0 ? canvasZoom * ZOOM_STEP : canvasZoom / ZOOM_STEP,
                    focus[0],
                    focus[1]
                );
                e.consume();
            }
        });
        miniCanvas.setOnScroll(e -> {
            if (e.isControlDown() || e.isShortcutDown()) {
                smoothZoomTo(
                    e.getDeltaY() > 0 ? canvasZoom * ZOOM_STEP : canvasZoom / ZOOM_STEP,
                    e.getX(),
                    e.getY()
                );
                e.consume();
            }
        });

        // Listen to viewport width to resize canvas
        canvasScroll.viewportBoundsProperty().addListener((obs, ov, nv) -> {
            if (nv != null && nv.getWidth() > 0) updateCanvasSize(nv.getWidth());
        });
        // Also fall back to VBox width when viewport not yet measured
        widthProperty().addListener((obs, ov, nv) -> {
            double vw = canvasScroll.getViewportBounds() != null
                ? canvasScroll.getViewportBounds().getWidth() : 0;
            updateCanvasSize(vw > 0 ? vw : nv.doubleValue());
        });

        // Zoom bar
        Button btnZoomOut = new Button("−");
        btnZoomOut.setStyle(S_BTN_ZOOM);
        btnZoomOut.setOnAction(e -> smoothZoomTo(canvasZoom / ZOOM_STEP));

        Button btnZoomIn = new Button("+");
        btnZoomIn.setStyle(S_BTN_ZOOM);
        btnZoomIn.setOnAction(e -> smoothZoomTo(canvasZoom * ZOOM_STEP));

        Button btnZoomReset = new Button("1:1");
        btnZoomReset.setStyle(S_BTN_ZOOM + "-fx-min-width: 30; -fx-pref-width: 30;");
        btnZoomReset.setOnAction(e -> smoothZoomTo(1.0));

        Button btnViewportPlace = new Button("Viewport");
        btnViewportPlace.setStyle(S_BTN_ZOOM + "-fx-min-width: 70; -fx-pref-width: 70; -fx-max-width: 70;");
        btnViewportPlace.setTooltip(new Tooltip("Place the next anchor in the main viewport with focused zoom"));
        btnViewportPlace.setOnAction(e -> beginViewportPlacement());

        btnExpandCanvas = new Button("Expand");
        btnExpandCanvas.setStyle(S_BTN_ZOOM + "-fx-min-width: 74; -fx-pref-width: 74; -fx-max-width: 84;");
        btnExpandCanvas.setOnAction(e -> setCanvasExpanded(!canvasExpanded));
        updateCanvasExpandButton();

        lblZoom = new Label("100%");
        lblZoom.setStyle("-fx-text-fill: #aaa; -fx-font-size: 10px; -fx-min-width: 36; -fx-alignment: center;");

        Label zoomLabel = new Label("Zoom:");
        zoomLabel.setStyle("-fx-text-fill: #777; -fx-font-size: 10px;");

        HBox zoomBar = new HBox(5, zoomLabel, btnZoomOut, lblZoom, btnZoomIn, btnZoomReset, btnViewportPlace, btnExpandCanvas);
        zoomBar.setAlignment(Pos.CENTER_LEFT);
        zoomBar.setPadding(new Insets(4, 8, 4, 8));
        zoomBar.setStyle(
            "-fx-background-color: #252525; "
            + "-fx-border-color: #333 transparent #333 transparent; "
            + "-fx-border-width: 1 0 1 0;"
        );

        // Pending-anchor name row
        txtPendingName = new TextField();
        txtPendingName.setPromptText("Anchor name (e.g. elbow) — Enter to confirm, Esc to cancel");
        txtPendingName.setStyle(S_FIELD);
        HBox.setHgrow(txtPendingName, Priority.ALWAYS);
        txtPendingName.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER)  confirmPending();
            if (e.getCode() == KeyCode.ESCAPE) cancelPending();
        });

        Button btnPlace  = new Button("Place");
        btnPlace.setStyle(S_BTN_CONFIRM);
        btnPlace.setOnAction(e -> confirmPending());

        Button btnCancel = new Button("Cancel");
        btnCancel.setStyle(S_BTN_CANCEL);
        btnCancel.setOnAction(e -> cancelPending());

        pendingRow = new HBox(5, txtPendingName, btnPlace, btnCancel);
        pendingRow.setPadding(new Insets(5, 8, 4, 8));
        pendingRow.setAlignment(Pos.CENTER_LEFT);
        pendingRow.setStyle("-fx-background-color: #1a1124; -fx-border-color: #5b21b6; "
                          + "-fx-border-width: 0 0 0 3;");
        setPendingRowVisible(false);

        // Anchor list
        Label lblHdr = new Label("Defined Anchors");
        lblHdr.setStyle(S_SECTION);
        lblHdr.setPadding(new Insets(8, 10, 4, 10));

        lblNoAnchors = new Label("No anchors yet — click anywhere on the sprite above to place one.");
        lblNoAnchors.setStyle(S_DIM);
        lblNoAnchors.setPadding(new Insets(2, 10, 6, 10));
        lblNoAnchors.setWrapText(true);

        anchorsContainer = new VBox(2);
        anchorsContainer.setPadding(new Insets(0, 6, 6, 6));

        anchorListScroll = new ScrollPane(anchorsContainer);
        anchorListScroll.setFitToWidth(true);
        anchorListScroll.setMaxHeight(180);
        anchorListScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        anchorListScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        getChildren().addAll(
            header, new Separator(),
            chipRow, new Separator(),
            canvasScroll, zoomBar, pendingRow,
            new Separator(),
            lblHdr, lblNoAnchors, anchorListScroll
        );

        redrawCanvas();
    }

    // ── Zoom ─────────────────────────────────────────────────────────────────

    private void smoothZoomTo(double targetZoom) {
        double[] center = viewportCenterCanvasPoint();
        smoothZoomTo(targetZoom, center[0], center[1]);
    }

    private void smoothZoomTo(double targetZoom, double focusCanvasX, double focusCanvasY) {
        double target = clampZoom(targetZoom);
        if (Math.abs(target - canvasZoom) < 0.001) return;
        if (zoomAnimator != null) zoomAnimator.stop();

        ZoomFocus focus = buildZoomFocus(focusCanvasX, focusCanvasY);
        double start = canvasZoom;
        zoomAnimator = new AnimationTimer() {
            private long startedAt = -1L;

            @Override
            public void handle(long now) {
                if (startedAt < 0L) startedAt = now;
                double t = Math.min(1.0, (now - startedAt) / (double) ZOOM_ANIM_NS);
                double eased = 1.0 - Math.pow(1.0 - t, 3);
                setZoomImmediate(start + (target - start) * eased, focus);
                if (t >= 1.0) {
                    stop();
                    zoomAnimator = null;
                    setZoomImmediate(target, focus);
                }
            }
        };
        zoomAnimator.start();
    }

    private void setZoomImmediate(double z, ZoomFocus focus) {
        canvasZoom = clampZoom(z);
        int pct = (int) Math.round(canvasZoom * 100);
        lblZoom.setText(pct + "%");
        double vw = canvasScroll.getViewportBounds() != null
            ? canvasScroll.getViewportBounds().getWidth() : getWidth();
        if (vw <= 0) vw = getWidth();
        updateCanvasSize(vw, focus);
    }

    private double clampZoom(double z) {
        return Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, z));
    }

    private void updateCanvasSize(double viewportWidth) {
        updateCanvasSize(viewportWidth, null);
    }

    private void updateCanvasSize(double viewportWidth, ZoomFocus focus) {
        if (viewportWidth <= 0) return;
        double cw = Math.max(viewportWidth, viewportWidth * canvasZoom);
        double ch = canvasBaseHeight() * canvasZoom;
        if (canvasExpanded) {
            canvasScroll.setMaxHeight(Double.MAX_VALUE);
            canvasScroll.setPrefHeight(Math.min(ch + 2, canvasBaseHeight() + 160));
        } else {
            canvasScroll.setMaxHeight(Math.min(ch + 2, CANVAS_BASE_H * 2));
            canvasScroll.setPrefHeight(Math.min(ch + 2, CANVAS_BASE_H * 2));
        }
        miniCanvas.setWidth(cw);
        miniCanvas.setHeight(ch);
        restoreZoomFocus(focus, cw, ch);
        redrawCanvas();
    }

    private int canvasBaseHeight() {
        return canvasExpanded ? CANVAS_EXPANDED_H : CANVAS_BASE_H;
    }

    private void setCanvasExpanded(boolean expanded) {
        canvasExpanded = expanded;
        VBox.setVgrow(canvasScroll, expanded ? Priority.ALWAYS : Priority.NEVER);
        if (anchorListScroll != null) {
            anchorListScroll.setMaxHeight(expanded ? 140 : 180);
        }
        updateCanvasExpandButton();
        double vw = canvasScroll.getViewportBounds() != null
            ? canvasScroll.getViewportBounds().getWidth()
            : getWidth();
        updateCanvasSize(vw > 0 ? vw : getWidth());
    }

    private void updateCanvasExpandButton() {
        if (btnExpandCanvas == null) return;
        btnExpandCanvas.setText(canvasExpanded ? "Compact" : "Expand");
        btnExpandCanvas.setTooltip(new Tooltip(canvasExpanded
            ? "Return the anchor canvas to its compact height"
            : "Grow the anchor canvas inside this panel for precise placement"));
        btnExpandCanvas.setStyle(S_BTN_ZOOM
            + "-fx-min-width: 74; -fx-pref-width: 74; -fx-max-width: 84;"
            + (canvasExpanded ? "-fx-background-color: #4a3a16; -fx-text-fill: #f4d58a;" : ""));
    }

    private ZoomFocus buildZoomFocus(double canvasX, double canvasY) {
        double cw = Math.max(1.0, miniCanvas.getWidth());
        double ch = Math.max(1.0, miniCanvas.getHeight());
        double[] scroll = currentScrollPixels();
        double relX = Math.max(0.0, Math.min(1.0, canvasX / cw));
        double relY = Math.max(0.0, Math.min(1.0, canvasY / ch));
        return new ZoomFocus(relX, relY, canvasX - scroll[0], canvasY - scroll[1]);
    }

    private double[] viewportCenterCanvasPoint() {
        double[] scroll = currentScrollPixels();
        double vw = viewportWidth();
        double vh = viewportHeight();
        return new double[]{scroll[0] + vw / 2.0, scroll[1] + vh / 2.0};
    }

    private double[] canvasFocusFromViewportEvent(double viewportX, double viewportY) {
        double[] scroll = currentScrollPixels();
        return new double[]{scroll[0] + viewportX, scroll[1] + viewportY};
    }

    private double[] currentScrollPixels() {
        double cw = Math.max(0.0, miniCanvas.getWidth());
        double ch = Math.max(0.0, miniCanvas.getHeight());
        double vw = viewportWidth();
        double vh = viewportHeight();
        double maxX = Math.max(0.0, cw - vw);
        double maxY = Math.max(0.0, ch - vh);
        return new double[]{canvasScroll.getHvalue() * maxX, canvasScroll.getVvalue() * maxY};
    }

    private void restoreZoomFocus(ZoomFocus focus, double canvasW, double canvasH) {
        if (focus == null) return;
        double vw = viewportWidth();
        double vh = viewportHeight();
        double maxX = Math.max(0.0, canvasW - vw);
        double maxY = Math.max(0.0, canvasH - vh);
        if (maxX > 0.0) {
            double scrollX = focus.relX() * canvasW - focus.viewportX();
            canvasScroll.setHvalue(Math.max(0.0, Math.min(1.0, scrollX / maxX)));
        } else {
            canvasScroll.setHvalue(0.0);
        }
        if (maxY > 0.0) {
            double scrollY = focus.relY() * canvasH - focus.viewportY();
            canvasScroll.setVvalue(Math.max(0.0, Math.min(1.0, scrollY / maxY)));
        } else {
            canvasScroll.setVvalue(0.0);
        }
    }

    private double viewportWidth() {
        return canvasScroll.getViewportBounds() == null || canvasScroll.getViewportBounds().getWidth() <= 0
            ? Math.max(1.0, canvasScroll.getWidth())
            : canvasScroll.getViewportBounds().getWidth();
    }

    private double viewportHeight() {
        return canvasScroll.getViewportBounds() == null || canvasScroll.getViewportBounds().getHeight() <= 0
            ? Math.max(1.0, canvasScroll.getHeight())
            : canvasScroll.getViewportBounds().getHeight();
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public void setProject(AnimationProject project) {
        this.project = project;
        refreshAnchorList();
        redrawCanvas();
    }

    public void setProjectRoot(File root) {
        this.projectRoot = root;
        this.pixelAnalyzer = new SpritePixelAnalyzer(root);
        this.spriteRegionCache.clear();
        if (currentEntityName != null) loadEntityImage(currentEntityName);
        redrawCanvas();
    }

    public void setOnAnchorChanged(Runnable cb) {
        this.onAnchorChanged = cb;
    }

    /**
     * Called with (entityName, anchor) immediately after a new anchor is confirmed.
     * PuppeteerWindow uses this to seed PIVOT_X/Y keyframes at t=0.
     */
    public void setOnAnchorPlaced(BiConsumer<String, Anchor> cb) {
        this.onAnchorPlaced = cb;
    }

    /**
     * Called with (entityName, anchor) when the user clicks "Set as Pivot" on an anchor row.
     * PuppeteerWindow uses this to insert PIVOT_X/Y keyframes at the current playhead time.
     */
    public void setOnAnchorUsedAsPivot(BiConsumer<String, Anchor> cb) {
        this.onAnchorUsedAsPivot = cb;
    }

    public void setAnimationPreview(AnimationPreview previewCanvas) {
        this.preview = previewCanvas;
    }

    /**
     * Called by PuppeteerWindow whenever the viewport / timeline / entity-tab selection changes.
     * Pass {@code null} when no entity (group, camera, or nothing) is selected.
     */
    public void setSelectedEntityName(String entityName) {
        setSelectedEntityName(entityName, false);
    }

    /**
     * Called by PuppeteerWindow whenever the viewport / timeline / entity-tab selection changes.
     * Pass {@code null} when no entity (group, camera, or nothing) is selected.
     * @param isGroup true if the selected entity is a group, false otherwise
     */
    public void setSelectedEntityName(String entityName, boolean isGroup) {
        if (entityName != null && entityName.equals(currentEntityName) && isGroup == currentEntityIsGroup) return;
        currentEntityName = entityName;
        currentEntityIsGroup = isGroup;
        selectedAnchorName = null;
        clearPending();
        updateEntityChip();
        loadEntityImage(entityName);
        refreshAnchorList();
        redrawCanvas();
    }

    // ── Entity chip ──────────────────────────────────────────────────────────

    private void updateEntityChip() {
        if (currentEntityName == null || currentEntityName.isBlank()) {
            lblEntityName.setText("No entity selected");
            lblEntityName.setStyle("-fx-text-fill: #555; -fx-font-size: 11px; -fx-font-style: italic;");
        } else {
            String label = currentEntityIsGroup ? "[Group] " + currentEntityName : currentEntityName;
            lblEntityName.setText(label);
            lblEntityName.setStyle(currentEntityIsGroup
                ? "-fx-text-fill: #f0b673; -fx-font-size: 11px; -fx-font-weight: bold;"
                : "-fx-text-fill: #d8b4fe; -fx-font-size: 11px; -fx-font-weight: bold;");
        }
    }

    // ── Image loading ────────────────────────────────────────────────────────

    private void loadEntityImage(String entityName) {
        spriteImage = null;
        groupVisualBounds = null;
        if (entityName == null || entityName.isBlank() || project == null) return;
        if (currentEntityIsGroup) {
            groupVisualBounds = buildGroupVisualBounds(entityName);
            return;
        }
        AnimationProject.SceneEntitySnapshot snap = project.getSceneEntitySnapshot(entityName);
        if (snap != null && !snap.imagePath().isBlank()) {
            spriteImage = resolveImage(snap.imagePath().split("\\|")[0].trim());
        }
    }

    private GroupVisualBounds buildGroupVisualBounds(String groupName) {
        if (project == null || groupName == null || groupName.isBlank()) return null;
        List<GroupVisualItem> items = new ArrayList<>();
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (String entityName : project.collectGroupEntityNames(groupName)) {
            AnimationProject.SceneEntitySnapshot snap = project.getSceneEntitySnapshot(entityName);
            if (snap == null) continue;
            List<GroupVisualItem> entityItems = buildGroupVisualItems(entityName, snap);
            if (entityItems.isEmpty()) continue;
            for (GroupVisualItem item : entityItems) {
                items.add(item);
                for (GroupVisualRegion region : item.regions()) {
                    minX = Math.min(minX, region.minX());
                    minY = Math.min(minY, region.minY());
                    maxX = Math.max(maxX, region.maxX());
                    maxY = Math.max(maxY, region.maxY());
                }
            }
        }

        items.sort(Comparator
            .comparingDouble((GroupVisualItem item) -> item.snapshot().z())
            .thenComparing(GroupVisualItem::entityName, String.CASE_INSENSITIVE_ORDER));
        return new GroupVisualBounds(items, minX, minY, maxX, maxY);
    }

    private List<GroupVisualItem> buildGroupVisualItems(String entityName, AnimationProject.SceneEntitySnapshot snap) {
        if (snap == null) return List.of();
        String imageSpec = snap.imagePath();
        if (imageSpec == null || imageSpec.isBlank()) {
            double minX = snap.x() - snap.originX() * snap.width();
            double minY = snap.y() - snap.originY() * snap.height();
            return List.of(new GroupVisualItem(entityName, snap, null,
                List.of(new GroupVisualRegion(0, 0, snap.width(), snap.height(), minX, minY, minX + snap.width(), minY + snap.height()))));
        }

        List<GroupVisualItem> items = new ArrayList<>();
        for (String layerPath : imageSpec.split("\\|")) {
            String path = layerPath == null ? "" : layerPath.trim();
            if (path.isEmpty()) continue;
            Image image = resolveImage(path);
            List<GroupVisualRegion> regions = buildVisibleRegions(snap, path, image);
            if (regions.isEmpty()) continue;
            items.add(new GroupVisualItem(entityName, snap, image, regions));
        }
        return items;
    }

    private List<GroupVisualRegion> buildVisibleRegions(AnimationProject.SceneEntitySnapshot snap, String path, Image image) {
        double imageW = image != null && !image.isError() && image.getWidth() > 1 ? image.getWidth() : snap.width();
        double imageH = image != null && !image.isError() && image.getHeight() > 1 ? image.getHeight() : snap.height();
        double imgToSpriteX = snap.width() / Math.max(1.0, imageW);
        double imgToSpriteY = snap.height() / Math.max(1.0, imageH);
        List<SpritePixelAnalyzer.DetectedRegion> detected = pixelAnalyzer != null
            ? spriteRegionCache.computeIfAbsent(path, pixelAnalyzer::detectRegions)
            : List.of();
        if (detected.isEmpty()) {
            double minX = snap.x() - snap.originX() * snap.width();
            double minY = snap.y() - snap.originY() * snap.height();
            return List.of(new GroupVisualRegion(0, 0, imageW, imageH, minX, minY, minX + snap.width(), minY + snap.height()));
        }

        List<GroupVisualRegion> regions = new ArrayList<>();
        for (SpritePixelAnalyzer.DetectedRegion region : detected) {
            double minX = snap.x() - snap.originX() * snap.width() + region.minX * imgToSpriteX;
            double minY = snap.y() - snap.originY() * snap.height() + region.minY * imgToSpriteY;
            double width = region.width * imgToSpriteX;
            double height = region.height * imgToSpriteY;
            regions.add(new GroupVisualRegion(
                region.minX,
                region.minY,
                region.width,
                region.height,
                minX,
                minY,
                minX + width,
                minY + height
            ));
        }
        return regions;
    }

    private Image resolveImage(String path) {
        if (path == null || path.isBlank()) return null;
        try {
            File f = new File(path);
            if (f.isAbsolute() && f.exists()) return new Image(f.toURI().toString(), false);
        } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
        try {
            if (projectRoot != null) {
                File rel = new File(projectRoot, path);
                if (rel.exists()) return new Image(rel.toURI().toString(), false);
            }
        } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
        try {
            java.net.URL url = getClass().getClassLoader().getResource(path);
            if (url != null) return new Image(url.toExternalForm(), false);
        } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
            }
        return null;
    }

    // ── Canvas ───────────────────────────────────────────────────────────────

    private void onCanvasClick(double cx, double cy) {
        if (currentEntityName == null) return;

        // Hit-test existing anchors first
        String hit = anchorNear(cx, cy);
        if (hit != null) {
            selectedAnchorName = hit.equals(selectedAnchorName) ? null : hit;
            clearPending();
            refreshAnchorList();
            redrawCanvas();
            return;
        }

        // Otherwise start a new placement
        double[] norm = canvasToNorm(cx, cy);
        if (norm == null) return;
        pendingRelX = norm[0];
        pendingRelY = norm[1];
        setPendingRowVisible(true);
        txtPendingName.clear();
        txtPendingName.requestFocus();
        redrawCanvas();
    }

    private void beginViewportPlacement() {
        if (preview == null || currentEntityName == null || currentEntityName.isBlank()) return;
        clearPending();
        preview.setAnchorPlacementMode(true);
        preview.focusAnchorPlacementOnSelection();
        preview.render();
    }

    public void startPendingPlacement(double relX, double relY) {
        if (currentEntityName == null) return;
        pendingRelX = Math.max(0.0, Math.min(1.0, relX));
        pendingRelY = Math.max(0.0, Math.min(1.0, relY));
        setPendingRowVisible(true);
        txtPendingName.clear();
        txtPendingName.requestFocus();
        redrawCanvas();
    }

    private void redrawCanvas() {
        double cw = miniCanvas.getWidth();
        double ch = miniCanvas.getHeight();
        if (cw <= 0 || ch <= 0) return;

        gc.setFill(Color.web("#141414"));
        gc.fillRect(0, 0, cw, ch);

        if (currentEntityName == null) {
            // No entity selected — show centered hint
            gc.setFill(Color.web("#3a3a3a"));
            gc.setFont(javafx.scene.text.Font.font("System", 11));
            String hint = "Select an entity from the Entities tab";
            gc.fillText(hint, PAD, ch / 2.0 + 4);
            drawCanvasBorder(cw, ch);
            return;
        }

        double[] b = imgBounds(cw, ch);
        double imgX = b[0], imgY = b[1], imgW = b[2], imgH = b[3];

        // Sprite, group contents, or placeholder
        if (currentEntityIsGroup) {
            drawGroupVisual(imgX, imgY, imgW, imgH);
        } else if (spriteImage != null && !spriteImage.isError()) {
            gc.drawImage(spriteImage, imgX, imgY, imgW, imgH);
        } else {
            gc.setFill(Color.web("#222"));
            gc.fillRect(imgX, imgY, imgW, imgH);
            gc.setStroke(Color.web("#3a3a3a"));
            gc.setLineWidth(1);
            gc.strokeRect(imgX + 0.5, imgY + 0.5, imgW - 1, imgH - 1);
            gc.setFill(Color.web("#555"));
            gc.setFont(javafx.scene.text.Font.font(10));
            gc.fillText("sprite unavailable", imgX + 6, imgY + imgH / 2 + 4);
        }

        // Existing anchors
        if (project != null) {
            Map<String, Anchor> anchors = project.getAnchorsForEntity(currentEntityName);
            if (anchors != null) {
                for (Map.Entry<String, Anchor> e : anchors.entrySet()) {
                    Anchor a = e.getValue();
                    if (!a.isRelative()) continue;
                    boolean sel = e.getKey().equals(selectedAnchorName);
                    double ax = imgX + a.getX() * imgW;
                    double ay = imgY + a.getY() * imgH;
                    drawDot(ax, ay, sel ? Color.web("#f0b673") : Color.web("#a855f7"), sel);
                    gc.setFill(sel ? Color.web("#f0e0a0") : Color.web("#c084fc", 0.9));
                    gc.setFont(javafx.scene.text.Font.font(9));
                    gc.fillText(e.getKey(), ax + DOT_R + 3, ay + 4);
                }
            }
        }

        // Pending anchor (half-transparent orange dot)
        if (pendingRelX >= 0) {
            gc.setGlobalAlpha(0.7);
            drawDot(imgX + pendingRelX * imgW, imgY + pendingRelY * imgH, Color.web("#f0b673"), false);
            gc.setGlobalAlpha(1.0);
        }

        // Bottom instruction overlay (always visible when entity selected + no pending)
        if (pendingRelX < 0) {
            double overlayH = 18;
            gc.setFill(Color.web("#000", 0.55));
            gc.fillRect(0, ch - overlayH, cw, overlayH);
            gc.setFill(Color.web("#a0a0a0"));
            gc.setFont(javafx.scene.text.Font.font(9.5));
            String hint = currentEntityIsGroup
                ? "Click on group contents to place anchor  ·  Click dot to select"
                : "Click on sprite to place anchor  ·  Click dot to select";
            gc.fillText(hint, PAD, ch - 5);
        }

        drawCanvasBorder(cw, ch);
    }

    private void drawDot(double cx, double cy, Color color, boolean selected) {
        if (selected) {
            double r2 = DOT_R + 3;
            gc.setFill(Color.web("#fff", 0.18));
            gc.fillOval(cx - r2, cy - r2, r2 * 2, r2 * 2);
            gc.setStroke(Color.web("#fff", 0.55));
            gc.setLineWidth(0.8);
            gc.strokeOval(cx - r2, cy - r2, r2 * 2, r2 * 2);
        }
        gc.setFill(color);
        gc.fillOval(cx - DOT_R, cy - DOT_R, DOT_R * 2, DOT_R * 2);
        gc.setStroke(Color.web("#000", 0.4));
        gc.setLineWidth(0.7);
        double arm = DOT_R + 3;
        gc.strokeLine(cx - arm, cy, cx + arm, cy);
        gc.strokeLine(cx, cy - arm, cx, cy + arm);
    }

    private void drawGroupVisual(double imgX, double imgY, double imgW, double imgH) {
        gc.setFill(Color.web("#202020"));
        gc.fillRect(imgX, imgY, imgW, imgH);
        gc.setStroke(Color.web("#f0b673"));
        gc.setLineWidth(2);
        gc.strokeRect(imgX + 0.5, imgY + 0.5, imgW - 1, imgH - 1);

        if (groupVisualBounds == null || groupVisualBounds.isEmpty()) {
            gc.setFill(Color.web("#f0b673"));
            gc.setFont(javafx.scene.text.Font.font(10));
            gc.fillText("Group bounds", imgX + 6, imgY + imgH / 2 + 4);
            return;
        }

        for (GroupVisualItem item : groupVisualBounds.items()) {
            for (GroupVisualRegion region : item.regions()) {
                double x = imgX + ((region.minX() - groupVisualBounds.minX()) / groupVisualBounds.width()) * imgW;
                double y = imgY + ((region.minY() - groupVisualBounds.minY()) / groupVisualBounds.height()) * imgH;
                double w = Math.max(1.0, (region.maxX() - region.minX()) / groupVisualBounds.width() * imgW);
                double h = Math.max(1.0, (region.maxY() - region.minY()) / groupVisualBounds.height() * imgH);
                Image image = item.image();
                if (image != null && !image.isError()) {
                    gc.setGlobalAlpha(Math.max(0.15, Math.min(1.0, item.snapshot().alpha())));
                    gc.drawImage(image, region.sourceX(), region.sourceY(), region.sourceW(), region.sourceH(), x, y, w, h);
                    gc.setGlobalAlpha(1.0);
                } else {
                    gc.setFill(Color.web("#333333", 0.80));
                    gc.fillRect(x, y, w, h);
                    gc.setStroke(Color.web("#777777", 0.65));
                    gc.setLineWidth(1);
                    gc.strokeRect(x + 0.5, y + 0.5, w - 1, h - 1);
                }
            }
        }
    }

    private void drawCanvasBorder(double cw, double ch) {
        gc.setStroke(Color.web("#2a2a2a"));
        gc.setLineWidth(1);
        gc.strokeRect(0.5, 0.5, cw - 1, ch - 1);
    }

    /** [x, y, w, h] rect in canvas space where the sprite is drawn (with PAD, aspect-correct). */
    private double[] imgBounds(double cw, double ch) {
        double availW = cw - PAD * 2;
        double availH = ch - PAD * 2 - 18; // leave room for the bottom instruction strip
        double imgW, imgH;

        if (currentEntityIsGroup && groupVisualBounds != null && !groupVisualBounds.isEmpty()) {
            double scale = Math.min(availW / groupVisualBounds.width(), availH / groupVisualBounds.height());
            imgW = groupVisualBounds.width() * scale;
            imgH = groupVisualBounds.height() * scale;
        } else if (spriteImage != null && !spriteImage.isError()
                && spriteImage.getWidth() > 0 && spriteImage.getHeight() > 0) {
            double iw = spriteImage.getWidth();
            double ih = spriteImage.getHeight();
            double scale = Math.min(availW / iw, availH / ih);
            imgW = iw * scale;
            imgH = ih * scale;
        } else {
            imgW = availW;
            imgH = availH * 0.65;
        }

        double imgX = PAD + (availW - imgW) / 2.0;
        double imgY = PAD + (availH - imgH) / 2.0;
        return new double[]{imgX, imgY, imgW, imgH};
    }

    /** Canvas click → normalized (0–1) sprite coords.  Returns null if click was outside the sprite rect. */
    private double[] canvasToNorm(double cx, double cy) {
        double[] b = imgBounds(miniCanvas.getWidth(), miniCanvas.getHeight());
        double rx = (cx - b[0]) / b[2];
        double ry = (cy - b[1]) / b[3];
        if (rx < 0 || rx > 1 || ry < 0 || ry > 1) return null;
        return new double[]{rx, ry};
    }

    /** First anchor whose canvas dot is within HIT_R pixels of (cx, cy). */
    private String anchorNear(double cx, double cy) {
        if (project == null || currentEntityName == null) return null;
        Map<String, Anchor> anchors = project.getAnchorsForEntity(currentEntityName);
        if (anchors == null) return null;
        double[] b = imgBounds(miniCanvas.getWidth(), miniCanvas.getHeight());
        String best = null;
        double bestDist = HIT_R;
        for (Map.Entry<String, Anchor> e : anchors.entrySet()) {
            Anchor a = e.getValue();
            if (!a.isRelative()) continue;
            double ax = b[0] + a.getX() * b[2];
            double ay = b[1] + a.getY() * b[3];
            double d = Math.hypot(cx - ax, cy - ay);
            if (d < bestDist) { bestDist = d; best = e.getKey(); }
        }
        return best;
    }

    // ── Pending anchor ───────────────────────────────────────────────────────

    private void setPendingRowVisible(boolean v) {
        pendingRow.setVisible(v);
        pendingRow.setManaged(v);
    }

    private void confirmPending() {
        if (currentEntityName == null || project == null) { cancelPending(); return; }
        String name = txtPendingName.getText().trim();
        if (name.isBlank()) name = autoName();
        Anchor placed = new Anchor(name, pendingRelX, pendingRelY, true);
        project.setAnchor(currentEntityName, placed);
        clearPending();
        refreshAnchorList();
        redrawCanvas();
        if (onAnchorChanged != null) onAnchorChanged.run();
        if (preview != null) {
            preview.setOrbitToolEnabled(true);
            preview.useAnchorAsOrbitPivot(currentEntityName, name);
        }
        if (onAnchorPlaced != null) onAnchorPlaced.accept(currentEntityName, placed);
        if (preview != null) preview.render();
    }

    private void cancelPending() {
        pendingRelX = -1;
        pendingRelY = -1;
        setPendingRowVisible(false);
        redrawCanvas();
    }

    private void clearPending() {
        pendingRelX = -1;
        pendingRelY = -1;
        setPendingRowVisible(false);
        if (txtPendingName != null) txtPendingName.clear();
    }

    private String autoName() {
        Map<String, Anchor> existing = project == null || currentEntityName == null
            ? null : project.getAnchorsForEntity(currentEntityName);
        int n = (existing == null) ? 0 : existing.size();
        String name;
        do { name = "joint_" + (++n); }
        while (existing != null && existing.containsKey(name));
        return name;
    }

    // ── Anchor list ──────────────────────────────────────────────────────────

    private void refreshAnchorList() {
        anchorsContainer.getChildren().clear();
        Map<String, Anchor> anchors = project == null || currentEntityName == null
            ? null : project.getAnchorsForEntity(currentEntityName);

        boolean hasAnchors = anchors != null && !anchors.isEmpty();
        lblNoAnchors.setVisible(!hasAnchors);
        lblNoAnchors.setManaged(!hasAnchors);

        if (hasAnchors && anchors != null) {
            for (Map.Entry<String, Anchor> e : anchors.entrySet()) {
                anchorsContainer.getChildren().add(anchorRow(e.getKey(), e.getValue()));
            }
        }
    }

    private HBox anchorRow(String name, Anchor anchor) {
        boolean sel = name.equals(selectedAnchorName);

        Rectangle dot = new Rectangle(8, 8, sel ? Color.web("#f0b673") : Color.web("#a855f7"));
        dot.setArcWidth(2);
        dot.setArcHeight(2);

        Label lblName = new Label(name);
        lblName.setStyle(sel
            ? "-fx-text-fill: #f0e0a0; -fx-font-size: 11px; -fx-font-weight: bold;"
            : "-fx-text-fill: #d0d0d0; -fx-font-size: 11px;");
        HBox.setHgrow(lblName, Priority.ALWAYS);

        Label lblCoords = new Label(String.format("%.2f, %.2f", anchor.getX(), anchor.getY()));
        lblCoords.setStyle(S_DIM);

        Button btnPivot = new Button("Set Pivot");
        btnPivot.setStyle(S_BTN_PIVOT);
        btnPivot.setTooltip(new Tooltip("Use this anchor as the rotation pivot (adds PIVOT_X/Y keyframe at current time)"));
        btnPivot.setOnAction(e -> {
            if (currentEntityName == null) return;
            if (preview != null) {
                preview.setOrbitToolEnabled(true);
                preview.useAnchorAsOrbitPivot(currentEntityName, name);
            }
            if (onAnchorUsedAsPivot != null) onAnchorUsedAsPivot.accept(currentEntityName, anchor);
        });

        Button btnRemove = new Button("×");
        btnRemove.setStyle(S_BTN_REMOVE);
        btnRemove.setOnAction(e -> {
            if (project == null || currentEntityName == null) return;
            project.removeAnchor(currentEntityName, name);
            if (name.equals(selectedAnchorName)) selectedAnchorName = null;
            refreshAnchorList();
            redrawCanvas();
            if (onAnchorChanged != null) onAnchorChanged.run();
            if (preview != null) preview.render();
        });

        HBox row = new HBox(6, dot, lblName, lblCoords, btnPivot, btnRemove);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 4, 3, 4));
        row.setStyle(sel
            ? "-fx-background-color: #1e1424; -fx-background-radius: 4;"
            : "-fx-background-color: transparent;");

        row.setOnMousePressed(e -> {
            selectedAnchorName = name.equals(selectedAnchorName) ? null : name;
            clearPending();
            refreshAnchorList();
            redrawCanvas();
        });

        return row;
    }
}
