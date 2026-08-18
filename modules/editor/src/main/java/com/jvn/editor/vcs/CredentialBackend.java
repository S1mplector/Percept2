package com.jvn.editor.vcs;

import java.io.IOException;
import java.util.Optional;

/**
 * A place a GitHub token can be stored and retrieved from. Implementations
 * back this with either an OS-native credential store or a local encrypted
 * file.
 */
public interface CredentialBackend {
  Optional<String> load() throws IOException;

  void save(String token) throws IOException;

  void clear() throws IOException;

  boolean exists();
}
