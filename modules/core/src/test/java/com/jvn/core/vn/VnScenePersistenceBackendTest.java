package com.jvn.core.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class VnScenePersistenceBackendTest {

  @Test
  void setPersistenceBackendRoutesThroughToTheUnderlyingPersistentStore() {
    VnScene scene = new VnScene(new VnScenarioBuilder("test").label("start").end().build());
    RecordingBackend backend = new RecordingBackend();

    scene.setPersistenceBackend(backend);
    scene.getState().getPersistentStore().put("k", "v");

    assertEquals(backend, scene.getState().getPersistentStore().getBackend());
    assertEquals(backend, scene.getPersistenceBackend());
    assertNotNull(backend.written);
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
