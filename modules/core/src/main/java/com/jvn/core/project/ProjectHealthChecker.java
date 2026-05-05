package com.jvn.core.project;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.assets.ClasspathAssetManager;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.assets.OverlayAssetManager;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuProfileLoader;
import com.jvn.core.menu.config.MenuScreenSpec;
import com.jvn.core.menu.config.MenuStyleSpec;
import com.jvn.core.phone.VnPhoneData;
import com.jvn.core.phone.VnPhonePropertiesCodec;
import com.jvn.core.vn.VnEntryScriptResolver;
import com.jvn.core.vn.ui.VnCursorConfigLoader;
import com.jvn.core.vn.ui.VnUiActionButtonSpec;
import com.jvn.core.vn.ui.VnUiLayoutLoader;
import com.jvn.core.vn.ui.VnUiStyleSpec;

/**
 * Aggregates engine-facing project diagnostics for runtime and editor consumers.
 */
public final class ProjectHealthChecker {
  public enum Severity {
    ERROR,
    WARNING
  }

  public record Diagnostic(Severity severity, String category, String location, String message) {
    public Diagnostic {
      severity = severity == null ? Severity.WARNING : severity;
      category = normalize(category, "project");
      location = normalize(location, null);
      message = normalize(message, "");
    }
  }

  public record Report(List<Diagnostic> diagnostics) {
    public Report {
      diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public int errorCount() {
      return (int) diagnostics.stream().filter(d -> d.severity() == Severity.ERROR).count();
    }

    public int warningCount() {
      return (int) diagnostics.stream().filter(d -> d.severity() == Severity.WARNING).count();
    }

    public boolean hasIssues() {
      return !diagnostics.isEmpty();
    }
  }

  private ProjectHealthChecker() {
  }

  public static Report inspect(File projectRoot) {
    if (projectRoot == null || !projectRoot.isDirectory()) {
      return new Report(List.of());
    }

    AssetCatalog assets = new AssetCatalog(new OverlayAssetManager(
        new FilesystemAssetManager(projectRoot.toPath()),
        new ClasspathAssetManager()));
    List<Diagnostic> diagnostics = new ArrayList<>();
    Properties manifest = loadManifest(projectRoot);
    inspectEntryScript(projectRoot, assets, manifest, diagnostics);
    inspectMenu(assets, diagnostics);
    inspectDialogue(projectRoot, assets, diagnostics);
    inspectPhone(projectRoot, diagnostics);
    inspectLocalization(assets, manifest, diagnostics);
    return new Report(diagnostics);
  }

  private static void inspectEntryScript(
      File projectRoot,
      AssetCatalog assets,
      Properties manifest,
      List<Diagnostic> diagnostics
  ) {
    String configured = manifest == null ? null : normalize(manifest.getProperty("entryVns"), null);
    if (configured != null && !assetExists(assets, AssetType.SCRIPT, configured)) {
      diagnostics.add(new Diagnostic(
          Severity.ERROR,
          "script",
          "jvn.project",
          "Configured entryVns is missing: " + configured
      ));
      return;
    }

    String resolved = VnEntryScriptResolver.resolveEntryScript(null, projectRoot);
    if (resolved == null) {
      diagnostics.add(new Diagnostic(
          Severity.ERROR,
          "script",
          "jvn.project",
          "No VN entry script could be resolved from entryVns or script discovery"
      ));
      return;
    }
    if (!assetExists(assets, AssetType.SCRIPT, resolved)) {
      diagnostics.add(new Diagnostic(
          Severity.ERROR,
          "script",
          resolved,
          "Resolved entry script is missing"
      ));
    }
  }

