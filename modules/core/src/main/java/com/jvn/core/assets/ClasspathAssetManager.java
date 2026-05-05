package com.jvn.core.assets;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * {@link AssetManager} implementation that resolves assets from the Java
 * classpath (embedded JARs or exploded class directories).
 *
 * <p>This is the default backend used by {@link AssetCatalog} and is
 * suitable for packaged game distributions where all assets are bundled
 * inside the application JAR.</p>
 *
 * <p>The {@link #list(String)} method handles both {@code file://} and
 * {@code jar://} URL protocols so it works in both IDE and packaged
 * environments.</p>
 *
 * @see FilesystemAssetManager
 * @see OverlayAssetManager
 */
public class ClasspathAssetManager implements AssetManager {

  /** The class loader used for resource lookups. */
  private final ClassLoader loader;

  /** Construct using the current thread's context class loader. */
  public ClasspathAssetManager() {
    this(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Construct using a specific class loader.
   *
   * @param loader the loader to use; if {@code null}, falls back to this class's loader
   */
  public ClasspathAssetManager(ClassLoader loader) {
    this.loader = loader == null ? ClasspathAssetManager.class.getClassLoader() : loader;
  }

  @Override
  public boolean exists(AssetType type, String name) {
    return url(type, name) != null;
  }

  @Override
  public URL url(AssetType type, String name) {
    String path = AssetPaths.build(type, name);
    return loader.getResource(path);
  }

  @Override
  public InputStream open(AssetType type, String name) throws IOException {
    String path = AssetPaths.build(type, name);
    InputStream in = loader.getResourceAsStream(path);
    if (in == null) throw new IOException("Asset not found: " + path);
    return in;
  }

  @Override
  public List<String> list(String directory) {
    // Ensure trailing slash for consistent directory resolution
    String dir = directory.endsWith("/") ? directory : directory + "/";
    try {
      Set<String> results = new HashSet<>();
      Enumeration<URL> urls = loader.getResources(dir);
      while (urls.hasMoreElements()) {
        URL u = urls.nextElement();
        String protocol = u.getProtocol();
        if ("file".equals(protocol)) {
          results.addAll(listFromFileProtocol(u, dir));
        } else if ("jar".equals(protocol)) {
          results.addAll(listFromJarProtocol(u, dir));
        }
      }
      return new ArrayList<>(results);
    } catch (IOException e) {
      return List.of();
    }
  }

  /** List immediate children of a directory resolved via {@code file://} URL. */
  private List<String> listFromFileProtocol(URL url, String dir) {
    try {
      Path path = Paths.get(url.toURI());
      if (!Files.isDirectory(path)) return List.of();
      try (var stream = Files.list(path)) {
        List<String> names = new ArrayList<>();
        stream.forEach(p -> names.add(p.getFileName().toString()));
        return names;
      }
    } catch (URISyntaxException | IOException e) {
      return List.of();
    }
  }

  /** List immediate children of a directory resolved via {@code jar://} URL. */
  private List<String> listFromJarProtocol(URL url, String dir) {
    try {
      JarURLConnection conn = (JarURLConnection) url.openConnection();
      try (JarFile jar = conn.getJarFile()) {
        List<String> names = new ArrayList<>();
        String entryName = conn.getEntryName();
        if (entryName == null) entryName = dir; // fallback
        if (!entryName.endsWith("/")) entryName += "/";
        final String prefix = entryName;
        final int prefixLen = prefix.length();
        jar.stream()
          .map(JarEntry::getName)
          .filter(n -> n.startsWith(prefix) && !n.equals(prefix))
          .map(n -> n.substring(prefixLen))
          .filter(n -> !n.isEmpty() && !n.contains("/")) // immediate children only
          .distinct()
          .forEach(names::add);
        return names;
      }
    } catch (IOException e) {
      return List.of();
    }
  }
}
