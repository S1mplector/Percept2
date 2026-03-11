import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import java.io.ByteArrayOutputStream
import java.io.File

plugins {
  java
}

val configuredJavaVersion = (findProperty("javaVersion") as String?)?.toIntOrNull() ?: 21
val overriddenNativeJavaHome = providers.gradleProperty("jvnNativeJavaHome").orNull?.trim()?.takeIf { it.isNotEmpty() }

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(configuredJavaVersion))
  }
}

allprojects {
  repositories {
    mavenLocal()
    mavenCentral()
  }
}

val osName = System.getProperty("os.name", "").lowercase()
val isWindows = osName.contains("win")
val isMac = osName.contains("mac")
val nativeBuildDir = layout.projectDirectory.dir("native-math/build").asFile
val nativeReleaseDir = layout.projectDirectory.dir("native-math/build/Release").asFile
val nativeDebugDir = layout.projectDirectory.dir("native-math/build/Debug").asFile

val simjotLibName = when {
  isWindows -> "simjot_native.dll"
  isMac -> "libsimjot_native.dylib"
  else -> "libsimjot_native.so"
}
val jvnBridgeLibName = when {
  isWindows -> "jvn_native_bridge.dll"
  isMac -> "libjvn_native_bridge.dylib"
  else -> "libjvn_native_bridge.so"
}

fun libExists(libName: String): Boolean =
  listOf(
    nativeBuildDir.resolve(libName),
    nativeReleaseDir.resolve(libName),
    nativeDebugDir.resolve(libName)
  ).any { it.exists() }

fun resolveNativeBridgePath(): String =
  listOf(
    nativeBuildDir.resolve(jvnBridgeLibName),
    nativeReleaseDir.resolve(jvnBridgeLibName),
    nativeDebugDir.resolve(jvnBridgeLibName)
  ).firstOrNull { it.exists() }?.absolutePath
    ?: nativeBuildDir.resolve(jvnBridgeLibName).absolutePath

fun nativeMathCacheFile() = nativeBuildDir.resolve("CMakeCache.txt")

fun configuredNativeMathJavaHome(): String? =
  nativeMathCacheFile()
    .takeIf { it.exists() }
    ?.useLines { lines ->
      lines
        .firstOrNull { it.startsWith("JAVA_HOME:") }
        ?.substringAfter("=")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    }

fun canonicalPath(path: String): String = File(path).canonicalFile.absolutePath

fun javaHomeReleaseVersion(javaHome: String): String? {
  val releaseFile = File(javaHome, "release")
  if (!releaseFile.exists()) return null
  return releaseFile.useLines { lines ->
    lines
      .firstOrNull { it.startsWith("JAVA_VERSION=") }
      ?.substringAfter("=")
      ?.trim()
      ?.trim('"')
      ?.takeIf { it.isNotEmpty() }
  }
}

fun javaFeatureVersion(version: String?): Int? {
  if (version == null || version.isBlank()) return null
  val normalized = version.trim()
  val withoutLegacyPrefix = if (normalized.startsWith("1.")) normalized.substring(2) else normalized
  val digits = withoutLegacyPrefix.takeWhile { it.isDigit() }
  return digits.toIntOrNull()
}

fun nativeMathCacheMatches(expectedJavaHome: String): Boolean {
  val configuredJavaHome = configuredNativeMathJavaHome() ?: return false
  val cachedPath = canonicalPath(configuredJavaHome)
  val expectedPath = canonicalPath(expectedJavaHome)
  if (cachedPath == expectedPath) return true

  // Avoid unnecessary native rebuilds when only the resolved JDK path changes
  // (e.g., different Gradle user homes) but the Java feature version remains the same.
  val cachedFeature = javaFeatureVersion(javaHomeReleaseVersion(cachedPath))
  val expectedFeature = javaFeatureVersion(javaHomeReleaseVersion(expectedPath))
  return cachedFeature != null && expectedFeature != null && cachedFeature == expectedFeature
}

fun cmakeAvailable(project: Project): Boolean = try {
  val out = ByteArrayOutputStream()
  project.exec {
    commandLine("cmake", "--version")
    standardOutput = out
    errorOutput = out
    isIgnoreExitValue = true
  }.exitValue == 0
} catch (_: Exception) {
  false
}

val skipNativeMathBuild = providers.gradleProperty("skipNativeMathBuild")
  .map { it.equals("true", ignoreCase = true) }
  .orElse(false)

