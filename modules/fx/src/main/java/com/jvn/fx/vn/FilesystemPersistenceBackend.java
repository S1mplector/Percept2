package com.jvn.fx.vn;

import com.jvn.core.vn.VnPersistenceBackend;
import com.jvn.core.vn.VnStoragePaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Desktop filesystem-backed {@link VnPersistenceBackend}, wired in via {@code VnScene}/{@code VnState}'s persistent store. */
public final class FilesystemPersistenceBackend implements VnPersistenceBackend {
  private final Path file;

  public FilesystemPersistenceBackend() {
    this(VnStoragePaths.persistentData());
  }

  public FilesystemPersistenceBackend(Path file) {
    this.file = file;
  }

  @Override
  public String read() {
    if (file == null || !Files.exists(file)) return null;
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return null;
    }
  }

  @Override
  public void write(String json) {
    if (file == null) return;
    try {
      Path parent = file.getParent();
      if (parent != null) Files.createDirectories(parent);
      Files.writeString(file, json, StandardCharsets.UTF_8);
    } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
    }
  }
}
