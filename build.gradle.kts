import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaToolchainService
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.Properties

plugins {
  java
}

val jvnGroup = (findProperty("jvnGroup") as String?) ?: "com.jvn"
val jvnVersion = (findProperty("jvnVersion") as String?) ?: "0.1-SNAPSHOT"

group = jvnGroup
version = jvnVersion

val configuredJavaVersion = (findProperty("javaVersion") as String?)?.toIntOrNull() ?: 21
java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(configuredJavaVersion))
  }
}

val jvnToolchains = extensions.getByType<JavaToolchainService>()
val jvnPackagingLauncher = jvnToolchains.launcherFor {
  languageVersion.set(JavaLanguageVersion.of(configuredJavaVersion))
}

data class JvnGameTarget(
  val id: String,
  val taskSuffix: String,
  val javafxClassifier: String,
  val windows: Boolean
)

data class JvnGameProjectValidation(
  val dir: File,
  val manifest: Properties,
  val type: String,
  val entryKey: String?,
  val warnings: List<String>
)

data class JvnGamePackageHost(
  val osId: String,
  val target: JvnGameTarget,
  val nativePackageTypes: List<String>,
  val defaultNativePackageType: String
)

data class JvnReleaseProfile(
  val name: String,
  val file: File?,
  val properties: Properties
) {
  fun value(key: String): String? {
    return listOf(
      "profile.$name.$key",
      "profile.default.$key",
      key
    ).asSequence()
      .mapNotNull { properties.getProperty(it)?.trim() }
      .firstOrNull { it.isNotBlank() }
  }

  fun flag(key: String, default: Boolean = false): Boolean {
    val raw = value(key) ?: return default
    return raw.lowercase() !in setOf("", "0", "false", "no", "off")
  }

  fun commands(prefix: String): List<String> {
    val roots = listOf("profile.$name.$prefix.", "profile.default.$prefix.")
    val values = mutableMapOf<String, String>()
    roots.forEach { root ->
      properties.stringPropertyNames()
        .filter { it.startsWith(root) }
        .sortedBy { it.removePrefix(root) }
        .forEach { key ->
          val suffix = key.removePrefix(root)
          val value = properties.getProperty(key)?.trim()
          if (!value.isNullOrBlank()) values.putIfAbsent(suffix, value)
        }
    }
    return values.toSortedMap().values.toList()
  }
}

val jvnJavaFxVersion = "21.0.3"
val jvnJavaFxModules = listOf(
  "javafx-base",
  "javafx-graphics",
  "javafx-controls",
  "javafx-media",
  "javafx-swing",
  "javafx-fxml"
)
val jvnJavaFxRuntimeModules = listOf(
  "javafx.controls",
  "javafx.graphics",
  "javafx.base",
  "javafx.media",
  "javafx.swing",
  "javafx.fxml"
)
val jvnGameTargets = listOf(
  JvnGameTarget("windows-x64", "WindowsX64", "win", true),
  JvnGameTarget("linux-x64", "LinuxX64", "linux", false),
  JvnGameTarget("macos-x64", "MacosX64", "mac", false),
  JvnGameTarget("macos-aarch64", "MacosAarch64", "mac-aarch64", false)
)
val jvnGameRuntimeProjectPaths = listOf(
  ":core",
  ":fx",
  ":audio",
  ":scripting",
  ":swing",
  ":runtime",
  ":demo-game"
)
val jvnGameJavaFxConfigurations = mutableMapOf<String, org.gradle.api.artifacts.Configuration>()

fun currentGameTarget(): JvnGameTarget {
  val osName = System.getProperty("os.name", "").lowercase()
  val arch = System.getProperty("os.arch", "").lowercase()
  return when {
    osName.contains("win") -> jvnGameTargets.first { it.id == "windows-x64" }
    osName.contains("linux") && (arch.contains("aarch64") || arch.contains("arm64")) ->
      throw GradleException("Linux aarch64 portable builds are not supported by OpenJFX $jvnJavaFxVersion target classifiers.")
    osName.contains("linux") -> jvnGameTargets.first { it.id == "linux-x64" }
    osName.contains("mac") && (arch.contains("aarch64") || arch.contains("arm64")) ->
      jvnGameTargets.first { it.id == "macos-aarch64" }
    osName.contains("mac") -> jvnGameTargets.first { it.id == "macos-x64" }
    else -> throw GradleException("Unsupported host OS/Arch for current portable target: $osName/$arch")
  }
}

