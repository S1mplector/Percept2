package com.jvn.core.vn;

import com.jvn.core.vn.save.VnSaveData;
import com.jvn.core.vn.save.VnSaveManager;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class VnSavePersistenceTest {
  @Test
  public void savesAndLoadsExtendedState() throws Exception {
    // Build a tiny scenario
    VnScenario scenario = new VnScenarioBuilder("persist_story")
        .addCharacter("alice", "Alice")
        .addBackground("room", "game/images/bg_room.png")
        .background("room")
        .dialogue("Alice", "Hello", "alice", "neutral", CharacterPosition.LEFT)
        .end()
        .build();

    // Prepare state
    VnState state = new VnState();
    state.setScenario(scenario);
    state.setCurrentBackgroundId("room");
    state.setCurrentNodeIndex(1);
    state.setVariable("flag", true);
    state.markNodeAsRead(0);
    state.markNodeAsRead(1);
    state.showCharacter(CharacterPosition.LEFT, "alice", "neutral");
    state.setSkipMode(true);
    state.setAutoPlayMode(false);
    state.setUiHidden(true);

    VnSettings s = state.getSettings();
    s.setTextSpeed(40);
    s.setBgmVolume(0.6f);
    s.setSfxVolume(0.5f);
    s.setVoiceVolume(0.4f);
    s.setAutoPlayDelay(2500);
    s.setSkipUnreadText(true);
    s.setSkipAfterChoices(false);

    // Use temp dir for saves
    Path tmp = Files.createTempDirectory("vn_saves_test");
    VnSaveManager mgr = new VnSaveManager(tmp.toString());

    mgr.save(state, "slot1");
    VnSaveData data = mgr.load("slot1");

    VnState loaded = new VnState();
    loaded.setScenario(scenario);
    mgr.applyToState(data, loaded);

    assertEquals(state.getCurrentNodeIndex(), loaded.getCurrentNodeIndex());
    assertEquals(state.getCurrentBackgroundId(), loaded.getCurrentBackgroundId());
    assertEquals(state.isSkipMode(), loaded.isSkipMode());
    assertEquals(state.isAutoPlayMode(), loaded.isAutoPlayMode());
    assertEquals(state.isUiHidden(), loaded.isUiHidden());

    assertEquals(state.getVariables().get("flag"), loaded.getVariables().get("flag"));
    assertEquals(state.getReadNodes(), loaded.getReadNodes());

    assertEquals(s.getTextSpeed(), loaded.getSettings().getTextSpeed());
    assertEquals(s.getBgmVolume(), loaded.getSettings().getBgmVolume(), 0.001);
    assertEquals(s.getSfxVolume(), loaded.getSettings().getSfxVolume(), 0.001);
    assertEquals(s.getVoiceVolume(), loaded.getSettings().getVoiceVolume(), 0.001);
    assertEquals(s.getAutoPlayDelay(), loaded.getSettings().getAutoPlayDelay());
    assertEquals(s.isSkipUnreadText(), loaded.getSettings().isSkipUnreadText());
    assertEquals(s.isSkipAfterChoices(), loaded.getSettings().isSkipAfterChoices());

    // Visible characters
    assertTrue(loaded.getVisibleCharacters().containsKey(CharacterPosition.LEFT));
    assertEquals("alice", loaded.getVisibleCharacters().get(CharacterPosition.LEFT).getCharacterId());
  }
}
