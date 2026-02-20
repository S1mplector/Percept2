package com.jvn.editor.ui;

import com.jvn.core.vn.ui.VnUiLayoutLoader;
import com.jvn.core.vn.ui.VnUiLayoutSpec;
import com.jvn.core.vn.ui.VnUiStyleSpec;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * Visual editor for dialogue UI layout (textbox/namebox/text bounds/choice bounds).
 * The view emits full properties text that can be synced with a code editor.
 */
public class DialogueLayoutEditorView extends BorderPane {
  private static final double PREVIEW_PADDING = 8.0;
  private static final String[] KNOWN_KEYS = new String[] {
      "textBoxX",
      "textBoxY",
      "textBoxWidth",
      "textBoxHeight",
      "textBoxPadding",
      "textBoxAsset",
      "choiceButtonAsset",
      "choiceButtonHoverAsset",
      "choiceButtonSelectedAsset",
      "choiceButtonDisabledAsset",
      "choiceBackgroundColor",
      "choiceHoverColor",
      "choiceSelectedColor",
      "choiceDisabledColor",
      "choiceTextColor",
      "choiceHoverTextColor",
      "choiceSelectedTextColor",
      "choiceDisabledTextColor",
      "choiceBorderColor",
      "choiceHoverBorderColor",
      "choiceSelectedBorderColor",
      "choiceDisabledBorderColor",
      "choiceCornerRadius",
      "choiceBorderWidth",
      "choiceTextBaselineOffset",
      "nameBoxXOffset",
      "nameBoxYOffset",
      "nameBoxWidth",
      "nameBoxHeight",
      "nameTextXOffset",
      "nameTextBaselineOffset",
      "dialogueTextHorizontalPadding",
      "dialogueTextTopPadding",
      "choiceXCenter",
      "choiceYStart",
      "choiceWidthFactor",
      "choiceHeight",
      "choiceGap",
      "choiceTextXPadding"
  };

  private final Canvas preview = new Canvas(920, 430);
  private final Properties rawProperties = new Properties();
  private VnUiLayoutSpec spec = VnUiLayoutSpec.defaults();
  private VnUiStyleSpec style = VnUiStyleSpec.defaults();
  private Consumer<String> onLayoutTextChanged;
  private boolean suppressEvents = false;
  private String lastLoadedText = "";
  private String lastEmittedText = "";
  private File projectRoot;
  private Image textBoxAssetImage;
  private Image choiceButtonAssetImage;
  private Image choiceButtonHoverAssetImage;
  private Image choiceButtonDisabledAssetImage;
  private final Map<String, Image> previewAssetCache = new LinkedHashMap<>();

  private final Spinner<Double> spTextBoxX = spinner(0, 1, 0, 0.01);
  private final Spinner<Double> spTextBoxY = spinner(0, 1, 0.75, 0.01);
  private final Spinner<Double> spTextBoxWidth = spinner(0.05, 1, 1, 0.01);
  private final Spinner<Double> spTextBoxHeight = spinner(0.05, 1, 0.25, 0.01);
  private final Spinner<Double> spTextBoxPadding = spinner(0, 200, 20, 1);
  private final TextField tfTextBoxAsset = new TextField();
  private final TextField tfChoiceButtonAsset = new TextField();
  private final TextField tfChoiceButtonHoverAsset = new TextField();
  private final TextField tfChoiceButtonSelectedAsset = new TextField();
  private final TextField tfChoiceButtonDisabledAsset = new TextField();

  private final Spinner<Double> spNameBoxXOffset = spinner(-500, 500, 20, 1);
  private final Spinner<Double> spNameBoxYOffset = spinner(-500, 500, -40, 1);
  private final Spinner<Double> spNameBoxWidth = spinner(20, 1000, 200, 1);
  private final Spinner<Double> spNameBoxHeight = spinner(12, 300, 40, 1);
  private final Spinner<Double> spNameTextXOffset = spinner(-300, 300, 10, 1);
  private final Spinner<Double> spNameTextBaselineOffset = spinner(-300, 300, 25, 1);

  private final Spinner<Double> spDialoguePaddingX = spinner(0, 300, 20, 1);
  private final Spinner<Double> spDialoguePaddingTop = spinner(-300, 300, 40, 1);

  private final Spinner<Double> spChoiceXCenter = spinner(0, 1, 0.5, 0.01);
  private final Spinner<Double> spChoiceYStart = spinner(-1, 1, -1, 0.01);
  private final Spinner<Double> spChoiceWidthFactor = spinner(0.1, 1, 0.6, 0.01);
  private final Spinner<Double> spChoiceHeight = spinner(14, 200, 50, 1);
  private final Spinner<Double> spChoiceGap = spinner(0, 120, 10, 1);
  private final Spinner<Double> spChoiceTextXPadding = spinner(0, 300, 20, 1);
  private final TextField tfChoiceBgColor = new TextField();
  private final TextField tfChoiceHoverColor = new TextField();
  private final TextField tfChoiceSelectedColor = new TextField();
  private final TextField tfChoiceDisabledColor = new TextField();
  private final TextField tfChoiceTextColor = new TextField();
  private final TextField tfChoiceHoverTextColor = new TextField();
  private final TextField tfChoiceSelectedTextColor = new TextField();
  private final TextField tfChoiceDisabledTextColor = new TextField();
  private final TextField tfChoiceBorderColor = new TextField();
  private final TextField tfChoiceHoverBorderColor = new TextField();
  private final TextField tfChoiceSelectedBorderColor = new TextField();
  private final TextField tfChoiceDisabledBorderColor = new TextField();
  private final Spinner<Double> spChoiceCornerRadius = spinner(0, 96, 10, 1);
  private final Spinner<Double> spChoiceBorderWidth = spinner(0, 12, 2, 0.1);
  private final Spinner<Double> spChoiceTextBaselineOffset = spinner(-120, 120, 5, 1);

  private DragTarget dragTarget = DragTarget.NONE;
  private double dragStartX;
  private double dragStartY;
  private VnUiLayoutSpec dragStartSpec = VnUiLayoutSpec.defaults();

