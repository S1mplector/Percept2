package com.jvn.editor.ui.actioneditor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
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

    private final EasingCurveEditor curveEditor = new EasingCurveEditor();
    private final Map<Easing.Type, Pane> presetCells = new LinkedHashMap<>();
    private Easing.Interpolation selectedInterp;
    private final List<Button> interpButtons = new ArrayList<>();
    private final BiConsumer<EasingSpec, Easing.Interpolation> onApply;

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
        setMinWidth(580);
        setMinHeight(420);

        VBox root = new VBox();
        root.setStyle("-fx-background-color: " + BG_DARK + ";");

        root.getChildren().addAll(
            buildHeader(contextTitle),
            buildBody(),
            buildFooter()
        );
        VBox.setVgrow(root.getChildren().get(1), Priority.ALWAYS);

        Scene scene = new Scene(root, 700, 520);
        scene.setFill(Color.web(BG_DARK));
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) close();
            if (e.getCode() == KeyCode.ENTER && (e.isControlDown() || e.isShortcutDown())) commitAndClose();
        });
        setScene(scene);

        // Load initial state
        EasingSpec spec = initialSpec != null ? initialSpec : EasingSpec.of(Easing.Type.LINEAR);
        curveEditor.setInterpolation(selectedInterp);
        curveEditor.setEasingSpec(spec);
        curveEditor.setExpanded(true);

        highlightPreset(spec.getType());
        refreshInterpButtons();
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
        panel.setPrefWidth(168);
        panel.setMinWidth(168);
        panel.setMaxWidth(168);
        panel.setStyle("-fx-background-color: " + BG_PANEL + "; -fx-border-color: " + BORDER + "; " +
                       "-fx-border-width: 0 1 0 0;");

        Label header = new Label("PRESETS");
        header.setStyle("-fx-text-fill: " + DIM + "; -fx-font-size: 9px; -fx-font-weight: bold; " +
                        "-fx-padding: 9 10 4 10;");

        VBox listBox = new VBox(0);
        listBox.setStyle("-fx-background-color: " + BG_PANEL + ";");
        listBox.setFillWidth(true);

        for (PresetGroup group : GROUPS) {
            Label groupLabel = new Label(group.header().toUpperCase());
            groupLabel.setStyle("-fx-text-fill: #3d3d50; -fx-font-size: 9px; -fx-font-weight: bold; " +
                                "-fx-padding: 7 10 2 10;");
            listBox.getChildren().add(groupLabel);

            for (PresetEntry entry : group.entries()) {
                Pane cell = buildPresetCell(entry);
                presetCells.put(entry.type(), cell);
                listBox.getChildren().add(cell);
            }
        }

        ScrollPane scroll = new ScrollPane(listBox);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: " + BG_PANEL + "; -fx-background-color: " + BG_PANEL + "; " +
                        "-fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        panel.getChildren().addAll(header, scroll);
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
            if (presetCells.get(entry.type()) != cell || !BG_SEL.equals(extractBg(cell)))
                cell.setStyle("-fx-background-color: #1a2535; -fx-cursor: hand;");
        });
        cell.setOnMouseExited(e -> {
            if (!BG_SEL.equals(extractBg(cell)))
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
        panel.getChildren().addAll(curveEditor, buildInterpRow());
        return panel;
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
            });
            interpButtons.add(btn);
            row.getChildren().add(btn);
        }

        row.getChildren().add(0, lbl);
        return row;
    }

    private HBox buildFooter() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnPreview = new Button("▶  Preview");
        btnPreview.setStyle(btnStyle("#252525", "#909090", "#353535"));
        btnPreview.setOnAction(e -> curveEditor.toggleAnimation());

        Button btnApply = new Button("Apply to Selection");
        btnApply.setStyle(btnStyle(APPLY_BG, APPLY_FG, APPLY_BD) + " -fx-font-weight: bold;");
        btnApply.setDefaultButton(true);
        btnApply.setOnAction(e -> commitAndClose());

        HBox row = new HBox(8, spacer, btnPreview, btnApply);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(9, 14, 9, 14));
        row.setStyle("-fx-background-color: #161616; -fx-border-color: " + BORDER + "; " +
                     "-fx-border-width: 1 0 0 0;");
        return row;
    }

    // -------------------------------------------------------------------------
    // Logic
    // -------------------------------------------------------------------------

    private void applyPreset(Easing.Type type) {
        EasingSpec spec;
        if (type == Easing.Type.CUSTOM) {
            // If already CUSTOM preserve the handles, else default bezier
            EasingSpec current = curveEditor.getEditedSpec();
            spec = current.getType() == Easing.Type.CUSTOM
                ? current
                : EasingSpec.cubicBezier(0.25, 0.10, 0.25, 1.00);
        } else {
            spec = EasingSpec.of(type);
        }
        curveEditor.setEasingSpec(spec);
        curveEditor.setInterpolation(selectedInterp);
        highlightPreset(type);
    }

    private void highlightPreset(Easing.Type type) {
        for (Map.Entry<Easing.Type, Pane> entry : presetCells.entrySet()) {
            boolean selected = entry.getKey() == type;
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

    private static String extractBg(Pane cell) {
        String style = cell.getStyle();
        int idx = style.indexOf("-fx-background-color:");
        if (idx < 0) return "";
        String rest = style.substring(idx + "-fx-background-color:".length()).trim();
        int end = rest.indexOf(';');
        return end >= 0 ? rest.substring(0, end).trim() : rest.trim();
    }
}
