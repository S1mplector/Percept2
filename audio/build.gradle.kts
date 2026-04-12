plugins {
  `java-library`
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
    }
  }
}

val javafxVersion = "21.0.3"
val osName = System.getProperty("os.name").lowercase()
val arch = System.getProperty("os.arch").lowercase()
val platform = when {
  osName.contains("win") && arch.contains("64") -> "win"
  osName.contains("linux") && arch.contains("aarch64") -> "linux-aarch64"
  osName.contains("linux") -> "linux"
  osName.contains("mac") && arch.contains("aarch64") -> "mac-aarch64"
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
}
