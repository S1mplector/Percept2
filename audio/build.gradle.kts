plugins {
  `java-library`
}

sourceSets {
  main {
    java {
      setSrcDirs(
          listOf(
              "src/main/java",
              "simp3/src/main/java/com/musicplayer/core/audio",
              "simp3/src/main/java/com/musicplayer/data/models"
          )
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

dependencies {
  api(project(":core"))
  implementation(project(":fx"))
  implementation(project(":audio-fx"))
  implementation("com.googlecode.soundlibs:basicplayer:3.0.0.0")
  implementation("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
  implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4")
  implementation("org.jflac:jflac-codec:1.5.2")
}
