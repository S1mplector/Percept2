package com.jvn.editor.ui;

import java.awt.Desktop;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;

import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Window;

public final class DeveloperToolsMenu {
  private static final String KEY_EDITOR_MAX_HEAP_MB = "editor.jvm.maxHeapMb";
  private static final String KEY_CAPTURE_EDITOR_OUTPUT = "launcher.captureEditorOutput";
  private static final String KEY_AUTO_WRITE_EDITOR_DIAGNOSTICS = "editor.autoWriteDiagnostics";
  private static final int MIN_HEAP_MB = 256;
  private static final int MAX_HEAP_MB = 65536;

  private DeveloperToolsMenu() {}

  public static Menu create(String appName,
                            Supplier<Window> ownerSupplier,
                            Runnable refreshLogs) {
    return create(appName, ownerSupplier, refreshLogs, List::of);
  }

  public static Menu create(String appName,
                            Supplier<Window> ownerSupplier,
                            Runnable refreshLogs,
                            Supplier<List<Path>> contextRootsSupplier) {
    return create(appName, ownerSupplier, refreshLogs, contextRootsSupplier, false);
  }

  public static Menu create(String appName,
                            Supplier<Window> ownerSupplier,
                            Runnable refreshLogs,
                            Supplier<List<Path>> contextRootsSupplier,
                            boolean includeEditorDiagnosticHeartbeat) {
    Menu menu = new Menu("DevTools");

    MenuItem miRuntimeInfo = new MenuItem("Show Runtime Info");
    miRuntimeInfo.setOnAction(e -> showRuntimeInfo(owner(ownerSupplier), appName));

    MenuItem miCopyRuntimeInfo = new MenuItem("Copy Runtime Info");
    miCopyRuntimeInfo.setOnAction(e -> copyRuntimeInfo(appName));

    MenuItem miGc = new MenuItem("Run GC");
    miGc.setOnAction(e -> {
      System.gc();
      EditorDialogs.info(owner(ownerSupplier), "DevTools", "Requested a JVM garbage collection pass.");
    });

    MenuItem miRefreshLogs = new MenuItem("Refresh Logs Panel");
    miRefreshLogs.setOnAction(e -> {
      if (refreshLogs != null) refreshLogs.run();
    });
    miRefreshLogs.setDisable(refreshLogs == null);

    MenuItem miSaveLogs = new MenuItem("Save Diagnostics Bundle...");
    miSaveLogs.setOnAction(e -> DeveloperDiagnosticsExporter.chooseAndExport(
        owner(ownerSupplier),
        appName,
        contextRootsSupplier));

    MenuItem miHeap = new MenuItem("Editor JVM Memory...");
    miHeap.setOnAction(e -> configureEditorHeap(owner(ownerSupplier)));

    CheckMenuItem miCaptureEditorOutput = new CheckMenuItem("Capture Editor Process Output");
    miCaptureEditorOutput.setSelected(isCaptureEditorProcessOutputEnabled());
    miCaptureEditorOutput.setOnAction(e -> {
      setCaptureEditorProcessOutputEnabled(miCaptureEditorOutput.isSelected());
      EditorDialogs.info(
          owner(ownerSupplier),
          "DevTools",
          "Editor process output capture is "
              + (miCaptureEditorOutput.isSelected() ? "enabled." : "disabled."));
    });

    CheckMenuItem miAutoWriteDiagnostics = new CheckMenuItem("Auto-write Editor Diagnostics");
    miAutoWriteDiagnostics.setSelected(isAutoWriteEditorDiagnosticsEnabled());
    miAutoWriteDiagnostics.setOnAction(e -> {
      setAutoWriteEditorDiagnosticsEnabled(miAutoWriteDiagnostics.isSelected());
      EditorDialogs.info(
          owner(ownerSupplier),
          "DevTools",
          "Editor diagnostics heartbeat auto-write is "
              + (miAutoWriteDiagnostics.isSelected() ? "enabled." : "disabled."));
    });

    MenuItem miOpenSettings = new MenuItem("Open DevTools Settings File");
    miOpenSettings.setOnAction(e -> openSettingsFile(owner(ownerSupplier)));

    List<MenuItem> items = new ArrayList<>(List.of(
        miRuntimeInfo,
        miCopyRuntimeInfo,
        miGc,
        new SeparatorMenuItem(),
        miRefreshLogs,
        miSaveLogs,
        new SeparatorMenuItem(),
        miHeap,
        miCaptureEditorOutput));
    if (includeEditorDiagnosticHeartbeat) {
      items.add(miAutoWriteDiagnostics);
    }
    items.add(miOpenSettings);
    menu.getItems().addAll(items);
    menu.setOnShowing(e -> {
      miCaptureEditorOutput.setSelected(isCaptureEditorProcessOutputEnabled());
      if (includeEditorDiagnosticHeartbeat) {
        miAutoWriteDiagnostics.setSelected(isAutoWriteEditorDiagnosticsEnabled());
      }
    });
    return menu;
  }

