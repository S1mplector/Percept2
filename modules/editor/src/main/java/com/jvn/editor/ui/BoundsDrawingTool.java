package com.jvn.editor.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.jvn.core.ui.BoundsPointCodec;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Reusable visual tool for drawing, editing, and managing bounds
 * on top of an asset image. Supports three interaction modes:
 *
 * <ul>
 *   <li><b>Select</b> – click to select an existing bound, drag to move, corner handles to resize</li>
 *   <li><b>Rectangle</b> – click-drag on the canvas to draw a new rectangular bound</li>
 *   <li><b>Point-nail</b> – click to place corner points, then generate polygon or rectangle bounds</li>
 * </ul>
 *
 * Bounds are emitted as normalized coordinates (0..1 relative to the canvas/viewport).
 * Optional polygon points are stored normalized relative to each bound's rectangle.
 */
public class BoundsDrawingTool extends BorderPane {

  // ── Drawing modes ──
  public enum Mode { SELECT, RECTANGLE, POINT_NAIL }

  // ── Bound entry ──
  public static class BoundEntry {
    private String id;
    private String label;
    private double x, y, w, h; // normalized 0..1
    private List<BoundsPointCodec.Point> localPoints; // normalized relative to this bound

    public BoundEntry(String id, String label, double x, double y, double w, double h) {
      this(id, label, x, y, w, h, null);
    }

    public BoundEntry(String id, String label, double x, double y, double w, double h, List<BoundsPointCodec.Point> localPoints) {
      this.id = id;
      this.label = label;
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
      setLocalPoints(localPoints);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public double getX() { return x; }
    public void setX(double x) { this.x = x; }
    public double getY() { return y; }
    public void setY(double y) { this.y = y; }
    public double getW() { return w; }
    public void setW(double w) { this.w = w; }
    public double getH() { return h; }
    public void setH(double h) { this.h = h; }
    public List<BoundsPointCodec.Point> getLocalPoints() {
      return localPoints == null ? List.of() : List.copyOf(localPoints);
    }
    public void setLocalPoints(List<BoundsPointCodec.Point> localPoints) {
      if (localPoints == null || localPoints.isEmpty()) {
        this.localPoints = List.of();
      } else {
        this.localPoints = List.copyOf(localPoints);
      }
    }
    public boolean hasCustomPolygon() {
      return localPoints != null && localPoints.size() >= 3;
    }

    @Override
    public String toString() {
      String display = (label != null && !label.isBlank()) ? label : id;
      String suffix = hasCustomPolygon() ? " poly(" + localPoints.size() + ")" : "";
      return display + "  [" + fmt(x) + ", " + fmt(y) + ", " + fmt(w) + ", " + fmt(h) + "]" + suffix;
    }

    private static String fmt(double v) {
      return String.format(Locale.ROOT, "%.3f", v);
    }
  }

  // ── Constants ──
  private static final double CANVAS_PADDING = 8.0;
  private static final double HANDLE_SIZE = 8.0;
  private static final double SNAP_THRESHOLD = 6.0;
  private static final double ZOOM_MIN = 0.5;
  private static final double ZOOM_MAX = 6.0;
  private static final double ZOOM_STEP = 1.2;
  private static final Color[] BOUND_COLORS = {
      Color.rgb(110, 170, 255, 0.85),
      Color.rgb(255, 170, 110, 0.85),
      Color.rgb(110, 255, 170, 0.85),
      Color.rgb(255, 110, 200, 0.85),
      Color.rgb(200, 200, 110, 0.85),
      Color.rgb(150, 110, 255, 0.85),
  };
  private static final Color POINT_NAIL_COLOR = Color.rgb(255, 80, 80, 0.95);
  private static final Color DRAWING_RECT_COLOR = Color.rgb(110, 255, 170, 0.7);
  private static final Color SELECTED_HIGHLIGHT = Color.rgb(255, 255, 100, 0.45);

  // ── UI components ──
  private final Canvas canvas = new Canvas(600, 400);
  private final StackPane canvasHost = new StackPane(canvas);
  private final ScrollPane canvasScroll = new ScrollPane(canvasHost);
  private final ListView<BoundEntry> boundsList = new ListView<>();
  private final ObservableList<BoundEntry> bounds = FXCollections.observableArrayList();
  private final TextField idField = new TextField();
  private final TextField labelField = new TextField();
  private final TextField xField = new TextField();
  private final TextField yField = new TextField();
  private final TextField wField = new TextField();
  private final TextField hField = new TextField();
  private final Label coordsLabel = new Label("—");
  private final Label redrawHintLabel = new Label("Redraw target: none");
  private final Label zoomLabel = new Label("100%");
  private final ToggleGroup modeGroup = new ToggleGroup();
  private final ToggleButton selectBtn = new ToggleButton();
  private final ToggleButton rectBtn = new ToggleButton();
  private final ToggleButton pointBtn = new ToggleButton();

  // ── State ──
  private Mode mode = Mode.SELECT;
  private Image backgroundImage;
  private Double workspaceAspectOverride;
  private int selectedIndex = -1;
  private Integer redrawTargetIndex;
  private double zoom = 1.0;
  private Consumer<List<BoundEntry>> onBoundsChanged;
  private Runnable onSaveRequested;
  private int nextId = 1;

  // Drag state (SELECT mode)
  private DragAction dragAction = DragAction.NONE;
  private double dragStartMX, dragStartMY;
  private double dragOrigX, dragOrigY, dragOrigW, dragOrigH;
  private int dragCorner = -1; // 0=TL, 1=TR, 2=BL, 3=BR

  // Rectangle-draw state
  private boolean drawing = false;
  private double drawStartX, drawStartY;
  private double drawEndX, drawEndY;

  // Point-nail state
  private final List<double[]> nailPoints = new ArrayList<>();
  private boolean suppressFieldEvents = false;

  private enum DragAction { NONE, MOVE, RESIZE }

