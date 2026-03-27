package com.jvn.fx;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class ProjectHotReloadTracker {
  private static final long DEFAULT_POLL_INTERVAL_MS = 400L;

  record ChangeSet(
      boolean scriptsChanged,
      boolean menuChanged,
      boolean uiChanged,
      boolean phoneChanged,
      boolean assetsChanged,
      boolean localizationChanged
  ) {
    boolean hasChanges() {
      return scriptsChanged || menuChanged || uiChanged || phoneChanged || assetsChanged || localizationChanged;
    }
  }

  private record Snapshot(long hash) {
  }

  private final File projectRoot;
  private final long pollIntervalNs;
  private long lastPollNs;
  private Snapshot scriptsSnapshot;
  private Snapshot menuSnapshot;
  private Snapshot uiSnapshot;
  private Snapshot phoneSnapshot;
  private Snapshot assetsSnapshot;
  private Snapshot localizationSnapshot;

  private ProjectHotReloadTracker(File projectRoot, long pollIntervalMs) {
    this.projectRoot = projectRoot;
    this.pollIntervalNs = Math.max(100L, pollIntervalMs) * 1_000_000L;
    this.lastPollNs = 0L;
    this.scriptsSnapshot = snapshot(paths(
        "jvn.project",
        "scripts",
        "game/scripts"
    ));
    this.menuSnapshot = snapshot(paths(
        "config/menu"
    ));
    this.uiSnapshot = snapshot(paths(
        "jvn.project",
        "config/ui/dialogue.layout",
        "config/vn/dialogue.layout",
        "dialogue.layout",
        "config/settings/vn.settings",
        "config/vn.settings",
        "vn.settings"
    ));
    this.phoneSnapshot = snapshot(paths(
        "config/phone/phone.properties",
        "game/config/phone/phone.properties"
    ));
    this.assetsSnapshot = snapshot(paths(
        "assets",
        "images",
        "audio",
        "fonts",
        "game/images",
        "game/audio",
        "game/fonts"
    ));
    this.localizationSnapshot = snapshot(paths(
        "jvn.project",
        "game/strings",
        "strings"
    ));
  }

  static ProjectHotReloadTracker create(File projectRoot) {
    if (projectRoot == null || !projectRoot.isDirectory()) return null;
    return new ProjectHotReloadTracker(projectRoot, DEFAULT_POLL_INTERVAL_MS);
  }

  ChangeSet poll(long nowNs) {
    if (nowNs > 0 && lastPollNs > 0 && nowNs - lastPollNs < pollIntervalNs) {
      return new ChangeSet(false, false, false, false, false, false);
    }
    lastPollNs = nowNs;

    Snapshot nextScripts = snapshot(paths("jvn.project", "scripts", "game/scripts"));
    Snapshot nextMenu = snapshot(paths("config/menu"));
    Snapshot nextUi = snapshot(paths(
        "jvn.project",
        "config/ui/dialogue.layout",
        "config/vn/dialogue.layout",
        "dialogue.layout",
        "config/settings/vn.settings",
        "config/vn.settings",
        "vn.settings"
    ));
    Snapshot nextPhone = snapshot(paths("config/phone/phone.properties", "game/config/phone/phone.properties"));
    Snapshot nextAssets = snapshot(paths("assets", "images", "audio", "fonts", "game/images", "game/audio", "game/fonts"));
    Snapshot nextLocalization = snapshot(paths("jvn.project", "game/strings", "strings"));

    boolean scriptsChanged = nextScripts.hash() != scriptsSnapshot.hash();
    boolean menuChanged = nextMenu.hash() != menuSnapshot.hash();
    boolean uiChanged = nextUi.hash() != uiSnapshot.hash();
    boolean phoneChanged = nextPhone.hash() != phoneSnapshot.hash();
    boolean assetsChanged = nextAssets.hash() != assetsSnapshot.hash();
    boolean localizationChanged = nextLocalization.hash() != localizationSnapshot.hash();

    scriptsSnapshot = nextScripts;
    menuSnapshot = nextMenu;
    uiSnapshot = nextUi;
    phoneSnapshot = nextPhone;
    assetsSnapshot = nextAssets;
    localizationSnapshot = nextLocalization;

    return new ChangeSet(
        scriptsChanged,
        menuChanged,
        uiChanged,
        phoneChanged,
        assetsChanged,
        localizationChanged
    );
  }

  private List<Path> paths(String... relatives) {
    List<Path> resolved = new ArrayList<>();
    if (projectRoot == null || relatives == null) return resolved;
    for (String relative : relatives) {
      if (relative == null || relative.isBlank()) continue;
      resolved.add(projectRoot.toPath().resolve(relative).normalize());
    }
    return resolved;
  }

  private static Snapshot snapshot(List<Path> paths) {
    List<Path> files = new ArrayList<>();
    if (paths != null) {
      for (Path path : paths) {
        collect(path, files);
      }
    }
    files.sort(Comparator.comparing(Path::toString));
    long hash = 1469598103934665603L;
    for (Path file : files) {
      hash ^= file.toString().hashCode();
      hash *= 1099511628211L;
      try {
        hash ^= Files.getLastModifiedTime(file).toMillis();
        hash *= 1099511628211L;
        if (Files.isRegularFile(file)) {
          hash ^= Files.size(file);
          hash *= 1099511628211L;
        }
      } catch (Exception ignored) {
      }
    }
    return new Snapshot(hash);
  }

  private static void collect(Path path, List<Path> files) {
    if (path == null || files == null || !Files.exists(path)) return;
    if (Files.isRegularFile(path)) {
      files.add(path);
      return;
    }
    if (!Files.isDirectory(path)) return;
    try (var stream = Files.walk(path)) {
      stream.filter(Files::isRegularFile).forEach(files::add);
    } catch (Exception ignored) {
    }
  }
}