fun gameProjectDir(): File {
  val raw = findProperty("jvnGameProject") as String?
  if (raw.isNullOrBlank()) {
    throw GradleException("Missing -PjvnGameProject=/absolute/path/to/jvn-game. Game packaging builds JVN-made projects, not the engine workspace.")
  }
  val dir = file(raw)
  if (!dir.isDirectory) {
    throw GradleException("JVN game project does not exist or is not a directory: ${dir.absolutePath}")
  }
  val manifest = File(dir, "jvn.project")
  if (!manifest.isFile) {
    throw GradleException("JVN game project is missing jvn.project: ${dir.absolutePath}")
  }
  return dir
}

fun gameManifest(): Properties {
  val props = Properties()
  val manifest = File(gameProjectDir(), "jvn.project")
  manifest.inputStream().use { props.load(it) }
  return props
}

fun gradleFlag(name: String): Boolean {
  val raw = findProperty(name) as String?
  return raw != null && raw.trim().lowercase() !in setOf("", "0", "false", "no", "off")
}

fun canonicalOrAbsolute(file: File): File {
  return try {
    file.canonicalFile
  } catch (_: Exception) {
    file.absoluteFile
  }
}

fun normalizeProjectPath(raw: String?): String? {
  if (raw == null) return null
  var value = raw.trim().replace('\\', '/')
  if (value.isBlank()) return null
  while (value.startsWith("./")) value = value.substring(2)
  while (value.startsWith("/")) value = value.substring(1)
  return value.ifBlank { null }
}

fun normalizeScriptKey(raw: String?): String? {
  var value = normalizeProjectPath(raw) ?: return null
  if (value.startsWith("game/scripts/")) value = value.substring("game/scripts/".length)
  if (value.startsWith("scripts/")) value = value.substring("scripts/".length)
  return value.ifBlank { null }
}

fun resolveScriptFile(dir: File, raw: String?): File? {
  val normalized = normalizeProjectPath(raw) ?: return null
  val scriptKey = normalizeScriptKey(normalized) ?: normalized
  val candidates = linkedSetOf<File>()
  candidates += File(dir, normalized)
  candidates += File(dir, scriptKey)
  candidates += File(dir, "scripts/$scriptKey")
  candidates += File(dir, "game/scripts/$scriptKey")
  if (normalized.startsWith("game/") && !normalized.startsWith("game/scripts/")) {
    candidates += File(dir, "scripts/${normalized.substring("game/".length)}")
  }
  return candidates.firstOrNull { it.isFile }
}

fun discoveredScript(dir: File, extension: String): String? {
  val ext = extension.removePrefix(".")
  val scriptsDir = listOf(File(dir, "scripts"), File(dir, "game/scripts")).firstOrNull { it.isDirectory } ?: return null
  return scriptsDir.walkTopDown()
    .filter { it.isFile && it.extension.equals(ext, ignoreCase = true) }
    .map { scriptsDir.toPath().relativize(it.toPath()).toString().replace('\\', '/') }
    .sortedWith(compareBy<String> {
      val key = it.lowercase()
      when {
        key == "story/prologue.$ext" -> 0
        key == "prologue.$ext" -> 1
        key == "story/main.$ext" -> 2
        key == "main.$ext" -> 3
        key.contains("prologue") -> 10
        key.contains("start") -> 11
        key.contains("main") -> 12
        else -> 100
      }
    }.thenBy { it.lowercase() })
    .firstOrNull()
}

