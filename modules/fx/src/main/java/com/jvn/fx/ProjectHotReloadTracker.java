package com.jvn.fx;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ProjectHotReloadTracker implements AutoCloseable {
  private static final long DEFAULT_POLL_INTERVAL_MS = 400L;
  private static final ChangeSet NO_CHANGES = new ChangeSet(false, false, false, false, false, false);
  private static final WatchEvent.Modifier[] WATCH_MODIFIERS = loadWatchModifiers();

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

  private enum Category {
    SCRIPTS,
    MENU,
    UI,
    PHONE,
    ASSETS,
    LOCALIZATION
  }

  private record WatchTarget(String relativePath, EnumSet<Category> categories) {
  }

  private static final List<WatchTarget> WATCH_TARGETS = List.of(
      target("jvn.project", Category.SCRIPTS, Category.UI, Category.LOCALIZATION),
      target("scripts", Category.SCRIPTS),
      target("game/scripts", Category.SCRIPTS),
      target("config/menu", Category.MENU),
      target("config/ui/dialogue.layout", Category.UI),
      target("config/vn/dialogue.layout", Category.UI),
      target("dialogue.layout", Category.UI),
      target("config/settings/vn.settings", Category.UI),
      target("config/vn.settings", Category.UI),
      target("vn.settings", Category.UI),
      target("config/phone/phone.properties", Category.PHONE),
      target("game/config/phone/phone.properties", Category.PHONE),
      target("assets", Category.ASSETS),
      target("images", Category.ASSETS),
      target("audio", Category.ASSETS),
      target("fonts", Category.ASSETS),
      target("game/images", Category.ASSETS),
      target("game/audio", Category.ASSETS),
      target("game/fonts", Category.ASSETS),
      target("game/strings", Category.LOCALIZATION),
      target("strings", Category.LOCALIZATION)
  );

  private final File projectRoot;
  private final Path projectRootPath;
  private final long pollIntervalNs;
  private final WatchService watchService;
  private final Map<WatchKey, Path> watchDirectories = new HashMap<>();
  private final Set<Path> registeredDirectories = new HashSet<>();
  private final EnumSet<Category> dirtyCategories = EnumSet.noneOf(Category.class);
  private long lastPollNs;
  private Snapshot scriptsSnapshot;
  private Snapshot menuSnapshot;
  private Snapshot uiSnapshot;
  private Snapshot phoneSnapshot;
  private Snapshot assetsSnapshot;
  private Snapshot localizationSnapshot;

  private ProjectHotReloadTracker(File projectRoot, long pollIntervalMs) {
    this.projectRoot = projectRoot;
    this.projectRootPath = projectRoot == null ? null : projectRoot.toPath().toAbsolutePath().normalize();
    this.pollIntervalNs = Math.max(100L, pollIntervalMs) * 1_000_000L;
    this.lastPollNs = 0L;
    this.watchService = initializeWatchService();
    if (watchService == null) {
      initializeSnapshots();
    }
  }

  static ProjectHotReloadTracker create(File projectRoot) {
    if (projectRoot == null || !projectRoot.isDirectory()) return null;
    return new ProjectHotReloadTracker(projectRoot, DEFAULT_POLL_INTERVAL_MS);
  }

  ChangeSet poll(long nowNs) {
    if (watchService != null) {
      return pollWatchService(nowNs);
    }
    return pollSnapshots(nowNs);
  }

  @Override
  public void close() {
    if (watchService == null) return;
    watchDirectories.clear();
    registeredDirectories.clear();
    dirtyCategories.clear();
    closeQuietly(watchService);
  }

  private ChangeSet pollWatchService(long nowNs) {
    if (nowNs > 0 && lastPollNs > 0 && nowNs - lastPollNs < pollIntervalNs) {
      return NO_CHANGES;
    }
    lastPollNs = nowNs;
    drainWatchEvents();
    if (dirtyCategories.isEmpty()) {
      return NO_CHANGES;
    }
    ChangeSet changes = new ChangeSet(
        dirtyCategories.contains(Category.SCRIPTS),
        dirtyCategories.contains(Category.MENU),
        dirtyCategories.contains(Category.UI),
        dirtyCategories.contains(Category.PHONE),
        dirtyCategories.contains(Category.ASSETS),
        dirtyCategories.contains(Category.LOCALIZATION)
    );
    dirtyCategories.clear();
    return changes;
  }

  private ChangeSet pollSnapshots(long nowNs) {
    if (nowNs > 0 && lastPollNs > 0 && nowNs - lastPollNs < pollIntervalNs) {
      return NO_CHANGES;
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

  private WatchService initializeWatchService() {
    if (projectRootPath == null) return null;
    WatchService watcher = null;
    try {
      watcher = FileSystems.getDefault().newWatchService();
      registerRecursively(watcher, projectRootPath);
      return watcher;
    } catch (Exception ignored) {
      closeQuietly(watcher);
      return null;
    }
  }

  private void initializeSnapshots() {
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

  private List<Path> paths(String... relatives) {
    List<Path> resolved = new ArrayList<>();
    if (projectRootPath == null || relatives == null) return resolved;
    for (String relative : relatives) {
      if (relative == null || relative.isBlank()) continue;
      resolved.add(projectRootPath.resolve(relative).normalize());
    }
    return resolved;
  }

  private void drainWatchEvents() {
    if (watchService == null) return;
    boolean requiresResync = false;
    WatchKey key;
    while ((key = watchService.poll()) != null) {
      Path watchedDirectory = watchDirectories.get(key);
      if (watchedDirectory == null) {
        requiresResync = true;
      }
      for (WatchEvent<?> event : key.pollEvents()) {
        WatchEvent.Kind<?> kind = event.kind();
        if (kind == StandardWatchEventKinds.OVERFLOW) {
          markAllDirty();
          requiresResync = true;
          continue;
        }
        if (watchedDirectory == null) {
          requiresResync = true;
          continue;
        }
        Path changedPath = watchedDirectory.resolve(cast(event).context()).normalize();
        markDirty(changedPath);
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
          registerRecursivelyIfDirectory(changedPath);
        }
      }
      if (!key.reset()) {
        Path removed = watchDirectories.remove(key);
        if (removed != null) {
          registeredDirectories.remove(removed);
        }
        requiresResync = true;
      }
    }
    if (requiresResync) {
      resyncWatches();
    }
  }

  private void resyncWatches() {
    if (watchService == null || projectRootPath == null || !Files.isDirectory(projectRootPath)) return;
    for (WatchKey key : new ArrayList<>(watchDirectories.keySet())) {
      key.cancel();
    }
    watchDirectories.clear();
    registeredDirectories.clear();
    try {
      registerRecursively(watchService, projectRootPath);
    } catch (Exception ignored) {
    }
  }

  private void registerRecursivelyIfDirectory(Path path) {
    if (watchService == null || path == null || !Files.isDirectory(path)) return;
    try {
      registerRecursively(watchService, path);
    } catch (Exception ignored) {
      markAllDirty();
    }
  }

  private void registerRecursively(WatchService watcher, Path root) throws IOException {
    if (watcher == null || root == null || !Files.isDirectory(root)) return;
    Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<>() {
      @Override
      public java.nio.file.FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        registerDirectory(watcher, dir);
        return java.nio.file.FileVisitResult.CONTINUE;
      }
    });
  }

  private void registerDirectory(WatchService watcher, Path directory) throws IOException {
    if (watcher == null || directory == null || !Files.isDirectory(directory) || !registeredDirectories.add(directory)) {
      return;
    }
    WatchKey key = WATCH_MODIFIERS.length == 0
        ? directory.register(
            watcher,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY
        )
        : directory.register(
            watcher,
            new WatchEvent.Kind<?>[] {
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
            },
            WATCH_MODIFIERS
        );
    watchDirectories.put(key, directory);
  }

  @SuppressWarnings("unchecked")
  private static WatchEvent<Path> cast(WatchEvent<?> event) {
    return (WatchEvent<Path>) event;
  }

  private void markDirty(Path absolutePath) {
    if (absolutePath == null || projectRootPath == null) return;
    Path normalizedAbsolute = absolutePath.toAbsolutePath().normalize();
    if (!normalizedAbsolute.startsWith(projectRootPath)) return;
    String relativePath = normalize(projectRootPath.relativize(normalizedAbsolute));
    if (relativePath.isEmpty()) return;
    for (WatchTarget target : WATCH_TARGETS) {
      if (isRelatedPath(relativePath, target.relativePath())) {
        dirtyCategories.addAll(target.categories());
      }
    }
  }

  private void markAllDirty() {
    dirtyCategories.addAll(EnumSet.allOf(Category.class));
  }

  private static boolean isRelatedPath(String changedPath, String watchedPath) {
    return changedPath.equals(watchedPath)
        || changedPath.startsWith(watchedPath + "/")
        || watchedPath.startsWith(changedPath + "/");
  }

  private static WatchTarget target(String relativePath, Category... categories) {
    EnumSet<Category> categorySet = EnumSet.noneOf(Category.class);
    for (Category category : categories) {
      categorySet.add(category);
    }
    return new WatchTarget(normalize(relativePath), categorySet);
  }

  private static String normalize(Path path) {
    return normalize(path == null ? null : path.toString());
  }

  private static String normalize(String path) {
    if (path == null || path.isBlank()) return "";
    String normalized = path.replace('\\', '/');
    while (normalized.startsWith("./")) {
      normalized = normalized.substring(2);
    }
    while (normalized.endsWith("/")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  private static void closeQuietly(WatchService watcher) {
    if (watcher == null) return;
    try {
      watcher.close();
    } catch (IOException ignored) {
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static WatchEvent.Modifier[] loadWatchModifiers() {
    try {
      Class<? extends Enum> modifierClass = Class.forName("com.sun.nio.file.SensitivityWatchEventModifier")
          .asSubclass(Enum.class);
      WatchEvent.Modifier high = (WatchEvent.Modifier) Enum.valueOf(modifierClass, "HIGH");
      return new WatchEvent.Modifier[] { high };
    } catch (Exception ignored) {
      return new WatchEvent.Modifier[0];
    }
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