  public static List<String> configuredEditorJvmArgs() {
    Optional<Integer> heapMb = configuredEditorHeapMb();
    if (heapMb.isEmpty()) return List.of();
    return List.of("-Xmx" + heapMb.get() + "m");
  }

  public static boolean isCaptureEditorProcessOutputEnabled() {
    return Boolean.parseBoolean(settings().getProperty(KEY_CAPTURE_EDITOR_OUTPUT, "true"));
  }

  public static boolean isAutoWriteEditorDiagnosticsEnabled() {
    return Boolean.parseBoolean(settings().getProperty(KEY_AUTO_WRITE_EDITOR_DIAGNOSTICS, "false"));
  }

  private static Optional<Integer> configuredEditorHeapMb() {
    String raw = settings().getProperty(KEY_EDITOR_MAX_HEAP_MB, "").trim();
    if (raw.isBlank()) return Optional.empty();
    try {
      int value = Integer.parseInt(raw);
      if (value < MIN_HEAP_MB || value > MAX_HEAP_MB) return Optional.empty();
      return Optional.of(value);
    } catch (NumberFormatException ignored) {
      return Optional.empty();
    }
  }

  private static void configureEditorHeap(Window owner) {
    String current = configuredEditorHeapMb().map(String::valueOf).orElse("");
    Optional<String> value = EditorDialogs.promptText(
        owner,
        "Editor JVM Memory",
        "Set the editor max heap for editor launches started from the launcher. Leave blank to use the JVM default. This applies on the next editor launch.",
        "Max heap in MB",
        current,
        "2048",
        "Save");
    if (value.isEmpty()) return;

    String raw = value.get() == null ? "" : value.get().trim();
    Properties props = settings();
    if (raw.isBlank()) {
      props.remove(KEY_EDITOR_MAX_HEAP_MB);
      saveSettings(props);
      EditorDialogs.info(owner, "Editor JVM Memory", "Editor launches will use the JVM default heap size.");
      return;
    }
    try {
      int mb = Integer.parseInt(raw);
      if (mb < MIN_HEAP_MB || mb > MAX_HEAP_MB) {
        EditorDialogs.warning(owner, "Editor JVM Memory", "Use a value between " + MIN_HEAP_MB + " and " + MAX_HEAP_MB + " MB.");
        return;
      }
      props.setProperty(KEY_EDITOR_MAX_HEAP_MB, Integer.toString(mb));
      saveSettings(props);
      EditorDialogs.info(owner, "Editor JVM Memory", "Editor launches will use -Xmx" + mb + "m on the next launch.");
    } catch (NumberFormatException ex) {
      EditorDialogs.warning(owner, "Editor JVM Memory", "Enter a whole number of megabytes, or leave the field blank.");
    }
  }

  private static void setCaptureEditorProcessOutputEnabled(boolean enabled) {
    Properties props = settings();
    props.setProperty(KEY_CAPTURE_EDITOR_OUTPUT, Boolean.toString(enabled));
    saveSettings(props);
  }

