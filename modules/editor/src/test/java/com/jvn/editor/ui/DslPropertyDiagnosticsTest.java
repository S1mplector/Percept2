package com.jvn.editor.ui;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import com.jvn.core.menu.config.MenuProfileLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for DslPropertyDiagnostics covering menu layout/style/screen diagnostics,
 * dialogue diagnostics, inline comment handling, and parse round-trip stability.
 */
class DslPropertyDiagnosticsTest {

  // ── Menu layout diagnostics ──

  private static final Set<String> LAYOUT_KEYS = Set.of(
      "listYStart", "lineHeight", "listWidthFactor", "textAlign", "hintsBottomMargin",
      "titleY", "subtitleGap", "listXCenter", "titleX", "maxVisibleItems", "titleAlign", "hintsAlign", "hintsX");

  @Test
  void menuLayoutCleanTemplateProducesNoDiagnostics() {
    String template = LayoutDslTemplates.defaultMenuLayoutTemplate(null);
    List<String> issues = DslPropertyDiagnostics.menuLayoutIssues(template, LAYOUT_KEYS);
    assertTrue(issues.isEmpty(), () -> "Default layout template should be clean but got: " + issues);
  }

  @Test
  void menuLayoutUnknownKeyReportsIssue() {
    String text = "listYStart=0.3\nbogusKey=42\n";
    List<String> issues = DslPropertyDiagnostics.menuLayoutIssues(text, LAYOUT_KEYS);
    assertFalse(issues.isEmpty(), "Unknown key should produce a diagnostic");
    assertTrue(issues.stream().anyMatch(s -> s.contains("bogusKey")));
  }

  @Test
  void editorKeyCatalogMatchesRuntimeCompatibilityAliases() {
    String text = "extends=default\nlistWidth=0.5\n";
    List<String> issues = DslPropertyDiagnostics.menuLayoutIssues(
        text,
        MenuProfileLoader.knownLayoutFields());
    assertTrue(issues.stream().noneMatch(s -> s.contains("Unknown")),
        () -> "Runtime-compatible keys must not be rejected as unknown: " + issues);
  }

  @Test
  void editorAcceptsEveryRuntimeMenuItemField() {
    for (String field : MenuProfileLoader.knownItemFields()) {
      String value = switch (field) {
        case "enabled", "slotPreviewEnabled", "sliderShowFill" -> "true";
        case "action" -> "noop";
        default -> "1";
      };
      String text = "items=fixture\nitem.fixture." + field + "=" + value + "\n";
      List<String> issues = DslPropertyDiagnostics.menuScreenIssues(
          text,
          MenuProfileLoader.knownScreenFields(),
          MenuProfileLoader.knownItemFields());
      assertTrue(issues.stream().noneMatch(s -> s.contains("Unknown")),
          () -> "Runtime field was rejected by editor: " + field + " -> " + issues);
    }
  }

  @Test
  void menuLayoutDuplicateKeyReportsIssue() {
    String text = "listYStart=0.3\nlistYStart=0.5\n";
    List<String> issues = DslPropertyDiagnostics.menuLayoutIssues(text, LAYOUT_KEYS);
    assertTrue(issues.stream().anyMatch(s -> s.contains("Duplicate")));
  }

  @Test
  void menuLayoutNegativeSubtitleGapReportsIssue() {
    String text = "subtitleGap=-4\n";
    List<String> issues = DslPropertyDiagnostics.menuLayoutIssues(text, LAYOUT_KEYS);
    assertTrue(issues.stream().anyMatch(s -> s.contains("subtitleGap")));
  }

  // ── Menu style diagnostics ──

  private static final Set<String> STYLE_KEYS = Set.of(
      "extends",
      "itemColor", "itemSelectedColor", "itemHoverColor", "itemDisabledColor",
      "itemPrefix", "itemSelectedPrefix", "itemDisabledPrefix",
      "itemFontFamily", "itemFontWeight", "itemFontSize",
      "itemShadowColor", "itemShadowOffsetX", "itemShadowOffsetY", "itemOpacity",
      "buttonAsset", "buttonSelectedAsset", "buttonHoverAsset", "buttonDisabledAsset",
      "buttonTextPaddingX", "buttonTextPaddingY",
      "titleColor", "titleFontFamily", "titleFontWeight", "titleFontSize", "titleShadowColor",
      "hintsColor", "hintsFontFamily", "hintsFontWeight", "hintsFontSize",
      "backgroundAsset", "backgroundColor", "backgroundOpacity");

  @Test
  void menuStyleCleanTemplateProducesNoDiagnostics() {
    String template = LayoutDslTemplates.submenuStyleTemplate();
    List<String> issues = DslPropertyDiagnostics.menuStyleIssues(template, STYLE_KEYS);
    assertTrue(issues.isEmpty(), () -> "Submenu style template should be clean but got: " + issues);
  }