val rootToolchainLauncher = javaToolchains.launcherFor {
  languageVersion.set(JavaLanguageVersion.of(configuredJavaVersion))
}

fun resolveNativeMathJavaHome(): String =
  overriddenNativeJavaHome?.let(::canonicalPath)
    ?: rootToolchainLauncher.get().metadata.installationPath.asFile.absolutePath

tasks.register("buildNativeMathIfNeeded") {
  group = "build"
  description = "Build native-math libraries via CMake when required outputs are missing."

  doLast {
    if (skipNativeMathBuild.get()) {
      logger.lifecycle("Skipping native-math build because -PskipNativeMathBuild=true")
      return@doLast
    }

    val javaHome = resolveNativeMathJavaHome()
    val cacheMatchesToolchain = nativeMathCacheMatches(javaHome)
    if (nativeBuildDir.exists() && !cacheMatchesToolchain) {
      logger.lifecycle(
        "native-math cache targets a different JDK (cached={}, expected={}); rebuilding",
        configuredNativeMathJavaHome() ?: "<missing>",
        javaHome
      )
      nativeBuildDir.deleteRecursively()
    }

    val simjotExists = libExists(simjotLibName)
    val bridgeExists = libExists(jvnBridgeLibName)
    if (simjotExists && bridgeExists && cacheMatchesToolchain) {
      logger.lifecycle("native-math already built: {} and {}", simjotLibName, jvnBridgeLibName)
      return@doLast
    }

    if (!cmakeAvailable(project)) {
      throw GradleException(
        "CMake is required to build native-math. Install CMake and a C/C++ toolchain, " +
          "or run with -PskipNativeMathBuild=true to bypass native build."
      )
    }

    logger.lifecycle("Building native-math (missing: simjot={}, bridge={})", !simjotExists, !bridgeExists)

    val configureArgs = listOf(
      "cmake", "-S", "native-math", "-B", "native-math/build",
      "-DCMAKE_BUILD_TYPE=Release",
      "-DSIMJOT_NATIVE_BUILD_TESTS=OFF",
      "-DJVN_BUILD_JNI_BRIDGE=ON",
      "-DJAVA_HOME=$javaHome"
    )
    exec {
      environment("JAVA_HOME", javaHome)
      commandLine(configureArgs)
    }

    if (isWindows) {
      exec {
        environment("JAVA_HOME", javaHome)
        commandLine("cmake", "--build", "native-math/build", "--config", "Release", "--parallel")
      }
    } else {
      exec {
        environment("JAVA_HOME", javaHome)
        commandLine("cmake", "--build", "native-math/build", "--parallel")
      }
    }

    val postSimjot = libExists(simjotLibName)
    val postBridge = libExists(jvnBridgeLibName)
    if (!postSimjot || !postBridge || !nativeMathCacheMatches(javaHome)) {
      throw GradleException(
        "native-math build completed but required outputs are missing: " +
          "$simjotLibName=$postSimjot, $jvnBridgeLibName=$postBridge, JAVA_HOME=${configuredNativeMathJavaHome() ?: "<missing>"}"
      )
    }
  }
}

tasks.named("build") {
  dependsOn("buildNativeMathIfNeeded")
}

subprojects {
  apply(plugin = "java")
  apply(plugin = "maven-publish")

  group = (findProperty("jvnGroup") as String?) ?: "com.jvn"
  version = (findProperty("jvnVersion") as String?) ?: "0.1-SNAPSHOT"

  java {
    toolchain {
      languageVersion.set(JavaLanguageVersion.of(configuredJavaVersion))
    }
  }

  tasks.test {
    useJUnitPlatform()
  }

  tasks.withType<Test>().configureEach {
    dependsOn(rootProject.tasks.named("buildNativeMathIfNeeded"))
    doFirst {
      systemProperty("jvn.native.path.jvn_native_bridge", resolveNativeBridgePath())
    }
  }

  dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    implementation("org.slf4j:slf4j-api:2.0.13")
  }

  configurations.all {
    resolutionStrategy.dependencySubstitution {
      substitute(module("com.jvn:core")).using(project(":core"))
      substitute(module("com.jvn:audio-fx")).using(project(":audio-fx"))
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

gradle.projectsEvaluated {
  rootProject.allprojects.forEach { target ->
    target.tasks.withType<JavaExec>().configureEach {
      dependsOn(rootProject.tasks.named("buildNativeMathIfNeeded"))
      doFirst {
        systemProperty("jvn.native.path.jvn_native_bridge", resolveNativeBridgePath())
      }
    }
  }
}
