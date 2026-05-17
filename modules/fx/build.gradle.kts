plugins {
  `java-library`
  id("net.ltgt.errorprone") version "4.0.1"
}

dependencies {
  api(project(":core"))
  api(project(":render-api"))

  val javafxVersion = (rootProject.findProperty("jvnJavaFxVersion") as String?)?.trim()?.ifBlank { null } ?: "21.0.3"
  val osName = System.getProperty("os.name").lowercase()
  val arch = System.getProperty("os.arch").lowercase()
  val platform = when {
    osName.contains("win") && arch.contains("64") -> "win"
    osName.contains("linux") && arch.contains("aarch64") -> "linux-aarch64"
    osName.contains("linux") -> "linux"
    osName.contains("mac") && arch.contains("aarch64") -> "mac-aarch64"
    osName.contains("mac") -> "mac"
    else -> throw GradleException("Unsupported OS/Arch for JavaFX: $osName/$arch")
  }

  api("org.openjfx:javafx-base:$javafxVersion:$platform")
  api("org.openjfx:javafx-graphics:$javafxVersion:$platform")
  api("org.openjfx:javafx-controls:$javafxVersion:$platform")
  api("org.openjfx:javafx-media:$javafxVersion:$platform")
  api("org.openjfx:javafx-swing:$javafxVersion:$platform")

  errorprone("com.google.errorprone:error_prone_core:2.28.0")
  errorprone("com.uber.nullaway:nullaway:0.11.0")
}

tasks.withType<JavaCompile>().configureEach {
  (options as org.gradle.api.plugins.ExtensionAware).extensions
    .findByType(net.ltgt.gradle.errorprone.ErrorProneOptions::class.java)
    ?.also { ep ->
      ep.disableAllChecks.set(true)
      ep.check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.WARN)
      ep.option("NullAway:AnnotatedPackages", "com.jvn.fx")
    }
}
