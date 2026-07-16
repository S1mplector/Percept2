package com.jvn.editor.ui;

import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.vn.ui.VnUiLayoutLoader;
import com.jvn.core.vn.ui.VnUiLayoutSpec;
import com.jvn.core.vn.ui.VnUiStyleSpec;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayoutDslTemplateRegressionTest {

  @Test
  void dialogueTemplateSnapshotMatches() throws Exception {
    assertSnapshot(
        LayoutDslTemplates.defaultDialogueLayoutTemplate(),
        "snapshots/layout-dsl/dialogue.layout.snapshot"
    );
  }

  @Test
  void menuLayoutTemplateSnapshotMatches() throws Exception {
    assertSnapshot(
        LayoutDslTemplates.defaultMenuLayoutTemplate(MenuProfile.defaultLayout()),
        "snapshots/layout-dsl/menu.layout.snapshot"
    );
  }

  @Test
  void menuStyleTemplateSnapshotMatches() throws Exception {
    assertSnapshot(
        LayoutDslTemplates.defaultMenuStyleTemplate(MenuProfile.defaultStyle()),
        "snapshots/layout-dsl/menu.style.snapshot"
    );
  }

  @Test
  void menuScreenTemplateSnapshotMatches() throws Exception {
    assertSnapshot(
        LayoutDslTemplates.defaultMenuScreenTemplate("main"),
        "snapshots/layout-dsl/menu.screen.snapshot"
    );
  }

  @Test
  void menuRegistryTemplateSnapshotMatches() throws Exception {
    assertSnapshot(
        LayoutDslTemplates.defaultMenuRegistryTemplate(),
        "snapshots/layout-dsl/menu.registry.snapshot"
    );
  }

  @Test
  void dialogueTemplateRoundTripsWithoutDrift() throws Exception {
    Properties source = parseProperties(LayoutDslTemplates.defaultDialogueLayoutTemplate());

    VnUiLayoutLoader.LoadResult first = VnUiLayoutLoader.parseWithDiagnostics(
        source,
        VnUiLayoutSpec.defaults(),
        VnUiStyleSpec.defaults()
    );
    assertTrue(
        first.diagnostics().isEmpty(),
        () -> "Default dialogue template emitted diagnostics: " + first.diagnostics()
    );

    Properties serialized = VnUiLayoutLoader.toProperties(first.layout(), first.style(), first.textBoxButtons());
    assertEquals(
        toCanonicalSortedMap(source),
        toCanonicalSortedMap(serialized),
        "Serialized dialogue properties drifted from template-declared defaults"
    );

    VnUiLayoutLoader.LoadResult second = VnUiLayoutLoader.parseWithDiagnostics(
        serialized,
        VnUiLayoutSpec.defaults(),
        VnUiStyleSpec.defaults()
    );
    assertTrue(
        second.diagnostics().isEmpty(),
        () -> "Re-parse of serialized dialogue template emitted diagnostics: " + second.diagnostics()
    );
    assertEquals(first.layout(), second.layout());
    assertEquals(first.style(), second.style());
    assertEquals(first.textBoxButtons(), second.textBoxButtons());

    Properties reserialized = VnUiLayoutLoader.toProperties(second.layout(), second.style(), second.textBoxButtons());
    assertEquals(
        toCanonicalSortedMap(serialized),
        toCanonicalSortedMap(reserialized),
        "Dialogue parse/serialize round-trip is not stable"
    );
  }

  @Test
  void minimalMonochromeDialogueTemplateIsValidAndKeepsChoicesRight() throws Exception {
    Properties source = parseProperties(LayoutDslTemplates.minimalMonochromeDialogueLayoutTemplate());
    VnUiLayoutLoader.LoadResult result = VnUiLayoutLoader.parseWithDiagnostics(
        source,
        VnUiLayoutSpec.defaults(),
        VnUiStyleSpec.defaults()
    );

    assertTrue(
        result.diagnostics().isEmpty(),
        () -> "Minimal dialogue template emitted diagnostics: " + result.diagnostics()
    );
    assertEquals("0.74", source.getProperty("choiceXCenter"));
    assertEquals("0.40", source.getProperty("choiceWidthFactor"));
    assertEquals("#FFFFFFE8", source.getProperty("choiceBackgroundColor"));
    assertEquals("1", source.getProperty("choiceBorderWidth"));
  }

  private static void assertSnapshot(String actual, String snapshotPath) throws Exception {
    String expected = readResource(snapshotPath);
    assertEquals(normalizeNewlines(expected), normalizeNewlines(actual), "Template snapshot drifted: " + snapshotPath);
  }

  private static String readResource(String path) throws Exception {
    try (InputStream in = LayoutDslTemplateRegressionTest.class.getClassLoader().getResourceAsStream(path)) {
      assertNotNull(in, "Missing snapshot resource: " + path);
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static Properties parseProperties(String text) throws Exception {
    Properties properties = new Properties();
    properties.load(new StringReader(text));
    return properties;
  }

  private static Map<String, String> toSortedMap(Properties properties) {
    Map<String, String> map = new TreeMap<>();
    for (String key : properties.stringPropertyNames()) {
      map.put(key, properties.getProperty(key));
    }
    return map;
  }

  private static Map<String, String> toCanonicalSortedMap(Properties properties) {
    Map<String, String> map = new TreeMap<>();
    for (String key : properties.stringPropertyNames()) {
      map.put(key, canonicalize(properties.getProperty(key)));
    }
    return map;
  }

  private static String canonicalize(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    if (!trimmed.matches("-?\\d+(\\.\\d+)?")) {
      return value;
    }
    return new BigDecimal(trimmed).stripTrailingZeros().toPlainString();
  }

  private static String normalizeNewlines(String value) {
    return value.replace("\r\n", "\n");
  }
}
