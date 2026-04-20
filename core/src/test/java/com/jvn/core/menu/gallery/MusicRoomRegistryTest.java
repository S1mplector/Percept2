package com.jvn.core.menu.gallery;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jvn.core.vn.VnPersistentStore;

class MusicRoomRegistryTest {

  @Test
  void parseBasicTracks() throws IOException {
    String content = """
        track.ids=theme,battle
        track.theme.audio=assets/audio/bgm/theme.ogg
        track.theme.title=Main Theme
        track.theme.artist=Composer
        track.theme.category=BGM
        track.theme.order=0
        track.battle.audio=assets/audio/bgm/battle.ogg
        track.battle.title=Battle Theme
        track.battle.category=BGM
        track.battle.order=1
        """;
    MusicRoomRegistry reg = MusicRoomRegistry.parse(
        new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    assertEquals(2, reg.entries().size());
    assertEquals("theme", reg.entries().get(0).id());
    assertEquals("battle", reg.entries().get(1).id());
    assertEquals("Composer", reg.entries().get(0).artist());
  }

  @Test
  void unlockAndQuery(@TempDir Path tempDir) {
    Map<String, String> props = new LinkedHashMap<>();
    props.put("track.ids", "t1");
    props.put("track.t1.audio", "test.ogg");
    MusicRoomRegistry reg = MusicRoomRegistry.parseProperties(props);

    VnPersistentStore store = new VnPersistentStore(tempDir.resolve("persistent.json"));
    MusicRoomEntry entry = reg.entries().get(0);
    assertFalse(reg.isUnlocked(entry, store));

    reg.unlock(entry, store);
    assertTrue(reg.isUnlocked(entry, store));
  }

  @Test
  void findByAudioPath() {
    Map<String, String> props = new LinkedHashMap<>();
    props.put("track.ids", "x");
    props.put("track.x.audio", "assets/audio/track.ogg");
    MusicRoomRegistry reg = MusicRoomRegistry.parseProperties(props);
    assertNotNull(reg.findByAudioPath("assets/audio/track.ogg"));
    assertNull(reg.findByAudioPath("assets/audio/other.ogg"));
  }

  @Test
  void defaultUnlockFlag() {
    MusicRoomEntry e = new MusicRoomEntry("myTrack", "t.ogg", "Title", "Artist", "BGM", 0, null);
    assertEquals("music.unlocked.myTrack", e.unlockFlag());
  }

  @Test
  void emptyRegistryReturnsEmpty() {
    MusicRoomRegistry reg = MusicRoomRegistry.parseProperties(Map.of());
    assertTrue(reg.isEmpty());
  }
}
