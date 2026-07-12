package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;
import com.jvn.editor.ui.EditorTheme;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Floating non-modal window exposing the EasingCurveEditor with a full preset
 * catalogue sidebar. Opened from the timeline context menu or by double-clicking
 * an existing keyframe. Calls the onApply callback when the user commits.
 */
public class EasingCurveDialog extends Stage {

    // Preset groups shown in the left sidebar
    private record PresetEntry(Easing.Type type, String label) {}
    private record PresetGroup(String header, List<PresetEntry> entries) {}

    private static final List<PresetGroup> GROUPS = List.of(
        new PresetGroup("Standard", List.of(
            new PresetEntry(Easing.Type.LINEAR, "Linear")
        )),
        new PresetGroup("Ease In", List.of(
            new PresetEntry(Easing.Type.EASE_IN_QUAD,    "Quad"),
            new PresetEntry(Easing.Type.EASE_IN_CUBIC,   "Cubic"),
            new PresetEntry(Easing.Type.EASE_IN_QUART,   "Quart"),
            new PresetEntry(Easing.Type.EASE_IN_QUINT,   "Quint"),
            new PresetEntry(Easing.Type.EASE_IN_SINE,    "Sine"),
            new PresetEntry(Easing.Type.EASE_IN_CIRC,    "Circ"),
            new PresetEntry(Easing.Type.EASE_IN_EXPO,    "Expo"),
            new PresetEntry(Easing.Type.EASE_IN_ELASTIC, "Elastic"),
            new PresetEntry(Easing.Type.EASE_IN_BACK,    "Back"),
            new PresetEntry(Easing.Type.EASE_IN_BOUNCE,  "Bounce")
        )),
        new PresetGroup("Ease Out", List.of(
            new PresetEntry(Easing.Type.EASE_OUT_QUAD,    "Quad"),
            new PresetEntry(Easing.Type.EASE_OUT_CUBIC,   "Cubic"),
            new PresetEntry(Easing.Type.EASE_OUT_QUART,   "Quart"),
            new PresetEntry(Easing.Type.EASE_OUT_QUINT,   "Quint"),
            new PresetEntry(Easing.Type.EASE_OUT_SINE,    "Sine"),
            new PresetEntry(Easing.Type.EASE_OUT_CIRC,    "Circ"),
            new PresetEntry(Easing.Type.EASE_OUT_EXPO,    "Expo"),
            new PresetEntry(Easing.Type.EASE_OUT_ELASTIC, "Elastic"),
            new PresetEntry(Easing.Type.EASE_OUT_BACK,    "Back"),
            new PresetEntry(Easing.Type.EASE_OUT_BOUNCE,  "Bounce")
        )),
        new PresetGroup("Ease In-Out", List.of(
            new PresetEntry(Easing.Type.EASE_IN_OUT_QUAD,    "Quad"),
            new PresetEntry(Easing.Type.EASE_IN_OUT_CUBIC,   "Cubic"),
            new PresetEntry(Easing.Type.EASE_IN_OUT_QUART,   "Quart"),
            new PresetEntry(Easing.Type.EASE_IN_OUT_QUINT,   "Quint"),
            new PresetEntry(Easing.Type.EASE_IN_OUT_SINE,    "Sine"),
            new PresetEntry(Easing.Type.EASE_IN_OUT_CIRC,    "Circ"),
            new PresetEntry(Easing.Type.EASE_IN_OUT_EXPO,    "Expo"),
            new PresetEntry(Easing.Type.EASE_IN_OUT_ELASTIC, "Elastic"),
            new PresetEntry(Easing.Type.EASE_IN_OUT_BACK,    "Back"),
            new PresetEntry(Easing.Type.EASE_IN_OUT_BOUNCE,  "Bounce")
        )),
        new PresetGroup("Physics", List.of(
            new PresetEntry(Easing.Type.SPRING,        "Spring"),
            new PresetEntry(Easing.Type.DAMPED_SPRING, "Damped Spring")
        )),
        new PresetGroup("Named", List.of(
            new PresetEntry(Easing.Type.HERO_POP,     "Hero Pop"),
            new PresetEntry(Easing.Type.UI_SOFT_IN,   "UI Soft In"),
            new PresetEntry(Easing.Type.CAMERA_GLIDE, "Camera Glide")
        )),
        new PresetGroup("Custom", List.of(
            new PresetEntry(Easing.Type.CUSTOM, "Custom Bezier"),
            new PresetEntry(Easing.Type.CURVE,  "Multi-Point Curve")
        ))
    );

