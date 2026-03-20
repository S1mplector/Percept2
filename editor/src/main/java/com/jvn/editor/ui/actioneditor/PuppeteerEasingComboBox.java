package com.jvn.editor.ui.actioneditor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;

import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;

final class PuppeteerEasingComboBox extends ComboBox<PuppeteerEasingCatalog.Entry> {
    private static final String OWNER_STYLE =
        "-fx-background-color: #121212; -fx-text-fill: #e6e6e6; -fx-border-color: #3a3a3a; " +
        "-fx-border-radius: 3; -fx-background-radius: 3; -fx-padding: 2 4; -fx-font-size: 11px;";

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
            setCurrentSpec(resolved);
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
}
