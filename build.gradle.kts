import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.ByteArrayOutputStream

plugins {
  java
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

tasks.register("buildNativeMathIfNeeded") {
  group = "build"
  description = "Build native-math libraries via CMake when required outputs are missing."

  doLast {
    if (skipNativeMathBuild.get()) {
      logger.lifecycle("Skipping native-math build because -PskipNativeMathBuild=true")
      return@doLast
    }

    val simjotExists = libExists(simjotLibName)
    val bridgeExists = libExists(jvnBridgeLibName)
    if (simjotExists && bridgeExists) {
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
      "-DJVN_BUILD_JNI_BRIDGE=ON"
    )
    exec { commandLine(configureArgs) }

    if (isWindows) {
      exec { commandLine("cmake", "--build", "native-math/build", "--config", "Release", "--parallel") }
    } else {
      exec { commandLine("cmake", "--build", "native-math/build", "--parallel") }
    }

    val postSimjot = libExists(simjotLibName)
    val postBridge = libExists(jvnBridgeLibName)
    if (!postSimjot || !postBridge) {
      throw GradleException(
        "native-math build completed but required outputs are missing: " +
          "$simjotLibName=$postSimjot, $jvnBridgeLibName=$postBridge"
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
      languageVersion.set(JavaLanguageVersion.of((findProperty("javaVersion") as String?)?.toIntOrNull() ?: 21))
    }
  }

  tasks.test {
    useJUnitPlatform()
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
