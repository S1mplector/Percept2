package com.jvn.editor.vcs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AesFileBackendTest {

  @TempDir
  Path tempDir;

  private AesFileBackend backend() {
    return new AesFileBackend(tempDir.resolve("token.enc"), tempDir.resolve(".key"));
  }

  @Test
  void hasNoTokenBeforeAnythingIsSaved() {
    assertFalse(backend().exists());
  }

  @Test
  void loadTokenReturnsEmptyWhenNothingIsSaved() throws Exception {
    assertEquals(Optional.empty(), backend().load());
  }

  @Test
  void savedTokenRoundTripsExactly() throws Exception {
    AesFileBackend backend = backend();
    backend.save("ghp_exampleToken123");

    assertTrue(backend.exists());
    assertEquals(Optional.of("ghp_exampleToken123"), backend.load());
  }

  @Test
  void clearTokenRemovesBothFilesAndLoadReturnsEmpty() throws Exception {
    AesFileBackend backend = backend();
    backend.save("ghp_exampleToken123");
    assertTrue(backend.exists());

    backend.clear();

    assertFalse(backend.exists());
    assertEquals(Optional.empty(), backend.load());
  }

  @Test
  void savingBlankTokenThrows() {
    AesFileBackend backend = backend();
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> backend.save(" "));
  }

  @Test
  void tokenFilesAreLockedDownToOwnerOnly() throws Exception {
    AesFileBackend backend = backend();
    backend.save("ghp_exampleToken123");

    Path tokenFile = tempDir.resolve("token.enc");
    Path keyFile = tempDir.resolve(".key");

    if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
      assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
          Files.getPosixFilePermissions(tokenFile));
      assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
          Files.getPosixFilePermissions(keyFile));
    } else {
      AclFileAttributeView aclView = Files.getFileAttributeView(tokenFile, AclFileAttributeView.class);
      org.junit.jupiter.api.Assumptions.assumeTrue(aclView != null, "ACL file attribute view not supported");
      List<AclEntry> acl = aclView.getAcl();
      assertEquals(1, acl.size());
    }
  }
}
