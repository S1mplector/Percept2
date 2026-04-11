package com.jvn.editor.ui;

import java.io.File;
import java.io.StringReader;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Unified menu editor that combines Layout, Style, and Screen editing
 * into a single cohesive workspace. Shows a live combined preview.
 */
public class UnifiedMenuEditor extends BorderPane {
  private final TabPane editorTabs = new TabPane();
  private final MenuLayoutVisualEditor layoutEditor = new MenuLayoutVisualEditor();
  private final MenuStyleVisualEditor styleEditor = new MenuStyleVisualEditor();
  private final MenuScreenVisualEditor screenEditor = new MenuScreenVisualEditor();

  private Consumer<String> onLayoutChanged;
  private Consumer<String> onStyleChanged;
  private Consumer<String> onScreenChanged;

  private File projectRoot;
  private final Canvas combinedPreview = new Canvas(640, 400);
  private StackPane combinedPreviewHost;

  public UnifiedMenuEditor() {
    setPadding(new Insets(0));

    Tab layoutTab = new Tab("Layout", layoutEditor);
    layoutTab.setClosable(false);

    Tab styleTab = new Tab("Style", styleEditor);
    styleTab.setClosable(false);

    Tab screenTab = new Tab("Screen", screenEditor);
    screenTab.setClosable(false);

    combinedPreview.setManaged(false);
    combinedPreviewHost = new StackPane(combinedPreview);
    StackPane.setAlignment(combinedPreview, Pos.TOP_LEFT);
    combinedPreviewHost.setPadding(new Insets(8));
    combinedPreviewHost.setStyle("-fx-background-color: #121212;");
    combinedPreviewHost.widthProperty().addListener((o, ov, nv) -> updateCombinedPreviewSize());
    combinedPreviewHost.heightProperty().addListener((o, ov, nv) -> updateCombinedPreviewSize());
    combinedPreview.widthProperty().addListener((o, ov, nv) -> redrawCombinedPreview());
    combinedPreview.heightProperty().addListener((o, ov, nv) -> redrawCombinedPreview());
    Tab previewTab = new Tab("Combined Preview", combinedPreviewHost);
    previewTab.setClosable(false);

    editorTabs.getTabs().addAll(layoutTab, styleTab, screenTab, previewTab);
    editorTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

    VBox header = new VBox(4);
    header.setPadding(new Insets(8, 12, 8, 12));
    header.setStyle("-fx-background-color: #1a1a1a; -fx-border-color: #2a2a2a; -fx-border-width: 0 0 1 0;");
    Label title = new Label("Unified Menu Editor");
    title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e6e6e6;");
    Label hint = new Label("Edit layout, style, and screen content in one place. Changes sync automatically.");
    hint.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 11px;");
    header.getChildren().addAll(title, hint);

    setTop(header);
    setCenter(editorTabs);

    setupSync();
    updateCombinedPreviewSize();
  }

  private void setupSync() {
    layoutEditor.setOnLayoutTextChanged(text -> {
      if (onLayoutChanged != null) onLayoutChanged.accept(text);
      syncPreviewContent();
    });

    styleEditor.setOnStyleTextChanged(text -> {
      if (onStyleChanged != null) onStyleChanged.accept(text);
    });

    screenEditor.setOnMenuTextChanged(text -> {
      if (onScreenChanged != null) onScreenChanged.accept(text);
      syncPreviewContent();
    });
  }

  private void syncPreviewContent() {
    List<String> screenItems = screenEditor.getItemLabels();
    String title = screenEditor.getTitleText();
    if (screenItems != null && !screenItems.isEmpty()) {
      layoutEditor.setPreviewContent(title, screenItems);
      styleEditor.setPreviewContent(screenItems);
    }
    redrawCombinedPreview();
  }

