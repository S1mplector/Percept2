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

public class ClasspathAssetManager implements AssetManager {
  private final ClassLoader loader;

  public ClasspathAssetManager() {
    this(Thread.currentThread().getContextClassLoader());
  }

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
    // Ensure trailing slash
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

  private List<String> listFromJarProtocol(URL url, String dir) {
    try {
      JarURLConnection conn = (JarURLConnection) url.openConnection();
      try (JarFile jar = conn.getJarFile()) {
        List<String> names = new ArrayList<>();
        String prefix = conn.getEntryName();
        if (prefix == null) prefix = dir; // fallback
        if (!prefix.endsWith("/")) prefix += "/";
        int prefixLen = prefix.length();
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