  @Test
  void menuStyleInvalidColorReportsIssue() {
    String text = "itemColor=notacolor\n";
    List<String> issues = DslPropertyDiagnostics.menuStyleIssues(text, STYLE_KEYS);
    assertTrue(issues.stream().anyMatch(s -> s.contains("itemColor")));
  }

  // ── Menu screen diagnostics ──

  private static final Set<String> SCREEN_TOP = Set.of(
      "titleText", "subtitleText", "hintsText", "layout", "layoutId", "defaultItemStyle", "wrapSelection", "items", "backgroundAsset");
  private static final Set<String> SCREEN_ITEM = Set.of(
      "label", "style", "icon", "enabled", "action", "target",
      "bgAsset", "bgSelectedAsset", "bgDisabledAsset",
      "boundsX", "boundsY", "boundsWidth", "boundsHeight",
      "slotPreviewEnabled", "slotPreviewPlaceholderAsset", "slotPreviewFrameAsset",
      "slotPreviewX", "slotPreviewY", "slotPreviewWidth", "slotPreviewHeight",
      "sliderX", "sliderY", "sliderWidth",
      "sliderTrackAsset", "sliderBaseAsset", "sliderTrackHeight", "sliderShowFill",
      "sliderFillAsset", "sliderFillActiveAsset", "sliderFillInactiveAsset",
      "sliderKnobAsset", "sliderKnobActiveAsset", "sliderKnobInactiveAsset",
      "sliderKnobWidth", "sliderKnobHeight", "sliderKnobOffsetX", "sliderKnobOffsetY",
      "sliderResetAsset", "sliderResetActiveAsset", "sliderResetInactiveAsset",
      "sliderResetX", "sliderResetY", "sliderResetWidth", "sliderResetHeight",
      "toggleCheckedAsset", "toggleUncheckedAsset",
      "toggleX", "toggleY", "toggleWidth", "toggleHeight");

  @Test
  void menuScreenCleanTemplateProducesNoDiagnostics() {
    String template = LayoutDslTemplates.defaultMenuScreenTemplate("main");
    List<String> issues = DslPropertyDiagnostics.menuScreenIssues(template, SCREEN_TOP, SCREEN_ITEM);
    assertTrue(issues.isEmpty(), () -> "Default menu screen template should be clean but got: " + issues);
  }

  @Test
  void menuScreenInvalidBooleanReportsIssue() {
    String text = "titleText=Test\nitems=a\nwrapSelection=maybe\n";
    List<String> issues = DslPropertyDiagnostics.menuScreenIssues(text, SCREEN_TOP, SCREEN_ITEM);
    assertTrue(issues.stream().anyMatch(s -> s.contains("wrapSelection")));
  }

  @Test
  void menuScreenRuntimeActionAliasesProduceNoDiagnostics() {
    String text = """
        titleText=Test
        items=start,continue,options,exit
        item.start.action=start
        item.continue.action=continue
        item.options.action=options
        item.exit.action=exit
        """;
    List<String> issues = DslPropertyDiagnostics.menuScreenIssues(text, SCREEN_TOP, SCREEN_ITEM);
    assertTrue(issues.isEmpty(), () -> "Runtime action aliases should be editor-valid but got: " + issues);
  }

  @Test
  void settingsMenuTemplateProducesNoDiagnostics() {
    String template = LayoutDslTemplates.defaultSettingsMenuTemplate();
    List<String> issues = DslPropertyDiagnostics.menuScreenIssues(template, SCREEN_TOP, SCREEN_ITEM);
    assertTrue(issues.isEmpty(), () -> "Settings menu template should be clean but got: " + issues);
  }

  // ── Dialogue diagnostics ──

  @Test
  void dialogueCleanTemplateProducesNoDiagnostics() {
    String template = LayoutDslTemplates.defaultDialogueLayoutTemplate();
    List<String> issues = DslPropertyDiagnostics.dialogueIssues(template, List.of());
    assertTrue(issues.isEmpty(), () -> "Default dialogue template should be clean but got: " + issues);
  }

  @Test
  void dialoguePreservesParserDiagnostics() {
    String template = LayoutDslTemplates.defaultDialogueLayoutTemplate();
    List<String> parserIssues = List.of("Unknown dialogue layout key 'bogus'");
    List<String> issues = DslPropertyDiagnostics.dialogueIssues(template, parserIssues);
    assertTrue(issues.stream().anyMatch(s -> s.contains("bogus")),
        "Parser diagnostics should be preserved in the output");
  }

  // ── Inline comment handling ──