fun validateGameProject(): JvnGameProjectValidation {
  val dir = gameProjectDir()
  val manifest = gameManifest()
  val warnings = mutableListOf<String>()
  val errors = mutableListOf<String>()
  val type = manifest.getProperty("type", "vn").trim().lowercase().ifBlank { "vn" }

  if (canonicalOrAbsolute(dir) == canonicalOrAbsolute(projectDir) && !gradleFlag("jvnAllowEngineWorkspacePackage")) {
    errors += "Selected project is the JVN engine workspace. Game packaging expects a separate JVN-made game project. If this is intentional, pass -PjvnAllowEngineWorkspacePackage=true."
  }

  if (dir.name != dir.name.trim()) {
    warnings += "Project folder name has leading or trailing whitespace. The build preserves it, but it is easy to mistype on the CLI."
  }

  val entryKey = when (type) {
    "vn" -> {
      val configured = normalizeScriptKey(manifest.getProperty("entryVns"))
      if (configured != null) {
        if (resolveScriptFile(dir, configured) == null) {
          errors += "Configured entryVns is missing: ${manifest.getProperty("entryVns")}"
        }
        configured
      } else {
        val discovered = discoveredScript(dir, "vns")
        if (discovered == null) {
          errors += "No VN entry script could be resolved. Set entryVns in jvn.project or add a .vns file under scripts/."
        } else {
          warnings += "entryVns is not set; runtime will start from discovered script: $discovered"
        }
        discovered
      }
    }
    "jes" -> {
      val configured = normalizeProjectPath(manifest.getProperty("entry") ?: "scripts/main.jes")
      if (configured == null) {
        errors += "JES projects must define entry=<path-to-jes> in jvn.project."
        null
      } else {
        if (resolveScriptFile(dir, configured) == null) {
          errors += "Configured JES entry is missing: $configured"
        }
        configured
      }
    }
    "gradle" -> {
      errors += "type=gradle projects describe an engine/workspace run command, not a distributable JVN game. Open a type=vn or type=jes game project for packaging."
      null
    }
    else -> {
      errors += "Unsupported jvn.project type for portable game packaging: $type. Supported types: vn, jes."
      null
    }
  }

  if (!File(dir, "scripts").isDirectory && !File(dir, "game/scripts").isDirectory) {
    warnings += "No scripts/ or game/scripts/ directory was found."
  }
  if (!File(dir, "assets").isDirectory && !File(dir, "game").isDirectory) {
    warnings += "No assets/ or game/ directory was found; package may be script-only."
  }

  if (errors.isNotEmpty()) {
    throw GradleException("Invalid JVN game project:\n - ${errors.joinToString("\n - ")}")
  }

  return JvnGameProjectValidation(dir, manifest, type, entryKey, warnings)
}

fun currentHostIsWindows(): Boolean = System.getProperty("os.name", "").lowercase().contains("win")

fun packagingJavaHome(): File = jvnPackagingLauncher.get().metadata.installationPath.asFile

fun packagingTool(name: String): File {
  val executable = if (currentHostIsWindows()) "$name.exe" else name
  val tool = packagingJavaHome().resolve("bin/$executable")
  if (!tool.isFile) {
    throw GradleException("Required packaging tool was not found in the configured JDK toolchain: ${tool.absolutePath}")
  }
  return tool
}

fun currentPackageHost(): JvnGamePackageHost {
  val target = currentGameTarget()
  val osName = System.getProperty("os.name", "").lowercase()
  return when {
    osName.contains("win") -> JvnGamePackageHost("windows", target, listOf("app-image", "exe", "msi"), "exe")
    osName.contains("linux") -> JvnGamePackageHost("linux", target, listOf("app-image", "deb", "rpm"), "deb")
    osName.contains("mac") -> JvnGamePackageHost("macos", target, listOf("app-image", "dmg", "pkg"), "dmg")
    else -> throw GradleException("Unsupported host OS for native packaging: $osName")
  }
}

fun gameReleaseConfigFile(): File? {
  val dir = gameProjectDir()
  return listOf(
    File(dir, "config/release/jvn-release.properties"),
    File(dir, "config/release/release.properties"),
    File(dir, "release/jvn-release.properties"),
    File(dir, "jvn-release.properties")
  ).firstOrNull { it.isFile }
}

