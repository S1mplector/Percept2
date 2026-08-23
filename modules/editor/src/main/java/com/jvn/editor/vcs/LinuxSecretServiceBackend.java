package com.jvn.editor.vcs;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Structure.FieldOrder;
import com.sun.jna.ptr.PointerByReference;
import org.jspecify.annotations.Nullable;

/**
 * Stores the GitHub token in the freedesktop Secret Service (GNOME Keyring / KWallet) via a
 * direct binding to libsecret's synchronous convenience API ({@code secret_password_store_sync},
 * {@code secret_password_lookup_sync}, {@code secret_password_clear_sync}).
 *
 * <p>These are C varargs functions: {@code (schema, ..., cancellable, error, ...)} where the
 * trailing {@code ...} is a NULL-terminated list of attribute-name/attribute-value string pairs
 * matching the schema's declared attributes. JNA cannot introspect true C varargs beyond
 * primitive/pointer promotion, so this binds a fixed-arity overload of each function with exactly
 * one attribute pair ({@code "account" = "github-token"}) plus the required {@code NULL}
 * terminator — this is a correct modeling of the real API for a call site that always passes
 * exactly one attribute, matching how e.g. Mozilla's Firefox NSS binds the same functions.
 */
final class LinuxSecretServiceBackend implements CredentialBackend {
  static final String SCHEMA_NAME = "com.jvn.editor.GitHubToken";
  private static final String ACCOUNT_ATTR_KEY = "account";
  private static final String ACCOUNT_ATTR_VALUE = "github-token";

  /** {@code SECRET_COLLECTION_DEFAULT} — the well-known alias for the default collection. */
  private static final String COLLECTION_DEFAULT = "default";

  private static final int SECRET_SCHEMA_ATTRIBUTE_STRING = 0;
  private static final int SECRET_SCHEMA_NONE = 0;

  /** Mirrors libsecret's {@code SecretSchemaAttribute} struct: {@code { name, type }}. */
  @FieldOrder({"name", "type"})
  public static class SecretSchemaAttribute extends Structure {
    public @Nullable String name;
    public int type;
  }

  /**
   * Mirrors libsecret's {@code SecretSchema} struct. The real struct is:
   * <pre>
   * typedef struct {
   *   const gchar *name;
   *   SecretSchemaFlags flags;
   *   SecretSchemaAttribute attributes[32];
   *   / private /
   *   gint reserved;
   *   gpointer reserved1;
   *   gpointer reserved2;
   *   gpointer reserved3;
   *   gpointer reserved4;
   *   gpointer reserved5;
   *   gpointer reserved6;
   *   gpointer reserved7;
   * } SecretSchema;
   * </pre>
   * The trailing reserved fields are private/unused by callers but are part of the struct's real
   * memory layout, so they must be declared here too — omitting them would make the JNA-computed
   * struct size too small and misrepresent what libsecret expects a {@code SecretSchema*} to
   * point at.
   */
  @FieldOrder({
    "name", "flags", "attributes", "reserved", "reserved1", "reserved2", "reserved3", "reserved4",
    "reserved5", "reserved6", "reserved7"
  })
  public static class SecretSchema extends Structure {
    public @Nullable String name;
    public int flags;
    public SecretSchemaAttribute[] attributes = new SecretSchemaAttribute[32];
    public int reserved;
    public @Nullable Pointer reserved1;
    public @Nullable Pointer reserved2;
    public @Nullable Pointer reserved3;
    public @Nullable Pointer reserved4;
    public @Nullable Pointer reserved5;
    public @Nullable Pointer reserved6;
    public @Nullable Pointer reserved7;
  }

  interface LibSecret extends Library {
    LibSecret INSTANCE = Native.load("secret-1", LibSecret.class);

    boolean secret_password_store_sync(SecretSchema schema, String collection, String label, String password,
        Pointer cancellable, PointerByReference error, String attr1Key, String attr1Value, Pointer terminator);

    String secret_password_lookup_sync(SecretSchema schema, Pointer cancellable, PointerByReference error,
        String attr1Key, String attr1Value, Pointer terminator);

    boolean secret_password_clear_sync(SecretSchema schema, Pointer cancellable, PointerByReference error,
        String attr1Key, String attr1Value, Pointer terminator);

