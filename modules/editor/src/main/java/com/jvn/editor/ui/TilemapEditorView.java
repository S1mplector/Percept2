package com.jvn.editor.ui;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.jvn.scripting.jes.JesParser;
import com.jvn.scripting.jes.JesToken;
import com.jvn.scripting.jes.JesTokenizer;
import com.jvn.scripting.jes.ast.JesAst;

import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

public class TilemapEditorView extends BorderPane {
  private File projectRoot;
  private File jesFile;

  private ComboBox<String> mapBox;
  private ComboBox<String> layerBox;
  private Spinner<Integer> indexSpinner;
  private Label statusLabel;
  private Canvas canvas;
  private ScrollPane scroll;
  private Canvas tilesetCanvas;
  private ScrollPane tilesetScroll;

  private JesAst.SceneDecl currentScene;
  private List<JesAst.MapDecl> maps = new ArrayList<>();
  private JesAst.MapDecl currentMap;
  private List<JesAst.MapLayerDecl> layers = new ArrayList<>();
  private JesAst.MapLayerDecl currentLayer;

  private int cols;
  private int rows;
  private int[][] tiles;
  private int selectedIndex = 0;
  private double cellSize = 24.0;

  private double gridScale = 1.0;

  private JesAst.TilesetDecl currentTileset;
  private Image tilesetImage;
  private int tilesetCols;
  private int tilesetTileW;
  private int tilesetTileH;
  private double tilesetScale = 2.0;