  public BoundsDrawingTool() {
    setPadding(new Insets(6));
    getStyleClass().add("bounds-drawing-tool");

    // Mode toolbar
    selectBtn.setToggleGroup(modeGroup);
    rectBtn.setToggleGroup(modeGroup);
    pointBtn.setToggleGroup(modeGroup);
    iconToggleButton(selectBtn, CssIcon.check("#b0b8c8"), "Select, move, and resize existing bounds");
    iconToggleButton(rectBtn, CssIcon.expand("#b0b8c8"), "Click-drag to draw a new rectangular bound");
    iconToggleButton(pointBtn, CssIcon.link("#b0b8c8"), "Click to place corner points, then generate polygon/rectangle bounds");
    selectBtn.setSelected(true);

    modeGroup.selectedToggleProperty().addListener((o, ov, nv) -> {
      if (nv == selectBtn) mode = Mode.SELECT;
      else if (nv == rectBtn) mode = Mode.RECTANGLE;
      else if (nv == pointBtn) mode = Mode.POINT_NAIL;
      else { selectBtn.setSelected(true); mode = Mode.SELECT; }
      nailPoints.clear();
      drawing = false;
      redraw();
    });

    Button makeRectFromNails = iconButton(CssIcon.grid("#8cd48c"), "Create rectangular bound from points");
    makeRectFromNails.setOnAction(e -> generateRectFromNails());
    Button makePolyFromNails = iconButton(CssIcon.check("#8cd48c"), "Create polygon bound from points");
    makePolyFromNails.setOnAction(e -> generatePolygonFromNails());

    Button deleteBtn = iconButton(CssIcon.minus("#e07070"), "Remove the selected bound");
    deleteBtn.setOnAction(e -> deleteSelected());
    Button duplicateBtn = iconButton(CssIcon.copy("#9cc7ff"), "Duplicate selected bound");
    duplicateBtn.setOnAction(e -> duplicateSelected());

    Button clearAllBtn = iconButton(CssIcon.clearX("#e07070"), "Clear all bounds");
    clearAllBtn.setOnAction(e -> {
      bounds.clear();
      selectedIndex = -1;
      boundsList.getSelectionModel().clearSelection();
      clearRedrawTarget();
      nailPoints.clear();
      drawing = false;
      populateFields();
      refreshListDisplay();
      redraw();
      emitChange();
    });

    Button zoomOutBtn = iconButton(CssIcon.minus("#9cc7ff"), "Zoom out");
    zoomOutBtn.setOnAction(e -> adjustZoom(1.0 / ZOOM_STEP));
    Button zoomInBtn = iconButton(CssIcon.plus("#9cc7ff"), "Zoom in");
    zoomInBtn.setOnAction(e -> adjustZoom(ZOOM_STEP));
    Button zoomResetBtn = iconButton(CssIcon.expand("#9cc7ff"), "Reset zoom to fit");
    zoomResetBtn.setOnAction(e -> setZoom(1.0));
    zoomLabel.setMinWidth(56);
    zoomLabel.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px; -fx-text-fill: #a0b0c8;");
    updateZoomLabel();

    Button saveBtn = iconButton(CssIcon.save("#8cd48c"), "Save bounds");
    saveBtn.setOnAction(e -> triggerSaveRequested());

    HBox toolbar = new HBox(6, selectBtn, rectBtn, pointBtn,
        zoomOutBtn, zoomInBtn, zoomResetBtn, zoomLabel,
        createSpacer(), saveBtn, makeRectFromNails, makePolyFromNails, duplicateBtn, deleteBtn, clearAllBtn);
    toolbar.setAlignment(Pos.CENTER_LEFT);
    toolbar.setPadding(new Insets(0, 0, 6, 0));

    // Canvas area
    canvas.setManaged(false);
    StackPane.setAlignment(canvas, Pos.TOP_LEFT);
    canvasHost.getStyleClass().add("layout-studio-preview-host");
    canvasHost.setPadding(new Insets(CANVAS_PADDING));
    canvasScroll.setPannable(true);
    canvasScroll.setFitToWidth(false);
    canvasScroll.setFitToHeight(false);
    canvasScroll.viewportBoundsProperty().addListener((o, ov, nv) -> resizeCanvas());
    canvasScroll.addEventFilter(ScrollEvent.SCROLL, this::onCanvasScroll);
    canvas.widthProperty().addListener((o, ov, nv) -> redraw());
    canvas.heightProperty().addListener((o, ov, nv) -> redraw());
    canvas.setFocusTraversable(true);

    installCanvasInteractions();
    addEventHandler(KeyEvent.KEY_PRESSED, this::onKeyPressed);

    // Sidebar: bounds list + property fields
    boundsList.setItems(bounds);
    boundsList.setPrefHeight(200);
    boundsList.getSelectionModel().selectedIndexProperty().addListener((o, ov, nv) -> {
      int idx = nv == null ? -1 : nv.intValue();
      if (idx != selectedIndex) {
        selectedIndex = idx;
        populateFields();
        redraw();
      }
    });
    installBoundsListContextMenu();

    idField.setPromptText("id");
    idField.textProperty().addListener((o, ov, nv) -> {
      if (selectedIndex >= 0 && selectedIndex < bounds.size()) {
        bounds.get(selectedIndex).setId(nv);
        updateRedrawHint();
        refreshListDisplay();
        emitChange();
      }
    });
    labelField.setPromptText("label");
    labelField.textProperty().addListener((o, ov, nv) -> {
      if (selectedIndex >= 0 && selectedIndex < bounds.size()) {
        bounds.get(selectedIndex).setLabel(nv);
        updateRedrawHint();
        refreshListDisplay();
        emitChange();
      }
    });
    xField.setPromptText("x");
    yField.setPromptText("y");
    wField.setPromptText("w");
    hField.setPromptText("h");
    xField.textProperty().addListener((o, ov, nv) -> applyNumericFieldEdits());
    yField.textProperty().addListener((o, ov, nv) -> applyNumericFieldEdits());
    wField.textProperty().addListener((o, ov, nv) -> applyNumericFieldEdits());
    hField.textProperty().addListener((o, ov, nv) -> applyNumericFieldEdits());

    coordsLabel.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px; -fx-text-fill: #a0b0c8;");
    redrawHintLabel.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 11px; -fx-text-fill: #f0c98a;");

    HBox xyRow = new HBox(4, new Label("x"), xField, new Label("y"), yField);
    HBox whRow = new HBox(4, new Label("w"), wField, new Label("h"), hField);
    HBox.setHgrow(xField, Priority.ALWAYS);
    HBox.setHgrow(yField, Priority.ALWAYS);
    HBox.setHgrow(wField, Priority.ALWAYS);
    HBox.setHgrow(hField, Priority.ALWAYS);

    VBox sideFields = new VBox(4,
        new Label("ID:"), idField,
        new Label("Label:"), labelField,
        xyRow,
        whRow,
        coordsLabel,
        redrawHintLabel
    );
    sideFields.setPadding(new Insets(6));

    VBox sidebar = new VBox(6,
        new Label("Bounds"),
        boundsList,
        sideFields
    );
    sidebar.setPrefWidth(240);
    sidebar.setMinWidth(180);

    ScrollPane sideScroll = new ScrollPane(sidebar);
    sideScroll.setFitToWidth(true);
    sideScroll.setPrefWidth(250);
    sideScroll.getStyleClass().add("layout-studio-controls-pane");

    setTop(toolbar);
    setCenter(canvasScroll);
    setRight(sideScroll);
    updateRedrawHint();
    resizeCanvas();
  }