fun gameReleaseConfig(): Properties {
  val props = Properties()
  val file = gameReleaseConfigFile() ?: return props
  file.inputStream().use { props.load(it) }
  return props
}

fun gameReleaseProfileNames(): List<String> {
  val props = gameReleaseConfig()
  val discovered = props.stringPropertyNames()
    .mapNotNull { key ->
      if (!key.startsWith("profile.")) return@mapNotNull null
      val suffix = key.removePrefix("profile.")
      val profile = suffix.substringBefore('.', "")
      profile.ifBlank { null }
    }
    .filterNot { it.equals("default", ignoreCase = true) }
    .distinct()
    .sorted()
  return if (discovered.isEmpty()) listOf("default") else listOf("default") + discovered
}

fun selectedReleaseProfileName(): String {
  val explicit = (findProperty("jvnReleaseProfile") as String?)?.trim()
  if (!explicit.isNullOrBlank()) return explicit
  val configured = gameReleaseConfig().getProperty("defaultProfile", "").trim()
  return configured.ifBlank { "default" }
}

fun selectedReleaseProfile(): JvnReleaseProfile {
  return JvnReleaseProfile(selectedReleaseProfileName(), gameReleaseConfigFile(), gameReleaseConfig())
}

fun nativeGameVersion(): String {
  val explicit = (findProperty("jvnNativeVersion") as String?)?.trim()
  if (!explicit.isNullOrBlank()) return explicit
  val normalized = gameVersion()
    .replace(Regex("[^0-9.]+"), ".")
    .replace(Regex("\\.{2,}"), ".")
    .trim('.')
  return normalized.ifBlank { "1.0.0" }
}

fun currentJavaFxConfiguration(): org.gradle.api.artifacts.Configuration {
  return jvnGameJavaFxConfigurations[currentGameTarget().id]
    ?: throw GradleException("No JavaFX runtime configuration is registered for ${currentGameTarget().id}.")
}

fun currentJavaFxRuntimeJars(): List<File> {
  return currentJavaFxConfiguration().resolve()
    .filter { it.isFile && it.name.startsWith("javafx-") && it.name.endsWith(".jar") }
    .distinctBy { it.name }
    .sortedBy { it.name }
}

fun gameRuntimeClasspathJars(): List<File> {
  return (
    jvnGameRuntimeProjectPaths.map { projectPath ->
      project(projectPath).tasks.named<Jar>("jar").get().archiveFile.get().asFile
    } + externalRuntimeJars()
  ).filter { it.isFile }
    .distinctBy { it.name }
    .sortedBy { it.name }
}

fun currentJpackageType(): String {
  val host = currentPackageHost()
  val explicit = (findProperty("jvnNativePackageType") as String?)?.trim()?.lowercase()
  if (!explicit.isNullOrBlank()) {
    if (explicit !in host.nativePackageTypes) {
      throw GradleException("Unsupported native package type '$explicit' for ${host.osId}. Supported values: ${host.nativePackageTypes.joinToString(", ")}.")
    }
    return explicit
  }
  return host.defaultNativePackageType
}

fun gameBundledDistName(): String {
  val host = currentPackageHost()
  return "${sanitizeGameName(gameDisplayName())}-${sanitizeGameName(gameVersion())}-${host.target.id}-runtime"
}

fun gameNativeArtifactStem(packageType: String): String {
  val host = currentPackageHost()
  return "${sanitizeGameName(gameDisplayName())}-${sanitizeGameName(nativeGameVersion())}-${host.target.id}-$packageType"
}

fun jpackageAppName(): String {
  val fromProfile = selectedReleaseProfile().value("appName")
  return if (!fromProfile.isNullOrBlank()) fromProfile else gameDisplayName()
}

fun bundledRuntimeMetadata(): String {
  val profile = selectedReleaseProfile()
  return """
    |JVN Game Bundled Runtime Build
    |builtAt=${Instant.now()}
    |target=${currentPackageHost().target.id}
    |distName=${gameBundledDistName()}
    |gameName=${gameDisplayName()}
    |gameVersion=${gameVersion()}
    |nativeVersion=${nativeGameVersion()}
    |releaseProfile=${profile.name}
    |releaseConfig=${profile.file?.absolutePath ?: "(none)"}
    |runtimeRequirement=Bundled Java runtime image
    |
  """.trimMargin()
}

