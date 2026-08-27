package com.jvn.core.vn;

/** In-memory no-op default — used until a platform wires in a real backend via {@link VnPersistentStore#setBackend}. */
final class NoopPersistenceBackend implements VnPersistenceBackend {
  @Override
  public String read() {
    return null;
  }

  @Override
  public void write(String json) {
    // Intentionally discarded — this is the safe default before any real backend is wired in.
  }
}
