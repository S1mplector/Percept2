package com.jvn.core.vn;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class NoopPersistenceBackendTest {

  @Test
  void readReturnsNull() {
    assertNull(new NoopPersistenceBackend().read());
  }

  @Test
  void writeDoesNotThrowAndHasNoObservableEffect() {
    NoopPersistenceBackend backend = new NoopPersistenceBackend();
    assertDoesNotThrow(() -> backend.write("{\"a\":1}"));
    assertNull(backend.read(), "write() must not cause a subsequent read() to return anything");
  }
}
