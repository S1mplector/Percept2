package com.jvn.core.vn.save;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * Migration and normalization helpers for persisted VN saves.
 */
public final class VnSaveMigration {
  private VnSaveMigration() {
  }

  public static final int LEGACY_SCHEMA_VERSION = 1;
  public static final int CURRENT_SCHEMA_VERSION = VnSaveData.CURRENT_SCHEMA_VERSION;

  /**
   * Upgrades save data in-memory to the current schema and normalizes nullable fields.
   *
   * @return true if a migration/normalization change was applied.
   */
  public static boolean migrateInPlace(VnSaveData data, String fallbackSaveName) {
    if (data == null) return false;
    boolean changed = false;

    int version = data.getSchemaVersion();
    if (version <= 0) {
      version = LEGACY_SCHEMA_VERSION;
      data.setSchemaVersion(version);
      changed = true;
    }

  // v1 -> v2
    if (version < 2) {
      if (data.getSaveTimestamp() <= 0) {
        data.setSaveTimestamp(System.currentTimeMillis());
      }
      if (data.getSettings() == null) {
        data.setSettings(new VnSaveData.SettingsData());
      }
      data.setSchemaVersion(2);
      version = 2;
      changed = true;
    }

    // v2 -> v3
    if (version < 3) {
      if (data.getCallStack() == null) {
        data.setCallStack(new ArrayList<>());
      }
      if (data.getGlobalPositionCharacters() == null) {
        data.setGlobalPositionCharacters(new HashSet<>());
      }
      if (data.getCharacterDefinedPositions() == null) {
        data.setCharacterDefinedPositions(new HashMap<>());
      }
      data.setSchemaVersion(3);
      version = 3;
      changed = true;
    }

    // v3 -> v4
    if (version < 4) {
      data.setSchemaVersion(4);
      version = 4;
      changed = true;
    }

    // v4 -> v5: visible character arrays may include display slot and custom position metadata.
    if (version < 5) {
      data.setSchemaVersion(5);
      version = 5;
      changed = true;
    }

    // v5 -> v6: display and accessibility preferences are included in settings snapshots.
    if (version < 6) {
      data.setSchemaVersion(6);
      version = 6;
      changed = true;
    }

    // Normalize common nullable fields across all schema versions.
    if (data.getVariables() == null) {
      data.setVariables(new HashMap<>());
      changed = true;
    }
    if (data.getReadNodes() == null) {
      data.setReadNodes(new HashSet<>());
      changed = true;
    }
    if (data.getVisibleCharacters() == null) {
      data.setVisibleCharacters(new HashMap<>());
      changed = true;
    }
    if (data.getCallStack() == null) {
      data.setCallStack(new ArrayList<>());
      changed = true;
    }
    if (data.getGlobalPositionCharacters() == null) {
      data.setGlobalPositionCharacters(new HashSet<>());
      changed = true;
    }
    if (data.getCharacterDefinedPositions() == null) {
      data.setCharacterDefinedPositions(new HashMap<>());
      changed = true;
    }
    if (data.getSettings() == null) {
      data.setSettings(new VnSaveData.SettingsData());
      changed = true;
    }
    if (data.getSaveName() == null || data.getSaveName().isBlank()) {
      if (fallbackSaveName != null && !fallbackSaveName.isBlank()) {
        data.setSaveName(fallbackSaveName);
        changed = true;
      }
    }
    if (data.getSaveTimestamp() <= 0) {
      data.setSaveTimestamp(System.currentTimeMillis());
      changed = true;
    }
    if (data.getSchemaVersion() < CURRENT_SCHEMA_VERSION) {
      data.setSchemaVersion(CURRENT_SCHEMA_VERSION);
      changed = true;
    }
    return changed;
  }
}