    void secret_password_free(String password);
  }

  private SecretSchema schema() {
    SecretSchema schema = new SecretSchema();
    schema.name = SCHEMA_NAME;
    schema.flags = SECRET_SCHEMA_NONE;

    SecretSchemaAttribute account = new SecretSchemaAttribute();
    account.name = ACCOUNT_ATTR_KEY;
    account.type = SECRET_SCHEMA_ATTRIBUTE_STRING;
    schema.attributes[0] = account;

    SecretSchemaAttribute terminator = new SecretSchemaAttribute();
    terminator.name = null;
    terminator.type = 0;
    schema.attributes[1] = terminator;

    return schema;
  }

  /**
   * Returns whether {@code libsecret-1.so.0} can be loaded on this system. Referencing
   * {@code LibSecret.INSTANCE} forces {@code LibSecret}'s static initializer to run (per JLS
   * 12.4.1, active use of a static field triggers class initialization on first access), which is
   * exactly where {@code Native.load("secret-1", ...)} executes. If that load fails,
   * {@code Native.load} throws {@link UnsatisfiedLinkError}; because {@code UnsatisfiedLinkError}
   * is itself an {@link Error}, JLS 12.4.2 propagates it unwrapped from this first triggering
   * access rather than boxing it in {@code ExceptionInInitializerError}. Any subsequent access to
   * the (now permanently erroneous) class throws {@link NoClassDefFoundError} instead — both are
   * caught here, so this correctly reports unavailability on every call, not just the first.
   */
  boolean isAvailable() {
    try {
      return LibSecret.INSTANCE != null;
    } catch (UnsatisfiedLinkError | NoClassDefFoundError ex) {
      return false;
    }
  }

  @Override
  public boolean exists() {
    try {
      return load().isPresent();
    } catch (IOException ex) {
      return false;
    }
  }

  @Override
  public Optional<String> load() throws IOException {
    PointerByReference error = new PointerByReference();
    String password;
    try {
      password = LibSecret.INSTANCE.secret_password_lookup_sync(schema(), Pointer.NULL, error, ACCOUNT_ATTR_KEY,
          ACCOUNT_ATTR_VALUE, Pointer.NULL);
    } catch (UnsatisfiedLinkError | NoClassDefFoundError ex) {
      throw new IOException("GitHub token load failed (Linux Secret Service): libsecret unavailable", ex);
    }
    if (error.getValue() != null) {
      throw new IOException("GitHub token load failed (Linux Secret Service): D-Bus error");
    }
    if (password == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(password);
    } finally {
      LibSecret.INSTANCE.secret_password_free(password);
    }
  }

  @Override
  public void save(String token) throws IOException {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("Token cannot be empty.");
    }
    PointerByReference error = new PointerByReference();
    boolean ok;
    try {
      ok = LibSecret.INSTANCE.secret_password_store_sync(schema(), COLLECTION_DEFAULT, "JVN Editor GitHub Token",
          token.trim(), Pointer.NULL, error, ACCOUNT_ATTR_KEY, ACCOUNT_ATTR_VALUE, Pointer.NULL);
    } catch (UnsatisfiedLinkError | NoClassDefFoundError ex) {
      throw new IOException("GitHub token save failed (Linux Secret Service): libsecret unavailable", ex);
    }
    if (!ok || error.getValue() != null) {
      throw new IOException("GitHub token save failed (Linux Secret Service): D-Bus error or no keyring daemon");
    }
  }

  @Override
  public void clear() throws IOException {
    PointerByReference error = new PointerByReference();
    boolean ok;
    try {
      ok = LibSecret.INSTANCE.secret_password_clear_sync(schema(), Pointer.NULL, error, ACCOUNT_ATTR_KEY,
          ACCOUNT_ATTR_VALUE, Pointer.NULL);
    } catch (UnsatisfiedLinkError | NoClassDefFoundError ex) {
      throw new IOException("GitHub token clear failed (Linux Secret Service): libsecret unavailable", ex);
    }
    // secret_password_clear_sync returns whether any item was removed, and does not treat
    // "no matching item" as an error — so a false result with no error set is not a failure.
    if (error.getValue() != null) {
      throw new IOException("GitHub token clear failed (Linux Secret Service): D-Bus error");
    }
  }
}