  private void redrawCombinedPreview() {
    double w = Math.max(1, combinedPreview.getWidth());
    double h = Math.max(1, combinedPreview.getHeight());
    GraphicsContext g = combinedPreview.getGraphicsContext2D();

    g.setFill(Color.web("#121212"));
    g.fillRect(0, 0, w, h);
    g.setStroke(Color.web("#2a2a2a"));
    g.setLineWidth(1);
    for (int i = 1; i < 6; i++) {
      double yy = (h / 6.0) * i;
      g.strokeLine(0, yy, w, yy);
    }

    // Parse current layout
    Properties layoutProps = new Properties();
    try { layoutProps.load(new StringReader(layoutEditor.getLayoutText())); } catch (Exception ignored) {}
    double listYStart = parseDouble(layoutProps.getProperty("listYStart"), 0.35);
    double lineHeight = parseDouble(layoutProps.getProperty("lineHeight"), 40);
    double listWidthFactor = parseDouble(layoutProps.getProperty("listWidthFactor"), 1.0);
    String textAlign = layoutProps.getProperty("textAlign", "center").toLowerCase(Locale.ROOT);
    String titleAlign = layoutProps.getProperty("titleAlign", "center").toLowerCase(Locale.ROOT);
    double hintsBottomMargin = parseDouble(layoutProps.getProperty("hintsBottomMargin"), 20);
    double subtitleGap = parseDouble(layoutProps.getProperty("subtitleGap"), 12);
    String hintsAlign = layoutProps.getProperty("hintsAlign", "center").toLowerCase(Locale.ROOT);
    String titleXStr = layoutProps.getProperty("titleX");
    Double titleX = titleXStr != null && !titleXStr.isBlank() ? parseDouble(titleXStr, 0.5) : null;
    String hintsXStr = layoutProps.getProperty("hintsX");
    Double hintsX = hintsXStr != null && !hintsXStr.isBlank() ? parseDouble(hintsXStr, 0.5) : null;
    String titleYStr = layoutProps.getProperty("titleY");
    double titleY = titleYStr != null ? parseDouble(titleYStr, 0.12) : 0.12;

    // Parse current style
    Properties styleProps = new Properties();
    try { styleProps.load(new StringReader(styleEditor.getStyleText())); } catch (Exception ignored) {}
    Color itemColor = parseColor(styleProps.getProperty("itemColor"), Color.web("#D3D3D3"));
    Color selectedColor = parseColor(styleProps.getProperty("itemSelectedColor"), Color.web("#FFFF00"));
    String prefix = styleProps.getProperty("itemPrefix", "  ");
    String selectedPrefix = styleProps.getProperty("itemSelectedPrefix", "> ");
    String fontFamily = styleProps.getProperty("itemFontFamily", "Arial");
    String fontWeightStr = styleProps.getProperty("itemFontWeight", "NORMAL");
    int fontSize = (int) parseDouble(styleProps.getProperty("itemFontSize"), 20);
    FontWeight fontWeight = "BOLD".equalsIgnoreCase(fontWeightStr) ? FontWeight.BOLD : FontWeight.NORMAL;

    // Screen items
    List<String> items = screenEditor.getItemLabels();
    String titleText = screenEditor.getTitleText();
    String subtitleText = screenEditor.getSubtitleText();
    if (items == null || items.isEmpty()) items = List.of("New Game", "Load", "Settings", "Quit");
    if (titleText == null || titleText.isBlank()) titleText = "Menu Title";

    // Resolve layout positions
    double resolvedTitleY = titleY <= 1.0 ? h * titleY : titleY;
    double resolvedListY = listYStart <= 1.0 ? h * listYStart : listYStart;
    double listW = w * Math.max(0.1, Math.min(1.0, listWidthFactor));
    double listX = switch (textAlign) {
      case "left" -> 0;
      case "right" -> w - listW;
      default -> (w - listW) / 2.0;
    };

    // Draw title
    g.setFill(Color.web("#e8eaed"));
    g.setFont(Font.font(fontFamily, FontWeight.BOLD, 26));
    javafx.scene.text.Text titleMeasure = new javafx.scene.text.Text(titleText);
    titleMeasure.setFont(g.getFont());
    double titleW = titleMeasure.getLayoutBounds().getWidth();
    double titleXPos = titleX != null
        ? Math.max(0, Math.min(w - titleW, w * titleX - titleW / 2.0))
        : switch (titleAlign) {
          case "left" -> 16.0;
          case "right" -> Math.max(0, w - titleW - 16.0);
          default -> (w - titleW) / 2.0;
        };
    double titleBaselineY = resolvedTitleY;
    g.fillText(titleText, titleXPos, titleBaselineY);
    if (subtitleText != null && !subtitleText.isBlank()) {
      g.setFill(Color.web("#9aa5b5"));
      g.setFont(Font.font(fontFamily, FontWeight.NORMAL, 15));
      javafx.scene.text.Text subtitleMeasure = new javafx.scene.text.Text(subtitleText);
      subtitleMeasure.setFont(g.getFont());
      double subtitleW = subtitleMeasure.getLayoutBounds().getWidth();
      double subtitleXPos = titleX != null
          ? Math.max(0, Math.min(w - subtitleW, w * titleX - subtitleW / 2.0))
          : switch (titleAlign) {
            case "left" -> 16.0;
            case "right" -> Math.max(0, w - subtitleW - 16.0);
            default -> (w - subtitleW) / 2.0;
          };
      double subtitleBaselineY = titleBaselineY + 20 + Math.max(0, subtitleGap);
      g.fillText(subtitleText, subtitleXPos, subtitleBaselineY);
    }

    // Draw items
    g.setFont(Font.font(fontFamily, fontWeight, fontSize));
    for (int i = 0; i < items.size(); i++) {
      boolean isSelected = i == 0;
      String itemPrefix = isSelected ? selectedPrefix : prefix;
      String label = itemPrefix + items.get(i);
      g.setFill(isSelected ? selectedColor : itemColor);

      javafx.scene.text.Text m = new javafx.scene.text.Text(label);
      m.setFont(g.getFont());
      double textW = m.getLayoutBounds().getWidth();

      double itemY = resolvedListY + i * lineHeight;
      double itemX = switch (textAlign) {
        case "left" -> listX;
        case "right" -> listX + Math.max(0, listW - textW);
        default -> listX + (listW - textW) / 2.0;
      };
      g.fillText(label, itemX, itemY);
    }

    // Draw hints
    double hintsY = h - Math.max(0, hintsBottomMargin);
    g.setFill(Color.web("#888888"));
    g.setFont(Font.font(Font.getDefault().getFamily(), 13));
    String hintsText = "Enter: Select    Esc: Back";
    javafx.scene.text.Text hintsMeasure = new javafx.scene.text.Text(hintsText);
    hintsMeasure.setFont(g.getFont());
    double hintsW = hintsMeasure.getLayoutBounds().getWidth();
    double hintsXPos = hintsX != null
        ? Math.max(0, Math.min(w - hintsW, w * hintsX - hintsW / 2.0))
        : switch (hintsAlign) {
          case "left" -> 12.0;
          case "right" -> Math.max(0, w - hintsW - 12.0);
          default -> (w - hintsW) / 2.0;
        };
    g.fillText(hintsText, hintsXPos, hintsY);

    // Labels
    g.setFill(Color.web("#4da3ff88"));
    g.setFont(Font.font(Font.getDefault().getFamily(), FontWeight.BOLD, 10));
    g.fillText("COMBINED PREVIEW", 8, 14);
  }

