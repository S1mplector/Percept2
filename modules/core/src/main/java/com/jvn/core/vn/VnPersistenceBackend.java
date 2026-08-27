package com.jvn.core.vn;

/** Platform-specific storage for {@link VnPersistentStore}'s serialized JSON. */
public interface VnPersistenceBackend {
  /** @return the stored JSON, or {@code null}/blank if nothing is stored yet. */
  String read();

  /** Persists {@code json} as the store's full contents. Best-effort; failures are swallowed. */
  void write(String json);
}