  // ── Public API ──

  public void setBackgroundImage(Image image) {
    this.backgroundImage = image;
    resizeCanvas();
    redraw();
  }

  public void setBackgroundImage(File file) {
    if (file != null && file.exists()) {
      try {
        this.backgroundImage = new Image(file.toURI().toString(), false);
      } catch (Exception e) {
        this.backgroundImage = null;
      }
    } else {
      this.backgroundImage = null;
    }
    resizeCanvas();
    redraw();
  }

  public void setOnBoundsChanged(Consumer<List<BoundEntry>> callback) {
    this.onBoundsChanged = callback;
  }

  public void setOnSaveRequested(Runnable callback) {
    this.onSaveRequested = callback;
  }

  /**
   * Optional normalized workspace aspect ratio (width / height).
   * When set, the canvas keeps this aspect regardless of host size or asset ratio.
   */
  public void setWorkspaceAspect(Double aspect) {
    if (aspect == null || !Double.isFinite(aspect) || aspect <= 0.0) {
      workspaceAspectOverride = null;
    } else {
      workspaceAspectOverride = aspect;
    }
    resizeCanvas();
    redraw();
  }

  public List<BoundEntry> getBounds() {
    return new ArrayList<>(bounds);
  }

  public void setBounds(List<BoundEntry> entries) {
    bounds.clear();
    if (entries != null) bounds.addAll(entries);
    clearRedrawTarget();
    selectedIndex = bounds.isEmpty() ? -1 : 0;
    boundsList.getSelectionModel().select(selectedIndex);
    populateFields();
    redraw();
  }

  public void addBound(BoundEntry entry) {
    if (entry == null) return;
    bounds.add(entry);
    selectedIndex = bounds.size() - 1;
    boundsList.getSelectionModel().select(selectedIndex);
    populateFields();
    redraw();
    emitChange();
  }

  public Mode getMode() { return mode; }

  // ── Canvas interactions ──