    // Colours
    private static final String BG_DARK   = "#111111";
    private static final String BG_PANEL  = "#181818";
    private static final String BG_SEL    = "#1e3450";
    private static final String BORDER    = "#282828";
    private static final String TEXT      = "#d8d8d8";
    private static final String DIM       = "#585858";
    private static final String ACCENT    = "#4da3ff";
    private static final String APPLY_BG  = "#1d3a25";
    private static final String APPLY_FG  = "#58d68d";
    private static final String APPLY_BD  = "#2e5a38";
    private static final String DIALOG_CSS = "/com/jvn/editor/ui/actioneditor/easing-curve-dialog.css";

    private final EasingCurveEditor curveEditor = new EasingCurveEditor();
    private final Map<Easing.Type, Pane> presetCells = new LinkedHashMap<>();
    private final VBox presetListBox = new VBox(0);
    private final TextField presetFilter = new TextField();
    private final TextField specField = new TextField();
    private final Label statusLabel = new Label();
    private final Label typeSummaryLabel = new Label();
    private final Label pointsSummaryLabel = new Label();
    private final Label rangeSummaryLabel = new Label();
    private final Label durationSummaryLabel = new Label();
    private Easing.Interpolation selectedInterp;
    private final List<Button> interpButtons = new ArrayList<>();
    private final List<Button> durationButtons = new ArrayList<>();
    private final BiConsumer<EasingSpec, Easing.Interpolation> onApply;
    private EasingSpec baselineSpec = EasingSpec.of(Easing.Type.LINEAR);
    private Easing.Type highlightedType = Easing.Type.LINEAR;
    private boolean updatingSpecField = false;
    private double previewDurationMs = 1000.0;
    private Button previewButton = new Button("▶  Preview");