fun nativeBuildMetadata(packageType: String): String {
  val profile = selectedReleaseProfile()
  return """
    |JVN Game Native Package
    |builtAt=${Instant.now()}
    |target=${currentPackageHost().target.id}
    |packageType=$packageType
    |gameName=${gameDisplayName()}
    |gameVersion=${gameVersion()}
    |nativeVersion=${nativeGameVersion()}
    |releaseProfile=${profile.name}
    |releaseConfig=${profile.file?.absolutePath ?: "(none)"}
    |
  """.trimMargin()
}

fun sanitizeGameName(raw: String?): String {
  val sanitized = (raw ?: "")
    .trim()
    .replace(Regex("[^A-Za-z0-9._-]+"), "-")
    .trim('-', '.', '_')
  return sanitized.ifBlank { "jvn-game" }
}

fun gameDisplayName(): String {
  val explicit = (findProperty("jvnGameName") as String?)?.trim()
  if (!explicit.isNullOrBlank()) return explicit
  val manifestName = gameManifest().getProperty("name", "").trim()
  if (manifestName.isNotBlank()) return manifestName
  return gameProjectDir().name
}

fun gameVersion(): String {
  val explicit = (findProperty("jvnGameVersion") as String?)?.trim()
  if (!explicit.isNullOrBlank()) return explicit
  val manifest = gameManifest()
  return listOf("version", "releaseVersion", "build.version")
    .map { manifest.getProperty(it, "").trim() }
    .firstOrNull { it.isNotBlank() }
    ?: project.version.toString()
}

fun gameDistName(target: JvnGameTarget): String {
  return "${sanitizeGameName(gameDisplayName())}-${sanitizeGameName(gameVersion())}-${target.id}"
}

fun gameLauncherBaseName(): String {
  return sanitizeGameName(gameDisplayName()).lowercase()
}

fun shQuote(value: String): String {
  return "'" + value.replace("'", "'\"'\"'") + "'"
}

fun batQuote(value: String): String {
  return "\"" + value.replace("\"", "") + "\""
}

fun gameLauncherStaticRuntimeArgs(): List<String> {
  val validation = validateGameProject()
  return when (validation.type) {
    "vn" -> validation.entryKey?.let { listOf("--script", it) } ?: emptyList()
    "jes" -> validation.entryKey?.let { listOf("--jes", it) } ?: emptyList()
    else -> emptyList()
  }
}

fun gameLauncherArgsUnix(dollar: String): String {
  val args = gameLauncherStaticRuntimeArgs()
  return buildString {
    append("    |  --assets \"${dollar}APP_HOME/game\" \\\n")
    args.forEach { arg ->
      append("    |  ${shQuote(arg)} \\\n")
    }
    append("    |  \"${dollar}@\"")
  }
}

fun gameLauncherExtraArgsWindows(): String {
  val args = gameLauncherStaticRuntimeArgs()
  return args.joinToString(separator = "") { arg -> " ${batQuote(arg)}" }
}

