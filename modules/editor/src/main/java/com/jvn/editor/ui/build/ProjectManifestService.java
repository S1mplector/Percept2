package com.jvn.editor.ui.build;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public class ProjectManifestService {

  public static Properties loadManifest(File root) {
    if (root == null) return null;
    File manifest = new File(root, "jvn.project");
    if (!manifest.isFile()) return null;
    try (FileInputStream in = new FileInputStream(manifest)) {
      Properties props = new Properties();
      props.load(in);
      return props;
    } catch (Exception ignored) {
      return null;
    }
  }

  public static String manifestEntryText(Properties manifest) {
    String type = manifest.getProperty("type", "vn").trim().toLowerCase(Locale.ROOT);
    if ("jes".equals(type)) {
      return "entry=" + manifest.getProperty("entry", "scripts/main.jes");
    }
    return "entryVns=" + manifest.getProperty("entryVns", "(auto)");
  }

  public static void validateManifest(File root, Properties manifest, List<String> errors, List<String> warnings) {
    String type = manifest.getProperty("type", "vn").trim().toLowerCase(Locale.ROOT);
    if (type.isBlank()) type = "vn";
    switch (type) {
      case "vn" -> {
        String entry = normalizeScriptKey(manifest.getProperty("entryVns"));
        if (entry == null) {
          File discovered = discoverScript(root, "vns");
          if (discovered == null) {
            errors.add("No VN entry script could be resolved. Set entryVns or add a .vns file under scripts/.");
          } else {
            warnings.add("entryVns is not set; runtime will use discovered script " + relativeTo(root, discovered) + ".");
          }
        } else if (resolveScriptFile(root, entry) == null) {
          errors.add("Configured entryVns is missing: " + manifest.getProperty("entryVns"));
        }
      }
      case "jes" -> {
        String entry = normalizeProjectPath(manifest.getProperty("entry", "scripts/main.jes"));
        if (entry == null) {
          errors.add("JES projects must define entry=<path-to-jes> in jvn.project.");
        } else if (resolveScriptFile(root, entry) == null) {
          errors.add("Configured JES entry is missing: " + entry);
        }
      }
      case "gradle" -> errors.add("type=gradle describes a workspace run command, not a distributable game package.");
      default -> errors.add("Unsupported jvn.project type for packaging: " + type + ". Supported types: vn, jes.");
    }

    if (!new File(root, "scripts").isDirectory() && !new File(root, "game/scripts").isDirectory()) {
      warnings.add("No scripts/ or game/scripts/ directory was found.");
    }
    if (!new File(root, "assets").isDirectory() && !new File(root, "game").isDirectory()) {
      warnings.add("No assets/ or game/ directory was found; package may be script-only.");
    }
  }

  public static File resolveScriptFile(File root, String raw) {
    if (root == null) return null;
    String normalized = normalizeProjectPath(raw);
    if (normalized == null) return null;
    String scriptKey = normalizeScriptKey(normalized);
    if (scriptKey == null) scriptKey = normalized;

    List<File> candidates = new ArrayList<>();
    addCandidate(candidates, new File(root, normalized));
    addCandidate(candidates, new File(root, scriptKey));
    addCandidate(candidates, new File(root, "scripts/" + scriptKey));
    addCandidate(candidates, new File(root, "game/scripts/" + scriptKey));
    if (normalized.startsWith("game/") && !normalized.startsWith("game/scripts/")) {
      addCandidate(candidates, new File(root, "scripts/" + normalized.substring("game/".length())));
    }

    for (File candidate : candidates) {
      if (candidate.isFile()) return candidate;
    }
    return null;
  }

  private static void addCandidate(List<File> candidates, File candidate) {
    if (candidate != null && !candidates.contains(candidate)) {
      candidates.add(candidate);
    }
  }

  public static File discoverScript(File root, String extension) {
    if (root == null) return null;
    File scripts = new File(root, "scripts");
    if (!scripts.isDirectory()) scripts = new File(root, "game/scripts");
    if (!scripts.isDirectory()) return null;
    File[] files = scripts.listFiles();
    if (files == null) return null;
    List<File> matches = new ArrayList<>();
    collectScripts(scripts, extension.startsWith(".") ? extension : "." + extension, matches);
    matches.sort((a, b) -> scoreScript(a.getName()) == scoreScript(b.getName())
        ? a.getPath().compareToIgnoreCase(b.getPath())
        : Integer.compare(scoreScript(a.getName()), scoreScript(b.getName())));
    return matches.isEmpty() ? null : matches.get(0);
  }

  private static void collectScripts(File dir, String extension, List<File> out) {
    File[] files = dir.listFiles();
    if (files == null) return;
    for (File file : files) {
      if (file.isDirectory()) {
        collectScripts(file, extension, out);
      } else if (file.getName().toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT))) {
        out.add(file);
      }
    }
  }

  private static int scoreScript(String name) {
    String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
    if (lower.equals("prologue.vns") || lower.equals("prologue.jes")) return 0;
    if (lower.equals("main.vns") || lower.equals("main.jes")) return 1;
    if (lower.equals("start.vns") || lower.equals("start.jes")) return 2;
    if (lower.contains("prologue")) return 10;
    if (lower.contains("start")) return 11;
    if (lower.contains("main")) return 12;
    return 100;
  }

  public static String relativeTo(File root, File file) {
    if (root == null || file == null) return "";
    try {
      return root.toPath().toAbsolutePath().normalize()
          .relativize(file.toPath().toAbsolutePath().normalize())
          .toString()
          .replace('\\', '/');
    } catch (Exception ignored) {
      return file.getPath();
    }
  }

  public static String normalizeProjectPath(String raw) {
    if (raw == null) return null;
    String value = raw.trim().replace('\\', '/');
    if (value.isBlank()) return null;
    while (value.startsWith("./")) value = value.substring(2);
    while (value.startsWith("/")) value = value.substring(1);
    return value.isBlank() ? null : value;
  }

  public static String normalizeScriptKey(String raw) {
    String value = normalizeProjectPath(raw);
    if (value == null) return null;
    if (value.startsWith("game/scripts/")) value = value.substring("game/scripts/".length());
    if (value.startsWith("scripts/")) value = value.substring("scripts/".length());
    return value.isBlank() ? null : value;
  }

  public static boolean sameCanonical(File a, File b) {
    if (a == null || b == null) return false;
    try {
      return a.getCanonicalFile().equals(b.getCanonicalFile());
    } catch (Exception ignored) {
      return a.getAbsoluteFile().equals(b.getAbsoluteFile());
    }
  }

  public static String firstNonBlank(String... values) {
    if (values != null) {
      for (String value : values) {
        if (value != null && !value.trim().isBlank()) return value.trim();
      }
    }
    return "";
  }
}