  private void installCanvasInteractions() {
    canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
    canvas.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
    canvas.addEventHandler(MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
    canvas.addEventHandler(MouseEvent.MOUSE_MOVED, this::onMouseMoved);
  }

  private void onMousePressed(MouseEvent e) {
    canvas.requestFocus();
    double mx = e.getX();
    double my = e.getY();
    double cw = canvasW();
    double ch = canvasH();

    if (mode == Mode.SELECT) {
      // Try to pick a resize handle first
      for (int i = bounds.size() - 1; i >= 0; i--) {
        BoundEntry b = bounds.get(i);
        int corner = hitCornerHandle(b, mx, my, cw, ch);
        if (corner >= 0) {
          selectedIndex = i;
          boundsList.getSelectionModel().select(i);
          dragAction = DragAction.RESIZE;
          dragCorner = corner;
          dragStartMX = mx;
          dragStartMY = my;
          dragOrigX = b.getX();
          dragOrigY = b.getY();
          dragOrigW = b.getW();
          dragOrigH = b.getH();
          populateFields();
          redraw();
          e.consume();
          return;
        }
      }
      // Then try picking a bound body
      for (int i = bounds.size() - 1; i >= 0; i--) {
        BoundEntry b = bounds.get(i);
        if (hitBound(b, mx, my, cw, ch)) {
          selectedIndex = i;
          boundsList.getSelectionModel().select(i);
          dragAction = DragAction.MOVE;
          dragStartMX = mx;
          dragStartMY = my;
          dragOrigX = b.getX();
          dragOrigY = b.getY();
          populateFields();
          redraw();
          e.consume();
          return;
        }
      }
      // Click on empty space deselects
      selectedIndex = -1;
      boundsList.getSelectionModel().clearSelection();
      dragAction = DragAction.NONE;
      populateFields();
      redraw();

    } else if (mode == Mode.RECTANGLE) {
      drawing = true;
      drawStartX = mx;
      drawStartY = my;
      drawEndX = mx;
      drawEndY = my;

    } else if (mode == Mode.POINT_NAIL) {
      if (e.getButton() == MouseButton.PRIMARY) {
        if (nailPoints.size() >= 3 && isNearFirstNail(mx, my, cw, ch)) {
          generatePolygonFromNails();
          e.consume();
          return;
        }
        nailPoints.add(new double[]{mx / cw, my / ch});
        redraw();
      } else if (e.getButton() == MouseButton.SECONDARY) {
        // Right-click removes last point
        if (!nailPoints.isEmpty()) {
          nailPoints.remove(nailPoints.size() - 1);
          redraw();
        }
      }
    }
  }

  private void onMouseDragged(MouseEvent e) {
    double mx = e.getX();
    double my = e.getY();
    double cw = canvasW();
    double ch = canvasH();

    if (mode == Mode.SELECT && dragAction != DragAction.NONE && selectedIndex >= 0 && selectedIndex < bounds.size()) {
      BoundEntry b = bounds.get(selectedIndex);
      double dx = (mx - dragStartMX) / cw;
      double dy = (my - dragStartMY) / ch;

      if (dragAction == DragAction.MOVE) {
        b.setX(clamp01(dragOrigX + dx));
        b.setY(clamp01(dragOrigY + dy));
      } else if (dragAction == DragAction.RESIZE) {
        applyResize(b, dx, dy);
      }
      populateFields();
      refreshListDisplay();
      redraw();
      e.consume();

    } else if (mode == Mode.RECTANGLE && drawing) {
      drawEndX = mx;
      drawEndY = my;
      redraw();
    }
  }

  private void onMouseReleased(MouseEvent e) {
    if (mode == Mode.SELECT && dragAction != DragAction.NONE) {
      dragAction = DragAction.NONE;
      emitChange();
    } else if (mode == Mode.RECTANGLE && drawing) {
      drawing = false;
      double cw = canvasW();
      double ch = canvasH();
      double nx = Math.min(drawStartX, drawEndX) / cw;
      double ny = Math.min(drawStartY, drawEndY) / ch;
      double nw = Math.abs(drawEndX - drawStartX) / cw;
      double nh = Math.abs(drawEndY - drawStartY) / ch;
      if (nw > 0.01 && nh > 0.01) {
        applyGeneratedBound(clamp01(nx), clamp01(ny), clamp(nw, 0.01, 1.0), clamp(nh, 0.01, 1.0), null);
      }
      redraw();
    }
  }

  private void onMouseMoved(MouseEvent e) {
    double mx = e.getX();
    double my = e.getY();
    double cw = canvasW();
    double ch = canvasH();

    // Update coordinate display
    coordsLabel.setText(String.format(Locale.ROOT, "x: %.3f  y: %.3f", mx / cw, my / ch));

    if (mode == Mode.SELECT) {
      // Update cursor based on hover
      for (int i = bounds.size() - 1; i >= 0; i--) {
        BoundEntry b = bounds.get(i);
        int corner = hitCornerHandle(b, mx, my, cw, ch);
        if (corner >= 0) {
          canvas.setCursor(corner == 0 || corner == 3 ? Cursor.NW_RESIZE : Cursor.NE_RESIZE);
          return;
        }
        if (hitBound(b, mx, my, cw, ch)) {
          canvas.setCursor(Cursor.MOVE);
          return;
        }
      }
      canvas.setCursor(Cursor.DEFAULT);
    } else if (mode == Mode.RECTANGLE) {
      canvas.setCursor(Cursor.CROSSHAIR);
    } else if (mode == Mode.POINT_NAIL) {
      canvas.setCursor(Cursor.CROSSHAIR);
    }
  }

  private void onKeyPressed(KeyEvent e) {
    if (e == null) return;
    KeyCode code = e.getCode();
    if (code == null) return;
    if (code == KeyCode.DELETE || code == KeyCode.BACK_SPACE) {
      deleteSelected();
      e.consume();
      return;
    }
    if (e.isControlDown() && code == KeyCode.D) {
      duplicateSelected();
      e.consume();
      return;
    }
    if (e.isControlDown() || e.isMetaDown()) {
      if (code == KeyCode.S) {
        triggerSaveRequested();
        e.consume();
        return;
      }
      if (code == KeyCode.EQUALS || code == KeyCode.PLUS) {
        adjustZoom(ZOOM_STEP);
        e.consume();
        return;
      }
      if (code == KeyCode.MINUS) {
        adjustZoom(1.0 / ZOOM_STEP);
        e.consume();
        return;
      }
      if (code == KeyCode.DIGIT0 || code == KeyCode.NUMPAD0) {
        setZoom(1.0);
        e.consume();
        return;
      }
    }
    if (!(code == KeyCode.LEFT || code == KeyCode.RIGHT || code == KeyCode.UP || code == KeyCode.DOWN)) return;
    if (selectedIndex < 0 || selectedIndex >= bounds.size()) return;
    BoundEntry b = bounds.get(selectedIndex);
    double step = e.isShiftDown() ? 0.01 : 0.0025;
    boolean resize = e.isAltDown();
    if (resize) {
      switch (code) {
        case LEFT -> b.setW(clamp(b.getW() - step, 0.01, 1.0));
        case RIGHT -> b.setW(clamp(b.getW() + step, 0.01, 1.0));
        case UP -> b.setH(clamp(b.getH() - step, 0.01, 1.0));
        case DOWN -> b.setH(clamp(b.getH() + step, 0.01, 1.0));
        default -> { return; }
      }
    } else {
      switch (code) {
        case LEFT -> b.setX(clamp01(b.getX() - step));
        case RIGHT -> b.setX(clamp01(b.getX() + step));
        case UP -> b.setY(clamp01(b.getY() - step));
        case DOWN -> b.setY(clamp01(b.getY() + step));
        default -> { return; }
      }
    }
    populateFields();
    refreshListDisplay();
    redraw();
    emitChange();
    e.consume();
  }

  // ── Hit testing ──

  private boolean hitBound(BoundEntry b, double mx, double my, double cw, double ch) {
    if (b != null && b.hasCustomPolygon()) {
      if (BoundsPointCodec.containsInRect(b.getLocalPoints(), b.getX() * cw, b.getY() * ch, b.getW() * cw, b.getH() * ch, mx, my)) {
        return true;
      }
    }
    double bx = b.getX() * cw;
    double by = b.getY() * ch;
    double bw = b.getW() * cw;
    double bh = b.getH() * ch;
    return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
  }

  /** Returns corner index 0-3 or -1 if no handle hit. */
  private int hitCornerHandle(BoundEntry b, double mx, double my, double cw, double ch) {
    double bx = b.getX() * cw;
    double by = b.getY() * ch;
    double bw = b.getW() * cw;
    double bh = b.getH() * ch;
    double hs = HANDLE_SIZE;

    // 0=TL, 1=TR, 2=BL, 3=BR
    double[][] corners = {
        {bx, by}, {bx + bw, by}, {bx, by + bh}, {bx + bw, by + bh}
    };
    for (int i = 0; i < 4; i++) {
      if (Math.abs(mx - corners[i][0]) <= hs && Math.abs(my - corners[i][1]) <= hs) {
        return i;
      }
    }
    return -1;
  }

  private void applyResize(BoundEntry b, double dxNorm, double dyNorm) {
    double x = dragOrigX;
    double y = dragOrigY;
    double w = dragOrigW;
    double h = dragOrigH;

    switch (dragCorner) {
      case 0: // TL
        x = clamp01(dragOrigX + dxNorm);
        y = clamp01(dragOrigY + dyNorm);
        w = clamp(dragOrigW - dxNorm, 0.01, 1.0);
        h = clamp(dragOrigH - dyNorm, 0.01, 1.0);
        break;
      case 1: // TR
        y = clamp01(dragOrigY + dyNorm);
        w = clamp(dragOrigW + dxNorm, 0.01, 1.0);
        h = clamp(dragOrigH - dyNorm, 0.01, 1.0);
        break;
      case 2: // BL
        x = clamp01(dragOrigX + dxNorm);
        w = clamp(dragOrigW - dxNorm, 0.01, 1.0);
        h = clamp(dragOrigH + dyNorm, 0.01, 1.0);
        break;
      case 3: // BR
        w = clamp(dragOrigW + dxNorm, 0.01, 1.0);
        h = clamp(dragOrigH + dyNorm, 0.01, 1.0);
        break;
    }
    b.setX(x);
    b.setY(y);
    b.setW(w);
    b.setH(h);
  }

  // ── Point-nail → bounds generation ──

  private void generateRectFromNails() {
    if (nailPoints.size() < 2) return;
    double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
    for (double[] pt : nailPoints) {
      if (pt[0] < minX) minX = pt[0];
      if (pt[1] < minY) minY = pt[1];
      if (pt[0] > maxX) maxX = pt[0];
      if (pt[1] > maxY) maxY = pt[1];
    }
    double w = maxX - minX;
    double h = maxY - minY;
    if (w > 0.005 && h > 0.005) {
      applyGeneratedBound(clamp01(minX), clamp01(minY), clamp(w, 0.01, 1.0), clamp(h, 0.01, 1.0), null);
    }
    nailPoints.clear();
    redraw();
  }

  private void generatePolygonFromNails() {
    if (nailPoints.size() < 3) return;
    double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
    for (double[] pt : nailPoints) {
      if (pt[0] < minX) minX = pt[0];
      if (pt[1] < minY) minY = pt[1];
      if (pt[0] > maxX) maxX = pt[0];
      if (pt[1] > maxY) maxY = pt[1];
    }
    double w = maxX - minX;
    double h = maxY - minY;
    if (w <= 0.005 || h <= 0.005) {
      nailPoints.clear();
      redraw();
      return;
    }
    List<BoundsPointCodec.Point> local = new ArrayList<>();
    for (double[] pt : nailPoints) {
      double lx = clamp01((pt[0] - minX) / w);
      double ly = clamp01((pt[1] - minY) / h);
      local.add(new BoundsPointCodec.Point(lx, ly));
    }
    applyGeneratedBound(clamp01(minX), clamp01(minY), clamp(w, 0.01, 1.0), clamp(h, 0.01, 1.0), local);
    nailPoints.clear();
    redraw();
  }

  private boolean isNearFirstNail(double mx, double my, double cw, double ch) {
    if (nailPoints.isEmpty()) return false;
    double[] first = nailPoints.get(0);
    double fx = first[0] * cw;
    double fy = first[1] * ch;
    double dx = mx - fx;
    double dy = my - fy;
    return (dx * dx + dy * dy) <= (SNAP_THRESHOLD * SNAP_THRESHOLD);
  }

  // ── Drawing ──

  private void redraw() {
    double cw = canvasW();
    double ch = canvasH();
    GraphicsContext g = canvas.getGraphicsContext2D();

    // Background
    g.setFill(LayoutStudioPalette.CANVAS_BACKGROUND);
    g.fillRect(0, 0, cw, ch);

    if (backgroundImage != null && backgroundImage.getWidth() > 1 && backgroundImage.getHeight() > 1) {
      // Stretch to full canvas so normalized bounds map directly to visible pixels.
      g.drawImage(backgroundImage, 0, 0, cw, ch);
      // Slight dim overlay so bounds are readable
      g.setFill(Color.rgb(0, 0, 0, 0.25));
      g.fillRect(0, 0, cw, ch);
    }

    // Grid
    g.setStroke(LayoutStudioPalette.GRID_LINE);
    g.setLineWidth(1);
    for (int i = 1; i < 6; i++) {
      double yy = (ch / 6.0) * i;
      g.strokeLine(0, yy, cw, yy);
    }
    for (int i = 1; i < 8; i++) {
      double xx = (cw / 8.0) * i;
      g.strokeLine(xx, 0, xx, ch);
    }

    // Draw existing bounds
    for (int i = 0; i < bounds.size(); i++) {
      BoundEntry b = bounds.get(i);
      boolean sel = i == selectedIndex;
      Color color = BOUND_COLORS[i % BOUND_COLORS.length];
      drawBound(g, b, cw, ch, color, sel, i);
    }

    // Draw active rectangle (RECTANGLE mode)
    if (mode == Mode.RECTANGLE && drawing) {
      double rx = Math.min(drawStartX, drawEndX);
      double ry = Math.min(drawStartY, drawEndY);
      double rw = Math.abs(drawEndX - drawStartX);
      double rh = Math.abs(drawEndY - drawStartY);
      g.setStroke(DRAWING_RECT_COLOR);
      g.setLineWidth(2);
      g.setLineDashes(6, 4);
      g.strokeRect(rx, ry, rw, rh);
      g.setLineDashes(0);
      g.setFill(Color.rgb(110, 255, 170, 0.12));
      g.fillRect(rx, ry, rw, rh);

      // Dimension label
      drawDimLabel(g, rx + rw / 2, ry - 6,
          String.format(Locale.ROOT, "%.3f × %.3f", rw / cw, rh / ch));
    }

    // Draw nail points (POINT_NAIL mode)
    if (mode == Mode.POINT_NAIL && !nailPoints.isEmpty()) {
      g.setFill(POINT_NAIL_COLOR);
      g.setStroke(Color.WHITE);
      g.setLineWidth(1.5);
      g.setLineDashes(0);

      // Connect nails for visual polygon path.
      if (nailPoints.size() >= 2) {
        g.setStroke(Color.rgb(255, 120, 120, 0.85));
        g.setLineWidth(1.2);
        for (int i = 0; i < nailPoints.size() - 1; i++) {
          double[] a = nailPoints.get(i);
          double[] b = nailPoints.get(i + 1);
          g.strokeLine(a[0] * cw, a[1] * ch, b[0] * cw, b[1] * ch);
        }
        if (nailPoints.size() >= 3) {
          double[] first = nailPoints.get(0);
          double[] last = nailPoints.get(nailPoints.size() - 1);
          g.setLineDashes(4, 3);
          g.strokeLine(last[0] * cw, last[1] * ch, first[0] * cw, first[1] * ch);
          g.setLineDashes(0);
        }
      }

      for (double[] pt : nailPoints) {
        double px = pt[0] * cw;
        double py = pt[1] * ch;
        g.fillOval(px - 5, py - 5, 10, 10);
        g.strokeOval(px - 5, py - 5, 10, 10);
      }

      // Preview bounding box of placed nails
      if (nailPoints.size() >= 2) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double[] pt : nailPoints) {
          if (pt[0] < minX) minX = pt[0];
          if (pt[1] < minY) minY = pt[1];
          if (pt[0] > maxX) maxX = pt[0];
          if (pt[1] > maxY) maxY = pt[1];
        }
        double bx = minX * cw;
        double by = minY * ch;
        double bw = (maxX - minX) * cw;
        double bh = (maxY - minY) * ch;
        g.setStroke(POINT_NAIL_COLOR);
        g.setLineWidth(1.5);
        g.setLineDashes(4, 3);
        g.strokeRect(bx, by, bw, bh);
        g.setLineDashes(0);
      }

      // Nail count label
      String helper = nailPoints.size() >= 3
          ? nailPoints.size() + " point(s) • click first point to close polygon"
          : nailPoints.size() + " point(s) placed";
      drawTag(g, 8, ch - 8, helper);
    }

