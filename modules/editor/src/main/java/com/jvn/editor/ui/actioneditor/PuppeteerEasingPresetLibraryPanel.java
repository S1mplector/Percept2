package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;
import com.jvn.editor.ui.EditorPathExplorer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

final class PuppeteerEasingPresetLibraryPanel extends VBox {
    private static final String PANEL_STYLE =
        "-fx-background-color: #121212; -fx-background-radius: 8; -fx-border-radius: 8; "
            + "-fx-border-color: #2f2f2f;";
    private static final String FIELD_STYLE =
        "-fx-background-color: #121212; -fx-text-fill: #e6e6e6; -fx-border-color: #3a3a3a; " +
        "-fx-border-radius: 3; -fx-background-radius: 3; -fx-padding: 4 6; -fx-font-size: 11px;";
    private static final String BUTTON_STYLE =
        "-fx-background-color: #232323; -fx-text-fill: #e0e0e0; -fx-background-radius: 4; "
            + "-fx-border-color: #3d3d3d; -fx-border-radius: 4; -fx-padding: 5 10; -fx-font-size: 11px; -fx-cursor: hand;";
    private static final String ACCENT_BUTTON_STYLE =
        "-fx-background-color: #434343; -fx-text-fill: white; -fx-border-color: #707070; -fx-background-radius: 4; "
            + "-fx-border-radius: 4; -fx-padding: 5 10; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";
    private static final String DANGER_BUTTON_STYLE =
        "-fx-background-color: #6d2f3a; -fx-text-fill: #ffe3e7; -fx-background-radius: 4; "
            + "-fx-border-radius: 4; -fx-padding: 5 10; -fx-font-size: 11px; -fx-font-weight: bold; -fx-cursor: hand;";

    private final Label filePathLabel = new Label("Project file: unavailable");
    private final Label currentSpecLabel = new Label();
    private final Label selectedSpecLabel = new Label();
    private final Label statusLabel = new Label();
    private final Canvas previewCanvas = new Canvas(228, 132);
    private final TextField presetNameField = new TextField();
    private final ListView<PuppeteerEasingPresetStore.Preset> presetList = new ListView<>();
    private final Button btnApply = new Button("Apply Selected");
    private final Button btnSaveCurrent = new Button("Save Current");
    private final Button btnDuplicate = new Button("Duplicate");
    private final Button btnRename = new Button("Rename");
    private final Button btnDelete = new Button("Delete");
    private final Button btnReload = new Button("Reload");
    private final Button btnReveal = new Button("Reveal File");
    private final Button btnImport = new Button("Import...");
    private final Button btnExport = new Button("Export...");

    private final List<PuppeteerEasingPresetStore.Preset> presets = new ArrayList<>();

    private File projectRoot;
    private Supplier<EasingSpec> currentSpecSupplier = () -> EasingSpec.of(Easing.Type.LINEAR);
    private Supplier<PuppeteerEasingCatalog.Entry> selectedEntrySupplier = () -> null;
    private Consumer<EasingSpec> onPresetApplied = spec -> {};
    private Runnable onLibraryChanged = () -> {};

