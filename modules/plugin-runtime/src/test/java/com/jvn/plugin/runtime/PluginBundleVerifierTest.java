package com.jvn.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jvn.plugin.api.JvnPlugin;
import com.jvn.plugin.api.PluginContext;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginBundleVerifierTest {
  @TempDir Path temp;

  @Test
  void acceptsValidManifestAndEntrypointWithoutStartingIt() throws Exception {
    Path jar = writeBundle("valid.jar", descriptor(NoOpPlugin.class.getName(), ">=1.0.0 <2.0.0"));

    var verification = new PluginBundleVerifier().verify(jar);

    assertTrue(verification.isValid(), verification.error());
    assertTrue(verification.descriptor().id().contains("verifier"));
  }

  @Test
  void rejectsIncompatibleApiBeforePluginCodeCanRun() throws Exception {
    Path jar = writeBundle("incompatible.jar", descriptor(NoOpPlugin.class.getName(), ">=99.0.0 <100.0.0"));

    var verification = new PluginBundleVerifier().verify(jar);

    assertFalse(verification.isValid());
    assertTrue(verification.error().contains("Requires JVN Plugin API"), verification.error());
  }

  @Test
  void rejectsJarWithoutPluginManifest() throws Exception {
    Path jar = temp.resolve("empty.jar");
    try (OutputStream output = Files.newOutputStream(jar); JarOutputStream archive = new JarOutputStream(output)) {
      archive.putNextEntry(new JarEntry("placeholder.txt"));
      archive.write("empty".getBytes(StandardCharsets.UTF_8));
      archive.closeEntry();
    }

    assertFalse(new PluginBundleVerifier().verify(jar).isValid());
  }

  private Path writeBundle(String fileName, String manifest) throws Exception {
    Path jar = temp.resolve(fileName);
    try (OutputStream output = Files.newOutputStream(jar); JarOutputStream archive = new JarOutputStream(output)) {
      archive.putNextEntry(new JarEntry(PluginManifestReader.MANIFEST_PATH));
      archive.write(manifest.getBytes(StandardCharsets.UTF_8));
      archive.closeEntry();
    }
    return jar;
  }

  private static String descriptor(String entrypoint, String apiVersion) {
    return "{\"id\":\"test.verifier\",\"name\":\"Verifier Test\",\"version\":\"1.0.0\","
        + "\"jvnApi\":\"" + apiVersion + "\",\"entrypoint\":\"" + entrypoint + "\"}";
  }

  public static final class NoOpPlugin implements JvnPlugin {
    @Override public void initialize(PluginContext context) { }
  }
}
