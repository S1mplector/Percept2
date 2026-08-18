package com.jvn.editor.vcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitHubTokenStoreTest {

  @TempDir
  Path tempDir;

  private GitHubTokenStore fileBackedStore() {
    return new GitHubTokenStore(tempDir.resolve("token.enc"), tempDir.resolve(".key"), GitHubTokenStore.StorageBackend.AES_FILE);
  }

  @Test
  void hasNoTokenBeforeAnythingIsSaved() {
    assertFalse(fileBackedStore().hasToken());
  }

  @Test
  void loadTokenReturnsEmptyWhenNothingIsSaved() {
    assertEquals(Optional.empty(), fileBackedStore().loadToken());
  }

  @Test
  void savedTokenRoundTripsExactlyThroughAesFileBackend() throws Exception {
    GitHubTokenStore store = fileBackedStore();
    store.saveToken("ghp_exampleToken123");

    assertTrue(store.hasToken());
    assertEquals(Optional.of("ghp_exampleToken123"), store.loadToken());
  }

  @Test
  void clearTokenRemovesTokenAndLoadReturnsEmpty() throws Exception {
    GitHubTokenStore store = fileBackedStore();
    store.saveToken("ghp_exampleToken123");
    assertTrue(store.hasToken());

    store.clearToken();

    assertFalse(store.hasToken());
    assertEquals(Optional.empty(), store.loadToken());
  }

  @Test
  void savingBlankTokenThrows() {
    GitHubTokenStore store = fileBackedStore();
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> store.saveToken(" "));
  }

  /** A fake OS-keystore backend for testing orchestration logic without real native calls. */
  private static final class FakeBackend implements CredentialBackend {
    String stored;
    boolean throwOnLoad;
    boolean throwOnSave;

    @Override
    public Optional<String> load() throws IOException {
      if (throwOnLoad) throw new IOException("fake native read failure");
      return Optional.ofNullable(stored);
    }

    @Override
    public void save(String token) throws IOException {
      if (throwOnSave) throw new IOException("fake native write failure");
      if (token == null || token.isBlank()) throw new IllegalArgumentException("Token cannot be empty.");
      stored = token;
    }

    @Override
    public void clear() {
      stored = null;
    }

    @Override
    public boolean exists() {
      return stored != null;
    }
  }

  @Test
  void loadMigratesLegacyAesFileTokenIntoOsBackendAndDeletesLegacyFiles() throws Exception {
    Path tokenFile = tempDir.resolve("token.enc");
    Path keyFile = tempDir.resolve(".key");
    AesFileBackend legacy = new AesFileBackend(tokenFile, keyFile);
    legacy.save("ghp_legacyToken");

    FakeBackend fake = new FakeBackend();
    GitHubTokenStore store = GitHubTokenStore.forTesting(tokenFile, keyFile, fake);

    Optional<String> loaded = store.loadToken();

    assertEquals(Optional.of("ghp_legacyToken"), loaded);
    assertEquals("ghp_legacyToken", fake.stored);
    assertFalse(Files.exists(tokenFile));
    assertFalse(Files.exists(keyFile));
  }

  @Test
  void loadFallsBackToLegacyAesFileWhenOsBackendReadFails() throws Exception {
    Path tokenFile = tempDir.resolve("token.enc");
    Path keyFile = tempDir.resolve(".key");
    AesFileBackend legacy = new AesFileBackend(tokenFile, keyFile);
    legacy.save("ghp_legacyToken");

    FakeBackend fake = new FakeBackend();
    fake.stored = "irrelevant";
    fake.throwOnLoad = true;
    GitHubTokenStore store = GitHubTokenStore.forTesting(tokenFile, keyFile, fake);

    Optional<String> loaded = store.loadToken();

    assertEquals(Optional.of("ghp_legacyToken"), loaded);
  }

  @Test
  void loadReturnsEmptyWhenOsBackendReadFailsAndNoLegacyFileExists() throws Exception {
    Path tokenFile = tempDir.resolve("token.enc");
    Path keyFile = tempDir.resolve(".key");

    FakeBackend fake = new FakeBackend();
    fake.throwOnLoad = true;
    GitHubTokenStore store = GitHubTokenStore.forTesting(tokenFile, keyFile, fake);

    assertEquals(Optional.empty(), store.loadToken());
  }

  @Test
  void hasTokenReturnsTrueWhenOnlyLegacyFileExistsBeforeMigration() throws Exception {
    Path tokenFile = tempDir.resolve("token.enc");
    Path keyFile = tempDir.resolve(".key");
    AesFileBackend legacy = new AesFileBackend(tokenFile, keyFile);
    legacy.save("ghp_legacyToken");

    FakeBackend fake = new FakeBackend();
    GitHubTokenStore store = GitHubTokenStore.forTesting(tokenFile, keyFile, fake);

    assertTrue(store.hasToken());
  }

  @Test
  void saveOnFirstNativeFailurePropagatesIOException() {
    Path tokenFile = tempDir.resolve("token.enc");
    Path keyFile = tempDir.resolve(".key");
    FakeBackend fake = new FakeBackend();
    fake.throwOnSave = true;
    GitHubTokenStore store = GitHubTokenStore.forTesting(tokenFile, keyFile, fake);

    IOException ex = org.junit.jupiter.api.Assertions.assertThrows(IOException.class,
        () -> store.saveToken("ghp_newToken"));
    assertTrue(ex.getMessage().contains("fake native write failure"));
  }

  @Test
  void clearRemovesBothOsBackendEntryAndAnyLegacyFile() throws Exception {
    Path tokenFile = tempDir.resolve("token.enc");
    Path keyFile = tempDir.resolve(".key");
    AesFileBackend legacy = new AesFileBackend(tokenFile, keyFile);
    legacy.save("ghp_legacyToken");

    FakeBackend fake = new FakeBackend();
    fake.stored = "ghp_currentToken";
    GitHubTokenStore store = GitHubTokenStore.forTesting(tokenFile, keyFile, fake);

    store.clearToken();

    assertFalse(fake.exists());
    assertFalse(Files.exists(tokenFile));
    assertFalse(Files.exists(keyFile));
    assertFalse(store.hasToken());
  }
}