  private static double parseDouble(String s, double fallback) {
    if (s == null || s.isBlank()) return fallback;
    try { return Double.parseDouble(s.trim()); } catch (Exception e) { return fallback; }
  }

  private static Color parseColor(String s, Color fallback) {
    if (s == null || s.isBlank()) return fallback;
    try { return Color.web(s.trim()); } catch (Exception e) { return fallback; }
  }

  private void updateCombinedPreviewSize() {
    if (combinedPreviewHost == null) return;
    double availableW = Math.max(1.0, combinedPreviewHost.getWidth() - 16.0);
    double availableH = Math.max(1.0, combinedPreviewHost.getHeight() - 16.0);
    double aspect = ProjectViewportSpec.resolve(projectRoot).aspect();
    double w = availableW;
    double h = w / Math.max(0.0001, aspect);
    if (h > availableH) {
      h = availableH;
      w = h * aspect;
    }
    if (Math.abs(combinedPreview.getWidth() - w) >= 0.5) combinedPreview.setWidth(w);
    if (Math.abs(combinedPreview.getHeight() - h) >= 0.5) combinedPreview.setHeight(h);
    double x = 8.0 + (availableW - w) * 0.5;
    double y = 8.0 + (availableH - h) * 0.5;
    if (Math.abs(combinedPreview.getLayoutX() - x) >= 0.5) combinedPreview.setLayoutX(x);
    if (Math.abs(combinedPreview.getLayoutY() - y) >= 0.5) combinedPreview.setLayoutY(y);
  }

  public void setProjectRoot(File root) {
    this.projectRoot = root;
    layoutEditor.setProjectRoot(root);
    styleEditor.setProjectRoot(root);
    screenEditor.setProjectRoot(root);
    updateCombinedPreviewSize();
    redrawCombinedPreview();
  }

  public void setLayoutText(String text) {
    layoutEditor.setLayoutText(text);
  }

  public void setStyleText(String text) {
    styleEditor.setStyleText(text);
  }

  public void setScreenText(String text) {
    screenEditor.setMenuText(text);
    syncPreviewContent();
  }

  public String getLayoutText() {
    return layoutEditor.getLayoutText();
  }

  public String getStyleText() {
    return styleEditor.getStyleText();
  }

  public String getScreenText() {
    return screenEditor.getMenuText();
  }

  public void setOnLayoutChanged(Consumer<String> listener) {
    this.onLayoutChanged = listener;
  }

  public void setOnStyleChanged(Consumer<String> listener) {
    this.onStyleChanged = listener;
  }

  public void setOnScreenChanged(Consumer<String> listener) {
    this.onScreenChanged = listener;
  }

  public void selectLayoutTab() {
    editorTabs.getSelectionModel().select(0);
  }

  public void selectStyleTab() {
    editorTabs.getSelectionModel().select(1);
  }

  public void selectScreenTab() {
    editorTabs.getSelectionModel().select(2);
  }
}