    /**
     * @param owner          owner window (for positioning); may be null
     * @param initialSpec    easing spec to display initially
     * @param initialInterp  interpolation mode to display initially
     * @param contextTitle   short string shown in the header (e.g. "X / 0ms")
     * @param onApply        called with the chosen spec+interp when Apply is clicked
     */
    public EasingCurveDialog(Window owner,
                              EasingSpec initialSpec,
                              Easing.Interpolation initialInterp,
                              String contextTitle,
                              BiConsumer<EasingSpec, Easing.Interpolation> onApply) {
        this.onApply = onApply;
        this.selectedInterp = initialInterp != null ? initialInterp : Easing.Interpolation.TWEEN;

        initOwner(owner);
        initStyle(StageStyle.DECORATED);
        setTitle("Easing Curve Editor");
        setResizable(true);
        setMinWidth(900);
        setMinHeight(600);

        VBox root = new VBox();
        root.getStyleClass().add("easing-curve-dialog-root");
        root.setStyle("-fx-background-color: " + BG_DARK + ";");

        root.getChildren().addAll(
            buildHeader(contextTitle),
            buildBody(),
            buildFooter()
        );
        VBox.setVgrow(root.getChildren().get(1), Priority.ALWAYS);

        Scene scene = new Scene(root, 980, 640);
        scene.setFill(Color.web(BG_DARK));
        EditorTheme.apply(scene);
        var dialogCss = EasingCurveDialog.class.getResource(DIALOG_CSS);
        if (dialogCss != null) scene.getStylesheets().add(dialogCss.toExternalForm());
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) close();
            if (e.getCode() == KeyCode.ENTER && (e.isControlDown() || e.isShortcutDown())) commitAndClose();
        });
        setScene(scene);

        // Load initial state
        EasingSpec spec = initialSpec != null ? initialSpec : EasingSpec.of(Easing.Type.LINEAR);
        baselineSpec = spec;
        curveEditor.setInterpolation(selectedInterp);
        curveEditor.setEasingSpec(spec);
        curveEditor.setAnimDurationMs(previewDurationMs);
        curveEditor.setExpanded(true);
        curveEditor.setHelperText("Drag editable points. Shift snaps. Double-click adds/removes points.");
        curveEditor.setOnCurveSpecChanged(changed -> {
            syncSpecField(changed);
            highlightPreset(changed != null ? changed.getType() : Easing.Type.LINEAR);
            refreshSummary();
            setStatus("Curve updated.", false);
        });

        highlightPreset(spec.getType());
        refreshInterpButtons();
        refreshDurationButtons();
        syncSpecField(spec);
        refreshSummary();
        setOnHidden(e -> curveEditor.stopAnimation());
    }

    // -------------------------------------------------------------------------
    // UI construction
    // -------------------------------------------------------------------------

    private HBox buildHeader(String contextTitle) {
        Label title = new Label("Easing Curve");
        title.setStyle("-fx-text-fill: " + TEXT + "; -fx-font-size: 13px; -fx-font-weight: bold;");

        Label ctx = new Label(contextTitle != null && !contextTitle.isBlank() ? "  ·  " + contextTitle : "");
        ctx.setStyle("-fx-text-fill: " + DIM + "; -fx-font-size: 11px;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(4, title, ctx, spacer);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setStyle("-fx-background-color: " + BG_PANEL + "; -fx-border-color: " + BORDER + "; " +
                     "-fx-border-width: 0 0 1 0;");
        return row;
    }

    private HBox buildBody() {
        HBox body = new HBox();
        body.setStyle("-fx-background-color: " + BG_DARK + ";");
        VBox.setVgrow(body, Priority.ALWAYS);

        body.getChildren().addAll(buildPresetPanel(), buildEditorPanel());
        return body;
    }

    private VBox buildPresetPanel() {
        VBox panel = new VBox();
        panel.setPrefWidth(210);
        panel.setMinWidth(190);
        panel.setMaxWidth(240);
        panel.setStyle("-fx-background-color: " + BG_PANEL + "; -fx-border-color: " + BORDER + "; " +
                       "-fx-border-width: 0 1 0 0;");

        Label header = new Label("PRESETS");
        header.setStyle("-fx-text-fill: " + DIM + "; -fx-font-size: 9px; -fx-font-weight: bold; " +
                        "-fx-padding: 9 10 4 10;");

        presetFilter.setPromptText("Filter presets");
        presetFilter.setStyle(fieldStyle());
        presetFilter.setTooltip(new Tooltip("Filter built-in easing presets by name or group"));
        presetFilter.textProperty().addListener((obs, oldValue, newValue) -> refreshPresetList());

        presetListBox.setStyle("-fx-background-color: " + BG_PANEL + ";");
        presetListBox.setFillWidth(true);
        refreshPresetList();

        ScrollPane scroll = new ScrollPane(presetListBox);
        scroll.getStyleClass().add("easing-preset-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: " + BG_PANEL + "; -fx-background-color: " + BG_PANEL + "; " +
                        "-fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox filterBox = new VBox(presetFilter);
        filterBox.setPadding(new Insets(4, 8, 6, 8));

        panel.getChildren().addAll(header, filterBox, scroll);
        return panel;
    }

    private Pane buildPresetCell(PresetEntry entry) {
        Label name = new Label(entry.label());
        name.setStyle("-fx-text-fill: " + TEXT + "; -fx-font-size: 11px;");
        name.setMouseTransparent(true);

        HBox cell = new HBox(name);
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.setPadding(new Insets(5, 10, 5, 14));
        cell.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");

        cell.setOnMouseEntered(e -> {
            if (highlightedType != entry.type())
                cell.setStyle("-fx-background-color: #1a2535; -fx-cursor: hand;");
        });
        cell.setOnMouseExited(e -> {
            if (highlightedType != entry.type())
                cell.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
        });
        cell.setOnMouseClicked(e -> applyPreset(entry.type()));
        return cell;
    }

    private VBox buildEditorPanel() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(10, 12, 8, 12));
        panel.setStyle("-fx-background-color: " + BG_DARK + ";");
        VBox.setVgrow(curveEditor, Priority.ALWAYS);
        HBox.setHgrow(panel, Priority.ALWAYS);
        panel.setFillWidth(true);
        panel.getChildren().addAll(
            buildSpecRow(),
            buildSummaryRow(),
            curveEditor,
            buildQuickShapeRow(),
            buildToolRow(),
            buildInterpRow()
        );
        return panel;
    }

    private HBox buildSpecRow() {
        Label lbl = new Label("Spec:");
        lbl.setStyle("-fx-text-fill: " + DIM + "; -fx-font-size: 10px;");

        specField.setPromptText("linear, ease_out_quart, cubic_bezier(...), curve(...)");
        specField.setStyle(fieldStyle());
        specField.setTooltip(new Tooltip("Paste or edit an easing token, cubic_bezier(...), curve(...), spring(...), or damped_spring(...)."));
        specField.setOnAction(e -> applySpecField());

        Button apply = new Button("Apply");
        apply.setStyle(btnStyle("#202020", "#d8d8d8", "#303030"));
        apply.setTooltip(new Tooltip("Apply the spec text to this editor"));
        apply.setOnAction(e -> applySpecField());

        Button copy = new Button("Copy");
        copy.setStyle(btnStyle("#202020", "#c8c8c8", "#303030"));
        copy.setTooltip(new Tooltip("Copy the current easing spec"));
        copy.setOnAction(e -> copySpecToClipboard());

        Button paste = new Button("Paste");
        paste.setStyle(btnStyle("#202020", "#c8c8c8", "#303030"));
        paste.setTooltip(new Tooltip("Paste an easing spec from the clipboard"));
        paste.setOnAction(e -> pasteSpecFromClipboard());

        HBox row = new HBox(8, lbl, specField, apply, copy, paste);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(specField, Priority.ALWAYS);
        return row;
    }

    private HBox buildSummaryRow() {
        HBox row = new HBox(8,
            summaryChip("Type", typeSummaryLabel),
            summaryChip("Points", pointsSummaryLabel),
            summaryChip("Range", rangeSummaryLabel),
            summaryChip("Preview", durationSummaryLabel)
        );
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox summaryChip(String title, Label value) {
        Label label = new Label(title.toUpperCase(Locale.ROOT));
        label.setStyle("-fx-text-fill: #707070; -fx-font-size: 9px; -fx-font-weight: bold;");
        value.setStyle("-fx-text-fill: " + TEXT + "; -fx-font-size: 11px;");

        VBox chip = new VBox(2, label, value);
        chip.setMinWidth(96);
        chip.setPadding(new Insets(6, 9, 6, 9));
        chip.setStyle("-fx-background-color: #151515; -fx-border-color: #272727; " +
                      "-fx-background-radius: 6; -fx-border-radius: 6;");
        return chip;
    }

    private FlowPane buildQuickShapeRow() {
        Label lbl = new Label("Shape:");
        lbl.setStyle("-fx-text-fill: " + DIM + "; -fx-font-size: 10px;");

        Button bezier = toolButton("Bezier", "Convert the visible curve to editable cubic Bezier handles", () -> {
            EasingSpec editable = KeyframeEditor.toEditableCurveSpec(curveEditor.getEditedSpec());
            applyEditorSpec(editable, editable.getType(), "Converted to editable Bezier.");
        });
        Button multi = toolButton("Multi-Point", "Convert the visible curve to editable sampled points", () -> {
            EasingSpec editable = KeyframeEditor.toMultiPointCurveSpec(curveEditor.getEditedSpec());
            applyEditorSpec(editable, editable.getType(), "Converted to multi-point curve.");
        });
        Button smooth = toolButton("Smooth S", "Use a balanced ease-in-out cubic curve", () ->
            applyEditorSpec(Easing.namedCurveSpec(Easing.Type.EASE_IN_OUT_CUBIC), Easing.Type.EASE_IN_OUT_CUBIC, "Shape: Smooth S."));
        Button overshoot = toolButton("Overshoot", "Use an ease-out back curve with a small overshoot", () ->
            applyEditorSpec(Easing.namedCurveSpec(Easing.Type.EASE_OUT_BACK), Easing.Type.EASE_OUT_BACK, "Shape: Overshoot."));
        Button spring = toolButton("Spring", "Use a spring curve", () ->
            applyEditorSpec(Easing.namedCurveSpec(Easing.Type.SPRING), Easing.Type.SPRING, "Shape: Spring."));
        Button pop = toolButton("Hero Pop", "Use the named hero pop curve", () ->
            applyEditorSpec(Easing.namedCurveSpec(Easing.Type.HERO_POP), Easing.Type.HERO_POP, "Shape: Hero Pop."));

        FlowPane row = new FlowPane(6, 6, lbl, bezier, multi, smooth, overshoot, spring, pop);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private FlowPane buildToolRow() {
        Button editable = toolButton("Make Editable", "Convert the visible curve to editable points", () -> {
            applyEditorSpec(KeyframeEditor.toMultiPointCurveSpec(curveEditor.getEditedSpec()), Easing.Type.CURVE, "Converted to editable curve.");
        });
        Button addPoint = toolButton("Add Point", "Add a point near the current cursor or selected point", () -> {
            ensureMultiPointCurve();
            if (curveEditor.addCurvePointAtCurrentFocus()) {
                syncSpecField(curveEditor.getEditedSpec());
                highlightPreset(Easing.Type.CURVE);
                setStatus("Point added.", false);
            }
        });
        Button removePoint = toolButton("Remove Point", "Remove the selected curve point", () -> {
            if (curveEditor.removeSelectedCurvePoint()) {
                syncSpecField(curveEditor.getEditedSpec());
                setStatus("Point removed.", false);
            } else {
                setStatus("Select a removable curve point first.", true);
            }
        });
        Button reverse = toolButton("Reverse", "Reverse the curve in time", () -> {
            EasingSpec reversed = KeyframeEditor.reverseEditableCurveSpec(curveEditor.getEditedSpec());
            applyEditorSpec(reversed, reversed.getType(), "Curve reversed.");
        });
        Button clamp = toolButton("Clamp Y", "Clamp curve output into 0..1", () -> {
            EasingSpec clamped = KeyframeEditor.clampEditableCurveSpec(curveEditor.getEditedSpec());
            applyEditorSpec(clamped, clamped.getType(), "Curve clamped.");
        });
        Button flatten = toolButton("Flatten", "Return the editor to a linear curve", () ->
            applyEditorSpec(EasingSpec.of(Easing.Type.LINEAR), Easing.Type.LINEAR, "Flattened to linear."));
        Button reset = toolButton("Reset", "Return to the curve opened in this dialog", () ->
            applyEditorSpec(baselineSpec, baselineSpec.getType(), "Reset to opened curve."));

        FlowPane row = new FlowPane(6, 6, editable, addPoint, removePoint, reverse, clamp, flatten, reset);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox buildInterpRow() {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);

        Label lbl = new Label("Mode:");
        lbl.setStyle("-fx-text-fill: " + DIM + "; -fx-font-size: 10px;");

        interpButtons.clear();
        for (Easing.Interpolation interp : Easing.Interpolation.values()) {
            String name = switch (interp) {
                case TWEEN -> "Tween";
                case HOLD  -> "Hold";
                case STEP  -> "Step";
            };
            Button btn = new Button(name);
            btn.setOnAction(e -> {
                selectedInterp = interp;
                curveEditor.setInterpolation(interp);
                refreshInterpButtons();
                refreshSummary();
            });
            interpButtons.add(btn);
            row.getChildren().add(btn);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label durationLabel = new Label("Preview:");
        durationLabel.setStyle("-fx-text-fill: " + DIM + "; -fx-font-size: 10px;");
        row.getChildren().add(spacer);
        row.getChildren().add(durationLabel);
        durationButtons.clear();
        addDurationButton(row, "0.5s", 500.0);
        addDurationButton(row, "1s", 1000.0);
        addDurationButton(row, "2s", 2000.0);

        row.getChildren().add(0, lbl);
        return row;
    }

    private HBox buildFooter() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        previewButton.setText("▶  Preview");
        previewButton.setStyle(btnStyle("#252525", "#c8c8c8", "#3d3d3d"));
        previewButton.setOnAction(e -> {
            curveEditor.toggleAnimation();
            updatePreviewButton();
        });

        Button btnApply = new Button("Apply to Selection");
        btnApply.setStyle(btnStyle(APPLY_BG, APPLY_FG, APPLY_BD) + " -fx-font-weight: bold;");
        btnApply.setDefaultButton(true);
        btnApply.setOnAction(e -> commitAndClose());

        statusLabel.setText("");
        statusLabel.setStyle("-fx-text-fill: " + DIM + "; -fx-font-size: 10px;");

        HBox row = new HBox(8, statusLabel, spacer, previewButton, btnApply);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(9, 14, 9, 14));
        row.setStyle("-fx-background-color: #161616; -fx-border-color: " + BORDER + "; " +
                     "-fx-border-width: 1 0 0 0;");
        return row;
    }

    // -------------------------------------------------------------------------
    // Logic
    // -------------------------------------------------------------------------

    private void refreshPresetList() {
        String query = presetFilter.getText() == null
            ? ""
            : presetFilter.getText().trim().toLowerCase();
        presetCells.clear();
        presetListBox.getChildren().clear();

        for (PresetGroup group : GROUPS) {
            List<PresetEntry> visibleEntries = new ArrayList<>();
            for (PresetEntry entry : group.entries()) {
                if (query.isBlank() || matchesPresetQuery(group, entry, query)) {
                    visibleEntries.add(entry);
                }
            }
            if (visibleEntries.isEmpty()) continue;

            Label groupLabel = new Label(group.header().toUpperCase());
            groupLabel.setStyle("-fx-text-fill: #565672; -fx-font-size: 9px; -fx-font-weight: bold; " +
                                "-fx-padding: 7 10 2 10;");
            presetListBox.getChildren().add(groupLabel);

            for (PresetEntry entry : visibleEntries) {
                Pane cell = buildPresetCell(entry);
                presetCells.put(entry.type(), cell);
                presetListBox.getChildren().add(cell);
            }
        }

        if (presetListBox.getChildren().isEmpty()) {
            Label empty = new Label("No presets match");
            empty.setStyle("-fx-text-fill: " + DIM + "; -fx-font-size: 11px; -fx-padding: 10;");
            presetListBox.getChildren().add(empty);
        }
        highlightPreset(highlightedType);
    }

    private boolean matchesPresetQuery(PresetGroup group, PresetEntry entry, String query) {
        String haystack = (group.header() + " " + entry.label() + " "
            + entry.type().name() + " " + Easing.displayName(entry.type())).toLowerCase();
        return haystack.contains(query);
    }

    private void applyPreset(Easing.Type type) {
        EasingSpec spec;
        if (type == Easing.Type.CUSTOM) {
            // If already CUSTOM preserve the handles, else default bezier
            EasingSpec current = curveEditor.getEditedSpec();
            spec = current.getType() == Easing.Type.CUSTOM
                ? current
                : EasingSpec.cubicBezier(0.25, 0.10, 0.25, 1.00);
        } else if (type == Easing.Type.CURVE) {
            EasingSpec current = curveEditor.getEditedSpec();
            spec = current.getType() == Easing.Type.CURVE
                ? current
                : KeyframeEditor.toMultiPointCurveSpec(current);
        } else {
            spec = Easing.namedCurveSpec(type);
        }
        applyEditorSpec(spec, type, "Preset: " + Easing.displayName(type));
    }

    private void highlightPreset(Easing.Type type) {
        highlightedType = type != null ? type : Easing.Type.LINEAR;
        for (Map.Entry<Easing.Type, Pane> entry : presetCells.entrySet()) {
            boolean selected = entry.getKey() == highlightedType;
            entry.getValue().setStyle(selected
                ? "-fx-background-color: " + BG_SEL + "; -fx-cursor: hand;"
                : "-fx-background-color: transparent; -fx-cursor: hand;");
        }
    }

    private void refreshInterpButtons() {
        Easing.Interpolation[] values = Easing.Interpolation.values();
        for (int i = 0; i < values.length && i < interpButtons.size(); i++) {
            boolean active = values[i] == selectedInterp;
            interpButtons.get(i).setStyle(active
                ? btnStyle("#1e3450", ACCENT, "#2d5070")
                : btnStyle("#202020", "#888888", "#303030"));
        }
    }

    private void addDurationButton(HBox row, String label, double durationMs) {
        Button button = new Button(label);
        button.setTooltip(new Tooltip("Set preview playback duration"));
        button.setOnAction(e -> setPreviewDuration(durationMs));
        durationButtons.add(button);
        row.getChildren().add(button);
    }

    private void refreshDurationButtons() {
        for (Button button : durationButtons) {
            double duration = switch (button.getText()) {
                case "0.5s" -> 500.0;
                case "2s" -> 2000.0;
                default -> 1000.0;
            };
            boolean active = Math.abs(duration - previewDurationMs) < 0.5;
            button.setStyle(active
                ? btnStyle("#1e3450", ACCENT, "#2d5070")
                : btnStyle("#202020", "#888888", "#303030"));
        }
    }

    private void setPreviewDuration(double durationMs) {
        previewDurationMs = Math.max(250.0, durationMs);
        curveEditor.setAnimDurationMs(previewDurationMs);
        refreshDurationButtons();
        refreshSummary();
        setStatus("Preview duration: " + formatDuration(previewDurationMs) + ".", false);
    }

    private Button toolButton(String text, String tooltip, Runnable action) {
        Button button = new Button(text);
        button.setStyle(btnStyle("#202020", "#c8c8c8", "#303030"));
        button.setTooltip(new Tooltip(tooltip));
        button.setOnAction(e -> {
            if (action != null) action.run();
            curveEditor.requestFocus();
        });
        return button;
    }

    private void applySpecField() {
        if (updatingSpecField) return;
        String raw = specField.getText();
        if (raw == null || raw.isBlank()) {
            syncSpecField(curveEditor.getEditedSpec());
            setSpecFieldError(false);
            return;
        }
        EasingSpec parsed = EasingSpec.tryParse(raw);
        if (parsed == null) {
            setSpecFieldError(true);
            setStatus("Could not parse easing spec.", true);
            return;
        }
        setSpecFieldError(false);
        selectedInterp = Easing.Interpolation.TWEEN;
        curveEditor.setInterpolation(selectedInterp);
        applyEditorSpec(parsed, parsed.getType(), "Spec applied.");
        refreshInterpButtons();
    }

    private void ensureMultiPointCurve() {
        if (curveEditor.getEditedSpec().getType() != Easing.Type.CURVE) {
            applyEditorSpec(KeyframeEditor.toMultiPointCurveSpec(curveEditor.getEditedSpec()), Easing.Type.CURVE, "Converted to editable curve.");
        }
    }

    private void applyEditorSpec(EasingSpec spec, Easing.Type highlightType, String status) {
        EasingSpec resolved = spec != null ? spec : EasingSpec.of(Easing.Type.LINEAR);
        curveEditor.setEasingSpec(resolved);
        curveEditor.setInterpolation(selectedInterp);
        syncSpecField(resolved);
        highlightPreset(highlightType != null ? highlightType : resolved.getType());
        refreshSummary();
        setStatus(status, false);
    }

    private void copySpecToClipboard() {
        ClipboardContent content = new ClipboardContent();
        content.putString(Easing.formatSpec(curveEditor.getEditedSpec()));
        Clipboard.getSystemClipboard().setContent(content);
        setStatus("Spec copied.", false);
    }

    private void pasteSpecFromClipboard() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (!clipboard.hasString()) {
            setStatus("Clipboard does not contain text.", true);
            return;
        }
        specField.setText(clipboard.getString());
        applySpecField();
    }

    private void refreshSummary() {
        EasingSpec spec = curveEditor.getEditedSpec();
        EasingSpec resolved = spec != null ? spec : EasingSpec.of(Easing.Type.LINEAR);
        Easing.Type type = resolved.getType();
        typeSummaryLabel.setText(Easing.displayName(type));
        pointsSummaryLabel.setText(pointSummaryText(resolved));
        rangeSummaryLabel.setText(rangeSummaryText(resolved));
        durationSummaryLabel.setText(formatDuration(previewDurationMs) + " / " + interpolationLabel(selectedInterp));
    }

    private String pointSummaryText(EasingSpec spec) {
        Easing.Type type = spec.getType();
        if (type == Easing.Type.CURVE) {
            return Easing.curvePointCount(spec.getParameters()) + " points";
        }
        if (type == Easing.Type.CUSTOM) {
            return "2 handles";
        }
        if (Easing.usesParameters(type)) {
            double[] params = Easing.coerceParameters(type, spec.getParameters());
            return params.length + " params";
        }
        return "preset";
    }

    private String rangeSummaryText(EasingSpec spec) {
        if (selectedInterp == Easing.Interpolation.HOLD) {
            return "0.00..0.00";
        }
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 48; i++) {
            double t = i / 48.0;
            double y = Easing.applyInterpolation(spec, selectedInterp, t);
            if (Double.isFinite(y)) {
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) return "n/a";
        return String.format(Locale.ROOT, "%.2f..%.2f", min, max);
    }

    private String interpolationLabel(Easing.Interpolation interp) {
        return switch (interp != null ? interp : Easing.Interpolation.TWEEN) {
            case TWEEN -> "Tween";
            case HOLD -> "Hold";
            case STEP -> "Step";
        };
    }

    private void syncSpecField(EasingSpec spec) {
        updatingSpecField = true;
        specField.setText(Easing.formatSpec(spec != null ? spec : EasingSpec.of(Easing.Type.LINEAR)));
        setSpecFieldError(false);
        updatingSpecField = false;
    }

    private void setSpecFieldError(boolean error) {
        specField.setStyle(error
            ? fieldStyle() + " -fx-border-color: #d85f70; -fx-border-width: 1.5;"
            : fieldStyle());
    }

    private void setStatus(String text, boolean error) {
        statusLabel.setText(text == null ? "" : text);
        statusLabel.setStyle("-fx-text-fill: " + (error ? "#e06a78" : DIM) + "; -fx-font-size: 10px;");
    }

    private void updatePreviewButton() {
        if (previewButton == null) return;
        boolean playing = curveEditor.isAnimating();
        previewButton.setText(playing ? "■  Stop" : "▶  Preview");
        previewButton.setStyle(playing
            ? btnStyle("#2e3445", "#f1f1f1", "#56617a")
            : btnStyle("#252525", "#c8c8c8", "#3d3d3d"));
    }

    private void commitAndClose() {
        if (onApply != null) {
            onApply.accept(curveEditor.getEditedSpec(), selectedInterp);
        }
        close();
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private static String btnStyle(String bg, String fg, String border) {
        return "-fx-background-color: " + bg + "; -fx-text-fill: " + fg + "; " +
               "-fx-border-color: " + border + "; -fx-border-width: 1; " +
               "-fx-background-radius: 5; -fx-border-radius: 5; " +
               "-fx-font-size: 11px; -fx-padding: 5 12 5 12; -fx-cursor: hand;";
    }

    private static String fieldStyle() {
        return "-fx-background-color: #101010; -fx-text-fill: " + TEXT + "; " +
               "-fx-border-color: #343434; -fx-border-radius: 5; -fx-background-radius: 5; " +
               "-fx-font-size: 11px; -fx-padding: 5 8;";
    }

    private static String formatDuration(double durationMs) {
        if (durationMs >= 1000.0) {
            return String.format(Locale.ROOT, "%.1fs", durationMs / 1000.0).replace(".0s", "s");
        }
        return String.format(Locale.ROOT, "%.0fms", durationMs);
    }
}
