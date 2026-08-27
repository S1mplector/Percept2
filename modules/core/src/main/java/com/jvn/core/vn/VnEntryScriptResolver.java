package com.jvn.core.vn;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Resolves the runtime VN entry script with a stable precedence:
 * explicit script -> project manifest -> system property -> discovered scripts.
 */
public final class VnEntryScriptResolver {
  private static final String ENTRY_SCRIPT_PROPERTY = "jvn.entryVns";
  private static final List<String> PREFERRED_SCRIPT_KEYS = List.of(
      "story/prologue.vns",
      "prologue.vns",
      "story/main.vns",
      "main.vns",
      "story/start.vns",
      "start.vns"
  );

  private VnEntryScriptResolver() {
  }

  public static String resolveEntryScript(String explicitScript, File projectRoot) {
    String explicit = normalizeScriptKey(explicitScript);
    if (explicit != null) return explicit;

    String fromManifest = resolveFromManifest(projectRoot);
    if (fromManifest != null) return fromManifest;

    String fromProperty = resolveFromSystemProperty();
    if (fromProperty != null) return fromProperty;

    return discoverFromProject(projectRoot);
  }

  public static String resolveFromManifest(File projectRoot) {
    if (projectRoot == null) return null;
    File manifest = new File(projectRoot, "jvn.project");
    if (!manifest.isFile()) return null;
    Properties props = new Properties();
    try (FileInputStream fis = new FileInputStream(manifest)) {
      props.load(fis);
    } catch (IOException e) {
      return null;
    }
    return normalizeScriptKey(props.getProperty("entryVns"));
  }

  public static String resolveFromSystemProperty() {
    return normalizeScriptKey(System.getProperty(ENTRY_SCRIPT_PROPERTY));
  }

  public static void publishToSystemProperty(String scriptName) {
    String normalized = normalizeScriptKey(scriptName);
    if (normalized == null) {
      System.clearProperty(ENTRY_SCRIPT_PROPERTY);
    } else {
      System.setProperty(ENTRY_SCRIPT_PROPERTY, normalized);
    }
  }

  public static String normalizeScriptKey(String raw) {
    if (raw == null) return null;
    String script = raw.trim().replace('\\', '/');
    if (script.isBlank()) return null;
    while (script.startsWith("./")) script = script.substring(2);
    while (script.startsWith("/")) script = script.substring(1);
    if (script.startsWith("game/scripts/")) script = script.substring("game/scripts/".length());
    if (script.startsWith("scripts/")) script = script.substring("scripts/".length());
    return script.isBlank() ? null : script;
  }

  static String discoverFromProject(File projectRoot) {
    if (projectRoot == null) return null;
    Path root = Paths.get(projectRoot.getAbsolutePath()).normalize();
    Path scriptsRootPath = root.resolve("scripts");
    if (!Files.isDirectory(scriptsRootPath)) {
      scriptsRootPath = root.resolve("game").resolve("scripts");
    }
    if (!Files.isDirectory(scriptsRootPath)) return null;
    final Path scriptsRoot = scriptsRootPath;

    List<String> scriptKeys = new ArrayList<>();
    try (var stream = Files.walk(scriptsRoot)) {
      stream.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".vns"))
          .forEach(path -> {
            String key = scriptsRoot.relativize(path).toString().replace('\\', '/');
            if (!key.isBlank()) scriptKeys.add(key);
          });
    } catch (IOException e) {
      return null;
    }
    return pickBestScript(scriptKeys);
  }

  static String pickBestScript(List<String> scriptKeys) {
    if (scriptKeys == null || scriptKeys.isEmpty()) return null;
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String key : scriptKeys) {
      String cleaned = normalizeScriptKey(key);
      if (cleaned != null) normalized.add(cleaned);
    }
    if (normalized.isEmpty()) return null;

    List<String> ordered = new ArrayList<>(normalized);
    ordered.sort(Comparator
        .comparingInt(VnEntryScriptResolver::priorityScore)
        .thenComparing(s -> s.toLowerCase(Locale.ROOT)));
    return ordered.get(0);
  }

  private static int priorityScore(String scriptKey) {
    if (scriptKey == null) return Integer.MAX_VALUE;
    String key = scriptKey.toLowerCase(Locale.ROOT);
    int preferredIndex = PREFERRED_SCRIPT_KEYS.indexOf(key);
    if (preferredIndex >= 0) return preferredIndex;

    String file = key.substring(key.lastIndexOf('/') + 1);
    if (file.contains("prologue")) return 100;
    if (file.contains("start")) return 110;
    if (file.contains("main")) return 120;
    return 1000;
  }
}