  @Test
  void inlineCommentStrippedFromValue() {
    String text = "listYStart=0.3 # start position\nlineHeight=60\n";
    List<String> issues = DslPropertyDiagnostics.menuLayoutIssues(text, LAYOUT_KEYS);
    // Should NOT report "invalid number '0.3 # start position'"
    assertTrue(issues.stream().noneMatch(s -> s.contains("Invalid number") && s.contains("listYStart")),
        "Inline comment should be stripped; value '0.3' is valid");
  }

  @Test
  void hashInColorValueNotStripped() {
    String text = "itemColor=#FF0000\n";
    List<String> issues = DslPropertyDiagnostics.menuStyleIssues(text, STYLE_KEYS);
    assertTrue(issues.stream().noneMatch(s -> s.contains("itemColor") && s.contains("Invalid")),
        "Color hex starting with # should not be treated as inline comment");
  }

  // ── Template parse stability ──

  @Test
  void registryTemplateParseableAsProperties() throws Exception {
    String text = LayoutDslTemplates.menuRegistryTemplate(
        "main", "main,extras,credits", "default,submenu,slots", "default,submenu,slot");
    Properties p = new Properties();
    p.load(new StringReader(text));
    assertEquals("main", p.getProperty("defaultMenu"));
    assertEquals("main,extras,credits", p.getProperty("menus"));
    assertEquals("default,submenu,slots", p.getProperty("layouts"));
    assertEquals("default,submenu,slot", p.getProperty("styles"));
  }

  @Test
  void submenuLayoutTemplateParseableAsProperties() throws Exception {
    Properties p = new Properties();
    p.load(new StringReader(LayoutDslTemplates.submenuLayoutTemplate()));
    assertEquals("0.32", p.getProperty("listYStart"));
    assertEquals("58", p.getProperty("lineHeight"));
    assertEquals("0.5", p.getProperty("listXCenter"));
  }

  @Test
  void slotsLayoutTemplateParseableAsProperties() throws Exception {
    Properties p = new Properties();
    p.load(new StringReader(LayoutDslTemplates.slotsLayoutTemplate()));
    assertEquals("0.22", p.getProperty("listYStart"));
    assertEquals("68", p.getProperty("lineHeight"));
    assertEquals("0.5", p.getProperty("listXCenter"));
  }

  @Test
  void submenuStyleTemplateParseableAsProperties() throws Exception {
    Properties p = new Properties();
    p.load(new StringReader(LayoutDslTemplates.submenuStyleTemplate()));
    assertFalse(p.isEmpty(), "Submenu style template should produce properties");
    assertEquals("default", p.getProperty("extends"));
    assertEquals("#B8C8E4", p.getProperty("itemColor"));
  }

  @Test
  void slotStyleTemplateParseableAsProperties() throws Exception {
    Properties p = new Properties();
    p.load(new StringReader(LayoutDslTemplates.slotStyleTemplate()));
    assertFalse(p.isEmpty(), "Slot style template should produce properties");
    assertEquals("default", p.getProperty("extends"));
    assertEquals("#C4D4EC", p.getProperty("itemColor"));
  }

  @Test
  void settingsStyleTemplateParseableAsProperties() throws Exception {
    Properties p = new Properties();
    p.load(new StringReader(LayoutDslTemplates.settingsStyleTemplate()));
    assertFalse(p.isEmpty(), "Settings style template should produce properties");
    assertEquals("#D7E2F4", p.getProperty("itemColor"));
    assertEquals("#08101E", p.getProperty("backgroundColor"));
  }

  @Test
  void defaultStyleFullTemplateParseableAsProperties() throws Exception {
    Properties p = new Properties();
    p.load(new StringReader(LayoutDslTemplates.defaultMenuStyleFullTemplate("assets/bg.png")));
    assertEquals("assets/bg.png", p.getProperty("backgroundAsset"));
    assertEquals("#C8D6EC", p.getProperty("itemColor"));
  }

  // ── Malformed input ──

  @Test
  void malformedLineProducesDiagnostic() {
    String text = "this line has no equals sign\n";
    List<String> issues = DslPropertyDiagnostics.menuLayoutIssues(text, LAYOUT_KEYS);
    assertTrue(issues.stream().anyMatch(s -> s.contains("Malformed")));
  }

  @Test
  void emptyKeyProducesDiagnostic() {
    String text = "=someValue\n";
    List<String> issues = DslPropertyDiagnostics.menuLayoutIssues(text, LAYOUT_KEYS);
    assertTrue(issues.stream().anyMatch(s -> s.contains("empty")));
  }

  @Test
  void blankInputProducesNoDiagnostics() {
    List<String> issues = DslPropertyDiagnostics.menuLayoutIssues("", LAYOUT_KEYS);
    assertTrue(issues.isEmpty());
  }

  @Test
  void nullInputProducesNoDiagnostics() {
    List<String> issues = DslPropertyDiagnostics.menuLayoutIssues(null, LAYOUT_KEYS);
    assertTrue(issues.isEmpty());
  }
}
