package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PuppeteerWorkspacePrefsTest {

    @Test
    void uiScaleRoundTripsThroughWorkspacePrefs(@TempDir Path tempDir) {
        PuppeteerWorkspacePrefs prefs = PuppeteerWorkspacePrefs.load(tempDir.toFile());
        prefs.setDouble(PuppeteerWorkspacePrefs.KEY_UI_SCALE, 1.25);
        prefs.save();

        PuppeteerWorkspacePrefs loaded = PuppeteerWorkspacePrefs.load(tempDir.toFile());

        assertEquals(1.25, loaded.getDouble(PuppeteerWorkspacePrefs.KEY_UI_SCALE).orElseThrow(), 0.0001);
    }

    @Test
    void dockLayoutStringsRoundTripThroughWorkspacePrefs(@TempDir Path tempDir) {
        PuppeteerWorkspacePrefs prefs = PuppeteerWorkspacePrefs.load(tempDir.toFile());
        prefs.setString(PuppeteerWorkspacePrefs.KEY_TOOLBAR_LAYOUT_MODE, "COMPACT");
        prefs.setString(PuppeteerWorkspacePrefs.KEY_DOCK_SLOT_PREFIX + "entities", "entities|code");
        prefs.setString(PuppeteerWorkspacePrefs.KEY_DOCK_TOOLBAR_ORDER, "transport,preview");
        prefs.setString(PuppeteerWorkspacePrefs.KEY_DOCK_HIDDEN_ITEMS, "timeline-panel");
        prefs.setString(PuppeteerWorkspacePrefs.KEY_DOCK_DYNAMIC_SLOTS, "custom-dock-1@workspace-top");
        prefs.setString(PuppeteerWorkspacePrefs.KEY_FLOATING_DOCKER_PREFIX + "toolbar-transport", "14.0,18.0");
        prefs.setString(PuppeteerWorkspacePrefs.KEY_WORKSPACE_PRESET_ORDER, "compact");
        prefs.setString(PuppeteerWorkspacePrefs.KEY_WORKSPACE_PRESET_PREFIX + "compact.name", "Compact");
        prefs.setString(PuppeteerWorkspacePrefs.KEY_WORKSPACE_PRESET_PREFIX + "compact.payload", "encoded");
        prefs.save();

        PuppeteerWorkspacePrefs loaded = PuppeteerWorkspacePrefs.load(tempDir.toFile());

        assertEquals("entities|code", loaded.getString(PuppeteerWorkspacePrefs.KEY_DOCK_SLOT_PREFIX + "entities").orElseThrow());
        assertEquals("COMPACT", loaded.getString(PuppeteerWorkspacePrefs.KEY_TOOLBAR_LAYOUT_MODE).orElseThrow());
        assertEquals("transport,preview", loaded.getString(PuppeteerWorkspacePrefs.KEY_DOCK_TOOLBAR_ORDER).orElseThrow());
        assertEquals("timeline-panel", loaded.getString(PuppeteerWorkspacePrefs.KEY_DOCK_HIDDEN_ITEMS).orElseThrow());
        assertEquals("custom-dock-1@workspace-top", loaded.getString(PuppeteerWorkspacePrefs.KEY_DOCK_DYNAMIC_SLOTS).orElseThrow());
        assertEquals("14.0,18.0", loaded.getString(PuppeteerWorkspacePrefs.KEY_FLOATING_DOCKER_PREFIX + "toolbar-transport").orElseThrow());
        assertEquals("compact", loaded.getString(PuppeteerWorkspacePrefs.KEY_WORKSPACE_PRESET_ORDER).orElseThrow());
        assertEquals("encoded", loaded.getString(PuppeteerWorkspacePrefs.KEY_WORKSPACE_PRESET_PREFIX + "compact.payload").orElseThrow());

        loaded.removeKeysStartingWith(PuppeteerWorkspacePrefs.KEY_FLOATING_DOCKER_PREFIX);
        assertFalse(loaded.getString(PuppeteerWorkspacePrefs.KEY_FLOATING_DOCKER_PREFIX + "toolbar-transport").isPresent());
    }

    @Test
    void uiScaleClampKeepsMenuValuesInSupportedRange() {
        assertEquals(0.80, PuppeteerWindow.clampUiScale(0.2), 0.0001);
        assertEquals(1.00, PuppeteerWindow.clampUiScale(Double.NaN), 0.0001);
        assertEquals(1.60, PuppeteerWindow.clampUiScale(2.5), 0.0001);
        assertEquals("125%", PuppeteerWindow.formatUiScaleLabel(1.25));
    }
}
