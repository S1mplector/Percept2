package com.jvn.hub;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;

/** Persistent JVM memory policy for applications launched by Engine Hub. */
final class JvmLaunchSettings {
  static final int MIN_HEAP_MB = 128;
  static final int MAX_HEAP_MB = 131_072;

  private static final String KEY_INITIAL_HEAP_MB = "heap.initialMb";
  private static final String KEY_MAX_HEAP_MB = "heap.maxMb";
  private static final String KEY_COLLECTOR = "gc.collector";
  private static final String KEY_HEAP_DUMP = "oom.heapDump";
  private static final String KEY_EXIT_ON_OOM = "oom.exit";
  private static final String KEY_STRING_DEDUPLICATION = "memory.stringDeduplication";
  private static final String KEY_EXTRA_ARGS = "jvm.extraArgs";

  enum Collector {
    DEFAULT("JDK default", ""),
    G1("G1 (balanced)", "-XX:+UseG1GC"),
    ZGC("ZGC (low pause)", "-XX:+UseZGC"),
    SERIAL("Serial (low footprint)", "-XX:+UseSerialGC");

    private final String displayName;
    private final String argument;

    Collector(String displayName, String argument) {
      this.displayName = displayName;
      this.argument = argument;
    }

    String argument() { return argument; }

    @Override public String toString() { return displayName; }

    static Collector parse(String raw) {
      if (raw == null || raw.isBlank()) return DEFAULT;
      try {
        return valueOf(raw.trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
        return DEFAULT;
      }
    }
  }

  private final int initialHeapMb;
  private final int maxHeapMb;
  private final Collector collector;
  private final boolean heapDumpOnOutOfMemory;
  private final boolean exitOnOutOfMemory;
  private final boolean stringDeduplication;
  private final String extraJvmArgs;

  JvmLaunchSettings(
      int initialHeapMb,
      int maxHeapMb,
      Collector collector,
      boolean heapDumpOnOutOfMemory,
      boolean exitOnOutOfMemory,
      boolean stringDeduplication,
      String extraJvmArgs
  ) {
    this.initialHeapMb = initialHeapMb;
    this.maxHeapMb = maxHeapMb;
    this.collector = collector == null ? Collector.DEFAULT : collector;
    this.heapDumpOnOutOfMemory = heapDumpOnOutOfMemory;
    this.exitOnOutOfMemory = exitOnOutOfMemory;
    this.stringDeduplication = stringDeduplication;
    this.extraJvmArgs = extraJvmArgs == null ? "" : extraJvmArgs.trim();
  }

  static JvmLaunchSettings defaults() {
    return new JvmLaunchSettings(0, 0, Collector.DEFAULT, false, false, false, "");
  }

  static Path defaultSettingsFile() {
    return Paths.get(System.getProperty("user.home", "."), ".jvn", "jvm-launch.properties");
  }

  static Path defaultArgumentsFile() {
    return Paths.get(System.getProperty("user.home", "."), ".jvn", "jvm-launch.args");
  }

  static Path defaultHeapDumpDirectory() {
    return Paths.get(System.getProperty("user.home", "."), ".jvn", "heap-dumps");
  }

  static JvmLaunchSettings load(Path file) {
    JvmLaunchSettings defaults = defaults();
    if (file == null || !Files.isRegularFile(file)) return defaults;
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(file)) {
      properties.load(input);
    } catch (IOException | IllegalArgumentException ignored) {
      return defaults;
    }
    JvmLaunchSettings loaded = new JvmLaunchSettings(
        parseHeap(properties.getProperty(KEY_INITIAL_HEAP_MB), 0),
        parseHeap(properties.getProperty(KEY_MAX_HEAP_MB), 0),
        Collector.parse(properties.getProperty(KEY_COLLECTOR)),
        parseBoolean(properties.getProperty(KEY_HEAP_DUMP), false),
        parseBoolean(properties.getProperty(KEY_EXIT_ON_OOM), false),
        parseBoolean(properties.getProperty(KEY_STRING_DEDUPLICATION), false),
        properties.getProperty(KEY_EXTRA_ARGS, ""));
    return loaded.validationError().isEmpty() ? loaded : defaults;
  }

