package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;
import com.jvn.editor.ui.CssIcon;
import com.jvn.editor.ui.EditorDialogs;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

final class PuppeteerEasingComboBox extends ComboBox<PuppeteerEasingCatalog.Entry> {
    private static final String OWNER_STYLE =
        "-fx-background-color: #121212; -fx-text-fill: #e6e6e6; -fx-border-color: #3a3a3a; " +
        "-fx-border-radius: 3; -fx-background-radius: 3; -fx-padding: 2 4; -fx-font-size: 11px;";
    private static final String CELL_ACTION_STYLE =
        "-fx-background-color: transparent; -fx-border-color: transparent; -fx-padding: 2 4; " +
        "-fx-background-radius: 4; -fx-border-radius: 4;";
    private static final String CELL_ACTION_HOVER_STYLE =
        "-fx-background-color: rgba(255,255,255,0.08); -fx-border-color: transparent; -fx-padding: 2 4; " +
        "-fx-background-radius: 4; -fx-border-radius: 4;";

    private final List<PuppeteerEasingPresetStore.Preset> presets = new ArrayList<>();
    private List<PuppeteerEasingCatalog.Entry> entries = List.of();

    private File projectRoot;
    private Supplier<EasingSpec> currentSpecSupplier = () -> EasingSpec.of(Easing.Type.LINEAR);
    private boolean updatingValue = false;

    PuppeteerEasingComboBox() {
        setEditable(false);
        setVisibleRowCount(14);
        setMaxWidth(Double.MAX_VALUE);
        setTooltip(new Tooltip("Select easing"));
        setStyle(OWNER_STYLE);
        setButtonCell(createCell(true));
        setCellFactory(list -> createCell(false));
        reloadProjectPresets();
        setCurrentSpec(EasingSpec.of(Easing.Type.LINEAR));
    }

    void setProjectRoot(File root) {
        projectRoot = root;
        reloadProjectPresets();
    }

    void setCurrentSpecSupplier(Supplier<EasingSpec> supplier) {
        currentSpecSupplier = supplier != null ? supplier : () -> EasingSpec.of(Easing.Type.LINEAR);
    }

    void setCurrentSpec(EasingSpec spec) {
        PuppeteerEasingCatalog.Entry match = PuppeteerEasingCatalog.matchForSpec(entries, spec);
        if (match != null) {
            applySelection(match, false);
        }
    }

    EasingSpec getSelectedSpec() {
        PuppeteerEasingCatalog.Entry value = getValue();
        return value != null ? value.spec() : EasingSpec.of(Easing.Type.LINEAR);
    }

    PuppeteerEasingCatalog.Entry getSelectedEntry() {
        return getValue();
    }

    List<PuppeteerEasingPresetStore.Preset> getProjectPresets() {
        return List.copyOf(presets);
    }

    void reloadProjectPresets() {
        EasingSpec currentSpec = getSelectedSpec();
        presets.clear();
        if (projectRoot != null) {
            presets.addAll(PuppeteerEasingPresetStore.load(projectRoot));
        }
        entries = PuppeteerEasingCatalog.buildEntries(presets);
        getItems().setAll(entries);
        setCurrentSpec(currentSpec);
    }

    void refreshState() {
        // Preset management moved into PuppeteerEasingPresetLibraryPanel.
    }