  public TilemapEditorView() {
    mapBox = new ComboBox<>();
    mapBox.setPrefWidth(160);
    mapBox.setPromptText("Map");
    mapBox.setOnAction(e -> {
      selectMap(mapBox.getSelectionModel().getSelectedIndex());
    });

    layerBox = new ComboBox<>();
    layerBox.setPrefWidth(160);
    layerBox.setPromptText("Layer");
    layerBox.setOnAction(e -> {
      selectLayer(layerBox.getSelectionModel().getSelectedIndex());
    });

    indexSpinner = new Spinner<>();
    indexSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 999, 0));
    indexSpinner.valueProperty().addListener((o, ov, nv) -> {
      if (nv != null) selectedIndex = nv;
    });

    Button reloadButton = new Button("Reload");
    reloadButton.setOnAction(e -> reloadFromJes());

    Button saveButton = new Button("Save Layer");
    saveButton.setOnAction(e -> saveLayer());

    Button gridZoomIn = new Button("Grid+");
    gridZoomIn.setOnAction(e -> zoomGrid(1.25));
    Button gridZoomOut = new Button("Grid-");
    gridZoomOut.setOnAction(e -> zoomGrid(1.0 / 1.25));

    Button tilesetZoomIn = new Button("Tileset+");
    tilesetZoomIn.setOnAction(e -> zoomTileset(1.25));
    Button tilesetZoomOut = new Button("Tileset-");
    tilesetZoomOut.setOnAction(e -> zoomTileset(1.0 / 1.25));

    statusLabel = new Label("No JES context");

    HBox tools = new HBox(8,
      new Label("Map:"), mapBox,
      new Label("Layer:"), layerBox,
      new Label("Tile:"), indexSpinner,
      gridZoomOut, gridZoomIn,
      tilesetZoomOut, tilesetZoomIn,
      reloadButton, saveButton, statusLabel);
    tools.setPadding(new Insets(4));

    canvas = new Canvas(400, 300);
    canvas.setOnMousePressed(this::handlePaint);
    canvas.setOnMouseDragged(this::handlePaint);
    Tooltip.install(canvas, new Tooltip("Click or drag to paint tiles"));

    scroll = new ScrollPane(canvas);
    scroll.setFitToWidth(true);
    scroll.setFitToHeight(true);

    tilesetCanvas = new Canvas(256, 128);
    tilesetCanvas.setOnMousePressed(e -> handleTilesetClick(e.getX(), e.getY()));
    Tooltip.install(tilesetCanvas, new Tooltip("Click a tile in the tileset to select index"));
    tilesetScroll = new ScrollPane(tilesetCanvas);
    tilesetScroll.setFitToWidth(true);
    tilesetScroll.setFitToHeight(false);

    ToolBar tb = new ToolBar();
    tb.getItems().add(tools);

    setTop(tb);
    VBox centerBox = new VBox(4, tilesetScroll, scroll);
    centerBox.setPadding(new Insets(4));
    setCenter(centerBox);
  }

  public void setProjectRoot(File root) {
    this.projectRoot = root;
  }

  public void setContext(File projectRoot, File jesFile) {
    this.projectRoot = projectRoot;
    this.jesFile = jesFile;
    reloadFromJes();
  }

  public void clearContext() {
    this.jesFile = null;
    this.currentScene = null;
    this.maps.clear();
    this.currentMap = null;
    this.layers.clear();
    this.currentLayer = null;
    this.tiles = null;
    if (mapBox != null) mapBox.getItems().clear();
    if (layerBox != null) layerBox.getItems().clear();
    currentTileset = null;
    tilesetImage = null;
    redraw();
    redrawTileset();
    if (statusLabel != null) statusLabel.setText("No JES context");
  }

  private void reloadFromJes() {
    if (jesFile == null || !jesFile.exists()) {
      clearContext();
      return;
    }
    try {
      String code = Files.readString(jesFile.toPath());
      List<JesToken> toks = JesTokenizer.tokenize(new java.io.ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8)));
      JesParser parser = new JesParser(toks);
      JesAst.Program prog = parser.parseProgram();
      if (prog.scenes.isEmpty()) {
        clearContext();
        return;
      }
      JesAst.SceneDecl scene = prog.scenes.get(0);
      currentScene = scene;
      if (scene.maps.isEmpty()) {
        clearContext();
        statusLabel.setText("Scene has no maps");
        return;
      }
      maps = new ArrayList<>();
      List<String> mapNames = new ArrayList<>();
      for (JesAst.MapDecl m : scene.maps) {
        if (m == null) continue;
        maps.add(m);
        mapNames.add(m.name == null ? "map" : m.name);
      }
      mapBox.getItems().setAll(mapNames);
      if (!maps.isEmpty()) {
        mapBox.getSelectionModel().select(0);
        selectMap(0);
      } else {
        currentMap = null;
        layers = new ArrayList<>();
        layerBox.getItems().clear();
        currentLayer = null;
        tiles = null;
        redraw();
        updateTilesetForCurrentMap();
        statusLabel.setText("Scene has no maps");
      }
    } catch (Exception ex) {
      clearContext();
      statusLabel.setText("Parse error");
    }
  }

  private void selectMap(int index) {
    if (maps == null || index < 0 || index >= maps.size()) {
      currentMap = null;
      layers = new ArrayList<>();
      layerBox.getItems().clear();
      currentLayer = null;
      tiles = null;
      redraw();
      updateTilesetForCurrentMap();
      return;
    }
    currentMap = maps.get(index);
    rebuildLayersForCurrentMap();
    updateTilesetForCurrentMap();
  }

  private void rebuildLayersForCurrentMap() {
    layers = new ArrayList<>();
    List<String> names = new ArrayList<>();
    if (currentMap != null) {
      for (JesAst.MapLayerDecl l : currentMap.layers) {
        if (l == null) continue;
        Object d = l.props.get("data");
        if (d instanceof String) {
          layers.add(l);
          names.add(l.name == null ? "layer" : l.name);
        }
      }
    }
    layerBox.getItems().setAll(names);
    if (!layers.isEmpty()) {
      layerBox.getSelectionModel().select(0);
      selectLayer(0);
    } else {
      currentLayer = null;
      tiles = null;
      redraw();
      statusLabel.setText("Map has no data layers");
    }
  }

  private void selectLayer(int index) {
    if (index < 0 || index >= layers.size()) return;
    currentLayer = layers.get(index);
    loadLayerData();
  }

  private void loadLayerData() {
    if (currentLayer == null) {
      tiles = null;
      redraw();
      return;
    }
    String dataPath = null;
    Object d = currentLayer.props.get("data");
    if (d instanceof String s) dataPath = s;
    if (projectRoot == null || dataPath == null || dataPath.isBlank()) {
      tiles = null;
      redraw();
      statusLabel.setText("Missing data path");
      return;
    }
    File f = new File(projectRoot, dataPath);
    if (!f.exists()) {
      int w = (int) getMapProp(currentMap, "width", 32);
      int h = (int) getMapProp(currentMap, "height", 32);
      cols = Math.max(1, w);
      rows = Math.max(1, h);
      tiles = new int[rows][cols];
      for (int y = 0; y < rows; y++) for (int x = 0; x < cols; x++) tiles[y][x] = -1;
      redraw();
      statusLabel.setText("Created new layer grid");
      return;
    }
    try {
      List<String> lines = Files.readAllLines(f.toPath());
      rows = lines.size();
      cols = 0;
      for (String line : lines) {
        String[] parts = line.split(",");
        if (parts.length > cols) cols = parts.length;
      }
      if (rows <= 0 || cols <= 0) {
        rows = 1;
        cols = 1;
      }
      tiles = new int[rows][cols];
      for (int y = 0; y < rows; y++) {
        String line = lines.get(y);
        String[] parts = line.split(",");
        for (int x = 0; x < cols; x++) {
          int val = -1;
          if (x < parts.length) {
            String s = parts[x].trim();
            if (!s.isEmpty()) {
              try { val = Integer.parseInt(s); } catch (NumberFormatException ignore) {}
            }
          }
          tiles[y][x] = val;
        }
      }
      redraw();
      statusLabel.setText("Loaded layer");
    } catch (Exception ex) {
      tiles = null;
      redraw();
      statusLabel.setText("Failed to load CSV");
    }
  }

  private double getMapProp(JesAst.MapDecl m, String key, double def) {
    if (m == null || m.props == null) return def;
    Object v = m.props.get(key);
    if (v instanceof Number n) return n.doubleValue();
    return def;
  }

  private double getTilesetProp(JesAst.TilesetDecl t, String key, double def) {
    if (t == null || t.props == null) return def;
    Object v = t.props.get(key);
    if (v instanceof Number n) return n.doubleValue();
    return def;
  }

  private void updateTilesetForCurrentMap() {
    currentTileset = null;
    tilesetImage = null;
    tilesetCols = 0;
    tilesetTileW = 0;
    tilesetTileH = 0;
    if (currentScene == null || currentMap == null || projectRoot == null) {
      redrawTileset();
      return;
    }
    Object tsObj = currentMap.props.get("tileset");
    if (!(tsObj instanceof String tsName) || tsName.isBlank()) {
      redrawTileset();
      return;
    }
    for (JesAst.TilesetDecl t : currentScene.tilesets) {
      if (t != null && tsName.equals(t.name)) {
        currentTileset = t;
        break;
      }
    }
    if (currentTileset == null) {
      redrawTileset();
      return;
    }
    Object imgObj = currentTileset.props.get("image");
    if (!(imgObj instanceof String imgPath) || imgPath.isBlank()) {
      redrawTileset();
      return;
    }
    File imgFile = new File(projectRoot, imgPath);
    if (!imgFile.exists()) {
      redrawTileset();
      return;
    }
    try {
      tilesetImage = new Image(imgFile.toURI().toString());
    } catch (Exception ex) {
      tilesetImage = null;
    }
    tilesetTileW = (int) getTilesetProp(currentTileset, "tileW", 16);
    tilesetTileH = (int) getTilesetProp(currentTileset, "tileH", 16);
    if (tilesetImage != null && tilesetTileW > 0) {
      int colsFromImage = (int) (tilesetImage.getWidth() / tilesetTileW);
      double defCols = colsFromImage > 0 ? colsFromImage : 1;
      tilesetCols = (int) getTilesetProp(currentTileset, "cols", defCols);
      if (tilesetCols <= 0) tilesetCols = colsFromImage > 0 ? colsFromImage : 1;
    } else {
      tilesetCols = (int) getTilesetProp(currentTileset, "cols", 8);
    }
    redrawTileset();
  }

  private void saveLayer() {
    if (currentLayer == null || tiles == null) return;
    if (projectRoot == null) return;
    Object d = currentLayer.props.get("data");
    if (!(d instanceof String s) || s.isBlank()) return;
    File f = new File(projectRoot, s);
    try {
      File parent = f.getParentFile();
      if (parent != null && !parent.exists()) parent.mkdirs();
      try (FileWriter fw = new FileWriter(f, false)) {
        for (int y = 0; y < rows; y++) {
          StringBuilder sb = new StringBuilder();
          for (int x = 0; x < cols; x++) {
            if (x > 0) sb.append(',');
            int v = tiles[y][x];
            if (v >= 0) sb.append(v);
          }
          sb.append('\n');
          fw.write(sb.toString());
        }
      }
      statusLabel.setText("Saved " + f.getName());
    } catch (Exception ex) {
      statusLabel.setText("Save failed");
    }
  }

  private void handleTilesetClick(double px, double py) {
    if (tilesetImage == null || tilesetTileW <= 0 || tilesetTileH <= 0 || tilesetCols <= 0) return;
    double stepX = tilesetTileW * tilesetScale;
    double stepY = tilesetTileH * tilesetScale;
    if (stepX <= 0 || stepY <= 0) return;
    int tx = (int) (px / stepX);
    int ty = (int) (py / stepY);
    if (tx < 0 || ty < 0) return;
    int maxCols = (int) (tilesetImage.getWidth() / tilesetTileW);
    int maxRows = (int) (tilesetImage.getHeight() / tilesetTileH);
    if (tx >= maxCols || ty >= maxRows) return;
    int idx = ty * tilesetCols + tx;
    selectedIndex = idx;
    if (indexSpinner != null && indexSpinner.getValueFactory() instanceof SpinnerValueFactory.IntegerSpinnerValueFactory vf) {
      if (idx > vf.getMax()) vf.setMax(idx);
      vf.setValue(idx);
    }
    redraw();
    redrawTileset();
  }

  private void handlePaint(MouseEvent e) {
    if (tiles == null) return;
    double step = cellSize * gridScale;
    if (step <= 0) return;
    double px = e.getX();
    double py = e.getY();
    int tx = (int) (px / step);
    int ty = (int) (py / step);
    if (ty < 0 || ty >= rows || tx < 0 || tx >= cols) return;
    boolean erase = e.isSecondaryButtonDown() || e.getButton() == MouseButton.SECONDARY;
    boolean paint = e.isPrimaryButtonDown() || e.getButton() == MouseButton.PRIMARY;
    if (erase) {
      tiles[ty][tx] = -1;
    } else if (paint) {
      tiles[ty][tx] = selectedIndex;
    } else {
      return;
    }
    redraw();
  }

  private void redraw() {
    GraphicsContext g = canvas.getGraphicsContext2D();
    if (tiles == null) {
      canvas.setWidth(sanitizeCanvasDimension(400));
      canvas.setHeight(sanitizeCanvasDimension(300));
      g.setFill(Color.color(0.1, 0.1, 0.12));
      g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
      g.setFill(Color.color(0.8, 0.8, 0.8));
      g.fillText("No map layer", 16, 24);
      return;
    }
    double step = cellSize * gridScale;
    canvas.setWidth(sanitizeCanvasDimension(cols * step));
    canvas.setHeight(sanitizeCanvasDimension(rows * step));
    g.setFill(Color.color(0.06, 0.06, 0.08));
    g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    for (int y = 0; y < rows; y++) {
      for (int x = 0; x < cols; x++) {
        double sx = x * step;
        double sy = y * step;
        int v = tiles[y][x];
        if (v >= 0) {
          double hue = (v * 37) % 360;
          Color c = Color.hsb(hue, 0.6, 0.9);
          g.setFill(c);
        } else {
          g.setFill(Color.color(0.12, 0.12, 0.16));
        }
        g.fillRect(sx, sy, step, step);
        g.setStroke(Color.color(0.2, 0.2, 0.25));
        g.strokeRect(sx, sy, step, step);
      }
    }
  }

  private void zoomGrid(double factor) {
    gridScale *= factor;
    if (gridScale < 0.25) gridScale = 0.25;
    if (gridScale > 4.0) gridScale = 4.0;
    redraw();
  }

  private void redrawTileset() {
    GraphicsContext g = tilesetCanvas.getGraphicsContext2D();
    if (tilesetImage == null || tilesetTileW <= 0 || tilesetTileH <= 0) {
      tilesetCanvas.setWidth(sanitizeCanvasDimension(300));
      tilesetCanvas.setHeight(sanitizeCanvasDimension(80));
      g.setFill(Color.color(0.1, 0.1, 0.12));
      g.fillRect(0, 0, tilesetCanvas.getWidth(), tilesetCanvas.getHeight());
      g.setFill(Color.color(0.8, 0.8, 0.8));
      g.fillText("No tileset", 16, 24);
      return;
    }
    double srcW = tilesetImage.getWidth();
    double srcH = tilesetImage.getHeight();
    double cw = srcW * tilesetScale;
    double ch = srcH * tilesetScale;
    tilesetCanvas.setWidth(sanitizeCanvasDimension(cw));
    tilesetCanvas.setHeight(sanitizeCanvasDimension(ch));
    cw = tilesetCanvas.getWidth();
    ch = tilesetCanvas.getHeight();
    g.setFill(Color.color(0.06, 0.06, 0.08));
    g.fillRect(0, 0, cw, ch);
    g.drawImage(tilesetImage, 0, 0, cw, ch);
    double stepX = tilesetTileW * tilesetScale;
    double stepY = tilesetTileH * tilesetScale;
    g.setStroke(Color.color(1, 1, 1, 0.25));
    for (double x = 0; x <= cw; x += stepX) g.strokeLine(x, 0, x, ch);
    for (double y = 0; y <= ch; y += stepY) g.strokeLine(0, y, cw, y);
    if (selectedIndex >= 0 && tilesetCols > 0) {
      int sx = selectedIndex % tilesetCols;
      int sy = selectedIndex / tilesetCols;
      double hx = sx * stepX;
      double hy = sy * stepY;
      g.setStroke(Color.color(1.0, 1.0, 0.2, 0.9));
      g.setLineWidth(2.0);
      g.strokeRect(hx + 1, hy + 1, stepX - 2, stepY - 2);
    }
  }

  private void zoomTileset(double factor) {
    tilesetScale *= factor;
    if (tilesetScale < 0.5) tilesetScale = 0.5;
    if (tilesetScale > 8.0) tilesetScale = 8.0;
    redrawTileset();
  }

  private static double sanitizeCanvasDimension(double value) {
    if (!Double.isFinite(value)) return 1.0;
    if (value < 1.0) return 1.0;
    if (value > 8192.0) return 8192.0;
    return value;
  }
}
