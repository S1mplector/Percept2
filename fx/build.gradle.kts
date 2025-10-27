plugins {
  `java-library`
}

dependencies {
  api(project(":core"))

  val javafxVersion = "21.0.3"
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
}
