package com.jvn.core.project;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jvn.core.animation.TimelineDataParser;
import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.ClasspathAssetManager;
import com.jvn.core.assets.FilesystemAssetManager;
import com.jvn.core.assets.OverlayAssetManager;
import com.jvn.core.menu.config.MenuActionSpec;
import com.jvn.core.menu.config.MenuActionType;
import com.jvn.core.menu.config.MenuItemSpec;
import com.jvn.core.menu.config.MenuProfile;
import com.jvn.core.menu.config.MenuProfileLoader;
import com.jvn.core.menu.config.MenuScreenSpec;
import com.jvn.core.vn.VnArgTokenizer;
import com.jvn.core.vn.script.VnScriptParser;
import com.jvn.core.vn.stage.VnStagePresetLoader;

/**
 * Performs a project-wide dependency scan for shipping-time validation.
 */
public final class ProjectDependencyValidator {
  public enum Severity {
    ERROR,
    WARNING,
    INFO
  }

  public record Finding(Severity severity, String category, String location, String message, String target) {
    public Finding {
      severity = severity == null ? Severity.WARNING : severity;
      category = normalize(category, "project");
      location = normalize(location, "project");
      message = normalize(message, "");
      target = normalize(target, null);
    }
  }

  public record AssetReference(String path, String kind, String location) {
    public AssetReference {
      path = normalizePath(path);
      kind = normalize(kind, "asset");
      location = normalize(location, "project");
    }
  }

  public record Report(
      Path projectRoot,
      List<Finding> findings,
      Set<String> referencedAssets,
      Set<String> knownAssets
  ) {
    public Report {
      projectRoot = projectRoot == null ? Path.of(".").toAbsolutePath().normalize()
          : projectRoot.toAbsolutePath().normalize();
      findings = findings == null ? List.of() : List.copyOf(findings);
      referencedAssets = referencedAssets == null ? Set.of()
          : Set.copyOf(referencedAssets);
      knownAssets = knownAssets == null ? Set.of()
          : Set.copyOf(knownAssets);
    }

    public int errorCount() {
      return count(Severity.ERROR);
    }

    public int warningCount() {
      return count(Severity.WARNING);
    }

    public int infoCount() {
      return count(Severity.INFO);
    }

    public boolean hasBlockingIssues() {
      return errorCount() > 0;
    }

    public List<Finding> bySeverity(Severity severity) {
      if (severity == null) return List.of();
      return findings.stream().filter(f -> f.severity() == severity).toList();
    }

    private int count(Severity severity) {
      return (int) findings.stream().filter(f -> f.severity() == severity).count();
    }
  }

  private enum RefKind {
    IMAGE("image"),
    AUDIO("audio"),
    FONT("font"),
    VIDEO("video"),
    SCRIPT("script"),
    CONFIG("config"),
    ASSET("asset");

    private final String label;

    RefKind(String label) {
      this.label = label;
    }
  }

  private record NamedReference(String name, String location) {}

  private record ResolvedReference(String requested, String resolved, boolean caseMismatch) {}

