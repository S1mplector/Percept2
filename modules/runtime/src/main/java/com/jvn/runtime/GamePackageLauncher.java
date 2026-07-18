package com.jvn.runtime;

import java.io.File;
import java.io.FileInputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import com.jvn.core.vn.VnEntryScriptResolver;

/**
 * Launches packaged JVN games by resolving the bundled game directory relative
 * to the packaged application layout and forwarding the correct arguments to
 * {@link JvnApp}.
 */
public final class GamePackageLauncher {
  private static final int SEARCH_DEPTH = 6;

  private GamePackageLauncher() {
  }

  public static void main(String[] args) {
    File gameRoot = resolvePackagedGameRoot();
    if (gameRoot == null) {
      throw new IllegalStateException(
          "Could not locate bundled game directory. Expected a nearby game/jvn.project next to the packaged application.");
    }
    List<String> runtimeArgs = buildRuntimeArgs(gameRoot, args);
    JvnApp.main(runtimeArgs.toArray(String[]::new));
  }

  static List<String> buildRuntimeArgs(File gameRoot, String[] userArgs) {
    List<String> args = new ArrayList<>();
    args.add("--assets");
    args.add(gameRoot.getAbsolutePath());

    if (!containsOption(userArgs, "--script") && !containsOption(userArgs, "--jes")) {
      Properties manifest = loadManifest(gameRoot);
      String type = manifest.getProperty("type", "vn").trim().toLowerCase(Locale.ROOT);
      if ("jes".equals(type)) {
        String entry = cleanPath(manifest.getProperty("entry", "scripts/main.jes"));
        if (entry != null) {
          args.add("--jes");
          args.add(entry);
        }
      } else {
        String script = VnEntryScriptResolver.resolveFromManifest(gameRoot);
        if (script != null) {
          args.add("--script");
          args.add(script);
        }
      }
    }

    if (userArgs != null) {
      for (String arg : userArgs) args.add(arg);
    }
    return args;
  }

  static File findPackagedGameRoot(List<File> roots) {
    LinkedHashSet<File> searchRoots = new LinkedHashSet<>();
    if (roots != null) {
      for (File root : roots) addRootAndAncestors(searchRoots, root);
    }

    for (File root : searchRoots) {
      File direct = requireManifest(root);
      if (direct != null) return direct;

      for (String relative : List.of(
          "game",
          "app/game",
          "content/game",
          "Contents/app/game",
          "Contents/content/game",
          "lib/app/game",
          "resources/game")) {
        File candidate = requireManifest(new File(root, relative));
        if (candidate != null) return candidate;
      }
    }
    return null;
  }

  static File resolvePackagedGameRoot() {
    String override = System.getProperty("jvn.packaged.gameRoot", "").trim();
    if (!override.isBlank()) {
      File explicit = requireManifest(new File(override));
      if (explicit != null) return explicit;
    }

    List<File> roots = new ArrayList<>();
    File codeSource = codeSourceRoot();
    if (codeSource != null) roots.add(codeSource);
    String userDir = System.getProperty("user.dir", "").trim();
    if (!userDir.isBlank()) roots.add(new File(userDir));
    return findPackagedGameRoot(roots);
  }

  private static File codeSourceRoot() {
    try {
      CodeSource codeSource = GamePackageLauncher.class.getProtectionDomain().getCodeSource();
      if (codeSource == null) return null;
      URL location = codeSource.getLocation();
      if (location == null) return null;
      File file = new File(location.toURI());
      return file.isFile() ? file.getParentFile() : file;
    } catch (URISyntaxException ex) {
      return null;
    }
  }

  private static void addRootAndAncestors(LinkedHashSet<File> out, File root) {
    File current = root;
    int depth = 0;
    while (current != null && depth++ < SEARCH_DEPTH) {
      out.add(current);
      current = current.getParentFile();
    }
  }

  private static File requireManifest(File candidate) {
    if (candidate == null || !candidate.isDirectory()) return null;
    return new File(candidate, "jvn.project").isFile() ? candidate : null;
  }

  private static Properties loadManifest(File root) {
    Properties props = new Properties();
    File manifest = new File(root, "jvn.project");
    if (!manifest.isFile()) return props;
    try (FileInputStream in = new FileInputStream(manifest)) {
      props.load(in);
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
    return props;
  }

  private static boolean containsOption(String[] args, String option) {
    if (args == null || option == null || option.isBlank()) return false;
    for (String arg : args) {
      if (option.equals(arg)) return true;
      if (arg != null && arg.startsWith(option + "=")) return true;
    }
    return false;
  }

  private static String cleanPath(String raw) {
    if (raw == null) return null;
    String value = raw.trim().replace('\\', '/');
    if (value.isBlank()) return null;
    while (value.startsWith("./")) value = value.substring(2);
    while (value.startsWith("/")) value = value.substring(1);
    return value.isBlank() ? null : value;
  }
}