fun gameScriptUnix(): String {
  val dollar = "$"
  val launcherArgs = gameLauncherArgsUnix(dollar)
  return """
    |#!/usr/bin/env sh
    |set -eu
    |APP_HOME=${dollar}(CDPATH= cd -- "${dollar}(dirname -- "${dollar}0")/.." && pwd)
    |if [ ! -f "${dollar}APP_HOME/game/jvn.project" ]; then
    |  echo "JVN launcher error: bundled game/jvn.project is missing." >&2
    |  exit 1
    |fi
    |if [ -n "${dollar}{JAVA_HOME:-}" ] && [ -x "${dollar}JAVA_HOME/bin/java" ]; then
    |  JAVA_EXE="${dollar}JAVA_HOME/bin/java"
    |else
    |  JAVA_EXE="java"
    |fi
    |if ! "${dollar}JAVA_EXE" -version >/dev/null 2>&1; then
    |  echo "JVN launcher error: Java 21 or newer is required. Set JAVA_HOME or add java to PATH." >&2
    |  exit 1
    |fi
    |JAVA_SPEC=$("${dollar}JAVA_EXE" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print ${dollar}2; exit}')
    |JAVA_MAJOR=${dollar}{JAVA_SPEC%%.*}
    |if [ "${dollar}JAVA_MAJOR" = "1" ]; then
    |  JAVA_MAJOR=${dollar}(printf '%s' "${dollar}JAVA_SPEC" | awk -F. '{print ${dollar}2}')
    |fi
    |if [ -n "${dollar}{JAVA_MAJOR:-}" ] && [ "${dollar}JAVA_MAJOR" -lt 21 ] 2>/dev/null; then
    |  echo "JVN launcher error: Java 21 or newer is required. Found Java ${dollar}JAVA_SPEC." >&2
    |  exit 1
    |fi
    |exec "${dollar}JAVA_EXE" \
    |  --module-path "${dollar}APP_HOME/lib/javafx" \
    |  --add-modules ${jvnJavaFxRuntimeModules.joinToString(",")} \
    |  -cp "${dollar}APP_HOME/lib/*" \
    |  com.jvn.runtime.JvnApp \
$launcherArgs
    |
  """.trimMargin()
}

fun gameScriptWindows(): String {
  val extraArgs = gameLauncherExtraArgsWindows()
  return """
    |@echo off
    |setlocal
    |set "APP_HOME=%~dp0.."
    |if not exist "%APP_HOME%\game\jvn.project" (
    |  echo JVN launcher error: bundled game\jvn.project is missing.
    |  exit /b 1
    |)
    |if defined JAVA_HOME (
    |  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    |) else (
    |  set "JAVA_EXE=java"
    |)
    |"%JAVA_EXE%" -version >nul 2>&1
    |if errorlevel 1 (
    |  echo JVN launcher error: Java 21 or newer is required. Set JAVA_HOME or add java to PATH.
    |  exit /b 1
    |)
    |"%JAVA_EXE%" --module-path "%APP_HOME%\lib\javafx" --add-modules ${jvnJavaFxRuntimeModules.joinToString(",")} -cp "%APP_HOME%\lib\*" com.jvn.runtime.JvnApp --assets "%APP_HOME%\game"$extraArgs %*
    |exit /b %ERRORLEVEL%
    |
  """.trimMargin()
}

fun gameReadme(target: JvnGameTarget, distName: String, launcherName: String): String {
  val launchExamples = if (target.windows) {
    """
      |  bin\$launcherName.bat
    """.trimMargin()
  } else {
    """
      |  bin/$launcherName
    """.trimMargin()
  }
  return """
    |$distName
    |
    |Portable JVN game build for ${target.id}.
    |
    |Requirements:
    |  Java 21 or newer on PATH, or JAVA_HOME pointing at a Java 21+ runtime.
    |
    |Launch:
    |$launchExamples
    |
    |Contents:
    |  bin/         game launcher
    |  game/        bundled JVN project files
    |  lib/         JVN runtime jars and third-party dependencies
    |  lib/javafx/  JavaFX native jars for ${target.javafxClassifier}
    |  BUILD-METADATA.txt package metadata for this build
    |
    |The launcher starts com.jvn.runtime.JvnApp with --assets pointing at the bundled
    |game folder. Runtime options from jvn.project, including entryVns, width,
    |height, runtime.ui, runtime.audio, and runtime.locale, are read at launch.
    |
  """.trimMargin()
}

