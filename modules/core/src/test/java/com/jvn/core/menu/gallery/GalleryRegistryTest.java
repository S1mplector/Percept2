package com.jvn.core.menu.gallery;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.jvn.core.vn.VnPersistentStore;

class GalleryRegistryTest {

  @Test
  void parseBasicEntries() throws IOException {
    String content = """
        entry.ids=sunset,classroom
        entry.sunset.image=assets/cg/sunset.png
        entry.sunset.category=Chapter 3
        entry.sunset.order=1
        entry.classroom.image=assets/cg/classroom.png
        entry.classroom.category=Chapter 1
        entry.classroom.order=0
        """;
    GalleryRegistry reg = GalleryRegistry.parse(
        new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    assertEquals(2, reg.entries().size());
    // Sorted by order then id: classroom(0) first, sunset(1) second
    assertEquals("classroom", reg.entries().get(0).id());
    assertEquals("sunset", reg.entries().get(1).id());
    assertEquals("Chapter 1", reg.entries().get(0).category());
  }

  @Test
  void byCategoryGroupsCorrectly() throws IOException {
    String content = """
        entry.ids=a,b,c
        entry.a.image=a.png
        entry.a.category=Cat1
        entry.a.order=0
        entry.b.image=b.png
        entry.b.category=Cat2
        entry.b.order=0
        entry.c.image=c.png
        entry.c.category=Cat1
        entry.c.order=1
        """;
    GalleryRegistry reg = GalleryRegistry.parse(
        new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    Map<String, List<GalleryEntry>> cats = reg.byCategory();
    assertEquals(2, cats.size());
    assertTrue(cats.containsKey("Cat1"));
    assertTrue(cats.containsKey("Cat2"));
    assertEquals(2, cats.get("Cat1").size());
    assertEquals(1, cats.get("Cat2").size());
  }

  @Test
  void unlockAndQuery(@TempDir Path tempDir) {
    Map<String, String> props = new LinkedHashMap<>();
    props.put("entry.ids", "test1");
    props.put("entry.test1.image", "img.png");
    GalleryRegistry reg = GalleryRegistry.parseProperties(props);

    VnPersistentStore store = new VnPersistentStore(tempDir.resolve("persistent.json"));
    GalleryEntry entry = reg.entries().get(0);
    assertFalse(reg.isUnlocked(entry, store));
    assertEquals(0, reg.unlockedCount(store));

    reg.unlock(entry, store);
    assertTrue(reg.isUnlocked(entry, store));
    assertEquals(1, reg.unlockedCount(store));
  }

  @Test
  void findByImagePath() {
    Map<String, String> props = new LinkedHashMap<>();
    props.put("entry.ids", "x");
    props.put("entry.x.image", "assets/cg/test.png");
    GalleryRegistry reg = GalleryRegistry.parseProperties(props);
    assertNotNull(reg.findByImagePath("assets/cg/test.png"));
    assertNull(reg.findByImagePath("assets/cg/other.png"));
  }

  @Test
  void emptyRegistryReturnsEmpty() {
    GalleryRegistry reg = GalleryRegistry.parseProperties(Map.of());
    assertTrue(reg.isEmpty());
    assertEquals(0, reg.entries().size());
  }

  @Test
  void customUnlockFlag() {
    Map<String, String> props = new LinkedHashMap<>();
    props.put("entry.ids", "t1");
    props.put("entry.t1.image", "t.png");
    props.put("entry.t1.unlockFlag", "custom.flag");
    GalleryRegistry reg = GalleryRegistry.parseProperties(props);
    assertEquals("custom.flag", reg.entries().get(0).unlockFlag());
  }

  @Test
  void defaultUnlockFlagGenerated() {
    GalleryEntry e = new GalleryEntry("myId", "img.png", "Cat", 0);
    assertEquals("gallery.unlocked.myId", e.unlockFlag());
  }
}
