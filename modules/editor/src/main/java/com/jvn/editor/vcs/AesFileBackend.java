package com.jvn.editor.vcs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Stores a GitHub personal access token encrypted at rest with a locally-generated AES key.
 * The key and ciphertext files are locked down to owner-only access where the platform supports it.
 *
 * <p>This is the legacy/fallback backend, used directly when {@link StorageBackend#AES_FILE} is
 * forced, and used by {@link GitHubTokenStore} as both a fallback and a migration source when the
 * OS-native backend is unavailable.
 */
final class AesFileBackend implements CredentialBackend {
  private static final String AES = "AES";
  private static final String CIPHER_TRANSFORM = "AES/GCM/NoPadding";
  private static final int GCM_TAG_BITS = 128;
  private static final int GCM_IV_BYTES = 12;
  private static final int AES_KEY_BITS = 256;

  private final Path tokenFile;
  private final Path keyFile;

  AesFileBackend(Path tokenFile, Path keyFile) {
    this.tokenFile = tokenFile;
    this.keyFile = keyFile;
  }

  static Path defaultTokenFilePath() {
    return Path.of(System.getProperty("user.home", "."), ".jvn-editor", "github-token.enc");
  }

  static Path defaultKeyFilePath() {
    return Path.of(System.getProperty("user.home", "."), ".jvn-editor", ".github-key");
  }

  @Override
  public boolean exists() {
    return Files.isRegularFile(tokenFile) && Files.isRegularFile(keyFile);
  }

  @Override
  public void save(String token) throws IOException {
    if (token == null || token.isBlank()) throw new IllegalArgumentException("Token cannot be empty.");
    Path parent = tokenFile.getParent();
    if (parent != null) Files.createDirectories(parent);

    try {
      SecretKey key = loadOrCreateKey();
      Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
      byte[] iv = new byte[GCM_IV_BYTES];
      new SecureRandom().nextBytes(iv);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] ciphertext = cipher.doFinal(token.trim().getBytes(StandardCharsets.UTF_8));

      ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
      buffer.put(iv).put(ciphertext);
      Files.write(tokenFile, buffer.array());
      lockDownToOwner(tokenFile);
    } catch (GeneralSecurityException ex) {
      throw new IOException("Failed to encrypt GitHub token.", ex);
    }
  }

  @Override
  public Optional<String> load() {
    if (!exists()) return Optional.empty();
    try {
      byte[] keyBytes = Files.readAllBytes(keyFile);
      byte[] payload = Files.readAllBytes(tokenFile);
      if (keyBytes.length == 0 || payload.length <= GCM_IV_BYTES) return Optional.empty();

      SecretKey key = new SecretKeySpec(keyBytes, AES);
      byte[] iv = new byte[GCM_IV_BYTES];
      ByteBuffer buffer = ByteBuffer.wrap(payload);
      buffer.get(iv);
      byte[] ciphertext = new byte[buffer.remaining()];
      buffer.get(ciphertext);

      Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] plaintext = cipher.doFinal(ciphertext);
      return Optional.of(new String(plaintext, StandardCharsets.UTF_8));
    } catch (IOException | GeneralSecurityException ex) {
      return Optional.empty();
    }
  }

  @Override
  public void clear() throws IOException {
    Files.deleteIfExists(tokenFile);
    Files.deleteIfExists(keyFile);
  }

  private SecretKey loadOrCreateKey() throws IOException, GeneralSecurityException {
    if (Files.isRegularFile(keyFile)) {
      byte[] existing = Files.readAllBytes(keyFile);
      if (existing.length == AES_KEY_BITS / 8) {
        return new SecretKeySpec(existing, AES);
      }
    }
    KeyGenerator generator = KeyGenerator.getInstance(AES);
    generator.init(AES_KEY_BITS);
    SecretKey key = generator.generateKey();
    Path parent = keyFile.getParent();
    if (parent != null) Files.createDirectories(parent);
    Files.write(keyFile, key.getEncoded());
    lockDownToOwner(keyFile);
    return key;
  }

  private void lockDownToOwner(Path path) {
    try {
      if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
        Files.setPosixFilePermissions(path, Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE));
        return;
      }
      AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);
      if (aclView == null) return;
      UserPrincipal owner = aclView.getOwner();
      AclEntry ownerFullControl = AclEntry.newBuilder()
          .setType(AclEntryType.ALLOW)
          .setPrincipal(owner)
          .setPermissions(AclEntryPermission.values())
          .build();
      aclView.setAcl(List.of(ownerFullControl));
    } catch (IOException | UnsupportedOperationException ignored) {
      // Best-effort lockdown; the file remains encrypted even if permission tightening fails.
    }
  }
}