  void save(Path file) throws IOException {
    Optional<String> error = validationError();
    if (error.isPresent()) throw new IllegalArgumentException(error.get());
    if (file == null) throw new IllegalArgumentException("Settings file is required.");
    Files.createDirectories(file.toAbsolutePath().getParent());
    Properties properties = new Properties();
    properties.setProperty(KEY_INITIAL_HEAP_MB, Integer.toString(initialHeapMb));
    properties.setProperty(KEY_MAX_HEAP_MB, Integer.toString(maxHeapMb));
    properties.setProperty(KEY_COLLECTOR, collector.name().toLowerCase(Locale.ROOT));
    properties.setProperty(KEY_HEAP_DUMP, Boolean.toString(heapDumpOnOutOfMemory));
    properties.setProperty(KEY_EXIT_ON_OOM, Boolean.toString(exitOnOutOfMemory));
    properties.setProperty(KEY_STRING_DEDUPLICATION, Boolean.toString(stringDeduplication));
    properties.setProperty(KEY_EXTRA_ARGS, extraJvmArgs);
    try (var output = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
      properties.store(output, "JVN application JVM launch settings. Auto-generated.");
    }
  }

  void writeArguments(Path file, Path heapDumpDirectory) throws IOException {
    Optional<String> error = validationError();
    if (error.isPresent()) throw new IllegalArgumentException(error.get());
    if (file == null) throw new IllegalArgumentException("Arguments file is required.");
    Files.createDirectories(file.toAbsolutePath().getParent());
    Files.write(file, jvmArguments(heapDumpDirectory), StandardCharsets.UTF_8);
  }

  List<String> jvmArguments(Path heapDumpDirectory) {
    List<String> arguments = new ArrayList<>();
    if (initialHeapMb > 0) arguments.add("-Xms" + initialHeapMb + "m");
    if (maxHeapMb > 0) arguments.add("-Xmx" + maxHeapMb + "m");
    if (!collector.argument().isBlank()) arguments.add(collector.argument());
    if (heapDumpOnOutOfMemory) {
      arguments.add("-XX:+HeapDumpOnOutOfMemoryError");
      Path directory = heapDumpDirectory == null ? defaultHeapDumpDirectory() : heapDumpDirectory;
      arguments.add("-XX:HeapDumpPath=" + directory.toAbsolutePath());
    }
    if (exitOnOutOfMemory) arguments.add("-XX:+ExitOnOutOfMemoryError");
    if (stringDeduplication) arguments.add("-XX:+UseStringDeduplication");
    arguments.addAll(splitArguments(extraJvmArgs));
    return List.copyOf(arguments);
  }

  Optional<String> validationError() {
    if (!validHeap(initialHeapMb)) {
      return Optional.of("Initial heap must be 0 (automatic) or between "
          + MIN_HEAP_MB + " and " + MAX_HEAP_MB + " MiB.");
    }
    if (!validHeap(maxHeapMb)) {
      return Optional.of("Maximum heap must be 0 (automatic) or between "
          + MIN_HEAP_MB + " and " + MAX_HEAP_MB + " MiB.");
    }
    if (initialHeapMb > 0 && maxHeapMb > 0 && initialHeapMb > maxHeapMb) {
      return Optional.of("Initial heap cannot be greater than maximum heap.");
    }
    if (stringDeduplication && collector != Collector.G1 && collector != Collector.ZGC) {
      return Optional.of("String deduplication requires explicitly selecting G1 or ZGC.");
    }
    if (extraJvmArgs.indexOf('\n') >= 0 || extraJvmArgs.indexOf('\r') >= 0) {
      return Optional.of("Extra JVM arguments cannot contain line breaks.");
    }
    if (!hasBalancedQuotes(extraJvmArgs)) {
      return Optional.of("Extra JVM arguments contain an unmatched quote.");
    }
    for (String argument : splitArguments(extraJvmArgs)) {
      if (isManagedArgument(argument)) {
        return Optional.of("Configure " + argument + " with the dedicated memory controls instead of Extra JVM arguments.");
      }
    }
    return Optional.empty();
  }