  private static void inspectMenu(AssetCatalog assets, List<Diagnostic> diagnostics) {
    MenuProfileLoader.LoadResult load = MenuProfileLoader.loadWithDiagnostics(assets);
    for (String diagnostic : load.diagnostics()) {
      diagnostics.add(new Diagnostic(Severity.WARNING, "menu", "config/menu", diagnostic));
    }
    MenuProfile profile = load.profile();
    if (profile == null) return;

    for (MenuStyleSpec style : profile.styles().values()) {
      if (style == null) continue;
      warnMissingAsset(assets, diagnostics, "menu", "style:" + style.id(), style.buttonAssetPath());
      warnMissingAsset(assets, diagnostics, "menu", "style:" + style.id(), style.buttonSelectedAssetPath());
      warnMissingAsset(assets, diagnostics, "menu", "style:" + style.id(), style.buttonHoverAssetPath());
      warnMissingAsset(assets, diagnostics, "menu", "style:" + style.id(), style.buttonDisabledAssetPath());
      warnMissingAsset(assets, diagnostics, "menu", "style:" + style.id(), style.backgroundAssetPath());
    }

    for (Map.Entry<String, MenuScreenSpec> entry : profile.screens().entrySet()) {
      MenuScreenSpec screen = entry.getValue();
      if (screen == null) continue;
      String location = "menu:" + entry.getKey();
      warnMissingAsset(assets, diagnostics, "menu", location, screen.backgroundAsset());
      for (MenuItemSpec item : screen.items()) {
        if (item == null) continue;
        warnMissingAsset(assets, diagnostics, "menu", location + "#" + item.id(), item.iconPath());
        warnMissingAsset(assets, diagnostics, "menu", location + "#" + item.id(), item.buttonAssetPath());
        warnMissingAsset(assets, diagnostics, "menu", location + "#" + item.id(), item.buttonSelectedAssetPath());
        warnMissingAsset(assets, diagnostics, "menu", location + "#" + item.id(), item.buttonDisabledAssetPath());
        warnMissingAsset(assets, diagnostics, "menu", location + "#" + item.id(), item.slotPreviewPlaceholderAssetPath());
        warnMissingAsset(assets, diagnostics, "menu", location + "#" + item.id(), item.slotPreviewFrameAssetPath());
        for (Map.Entry<String, String> extra : item.extras().entrySet()) {
          String key = extra.getKey() == null ? "" : extra.getKey().toLowerCase(Locale.ROOT);
          if (!key.contains("asset")) continue;
          warnMissingAsset(assets, diagnostics, "menu", location + "#" + item.id(), extra.getValue());
        }
      }
    }
  }

  private static void inspectDialogue(File projectRoot, AssetCatalog assets, List<Diagnostic> diagnostics) {
    VnUiLayoutLoader.LoadResult load = VnUiLayoutLoader.loadFromProjectRootWithDiagnostics(projectRoot);
    for (String diagnostic : load.diagnostics()) {
      diagnostics.add(new Diagnostic(Severity.WARNING, "dialogue", "dialogue.layout", diagnostic));
    }

    VnUiStyleSpec style = load.style();
    if (style != null) {
      warnMissingAsset(assets, diagnostics, "dialogue", "dialogue.layout", style.textBoxAssetPath());
      warnMissingAsset(assets, diagnostics, "dialogue", "dialogue.layout", style.textBoxNarrationAssetPath());
      warnMissingAsset(assets, diagnostics, "dialogue", "dialogue.layout", style.nameBoxAssetPath());
      warnMissingAsset(assets, diagnostics, "dialogue", "dialogue.layout", style.choiceButtonAssetPath());
      warnMissingAsset(assets, diagnostics, "dialogue", "dialogue.layout", style.choiceButtonHoverAssetPath());
      warnMissingAsset(assets, diagnostics, "dialogue", "dialogue.layout", style.choiceButtonSelectedAssetPath());
      warnMissingAsset(assets, diagnostics, "dialogue", "dialogue.layout", style.choiceButtonDisabledAssetPath());
      warnMissingAsset(assets, diagnostics, "dialogue", "dialogue.layout", style.nvlPanelAssetPath());
      warnMissingAsset(assets, diagnostics, "dialogue", "dialogue.layout", style.bubbleAssetPath());
    }
    for (VnUiActionButtonSpec button : load.textBoxButtons()) {
      if (button == null) continue;
      String location = "dialogue.layout#textBoxButton." + button.id();
      warnMissingAsset(assets, diagnostics, "dialogue", location, button.assetPath());
      warnMissingAsset(assets, diagnostics, "dialogue", location, button.hoverAssetPath());
      warnMissingAsset(assets, diagnostics, "dialogue", location, button.disabledAssetPath());
    }

    VnCursorConfigLoader.LoadResult cursorLoad = VnCursorConfigLoader.loadFromProjectRootWithDiagnostics(projectRoot);
    for (String diagnostic : cursorLoad.diagnostics()) {
      diagnostics.add(new Diagnostic(Severity.WARNING, "cursor", "vn.settings", diagnostic));
    }
    VnCursorConfigLoader.VnCursorConfig cursor = cursorLoad.config();
    if (cursor != null) {
      warnMissingAsset(assets, diagnostics, "cursor", "vn.settings", cursor.assetPath());
    }
  }