fun gameBuildMetadata(target: JvnGameTarget, distName: String): String {
  val validation = validateGameProject()
  val manifest = validation.manifest
  return """
    |JVN Game Portable Build
    |builtAt=${Instant.now()}
    |target=${target.id}
    |distName=$distName
    |gameName=${gameDisplayName()}
    |gameVersion=${gameVersion()}
    |projectType=${validation.type}
    |entry=${validation.entryKey ?: "(runtime discovery)"}
    |runtimeRequirement=Java 21+
    |runtimeModules=${jvnGameRuntimeProjectPaths.joinToString(",")}
    |javafxVersion=$jvnJavaFxVersion
    |javafxClassifier=${target.javafxClassifier}
    |runtime.ui=${manifest.getProperty("runtime.ui", "fx")}
    |runtime.audio=${manifest.getProperty("runtime.audio", "auto")}
    |runtime.locale=${manifest.getProperty("runtime.locale", "en")}
    |warnings=${validation.warnings.joinToString(" | ").ifBlank { "none" }}
    |
  """.trimMargin()
}

fun externalRuntimeJars(): List<File> {
  val runtimeRuntime = project(":runtime").configurations.getByName("runtimeClasspath").resolve()
  return runtimeRuntime
    .filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
    .filterNot { it.name.startsWith("javafx-") }
    .distinctBy { it.name }
    .sortedBy { it.name }
}

