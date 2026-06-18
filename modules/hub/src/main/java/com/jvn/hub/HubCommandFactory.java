package com.jvn.hub;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Builds process commands for Engine Hub views. */
final class HubCommandFactory {
  private HubCommandFactory() {
  }

  static List<String> gradleTask(Path projectRoot, String task, HubGradleOptions options) {
    List<String> cmd = new ArrayList<>();
    cmd.add(gradleCommand(projectRoot));
    cmd.add("--console=plain");
    if (options.developerMode()) {
      cmd.add("-Djvn.hub.developerMode=true");
      cmd.add("-Djvn.editor.developerMode=true");
      cmd.add("-Djvn.launcher.developerMode=true");
      cmd.add("-Djvn.help.developerMode=true");
      cmd.addAll(developerGradleOptions(options));
    }
    if (options.safeMode()) {
      cmd.add("-Djvn.hub.safeMode=true");
      cmd.add("-Djvn.editor.safeMode=true");
      cmd.add("-Djvn.launcher.safeMode=true");
      cmd.add("-Djvn.help.safeMode=true");
    }
    if (shouldPreferConfigurationCache(task, options)) {
      cmd.add("--configuration-cache");
    }
    if (shouldLimitLaunchWorkers(task, options)) {
      cmd.add("--max-workers=" + balancedLaunchWorkerCount());
    }
    cmd.add(task);
    return cmd;
  }

  static List<String> updateEngine(boolean safeMode) {
    return safeMode
        ? List.of("git", "pull", "--rebase", "--autostash", HubUpdateTarget.REMOTE, HubUpdateTarget.BRANCH)
        : List.of("git", "pull", "--rebase", HubUpdateTarget.REMOTE, HubUpdateTarget.BRANCH);
  }

  static List<String> fetchStable() {
    return List.of("git", "fetch", "--quiet", "--prune", "--no-tags",
        HubUpdateTarget.REMOTE, HubUpdateTarget.FETCH_REFSPEC);
  }

  static List<String> incomingCount() {
    return List.of("git", "rev-list", "--count", "HEAD.." + HubUpdateTarget.REMOTE_REF);
  }

  static HubShortcutCommand shortcutInstaller(Path projectRoot) {
    Path root = projectRoot == null ? Path.of(".") : projectRoot;
    String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
    Path script;
    List<String> command;
    if (os.contains("win")) {
      script = root.resolve("install-windows-launcher.ps1");
      command = List.of(
          windowsPowerShellCommand(),
          "-NoProfile",
          "-ExecutionPolicy",
          "Bypass",
          "-File",
          script.toAbsolutePath().toString());
    } else if (os.contains("mac") || os.contains("darwin")) {
      script = root.resolve("install-macos-launcher.sh");
      command = List.of("bash", script.toAbsolutePath().toString());
    } else {
      script = root.resolve("install-linux-launcher.sh");
      command = List.of("bash", script.toAbsolutePath().toString());
    }
    return new HubShortcutCommand(script, command);
  }

  static List<String> developerGradleOptions(HubGradleOptions options) {
    List<String> out = new ArrayList<>();
    if (options.stacktrace()) out.add("--stacktrace");
    if (options.debugLogging()) {
      out.add("--debug");
    } else if (options.infoLogging()) {
      out.add("--info");
    }
    if (options.offline()) {
      out.add("--offline");
    } else if (options.refreshDependencies()) {
      out.add("--refresh-dependencies");
    }
    if (options.noBuildCache()) out.add("--no-build-cache");
    if (options.noDaemon()) out.add("--no-daemon");
    out.addAll(options.splitExtraArgs());
    return out;
  }

  static String gradleCommand(Path projectRoot) {
    String wrapper = isWindows() ? "gradlew.bat" : "./gradlew";
    Path root = projectRoot == null ? Path.of(".") : projectRoot;
    Path direct = root.resolve(isWindows() ? "gradlew.bat" : "gradlew");
    if (direct.toFile().exists()) return wrapper;
    return wrapper;
  }

  static boolean shouldPreferConfigurationCache(String task, HubGradleOptions options) {
    if (options.safeMode()) return false;
    if (hasConfigurationCacheFlag(options)) return false;
    return switch (task) {
      case ":editor:run", ":editor:runLauncher", ":editor:runHelpCenter", ":runtime:run",
          "build", "test", "check", "ci", "compileAll", "quickCheck" -> true;
      default -> false;
    };
  }

  static boolean shouldLimitLaunchWorkers(String task, HubGradleOptions options) {
    if (hasMaxWorkersFlag(options)) return false;
    return switch (task) {
      case ":editor:run", ":editor:runLauncher", ":editor:runHelpCenter", ":runtime:run" -> true;
      default -> false;
    };
  }

  static int balancedLaunchWorkerCount() {
    int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
    return Math.max(2, processors <= 4 ? 2 : processors - 2);
  }

  static boolean hasConfigurationCacheFlag(HubGradleOptions options) {
    for (String arg : options.splitExtraArgs()) {
      if (arg.equals("--configuration-cache")
          || arg.startsWith("--configuration-cache=")
          || arg.equals("--no-configuration-cache")
          || arg.equals("-Dorg.gradle.configuration-cache")
          || arg.startsWith("-Dorg.gradle.configuration-cache=")) {
        return true;
      }
    }
    return false;
  }

  static boolean hasMaxWorkersFlag(HubGradleOptions options) {
    List<String> args = options.splitExtraArgs();
    for (int i = 0; i < args.size(); i++) {
      String arg = args.get(i);
      if (arg.equals("--max-workers") || arg.startsWith("--max-workers=")) {
        return true;
      }
    }
    return false;
  }

  static List<String> splitArgs(String raw) {
    if (raw == null || raw.isBlank()) return List.of();
    List<String> out = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean single = false;
    boolean dbl = false;
    boolean escaping = false;
    for (int i = 0; i < raw.length(); i++) {
      char ch = raw.charAt(i);
      if (escaping) {
        current.append(ch);
        escaping = false;
      } else if (ch == '\\') {
        escaping = true;
      } else if (ch == '\'' && !dbl) {
        single = !single;
      } else if (ch == '"' && !single) {
        dbl = !dbl;
      } else if (Character.isWhitespace(ch) && !single && !dbl) {
        if (current.length() > 0) {
          out.add(current.toString());
          current.setLength(0);
        }
      } else {
        current.append(ch);
      }
    }
    if (escaping) current.append('\\');
    if (current.length() > 0) out.add(current.toString());
    return List.copyOf(out);
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
  }

  private static String windowsPowerShellCommand() {
    if (commandExists("pwsh")) return "pwsh";
    for (String envName : List.of("SystemRoot", "WINDIR")) {
      String root = System.getenv(envName);
      if (root == null || root.isBlank()) continue;
      Path candidate = Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
      if (candidate.toFile().isFile()) return candidate.toAbsolutePath().toString();
    }
    return "powershell.exe";
  }

  private static boolean commandExists(String command) {
    if (command == null || command.isBlank()) return false;
    String path = System.getenv("PATH");
    if (path == null || path.isBlank()) return false;
    String[] exts = isWindows() ? new String[]{"", ".exe", ".bat", ".cmd"} : new String[]{""};
    for (String entry : path.split(java.io.File.pathSeparator)) {
      if (entry == null || entry.isBlank()) continue;
      for (String ext : exts) {
        if (Path.of(entry, command + ext).toFile().isFile()) return true;
      }
    }
    return false;
  }
}