  private static void inspectPhone(File projectRoot, List<Diagnostic> diagnostics) {
    VnPhonePropertiesCodec.LoadResult load = VnPhonePropertiesCodec.loadFromProjectRootWithDiagnostics(projectRoot);
    for (String diagnostic : load.diagnostics()) {
      diagnostics.add(new Diagnostic(Severity.WARNING, "phone", "config/phone/phone.properties", diagnostic));
    }
    VnPhoneData data = load.data();
    if (data != null && data.getChats().isEmpty() && data.getApps().isEmpty() && data.getCalls().isEmpty()
        && data.getContacts().isEmpty() && !hasPhoneConfig(projectRoot)) {
      return;
    }
  }

  private static void inspectLocalization(AssetCatalog assets, Properties manifest, List<Diagnostic> diagnostics) {
    String locale = manifest == null ? null : normalize(manifest.getProperty("runtime.locale"), null);
    if (locale == null) locale = "en";
    String english = "game/strings/en.properties";
    if (!assetExistsAny(assets, english, "strings/en.properties")) {
      diagnostics.add(new Diagnostic(
          Severity.WARNING,
          "localization",
          english,
          "English fallback string table is missing"
      ));
    }

    if (!"en".equalsIgnoreCase(locale)) {
      String requested = "game/strings/" + locale + ".properties";
      if (!assetExistsAny(assets, requested, "strings/" + locale + ".properties")) {
        diagnostics.add(new Diagnostic(
            Severity.WARNING,
            "localization",
            requested,
            "Configured runtime.locale '" + locale + "' has no string table; runtime will fall back to English"
        ));
      }
    }
  }

  private static boolean hasPhoneConfig(File projectRoot) {
    if (projectRoot == null) return false;
    return new File(projectRoot, VnPhonePropertiesCodec.DIRECT_CONFIG_PATH).isFile()
        || new File(projectRoot, "game/config/phone/phone.properties").isFile();
  }

  private static void warnMissingAsset(
      AssetCatalog assets,
      List<Diagnostic> diagnostics,
      String category,
      String location,
      String path
  ) {
    if (path == null || path.isBlank()) return;
    if (assetExistsAnyType(assets, path)) return;
    diagnostics.add(new Diagnostic(
        Severity.WARNING,
        category,
        location,
        "Missing asset: " + path
    ));
  }

  private static boolean assetExistsAnyType(AssetCatalog assets, String path) {
    if (assets == null || path == null || path.isBlank()) return false;
    for (AssetType type : AssetType.values()) {
      if (assetExists(assets, type, path)) return true;
    }
    return false;
  }

  private static boolean assetExists(AssetCatalog assets, AssetType type, String path) {
    try {
      return assets != null && type != null && path != null && assets.exists(type, path);
    } catch (Exception ignored) {
      return false;
    }
  }

  private static boolean assetExistsAny(AssetCatalog assets, String... paths) {
    if (paths == null) return false;
    for (String path : paths) {
      if (assetExistsAnyType(assets, path)) return true;
    }
    return false;
  }

  private static Properties loadManifest(File projectRoot) {
    if (projectRoot == null) return null;
    File manifest = new File(projectRoot, "jvn.project");
    if (!manifest.isFile()) return null;
    try (FileInputStream in = new FileInputStream(manifest)) {
      Properties props = new Properties();
      props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
      return props;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String normalize(String value, String fallback) {
    if (value == null) return fallback;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? fallback : trimmed;
  }
}
