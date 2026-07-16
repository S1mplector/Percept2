package com.jvn.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.jvn.plugin.api.PluginCapability;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PluginManifestReaderTest {
  @Test
  void readsACompleteManifest() throws Exception {
    String json = """
        {"id":"dev.example.tools","name":"Example Tools","version":"1.2.0","jvnApi":"1.x",
         "entrypoint":"dev.example.ToolsPlugin","vendor":"Example",
         "capabilities":["editor.tool","script.command"],
         "dependencies":[{"id":"dev.example.base","version":"^2.0.0"}]}
        """;
    var descriptor = new PluginManifestReader().read(stream(json));
    assertEquals("dev.example.tools", descriptor.id());
    org.junit.jupiter.api.Assertions.assertTrue(descriptor.capabilities().contains(PluginCapability.EDITOR_TOOL));
    assertEquals("dev.example.base", descriptor.dependencies().get(0).id());
  }

  @Test
  void rejectsMissingRequiredFields() {
    assertThrows(IOException.class, () -> new PluginManifestReader().read(stream("{\"name\":\"Broken\"}")));
  }

  private static ByteArrayInputStream stream(String value) {
    return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
  }
}