val gameZipTasks = jvnGameTargets.map { target ->
  val javaFxConfiguration = configurations.create("jvn${target.taskSuffix}JavaFx") {
    isCanBeResolved = true
    isCanBeConsumed = false
    description = "Target JavaFX runtime jars for ${target.id} portable JVN game builds."
  }
  jvnGameJavaFxConfigurations[target.id] = javaFxConfiguration
  jvnJavaFxModules.forEach { moduleName ->
    dependencies.add(javaFxConfiguration.name, "org.openjfx:$moduleName:$jvnJavaFxVersion:${target.javafxClassifier}")
  }

  val generatedDir = layout.buildDirectory.dir("generated/jvnGamePortable/${target.id}")
  val prepareTask = tasks.register("prepareJvnGamePortable${target.taskSuffix}") {
    group = "distribution"
    description = "Generates launcher scripts for the ${target.id} portable JVN game build."
    outputs.dir(generatedDir)
    outputs.upToDateWhen { false }
    doLast {
      val validation = validateGameProject()
      validation.warnings.forEach { warning -> logger.warn("JVN game project warning: $warning") }
      val distName = gameDistName(target)
      val launcherName = gameLauncherBaseName()
      val rootDir = generatedDir.get().asFile
      val binDir = rootDir.resolve("bin")
      rootDir.deleteRecursively()
      binDir.mkdirs()
      val scriptFile = binDir.resolve(if (target.windows) "$launcherName.bat" else launcherName)
      scriptFile.writeText(if (target.windows) gameScriptWindows() else gameScriptUnix())
      if (!target.windows) scriptFile.setExecutable(true)
      rootDir.resolve("README.txt").writeText(gameReadme(target, distName, launcherName))
      rootDir.resolve("BUILD-METADATA.txt").writeText(gameBuildMetadata(target, distName))
    }
  }

  tasks.register<Zip>("assembleJvnGamePortable${target.taskSuffix}") {
    group = "distribution"
    description = "Assembles a portable JVN game zip for ${target.id}. Requires -PjvnGameProject=/path/to/game."
    archiveBaseName.set(providers.provider { sanitizeGameName(gameDisplayName()) })
    archiveVersion.set(providers.provider { sanitizeGameName(gameVersion()) })
    archiveClassifier.set(target.id)
    destinationDirectory.set(layout.buildDirectory.dir("distributions/games"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    dependsOn("validateJvnGameProject")
    dependsOn(prepareTask)
    jvnGameRuntimeProjectPaths.forEach { projectPath ->
      dependsOn("$projectPath:jar")
    }

    from(prepareTask.map { generatedDir.get().asFile }) {
      into(providers.provider { gameDistName(target) })
    }
    from({
      jvnGameRuntimeProjectPaths.map { projectPath ->
        project(projectPath).tasks.named<Jar>("jar").get().archiveFile.get().asFile
      }
    }) {
      into(providers.provider { "${gameDistName(target)}/lib" })
    }
    from({ externalRuntimeJars() }) {
      into(providers.provider { "${gameDistName(target)}/lib" })
    }
    from(javaFxConfiguration) {
      include("javafx-*-${target.javafxClassifier}.jar")
      into(providers.provider { "${gameDistName(target)}/lib/javafx" })
    }
    from({ gameProjectDir() }) {
      into(providers.provider { "${gameDistName(target)}/game" })
      exclude(
        ".git/**",
        ".gradle/**",
        ".jvn-gradle-user-home/**",
        "build/**",
        "out/**",
        "dist/**",
        "save/**",
        "saves/**",
        "logs/**",
        ".idea/**",
        ".vscode/**",
        "__MACOSX/**",
        "**/*.log",
        "**/*.tmp",
        "**/Icon\r",
        "**/.DS_Store",
        "**/Thumbs.db"
      )
    }
  }
}

tasks.register("validateJvnGameProject") {
  group = "verification"
  description = "Validates the JVN game project selected with -PjvnGameProject."
  doLast {
    val validation = validateGameProject()
    val dir = validation.dir
    val manifest = validation.manifest
    println("JVN game project: ${dir.absolutePath}")
    println("  name: ${gameDisplayName()}")
    println("  version: ${gameVersion()}")
    println("  type: ${validation.type}")
    println("  entry: ${validation.entryKey ?: "(runtime discovery)"}")
    println("  runtime.ui: ${manifest.getProperty("runtime.ui", "fx")}")
    println("  runtime.audio: ${manifest.getProperty("runtime.audio", "auto")}")
    if (validation.warnings.isEmpty()) {
      println("  warnings: none")
    } else {
      println("  warnings:")
      validation.warnings.forEach { println("    - $it") }
    }
  }
}

tasks.register("assembleJvnGamePortableCurrent") {
  group = "distribution"
  description = "Assembles a portable JVN game zip for the current host platform."
  dependsOn(tasks.named("assembleJvnGamePortable${currentGameTarget().taskSuffix}"))
}

tasks.register("assembleJvnGamePortableAll") {
  group = "distribution"
  description = "Assembles portable JVN game zips for Windows, Linux, and macOS targets."
  dependsOn(gameZipTasks)
}

tasks.register("assembleJvnGamePortable") {
  group = "distribution"
  description = "Assembles portable JVN game zips for every supported target platform."
  dependsOn(tasks.named("assembleJvnGamePortableAll"))
}

tasks.register("printJvnGamePortableTargets") {
  group = "help"
  description = "Prints supported portable JVN game build targets."
  doLast {
    println("Supported portable JVN game targets:")
    jvnGameTargets.forEach { target ->
      println("  ${target.id} (JavaFX classifier: ${target.javafxClassifier})")
    }
  }
}

allprojects {
  repositories {
    mavenLocal {
      content {
        excludeGroup("org.openjfx")
      }
    }
    mavenCentral()
  }
}

subprojects {
  apply(plugin = "java")
  apply(plugin = "maven-publish")

  group = jvnGroup
  version = jvnVersion

  java {
    toolchain {
      languageVersion.set(JavaLanguageVersion.of(configuredJavaVersion))
    }
  }

  tasks.test {
    useJUnitPlatform()
  }

  tasks.withType<Test>().configureEach {
  }

  dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("org.slf4j:slf4j-api:2.0.13")
  }

  configurations.all {
    resolutionStrategy.dependencySubstitution {
      substitute(module("com.jvn:core")).using(project(":core"))
      substitute(module("com.jvn:fx")).using(project(":fx"))
      substitute(module("com.jvn:scripting")).using(project(":scripting"))
      substitute(module("com.jvn:audio")).using(project(":audio"))
    }
    // Force consistent logback to avoid mixed versions at runtime
    // (e.g. from transitive dependencies of JUnit or other libraries)
    // Note: we use 1.5.6 which is the latest as of mid-2024, but this may need to be updated in the future
    resolutionStrategy.force(
      "ch.qos.logback:logback-classic:1.5.6",
      "ch.qos.logback:logback-core:1.5.6"
    )
  }

  extensions.configure<org.gradle.api.publish.PublishingExtension> {
    publications {
      create("mavenJava", org.gradle.api.publish.maven.MavenPublication::class.java) {
        from(components["java"])
      }
    }
  }
}
