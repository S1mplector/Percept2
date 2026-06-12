package com.jvn.core.localization;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Command-line entry point for translation extraction/update workflows.
 */
public final class TranslationCli {
  private TranslationCli() {
  }

  public static void main(String[] args) throws Exception {
    Map<String, String> options = parseOptions(args);
    String command = options.getOrDefault("_command", "extract");
    if (!command.equals("extract") && !command.equals("update")) {
      throw new IllegalArgumentException("Expected command 'extract' or 'update'");
    }

    Path project = Path.of(options.getOrDefault("project", ".")).toAbsolutePath().normalize();
    String locale = options.getOrDefault("locale", readManifestLocale(project));
    if (locale == null || locale.isBlank()) locale = "en";
    String sourceLocale = options.getOrDefault("source-locale", "en");
    Path output = options.containsKey("output")
        ? Path.of(options.get("output")).toAbsolutePath().normalize()
        : project.resolve("config/locales/" + locale + ".properties");
    boolean emptyMissing = parseBoolean(options.get("empty-missing"));
    boolean dryRun = parseBoolean(options.get("dry-run"));

    List<TranslationEntry> entries = TranslationExtractor.extract(project);
    if (dryRun) {
      System.out.println("JVN translation " + command + " dry run");
      System.out.println("Project : " + project);
      System.out.println("Locale  : " + locale);
      System.out.println("Output  : " + output);
      System.out.println("Entries : " + entries.size());
      return;
    }

    TranslationCatalogWriter.WriteResult result =
        TranslationCatalogWriter.write(output, entries, locale, sourceLocale, emptyMissing);
    System.out.println("JVN translation catalog updated");
    System.out.println("Project   : " + project);
    System.out.println("Locale    : " + locale);
    System.out.println("Output    : " + result.output());
    System.out.println("Entries   : " + result.entries());
    System.out.println("Added     : " + result.added());
    System.out.println("Preserved : " + result.preserved());
    System.out.println("Retained  : " + result.retained());
  }

  private static Map<String, String> parseOptions(String[] args) {
    Map<String, String> out = new LinkedHashMap<>();
    if (args == null) return out;
    int index = 0;
    if (args.length > 0 && args[0] != null && !args[0].startsWith("--")) {
      out.put("_command", args[0].trim().toLowerCase(Locale.ROOT));
      index = 1;
    }
    for (int i = index; i < args.length; i++) {
      String arg = args[i] == null ? "" : args[i].trim();
      if (arg.isEmpty()) continue;
      if (!arg.startsWith("--")) continue;
      String key;
      String value;
      int eq = arg.indexOf('=');
      if (eq > 2) {
        key = arg.substring(2, eq);
        value = arg.substring(eq + 1);
      } else {
        key = arg.substring(2);
        if (i + 1 < args.length && args[i + 1] != null && !args[i + 1].startsWith("--")) {
          value = args[++i];
        } else {
          value = "true";
        }
      }
      out.put(key.trim().toLowerCase(Locale.ROOT), value.trim());
    }
    return out;
  }

  private static String readManifestLocale(Path project) {
    try {
      Path manifest = project.resolve("jvn.project");
      if (!java.nio.file.Files.exists(manifest)) return "en";
      PropertiesReader props = new PropertiesReader(manifest);
      String runtime = props.get("runtime.locale");
      if (runtime != null && !runtime.isBlank()) return runtime;
      String legacy = props.get("locale");
      return legacy == null || legacy.isBlank() ? "en" : legacy;
    } catch (Exception ex) {
      return "en";
    }
  }

  private static boolean parseBoolean(String raw) {
    if (raw == null || raw.isBlank()) return false;
    String value = raw.trim().toLowerCase(Locale.ROOT);
    return value.equals("true") || value.equals("1") || value.equals("yes") || value.equals("on");
  }

  private static final class PropertiesReader {
    private final java.util.Properties props = new java.util.Properties();

    PropertiesReader(Path path) throws java.io.IOException {
      try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(path, java.nio.charset.StandardCharsets.UTF_8)) {
        props.load(reader);
      }
    }

    String get(String key) {
      return props.getProperty(key);
    }
  }
}
