plugins {
  `java-library`
  id("net.ltgt.errorprone") version "4.0.1"
}

sourceSets {
  main {
    java {
      setSrcDirs(
          listOf(
              "src/main/java",
              "simp3/src/main/java"
          )
      )
      include(
          "com/jvn/audio/**",
          "com/musicplayer/**"
      )
    }
    resources {
      setSrcDirs(
          listOf(
              "src/main/resources",
              "simp3/src/main/resources"
          )
      )
      // A library must not contribute a root Logback configuration to consuming applications.
      exclude("logback.xml")
    }
  }
}

val javafxVersion = (rootProject.findProperty("jvnJavaFxVersion") as String?)?.trim()?.ifBlank { null } ?: "23.0.1"
val osName = System.getProperty("os.name").lowercase()
val arch = System.getProperty("os.arch").lowercase()
val platform = when {
  osName.contains("win") && arch.contains("64") -> "win"
  osName.contains("linux") && (arch.contains("aarch64") || arch.contains("arm64")) -> "linux-aarch64"
  osName.contains("linux") -> "linux"
  osName.contains("mac") && (arch.contains("aarch64") || arch.contains("arm64")) -> "mac-aarch64"
  osName.contains("mac") -> "mac"
  else -> "win"
}

dependencies {
  api(project(":core"))
  implementation(project(":fx"))
  implementation("com.googlecode.soundlibs:basicplayer:3.0.0.0")
  implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
  implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
  implementation("org.jflac:jflac-codec:1.5.2")
  implementation("org.openjfx:javafx-fxml:$javafxVersion:$platform")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")
  implementation("net.jthink:jaudiotagger:3.0.1")

  errorprone("com.google.errorprone:error_prone_core:2.28.0")
  errorprone("com.uber.nullaway:nullaway:0.11.0")
}

tasks.withType<JavaCompile>().configureEach {
  (options as org.gradle.api.plugins.ExtensionAware).extensions
    .findByType(net.ltgt.gradle.errorprone.ErrorProneOptions::class.java)
    ?.also { ep ->
      ep.disableAllChecks.set(true)
      ep.check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.WARN)
      ep.option("NullAway:AnnotatedPackages", "com.jvn.audio")
    }
}