    PuppeteerEasingPresetLibraryPanel() {
        setSpacing(10);
        setPadding(new Insets(10));
        setStyle(PANEL_STYLE);
        setVisible(false);
        setManaged(false);

        Label header = new Label("Preset Library");
        header.setStyle("-fx-text-fill: #f2f4f7; -fx-font-size: 12px; -fx-font-weight: bold;");

        filePathLabel.setWrapText(true);
        filePathLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 10px;");

        previewCanvas.setWidth(228);
        previewCanvas.setHeight(132);

        currentSpecLabel.setWrapText(true);
        currentSpecLabel.setStyle("-fx-text-fill: #b0b0b0; -fx-font-size: 10px;");
        selectedSpecLabel.setWrapText(true);
        selectedSpecLabel.setStyle("-fx-text-fill: #f2d591; -fx-font-size: 10px;");

        presetNameField.setPromptText("Preset name");
        presetNameField.setStyle(FIELD_STYLE);
        presetNameField.textProperty().addListener((obs, oldValue, newValue) -> refreshActionState());

        presetList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(PuppeteerEasingPresetStore.Preset item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(item.name());
                setTooltip(new Tooltip(item.spec().toDslString()));
            }
        });
        presetList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                presetNameField.setText(newValue.name());
            }
            refreshPreview();
            refreshActionState();
        });
        presetList.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                applySelectedPreset();
                event.consume();
            }
        });
        VBox.setVgrow(presetList, Priority.ALWAYS);

        styleButton(btnApply, ACCENT_BUTTON_STYLE);
        styleButton(btnSaveCurrent, BUTTON_STYLE);
        styleButton(btnDuplicate, BUTTON_STYLE);
        styleButton(btnRename, BUTTON_STYLE);
        styleButton(btnDelete, DANGER_BUTTON_STYLE);
        styleButton(btnReload, BUTTON_STYLE);
        styleButton(btnReveal, BUTTON_STYLE);
        styleButton(btnImport, BUTTON_STYLE);
        styleButton(btnExport, BUTTON_STYLE);

        btnApply.setOnAction(event -> applySelectedPreset());
        btnSaveCurrent.setOnAction(event -> saveCurrentAsPreset());
        btnDuplicate.setOnAction(event -> duplicateSelectedPreset());
        btnRename.setOnAction(event -> renameSelectedPreset());
        btnDelete.setOnAction(event -> deleteSelectedPreset());
        btnReload.setOnAction(event -> reloadLibrary());
        btnReveal.setOnAction(event -> revealProjectFile());
        btnImport.setOnAction(event -> importPresetFile());
        btnExport.setOnAction(event -> exportPresetFile());

        HBox saveRow = new HBox(8, presetNameField, btnSaveCurrent, btnDuplicate, btnRename, btnDelete);
        saveRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(presetNameField, Priority.ALWAYS);

        HBox fileRow = new HBox(8, btnReload, btnReveal, btnImport, btnExport);
        fileRow.setAlignment(Pos.CENTER_LEFT);

        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 10px;");

        getChildren().addAll(
            header,
            filePathLabel,
            previewCanvas,
            currentSpecLabel,
            selectedSpecLabel,
            presetList,
            btnApply,
            saveRow,
            fileRow,
            statusLabel
        );
        refreshView();
    }

    void setProjectRoot(File root) {
        projectRoot = root;
        reloadLibrary();
    }

    void setCurrentSpecSupplier(Supplier<EasingSpec> supplier) {
        currentSpecSupplier = supplier != null ? supplier : () -> EasingSpec.of(Easing.Type.LINEAR);
        refreshPreview();
    }

    void setSelectedEntrySupplier(Supplier<PuppeteerEasingCatalog.Entry> supplier) {
        selectedEntrySupplier = supplier != null ? supplier : () -> null;
        refreshView();
    }

    void setOnPresetApplied(Consumer<EasingSpec> callback) {
        onPresetApplied = callback != null ? callback : spec -> {};
    }

    void setOnLibraryChanged(Runnable callback) {
        onLibraryChanged = callback != null ? callback : () -> {};
    }

    void toggleVisible() {
        boolean next = !isVisible();
        setVisible(next);
        setManaged(next);
        if (next) {
            refreshView();
        }
    }

    boolean isPanelVisible() {
        return isVisible();
    }

    boolean hasProjectAccess() {
        return hasProject();
    }

    boolean saveCurrentSpecAsPreset(String preferredName) {
        if (preferredName != null) {
            presetNameField.setText(preferredName);
        }
        return saveCurrentAsPreset();
    }

    void reloadLibrary() {
        presets.clear();
        if (projectRoot != null) {
            presets.addAll(PuppeteerEasingPresetStore.load(projectRoot));
        }
        presetList.getItems().setAll(presets);
        syncSelectionFromEditor();
        updateFilePathLabel();
        setStatus(projectRoot == null
            ? "Open a project to manage easing presets."
            : presets.isEmpty()
                ? "No saved presets yet. Save the current easing into the project file."
                : presets.size() + " preset" + (presets.size() == 1 ? "" : "s") + " loaded.",
            false);
        refreshPreview();
        refreshActionState();
    }

    void refreshView() {
        syncSelectionFromEditor();
        updateFilePathLabel();
        refreshPreview();
        refreshActionState();
    }

    private void applySelectedPreset() {
        PuppeteerEasingPresetStore.Preset selected = presetList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            presetList.requestFocus();
            return;
        }
        onPresetApplied.accept(selected.spec());
        setStatus("Applied preset '" + selected.name() + "'.", false);
    }

    private boolean saveCurrentAsPreset() {
        if (!hasProject()) {
            setStatus("Open a project before saving presets.", true);
            return false;
        }
        String name = normalizedFieldName();
        if (name.isBlank()) {
            setStatus("Preset name cannot be blank.", true);
            presetNameField.requestFocus();
            return false;
        }
        for (PuppeteerEasingPresetStore.Preset preset : presets) {
            if (preset.name().equalsIgnoreCase(name)) {
                setStatus("Preset '" + name + "' already exists.", true);
                return false;
            }
        }
        List<PuppeteerEasingPresetStore.Preset> updated = new ArrayList<>(presets);
        updated.add(new PuppeteerEasingPresetStore.Preset(
            PuppeteerEasingPresetStore.uniqueId(name, updated, null),
            name,
            resolveCurrentSpec()
        ));
        persistPresets(updated, "Saved preset '" + name + "'.");
        return true;
    }

    private void duplicateSelectedPreset() {
        PuppeteerEasingPresetStore.Preset selected = presetList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("Select a preset to duplicate.", true);
            return;
        }
        String preferred = normalizedFieldName();
        if (preferred.isBlank() || preferred.equalsIgnoreCase(selected.name())) {
            preferred = uniqueName(selected.name() + " Copy", presets, null);
        }
        List<PuppeteerEasingPresetStore.Preset> updated = new ArrayList<>(presets);
        updated.add(new PuppeteerEasingPresetStore.Preset(
            PuppeteerEasingPresetStore.uniqueId(preferred, updated, null),
            preferred,
            selected.spec()
        ));
        persistPresets(updated, "Duplicated preset as '" + preferred + "'.");
    }

    private void renameSelectedPreset() {
        PuppeteerEasingPresetStore.Preset selected = presetList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("Select a preset to rename.", true);
            return;
        }
        String name = normalizedFieldName();
        if (name.isBlank()) {
            setStatus("Preset name cannot be blank.", true);
            return;
        }
        List<PuppeteerEasingPresetStore.Preset> updated = new ArrayList<>(presets);
        for (PuppeteerEasingPresetStore.Preset preset : updated) {
            if (!preset.id().equals(selected.id()) && preset.name().equalsIgnoreCase(name)) {
                setStatus("Preset '" + name + "' already exists.", true);
                return;
            }
        }
        int index = indexOfPreset(selected.id());
        if (index < 0) {
            setStatus("The selected preset is no longer loaded.", true);
            return;
        }
        updated.set(index, new PuppeteerEasingPresetStore.Preset(selected.id(), name, selected.spec()));
        persistPresets(updated, "Renamed preset to '" + name + "'.");
    }

    private void deleteSelectedPreset() {
        PuppeteerEasingPresetStore.Preset selected = presetList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("Select a preset to delete.", true);
            return;
        }
        List<PuppeteerEasingPresetStore.Preset> updated = new ArrayList<>(presets);
        updated.removeIf(preset -> preset.id().equals(selected.id()));
        persistPresets(updated, "Deleted preset '" + selected.name() + "'.");
    }

    private void importPresetFile() {
        if (!hasProject()) {
            setStatus("Open a project before importing presets.", true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Easing Preset Library");
        chooser.getExtensionFilters().setAll(
            new FileChooser.ExtensionFilter("Properties", "*.properties"),
            new FileChooser.ExtensionFilter("All Files", "*.*"));
        File file = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) return;

        List<PuppeteerEasingPresetStore.Preset> imported = PuppeteerEasingPresetStore.load(file.toPath());
        if (imported.isEmpty()) {
            setStatus("No presets were found in " + file.getName() + ".", true);
            return;
        }
        List<PuppeteerEasingPresetStore.Preset> updated = new ArrayList<>(presets);
        for (PuppeteerEasingPresetStore.Preset preset : imported) {
            String uniqueName = uniqueName(preset.name(), updated, null);
            updated.add(new PuppeteerEasingPresetStore.Preset(
                PuppeteerEasingPresetStore.uniqueId(uniqueName, updated, null),
                uniqueName,
                preset.spec()
            ));
        }
        persistPresets(updated, "Imported " + imported.size() + " preset" + (imported.size() == 1 ? "" : "s") + ".");
    }

    private void exportPresetFile() {
        if (presets.isEmpty()) {
            setStatus("There are no presets to export.", true);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Easing Preset Library");
        chooser.setInitialFileName("easing-presets.properties");
        chooser.getExtensionFilters().setAll(
            new FileChooser.ExtensionFilter("Properties", "*.properties"),
            new FileChooser.ExtensionFilter("All Files", "*.*"));
        File file = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) return;
        try {
            PuppeteerEasingPresetStore.save(file.toPath(), presets);
            setStatus("Exported presets to " + file.getName() + ".", false);
        } catch (IOException ex) {
            setStatus("Export failed: " + rootMessage(ex), true);
        }
    }

    private void revealProjectFile() {
        Path file = PuppeteerEasingPresetStore.resolveProjectFile(projectRoot);
        if (file == null) {
            setStatus("Open a project before revealing the preset file.", true);
            return;
        }
        if (!revealFile(file.toFile())) {
            setStatus("Could not reveal the preset file on this system.", true);
        }
    }

    private void persistPresets(List<PuppeteerEasingPresetStore.Preset> updated, String successMessage) {
        if (!hasProject()) {
            setStatus("Open a project before editing presets.", true);
            return;
        }
        try {
            PuppeteerEasingPresetStore.save(projectRoot, updated);
            reloadLibrary();
            onLibraryChanged.run();
            setStatus(successMessage, false);
            String targetName = normalizedFieldName();
            if (!targetName.isBlank()) {
                selectPresetByName(targetName);
            }
        } catch (IOException ex) {
            setStatus("Failed to save presets: " + rootMessage(ex), true);
        }
    }

    private void selectPresetByName(String name) {
        if (name == null || name.isBlank()) return;
        for (PuppeteerEasingPresetStore.Preset preset : presetList.getItems()) {
            if (preset.name().equalsIgnoreCase(name)) {
                presetList.getSelectionModel().select(preset);
                presetList.scrollTo(preset);
                return;
            }
        }
    }

    private void syncSelectionFromEditor() {
        PuppeteerEasingCatalog.Entry selectedEntry = selectedEntrySupplier != null ? selectedEntrySupplier.get() : null;
        if (selectedEntry != null && selectedEntry.isPreset()) {
            String presetId = selectedEntry.id().substring("preset:".length());
            for (PuppeteerEasingPresetStore.Preset preset : presetList.getItems()) {
                if (preset.id().equals(presetId)) {
                    presetList.getSelectionModel().select(preset);
                    presetList.scrollTo(preset);
                    return;
                }
            }
        }
    }

    private void refreshActionState() {
        boolean hasProject = hasProject();
        boolean hasSelection = presetList.getSelectionModel().getSelectedItem() != null;
        btnApply.setDisable(!hasSelection);
        btnSaveCurrent.setDisable(!hasProject);
        btnDuplicate.setDisable(!hasProject || !hasSelection);
        btnRename.setDisable(!hasProject || !hasSelection || normalizedFieldName().isBlank());
        btnDelete.setDisable(!hasProject || !hasSelection);
        btnReload.setDisable(!hasProject);
        btnReveal.setDisable(!hasProject);
        btnImport.setDisable(!hasProject);
        btnExport.setDisable(presets.isEmpty());
        presetNameField.setDisable(!hasProject);
    }

    private void refreshPreview() {
        EasingSpec currentSpec = resolveCurrentSpec();
        PuppeteerEasingPresetStore.Preset selected = presetList.getSelectionModel().getSelectedItem();
        currentSpecLabel.setText("Current: " + currentSpec.toDslString());
        selectedSpecLabel.setText(selected == null
            ? "Selected: none"
            : "Selected: " + selected.spec().toDslString());

        GraphicsContext gc = previewCanvas.getGraphicsContext2D();
        double w = previewCanvas.getWidth();
        double h = previewCanvas.getHeight();
        gc.setFill(Color.web("#0f1217"));
        gc.fillRoundRect(0, 0, w, h, 8, 8);
        gc.setStroke(Color.web("#2a3240"));
        gc.strokeRoundRect(0.5, 0.5, w - 1.0, h - 1.0, 8, 8);

        gc.setStroke(Color.web("#253041"));
        gc.setLineWidth(1.0);
        gc.strokeLine(18, h - 18, w - 12, h - 18);
        gc.strokeLine(18, h - 18, 18, 12);

        drawCurve(gc, currentSpec, Color.web("#5aa8ff"), 2.0, w, h);
        if (selected != null) {
            drawCurve(gc, selected.spec(), Color.web("#f0d98a"), 2.4, w, h);
        }
    }

    private void drawCurve(GraphicsContext gc, EasingSpec spec, Color color, double lineWidth, double w, double h) {
        gc.setStroke(color);
        gc.setLineWidth(lineWidth);
        double prevX = 18.0;
        double prevY = h - 18.0;
        for (int i = 1; i <= 72; i++) {
            double t = i / 72.0;
            double value = Easing.apply(spec, t);
            double x = 18.0 + (w - 30.0) * t;
            double y = (h - 18.0) - (h - 30.0) * value;
            gc.strokeLine(prevX, prevY, x, y);
            prevX = x;
            prevY = y;
        }
    }

    private void updateFilePathLabel() {
        Path file = PuppeteerEasingPresetStore.resolveProjectFile(projectRoot);
        filePathLabel.setText(file == null
            ? "Project file: unavailable"
            : "Project file: " + file.toString().replace('\\', '/'));
    }

    private EasingSpec resolveCurrentSpec() {
        try {
            EasingSpec spec = currentSpecSupplier != null ? currentSpecSupplier.get() : null;
            return spec != null ? spec : EasingSpec.of(Easing.Type.LINEAR);
        } catch (RuntimeException ex) {
            return EasingSpec.of(Easing.Type.LINEAR);
        }
    }

    private boolean hasProject() {
        return projectRoot != null;
    }

    private int indexOfPreset(String presetId) {
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).id().equals(presetId)) return i;
        }
        return -1;
    }

    private String normalizedFieldName() {
        return PuppeteerEasingPresetStore.normalizeName(presetNameField.getText());
    }

    private void setStatus(String message, boolean error) {
        statusLabel.setText(message == null ? "" : message.trim());
        statusLabel.setStyle(error
            ? "-fx-text-fill: #e6a8b3; -fx-font-size: 10px;"
            : "-fx-text-fill: #888888; -fx-font-size: 10px;");
    }

    private static String uniqueName(String preferred, List<PuppeteerEasingPresetStore.Preset> existing, String reservedId) {
        String base = PuppeteerEasingPresetStore.normalizeName(preferred);
        if (base.isBlank()) base = "Preset";
        String candidate = base;
        int suffix = 2;
        while (nameExists(candidate, existing, reservedId)) {
            candidate = base + " " + suffix++;
        }
        return candidate;
    }

    private static boolean nameExists(String candidate, List<PuppeteerEasingPresetStore.Preset> existing, String reservedId) {
        for (PuppeteerEasingPresetStore.Preset preset : existing) {
            if (preset == null) continue;
            if (reservedId != null && reservedId.equals(preset.id())) continue;
            if (candidate.equalsIgnoreCase(preset.name())) return true;
        }
        return false;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
            ? current.getClass().getSimpleName()
            : message.trim().replace("\n", " ");
    }

    private static void styleButton(Button button, String style) {
        button.setStyle(style);
        button.setFocusTraversable(false);
    }

    private static boolean revealFile(File file) {
        return EditorPathExplorer.show(file);
    }
}