  private static void setAutoWriteEditorDiagnosticsEnabled(boolean enabled) {
    Properties props = settings();
    props.setProperty(KEY_AUTO_WRITE_EDITOR_DIAGNOSTICS, Boolean.toString(enabled));
    saveSettings(props);
  }

  private static void showRuntimeInfo(Window owner, String appName) {
    EditorDialogs.showTextBlock(owner, "DevTools Runtime Info", appName + " JVM/runtime details.", runtimeInfo(appName), "Close");
  }

  private static void copyRuntimeInfo(String appName) {
    ClipboardContent content = new ClipboardContent();
    content.putString(runtimeInfo(appName));
    Clipboard.getSystemClipboard().setContent(content);
  }

  private static String runtimeInfo(String appName) {
    Runtime runtime = Runtime.getRuntime();
    MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    MemoryUsage heap = memoryBean.getHeapMemoryUsage();
    MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
    List<String> lines = new ArrayList<>();
    lines.add("App: " + (appName == null || appName.isBlank() ? "JVN" : appName));
    lines.add("Java: " + System.getProperty("java.version", "unknown"));
    lines.add("Java home: " + System.getProperty("java.home", "unknown"));
    lines.add("OS: " + System.getProperty("os.name", "unknown") + " " + System.getProperty("os.version", ""));
    lines.add("User dir: " + System.getProperty("user.dir", ""));
    lines.add("Developer mode editor: " + System.getProperty("jvn.editor.developerMode", "false"));
    lines.add("Developer mode launcher: " + System.getProperty("jvn.launcher.developerMode", "false"));
    lines.add("Heap used/max: " + mb(heap.getUsed()) + " / " + mb(heap.getMax()));
    lines.add("Non-heap used: " + mb(nonHeap.getUsed()));
    lines.add("Runtime max/free: " + mb(runtime.maxMemory()) + " / " + mb(runtime.freeMemory()));
    lines.add("Configured editor heap: " + configuredEditorHeapMb().map(v -> v + " MB").orElse("JVM default"));
    lines.add("Capture editor output: " + isCaptureEditorProcessOutputEnabled());
    lines.add("Auto-write editor diagnostics: " + isAutoWriteEditorDiagnosticsEnabled());
    lines.add("Input args: " + ManagementFactory.getRuntimeMXBean().getInputArguments());
    lines.add("DevTools settings: " + settingsFile().toAbsolutePath());
    return String.join("\n", lines);
  }

  private static String mb(long bytes) {
    if (bytes < 0L) return "unknown";
    return (bytes / (1024L * 1024L)) + " MB";
  }

  private static void openSettingsFile(Window owner) {
    try {
      Path file = settingsFile();
      ensureSettingsFile(file);
      Desktop.getDesktop().open(file.toFile());
    } catch (Exception ex) {
      EditorDialogs.error(
          owner,
          "DevTools Settings",
          "Could not open the DevTools settings file.",
          ex,
          "Open it manually at: " + settingsFile().toAbsolutePath());
    }
  }

  private static Properties settings() {
    Properties props = new Properties();
    Path file = settingsFile();
    if (!Files.isRegularFile(file)) return props;
    try (var in = Files.newInputStream(file)) {
      props.load(in);
    } catch (IOException ignored) {
    }
    return props;
  }

  private static void saveSettings(Properties props) {
    Path file = settingsFile();
    try {
      ensureSettingsFile(file);
      try (var out = Files.newOutputStream(file)) {
        props.store(out, "JVN Developer Tools");
      }
    } catch (IOException ignored) {
    }
  }

  private static void ensureSettingsFile(Path file) throws IOException {
    Path parent = file.getParent();
    if (parent != null) Files.createDirectories(parent);
    if (!Files.exists(file)) {
      Files.writeString(file, "# JVN Developer Tools\n");
    }
  }

  private static Path settingsFile() {
    return Path.of(System.getProperty("user.home", "."), ".jvn-editor", "devtools.properties");
  }

  private static Window owner(Supplier<Window> ownerSupplier) {
    return ownerSupplier == null ? null : ownerSupplier.get();
  }
}
