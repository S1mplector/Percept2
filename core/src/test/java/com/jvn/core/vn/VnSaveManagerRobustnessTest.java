package com.jvn.core.vn;

import com.jvn.core.vn.save.VnSaveData;
import com.jvn.core.vn.save.VnSaveManager;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VnSaveManagerRobustnessTest {
  @Test
  void migratesLegacySaveSchemaOnLoad() throws Exception {
    Path dir = Files.createTempDirectory("vn_save_migration");
    VnSaveData legacy = new VnSaveData();
    legacy.setSchemaVersion(0); // Simulate old save with no schema field persisted.
    legacy.setSaveName(null);
    legacy.setSaveTimestamp(0);
    legacy.setVariables(null);
    legacy.setReadNodes(null);
    legacy.setVisibleCharacters(null);
    legacy.setSettings(null);
    legacy.setScenarioId("legacy_story");
    legacy.setCurrentNodeIndex(2);

    Path legacyPath = dir.resolve("legacy.sav");
    try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(legacyPath.toFile()))) {
      oos.writeObject(legacy);
    }

    VnSaveManager mgr = new VnSaveManager(dir.toString());
    VnSaveData loaded = mgr.load("legacy");

    assertNotNull(loaded);
    assertEquals(VnSaveData.CURRENT_SCHEMA_VERSION, loaded.getSchemaVersion());
    assertEquals("legacy", loaded.getSaveName());
    assertTrue(loaded.getSaveTimestamp() > 0);
    assertNotNull(loaded.getVariables());
    assertNotNull(loaded.getReadNodes());
    assertNotNull(loaded.getVisibleCharacters());
    assertNotNull(loaded.getSettings());
  }

  @Test
  void writesSaveFilesAtomicallyWithoutTempResidue() throws Exception {
    Path dir = Files.createTempDirectory("vn_atomic_save");
    VnSaveManager mgr = new VnSaveManager(dir.toString());
    VnState state = createState("atomic_story");

    mgr.save(state, "slot_a");
    state.setVariable("tick", 1);
    mgr.save(state, "slot_a"); // overwrite existing file

    Path savePath = dir.resolve("slot_a.sav");
    assertTrue(Files.exists(savePath));
    assertTrue(Files.size(savePath) > 0);

    List<Path> leftovers;
    try (var stream = Files.list(dir)) {
      leftovers = stream
          .filter(p -> p.getFileName().toString().contains(".tmp"))
          .toList();
    }
    assertTrue(leftovers.isEmpty(), "Temporary files should not remain after save");

    VnSaveData loaded = mgr.load("slot_a");
    assertNotNull(loaded);
    assertEquals("atomic_story", loaded.getScenarioId());
  }

  @Test
  void rotatesAutosaveSlotsAndLoadsLatest() throws Exception {
    Path dir = Files.createTempDirectory("vn_autosave_slots");
    VnSaveManager mgr = new VnSaveManager(dir.toString());
    VnState state = createState("autosave_story");

    for (int i = 0; i < 8; i++) {
      state.setVariable("step", i);
      String slot = mgr.autosave(state);
      assertTrue(slot.startsWith("_autosave_"));
      Thread.sleep(2);
    }

    List<String> autos = mgr.listAutoSaves();
    assertFalse(autos.isEmpty());
    assertTrue(autos.size() <= mgr.getAutosaveSlotCount());
    for (int i = 0; i < mgr.getAutosaveSlotCount(); i++) {
      assertTrue(autos.contains(mgr.getAutosaveSlotName(i)));
    }

    VnSaveData latest = mgr.loadLatestAutoSave();
    assertNotNull(latest);
    assertEquals("autosave_story", latest.getScenarioId());
    assertEquals(7, ((Number) latest.getVariables().get("step")).intValue());
  }

  private VnState createState(String scenarioId) {
    VnScenario scenario = new VnScenarioBuilder(scenarioId)
        .label("start")
        .dialogue("Narrator", "Line")
        .end()
        .build();

    VnState state = new VnState();
    state.setScenario(scenario);
    state.setCurrentNodeIndex(1);
    state.setCurrentBackgroundId("none");
    return state;
  }
}