  private static final Set<String> IMAGE_EXTENSIONS = Set.of(
      "png", "jpg", "jpeg", "webp", "gif", "bmp", "svg");
  private static final Set<String> AUDIO_EXTENSIONS = Set.of(
      "wav", "ogg", "mp3", "flac", "aac", "m4a", "opus");
  private static final Set<String> FONT_EXTENSIONS = Set.of(
      "ttf", "otf", "ttc", "woff", "woff2");
  private static final Set<String> VIDEO_EXTENSIONS = Set.of(
      "mp4", "webm", "mov", "mkv");
  private static final Set<String> SCRIPT_EXTENSIONS = Set.of("vns", "jes");
  private static final Set<String> CONFIG_EXTENSIONS = Set.of(
      "properties", "menu", "layout", "style", "buttonlayout", "screen",
      "stagepreset", "registry", "settings", "project", "theme");
  private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
      ".git", ".gradle", ".idea", ".vscode", ".jvn-gradle-user-home",
      ".codex", "build", "out", "target", "dist", "save", "saves", "logs",
      "node_modules", "__macosx");

  private static final Pattern INCLUDE_PATTERN =
      Pattern.compile("^@include\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern BACKGROUND_PATTERN =
      Pattern.compile("^@background\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARIMG_PATTERN =
      Pattern.compile("^@charimg\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARLAYER_PATTERN =
      Pattern.compile("^@charlayer\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern CHARPRESET_PATTERN =
      Pattern.compile("^@charpreset\\s+(\\S+)\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern STAGE_PRESET_PATTERN =
      Pattern.compile("^@stagepreset\\s+(\\S+)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
  private static final Pattern EXTERNAL_PATTERN =
      Pattern.compile("^@external\\s+(\\S+)(?:\\s+(.+))?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern COMMAND_PATTERN =
      Pattern.compile("^\\[(.+)]$");
  private static final Pattern TIMELINE_AUDIO_PATTERN =
      Pattern.compile("playAudio\\s+\"([^\"]+)\"\\s*\\{", Pattern.CASE_INSENSITIVE);
  private static final Pattern QUOTED_VALUE_PATTERN =
      Pattern.compile("\"([^\"]+)\"");
  private static final Pattern PROPERTY_LINE_PATTERN =
      Pattern.compile("^\\s*([^#!:=\\s][^:=]*)\\s*[:=]\\s*(.*?)\\s*$");
  private ProjectDependencyValidator() {
  }

  public static Report inspect(File projectRoot) {
    return inspect(projectRoot == null ? null : projectRoot.toPath());
  }

  public static Report inspect(Path projectRoot) {
    Path root = projectRoot == null ? Path.of(".") : projectRoot;
    root = root.toAbsolutePath().normalize();
    ValidationContext ctx = new ValidationContext(root);

    if (!Files.isDirectory(root)) {
      ctx.add(Severity.ERROR, "packaging", "project",
          "Project root is missing or is not a directory", root.toString());
      return ctx.report();
    }

    indexProject(ctx);
    inspectPackaging(ctx);
    inspectRuntimeHealth(ctx);
    inspectMenus(ctx);
    inspectConfigFiles(ctx);
    inspectScripts(ctx);
    inspectTimelineFiles(ctx);
    inspectPendingReferences(ctx);
    inspectUnusedAssets(ctx);
    return ctx.report();
  }

  private static void indexProject(ValidationContext ctx) {
    try {
      Files.walkFileTree(ctx.root, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
          if (dir.equals(ctx.root)) return FileVisitResult.CONTINUE;
          String name = dir.getFileName() == null ? "" : dir.getFileName().toString().toLowerCase(Locale.ROOT);
          if (SKIPPED_DIRECTORIES.contains(name)) return FileVisitResult.SKIP_SUBTREE;
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
          if (!attrs.isRegularFile()) return FileVisitResult.CONTINUE;
          String rel = ctx.rel(file);
          ctx.files.add(file);
          ctx.filesByRel.put(rel, file);
          ctx.filesByLowerRel.put(rel.toLowerCase(Locale.ROOT), rel);
          if (isKnownMediaAsset(rel)) ctx.knownAssets.add(rel);
          String ext = extension(rel);
          if ("jes".equals(ext) && isTimelineFile(rel)) {
            ctx.timelineIds.add(stripExtension(Path.of(rel).getFileName().toString()));
          }
          return FileVisitResult.CONTINUE;
        }
      });
    } catch (IOException ex) {
      ctx.add(Severity.ERROR, "project", "project",
          "Could not index project files: " + ex.getMessage(), null);
    }
    ctx.files.sort(Comparator.comparing(ctx::rel));
  }

  private static void inspectPackaging(ValidationContext ctx) {
    boolean looksLikeEngine = ctx.filesByRel.containsKey("settings.gradle.kts")
        && ctx.filesByRel.containsKey("modules/core/build.gradle.kts")
        && ctx.filesByRel.containsKey("modules/editor/build.gradle.kts");
    if (looksLikeEngine) {
      ctx.add(Severity.ERROR, "packaging", "project",
          "This looks like the JVN engine workspace, not a game project; pass a game directory instead",
          ctx.root.toString());
    }

    Properties manifest = loadProperties(ctx.root.resolve("jvn.project")).orElse(null);
    if (manifest == null) {
      ctx.add(Severity.ERROR, "packaging", "jvn.project",
          "Missing jvn.project; packaged games need a project manifest", "jvn.project");
      return;
    }

    String type = normalize(manifest.getProperty("type"), "vn").toLowerCase(Locale.ROOT);
    if (!Set.of("vn", "jes").contains(type)) {
      ctx.add(Severity.ERROR, "packaging", "jvn.project",
          "Unsupported game type '" + type + "'; supported types are vn and jes", type);
    }

    String name = normalize(manifest.getProperty("name"), null);
    String version = normalize(manifest.getProperty("version"), null);
    if (name == null) {
      ctx.add(Severity.WARNING, "packaging", "jvn.project",
          "Manifest has no name; distribution tasks will fall back to a generic game name", null);
    }
    if (version == null) {
      ctx.add(Severity.INFO, "packaging", "jvn.project",
          "Manifest has no version; distribution tasks will use the build default", null);
    }

    if ("vn".equals(type)) {
      inspectVnEntry(ctx, manifest);
    } else if ("jes".equals(type)) {
      inspectJesEntry(ctx, manifest);
    }

    if (!hasAnyFileWithExtension(ctx, SCRIPT_EXTENSIONS)) {
      ctx.add(Severity.ERROR, "packaging", "scripts",
          "No .vns or .jes scripts were found in the project", null);
    }
    if (ctx.knownAssets.isEmpty()) {
      ctx.add(Severity.WARNING, "packaging", "assets",
          "No conventional media assets were found under assets/, images/, audio/, fonts/, ui/, or game/",
          null);
    }
  }

  private static void inspectVnEntry(ValidationContext ctx, Properties manifest) {
    String entry = firstNonBlank(
        manifest.getProperty("entryVns"),
        manifest.getProperty("entry"),
        manifest.getProperty("script"));
    if (entry != null) {
      validateReference(ctx, entry, RefKind.SCRIPT, "jvn.project", ctx.root.resolve("jvn.project"),
          true, Severity.ERROR, "packaging");
      return;
    }

    boolean hasVns = ctx.filesByRel.keySet().stream().anyMatch(p -> "vns".equals(extension(p)));
    if (!hasVns) {
      ctx.add(Severity.ERROR, "packaging", "jvn.project",
          "VN project has no entryVns and no .vns script to discover", null);
    } else {
      ctx.add(Severity.INFO, "packaging", "jvn.project",
          "entryVns is not set; runtime discovery will choose the entry script", null);
    }
  }

  private static void inspectJesEntry(ValidationContext ctx, Properties manifest) {
    String entry = firstNonBlank(
        manifest.getProperty("entryJes"),
        manifest.getProperty("entry"),
        manifest.getProperty("script"),
        manifest.getProperty("mainScript"));
    if (entry != null) {
      validateReference(ctx, entry, RefKind.SCRIPT, "jvn.project", ctx.root.resolve("jvn.project"),
          true, Severity.ERROR, "packaging");
      return;
    }

    if (ctx.filesByRel.containsKey("scripts/main.jes") || ctx.filesByRel.containsKey("game/scripts/main.jes")) {
      return;
    }
    ctx.add(Severity.ERROR, "packaging", "jvn.project",
        "JES project has no entry script and no scripts/main.jes fallback", null);
  }

  private static void inspectRuntimeHealth(ValidationContext ctx) {
    ProjectHealthChecker.Report health = ProjectHealthChecker.inspect(ctx.root.toFile());
    for (ProjectHealthChecker.Diagnostic diagnostic : health.diagnostics()) {
      Severity severity = diagnostic.severity() == ProjectHealthChecker.Severity.ERROR
          ? Severity.ERROR : Severity.WARNING;
      ctx.add(severity, diagnostic.category(), diagnostic.location(), diagnostic.message(), null);
    }
  }

  private static void inspectMenus(ValidationContext ctx) {
    AssetCatalog assets = new AssetCatalog(new OverlayAssetManager(
        new FilesystemAssetManager(ctx.root),
        new ClasspathAssetManager()));
    MenuProfileLoader.LoadResult load = MenuProfileLoader.loadWithDiagnostics(assets);
    MenuProfile profile = load.profile();
    if (profile == null) return;

    for (Map.Entry<String, MenuScreenSpec> entry : profile.screens().entrySet()) {
      String screenId = entry.getKey();
      MenuScreenSpec screen = entry.getValue();
      if (screen == null) continue;
      for (MenuItemSpec item : screen.items()) {
        if (item == null) continue;
        MenuActionSpec action = item.action();
        if (action == null) continue;
        if (action.type() == MenuActionType.RUN_SCRIPT) {
          validateReference(ctx, action.target(), RefKind.SCRIPT,
              "menu:" + screenId + "#" + item.id(), null,
              true, Severity.WARNING, "menu");
        }
        if (action.type() == MenuActionType.OPEN_MENU
            && action.target() != null
            && !profile.hasScreen(action.target())) {
          ctx.add(Severity.WARNING, "menu", "menu:" + screenId + "#" + item.id(),
              "Menu item opens a missing menu screen", action.target());
        }
      }
    }
  }

  private static void inspectConfigFiles(ValidationContext ctx) {
    for (Path file : ctx.files) {
      String rel = ctx.rel(file);
      String ext = extension(rel);
      if (!CONFIG_EXTENSIONS.contains(ext) && !rel.equals("jvn.project")) continue;
      if ("stagepreset".equals(ext)) {
        inspectStagePresetFile(ctx, file);
      }
      inspectPropertyLikeReferences(ctx, file);
    }
  }

  private static void inspectStagePresetFile(ValidationContext ctx, Path file) {
    String rel = ctx.rel(file);
    Optional<Properties> props = loadProperties(file);
    if (props.isEmpty()) {
      ctx.add(Severity.WARNING, "stage-preset", rel,
          "Stage preset could not be loaded as UTF-8 properties", rel);
      return;
    }
    String id = firstNonBlank(
        props.get().getProperty("jvn.stagePreset.id"),
        stripExtension(file.getFileName().toString()));
    if (id != null) ctx.stagePresetIds.add(id);
    try {
      VnStagePresetLoader.load(id, rel, props.get());
    } catch (RuntimeException ex) {
      ctx.add(Severity.WARNING, "stage-preset", rel,
          "Stage preset could not be parsed: " + ex.getMessage(), rel);
    }
  }

  private static void inspectPropertyLikeReferences(ValidationContext ctx, Path file) {
    String rel = ctx.rel(file);
    List<String> lines = readLines(ctx, file);
    for (int i = 0; i < lines.size(); i++) {
      Matcher matcher = PROPERTY_LINE_PATTERN.matcher(lines.get(i));
      if (!matcher.matches()) continue;
      String key = matcher.group(1).trim();
      String value = stripQuotes(matcher.group(2).trim());
      if (value.isBlank() || isIgnoredPropertyValue(value)) continue;
      if (!isReferenceKey(key) && !looksLikePath(value)) continue;

      RefKind kind = inferKind(key, value);
      validateReference(ctx, value, kind, rel + ":" + (i + 1), file,
          false, Severity.WARNING, categoryFor(kind));
    }
  }

  private static void inspectScripts(ValidationContext ctx) {
    VnScriptParser parser = new VnScriptParser();

    for (Path file : ctx.files) {
      String rel = ctx.rel(file);
      String ext = extension(rel);
      if ("vns".equals(ext)) {
        inspectVnsFile(ctx, file, parser);
      } else if ("jes".equals(ext)) {
        inspectJesFile(ctx, file);
      }
    }
  }

  private static void inspectVnsFile(ValidationContext ctx, Path file, VnScriptParser parser) {
    String rel = ctx.rel(file);
    List<String> lines = readLines(ctx, file);
    for (int i = 0; i < lines.size(); i++) {
      inspectVnsLine(ctx, file, rel + ":" + (i + 1), lines.get(i));
    }

    try {
      String script = Files.readString(file, StandardCharsets.UTF_8);
      parser.parse(new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)), rel,
          includePath -> openInclude(ctx, file, includePath));
    } catch (IOException | RuntimeException ex) {
      ctx.add(Severity.ERROR, "script", rel,
          "VNS parse failed: " + shortMessage(ex), rel);
    }
  }

  private static void inspectVnsLine(ValidationContext ctx, Path sourceFile, String location, String rawLine) {
    String line = stripInlineComment(rawLine).trim();
    if (line.isEmpty()) return;

    Matcher include = INCLUDE_PATTERN.matcher(line);
    if (include.matches()) {
      validateReference(ctx, include.group(1), RefKind.SCRIPT, location, sourceFile,
          true, Severity.WARNING, "script");
      return;
    }

    Matcher background = BACKGROUND_PATTERN.matcher(line);
    if (background.matches()) {
      validateReference(ctx, background.group(2), RefKind.IMAGE, location, sourceFile,
          true, Severity.WARNING, "asset");
      return;
    }

    Matcher charImg = CHARIMG_PATTERN.matcher(line);
    if (charImg.matches()) {
      validateReference(ctx, charImg.group(3), RefKind.IMAGE, location, sourceFile,
          true, Severity.WARNING, "asset");
      return;
    }

    Matcher charLayer = CHARLAYER_PATTERN.matcher(line);
    if (charLayer.matches()) {
      validateReference(ctx, charLayer.group(3), RefKind.IMAGE, location, sourceFile,
          true, Severity.WARNING, "asset");
      return;
    }

    Matcher charPreset = CHARPRESET_PATTERN.matcher(line);
    if (charPreset.matches()) {
      inspectCharPresetSpec(ctx, sourceFile, location, charPreset.group(3));
      return;
    }

    Matcher stagePreset = STAGE_PRESET_PATTERN.matcher(line);
    if (stagePreset.matches()) {
      String id = stagePreset.group(1).trim();
      ctx.stagePresetIds.add(id);
      validateReference(ctx, stagePreset.group(2), RefKind.CONFIG, location, sourceFile,
          true, Severity.WARNING, "stage-preset");
      return;
    }

    Matcher external = EXTERNAL_PATTERN.matcher(line);
    if (external.matches()) {
      inspectExternal(ctx, location, external.group(1), external.group(2));
      return;
    }

    Matcher command = COMMAND_PATTERN.matcher(line);
    if (command.matches()) {
      inspectVnsCommand(ctx, sourceFile, location, command.group(1).trim());
    }
  }

  private static void inspectCharPresetSpec(
      ValidationContext ctx,
      Path sourceFile,
      String location,
      String rawSpec
  ) {
    if (rawSpec == null || rawSpec.isBlank()) return;
    String[] parts = rawSpec.split("\\|");
    for (String part : parts) {
      String token = stripQuotes(part.trim());
      if (token.isBlank() || token.startsWith("$")) continue;
      if (looksLikePath(token)) {
        validateReference(ctx, token, RefKind.IMAGE, location, sourceFile,
            false, Severity.WARNING, "asset");
      }
    }
  }

  private static void inspectExternal(
      ValidationContext ctx,
      String location,
      String provider,
      String payload
  ) {
    String normalizedProvider = normalize(provider, "").toLowerCase(Locale.ROOT);
    String normalizedPayload = normalize(payload, "");
    if ("jes_timeline".equals(normalizedProvider)) {
      String name = firstToken(normalizedPayload);
      if (name != null) ctx.timelineUses.add(new NamedReference(name, location));
    }
  }

  private static void inspectVnsCommand(
      ValidationContext ctx,
      Path sourceFile,
      String location,
      String body
  ) {
    String[] split = body.split("\\s+", 2);
    String command = split[0].trim().toLowerCase(Locale.ROOT);
    String payload = split.length > 1 ? split[1].trim() : "";

    switch (command) {
      case "bgm", "sfx", "voice", "bgm_crossfade", "bgm_fadein" ->
          inspectAudioCommand(ctx, sourceFile, location, command, payload);
      case "audio" -> inspectGenericAudioPayload(ctx, sourceFile, location, payload);
      case "stage" -> inspectStageCommand(ctx, sourceFile, location, payload);
      case "call" -> inspectCallCommand(ctx, sourceFile, location, payload);
      case "jes_timeline" -> {
        String name = firstToken(payload);
        if (name != null) ctx.timelineUses.add(new NamedReference(name, location));
      }
      case "load" -> {
        String target = firstToken(payload);
        if (target != null) {
          validateReference(ctx, target, RefKind.SCRIPT, location, sourceFile,
              true, Severity.WARNING, "script");
        }
      }
      case "transition" -> inspectTransitionCommand(ctx, sourceFile, location, payload);
      default -> {
      }
    }
  }

  private static void inspectAudioCommand(
      ValidationContext ctx,
      Path sourceFile,
      String location,
      String command,
      String payload
  ) {
    String track = null;
    for (String token : tokenize(payload)) {
      int eq = token.indexOf('=');
      if (eq > 0) {
        String key = token.substring(0, eq).trim().toLowerCase(Locale.ROOT);
        String value = token.substring(eq + 1).trim();
        if (Set.of("track", "id", "file", "path").contains(key)) {
          track = value;
          break;
        }
        continue;
      }
      if (track == null) {
        track = token;
        break;
      }
    }
    if (track == null) return;
    boolean required = looksLikePath(track) || hasExtension(track);
    validateReference(ctx, track, RefKind.AUDIO, location, sourceFile,
        required, Severity.WARNING, "asset");
  }

  private static void inspectGenericAudioPayload(
      ValidationContext ctx,
      Path sourceFile,
      String location,
      String payload
  ) {
    for (String token : tokenize(payload)) {
      String value = valuePart(token);
      if (!looksLikePath(value) || !isKindExtension(value, RefKind.AUDIO)) continue;
      validateReference(ctx, value, RefKind.AUDIO, location, sourceFile,
          false, Severity.WARNING, "asset");
    }
  }

  private static void inspectStageCommand(
      ValidationContext ctx,
      Path sourceFile,
      String location,
      String payload
  ) {
    String preset = null;
    for (String token : tokenize(payload)) {
      String cleaned = stripQuotes(token);
      if (Set.of("clear", "off", "none").contains(cleaned.toLowerCase(Locale.ROOT))) return;
      int eq = cleaned.indexOf('=');
      if (eq > 0) {
        String key = cleaned.substring(0, eq).trim().toLowerCase(Locale.ROOT);
        if (Set.of("preset", "id", "name", "mode").contains(key)) {
          preset = cleaned.substring(eq + 1).trim();
          break;
        }
      } else if (preset == null) {
        preset = cleaned;
      }
    }
    if (preset != null && !preset.isBlank()) {
      ctx.stageUses.add(new NamedReference(preset, location));
    }
  }

  private static void inspectCallCommand(
      ValidationContext ctx,
      Path sourceFile,
      String location,
      String payload
  ) {
    String[] parts = payload.split("\\s+", 2);
    if (parts.length < 2) return;
    if ("jes_timeline".equalsIgnoreCase(parts[0])) {
      String name = firstToken(parts[1]);
      if (name != null) ctx.timelineUses.add(new NamedReference(name, location));
    }
  }

  private static void inspectTransitionCommand(
      ValidationContext ctx,
      Path sourceFile,
      String location,
      String payload
  ) {
    for (String token : tokenize(payload)) {
      String value = valuePart(token);
      if (!looksLikePath(value) || !isKindExtension(value, RefKind.IMAGE)) continue;
      validateReference(ctx, value, RefKind.IMAGE, location, sourceFile,
          false, Severity.WARNING, "asset");
    }
  }

  private static void inspectJesFile(ValidationContext ctx, Path file) {
    String rel = ctx.rel(file);
    String source;
    try {
      source = Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException ex) {
      ctx.add(Severity.WARNING, "script", rel,
          "Could not read JES script: " + ex.getMessage(), rel);
      return;
    }

    try {
      TimelineDataParser.parse(stripExtension(file.getFileName().toString()), source);
    } catch (RuntimeException ex) {
      ctx.add(Severity.WARNING, "timeline", rel,
          "JES timeline parse failed: " + shortMessage(ex), rel);
    }

    Matcher audioMatcher = TIMELINE_AUDIO_PATTERN.matcher(source);
    while (audioMatcher.find()) {
      String path = audioMatcher.group(1);
      int line = lineNumber(source, audioMatcher.start());
      validateReference(ctx, path, RefKind.AUDIO, rel + ":" + line, file,
          true, Severity.WARNING, "asset");
    }

    Matcher quotedMatcher = QUOTED_VALUE_PATTERN.matcher(source);
    while (quotedMatcher.find()) {
      String value = quotedMatcher.group(1);
      if (!looksLikePath(value)) continue;
      RefKind kind = inferKind("", value);
      if (kind == RefKind.SCRIPT || kind == RefKind.CONFIG || isMediaKind(kind)) {
        int line = lineNumber(source, quotedMatcher.start());
        validateReference(ctx, value, kind, rel + ":" + line, file,
            false, Severity.WARNING, categoryFor(kind));
      }
    }
  }

  private static void inspectTimelineFiles(ValidationContext ctx) {
    for (Path file : ctx.files) {
      String rel = ctx.rel(file);
      if (!"jes".equals(extension(rel))) continue;
      if (isTimelineFile(rel)) {
        ctx.timelineIds.add(stripExtension(file.getFileName().toString()));
      }
    }
  }

  private static void inspectPendingReferences(ValidationContext ctx) {
    for (NamedReference ref : ctx.stageUses) {
      if (!ctx.stagePresetIds.contains(ref.name())) {
        ctx.add(Severity.WARNING, "stage-preset", ref.location(),
            "Stage preset is used but no @stagepreset declaration or .stagepreset export defines it",
            ref.name());
      }
    }
    for (NamedReference ref : ctx.timelineUses) {
      if (!ctx.timelineIds.contains(ref.name())) {
        ctx.add(Severity.WARNING, "timeline", ref.location(),
            "JES timeline is referenced but no matching scripts/timelines/<name>.jes file was found",
            ref.name());
      }
    }
  }

  private static void inspectUnusedAssets(ValidationContext ctx) {
    Set<String> used = new HashSet<>(ctx.referencedAssets);
    for (String asset : new TreeSet<>(ctx.knownAssets)) {
      if (used.contains(asset)) continue;
      ctx.add(Severity.INFO, "asset", asset,
          "Asset is present in a conventional media folder but was not referenced by scanned scripts or configs",
          asset);
    }
  }

  private static void validateReference(
      ValidationContext ctx,
      String rawPath,
      RefKind kind,
      String location,
      Path sourceFile,
      boolean required,
      Severity missingSeverity,
      String category
  ) {
    String path = normalizePath(rawPath);
    if (path == null || path.isBlank() || isDynamicPath(path) || isIgnoredPropertyValue(path)) return;
    if (!required && !looksLikePath(path) && !hasKnownReferenceExtension(path)) return;

    RefKind effectiveKind = kind == RefKind.ASSET ? inferKind("", path) : kind;
    Optional<ResolvedReference> resolved = resolveReference(ctx, path, effectiveKind, sourceFile);
    if (resolved.isPresent()) {
      String rel = resolved.get().resolved();
      if (isKnownMediaAsset(rel) || isMediaKind(effectiveKind)) {
        ctx.referencedAssets.add(rel);
      }
      if (resolved.get().caseMismatch()) {
        ctx.add(Severity.WARNING, category, location,
            "Reference resolves only with different filename casing; this can break on Linux",
            path + " -> " + rel);
      }
      return;
    }

    ctx.add(missingSeverity == null ? Severity.WARNING : missingSeverity, category, location,
        "Missing " + effectiveKind.label + " reference", path);
  }

  private static Optional<ResolvedReference> resolveReference(
      ValidationContext ctx,
      String raw,
      RefKind kind,
      Path sourceFile
  ) {
    String path = normalizePath(raw);
    if (path == null || path.isBlank()) return Optional.empty();
    if (isAbsolutePath(path)) return Optional.empty();

    LinkedHashSet<String> candidates = new LinkedHashSet<>();
    addCandidate(candidates, path);
    if (sourceFile != null) {
      String sourceRel = ctx.rel(sourceFile);
      Path sourceParent = Path.of(sourceRel).getParent();
      if (sourceParent != null) {
        addCandidate(candidates, sourceParent.resolve(path).normalize().toString());
      }
    }

    String withoutConventionalRoot = stripConventionalRoot(path);
    addCandidate(candidates, withoutConventionalRoot);
    switch (kind) {
      case SCRIPT -> addScriptCandidates(candidates, path, withoutConventionalRoot);
      case IMAGE -> addImageCandidates(candidates, path, withoutConventionalRoot);
      case AUDIO -> addAudioCandidates(candidates, path, withoutConventionalRoot);
      case FONT -> addFontCandidates(candidates, path, withoutConventionalRoot);
      case VIDEO -> addVideoCandidates(candidates, path, withoutConventionalRoot);
      case CONFIG -> addConfigCandidates(candidates, path, withoutConventionalRoot);
      case ASSET -> addAssetCandidates(candidates, path, withoutConventionalRoot);
    }

    for (String candidate : candidates) {
      String normalized = normalizePath(candidate);
      if (normalized == null || normalized.startsWith("../") || normalized.equals("..")) continue;
      if (ctx.filesByRel.containsKey(normalized)) {
        return Optional.of(new ResolvedReference(path, normalized, false));
      }
    }

    for (String candidate : candidates) {
      String normalized = normalizePath(candidate);
      if (normalized == null) continue;
      String caseResolved = ctx.filesByLowerRel.get(normalized.toLowerCase(Locale.ROOT));
      if (caseResolved != null) {
        return Optional.of(new ResolvedReference(path, caseResolved, true));
      }
    }
    return Optional.empty();
  }

  private static void addScriptCandidates(Set<String> candidates, String path, String rel) {
    addCandidate(candidates, "scripts/" + rel);
    addCandidate(candidates, "game/scripts/" + rel);
    addCandidate(candidates, "game/" + rel);
    if (!hasExtension(rel)) {
      addCandidate(candidates, rel + ".vns");
      addCandidate(candidates, rel + ".jes");
      addCandidate(candidates, "scripts/" + rel + ".vns");
      addCandidate(candidates, "scripts/" + rel + ".jes");
      addCandidate(candidates, "game/scripts/" + rel + ".vns");
      addCandidate(candidates, "game/scripts/" + rel + ".jes");
    }
    if (path.startsWith("story/")) {
      addCandidate(candidates, "scripts/" + path);
      addCandidate(candidates, "game/scripts/" + path);
    }
  }

  private static void addImageCandidates(Set<String> candidates, String path, String rel) {
    addCandidate(candidates, "assets/" + rel);
    addCandidate(candidates, "assets/images/" + rel);
    addCandidate(candidates, "assets/backgrounds/" + rel);
    addCandidate(candidates, "assets/characters/" + rel);
    addCandidate(candidates, "assets/ui/" + rel);
    addCandidate(candidates, "images/" + rel);
    addCandidate(candidates, "ui/" + rel);
    addCandidate(candidates, "game/assets/" + rel);
    addCandidate(candidates, "game/images/" + rel);
    addCandidate(candidates, "game/ui/" + rel);
  }

  private static void addAudioCandidates(Set<String> candidates, String path, String rel) {
    addCandidate(candidates, "assets/" + rel);
    addCandidate(candidates, "assets/audio/" + rel);
    addCandidate(candidates, "audio/" + rel);
    addCandidate(candidates, "game/assets/" + rel);
    addCandidate(candidates, "game/assets/audio/" + rel);
    addCandidate(candidates, "game/audio/" + rel);
  }

  private static void addFontCandidates(Set<String> candidates, String path, String rel) {
    addCandidate(candidates, "assets/" + rel);
    addCandidate(candidates, "assets/fonts/" + rel);
    addCandidate(candidates, "fonts/" + rel);
    addCandidate(candidates, "game/assets/" + rel);
    addCandidate(candidates, "game/assets/fonts/" + rel);
    addCandidate(candidates, "game/fonts/" + rel);
  }

  private static void addVideoCandidates(Set<String> candidates, String path, String rel) {
    addCandidate(candidates, "assets/" + rel);
    addCandidate(candidates, "assets/video/" + rel);
    addCandidate(candidates, "video/" + rel);
    addCandidate(candidates, "game/assets/" + rel);
    addCandidate(candidates, "game/video/" + rel);
  }

  private static void addConfigCandidates(Set<String> candidates, String path, String rel) {
    addCandidate(candidates, "config/" + rel);
    addCandidate(candidates, "game/config/" + rel);
    if (!hasExtension(rel)) {
      addCandidate(candidates, rel + ".properties");
      addCandidate(candidates, "config/" + rel + ".properties");
      addCandidate(candidates, "config/" + rel + ".stagepreset");
      addCandidate(candidates, "game/config/" + rel + ".properties");
      addCandidate(candidates, "game/config/" + rel + ".stagepreset");
    }
  }

  private static void addAssetCandidates(Set<String> candidates, String path, String rel) {
    addCandidate(candidates, "assets/" + rel);
    addCandidate(candidates, "game/assets/" + rel);
    addImageCandidates(candidates, path, rel);
    addAudioCandidates(candidates, path, rel);
    addFontCandidates(candidates, path, rel);
    addVideoCandidates(candidates, path, rel);
  }

  private static void addCandidate(Set<String> candidates, String candidate) {
    String normalized = normalizePath(candidate);
    if (normalized != null && !normalized.isBlank()) candidates.add(normalized);
  }

  private static java.io.InputStream openInclude(ValidationContext ctx, Path sourceFile, String includePath)
      throws IOException {
    Optional<ResolvedReference> resolved = resolveReference(ctx, includePath, RefKind.SCRIPT, sourceFile);
    if (resolved.isPresent()) return Files.newInputStream(ctx.root.resolve(resolved.get().resolved()));
    throw new IOException("Missing include: " + includePath);
  }

  private static Optional<Properties> loadProperties(Path file) {
    if (file == null || !Files.isRegularFile(file)) return Optional.empty();
    Properties props = new Properties();
    try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file.toFile()), StandardCharsets.UTF_8)) {
      props.load(reader);
      return Optional.of(props);
    } catch (IOException | IllegalArgumentException ex) {
      return Optional.empty();
    }
  }

  private static List<String> readLines(ValidationContext ctx, Path file) {
    try {
      return Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (IOException ex) {
      ctx.add(Severity.WARNING, "project", ctx.rel(file),
          "Could not read file during dependency scan: " + ex.getMessage(), ctx.rel(file));
      return List.of();
    }
  }

  private static boolean hasAnyFileWithExtension(ValidationContext ctx, Set<String> extensions) {
    for (String rel : ctx.filesByRel.keySet()) {
      if (extensions.contains(extension(rel))) return true;
    }
    return false;
  }

  private static boolean isKnownMediaAsset(String rel) {
    String p = normalizePath(rel);
    if (p == null) return false;
    if (!isMediaExtension(extension(p))) return false;
    return startsWithAny(p,
        "assets/", "images/", "audio/", "fonts/", "ui/", "video/",
        "game/assets/", "game/images/", "game/audio/", "game/fonts/", "game/ui/", "game/video/");
  }

  private static boolean isTimelineFile(String rel) {
    String p = normalizePath(rel);
    return p != null && (p.startsWith("scripts/timelines/") || p.startsWith("game/scripts/timelines/"));
  }

  private static boolean isReferenceKey(String key) {
    String k = key == null ? "" : key.toLowerCase(Locale.ROOT);
    return k.contains("asset")
        || k.contains("image")
        || k.contains("icon")
        || k.contains("font")
        || k.contains("audio")
        || k.contains("sound")
        || k.contains("music")
        || k.contains("voice")
        || k.contains("path")
        || k.contains("file")
        || k.contains("background")
        || k.contains("sprite")
        || k.contains("tileset")
        || k.contains("mask")
        || k.contains("script");
  }

  private static RefKind inferKind(String key, String value) {
    String k = key == null ? "" : key.toLowerCase(Locale.ROOT);
    String ext = extension(value);
    if (IMAGE_EXTENSIONS.contains(ext) || k.contains("image") || k.contains("icon")
        || k.contains("background") || k.contains("sprite") || k.contains("tileset")
        || k.contains("mask")) {
      return RefKind.IMAGE;
    }
    if (AUDIO_EXTENSIONS.contains(ext) || k.contains("audio") || k.contains("sound")
        || k.contains("music") || k.contains("voice")) {
      return RefKind.AUDIO;
    }
    if (FONT_EXTENSIONS.contains(ext) || k.contains("font")) return RefKind.FONT;
    if (VIDEO_EXTENSIONS.contains(ext) || k.contains("video")) return RefKind.VIDEO;
    if (SCRIPT_EXTENSIONS.contains(ext) || k.contains("script")) return RefKind.SCRIPT;
    if (CONFIG_EXTENSIONS.contains(ext) || k.contains("config") || k.contains("preset")) return RefKind.CONFIG;
    return RefKind.ASSET;
  }

  private static String categoryFor(RefKind kind) {
    if (kind == RefKind.SCRIPT) return "script";
    if (kind == RefKind.CONFIG) return "config";
    return "asset";
  }

  private static boolean isMediaKind(RefKind kind) {
    return kind == RefKind.IMAGE || kind == RefKind.AUDIO || kind == RefKind.FONT || kind == RefKind.VIDEO;
  }

  private static boolean isKindExtension(String path, RefKind kind) {
    String ext = extension(path);
    return switch (kind) {
      case IMAGE -> IMAGE_EXTENSIONS.contains(ext);
      case AUDIO -> AUDIO_EXTENSIONS.contains(ext);
      case FONT -> FONT_EXTENSIONS.contains(ext);
      case VIDEO -> VIDEO_EXTENSIONS.contains(ext);
      case SCRIPT -> SCRIPT_EXTENSIONS.contains(ext);
      case CONFIG -> CONFIG_EXTENSIONS.contains(ext);
      case ASSET -> hasExtension(path);
    };
  }

  private static boolean isMediaExtension(String ext) {
    return IMAGE_EXTENSIONS.contains(ext)
        || AUDIO_EXTENSIONS.contains(ext)
        || FONT_EXTENSIONS.contains(ext)
        || VIDEO_EXTENSIONS.contains(ext);
  }

  private static boolean looksLikePath(String value) {
    String v = normalizePath(value);
    if (v == null || v.isBlank()) return false;
    if (isDynamicPath(v) || isIgnoredPropertyValue(v)) return false;
    return v.contains("/")
        || v.contains("\\")
        || startsWithAny(v, "assets/", "images/", "audio/", "fonts/", "ui/", "video/",
            "game/", "scripts/", "config/", "story/")
        || hasKnownReferenceExtension(v);
  }

  private static boolean hasExtension(String value) {
    return !extension(value).isBlank();
  }

  private static boolean hasKnownReferenceExtension(String value) {
    String ext = extension(value);
    return IMAGE_EXTENSIONS.contains(ext)
        || AUDIO_EXTENSIONS.contains(ext)
        || FONT_EXTENSIONS.contains(ext)
        || VIDEO_EXTENSIONS.contains(ext)
        || SCRIPT_EXTENSIONS.contains(ext)
        || CONFIG_EXTENSIONS.contains(ext);
  }

  private static boolean isIgnoredPropertyValue(String value) {
    String v = value == null ? "" : value.trim();
    if (v.isBlank()) return true;
    String lower = v.toLowerCase(Locale.ROOT);
    if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("data:")) return true;
    if ("true".equals(lower) || "false".equals(lower) || "on".equals(lower) || "off".equals(lower)) return true;
    if (v.matches("#[0-9A-Fa-f]{3,8}")) return true;
    if (v.matches("-?\\d+(\\.\\d+)?")) return true;
    if (v.matches("\\d+(\\.\\d+){2,}([A-Za-z0-9_.+-]*)?")) return true;
    if (v.matches("-?\\d+(\\.\\d+)?(,-?\\d+(\\.\\d+)?)+")) return true;
    return v.contains("${") || v.contains("%{");
  }

  private static boolean isDynamicPath(String value) {
    String v = value == null ? "" : value;
    return v.contains("${") || v.contains("$(") || v.contains("{") || v.contains("}");
  }

  private static boolean isAbsolutePath(String path) {
    try {
      return Path.of(path).isAbsolute();
    } catch (InvalidPathException ex) {
      return false;
    }
  }

  private static String stripConventionalRoot(String path) {
    String p = normalizePath(path);
    if (p == null) return "";
    for (String prefix : List.of(
        "assets/images/", "assets/backgrounds/", "assets/characters/", "assets/audio/",
        "assets/fonts/", "assets/ui/", "assets/video/", "assets/",
        "game/assets/images/", "game/assets/audio/", "game/assets/fonts/", "game/assets/",
        "game/images/", "game/audio/", "game/fonts/", "game/ui/", "game/video/",
        "images/", "audio/", "fonts/", "ui/", "video/",
        "scripts/", "game/scripts/", "config/", "game/config/")) {
      if (p.startsWith(prefix)) return p.substring(prefix.length());
    }
    return p;
  }

  private static String stripInlineComment(String line) {
    if (line == null) return "";
    String t = line.trim();
    if (t.startsWith("#") || t.startsWith("//")) return "";
    return line;
  }

  private static List<String> tokenize(String payload) {
    if (payload == null || payload.isBlank()) return List.of();
    try {
      return List.of(VnArgTokenizer.tokenizeToArray(payload));
    } catch (RuntimeException ex) {
      return List.of(payload.split("\\s+"));
    }
  }

  private static String valuePart(String token) {
    if (token == null) return "";
    int eq = token.indexOf('=');
    if (eq > 0 && eq < token.length() - 1) return stripQuotes(token.substring(eq + 1));
    return stripQuotes(token);
  }

  private static String firstToken(String payload) {
    if (payload == null || payload.isBlank()) return null;
    for (String token : tokenize(payload)) {
      String cleaned = stripQuotes(token);
      if (!cleaned.isBlank()) return cleaned;
    }
    return null;
  }

  private static String firstNonBlank(String... values) {
    if (values == null) return null;
    for (String value : values) {
      String normalized = normalize(value, null);
      if (normalized != null) return normalized;
    }
    return null;
  }

  private static int lineNumber(String source, int offset) {
    int line = 1;
    for (int i = 0; i < offset && i < source.length(); i++) {
      if (source.charAt(i) == '\n') line++;
    }
    return line;
  }

  private static String shortMessage(Throwable ex) {
    String message = ex.getMessage();
    if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
    int newline = message.indexOf('\n');
    return newline >= 0 ? message.substring(0, newline) : message;
  }

  private static boolean startsWithAny(String value, String... prefixes) {
    if (value == null) return false;
    for (String prefix : prefixes) {
      if (value.startsWith(prefix)) return true;
    }
    return false;
  }

  private static String extension(String path) {
    String p = normalizePath(path);
    if (p == null) return "";
    int slash = p.lastIndexOf('/');
    int dot = p.lastIndexOf('.');
    if (dot <= slash || dot == p.length() - 1) return "";
    return p.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private static String stripExtension(String fileName) {
    if (fileName == null) return "";
    int dot = fileName.lastIndexOf('.');
    return dot <= 0 ? fileName : fileName.substring(0, dot);
  }

  private static String stripQuotes(String value) {
    if (value == null) return null;
    String t = value.trim();
    if (t.length() >= 2) {
      char first = t.charAt(0);
      char last = t.charAt(t.length() - 1);
      if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        return t.substring(1, t.length() - 1).trim();
      }
    }
    return t;
  }

  private static String normalizePath(String value) {
    String t = stripQuotes(value);
    if (t == null) return null;
    int query = t.indexOf('?');
    if (query >= 0) t = t.substring(0, query);
    int fragment = t.indexOf('#');
    if (fragment > 0) t = t.substring(0, fragment);
    t = t.replace('\\', '/').trim();
    while (t.startsWith("./")) t = t.substring(2);
    while (t.startsWith("/")) t = t.substring(1);
    while (t.contains("//")) t = t.replace("//", "/");
    return t.isBlank() ? null : t;
  }

  private static String normalize(String value, String fallback) {
    if (value == null) return fallback;
    String t = value.trim();
    return t.isEmpty() ? fallback : t;
  }

  private static final class ValidationContext {
    private final Path root;
    private final List<Path> files = new ArrayList<>();
    private final Map<String, Path> filesByRel = new LinkedHashMap<>();
    private final Map<String, String> filesByLowerRel = new HashMap<>();
    private final Set<String> knownAssets = new LinkedHashSet<>();
    private final Set<String> referencedAssets = new LinkedHashSet<>();
    private final Set<String> timelineIds = new LinkedHashSet<>();
    private final Set<String> stagePresetIds = new LinkedHashSet<>();
    private final List<NamedReference> timelineUses = new ArrayList<>();
    private final List<NamedReference> stageUses = new ArrayList<>();
    private final List<Finding> findings = new ArrayList<>();
    private final Set<String> findingKeys = new HashSet<>();

    private ValidationContext(Path root) {
      this.root = root;
    }

    private String rel(Path file) {
      Path absolute = file.toAbsolutePath().normalize();
      try {
        return normalizePath(root.relativize(absolute).toString());
      } catch (IllegalArgumentException ex) {
        return normalizePath(absolute.toString());
      }
    }

    private void add(Severity severity, String category, String location, String message, String target) {
      Finding finding = new Finding(severity, category, location, message, target);
      String key = finding.severity() + "\n" + finding.category() + "\n"
          + finding.location() + "\n" + finding.message() + "\n"
          + Objects.toString(finding.target(), "");
      if (findingKeys.add(key)) findings.add(finding);
    }

    private Report report() {
      List<Finding> sorted = findings.stream()
          .sorted(Comparator
              .comparing((Finding f) -> f.severity().ordinal())
              .thenComparing(Finding::category)
              .thenComparing(Finding::location)
              .thenComparing(Finding::message))
          .toList();
      return new Report(root, sorted, referencedAssets, knownAssets);
    }
  }
}