  private enum DragTarget {
    NONE,
    TEXT_BOX,
    NAME_BOX,
    CHOICE_BLOCK
  }

  public DialogueLayoutEditorView() {
    setPadding(new Insets(8));

    preview.setManaged(false);
    StackPane previewPane = new StackPane(preview);
    StackPane.setAlignment(preview, Pos.TOP_LEFT);
    previewPane.getStyleClass().add("layout-studio-preview-host");
    previewPane.setPadding(new Insets(PREVIEW_PADDING));
    setCenter(previewPane);

    ScrollPane controls = new ScrollPane(buildControls());
    controls.setFitToWidth(true);
    controls.setPrefWidth(350);
    controls.getStyleClass().add("layout-studio-controls-pane");
    setRight(controls);

    previewPane.widthProperty().addListener((o, ov, nv) -> updatePreviewSize(previewPane));
    previewPane.heightProperty().addListener((o, ov, nv) -> updatePreviewSize(previewPane));
    preview.widthProperty().addListener((o, ov, nv) -> redraw());
    preview.heightProperty().addListener((o, ov, nv) -> redraw());

    registerPreviewDrag();
    registerControlListeners();
    updatePreviewSize(previewPane);
    redraw();
  }

  public void setOnLayoutTextChanged(Consumer<String> onLayoutTextChanged) {
    this.onLayoutTextChanged = onLayoutTextChanged;
  }

  public void setProjectRoot(File root) {
    this.projectRoot = root;
    previewAssetCache.clear();
    loadTextBoxAssetImage();
    loadChoiceAssetImages();
    redraw();
  }

  public void setLayoutText(String text) {
    String normalizedInput = normalizeText(text);
    if (normalizedInput.equals(lastLoadedText)) return;
    suppressEvents = true;
    rawProperties.clear();
    try {
      if (text != null && !text.isBlank()) rawProperties.load(new StringReader(text));
    } catch (Exception ignored) {
      // Keep defaults for invalid input.
    }
    spec = VnUiLayoutLoader.parse(rawProperties, VnUiLayoutSpec.defaults());
    style = VnUiLayoutLoader.parseStyle(rawProperties, VnUiStyleSpec.defaults());
    applySpecToControls(spec);
    applyStyleToControls(style);
    loadTextBoxAssetImage();
    loadChoiceAssetImages();
    suppressEvents = false;
    redraw();
    lastLoadedText = normalizedInput;
    lastEmittedText = normalizeText(serialize(spec, style, rawProperties));
  }

  public String getLayoutText() {
    return serialize(spec, style, rawProperties);
  }

  private GridPane buildControls() {
    GridPane grid = new GridPane();
    grid.setHgap(8);
    grid.setVgap(8);
    grid.setPadding(new Insets(8));

    tfTextBoxAsset.setPromptText("assets/ui/textbox.png");
    tfChoiceButtonAsset.setPromptText("assets/ui/choice.png");
    tfChoiceButtonHoverAsset.setPromptText("assets/ui/choice_hover.png");
    tfChoiceButtonSelectedAsset.setPromptText("assets/ui/choice_selected.png");
    tfChoiceButtonDisabledAsset.setPromptText("assets/ui/choice_disabled.png");

    tfChoiceBgColor.setPromptText("#3a3f54");
    tfChoiceHoverColor.setPromptText("#4a5570");
    tfChoiceSelectedColor.setPromptText("#4a5570");
    tfChoiceDisabledColor.setPromptText("#3c3f4a");
    tfChoiceTextColor.setPromptText("#ffffff");
    tfChoiceHoverTextColor.setPromptText("#ffffff");
    tfChoiceSelectedTextColor.setPromptText("#ffffff");
    tfChoiceDisabledTextColor.setPromptText("#8b90a0");
    tfChoiceBorderColor.setPromptText("#b2c5ff");
    tfChoiceHoverBorderColor.setPromptText("#c5d3ff");
    tfChoiceSelectedBorderColor.setPromptText("#c5d3ff");
    tfChoiceDisabledBorderColor.setPromptText("#7a8194");

    int row = 0;
    row = addHeader(grid, row, "Textbox");
    row = addRow(grid, row, "TextBox X", spTextBoxX);
    row = addRow(grid, row, "TextBox Y", spTextBoxY);
    row = addRow(grid, row, "TextBox Width", spTextBoxWidth);
    row = addRow(grid, row, "TextBox Height", spTextBoxHeight);
    row = addRow(grid, row, "TextBox Padding", spTextBoxPadding);
    row = addRow(grid, row, "TextBox Asset", assetFieldRow(tfTextBoxAsset, "Select Textbox Asset"));

    row = addHeader(grid, row, "Name Box");
    row = addRow(grid, row, "Name X Offset", spNameBoxXOffset);
    row = addRow(grid, row, "Name Y Offset", spNameBoxYOffset);
    row = addRow(grid, row, "Name Width", spNameBoxWidth);
    row = addRow(grid, row, "Name Height", spNameBoxHeight);
    row = addRow(grid, row, "Name Text X Offset", spNameTextXOffset);
    row = addRow(grid, row, "Name Text Baseline", spNameTextBaselineOffset);

    row = addHeader(grid, row, "Dialogue Text Bounds");
    row = addRow(grid, row, "Text Horizontal Padding", spDialoguePaddingX);
    row = addRow(grid, row, "Text Top Padding", spDialoguePaddingTop);

    row = addHeader(grid, row, "Choices");
    row = addRow(grid, row, "Choice X Center", spChoiceXCenter);
    row = addRow(grid, row, "Choice Y Start", spChoiceYStart);
    row = addRow(grid, row, "Choice Width Factor", spChoiceWidthFactor);
    row = addRow(grid, row, "Choice Height", spChoiceHeight);
    row = addRow(grid, row, "Choice Gap", spChoiceGap);
    row = addRow(grid, row, "Choice Text Padding", spChoiceTextXPadding);
    row = addRow(grid, row, "Choice Button Asset", assetFieldRow(tfChoiceButtonAsset, "Select Choice Button Asset"));
    row = addRow(grid, row, "Choice Hover Asset", assetFieldRow(tfChoiceButtonHoverAsset, "Select Choice Hover Asset"));
    row = addRow(grid, row, "Choice Selected Asset", assetFieldRow(tfChoiceButtonSelectedAsset, "Select Choice Selected Asset"));
    row = addRow(grid, row, "Choice Disabled Asset", assetFieldRow(tfChoiceButtonDisabledAsset, "Select Choice Disabled Asset"));
    row = addRow(grid, row, "Choice Background Color", tfChoiceBgColor);
    row = addRow(grid, row, "Choice Hover Color", tfChoiceHoverColor);
    row = addRow(grid, row, "Choice Selected Color", tfChoiceSelectedColor);
    row = addRow(grid, row, "Choice Disabled Color", tfChoiceDisabledColor);
    row = addRow(grid, row, "Choice Text Color", tfChoiceTextColor);
    row = addRow(grid, row, "Choice Hover Text Color", tfChoiceHoverTextColor);
    row = addRow(grid, row, "Choice Selected Text Color", tfChoiceSelectedTextColor);
    row = addRow(grid, row, "Choice Disabled Text Color", tfChoiceDisabledTextColor);
    row = addRow(grid, row, "Choice Border Color", tfChoiceBorderColor);
    row = addRow(grid, row, "Choice Hover Border Color", tfChoiceHoverBorderColor);
    row = addRow(grid, row, "Choice Selected Border Color", tfChoiceSelectedBorderColor);
    row = addRow(grid, row, "Choice Disabled Border Color", tfChoiceDisabledBorderColor);
    row = addRow(grid, row, "Choice Corner Radius", spChoiceCornerRadius);
    row = addRow(grid, row, "Choice Border Width", spChoiceBorderWidth);
    row = addRow(grid, row, "Choice Text Baseline", spChoiceTextBaselineOffset);

    Label hint = new Label("Drag boxes in preview to position Textbox/Name/Choices.");
    hint.getStyleClass().add("muted");
    hint.setWrapText(true);
    grid.add(hint, 0, row, 2, 1);
    return grid;
  }

