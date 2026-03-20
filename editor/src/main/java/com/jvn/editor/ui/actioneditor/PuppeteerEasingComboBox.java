package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

final class PuppeteerEasingComboBox extends ComboBox<PuppeteerEasingCatalog.Entry> {
    private static final String OWNER_STYLE =
        "-fx-background-color: #121212; -fx-text-fill: #e6e6e6; -fx-border-color: #3a3a3a; " +
        "-fx-border-radius: 3; -fx-background-radius: 3; -fx-padding: 2 4; -fx-font-size: 11px;";
    private static final String FIELD_STYLE =
        "-fx-background-color: #121212; -fx-text-fill: #e6e6e6; -fx-border-color: #3a3a3a; " +
        "-fx-border-radius: 3; -fx-background-radius: 3; -fx-padding: 4 6; -fx-font-size: 11px;";
    private static final String BUTTON_STYLE =
        "-fx-background-color: #2a2a2a; -fx-text-fill: #e6e6e6; -fx-background-radius: 3; " +
        "-fx-border-radius: 3; -fx-padding: 4 10; -fx-font-size: 11px; -fx-cursor: hand;";
    private static final String DELETE_BUTTON_STYLE =
        "-fx-background-color: #3a2323; -fx-text-fill: #f1c1c1; -fx-background-radius: 3; " +
        "-fx-border-radius: 3; -fx-padding: 4 10; -fx-font-size: 11px; -fx-cursor: hand;";

    private final Popup popup = new Popup();
    private final VBox popupRoot = new VBox(8);
    private final TextField searchField = new TextField();
    private final ListView<PuppeteerEasingCatalog.Entry> listView = new ListView<>();
    private final TextField presetNameField = new TextField();
    private final Button btnSavePreset = new Button("Save New");
    private final Button btnUpdatePreset = new Button("Update");
    private final Button btnDeletePreset = new Button("Delete");
    private final Label statusLabel = new Label();

    private final List<PuppeteerEasingPresetStore.Preset> presets = new ArrayList<>();
    private List<PuppeteerEasingCatalog.Entry> entries = List.of();

    private File projectRoot;
    private Supplier<EasingSpec> currentSpecSupplier = () -> EasingSpec.of(Easing.Type.LINEAR);
    private boolean updatingValue = false;

    PuppeteerEasingComboBox() {
        setEditable(false);
        setVisibleRowCount(14);
        setMaxWidth(Double.MAX_VALUE);
        setTooltip(new Tooltip("Search easings and manage project presets"));
        setStyle(OWNER_STYLE);
        setButtonCell(createCell(true));
        setCellFactory(list -> createCell(false));

        configurePopup();
        installPopupBridge();
        rebuildEntries();
        setCurrentSpec(EasingSpec.of(Easing.Type.LINEAR));
    }

    void setProjectRoot(File root) {
        projectRoot = root;
        reloadPresets();
    }

    void setCurrentSpecSupplier(Supplier<EasingSpec> supplier) {
        currentSpecSupplier = supplier != null ? supplier : () -> EasingSpec.of(Easing.Type.LINEAR);
        refreshState();
    }

    void setCurrentSpec(EasingSpec spec) {
        PuppeteerEasingCatalog.Entry match = PuppeteerEasingCatalog.matchForSpec(entries, spec);
        if (match != null) {
            applySelection(match, false, match.isPreset());
        }
    }

    EasingSpec getSelectedSpec() {
        PuppeteerEasingCatalog.Entry value = getValue();
        return value != null ? value.spec() : EasingSpec.of(Easing.Type.LINEAR);
    }

    void refreshState() {
        boolean hasProject = projectRoot != null;
        boolean hasPresetSelection = getValue() != null && getValue().isPreset();
        String presetName = PuppeteerEasingPresetStore.normalizeName(presetNameField.getText());
        presetNameField.setDisable(!hasProject);
        btnSavePreset.setDisable(!hasProject || presetName.isBlank());
        btnUpdatePreset.setDisable(!hasProject || !hasPresetSelection || presetName.isBlank());
        btnDeletePreset.setDisable(!hasProject || !hasPresetSelection);
        presetNameField.setPromptText(hasProject
            ? "Preset name"
            : "Open a project to save presets");
    }