    // Mode indicator tag
    String modeText = switch (mode) {
      case SELECT -> "Select";
      case RECTANGLE -> "Draw Rectangle";
      case POINT_NAIL -> "Point-Nail";
    };
    drawTag(g, 8, 18, modeText);
    if (isRedrawTargetActive()) {
      BoundEntry target = bounds.get(redrawTargetIndex);
      drawTag(g, 8, 38, "Redraw: " + displayLabel(target));
    }
  }

  private void drawBound(GraphicsContext g, BoundEntry b, double cw, double ch, Color color, boolean selected, int index) {
    double bx = b.getX() * cw;
    double by = b.getY() * ch;
    double bw = b.getW() * cw;
    double bh = b.getH() * ch;

    // Fill
    if (selected) {
      g.setFill(SELECTED_HIGHLIGHT);
      g.fillRect(bx, by, bw, bh);
    } else {
      g.setFill(color.deriveColor(0, 1, 1, 0.12));
      g.fillRect(bx, by, bw, bh);
    }

    // Border
    g.setStroke(selected ? Color.WHITE : color);
    g.setLineWidth(selected ? 2.5 : 1.5);
    g.strokeRect(bx, by, bw, bh);

    if (b.hasCustomPolygon()) {
      List<BoundsPointCodec.Point> pts = b.getLocalPoints();
      if (pts.size() >= 3) {
        double[] xs = new double[pts.size()];
        double[] ys = new double[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
          BoundsPointCodec.Point p = pts.get(i);
          xs[i] = bx + bw * p.x();
          ys[i] = by + bh * p.y();
        }
        g.setStroke(selected ? Color.rgb(255, 240, 140, 0.95) : Color.rgb(255, 140, 140, 0.9));
        g.setLineWidth(selected ? 2.0 : 1.4);
        g.strokePolygon(xs, ys, pts.size());
        g.setFill(Color.rgb(255, 120, 120, selected ? 0.6 : 0.45));
        for (int i = 0; i < pts.size(); i++) {
          g.fillOval(xs[i] - 3, ys[i] - 3, 6, 6);
        }
      }
    }

    // Corner handles (in select mode for selected bound)
    if (selected && mode == Mode.SELECT) {
      double hs = HANDLE_SIZE;
      g.setFill(Color.WHITE);
      g.fillRect(bx - hs / 2, by - hs / 2, hs, hs);
      g.fillRect(bx + bw - hs / 2, by - hs / 2, hs, hs);
      g.fillRect(bx - hs / 2, by + bh - hs / 2, hs, hs);
      g.fillRect(bx + bw - hs / 2, by + bh - hs / 2, hs, hs);
      g.setStroke(color);
      g.setLineWidth(1);
      g.strokeRect(bx - hs / 2, by - hs / 2, hs, hs);
      g.strokeRect(bx + bw - hs / 2, by - hs / 2, hs, hs);
      g.strokeRect(bx - hs / 2, by + bh - hs / 2, hs, hs);
      g.strokeRect(bx + bw - hs / 2, by + bh - hs / 2, hs, hs);
    }

    // ID label
    String display = (b.getLabel() != null && !b.getLabel().isBlank()) ? b.getLabel() : b.getId();
    drawTag(g, bx + 4, by + 14, display);
  }

