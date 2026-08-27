package com.jvn.core.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VnPersistentStoreTest {

  @Test
  void noArgConstructorDefaultsToNoopBackendAndNeverThrows() {
    VnPersistentStore store = new VnPersistentStore();
    store.put("unlocked_gallery_1", true);
    assertEquals(Boolean.TRUE, store.get("unlocked_gallery_1"));
    // A fresh store with the same no-op default sees none of the above —
    // proving nothing was actually persisted to any shared/real storage.
    VnPersistentStore secondStore = new VnPersistentStore();
    assertNull(secondStore.get("unlocked_gallery_1"));
  }

  @Test
  void setBackendRoutesLoadAndSaveThroughTheGivenBackend() {
    RecordingBackend backend = new RecordingBackend();
    VnPersistentStore store = new VnPersistentStore();
    store.setBackend(backend);

    store.put("score", 42);

    assertEquals("{\n  \"score\": 42\n}", backend.written);
  }

  @Test
  void setBackendNullCoercesToNoopRatherThanThrowing() {
    VnPersistentStore store = new VnPersistentStore();
    store.setBackend(null);
    // Must not throw on the next put()/save() cycle despite the null backend.
    store.put("k", "v");
    assertEquals("v", store.get("k"));
  }

  @Test
  void pathConstructorStillWorksAndRoundTripsThroughARealFile(@TempDir Path tempDir) {
    Path file = tempDir.resolve("persistent.json");
    VnPersistentStore store = new VnPersistentStore(file);
    store.put("unlocked", true);

    assertEquals(true, Files.exists(file), "expected the Path-arg constructor to still write a real file");

    VnPersistentStore reloaded = new VnPersistentStore(file);
    assertEquals(Boolean.TRUE, reloaded.get("unlocked"));
  }

  @Test
  void getBackendReturnsWhateverWasLastSet() {
    RecordingBackend backend = new RecordingBackend();
    VnPersistentStore store = new VnPersistentStore();
    store.setBackend(backend);
    assertEquals(backend, store.getBackend());
  }

  private static final class RecordingBackend implements VnPersistenceBackend {
    String written;

    @Override
    public String read() {
      return null;
    }

    @Override
    public void write(String json) {
      this.written = json;
    }
  }
}
