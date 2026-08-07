package com.jvn.plugin.runtime;

import com.jvn.plugin.api.JvnPlugin;
import com.jvn.plugin.api.PluginDescriptor;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

/** Performs plugin bundle preflight checks without constructing or starting plugin code. */
public final class PluginBundleVerifier {
  private final PluginManifestReader manifestReader = new PluginManifestReader();

  public Verification verify(Path source) {
    Path jar = source == null ? null : source.toAbsolutePath().normalize();
    if (jar == null || !Files.isRegularFile(jar)) {
      return Verification.failed(jar, "Plugin bundle is not a regular file");
    }
    if (!jar.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
      return Verification.failed(jar, "Plugin bundle must be a .jar file");
    }
    try (JarFile archive = new JarFile(jar.toFile())) {
      var entry = archive.getJarEntry(PluginManifestReader.MANIFEST_PATH);
      if (entry == null) return Verification.failed(jar, "Missing " + PluginManifestReader.MANIFEST_PATH);
      PluginDescriptor descriptor;
      try (var input = archive.getInputStream(entry)) {
        descriptor = manifestReader.read(input);
      }
      if (!VersionRange.accepts(descriptor.apiVersion(), PluginHost.API_VERSION)) {
        return Verification.failed(jar, descriptor, "Requires JVN Plugin API " + descriptor.apiVersion()
            + "; this engine provides " + PluginHost.API_VERSION);
      }
      try (URLClassLoader loader = new URLClassLoader(
          new URL[] {jar.toUri().toURL()}, JvnPlugin.class.getClassLoader())) {
        Class<?> entrypoint = Class.forName(descriptor.entrypoint(), false, loader);
        if (!JvnPlugin.class.isAssignableFrom(entrypoint)) {
          return Verification.failed(jar, descriptor, "Entrypoint does not implement JvnPlugin");
        }
        if (!Modifier.isPublic(entrypoint.getModifiers())) {
          return Verification.failed(jar, descriptor, "Entrypoint class must be public");
        }
        var constructor = entrypoint.getDeclaredConstructor();
        if (!Modifier.isPublic(constructor.getModifiers())) {
          return Verification.failed(jar, descriptor, "Entrypoint needs a public no-argument constructor");
        }
      }
      return Verification.valid(jar, descriptor);
    } catch (ReflectiveOperationException | LinkageError | IOException error) {
      String detail = error.getMessage();
      return Verification.failed(jar, detail == null || detail.isBlank()
          ? error.getClass().getSimpleName() : detail);
    }
  }

  public record Verification(Path source, PluginDescriptor descriptor, String error) {
    public boolean isValid() { return error == null || error.isBlank(); }

    private static Verification valid(Path source, PluginDescriptor descriptor) {
      return new Verification(source, descriptor, "");
    }

    private static Verification failed(Path source, String error) {
      return new Verification(source, null, error);
    }

    private static Verification failed(Path source, PluginDescriptor descriptor, String error) {
      return new Verification(source, descriptor, error);
    }
  }
}