  private void drawTag(GraphicsContext g, double x, double y, String text) {
    double w = Math.max(44, text.length() * 7.0 + 12);
    g.setFill(LayoutStudioPalette.TAG_BG);
    g.fillRoundRect(x, y - 12, w, 16, 6, 6);
    g.setStroke(LayoutStudioPalette.TAG_BORDER);
    g.setLineWidth(1);
    g.strokeRoundRect(x, y - 12, w, 16, 6, 6);
    g.setFill(LayoutStudioPalette.TAG_TEXT);
    g.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, 11));
    g.fillText(text, x + 6, y);
  }

  private void drawDimLabel(GraphicsContext g, double cx, double y, String text) {
    double tw = text.length() * 6.5 + 8;
    g.setFill(Color.rgb(12, 16, 25, 0.85));
    g.fillRoundRect(cx - tw / 2, y - 10, tw, 14, 4, 4);
    g.setFill(Color.rgb(200, 220, 255, 0.9));
    g.setFont(Font.font(Font.getDefault().getFamily(), 10));
    g.fillText(text, cx - tw / 2 + 4, y);
  }

  // ── Sidebar helpers ──

  private void applyNumericFieldEdits() {
    if (suppressFieldEvents) return;
    if (selectedIndex < 0 || selectedIndex >= bounds.size()) return;
    BoundEntry b = bounds.get(selectedIndex);
    Double nx = parseDouble(xField.getText());
    Double ny = parseDouble(yField.getText());
    Double nw = parseDouble(wField.getText());
    Double nh = parseDouble(hField.getText());
    if (nx == null || ny == null || nw == null || nh == null) return;
    b.setX(clamp01(nx));
    b.setY(clamp01(ny));
    b.setW(clamp(nw, 0.01, 1.0));
    b.setH(clamp(nh, 0.01, 1.0));
    refreshListDisplay();
    redraw();
    emitChange();
  }

  private void populateFields() {
    suppressFieldEvents = true;
    if (selectedIndex >= 0 && selectedIndex < bounds.size()) {
      BoundEntry b = bounds.get(selectedIndex);
      idField.setText(b.getId());
      labelField.setText(b.getLabel() != null ? b.getLabel() : "");
      xField.setText(formatDouble(b.getX()));
      yField.setText(formatDouble(b.getY()));
      wField.setText(formatDouble(b.getW()));
      hField.setText(formatDouble(b.getH()));
      coordsLabel.setText(String.format(Locale.ROOT,
          "x: %.4f  y: %.4f  w: %.4f  h: %.4f",
          b.getX(), b.getY(), b.getW(), b.getH()));
    } else {
      idField.setText("");
      labelField.setText("");
      xField.setText("");
      yField.setText("");
      wField.setText("");
      hField.setText("");
      coordsLabel.setText("—");
    }
    suppressFieldEvents = false;
  }

  private void refreshListDisplay() {
    // Force list cell refresh
    boundsList.refresh();
  }

  private void deleteSelected() {
    if (selectedIndex >= 0 && selectedIndex < bounds.size()) {
      int removedIndex = selectedIndex;
      bounds.remove(selectedIndex);
      onBoundRemovedAt(removedIndex);
      selectedIndex = Math.min(selectedIndex, bounds.size() - 1);
      if (selectedIndex >= 0) boundsList.getSelectionModel().select(selectedIndex);
      else boundsList.getSelectionModel().clearSelection();
      populateFields();
      redraw();
      emitChange();
    }
  }

  private void duplicateSelected() {
    if (selectedIndex < 0 || selectedIndex >= bounds.size()) return;
    BoundEntry source = bounds.get(selectedIndex);
    if (source == null) return;
    String baseId = (source.getId() == null || source.getId().isBlank()) ? "button" : source.getId();
    String next = baseId + "_copy";
    int index = 2;
    while (containsId(next)) {
      next = baseId + "_copy_" + index++;
    }
    BoundEntry duplicate = new BoundEntry(
        next,
        source.getLabel(),
        clamp01(source.getX() + 0.015),
        clamp01(source.getY() + 0.015),
        source.getW(),
        source.getH(),
        source.getLocalPoints()
    );
    int insertIndex = selectedIndex + 1;
    bounds.add(insertIndex, duplicate);
    onBoundInsertedAt(insertIndex);
    selectedIndex = insertIndex;
    boundsList.getSelectionModel().select(selectedIndex);
    populateFields();
    refreshListDisplay();
    redraw();
    emitChange();
  }

  private void installBoundsListContextMenu() {
    MenuItem redrawRectItem = new MenuItem("Edit/Redraw (Rectangle)");
    redrawRectItem.setOnAction(e -> armRedrawTarget(selectedIndex, Mode.RECTANGLE));

    MenuItem redrawPointItem = new MenuItem("Edit/Redraw (Point-Nail)");
    redrawPointItem.setOnAction(e -> armRedrawTarget(selectedIndex, Mode.POINT_NAIL));

    MenuItem cancelRedrawItem = new MenuItem("Cancel Redraw Target");
    cancelRedrawItem.setOnAction(e -> {
      clearRedrawTarget();
      redraw();
    });

    ContextMenu contextMenu = new ContextMenu(
        redrawRectItem,
        redrawPointItem,
        new SeparatorMenuItem(),
        cancelRedrawItem
    );
    contextMenu.setOnShowing(e -> {
      boolean hasSelection = selectedIndex >= 0 && selectedIndex < bounds.size();
      redrawRectItem.setDisable(!hasSelection);
      redrawPointItem.setDisable(!hasSelection);
      cancelRedrawItem.setDisable(!isRedrawTargetActive());
    });
    boundsList.setContextMenu(contextMenu);

    boundsList.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
      if (e.getButton() != MouseButton.SECONDARY) return;
      Integer index = findListCellIndex(e.getPickResult().getIntersectedNode());
      if (index == null) return;
      boundsList.getSelectionModel().select(index);
    });
  }

  private Integer findListCellIndex(Node picked) {
    Node node = picked;
    while (node != null) {
      if (node instanceof ListCell<?> cell) {
        int index = cell.getIndex();
        if (index >= 0 && index < bounds.size()) return index;
        return null;
      }
      node = node.getParent();
    }
    return null;
  }

  private void armRedrawTarget(int index, Mode drawMode) {
    if (index < 0 || index >= bounds.size()) return;
    redrawTargetIndex = index;
    selectedIndex = index;
    boundsList.getSelectionModel().select(index);
    populateFields();
    updateRedrawHint();
    activateMode(drawMode);
    redraw();
  }

  private void clearRedrawTarget() {
    redrawTargetIndex = null;
    updateRedrawHint();
  }

  private boolean isRedrawTargetActive() {
    return redrawTargetIndex != null
        && redrawTargetIndex >= 0
        && redrawTargetIndex < bounds.size();
  }

  private void updateRedrawHint() {
    if (isRedrawTargetActive()) {
      BoundEntry target = bounds.get(redrawTargetIndex);
      redrawHintLabel.setText("Redraw target: " + displayLabel(target) + " (draw to replace)");
    } else {
      redrawHintLabel.setText("Redraw target: none");
    }
  }

  private String displayLabel(BoundEntry entry) {
    if (entry == null) return "unknown";
    if (entry.getLabel() != null && !entry.getLabel().isBlank()) return entry.getLabel();
    if (entry.getId() != null && !entry.getId().isBlank()) return entry.getId();
    return "unnamed";
  }

  private void applyGeneratedBound(double x, double y, double w, double h, List<BoundsPointCodec.Point> localPoints) {
    List<BoundsPointCodec.Point> local = (localPoints == null || localPoints.size() < 3)
        ? List.of()
        : List.copyOf(localPoints);
    if (isRedrawTargetActive()) {
      BoundEntry target = bounds.get(redrawTargetIndex);
      target.setX(clamp01(x));
      target.setY(clamp01(y));
      target.setW(clamp(w, 0.01, 1.0));
      target.setH(clamp(h, 0.01, 1.0));
      target.setLocalPoints(local);
      selectedIndex = redrawTargetIndex;
      boundsList.getSelectionModel().select(selectedIndex);
      clearRedrawTarget();
      activateMode(Mode.SELECT);
      populateFields();
      refreshListDisplay();
      redraw();
      emitChange();
      return;
    }
    addBound(new BoundEntry(nextGeneratedId(), null, clamp01(x), clamp01(y), clamp(w, 0.01, 1.0), clamp(h, 0.01, 1.0), local));
  }

  private String nextGeneratedId() {
    String id = "button_" + (nextId++);
    while (containsId(id)) {
      id = "button_" + (nextId++);
    }
    return id;
  }

  private void onBoundInsertedAt(int index) {
    if (redrawTargetIndex == null) return;
    if (index <= redrawTargetIndex) {
      redrawTargetIndex = redrawTargetIndex + 1;
      updateRedrawHint();
    }
  }

  private void onBoundRemovedAt(int index) {
    if (redrawTargetIndex == null) return;
    if (index == redrawTargetIndex) {
      redrawTargetIndex = null;
    } else if (index < redrawTargetIndex) {
      redrawTargetIndex = redrawTargetIndex - 1;
    }
    updateRedrawHint();
  }

  private void activateMode(Mode nextMode) {
    ToggleButton target = switch (nextMode) {
      case SELECT -> selectBtn;
      case RECTANGLE -> rectBtn;
      case POINT_NAIL -> pointBtn;
    };
    if (modeGroup.getSelectedToggle() != target) {
      target.setSelected(true);
      return;
    }
    mode = nextMode;
    nailPoints.clear();
    drawing = false;
    redraw();
  }

  private void onCanvasScroll(ScrollEvent event) {
    if (event == null) return;
    if (!(event.isControlDown() || event.isMetaDown())) return;
    double delta = event.getDeltaY();
    if (Math.abs(delta) < 0.0001) return;
    adjustZoom(delta > 0 ? ZOOM_STEP : 1.0 / ZOOM_STEP);
    event.consume();
  }

  private void adjustZoom(double factor) {
    if (!Double.isFinite(factor) || factor <= 0.0) return;
    setZoom(zoom * factor);
  }

  private void setZoom(double nextZoom) {
    double clamped = clamp(nextZoom, ZOOM_MIN, ZOOM_MAX);
    if (Math.abs(clamped - zoom) < 0.0001) return;
    zoom = clamped;
    updateZoomLabel();

    double oldH = canvasScroll.getHvalue();
    double oldV = canvasScroll.getVvalue();
    resizeCanvas();
    canvasScroll.setHvalue(clamp(oldH, 0.0, 1.0));
    canvasScroll.setVvalue(clamp(oldV, 0.0, 1.0));
  }

  private void updateZoomLabel() {
    int pct = (int) Math.round(zoom * 100.0);
    zoomLabel.setText("Zoom " + pct + "%");
  }

  private void triggerSaveRequested() {
    if (onSaveRequested != null) {
      onSaveRequested.run();
    }
  }

  // ── Canvas sizing ──

  private void resizeCanvas() {
    Bounds viewport = canvasScroll.getViewportBounds();
    double viewportW = viewport == null ? canvasHost.getWidth() : viewport.getWidth();
    double viewportH = viewport == null ? canvasHost.getHeight() : viewport.getHeight();
    if (viewportW <= 0 || viewportH <= 0) {
      viewportW = Math.max(viewportW, 600);
      viewportH = Math.max(viewportH, 400);
    }

    double availableW = Math.max(1, viewportW - CANVAS_PADDING * 2);
    double availableH = Math.max(1, viewportH - CANVAS_PADDING * 2);

    double aspect = availableW / Math.max(1.0, availableH);
    if (workspaceAspectOverride != null && Double.isFinite(workspaceAspectOverride) && workspaceAspectOverride > 0.0) {
      aspect = workspaceAspectOverride;
    } else if (backgroundImage != null && backgroundImage.getWidth() > 1 && backgroundImage.getHeight() > 1) {
      aspect = backgroundImage.getWidth() / backgroundImage.getHeight();
    }
    if (!Double.isFinite(aspect) || aspect <= 0.0) {
      aspect = availableW / Math.max(1.0, availableH);
    }

    double w = availableW;
    double h = w / aspect;
    if (h > availableH) {
      h = availableH;
      w = h * aspect;
    }
    w = Math.max(1, w * zoom);
    h = Math.max(1, h * zoom);

    if (Math.abs(canvas.getWidth() - w) >= 0.5) canvas.setWidth(w);
    if (Math.abs(canvas.getHeight() - h) >= 0.5) canvas.setHeight(h);

    double hostW = Math.max(viewportW, w + CANVAS_PADDING * 2);
    double hostH = Math.max(viewportH, h + CANVAS_PADDING * 2);
    if (Math.abs(canvasHost.getMinWidth() - hostW) >= 0.5) canvasHost.setMinWidth(hostW);
    if (Math.abs(canvasHost.getMinHeight() - hostH) >= 0.5) canvasHost.setMinHeight(hostH);
    if (Math.abs(canvasHost.getPrefWidth() - hostW) >= 0.5) canvasHost.setPrefWidth(hostW);
    if (Math.abs(canvasHost.getPrefHeight() - hostH) >= 0.5) canvasHost.setPrefHeight(hostH);

    canvas.setLayoutX((hostW - w) * 0.5);
    canvas.setLayoutY((hostH - h) * 0.5);
  }

  private double canvasW() { return Math.max(1, canvas.getWidth()); }
  private double canvasH() { return Math.max(1, canvas.getHeight()); }

  // ── Change emission ──

  private void emitChange() {
    if (onBoundsChanged != null) {
      onBoundsChanged.accept(new ArrayList<>(bounds));
    }
  }

  // ── Utilities ──

  private static javafx.scene.layout.Region createSpacer() {
    javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    return spacer;
  }

  private static Button iconButton(javafx.scene.Node icon, String tooltipText) {
    Button button = new Button();
    button.setGraphic(icon);
    button.setTooltip(new Tooltip(tooltipText));
    button.setMinWidth(30);
    button.setPrefWidth(30);
    return button;
  }

  private static void iconToggleButton(ToggleButton button, javafx.scene.Node icon, String tooltipText) {
    button.setText("");
    button.setGraphic(icon);
    button.setTooltip(new Tooltip(tooltipText));
    button.setMinWidth(30);
    button.setPrefWidth(30);
  }

  private boolean containsId(String id) {
    if (id == null || id.isBlank()) return false;
    for (BoundEntry entry : bounds) {
      if (entry == null) continue;
      if (id.equals(entry.getId())) return true;
    }
    return false;
  }

  private static Double parseDouble(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
      return Double.parseDouble(raw.trim());
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String formatDouble(double value) {
    if (Math.rint(value) == value) return Long.toString(Math.round(value));
    return String.format(Locale.ROOT, "%.4f", value)
        .replaceAll("0+$", "")
        .replaceAll("\\.$", "");
  }

  private static double clamp01(double v) { return clamp(v, 0, 1); }

  private static double clamp(double v, double min, double max) {
    if (Double.isNaN(v) || Double.isInfinite(v)) return min;
    if (v < min) return min;
    if (v > max) return max;
    return v;
  }
}
