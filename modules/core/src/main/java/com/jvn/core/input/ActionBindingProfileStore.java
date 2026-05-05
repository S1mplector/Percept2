package com.jvn.core.input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * File-based persistence for {@link ActionBindingProfile} instances.
 *
 * <p>Profiles are stored as plain-text files using the serialisation format
 * defined by {@link ActionBindingProfile#serialize()}. The default location
 * is {@code ~/.jvn/input-bindings.properties}, but a custom path can be
 * supplied via the constructor.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * ActionBindingProfileStore store = new ActionBindingProfileStore(null); // default path
 * ActionBindingProfile profile = store.load();
 * // ... user changes bindings ...
 * store.save(actionMap.toProfile());
 * }</pre>
 *
 * @see ActionBindingProfile
 * @see ActionMap
 */
public class ActionBindingProfileStore {

  /** Resolved file path for the profile. */
  private final Path path;

  /**
   * Construct a store pointing at the given file path.
   *
   * @param path file path; if {@code null} or blank, the
   *             {@link #defaultPath()} is used
   */
  public ActionBindingProfileStore(String path) {
    this.path = Paths.get(path == null || path.isBlank() ? defaultPath() : path);
  }

  /**
   * Load a profile from disk. Returns an empty profile if the file
   * does not exist or cannot be read.
   *
   * @return the loaded profile (never {@code null})
   */
  public ActionBindingProfile load() {
    try {
      if (!Files.exists(path)) return new ActionBindingProfile();
      String data = Files.readString(path);
      return ActionBindingProfile.deserialize(data);
    } catch (IOException e) {
      return new ActionBindingProfile();
    }
  }

  /**
   * Save a profile to disk. Parent directories are created automatically.
   *
   * @param profile the profile to persist; {@code null} is a no-op
   * @throws IOException if the file cannot be written
   */
  public void save(ActionBindingProfile profile) throws IOException {
    if (profile == null) return;
    Files.createDirectories(path.getParent());
    Files.writeString(path, profile.serialize());
  }

  /** @return the resolved file path as a string */
  public String getPath() { return path.toString(); }

  /**
   * @return the platform default path:
   *         {@code ~/.jvn/input-bindings.properties}
   */
  public static String defaultPath() {
    return System.getProperty("user.home") + "/.jvn/input-bindings.properties";
  }
}
