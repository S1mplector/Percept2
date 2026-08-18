package com.jvn.editor.vcs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Stores a GitHub personal access token, preferring the current OS's native credential store
 * (Windows Credential Manager, macOS Keychain, or Linux Secret Service) and falling back to a
 * locally-encrypted file when no native store is available. Tokens previously saved by the
 * legacy AES-file-only implementation are migrated into the native store transparently on the
 * first successful load.
 */
public final class GitHubTokenStore {

  public enum StorageBackend { AUTO, OS_KEYSTORE, AES_FILE }

  private final Path legacyTokenFile;
  private final Path legacyKeyFile;
  private final StorageBackend requestedBackend;

  private CredentialBackend resolvedBackend;
  private boolean downgradedToFileBackend;

  public GitHubTokenStore() {
    this(AesFileBackend.defaultTokenFilePath(), AesFileBackend.defaultKeyFilePath(), StorageBackend.AUTO);
  }

  public GitHubTokenStore(StorageBackend backend) {
    this(AesFileBackend.defaultTokenFilePath(), AesFileBackend.defaultKeyFilePath(), backend);
  }

  public GitHubTokenStore(Path tokenFile, Path keyFile, StorageBackend backend) {
    this.legacyTokenFile = tokenFile;
    this.legacyKeyFile = keyFile;
    this.requestedBackend = backend;
  }

  /** Test-only seam: forces a specific fake {@link CredentialBackend} in place of OS resolution. */
  static GitHubTokenStore forTesting(Path tokenFile, Path keyFile, CredentialBackend fakeOsBackend) {
    GitHubTokenStore store = new GitHubTokenStore(tokenFile, keyFile, StorageBackend.OS_KEYSTORE);
    store.resolvedBackend = fakeOsBackend;
    return store;
  }

  public boolean hasToken() {
    CredentialBackend backend = resolveBackend();
    if (backend.exists()) return true;
    return legacyFileBackend().exists();
  }

  public void saveToken(String token) throws IOException {
    CredentialBackend backend = resolveBackend();
    backend.save(token);
  }

  public Optional<String> loadToken() {
    CredentialBackend backend = resolveBackend();
    AesFileBackend legacy = legacyFileBackend();

    if (!backend.exists()) {
      return migrateFromLegacyIfPresent(backend, legacy);
    }

    try {
      Optional<String> loaded = backend.load();
      if (loaded.isPresent()) return loaded;
      return migrateFromLegacyIfPresent(backend, legacy);
    } catch (IOException ex) {
      Optional<String> legacyToken = legacy.load();
      if (legacyToken.isPresent()) return legacyToken;
      return Optional.empty();
    }
  }

  public void clearToken() throws IOException {
    CredentialBackend backend = resolveBackend();
    backend.clear();
    legacyFileBackend().clear();
  }

  private Optional<String> migrateFromLegacyIfPresent(CredentialBackend backend, AesFileBackend legacy) {
    Optional<String> legacyToken = legacy.load();
    if (legacyToken.isEmpty()) return Optional.empty();
    try {
      backend.save(legacyToken.get());
      legacy.clear();
    } catch (IOException ex) {
      // Migration write failed; still return the legacy token this once rather than losing it.
      return legacyToken;
    }
    return legacyToken;
  }

  private AesFileBackend legacyFileBackend() {
    return new AesFileBackend(legacyTokenFile, legacyKeyFile);
  }

  private CredentialBackend resolveBackend() {
    if (resolvedBackend != null) return resolvedBackend;

    if (requestedBackend == StorageBackend.AES_FILE) {
      resolvedBackend = legacyFileBackend();
      return resolvedBackend;
    }

    if (requestedBackend == StorageBackend.OS_KEYSTORE) {
      resolvedBackend = osNativeBackendForCurrentPlatform();
      return resolvedBackend;
    }

    // AUTO
    if (downgradedToFileBackend) {
      resolvedBackend = legacyFileBackend();
      return resolvedBackend;
    }

    try {
      CredentialBackend native_ = osNativeBackendForCurrentPlatform();
      if (native_ == null) {
        downgradedToFileBackend = true;
        resolvedBackend = legacyFileBackend();
      } else {
        // Constructing the backend object alone cannot surface a JNA native-library-load
        // failure: for WindowsDpapiBackend/MacKeychainBackend, Native.load(...) runs inside
        // the static initializer of a nested interface (CredAdvapi32 / CoreFoundation &
        // Security), which only executes on first reference to that interface's INSTANCE
        // field — i.e. on the first real method call, not at `new ...Backend()` time. So a
        // cheap probe call is required here to actually trigger (and catch) that failure
        // during resolution, consistent with LinuxSecretServiceBackend's own explicit
        // isAvailable() probe, which already does this for libsecret. exists() is the
        // cheapest read-only, side-effect-free method every backend implements for this.
        native_.exists();
        resolvedBackend = native_;
      }
    } catch (RuntimeException | LinkageError ex) {
      downgradedToFileBackend = true;
      resolvedBackend = legacyFileBackend();
    }
    return resolvedBackend;
  }

  private CredentialBackend osNativeBackendForCurrentPlatform() {
    String os = System.getProperty("os.name", "").toLowerCase();
    if (os.contains("win")) return new WindowsDpapiBackend();
    if (os.contains("mac")) return new MacKeychainBackend();
    if (os.contains("nux") || os.contains("nix")) {
      LinuxSecretServiceBackend linux = new LinuxSecretServiceBackend();
      return linux.isAvailable() ? linux : null;
    }
    return null;
  }
}