  String summary() {
    String heap = initialHeapMb == 0 && maxHeapMb == 0
        ? "Heap automatic"
        : "Heap " + (initialHeapMb == 0 ? "auto" : initialHeapMb + " MiB")
            + " → " + (maxHeapMb == 0 ? "auto" : maxHeapMb + " MiB");
    return heap + " · " + collector + " · OOM dump " + (heapDumpOnOutOfMemory ? "on" : "off");
  }

  int initialHeapMb() { return initialHeapMb; }
  int maxHeapMb() { return maxHeapMb; }
  Collector collector() { return collector; }
  boolean heapDumpOnOutOfMemory() { return heapDumpOnOutOfMemory; }
  boolean exitOnOutOfMemory() { return exitOnOutOfMemory; }
  boolean stringDeduplication() { return stringDeduplication; }
  String extraJvmArgs() { return extraJvmArgs; }

  static List<String> splitArguments(String raw) {
    if (raw == null || raw.isBlank()) return List.of();
    List<String> output = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean singleQuoted = false;
    boolean doubleQuoted = false;
    boolean escaping = false;
    for (int index = 0; index < raw.length(); index++) {
      char character = raw.charAt(index);
      if (escaping) {
        current.append(character);
        escaping = false;
      } else if (character == '\\' && !singleQuoted) {
        escaping = true;
      } else if (character == '\'' && !doubleQuoted) {
        singleQuoted = !singleQuoted;
      } else if (character == '"' && !singleQuoted) {
        doubleQuoted = !doubleQuoted;
      } else if (Character.isWhitespace(character) && !singleQuoted && !doubleQuoted) {
        if (current.length() > 0) {
          output.add(current.toString());
          current.setLength(0);
        }
      } else {
        current.append(character);
      }
    }
    if (escaping) current.append('\\');
    if (current.length() > 0) output.add(current.toString());
    return List.copyOf(output);
  }

  private static boolean validHeap(int value) {
    return value == 0 || value >= MIN_HEAP_MB && value <= MAX_HEAP_MB;
  }

  private static int parseHeap(String raw, int fallback) {
    try {
      int value = Integer.parseInt(raw == null ? "" : raw.trim());
      return validHeap(value) ? value : fallback;
    } catch (NumberFormatException ignored) {
      return fallback;
    }
  }

  private static boolean parseBoolean(String raw, boolean fallback) {
    if (raw == null || raw.isBlank()) return fallback;
    return switch (raw.trim().toLowerCase(Locale.ROOT)) {
      case "true", "1", "yes", "on" -> true;
      case "false", "0", "no", "off" -> false;
      default -> fallback;
    };
  }

  private static boolean isManagedArgument(String argument) {
    String normalized = argument == null ? "" : argument.trim();
    return normalized.startsWith("-Xms")
        || normalized.startsWith("-Xmx")
        || normalized.startsWith("-XX:InitialHeapSize=")
        || normalized.startsWith("-XX:MaxHeapSize=")
        || normalized.matches("-XX:[+-]Use[A-Za-z0-9]+GC")
        || normalized.startsWith("-XX:HeapDumpPath=")
        || normalized.equals("-XX:+HeapDumpOnOutOfMemoryError")
        || normalized.equals("-XX:-HeapDumpOnOutOfMemoryError")
        || normalized.equals("-XX:+ExitOnOutOfMemoryError")
        || normalized.equals("-XX:-ExitOnOutOfMemoryError")
        || normalized.equals("-XX:+UseStringDeduplication")
        || normalized.equals("-XX:-UseStringDeduplication");
  }

  private static boolean hasBalancedQuotes(String raw) {
    boolean singleQuoted = false;
    boolean doubleQuoted = false;
    boolean escaping = false;
    for (int index = 0; raw != null && index < raw.length(); index++) {
      char character = raw.charAt(index);
      if (escaping) {
        escaping = false;
      } else if (character == '\\' && !singleQuoted) {
        escaping = true;
      } else if (character == '\'' && !doubleQuoted) {
        singleQuoted = !singleQuoted;
      } else if (character == '"' && !singleQuoted) {
        doubleQuoted = !doubleQuoted;
      }
    }
    return !singleQuoted && !doubleQuoted;
  }
}
