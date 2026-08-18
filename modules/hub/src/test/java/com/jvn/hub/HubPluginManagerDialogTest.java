package com.jvn.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HubPluginManagerDialogTest {
  @TempDir Path temporary;

  @Test
  void readsCompleteManifestWithoutConfusingNestedDependencyId() throws Exception {
    Path bundle = bundle("sample.jar", """
        {
          "id": "dev.jvn.sample",
          "name": "Sample Tools",
          "version": "2.4.0",
          "jvnApi": ">=1.0.0 <2.0.0",
          "entrypoint": "dev.jvn.SamplePlugin",
          "description": "Useful editor tools",
          "vendor": "JVN Labs",
          "dependencies": [{"id": "dev.jvn.foundation", "version": "^1.0.0"}],
          "capabilities": ["editor.tool", "asset.importer"]
        }
        """);

    HubPluginManagerDialog.Verification result = verifier().verify(bundle);

    assertTrue(result.isValid(), result.error());
    assertEquals("dev.jvn.sample", result.descriptor().id());
    assertEquals("dev.jvn.foundation", result.descriptor().dependencies().get(0).id());
    assertEquals("JVN Labs", result.descriptor().vendor());
    assertEquals(2, result.descriptor().capabilities().size());
    assertEquals(64, result.sha256().length());
  }

  @Test
  void verifiesDisabledBundleButRejectsIncompatibleApi() throws Exception {
    Path bundle = bundle("future.jar.disabled", """
        {"id":"dev.jvn.future","name":"Future","version":"1.0.0",
         "jvnApi":">=2.0.0","entrypoint":"dev.jvn.SamplePlugin"}
        """);

    HubPluginManagerDialog.Verification result = verifier().verify(bundle);

    assertFalse(result.isValid());
    assertTrue(result.error().contains("provides 1.1.0"));
  }

  @Test
  void rejectsMalformedOrStructurallyIncompleteBundles() throws Exception {
    Path missingEntrypoint = bundle("missing.jar", """
        {"id":"dev.jvn.missing","name":"Missing","version":"1.0.0",
         "jvnApi":"1.x","entrypoint":"dev.jvn.NotInJar"}
        """, false);
    Path duplicateField = bundle("duplicate.jar", """
        {"id":"one","id":"two","name":"Duplicate","version":"1.0.0",
         "jvnApi":"1.x","entrypoint":"dev.jvn.SamplePlugin"}
        """);

    assertTrue(verifier().verify(missingEntrypoint).error().contains("Entrypoint class"));
    assertTrue(verifier().verify(duplicateField).error().contains("Duplicate object key"));
  }

  private HubPluginManagerDialog.HubPluginBundleVerifier verifier() {
    return new HubPluginManagerDialog.HubPluginBundleVerifier();
  }

  private Path bundle(String filename, String manifest) throws IOException {
    return bundle(filename, manifest, true);
  }

  private Path bundle(String filename, String manifest, boolean entrypoint) throws IOException {
    Path output = temporary.resolve(filename);
    try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(output))) {
      jar.putNextEntry(new JarEntry("jvn-plugin.json"));
      jar.write(manifest.getBytes(StandardCharsets.UTF_8));
      jar.closeEntry();
      if (entrypoint) {
        jar.putNextEntry(new JarEntry("dev/jvn/SamplePlugin.class"));
        jar.write(new byte[] {0, 1, 2, 3});
        jar.closeEntry();
      }
    }
    return output;
  }
}