  private HBox assetFieldRow(TextField field, String dialogTitle) {
    Button browse = new Button("Browse...");
    browse.setOnAction(e -> browseAsset(field, dialogTitle));
    Button clear = new Button("Clear");
    clear.setOnAction(e -> field.setText(""));
    HBox row = new HBox(6, field, browse, clear);
    HBox.setHgrow(field, Priority.ALWAYS);
    return row;
  }

  private int addHeader(GridPane grid, int row, String title) {
    if (row > 0) grid.add(new Separator(), 0, row++, 2, 1);
    Label label = new Label(title);
    label.setFont(Font.font(label.getFont().getFamily(), FontWeight.BOLD, 12));
    grid.add(label, 0, row++, 2, 1);
    return row;
  }

  private int addRow(GridPane grid, int row, String label, Spinner<Double> spinner) {
    return addRow(grid, row, label, (javafx.scene.Node) spinner);
  }

  private int addRow(GridPane grid, int row, String label, javafx.scene.Node control) {
    Label l = new Label(label);
    GridPane.setHgrow(control, Priority.ALWAYS);
    if (control instanceof Spinner<?> spinner) spinner.setMaxWidth(Double.MAX_VALUE);
    if (control instanceof HBox box) box.setMaxWidth(Double.MAX_VALUE);
    if (control instanceof TextField textField) textField.setMaxWidth(Double.MAX_VALUE);
    grid.add(l, 0, row);
    grid.add(control, 1, row);
    return row + 1;
  }

  private void registerControlListeners() {
    List<Spinner<Double>> controls = new ArrayList<>();
    controls.add(spTextBoxX);
    controls.add(spTextBoxY);
    controls.add(spTextBoxWidth);
    controls.add(spTextBoxHeight);
    controls.add(spTextBoxPadding);
    controls.add(spNameBoxXOffset);
    controls.add(spNameBoxYOffset);
    controls.add(spNameBoxWidth);
    controls.add(spNameBoxHeight);
    controls.add(spNameTextXOffset);
    controls.add(spNameTextBaselineOffset);
    controls.add(spDialoguePaddingX);
    controls.add(spDialoguePaddingTop);
    controls.add(spChoiceXCenter);
    controls.add(spChoiceYStart);
    controls.add(spChoiceWidthFactor);
    controls.add(spChoiceHeight);
    controls.add(spChoiceGap);
    controls.add(spChoiceTextXPadding);
    controls.add(spChoiceCornerRadius);
    controls.add(spChoiceBorderWidth);
    controls.add(spChoiceTextBaselineOffset);
    for (Spinner<Double> control : controls) {
      control.valueProperty().addListener((o, ov, nv) -> onControlChanged());
    }
    List<TextField> styleFields = List.of(
        tfTextBoxAsset,
        tfChoiceButtonAsset,
        tfChoiceButtonHoverAsset,
        tfChoiceButtonSelectedAsset,
        tfChoiceButtonDisabledAsset,
        tfChoiceBgColor,
        tfChoiceHoverColor,
        tfChoiceSelectedColor,
        tfChoiceDisabledColor,
        tfChoiceTextColor,
        tfChoiceHoverTextColor,
        tfChoiceSelectedTextColor,
        tfChoiceDisabledTextColor,
        tfChoiceBorderColor,
        tfChoiceHoverBorderColor,
        tfChoiceSelectedBorderColor,
        tfChoiceDisabledBorderColor
    );
    for (TextField field : styleFields) {
      field.textProperty().addListener((o, ov, nv) -> onStyleChanged());
    }
  }

  private void onControlChanged() {
    if (suppressEvents) return;
    spec = readSpecFromControls();
    style = readStyleFromControls();
    loadTextBoxAssetImage();
    loadChoiceAssetImages();
    redraw();
    emitText();
  }

