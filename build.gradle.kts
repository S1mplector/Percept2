import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
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

data class JvnGameTarget(
  val id: String,
  val taskSuffix: String,
  val javafxClassifier: String,
  val windows: Boolean
)

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

fun gameScriptUnix(): String {
  val dollar = "$"
  return """
    |#!/usr/bin/env sh
    |set -eu
    |APP_HOME=${dollar}(CDPATH= cd -- "${dollar}(dirname -- "${dollar}0")/.." && pwd)
    |if [ -n "${dollar}{JAVA_HOME:-}" ] && [ -x "${dollar}JAVA_HOME/bin/java" ]; then
    |  JAVA_EXE="${dollar}JAVA_HOME/bin/java"
    |else
    |  JAVA_EXE="java"
    |fi
    |exec "${dollar}JAVA_EXE" \
    |  --module-path "${dollar}APP_HOME/lib/javafx" \
    |  --add-modules ${jvnJavaFxRuntimeModules.joinToString(",")} \
    |  -cp "${dollar}APP_HOME/lib/*" \
    |  com.jvn.runtime.JvnApp \
    |  --assets "${dollar}APP_HOME/game" \
    |  "${dollar}@"
    |
  """.trimMargin()
}

fun gameScriptWindows(): String {
  return """
    |@echo off
    |setlocal
    |set "APP_HOME=%~dp0.."
    |if defined JAVA_HOME (
    |  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    |) else (
    |  set "JAVA_EXE=java"
    |)
    |"%JAVA_EXE%" --module-path "%APP_HOME%\lib\javafx" --add-modules ${jvnJavaFxRuntimeModules.joinToString(",")} -cp "%APP_HOME%\lib\*" com.jvn.runtime.JvnApp --assets "%APP_HOME%\game" %*
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
    |
    |The launcher starts com.jvn.runtime.JvnApp with --assets pointing at the bundled
    |game folder. Runtime options from jvn.project, including entryVns, width,
    |height, runtime.ui, runtime.audio, and runtime.locale, are read at launch.
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
    val dir = gameProjectDir()
    val manifest = gameManifest()
    val type = manifest.getProperty("type", "vn").trim()
    println("JVN game project: ${dir.absolutePath}")
    println("  name: ${gameDisplayName()}")
    println("  version: ${gameVersion()}")
    println("  type: $type")
    println("  entryVns: ${manifest.getProperty("entryVns", "(auto)")}")
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
