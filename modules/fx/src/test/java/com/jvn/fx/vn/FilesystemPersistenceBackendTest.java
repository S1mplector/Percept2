package com.jvn.fx.vn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemPersistenceBackendTest {

  @Test
  void readReturnsNullWhenFileDoesNotExist(@TempDir Path tempDir) {
    FilesystemPersistenceBackend backend = new FilesystemPersistenceBackend(tempDir.resolve("nope.json"));
    assertNull(backend.read());
  }

  @Test
  void writeThenReadRoundTrips(@TempDir Path tempDir) {
    Path file = tempDir.resolve("nested").resolve("persistent.json");
    FilesystemPersistenceBackend backend = new FilesystemPersistenceBackend(file);

    backend.write("{\"a\":1}");

    assertTrue(Files.exists(file), "expected write() to create parent directories and the file");
    assertEquals("{\"a\":1}", backend.read());
  }

  @Test
  void writeOverwritesPreviousContent(@TempDir Path tempDir) {
    Path file = tempDir.resolve("persistent.json");
    FilesystemPersistenceBackend backend = new FilesystemPersistenceBackend(file);

    backend.write("{\"a\":1}");
    backend.write("{\"a\":2}");

    assertEquals("{\"a\":2}", backend.read());
  }
}