  private void onStyleChanged() {
    if (suppressEvents) return;
    style = readStyleFromControls();
    loadTextBoxAssetImage();
    loadChoiceAssetImages();
    redraw();
    emitText();
  }

  private void browseAsset(TextField target, String dialogTitle) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle(dialogTitle == null || dialogTitle.isBlank() ? "Select Asset" : dialogTitle);
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
        "Image Files", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif", "*.webp"));

    File initialDir = resolveInitialAssetDirectory();
    if (initialDir != null && initialDir.isDirectory()) {
      chooser.setInitialDirectory(initialDir);
    }

    Window owner = getScene() != null ? getScene().getWindow() : null;
    File picked = chooser.showOpenDialog(owner);
    if (picked == null) return;
    String relative = toProjectRelativePath(picked);
    target.setText(relative);
  }

  private void registerPreviewDrag() {
    preview.setOnMousePressed(e -> {
      dragStartX = e.getX();
      dragStartY = e.getY();
      dragStartSpec = spec;
      dragTarget = hitTest(e.getX(), e.getY());
    });
    preview.setOnMouseReleased(e -> dragTarget = DragTarget.NONE);
    preview.setOnMouseDragged(e -> {
      if (dragTarget == DragTarget.NONE) return;
      double w = Math.max(1, preview.getWidth());
      double h = Math.max(1, preview.getHeight());
      double dx = e.getX() - dragStartX;
      double dy = e.getY() - dragStartY;

      VnUiLayoutSpec next = dragStartSpec;
      if (dragTarget == DragTarget.TEXT_BOX) {
        next = new VnUiLayoutSpec(
            dragStartSpec.textBoxX() + (dx / w),
            dragStartSpec.textBoxY() + (dy / h),
            dragStartSpec.textBoxWidth(),
            dragStartSpec.textBoxHeight(),
            dragStartSpec.textBoxPadding(),
            dragStartSpec.nameBoxXOffset(),
            dragStartSpec.nameBoxYOffset(),
            dragStartSpec.nameBoxWidth(),
            dragStartSpec.nameBoxHeight(),
            dragStartSpec.nameTextXOffset(),
            dragStartSpec.nameTextBaselineOffset(),
            dragStartSpec.dialogueTextHorizontalPadding(),
            dragStartSpec.dialogueTextTopPadding(),
            dragStartSpec.choiceXCenter(),
            dragStartSpec.choiceYStart(),
            dragStartSpec.choiceWidthFactor(),
            dragStartSpec.choiceHeight(),
            dragStartSpec.choiceGap(),
            dragStartSpec.choiceTextXPadding()
        );
      } else if (dragTarget == DragTarget.NAME_BOX) {
        next = new VnUiLayoutSpec(
            dragStartSpec.textBoxX(),
            dragStartSpec.textBoxY(),
            dragStartSpec.textBoxWidth(),
            dragStartSpec.textBoxHeight(),
            dragStartSpec.textBoxPadding(),
            dragStartSpec.nameBoxXOffset() + dx,
            dragStartSpec.nameBoxYOffset() + dy,
            dragStartSpec.nameBoxWidth(),
            dragStartSpec.nameBoxHeight(),
            dragStartSpec.nameTextXOffset(),
            dragStartSpec.nameTextBaselineOffset(),
            dragStartSpec.dialogueTextHorizontalPadding(),
            dragStartSpec.dialogueTextTopPadding(),
            dragStartSpec.choiceXCenter(),
            dragStartSpec.choiceYStart(),
            dragStartSpec.choiceWidthFactor(),
            dragStartSpec.choiceHeight(),
            dragStartSpec.choiceGap(),
            dragStartSpec.choiceTextXPadding()
        );
      } else if (dragTarget == DragTarget.CHOICE_BLOCK) {
        double currentChoiceStart = resolveChoiceYStart(dragStartSpec, h, 3);
        double nextStart = currentChoiceStart + dy;
        double nextYStartNorm = clamp01(nextStart / h);
        next = new VnUiLayoutSpec(
            dragStartSpec.textBoxX(),
            dragStartSpec.textBoxY(),
            dragStartSpec.textBoxWidth(),
            dragStartSpec.textBoxHeight(),
            dragStartSpec.textBoxPadding(),
            dragStartSpec.nameBoxXOffset(),
            dragStartSpec.nameBoxYOffset(),
            dragStartSpec.nameBoxWidth(),
            dragStartSpec.nameBoxHeight(),
            dragStartSpec.nameTextXOffset(),
            dragStartSpec.nameTextBaselineOffset(),
            dragStartSpec.dialogueTextHorizontalPadding(),
            dragStartSpec.dialogueTextTopPadding(),
            dragStartSpec.choiceXCenter() + (dx / w),
            nextYStartNorm,
            dragStartSpec.choiceWidthFactor(),
            dragStartSpec.choiceHeight(),
            dragStartSpec.choiceGap(),
            dragStartSpec.choiceTextXPadding()
        );
      }

      spec = next;
      suppressEvents = true;
      applySpecToControls(spec);
      suppressEvents = false;
      redraw();
      emitText();
    });
  }

  private DragTarget hitTest(double x, double y) {
    LayoutRects r = computeRects(spec, preview.getWidth(), preview.getHeight());
    if (r.nameBox().contains(x, y)) return DragTarget.NAME_BOX;
    if (r.choiceBlock().contains(x, y)) return DragTarget.CHOICE_BLOCK;
    if (r.textBox().contains(x, y)) return DragTarget.TEXT_BOX;
    return DragTarget.NONE;
  }

  private void redraw() {
    double w = Math.max(1, preview.getWidth());
    double h = Math.max(1, preview.getHeight());
    GraphicsContext g = preview.getGraphicsContext2D();

    g.setFill(LayoutStudioPalette.CANVAS_BACKGROUND_ALT);
    g.fillRect(0, 0, w, h);

    g.setStroke(LayoutStudioPalette.GRID_LINE);
    g.setLineWidth(1);
    for (int i = 1; i < 6; i++) {
      double yy = (h / 6.0) * i;
      g.strokeLine(0, yy, w, yy);
    }

    LayoutRects rects = computeRects(spec, w, h);
    ChoicePreviewStyle choiceStyle = resolveChoicePreviewStyle();

    // Choice block preview.
    double y = rects.choiceBlock().y();
    for (int i = 0; i < 3; i++) {
      boolean hovered = i == 0;
      boolean enabled = i != 2;
      Image buttonImage = enabled
          ? (hovered ? firstNonNull(choiceButtonHoverAssetImage, choiceButtonAssetImage) : choiceButtonAssetImage)
          : firstNonNull(choiceButtonDisabledAssetImage, choiceButtonAssetImage);
      if (buttonImage != null && buttonImage.getWidth() > 1 && buttonImage.getHeight() > 1) {
        g.drawImage(buttonImage, rects.choiceBlock().x(), y, rects.choiceBlock().w(), spec.choiceHeight());
      } else {
        Color fill = !enabled
            ? choiceStyle.disabledBackgroundColor()
            : (hovered ? choiceStyle.hoverBackgroundColor() : choiceStyle.backgroundColor());
        g.setFill(fill);
        g.fillRoundRect(
            rects.choiceBlock().x(),
            y,
            rects.choiceBlock().w(),
            spec.choiceHeight(),
            choiceStyle.cornerRadius(),
            choiceStyle.cornerRadius());
      }
      Color border = !enabled
          ? choiceStyle.disabledBorderColor()
          : (hovered ? choiceStyle.hoverBorderColor() : choiceStyle.borderColor());
      g.setStroke(border);
      g.setLineWidth(choiceStyle.borderWidth());
      g.strokeRoundRect(
          rects.choiceBlock().x(),
          y,
          rects.choiceBlock().w(),
          spec.choiceHeight(),
          choiceStyle.cornerRadius(),
          choiceStyle.cornerRadius());
      Color textColor = !enabled
          ? choiceStyle.disabledTextColor()
          : (hovered ? choiceStyle.hoverTextColor() : choiceStyle.textColor());
      g.setFill(textColor);
      g.setFont(Font.font("Arial", 14));
      g.fillText(
          "Choice " + (i + 1),
          rects.choiceBlock().x() + spec.choiceTextXPadding(),
          y + spec.choiceHeight() / 2 + choiceStyle.textBaselineOffset());
      y += spec.choiceHeight() + spec.choiceGap();
    }

    // Textbox and name box overlay.
    if (textBoxAssetImage != null && textBoxAssetImage.getWidth() > 1 && textBoxAssetImage.getHeight() > 1) {
      g.drawImage(textBoxAssetImage, rects.textBox().x(), rects.textBox().y(), rects.textBox().w(), rects.textBox().h());
      g.setFill(LayoutStudioPalette.DIALOGUE_ASSET_OVERLAY);
      g.fillRect(rects.textBox().x(), rects.textBox().y(), rects.textBox().w(), rects.textBox().h());
    } else {
      g.setFill(LayoutStudioPalette.DIALOGUE_OVERLAY);
      g.fillRect(rects.textBox().x(), rects.textBox().y(), rects.textBox().w(), rects.textBox().h());
    }
    g.setStroke(LayoutStudioPalette.ACCENT_BLUE);
    g.setLineWidth(2);
    g.strokeRect(rects.textBox().x(), rects.textBox().y(), rects.textBox().w(), rects.textBox().h());

    g.setFill(LayoutStudioPalette.DIALOGUE_NAME_FILL);
    g.fillRect(rects.nameBox().x(), rects.nameBox().y(), rects.nameBox().w(), rects.nameBox().h());
    g.setStroke(LayoutStudioPalette.PANEL_BORDER_LIGHT);
    g.strokeRect(rects.nameBox().x(), rects.nameBox().y(), rects.nameBox().w(), rects.nameBox().h());

    g.setFill(LayoutStudioPalette.TEXT_PRIMARY);
    g.setFont(Font.font("Arial", FontWeight.BOLD, 14));
    g.fillText("Speaker Name", rects.nameBox().x() + spec.nameTextXOffset(), rects.nameBox().y() + spec.nameTextBaselineOffset());

    // Dialogue text bounds.
    g.setStroke(LayoutStudioPalette.ACCENT_GOLD);
    g.setLineDashes(6);
    g.strokeRect(rects.dialogueBounds().x(), rects.dialogueBounds().y(), rects.dialogueBounds().w(), rects.dialogueBounds().h());
    g.setLineDashes(0);
    g.setFill(LayoutStudioPalette.TEXT_SECONDARY);
    g.setFont(Font.font("Arial", 13));
    g.fillText("Narrator: The GUI editor now controls dialogue bounds.", rects.dialogueBounds().x() + 8, rects.dialogueBounds().y() + 18);
    g.fillText("Drag the highlighted blocks or edit numeric fields.", rects.dialogueBounds().x() + 8, rects.dialogueBounds().y() + 36);

    // Labels
    drawTag(g, rects.textBox().x() + 6, rects.textBox().y() + 16, "Textbox");
    drawTag(g, rects.nameBox().x() + 6, rects.nameBox().y() - 4, "Name Box");
    drawTag(g, rects.choiceBlock().x() + 6, rects.choiceBlock().y() - 4, "Choices");
    drawTag(g, rects.dialogueBounds().x() + 6, rects.dialogueBounds().y() - 4, "Text Bounds");
  }

  private void drawTag(GraphicsContext g, double x, double y, String text) {
    double w = Math.max(54, text.length() * 7.2 + 12);
    g.setFill(LayoutStudioPalette.TAG_BG);
    g.fillRoundRect(x, y - 12, w, 16, 6, 6);
    g.setStroke(LayoutStudioPalette.TAG_BORDER);
    g.strokeRoundRect(x, y - 12, w, 16, 6, 6);
    g.setFill(LayoutStudioPalette.TAG_TEXT);
    g.setFont(Font.font("Arial", FontWeight.BOLD, 11));
    g.fillText(text, x + 6, y);
  }

  private LayoutRects computeRects(VnUiLayoutSpec s, double w, double h) {
    double tbX = clamp(s.textBoxX() * w, 0, w);
    double tbY = clamp(s.textBoxY() * h, 0, h);
    double tbW = clamp(s.textBoxWidth() * w, 1, Math.max(1, w - tbX));
    double tbH = clamp(s.textBoxHeight() * h, 1, Math.max(1, h - tbY));

    double nbX = tbX + s.nameBoxXOffset();
    double nbY = tbY + s.nameBoxYOffset();
    double nbW = s.nameBoxWidth();
    double nbH = s.nameBoxHeight();

    double textX = tbX + s.dialogueTextHorizontalPadding();
    double textY = tbY + s.dialogueTextTopPadding();
    double textW = Math.max(40, tbW - s.dialogueTextHorizontalPadding() * 2);
    double textH = Math.max(20, tbH - s.dialogueTextTopPadding() - 10);

    double choiceW = clamp(w * s.choiceWidthFactor(), 20, w);
    double choiceX = clamp(w * s.choiceXCenter() - choiceW / 2, 0, Math.max(0, w - choiceW));
    double totalChoiceH = 3 * s.choiceHeight() + 2 * s.choiceGap();
    double choiceStartY = resolveChoiceYStart(s, h, 3);
    choiceStartY = clamp(choiceStartY, 0, Math.max(0, h - totalChoiceH));

    return new LayoutRects(
        new Rect(tbX, tbY, tbW, tbH),
        new Rect(nbX, nbY, nbW, nbH),
        new Rect(textX, textY, textW, textH),
        new Rect(choiceX, choiceStartY, choiceW, totalChoiceH)
    );
  }

  private double resolveChoiceYStart(VnUiLayoutSpec s, double h, int count) {
    double total = count * s.choiceHeight() + Math.max(0, count - 1) * s.choiceGap();
    if (s.choiceYStart() < 0) return (h - total) / 2.0;
    return h * s.choiceYStart();
  }

  private VnUiLayoutSpec readSpecFromControls() {
    return new VnUiLayoutSpec(
        value(spTextBoxX),
        value(spTextBoxY),
        value(spTextBoxWidth),
        value(spTextBoxHeight),
        value(spTextBoxPadding),
        value(spNameBoxXOffset),
        value(spNameBoxYOffset),
        value(spNameBoxWidth),
        value(spNameBoxHeight),
        value(spNameTextXOffset),
        value(spNameTextBaselineOffset),
        value(spDialoguePaddingX),
        value(spDialoguePaddingTop),
        value(spChoiceXCenter),
        value(spChoiceYStart),
        value(spChoiceWidthFactor),
        value(spChoiceHeight),
        value(spChoiceGap),
        value(spChoiceTextXPadding)
    );
  }

  private void applySpecToControls(VnUiLayoutSpec s) {
    setValue(spTextBoxX, s.textBoxX());
    setValue(spTextBoxY, s.textBoxY());
    setValue(spTextBoxWidth, s.textBoxWidth());
    setValue(spTextBoxHeight, s.textBoxHeight());
    setValue(spTextBoxPadding, s.textBoxPadding());
    setValue(spNameBoxXOffset, s.nameBoxXOffset());
    setValue(spNameBoxYOffset, s.nameBoxYOffset());
    setValue(spNameBoxWidth, s.nameBoxWidth());
    setValue(spNameBoxHeight, s.nameBoxHeight());
    setValue(spNameTextXOffset, s.nameTextXOffset());
    setValue(spNameTextBaselineOffset, s.nameTextBaselineOffset());
    setValue(spDialoguePaddingX, s.dialogueTextHorizontalPadding());
    setValue(spDialoguePaddingTop, s.dialogueTextTopPadding());
    setValue(spChoiceXCenter, s.choiceXCenter());
    setValue(spChoiceYStart, s.choiceYStart());
    setValue(spChoiceWidthFactor, s.choiceWidthFactor());
    setValue(spChoiceHeight, s.choiceHeight());
    setValue(spChoiceGap, s.choiceGap());
    setValue(spChoiceTextXPadding, s.choiceTextXPadding());
  }

  private VnUiStyleSpec readStyleFromControls() {
    return new VnUiStyleSpec(
        normalizeAssetPath(tfTextBoxAsset.getText()),
        normalizeAssetPath(tfChoiceButtonAsset.getText()),
        normalizeAssetPath(tfChoiceButtonHoverAsset.getText()),
        normalizeAssetPath(tfChoiceButtonSelectedAsset.getText()),
        normalizeAssetPath(tfChoiceButtonDisabledAsset.getText()),
        normalizeColorValue(tfChoiceBgColor.getText()),
        normalizeColorValue(tfChoiceHoverColor.getText()),
        normalizeColorValue(tfChoiceSelectedColor.getText()),
        normalizeColorValue(tfChoiceDisabledColor.getText()),
        normalizeColorValue(tfChoiceTextColor.getText()),
        normalizeColorValue(tfChoiceHoverTextColor.getText()),
        normalizeColorValue(tfChoiceSelectedTextColor.getText()),
        normalizeColorValue(tfChoiceDisabledTextColor.getText()),
        normalizeColorValue(tfChoiceBorderColor.getText()),
        normalizeColorValue(tfChoiceHoverBorderColor.getText()),
        normalizeColorValue(tfChoiceSelectedBorderColor.getText()),
        normalizeColorValue(tfChoiceDisabledBorderColor.getText()),
        value(spChoiceCornerRadius),
        value(spChoiceBorderWidth),
        value(spChoiceTextBaselineOffset)
    );
  }

  private void applyStyleToControls(VnUiStyleSpec s) {
    tfTextBoxAsset.setText(normalizeAssetPath(s.textBoxAssetPath()));
    tfChoiceButtonAsset.setText(normalizeAssetPath(s.choiceButtonAssetPath()));
    tfChoiceButtonHoverAsset.setText(normalizeAssetPath(s.choiceButtonHoverAssetPath()));
    tfChoiceButtonSelectedAsset.setText(normalizeAssetPath(s.choiceButtonSelectedAssetPath()));
    tfChoiceButtonDisabledAsset.setText(normalizeAssetPath(s.choiceButtonDisabledAssetPath()));

    tfChoiceBgColor.setText(normalizeColorValue(s.choiceBackgroundColor()));
    tfChoiceHoverColor.setText(normalizeColorValue(s.choiceHoverColor()));
    tfChoiceSelectedColor.setText(normalizeColorValue(s.choiceSelectedColor()));
    tfChoiceDisabledColor.setText(normalizeColorValue(s.choiceDisabledColor()));
    tfChoiceTextColor.setText(normalizeColorValue(s.choiceTextColor()));
    tfChoiceHoverTextColor.setText(normalizeColorValue(s.choiceHoverTextColor()));
    tfChoiceSelectedTextColor.setText(normalizeColorValue(s.choiceSelectedTextColor()));
    tfChoiceDisabledTextColor.setText(normalizeColorValue(s.choiceDisabledTextColor()));
    tfChoiceBorderColor.setText(normalizeColorValue(s.choiceBorderColor()));
    tfChoiceHoverBorderColor.setText(normalizeColorValue(s.choiceHoverBorderColor()));
    tfChoiceSelectedBorderColor.setText(normalizeColorValue(s.choiceSelectedBorderColor()));
    tfChoiceDisabledBorderColor.setText(normalizeColorValue(s.choiceDisabledBorderColor()));

    setValue(spChoiceCornerRadius, s.choiceCornerRadius());
    setValue(spChoiceBorderWidth, s.choiceBorderWidth());
    setValue(spChoiceTextBaselineOffset, s.choiceTextBaselineOffset());
  }

  private void emitText() {
    if (onLayoutTextChanged == null) return;
    String text = serialize(spec, style, rawProperties);
    String normalized = normalizeText(text);
    if (normalized.equals(lastEmittedText)) return;
    lastEmittedText = normalized;
    onLayoutTextChanged.accept(text);
  }

  private static String serialize(VnUiLayoutSpec spec, VnUiStyleSpec style, Properties base) {
    Properties merged = new Properties();
    if (base != null) {
      for (String key : base.stringPropertyNames()) merged.setProperty(key, base.getProperty(key));
    }
    Properties generated = VnUiLayoutLoader.toProperties(spec, style);
    for (String key : generated.stringPropertyNames()) {
      merged.setProperty(key, generated.getProperty(key));
    }

    StringBuilder out = new StringBuilder();
    out.append("# Dialogue UI layout").append(System.lineSeparator());
    out.append("# choiceYStart: -1 = auto-center").append(System.lineSeparator());
    for (String key : KNOWN_KEYS) {
      String value = merged.getProperty(key);
      if (value == null) continue;
      out.append(key).append("=").append(value).append(System.lineSeparator());
    }
    List<String> extras = new ArrayList<>();
    for (String key : merged.stringPropertyNames()) {
      if (isKnownKey(key)) continue;
      extras.add(key);
    }
    extras.sort(String::compareTo);
    if (!extras.isEmpty()) {
      out.append(System.lineSeparator()).append("# Additional custom keys").append(System.lineSeparator());
      for (String key : extras) {
        out.append(key).append("=").append(merged.getProperty(key, "")).append(System.lineSeparator());
      }
    }
    return out.toString();
  }

  private static boolean isKnownKey(String key) {
    for (String known : KNOWN_KEYS) {
      if (known.equals(key)) return true;
    }
    return false;
  }

  private static Spinner<Double> spinner(double min, double max, double initial, double step) {
    Spinner<Double> spinner = new Spinner<>();
    SpinnerValueFactory.DoubleSpinnerValueFactory vf =
        new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, initial, step);
    vf.setConverter(new javafx.util.StringConverter<>() {
      @Override
      public String toString(Double value) {
        if (value == null) return "";
        if (Math.rint(value) == value) return Long.toString(Math.round(value));
        return String.format(Locale.ROOT, "%.4f", value)
            .replaceAll("0+$", "")
            .replaceAll("\\.$", "");
      }

      @Override
      public Double fromString(String string) {
        if (string == null || string.isBlank()) return vf.getValue();
        try {
          return Double.parseDouble(string.trim());
        } catch (Exception ignored) {
          return vf.getValue();
        }
      }
    });
    spinner.setValueFactory(vf);
    spinner.setEditable(true);
    return spinner;
  }

  private static double value(Spinner<Double> spinner) {
    Double v = spinner.getValue();
    return v == null ? 0 : v;
  }

  private static void setValue(Spinner<Double> spinner, double value) {
    SpinnerValueFactory<Double> vf = spinner.getValueFactory();
    if (vf instanceof SpinnerValueFactory.DoubleSpinnerValueFactory dsvf) {
      dsvf.setValue(value);
    } else {
      vf.setValue(value);
    }
  }

  private static double clamp(double v, double min, double max) {
    if (Double.isNaN(v) || Double.isInfinite(v)) return min;
    if (v < min) return min;
    if (v > max) return max;
    return v;
  }

  private static double clamp01(double v) {
    return clamp(v, 0, 1);
  }

  private ChoicePreviewStyle resolveChoicePreviewStyle() {
    Color bg = parseColorValue(style.choiceBackgroundColor(), LayoutStudioPalette.PANEL_FILL);
    Color hoverBg = parseColorValue(
        firstNonBlank(style.choiceHoverColor(), style.choiceSelectedColor()),
        LayoutStudioPalette.PANEL_FILL_SELECTED);
    Color disabledBg = parseColorValue(style.choiceDisabledColor(), LayoutStudioPalette.PANEL_FILL_DISABLED);

    Color text = parseColorValue(style.choiceTextColor(), LayoutStudioPalette.TEXT_PRIMARY);
    Color hoverText = parseColorValue(
        firstNonBlank(style.choiceHoverTextColor(), style.choiceSelectedTextColor()),
        text);
    Color disabledText = parseColorValue(style.choiceDisabledTextColor(), LayoutStudioPalette.TEXT_DISABLED);

    Color border = parseColorValue(style.choiceBorderColor(), LayoutStudioPalette.PANEL_BORDER_LIGHT);
    Color hoverBorder = parseColorValue(
        firstNonBlank(style.choiceHoverBorderColor(), style.choiceSelectedBorderColor()),
        border);
    Color disabledBorder = parseColorValue(style.choiceDisabledBorderColor(), LayoutStudioPalette.PANEL_BORDER);

    double cornerRadius = clamp(style.choiceCornerRadius(), 0.0, 96.0);
    double borderWidth = clamp(style.choiceBorderWidth(), 0.0, 12.0);
    double textBaselineOffset = clamp(style.choiceTextBaselineOffset(), -120.0, 120.0);

    return new ChoicePreviewStyle(
        bg,
        hoverBg,
        disabledBg,
        text,
        hoverText,
        disabledText,
        border,
        hoverBorder,
        disabledBorder,
        cornerRadius,
        borderWidth,
        textBaselineOffset
    );
  }

  private Color parseColorValue(String raw, Color fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      return Color.web(raw.trim());
    } catch (Exception ignored) {
      return fallback;
    }
  }

  private static <T> T firstNonNull(T first, T second) {
    return first != null ? first : second;
  }

  private void loadTextBoxAssetImage() {
    textBoxAssetImage = loadImageAsset(style.textBoxAssetPath());
  }

  private void loadChoiceAssetImages() {
    choiceButtonAssetImage = loadImageAsset(style.choiceButtonAssetPath());
    choiceButtonHoverAssetImage = loadImageAsset(
        firstNonBlank(style.choiceButtonHoverAssetPath(), style.choiceButtonSelectedAssetPath()));
    choiceButtonDisabledAssetImage = loadImageAsset(style.choiceButtonDisabledAssetPath());
  }

  private Image loadImageAsset(String assetPath) {
    String normalized = normalizeAssetPath(assetPath);
    if (normalized.isBlank()) return null;
    Image cached = previewAssetCache.get(normalized);
    if (cached != null) return cached;
    File assetFile = resolveAssetFile(normalized);
    if (assetFile == null || !assetFile.exists() || !assetFile.isFile()) {
      previewAssetCache.remove(normalized);
      return null;
    }
    try {
      String url = assetFile.toURI().toURL().toExternalForm();
      Image image = new Image(url, false);
      if (image.isError()) {
        previewAssetCache.remove(normalized);
        return null;
      }
      previewAssetCache.put(normalized, image);
      return image;
    } catch (Exception ignored) {
      previewAssetCache.remove(normalized);
      return null;
    }
  }

  private File resolveInitialAssetDirectory() {
    if (projectRoot != null) {
      File uiDir = new File(projectRoot, "assets/ui");
      if (uiDir.exists() && uiDir.isDirectory()) return uiDir;
      if (projectRoot.exists() && projectRoot.isDirectory()) return projectRoot;
    }
    return new File(System.getProperty("user.home", "."));
  }

  private File resolveAssetFile(String path) {
    String normalized = normalizeAssetPath(path);
    if (normalized.isBlank()) return null;

    File direct = new File(normalized);
    if (direct.isAbsolute()) return direct;

    if (projectRoot != null) {
      File fromRoot = new File(projectRoot, normalized);
      if (fromRoot.exists()) return fromRoot;
    }
    return direct;
  }

  private String toProjectRelativePath(File file) {
    if (file == null) return "";
    if (projectRoot == null) {
      return file.getAbsolutePath().replace('\\', '/');
    }
    try {
      Path root = projectRoot.toPath().toAbsolutePath().normalize();
      Path abs = file.toPath().toAbsolutePath().normalize();
      if (abs.startsWith(root)) {
        return root.relativize(abs).toString().replace('\\', '/');
      }
    } catch (Exception ignored) {
      // Fall through to absolute path.
    }
    return file.getAbsolutePath().replace('\\', '/');
  }

  private static String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) return first;
    if (second != null && !second.isBlank()) return second;
    return "";
  }

  private static String normalizeAssetPath(String value) {
    if (value == null) return "";
    return value.trim().replace('\\', '/');
  }

  private static String normalizeColorValue(String value) {
    if (value == null) return "";
    return value.trim();
  }

  private static String normalizeText(String text) {
    if (text == null) return "";
    return text.replace("\r\n", "\n").replace('\r', '\n');
  }

  private void updatePreviewSize(StackPane previewPane) {
    if (previewPane == null) return;
    double w = sanitizeCanvasDimension(previewPane.getWidth() - PREVIEW_PADDING * 2.0);
    double h = sanitizeCanvasDimension(previewPane.getHeight() - PREVIEW_PADDING * 2.0);
    if (Math.abs(preview.getWidth() - w) >= 0.5) preview.setWidth(w);
    if (Math.abs(preview.getHeight() - h) >= 0.5) preview.setHeight(h);
    if (Math.abs(preview.getLayoutX() - PREVIEW_PADDING) >= 0.5) preview.setLayoutX(PREVIEW_PADDING);
    if (Math.abs(preview.getLayoutY() - PREVIEW_PADDING) >= 0.5) preview.setLayoutY(PREVIEW_PADDING);
  }

  private static double sanitizeCanvasDimension(double value) {
    if (!Double.isFinite(value)) return 1.0;
    return clamp(value, 1.0, 8192.0);
  }

  private record Rect(double x, double y, double w, double h) {
    boolean contains(double px, double py) {
      return px >= x && px <= x + w && py >= y && py <= y + h;
    }
  }

  private record ChoicePreviewStyle(
      Color backgroundColor,
      Color hoverBackgroundColor,
      Color disabledBackgroundColor,
      Color textColor,
      Color hoverTextColor,
      Color disabledTextColor,
      Color borderColor,
      Color hoverBorderColor,
      Color disabledBorderColor,
      double cornerRadius,
      double borderWidth,
      double textBaselineOffset
  ) {}

  private record LayoutRects(Rect textBox, Rect nameBox, Rect dialogueBounds, Rect choiceBlock) {}
}
