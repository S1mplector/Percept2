import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import java.io.ByteArrayOutputStream
import java.io.File

plugins {
  `java-library`
}

dependencies {
  api(project(":core"))
}

val osName = System.getProperty("os.name", "").lowercase()
val isWindows = osName.contains("win")
val osDir = when {
  osName.contains("mac") -> "mac"
  osName.contains("win") -> "windows"
  else -> "linux"
}
val nativeLibName = when {
  isWindows -> "jvn_audiofx_native.dll"
  osName.contains("mac") -> "libjvn_audiofx_native.dylib"
  else -> "libjvn_audiofx_native.so"
}
val nativeBuildDir = layout.buildDirectory.dir("native")

fun audioFxNativeCacheFile(): File = nativeBuildDir.get().asFile.resolve("CMakeCache.txt")

fun canonicalPath(path: String): String = File(path).canonicalFile.absolutePath

fun configuredAudioFxCacheValue(key: String): String? =
  audioFxNativeCacheFile()
    .takeIf { it.exists() }
    ?.useLines { lines ->
      lines
        .firstOrNull { it.startsWith("$key=") }
        ?.substringAfter("=")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    }

fun configuredAudioFxSourceDir(): String? =
  configuredAudioFxCacheValue("CMAKE_HOME_DIRECTORY:INTERNAL")

fun configuredAudioFxBuildDir(): String? =
  configuredAudioFxCacheValue("CMAKE_CACHEFILE_DIR:INTERNAL")

fun audioFxCacheMatches(sourceDir: String, buildDir: String): Boolean {
  val cachedSourceDir = configuredAudioFxSourceDir() ?: return false
  val cachedBuildDir = configuredAudioFxBuildDir() ?: return false
  return canonicalPath(cachedSourceDir) == canonicalPath(sourceDir)
    && canonicalPath(cachedBuildDir) == canonicalPath(buildDir)
}

fun audioFxNativeCandidates(): List<java.io.File> {
  val base = nativeBuildDir.get().asFile
  return listOf(
    base.resolve(nativeLibName),
    base.resolve("Release/$nativeLibName"),
    base.resolve("Debug/$nativeLibName"),
    base.resolve("$osDir/$nativeLibName"),
    base.resolve("$osDir/Release/$nativeLibName"),
    base.resolve("$osDir/Debug/$nativeLibName")
  )
}

fun resolveAudioFxNativePath(): String =
  audioFxNativeCandidates().firstOrNull { it.exists() }?.absolutePath
    ?: nativeBuildDir.get().file(nativeLibName).asFile.absolutePath

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

val skipAudioFxNativeBuild = providers.gradleProperty("skipAudioFxNativeBuild")
  .map { it.equals("true", ignoreCase = true) }
  .orElse(false)

val toolchainLauncher = javaToolchains.launcherFor {
  languageVersion.set(java.toolchain.languageVersion)
}

val buildAudioFxNativeIfNeeded = tasks.register("buildAudioFxNativeIfNeeded") {
  group = "build"
  description = "Build the audio-fx JNI bridge and native synth backends via CMake."

  doLast {
    if (skipAudioFxNativeBuild.get()) {
      logger.lifecycle("Skipping audio-fx native build because -PskipAudioFxNativeBuild=true")
      return@doLast
    }

    if (!cmakeAvailable(project)) {
      throw GradleException(
        "CMake is required to build audio-fx native backends. Install CMake and a C/C++ toolchain, " +
          "or run with -PskipAudioFxNativeBuild=true to bypass native synthesis."
      )
    }

    val javaHome = toolchainLauncher.get().metadata.installationPath.asFile.absolutePath
    val sourceDir = project.file("native").absolutePath
    val buildDirFile = nativeBuildDir.get().asFile
    if (buildDirFile.exists() && !audioFxCacheMatches(sourceDir, buildDirFile.absolutePath)) {
      logger.lifecycle(
        "audio-fx native cache targets a different source/build directory (cachedSource={}, cachedBuild={}, expectedSource={}, expectedBuild={}); rebuilding",
        configuredAudioFxSourceDir() ?: "<missing>",
        configuredAudioFxBuildDir() ?: "<missing>",
        sourceDir,
        buildDirFile.absolutePath
      )
      buildDirFile.deleteRecursively()
    }
    buildDirFile.mkdirs()

    exec {
      commandLine(
        "cmake",
        "-S", sourceDir,
        "-B", buildDirFile.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DJAVA_HOME=$javaHome"
      )
    }

    if (isWindows) {
      exec { commandLine("cmake", "--build", buildDirFile.absolutePath, "--config", "Release", "--parallel") }
    } else {
      exec { commandLine("cmake", "--build", buildDirFile.absolutePath, "--parallel") }
    }

    val built = audioFxNativeCandidates().firstOrNull { it.exists() }
    if (built == null) {
      throw GradleException("audio-fx native build completed but $nativeLibName was not produced")
    }
  }
}

val runAudioFxNativeTests = tasks.register("runAudioFxNativeTests") {
  group = "verification"
  description = "Run native audio-fx math/regression tests via CTest."
  dependsOn(buildAudioFxNativeIfNeeded)

  doLast {
    if (skipAudioFxNativeBuild.get()) {
      logger.lifecycle("Skipping audio-fx native tests because -PskipAudioFxNativeBuild=true")
      return@doLast
    }

    val buildDirFile = nativeBuildDir.get().asFile
    if (isWindows) {
      exec {
        commandLine("ctest", "--test-dir", buildDirFile.absolutePath, "--output-on-failure", "-C", "Release")
      }
    } else {
      exec {
        commandLine("ctest", "--test-dir", buildDirFile.absolutePath, "--output-on-failure")
      }
    }
  }
}

tasks.named("build") {
  dependsOn(buildAudioFxNativeIfNeeded)
}

tasks.withType<Test>().configureEach {
  dependsOn(buildAudioFxNativeIfNeeded)
  dependsOn(runAudioFxNativeTests)
  doFirst {
    systemProperty("jvn.native.path.jvn_audiofx_native", resolveAudioFxNativePath())
  }
}

gradle.projectsEvaluated {
  rootProject.allprojects.forEach { target ->
    target.tasks.withType<JavaExec>().configureEach {
      dependsOn(buildAudioFxNativeIfNeeded)
      doFirst {
        systemProperty("jvn.native.path.jvn_audiofx_native", resolveAudioFxNativePath())
      }
    }
  }
}