    boolean updatePreset(String presetEntryId, String presetName, EasingSpec spec) {
        if (projectRoot == null) return false;
        String presetId = stripPresetPrefix(presetEntryId);
        if (presetId == null || presetId.isBlank()) return false;
        String name = PuppeteerEasingPresetStore.normalizeName(presetName);
        if (name.isBlank()) return false;

        EasingSpec resolved = spec != null ? spec : resolveCurrentSpec();
        if (resolved == null) return false;

        List<PuppeteerEasingPresetStore.Preset> updated = new ArrayList<>(presets);
        int index = indexOfPreset(presetId);
        if (index < 0) return false;
        for (PuppeteerEasingPresetStore.Preset preset : updated) {
            if (!preset.id().equals(presetId) && preset.name().equalsIgnoreCase(name)) {
                return false;
            }
        }
        updated.set(index, new PuppeteerEasingPresetStore.Preset(presetId, name, resolved));
        try {
            PuppeteerEasingPresetStore.save(projectRoot, updated);
            reloadProjectPresets();
            selectEntryById("preset:" + presetId, resolved);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    boolean renamePreset(String presetEntryId, String preferredName) {
        if (projectRoot == null) return false;
        String presetId = stripPresetPrefix(presetEntryId);
        if (presetId == null || presetId.isBlank()) return false;
        int index = indexOfPreset(presetId);
        if (index < 0) return false;

        PuppeteerEasingPresetStore.Preset existing = presets.get(index);
        String name = PuppeteerEasingPresetStore.normalizeName(
            preferredName != null ? preferredName : existing.name());
        if (name.isBlank()) return false;
        if (existing.name().equals(name)) return true;

        List<PuppeteerEasingPresetStore.Preset> updated = new ArrayList<>(presets);
        for (PuppeteerEasingPresetStore.Preset preset : updated) {
            if (!preset.id().equals(presetId) && preset.name().equalsIgnoreCase(name)) {
                return false;
            }
        }
        updated.set(index, new PuppeteerEasingPresetStore.Preset(existing.id(), name, existing.spec()));
        try {
            PuppeteerEasingPresetStore.save(projectRoot, updated);
            reloadProjectPresets();
            selectEntryById("preset:" + presetId, existing.spec());
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    boolean deletePreset(String presetEntryId) {
        if (projectRoot == null) return false;
        String presetId = stripPresetPrefix(presetEntryId);
        if (presetId == null || presetId.isBlank()) return false;
        int index = indexOfPreset(presetId);
        if (index < 0) return false;

        EasingSpec currentSpec = getSelectedSpec();
        List<PuppeteerEasingPresetStore.Preset> updated = new ArrayList<>(presets);
        updated.removeIf(preset -> preset.id().equals(presetId));
        try {
            PuppeteerEasingPresetStore.save(projectRoot, updated);
            reloadProjectPresets();
            setCurrentSpec(currentSpec);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private EasingSpec resolveCurrentSpec() {
        try {
            EasingSpec spec = currentSpecSupplier != null ? currentSpecSupplier.get() : null;
            return spec != null ? spec : getSelectedSpec();
        } catch (RuntimeException ex) {
            return getSelectedSpec();
        }
    }

    private int indexOfPreset(String entryId) {
        String presetId = stripPresetPrefix(entryId);
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).id().equals(presetId)) return i;
        }
        return -1;
    }

    private String stripPresetPrefix(String entryId) {
        return entryId != null && entryId.startsWith("preset:")
            ? entryId.substring("preset:".length())
            : entryId;
    }

    private void applySelection(PuppeteerEasingCatalog.Entry entry, boolean fireAction) {
        if (entry == null) return;
        PuppeteerEasingCatalog.Entry previous = getValue();
        updatingValue = true;
        try {
            getSelectionModel().select(entry);
            super.setValue(entry);
        } finally {
            updatingValue = false;
        }
        if (fireAction && !Objects.equals(previous, entry)) {
            fireEvent(new javafx.event.ActionEvent());
        }
    }

    private void selectEntryById(String entryId, EasingSpec fallbackSpec) {
        if (entryId != null) {
            for (PuppeteerEasingCatalog.Entry entry : entries) {
                if (entryId.equals(entry.id())) {
                    applySelection(entry, false);
                    return;
                }
            }
        }
        setCurrentSpec(fallbackSpec);
    }

    private ListCell<PuppeteerEasingCatalog.Entry> createCell(boolean buttonCell) {
        return new ListCell<>() {
            private final Label textLabel = new Label();
            private final Region spacer = new Region();
            private final Button renameButton = createCellActionButton(
                CssIcon.edit("#8cc5ff"),
                "Rename custom curve");
            private final Button deleteButton = createCellActionButton(
                CssIcon.delete("#f38ba8"),
                "Delete custom curve");
            private final HBox graphicRow = new HBox(8, textLabel, spacer, renameButton, deleteButton);

            {
                graphicRow.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                setContentDisplay(ContentDisplay.TEXT_ONLY);

                renameButton.setOnAction(event -> {
                    event.consume();
                    PuppeteerEasingCatalog.Entry entry = getItem();
                    if (entry == null || !entry.isPreset()) return;
                    EditorDialogs.promptText(
                            getScene() == null ? null : getScene().getWindow(),
                            "Rename Custom Curve",
                            "Rename custom curve",
                            "Name",
                            entry.label(),
                            entry.label(),
                            "Rename")
                        .ifPresent(value -> renamePreset(entry.id(), value));
                });
                deleteButton.setOnAction(event -> {
                    event.consume();
                    PuppeteerEasingCatalog.Entry entry = getItem();
                    if (entry == null || !entry.isPreset()) return;
                    if (EditorDialogs.confirm(
                            getScene() == null ? null : getScene().getWindow(),
                            "Delete Custom Curve",
                            "Delete custom curve?\nRemove '" + entry.label() + "' from this project?",
                            "Delete",
                            true)) {
                        deletePreset(entry.id());
                    }
                });
            }

            @Override
            protected void updateItem(PuppeteerEasingCatalog.Entry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    setStyle("");
                    return;
                }
                String text = buttonCell && !item.isPreset()
                    ? item.label()
                    : item.label() + "  [" + item.badge() + "]";
                setTooltip(new Tooltip(item.spec().toDslString()));
                if (buttonCell || !item.isPreset()) {
                    setText(text);
                    setGraphic(null);
                    setContentDisplay(ContentDisplay.TEXT_ONLY);
                } else {
                    textLabel.setText(text);
                    textLabel.setStyle("-fx-text-fill: #f0d98a;");
                    setText(null);
                    setGraphic(graphicRow);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
                setStyle(item.isPreset() ? "-fx-text-fill: #f0d98a;" : "");
            }
        };
    }

    private static Button createCellActionButton(Region icon, String tooltipText) {
        Button button = new Button();
        button.setGraphic(icon);
        button.setTooltip(new Tooltip(tooltipText));
        button.setFocusTraversable(false);
        button.setMnemonicParsing(false);
        button.setStyle(CELL_ACTION_STYLE);
        button.setOnMouseEntered(event -> button.setStyle(CELL_ACTION_HOVER_STYLE));
        button.setOnMouseExited(event -> button.setStyle(CELL_ACTION_STYLE));
        button.setOnMousePressed(event -> event.consume());
        button.setOnMouseReleased(event -> event.consume());
        return button;
    }
}