    private void configurePopup() {
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.setAutoFix(true);

        popupRoot.setPadding(new Insets(8));
        popupRoot.setMinWidth(360);
        popupRoot.setStyle(
            "-fx-background-color: #161616;"
                + "-fx-border-color: #3a3a3a;"
                + "-fx-border-radius: 4;"
                + "-fx-background-radius: 4;");

        searchField.setPromptText("Search easings and presets...");
        searchField.setStyle(FIELD_STYLE);

        listView.setPlaceholder(new Label("No matching easings"));
        listView.setCellFactory(list -> createCell(false));
        VBox.setVgrow(listView, Priority.ALWAYS);

        Label presetHeading = new Label("Project Presets");
        presetHeading.setStyle("-fx-text-fill: #8ea4c6; -fx-font-size: 10px; -fx-font-weight: bold;");

        presetNameField.setPromptText("Preset name");
        presetNameField.setStyle(FIELD_STYLE);
        HBox.setHgrow(presetNameField, Priority.ALWAYS);

        btnSavePreset.setStyle(BUTTON_STYLE);
        btnUpdatePreset.setStyle(BUTTON_STYLE);
        btnDeletePreset.setStyle(DELETE_BUTTON_STYLE);

        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-text-fill: #7f8796; -fx-font-size: 10px;");

        HBox presetActions = new HBox(6, presetNameField, btnSavePreset, btnUpdatePreset, btnDeletePreset);
        HBox.setHgrow(presetNameField, Priority.ALWAYS);
        popupRoot.getChildren().setAll(
            searchField,
            listView,
            new Separator(),
            presetHeading,
            presetActions,
            statusLabel
        );
        popup.getContent().setAll(popupRoot);

        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilter());
        searchField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                if (!listView.getItems().isEmpty()) {
                    listView.requestFocus();
                    int index = Math.max(0, listView.getSelectionModel().getSelectedIndex());
                    listView.getSelectionModel().select(index);
                    listView.scrollTo(index);
                }
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                commitSelection();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                hidePopup();
                requestFocus();
                event.consume();
            }
        });

        listView.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            commitSelection();
            event.consume();
        });
        listView.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                commitSelection();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                hidePopup();
                requestFocus();
                event.consume();
            }
        });

        presetNameField.textProperty().addListener((obs, oldValue, newValue) -> refreshState());
        presetNameField.setOnAction(event -> {
            savePreset(false);
            event.consume();
        });
        btnSavePreset.setOnAction(event -> savePreset(false));
        btnUpdatePreset.setOnAction(event -> savePreset(true));
        btnDeletePreset.setOnAction(event -> deleteSelectedPreset());
    }

    private void installPopupBridge() {
        addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (isDisabled() || event.getButton() != MouseButton.PRIMARY) return;
            event.consume();
            requestFocus();
            Platform.runLater(() -> {
                if (!isDisabled()) togglePopup();
            });
        });
        addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (isDisabled() || event.getButton() != MouseButton.PRIMARY) return;
            event.consume();
        });
        addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (isDisabled() || event.getButton() != MouseButton.PRIMARY) return;
            event.consume();
        });
        setOnShowing(event -> {
            event.consume();
            hide();
            hidePopup();
        });
        setOnHidden(event -> hidePopup());
        showingProperty().addListener((obs, wasShowing, showing) -> {
            if (!showing) return;
            Platform.runLater(() -> {
                if (isShowing()) hide();
            });
        });
        disabledProperty().addListener((obs, wasDisabled, isDisabled) -> {
            if (isDisabled) hidePopup();
        });
        setOnKeyPressed(event -> {
            if (isDisabled()) return;
            if (event.getCode() == KeyCode.DOWN
                || event.getCode() == KeyCode.UP
                || event.getCode() == KeyCode.SPACE
                || event.getCode() == KeyCode.ENTER
                || event.getCode() == KeyCode.F4) {
                event.consume();
                showPopup();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                hidePopup();
            }
        });
    }

    private ListCell<PuppeteerEasingCatalog.Entry> createCell(boolean buttonCell) {
        return new ListCell<>() {
            @Override
            protected void updateItem(PuppeteerEasingCatalog.Entry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    setStyle("");
                    return;
                }
                String text = buttonCell && !item.isPreset()
                    ? item.label()
                    : item.label() + "  [" + item.badge() + "]";
                setText(text);
                setTooltip(new Tooltip(item.spec().toDslString()));
                setStyle(item.isPreset() ? "-fx-text-fill: #f0d98a;" : "");
            }
        };
    }

    private void togglePopup() {
        if (popup.isShowing()) {
            hidePopup();
            return;
        }
        showPopup();
    }

    private void showPopup() {
        if (isDisabled()) return;
        applyFilter();
        selectCurrentValue();

        Bounds bounds = localToScreen(getBoundsInLocal());
        if (bounds == null) return;
        double width = Math.max(460.0, bounds.getWidth() + 140.0);
        popupRoot.setPrefWidth(width);
        popupRoot.setMaxWidth(width);
        listView.setPrefWidth(width - 16.0);
        listView.setPrefHeight(Math.min(360.0, Math.max(170.0, listView.getItems().size() * 28.0 + 12.0)));

        popup.show(this, bounds.getMinX(), bounds.getMaxY());
        Platform.runLater(searchField::requestFocus);
    }

    private void hidePopup() {
        popup.hide();
    }

    private void applyFilter() {
        List<PuppeteerEasingCatalog.Entry> filtered = PuppeteerEasingCatalog.filter(entries, searchField.getText());
        listView.getItems().setAll(filtered);
        if (!filtered.isEmpty() && listView.getSelectionModel().isEmpty()) {
            listView.getSelectionModel().select(0);
        }
    }

    private void selectCurrentValue() {
        PuppeteerEasingCatalog.Entry current = getValue();
        if (current == null) {
            if (!listView.getItems().isEmpty()) {
                listView.getSelectionModel().select(0);
                listView.scrollTo(0);
            }
            return;
        }
        int index = listView.getItems().indexOf(current);
        if (index >= 0) {
            listView.getSelectionModel().select(index);
            listView.scrollTo(index);
        } else if (!listView.getItems().isEmpty()) {
            listView.getSelectionModel().select(0);
            listView.scrollTo(0);
        }
    }

    private void commitSelection() {
        PuppeteerEasingCatalog.Entry selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        applySelection(selected, true, selected.isPreset());
        hidePopup();
        requestFocus();
    }

    private void applySelection(PuppeteerEasingCatalog.Entry entry, boolean fireAction, boolean syncPresetName) {
        if (entry == null) return;
        PuppeteerEasingCatalog.Entry previous = getValue();
        updatingValue = true;
        try {
            getSelectionModel().select(entry);
            super.setValue(entry);
        } finally {
            updatingValue = false;
        }
        if (syncPresetName && entry.isPreset()) {
            presetNameField.setText(entry.label());
        }
        refreshState();
        if (fireAction && !Objects.equals(previous, entry)) {
            fireEvent(new javafx.event.ActionEvent());
        }
    }

    private void reloadPresets() {
        presets.clear();
        if (projectRoot != null) {
            presets.addAll(PuppeteerEasingPresetStore.load(projectRoot));
        }
        rebuildEntries();
        setCurrentSpec(getSelectedSpec());
        if (projectRoot == null) {
            setStatus("Open a project to save easing presets.", false);
        } else if (presets.isEmpty()) {
            setStatus("No project presets yet. Save the current easing to "
                + PuppeteerEasingPresetStore.CONFIG_PATH + ".", false);
        } else {
            setStatus(presets.size() + " project preset"
                + (presets.size() == 1 ? "" : "s") + " loaded from "
                + PuppeteerEasingPresetStore.CONFIG_PATH + ".", false);
        }
        refreshState();
    }

    private void rebuildEntries() {
        EasingSpec currentSpec = getSelectedSpec();
        entries = PuppeteerEasingCatalog.buildEntries(presets);
        getItems().setAll(entries);
        PuppeteerEasingCatalog.Entry match = PuppeteerEasingCatalog.matchForSpec(entries, currentSpec);
        if (match != null) {
            applySelection(match, false, match.isPreset());
        }
        if (popup.isShowing()) {
            applyFilter();
            selectCurrentValue();
        }
    }

    private void savePreset(boolean updateExisting) {
        if (projectRoot == null) {
            setStatus("Open a project before saving easing presets.", true);
            refreshState();
            return;
        }
        String name = PuppeteerEasingPresetStore.normalizeName(presetNameField.getText());
        if (name.isBlank()) {
            setStatus("Preset name cannot be blank.", true);
            refreshState();
            return;
        }
        EasingSpec spec = resolveCurrentSpec();
        if (spec == null) {
            setStatus("No easing is available to save yet.", true);
            refreshState();
            return;
        }

        if (updateExisting) {
            PuppeteerEasingCatalog.Entry selected = getValue();
            if (selected == null || !selected.isPreset()) {
                setStatus("Select a project preset to update.", true);
                refreshState();
                return;
            }
            List<PuppeteerEasingPresetStore.Preset> updated = new ArrayList<>(presets);
            int index = indexOfPreset(selected.id());
            if (index < 0) {
                setStatus("The selected preset is no longer loaded.", true);
                refreshState();
                return;
            }
            for (PuppeteerEasingPresetStore.Preset preset : updated) {
                if (!("preset:" + preset.id()).equals(selected.id())
                    && preset.name().equalsIgnoreCase(name)) {
                    setStatus("Preset '" + name + "' already exists.", true);
                    refreshState();
                    return;
                }
            }
            PuppeteerEasingPresetStore.Preset updatedPreset =
                new PuppeteerEasingPresetStore.Preset(stripPresetPrefix(selected.id()), name, spec);
            updated.set(index, updatedPreset);
            if (persistPresets(updated, "Updated easing preset '" + name + "'.")) {
                presetNameField.setText(name);
                setCurrentSpec(spec);
            }
            return;
        }

        for (PuppeteerEasingPresetStore.Preset preset : presets) {
            if (preset.name().equalsIgnoreCase(name)) {
                setStatus("Preset '" + name + "' already exists. Use Update instead.", true);
                refreshState();
                return;
            }
        }
        List<PuppeteerEasingPresetStore.Preset> updated = new ArrayList<>(presets);
        updated.add(new PuppeteerEasingPresetStore.Preset(
            PuppeteerEasingPresetStore.uniqueId(name, updated, null),
            name,
            spec
        ));
        if (persistPresets(updated, "Saved easing preset '" + name + "'.")) {
            presetNameField.setText(name);
            setCurrentSpec(spec);
        }
    }

    private void deleteSelectedPreset() {
        if (projectRoot == null) {
            setStatus("Open a project before deleting easing presets.", true);
            refreshState();
            return;
        }
        PuppeteerEasingCatalog.Entry selected = getValue();
        if (selected == null || !selected.isPreset()) {
            setStatus("Select a project preset to delete.", true);
            refreshState();
            return;
        }
        List<PuppeteerEasingPresetStore.Preset> updated = new ArrayList<>(presets);
        boolean removed = updated.removeIf(preset -> preset.id().equals(stripPresetPrefix(selected.id())));
        if (!removed) {
            setStatus("The selected preset is no longer loaded.", true);
            refreshState();
            return;
        }
        EasingSpec fallbackSpec = EasingSpec.of(selected.spec().getType());
        if (persistPresets(updated, "Deleted easing preset '" + selected.label() + "'.")) {
            presetNameField.setText("");
            setCurrentSpec(fallbackSpec);
        }
    }

    private boolean persistPresets(List<PuppeteerEasingPresetStore.Preset> updated, String successMessage) {
        try {
            PuppeteerEasingPresetStore.save(projectRoot, updated);
            presets.clear();
            presets.addAll(updated);
            rebuildEntries();
            setStatus(successMessage, false);
            return true;
        } catch (IOException ex) {
            setStatus("Failed to save easing presets: " + rootMessage(ex), true);
            return false;
        }
    }

    private EasingSpec resolveCurrentSpec() {
        try {
            EasingSpec spec = currentSpecSupplier != null ? currentSpecSupplier.get() : null;
            return spec != null ? spec : getSelectedSpec();
        } catch (RuntimeException ex) {
            setStatus("Unable to read current easing: " + rootMessage(ex), true);
            return null;
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

    private void setStatus(String message, boolean error) {
        statusLabel.setText(message != null ? message : "");
        statusLabel.setStyle(error
            ? "-fx-text-fill: #e6a8b3; -fx-font-size: 10px;"
            : "-fx-text-fill: #7f8796; -fx-font-size: 10px;");
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message.trim();
    }
}
