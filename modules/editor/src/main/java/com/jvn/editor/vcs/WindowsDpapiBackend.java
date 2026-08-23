package com.jvn.editor.vcs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Structure.FieldOrder;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinBase.FILETIME;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.ptr.PointerByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.jspecify.annotations.Nullable;

/**
 * Stores the GitHub token in Windows Credential Manager as a generic credential. Credential
 * Manager encrypts the stored blob per-user via DPAPI internally, so no separate
 * CryptProtectData call is needed here.
 *
 * <p>JNA-Platform 5.19.1 does not ship bindings for the Windows Credential Manager APIs
 * (no {@code WinCred} class, and no credential-related methods on {@code Advapi32} or
 * {@code Advapi32Util}) — this was verified by disassembling the jar's classes with
 * {@code javap}. This class therefore binds {@code CredWriteW}/{@code CredReadW}/
 * {@code CredFree}/{@code CredDeleteW} directly from {@code advapi32.dll}.
 */
final class WindowsDpapiBackend implements CredentialBackend {
  static final String CREDENTIAL_TARGET = "jvn-editor-github-token";

  private static final int CRED_TYPE_GENERIC = 1;
  private static final int CRED_PERSIST_LOCAL_MACHINE = 2;

  /** Mirrors the Win32 {@code CREDENTIALW} structure (wide-char variant). */
  @FieldOrder({
    "flags", "type", "targetName", "comment", "lastWritten", "credentialBlobSize",
    "credentialBlob", "persist", "attributeCount", "attributes", "targetAlias", "userName"
  })
  public static class CREDENTIALW extends Structure {
    public int flags;
    public int type;
    public @Nullable WString targetName;
    public @Nullable WString comment;
    public @Nullable FILETIME lastWritten;
    public int credentialBlobSize;
    public @Nullable Pointer credentialBlob;
    public int persist;
    public int attributeCount;
    public @Nullable Pointer attributes;
    public @Nullable WString targetAlias;
    public @Nullable WString userName;

    public CREDENTIALW() {
      super();
    }

    public CREDENTIALW(Pointer memory) {
      super(memory);
      read();
    }
  }

  interface CredAdvapi32 extends StdCallLibrary {
    CredAdvapi32 INSTANCE = Native.load("advapi32", CredAdvapi32.class, W32APIOptions.UNICODE_OPTIONS);

    boolean CredWriteW(CREDENTIALW credential, int flags);

    boolean CredReadW(WString targetName, int type, int flags, PointerByReference credentialPtr);

    boolean CredDeleteW(WString targetName, int type, int flags);

    void CredFree(Pointer credential);
  }

  @Override
  public boolean exists() {
    return readCredentialBlob() != null;
  }

  @Override
  public Optional<String> load() throws IOException {
    try {
      byte[] blob = readCredentialBlob();
      if (blob == null) return Optional.empty();
      return Optional.of(new String(blob, StandardCharsets.UTF_8));
    } catch (Win32Exception ex) {
      if (ex.getErrorCode() == WinError.ERROR_NOT_FOUND) {
        return Optional.empty();
      }
      throw new IOException(
          "GitHub token load failed (Windows Credential Manager): " + ex.getMessage(), ex);
    }
  }

  @Override
  public void save(String token) throws IOException {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("Token cannot be empty.");
    }
    String trimmed = token.trim();
    byte[] blobBytes = trimmed.getBytes(StandardCharsets.UTF_8);
    Pointer blobPointer = new com.sun.jna.Memory(blobBytes.length == 0 ? 1 : blobBytes.length);
    blobPointer.write(0, blobBytes, 0, blobBytes.length);

    CREDENTIALW credential = new CREDENTIALW();
    credential.flags = 0;
    credential.type = CRED_TYPE_GENERIC;
    credential.targetName = new WString(CREDENTIAL_TARGET);
    credential.comment = null;
    credential.lastWritten = new FILETIME();
    credential.credentialBlobSize = blobBytes.length;
    credential.credentialBlob = blobPointer;
    credential.persist = CRED_PERSIST_LOCAL_MACHINE;
    credential.attributeCount = 0;
    credential.attributes = null;
    credential.targetAlias = null;
    credential.userName = new WString(System.getProperty("user.name", "jvn-editor"));

    boolean ok = CredAdvapi32.INSTANCE.CredWriteW(credential, 0);
    if (!ok) {
      int errorCode = Native.getLastError();
      throw new IOException(
          "GitHub token save failed (Windows Credential Manager): " + new Win32Exception(errorCode).getMessage());
    }
  }

  @Override
  public void clear() throws IOException {
    boolean ok = CredAdvapi32.INSTANCE.CredDeleteW(
        new WString(CREDENTIAL_TARGET), CRED_TYPE_GENERIC, 0);
    if (!ok) {
      int errorCode = Native.getLastError();
      if (errorCode != WinError.ERROR_NOT_FOUND) {
        throw new IOException(
            "GitHub token clear failed (Windows Credential Manager): "
                + new Win32Exception(errorCode).getMessage());
      }
    }
  }

  /**
   * Reads the raw credential blob bytes, or returns {@code null} if no credential is stored.
   * Throws {@link Win32Exception} for any failure other than "not found".
   */
  private @Nullable byte[] readCredentialBlob() {
    PointerByReference credentialPtrRef = new PointerByReference();
    boolean ok = CredAdvapi32.INSTANCE.CredReadW(
        new WString(CREDENTIAL_TARGET), CRED_TYPE_GENERIC, 0, credentialPtrRef);
    if (!ok) {
      int errorCode = Native.getLastError();
      if (errorCode == WinError.ERROR_NOT_FOUND) {
        return null;
      }
      throw new Win32Exception(errorCode);
    }
    Pointer credentialPtr = credentialPtrRef.getValue();
    try {
      CREDENTIALW credential = new CREDENTIALW(credentialPtr);
      if (credential.credentialBlob == null || credential.credentialBlobSize <= 0) {
        return new byte[0];
      }
      return credential.credentialBlob.getByteArray(0, credential.credentialBlobSize);
    } finally {
      CredAdvapi32.INSTANCE.CredFree(credentialPtr);
    }
  }
}
